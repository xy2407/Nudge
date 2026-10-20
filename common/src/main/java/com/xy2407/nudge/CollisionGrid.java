package com.xy2407.nudge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.xy2407.nudge.duck.EntityStamp;

/**
 * Incremental spatial grid serving hard collision, push and area entity queries
 */
public final class CollisionGrid {

    private static final int RECONCILE_INTERVAL = 400;
    private static final int BUCKET_PRUNE_INTERVAL = 100;
    private static final long OVERSIZED_CELL_LIMIT = 512L;
    private static final long MAX_QUERY_CELLS = 64L;
    private static final AtomicLong QUERY_ID = new AtomicLong();
    private static final Map<ServerLevel, CollisionGrid> GRIDS = new HashMap<>();
    private static final ThreadLocal<List<Entity>> CANDIDATE_BUFFER = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<Entity>> PUSH_BUFFER = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<VoxelShape>> SHAPE_BUFFER = ThreadLocal.withInitial(ArrayList::new);

    private final Long2ObjectOpenHashMap<Bucket> buckets = new Long2ObjectOpenHashMap<>();
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    private final Queue<Entity> joined = new ArrayDeque<>();
    private final List<Entity> oversized = new ArrayList<>();
    private long sweepCount;
    private boolean initialized;

    private static final class Bucket {
        final List<Entity> collidable = new ArrayList<>();
        final List<Entity> pushable = new ArrayList<>();
        final List<Entity> plain = new ArrayList<>();
    }

    private static final class Tracked {
        final Entity entity;
        AABB box;
        boolean collidable;
        boolean pushable;
        boolean spectator;
        boolean oversized;
        final LongList cells = new LongArrayList();

        Tracked(Entity entity) {
            this.entity = entity;
        }
    }

    private CollisionGrid() {
    }

    public static void tick(ServerLevel level) {
        GRIDS.computeIfAbsent(level, key -> new CollisionGrid()).doTick(level);
    }

    public static void onEntityJoin(ServerLevel level, Entity entity) {
        CollisionGrid grid = GRIDS.get(level);
        if (grid != null && grid.initialized) {
            grid.joined.add(entity);
        }
    }

    public static void onEntityLeave(ServerLevel level, Entity entity) {
        CollisionGrid grid = GRIDS.get(level);
        if (grid != null) {
            grid.untrack(entity.getUUID());
        }
    }

    public static void forget(ServerLevel level) {
        GRIDS.remove(level);
    }

    public static void clear() {
        GRIDS.clear();
    }

    private void doTick(ServerLevel level) {
        if (!NudgeCore.gridActive()) {
            joined.clear();
            oversized.clear();
            tracked.clear();
            buckets.clear();
            initialized = false;
            return;
        }
        if (!initialized) {
            doFullRebuild(level);
            return;
        }
        Entity entity;
        while ((entity = joined.poll()) != null) {
            track(level, entity);
        }
        sweep(level);
        sweepCount++;
        if (sweepCount % RECONCILE_INTERVAL == 0) {
            doFullRebuild(level);
            return;
        }
        if (sweepCount % BUCKET_PRUNE_INTERVAL == 0) {
            buckets.values().removeIf(b -> b.collidable.isEmpty() && b.pushable.isEmpty() && b.plain.isEmpty());
        }
    }

    private void doFullRebuild(ServerLevel level) {
        for (Bucket bucket : buckets.values()) {
            bucket.collidable.clear();
            bucket.pushable.clear();
            bucket.plain.clear();
        }
        oversized.clear();
        tracked.clear();
        joined.clear();
        for (Entity entity : level.getAllEntities()) {
            track(level, entity);
        }
        initialized = true;
    }

    private void track(ServerLevel level, Entity entity) {
        if (entity.isRemoved() || entity.level() != level || tracked.containsKey(entity.getUUID())) {
            return;
        }
        Tracked t = new Tracked(entity);
        t.box = entity.getBoundingBox();
        t.collidable = NudgeCompat.collidable(entity);
        t.pushable = entity.isPushable();
        t.spectator = entity.isSpectator();
        t.oversized = coveredCells(t.box) > OVERSIZED_CELL_LIMIT;
        bucketAdd(t);
        tracked.put(entity.getUUID(), t);
    }

    private void untrack(UUID id) {
        Tracked t = tracked.remove(id);
        if (t != null) {
            bucketRemove(t);
        }
    }

    private void sweep(ServerLevel level) {
        if (tracked.isEmpty()) {
            return;
        }
        Iterator<Tracked> iterator = tracked.values().iterator();
        while (iterator.hasNext()) {
            Tracked t = iterator.next();
            Entity entity = t.entity;
            if (entity.isRemoved() || entity.level() != level) {
                bucketRemove(t);
                iterator.remove();
                continue;
            }
            AABB box = entity.getBoundingBox();
            boolean collidable = NudgeCompat.collidable(entity);
            boolean pushable = entity.isPushable();
            boolean spectator = entity.isSpectator();
            if (box.equals(t.box) && collidable == t.collidable && pushable == t.pushable
                    && spectator == t.spectator) {
                continue;
            }
            bucketRemove(t);
            t.box = box;
            t.collidable = collidable;
            t.pushable = pushable;
            t.spectator = spectator;
            t.oversized = coveredCells(box) > OVERSIZED_CELL_LIMIT;
            bucketAdd(t);
        }
    }

    private void bucketAdd(Tracked t) {
        if (t.spectator) {
            return;
        }
        if (t.oversized) {
            oversized.add(t.entity);
            return;
        }
        AABB box = t.box;
        for (int cx = cellOf(box.minX); cx <= cellOf(box.maxX); cx++) {
            for (int cy = cellOf(box.minY); cy <= cellOf(box.maxY); cy++) {
                for (int cz = cellOf(box.minZ); cz <= cellOf(box.maxZ); cz++) {
                    Bucket bucket = buckets.computeIfAbsent(key(cx, cy, cz), k -> new Bucket());
                    if (t.collidable) {
                        bucket.collidable.add(t.entity);
                    }
                    if (t.pushable) {
                        bucket.pushable.add(t.entity);
                    }
                    if (!t.collidable && !t.pushable) {
                        bucket.plain.add(t.entity);
                    }
                    t.cells.add(key(cx, cy, cz));
                }
            }
        }
    }

    private void bucketRemove(Tracked t) {
        if (t.oversized) {
            oversized.remove(t.entity);
            return;
        }
        for (int i = 0, n = t.cells.size(); i < n; i++) {
            Bucket bucket = buckets.get(t.cells.getLong(i));
            if (bucket != null) {
                bucket.collidable.remove(t.entity);
                bucket.pushable.remove(t.entity);
                bucket.plain.remove(t.entity);
            }
        }
        t.cells.clear();
    }

    public static List<VoxelShape> entityCollisions(Level level, Entity entity, AABB collisionBox) {
        if (collisionBox.getSize() < 1.0E-7D) {
            return List.of();
        }
        AABB query = collisionBox.inflate(1.0E-7D);
        if (coveredCells(query) > MAX_QUERY_CELLS || !(level instanceof ServerLevel serverLevel)) {
            return shapesOf(fallback(level, entity, query));
        }
        CollisionGrid grid = GRIDS.get(serverLevel);
        List<Entity> candidates;
        if (grid != null && grid.initialized && NudgeCore.gridActive()
                && serverLevel.getServer() != null && serverLevel.getServer().isSameThread()) {
            candidates = grid.collectHard(entity, query);
        } else {
            candidates = fallback(level, entity, query);
        }
        return shapesOf(candidates);
    }

    private static List<VoxelShape> shapesOf(List<Entity> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<VoxelShape> shapes = SHAPE_BUFFER.get();
        shapes.clear();
        for (Entity candidate : candidates) {
            shapes.add(Shapes.create(candidate.getBoundingBox()));
        }
        return shapes;
    }

    public static List<Entity> pushCandidates(Level level, Entity except, AABB box) {
        if (!(level instanceof ServerLevel serverLevel) || coveredCells(box) > MAX_QUERY_CELLS) {
            return null;
        }
        CollisionGrid grid = GRIDS.get(serverLevel);
        if (grid == null || !grid.initialized || !NudgeCore.gridActive() || !NudgeCore.pushIndexActive()
                || serverLevel.getServer() == null || !serverLevel.getServer().isSameThread()) {
            return null;
        }
        List<Entity> out = PUSH_BUFFER.get();
        out.clear();
        grid.collectPush(except, box, out);
        return out;
    }

    public static List<Entity> entities(Level level, Entity except, AABB area,
                                        Predicate<? super Entity> predicate) {
        if (!(level instanceof ServerLevel serverLevel) || coveredCells(area) > MAX_QUERY_CELLS) {
            return null;
        }
        CollisionGrid grid = GRIDS.get(serverLevel);
        if (grid == null || !grid.initialized || !NudgeCore.gridActive() || !NudgeCore.entityQueryActive()) {
            return null;
        }
        if (serverLevel.getServer() == null || !serverLevel.getServer().isSameThread()) {
            return null;
        }
        List<Entity> out = new ArrayList<>(8);
        grid.collectGeneric(except, area, predicate, out);
        return out;
    }

    private List<Entity> collectGeneric(Entity except, AABB query,
                                        Predicate<? super Entity> predicate, List<Entity> out) {
        long stamp = QUERY_ID.incrementAndGet();
        for (int cx = cellOf(query.minX) - 1; cx <= cellOf(query.maxX) + 1; cx++) {
            for (int cy = cellOf(query.minY) - 1; cy <= cellOf(query.maxY) + 1; cy++) {
                for (int cz = cellOf(query.minZ) - 1; cz <= cellOf(query.maxZ) + 1; cz++) {
                    Bucket bucket = buckets.get(key(cx, cy, cz));
                    if (bucket == null) {
                        continue;
                    }
                    deliver(bucket.collidable, except, query, predicate, out, stamp);
                    deliver(bucket.pushable, except, query, predicate, out, stamp);
                    deliver(bucket.plain, except, query, predicate, out, stamp);
                }
            }
        }
        deliver(oversized, except, query, predicate, out, stamp);
        return out;
    }

    private List<Entity> collectHard(Entity self, AABB query) {
        List<Entity> out = CANDIDATE_BUFFER.get();
        out.clear();
        long stamp = QUERY_ID.incrementAndGet();
        for (int cx = cellOf(query.minX) - 1; cx <= cellOf(query.maxX) + 1; cx++) {
            for (int cy = cellOf(query.minY) - 1; cy <= cellOf(query.maxY) + 1; cy++) {
                for (int cz = cellOf(query.minZ) - 1; cz <= cellOf(query.maxZ) + 1; cz++) {
                    Bucket bucket = buckets.get(key(cx, cy, cz));
                    if (bucket != null) {
                        deliver(bucket.collidable, self, query, null, out, stamp);
                    }
                }
            }
        }
        deliver(oversized, self, query, EntitySelector.CAN_BE_COLLIDED_WITH, out, stamp);
        return out;
    }

    private void collectPush(Entity except, AABB query, List<Entity> out) {
        long stamp = QUERY_ID.incrementAndGet();
        for (int cx = cellOf(query.minX) - 1; cx <= cellOf(query.maxX) + 1; cx++) {
            for (int cy = cellOf(query.minY) - 1; cy <= cellOf(query.maxY) + 1; cy++) {
                for (int cz = cellOf(query.minZ) - 1; cz <= cellOf(query.maxZ) + 1; cz++) {
                    Bucket bucket = buckets.get(key(cx, cy, cz));
                    if (bucket != null) {
                        deliverPushing(bucket.pushable, except, query, out, stamp);
                    }
                }
            }
        }
        deliver(oversized, except, query,
                candidate -> candidate.isPushable() && !candidate.isPassengerOfSameVehicle(except),
                out, stamp);
    }

    private static void deliverPushing(List<Entity> set, Entity except, AABB query,
                                       List<Entity> out, long stamp) {
        for (int i = 0, n = set.size(); i < n; i++) {
            Entity candidate = set.get(i);
            if (candidate == except || !sectionMatches(candidate, query)
                    || !candidate.getBoundingBox().intersects(query)) {
                continue;
            }
            if (except != null && candidate.isPassengerOfSameVehicle(except)) {
                continue;
            }
            if (candidate instanceof EntityStamp stampable) {
                if (stampable.nudgeQueryStamp() == stamp) {
                    continue;
                }
                stampable.nudgeSetQueryStamp(stamp);
            }
            out.add(candidate);
        }
    }

    private static void deliver(List<Entity> set, Entity except, AABB query,
                                Predicate<? super Entity> predicate, List<Entity> out, long stamp) {
        for (int i = 0, n = set.size(); i < n; i++) {
            Entity candidate = set.get(i);
            if (candidate == except || !sectionMatches(candidate, query)
                    || !candidate.getBoundingBox().intersects(query)) {
                continue;
            }
            if (candidate instanceof EntityStamp stampable) {
                if (stampable.nudgeQueryStamp() == stamp) {
                    continue;
                }
                stampable.nudgeSetQueryStamp(stamp);
            }
            if (predicate == null || predicate.test(candidate)) {
                out.add(candidate);
            }
        }
    }

    private static List<Entity> fallback(Level level, Entity entity, AABB query) {
        List<Entity> raw = level.getEntities(entity, query, EntitySelector.NO_SPECTATORS);
        if (raw.isEmpty()) {
            return List.of();
        }
        List<Entity> out = CANDIDATE_BUFFER.get();
        out.clear();
        for (int i = 0, n = raw.size(); i < n; i++) {
            Entity candidate = raw.get(i);
            if (candidate != entity && NudgeCompat.collidable(candidate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private static boolean sectionMatches(Entity candidate, AABB area) {
        BlockPos pos = candidate.blockPosition();
        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;
        return sectionX >= (Mth.floor(area.minX) >> 4) && sectionX <= (Mth.floor(area.maxX) >> 4)
                && sectionY >= (Mth.floor(area.minY) >> 4) && sectionY <= (Mth.floor(area.maxY) >> 4)
                && sectionZ >= (Mth.floor(area.minZ) >> 4) && sectionZ <= (Mth.floor(area.maxZ) >> 4);
    }

    private static int cellOf(double value) {
        return Mth.floor(value);
    }

    private static long coveredCells(AABB box) {
        long dx = (long) (cellOf(box.maxX) - cellOf(box.minX) + 1);
        long dy = (long) (cellOf(box.maxY) - cellOf(box.minY) + 1);
        long dz = (long) (cellOf(box.maxZ) - cellOf(box.minZ) + 1);
        return dx * dy * dz;
    }

    private static long key(int cx, int cy, int cz) {
        return ((long) cx * 73856093L) ^ ((long) cy * 19349663L) ^ ((long) cz * 83492791L);
    }
}
