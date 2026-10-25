package com.xy2407.collision;

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
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.entity.PartEntity;

import com.xy2407.collision.duck.CollisionShapeHolder;
import com.xy2407.collision.duck.EntityStamp;

/**
 * Incremental spatial grid serving hard collision, push and area entity queries
 */
public final class CollisionGrid {

    private static final int RECONCILE_INTERVAL = 400;
    private static final int RECONCILE_BUDGET = 256;
    private static final double AABB_SWEEP_EPSILON = 0.1D;
    private static final double SQRT_HALF = 0.7071067811865476D;
    private static final double[][] CROWD_DIRECTIONS = {
            {0.0D, 0.0D},
            {1.0D, 0.0D},
            {SQRT_HALF, SQRT_HALF},
            {0.0D, 1.0D},
            {-SQRT_HALF, SQRT_HALF},
            {-1.0D, 0.0D},
            {-SQRT_HALF, -SQRT_HALF},
            {0.0D, -1.0D},
            {SQRT_HALF, -SQRT_HALF}
    };
    private static final int BUCKET_PRUNE_INTERVAL = 100;
    private static final long OVERSIZED_CELL_LIMIT = 512L;
    private static final long MAX_QUERY_CELLS = 64L;
    private static final ClassValue<Boolean> OVERRIDES_CAN_COLLIDE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("canCollideWith", Entity.class).getDeclaringClass() != Entity.class;
            } catch (NoSuchMethodException missing) {
                return true;
            }
        }
    };
    private static final AtomicLong QUERY_ID = new AtomicLong();
    private static final Map<ServerLevel, CollisionGrid> GRIDS = new HashMap<>();
    private static final ThreadLocal<List<Entity>> CANDIDATE_BUFFER = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<Entity>> PUSH_BUFFER = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<VoxelShape>> SHAPE_BUFFER = ThreadLocal.withInitial(ArrayList::new);

    private final Long2ObjectOpenHashMap<Bucket> buckets = new Long2ObjectOpenHashMap<>();
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    private final Queue<Entity> joined = new ArrayDeque<>();
    private final List<Entity> oversized = new ArrayList<>();
    private final LongOpenHashSet crowdCells = new LongOpenHashSet();
    private long crowdTick = Long.MIN_VALUE;
    private List<Entity> reconcileQueue;
    private int reconcileIndex;
    private boolean reconciling;
    private boolean reconcileSawDragon;
    private long sweepCount;
    private boolean initialized;
    private boolean dragonPresent;

    private static final class Bucket {
        final ObjectLinkedOpenHashSet<Entity> collidable = new ObjectLinkedOpenHashSet<>();
        final ObjectLinkedOpenHashSet<Entity> pushable = new ObjectLinkedOpenHashSet<>();
        final ObjectLinkedOpenHashSet<Entity> plain = new ObjectLinkedOpenHashSet<>();
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
        if (!NudgeConfig.gridActive()) {
            joined.clear();
            oversized.clear();
            tracked.clear();
            buckets.clear();
            reconcileQueue = null;
            reconcileIndex = 0;
            reconciling = false;
            reconcileSawDragon = false;
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
        if (!reconciling && sweepCount % RECONCILE_INTERVAL == 0) {
            startReconcile(level);
        }
        if (reconciling) {
            reconcileStep(level);
        }
        if (sweepCount % BUCKET_PRUNE_INTERVAL == 0) {
            buckets.values().removeIf(b -> b.collidable.isEmpty() && b.pushable.isEmpty()
                    && b.plain.isEmpty());
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
        reconcileQueue = null;
        reconcileIndex = 0;
        reconciling = false;
        reconcileSawDragon = false;
        dragonPresent = false;
        for (Entity entity : level.getAllEntities()) {
            track(level, entity);
        }
        initialized = true;
    }

    private void startReconcile(ServerLevel level) {
        reconcileQueue = new ArrayList<>(tracked.size());
        for (Tracked t : tracked.values()) {
            reconcileQueue.add(t.entity);
        }
        for (Entity entity : level.getAllEntities()) {
            if (!tracked.containsKey(entity.getUUID())) {
                reconcileQueue.add(entity);
            }
        }
        reconcileIndex = 0;
        reconcileSawDragon = false;
        reconciling = true;
    }

    private void reconcileStep(ServerLevel level) {
        int end = Math.min(reconcileIndex + RECONCILE_BUDGET, reconcileQueue.size());
        for (; reconcileIndex < end; reconcileIndex++) {
            Entity entity = reconcileQueue.get(reconcileIndex);
            if (entity.isRemoved() || entity.level() != level) {
                untrack(entity.getUUID());
                continue;
            }
            Tracked t = tracked.get(entity.getUUID());
            if (t == null) {
                track(level, entity);
                continue;
            }
            if (entity.getType() == EntityType.ENDER_DRAGON) {
                reconcileSawDragon = true;
            }
            bucketRemove(t);
            t.box = entity.getBoundingBox();
            t.collidable = entity.canBeCollidedWith();
            t.pushable = entity.isPushable();
            t.spectator = entity.isSpectator();
            t.oversized = coveredCells(t.box) > OVERSIZED_CELL_LIMIT;
            bucketAdd(t);
        }
        if (reconcileIndex >= reconcileQueue.size()) {
            reconciling = false;
            reconcileQueue = null;
            reconcileIndex = 0;
            dragonPresent = reconcileSawDragon || hasDragonTracked();
        }
    }

    private boolean hasDragonTracked() {
        for (Tracked t : tracked.values()) {
            if (t.entity.getType() == EntityType.ENDER_DRAGON) {
                return true;
            }
        }
        return false;
    }

    private void track(ServerLevel level, Entity entity) {
        if (entity.isRemoved() || entity.level() != level || tracked.containsKey(entity.getUUID())) {
            return;
        }
        Tracked t = new Tracked(entity);
        t.box = entity.getBoundingBox();
        t.collidable = entity.canBeCollidedWith();
        t.pushable = entity.isPushable();
        t.spectator = entity.isSpectator();
        if (entity.getType() == EntityType.ENDER_DRAGON) {
            dragonPresent = true;
        }
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

    public static int crowdPushSize(ServerLevel level, Entity entity) {
        CollisionGrid grid = GRIDS.get(level);
        if (grid == null || !grid.initialized) {
            return 0;
        }
        Tracked t = grid.tracked.get(entity.getUUID());
        if (t == null || t.oversized) {
            return 0;
        }
        Bucket bucket = grid.buckets.get(grid.crowdCell(t));
        return bucket == null ? 0 : bucket.pushable.size();
    }

    public static boolean crowdPush(ServerLevel level, Entity entity) {
        CollisionGrid grid = GRIDS.get(level);
        if (grid == null || !grid.initialized) {
            return false;
        }
        Tracked t = grid.tracked.get(entity.getUUID());
        if (t == null || t.oversized) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (grid.crowdTick != gameTime) {
            grid.crowdCells.clear();
            grid.crowdTick = gameTime;
        }
        long cell = grid.crowdCell(t);
        if (!grid.crowdCells.add(cell)) {
            return true;
        }
        List<Entity> candidates = pushCandidates(level, entity, entity.getBoundingBox());
        if (candidates == null || candidates.isEmpty()) {
            grid.crowdCells.remove(cell);
            return false;
        }
        int size = candidates.size() + 1;
        int threshold = NudgeConfig.crowdPushThreshold();
        int limit = NudgeConfig.crowdPushLimit();
        if (size <= threshold || (limit > 0 && size > limit)) {
            grid.crowdCells.remove(cell);
            return false;
        }
        List<Entity> group = new ArrayList<>(size);
        group.add(entity);
        group.addAll(candidates);
        int offset = (int) Math.floorMod(gameTime + entity.getId(), (long) CROWD_DIRECTIONS.length);
        double strength = NudgeConfig.crowdPushStrength();
        for (int i = 0; i < group.size(); i++) {
            Entity member = group.get(i);
            if (member.isRemoved()) {
                continue;
            }
            double[] direction = CROWD_DIRECTIONS[(i + offset) % CROWD_DIRECTIONS.length];
            if (direction[0] == 0.0D && direction[1] == 0.0D) {
                continue;
            }
            member.push(new Vec3(direction[0] * strength, 0.0D, direction[1] * strength));
        }
        crowdCramming(level, group);
        return true;
    }

    private long crowdCell(Tracked t) {
        Entity entity = t.entity;
        return BlockPos.asLong(cellOf(entity.getX()), cellOf(entity.getY()), cellOf(entity.getZ()));
    }

    private static void crowdCramming(ServerLevel level, List<Entity> group) {
        int max = level.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
        if (max <= 0 || group.size() <= max - 1) {
            return;
        }
        int count = 0;
        for (Entity member : group) {
            if (!member.isPassenger()) {
                count++;
            }
        }
        if (count <= max - 1) {
            return;
        }
        for (Entity member : group) {
            if (member instanceof LivingEntity living && living.getRandom().nextInt(4) == 0) {
                living.hurt(living.damageSources().cramming(), 6.0F);
            }
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
            boolean collidable = entity.canBeCollidedWith();
            boolean pushable = entity.isPushable();
            boolean spectator = entity.isSpectator();
            if (closeEnough(box, t.box) && collidable == t.collidable && pushable == t.pushable
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
        if (t.oversized) {
            oversized.add(t.entity);
            return;
        }
        AABB box = t.box;
        for (int cx = cellOf(box.minX); cx <= cellOf(box.maxX); cx++) {
            for (int cy = cellOf(box.minY); cy <= cellOf(box.maxY); cy++) {
                for (int cz = cellOf(box.minZ); cz <= cellOf(box.maxZ); cz++) {
                    Bucket bucket = buckets.computeIfAbsent(BlockPos.asLong(cx, cy, cz), k -> new Bucket());
                    if (t.collidable) {
                        bucket.collidable.add(t.entity);
                    }
                    if (t.pushable) {
                        bucket.pushable.add(t.entity);
                    }
                    if (t.spectator || (!t.collidable && !t.pushable)) {
                        bucket.plain.add(t.entity);
                    }
                    t.cells.add(BlockPos.asLong(cx, cy, cz));
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
        ServerLevel serverLevel = level instanceof ServerLevel server ? server : null;
        boolean serverThread = serverLevel != null && serverLevel.getServer() != null
                && serverLevel.getServer().isSameThread();
        if (serverLevel == null || coveredCells(query) > MAX_QUERY_CELLS) {
            return shapesOf(fallback(level, entity, query), serverThread, null, null, query);
        }
        CollisionGrid grid = GRIDS.get(serverLevel);
        List<Entity> candidates;
        ServerLevel partsLevel = null;
        if (grid != null && grid.initialized && NudgeConfig.gridActive() && serverThread && !grid.dragonPresent) {
            candidates = grid.collectHard(entity, query);
            partsLevel = serverLevel;
        } else {
            candidates = fallback(level, entity, query);
        }
        return shapesOf(candidates, serverThread, partsLevel, entity, query);
    }

    private static List<VoxelShape> shapesOf(List<Entity> candidates, boolean cacheShapes,
                                             ServerLevel partsLevel, Entity self, AABB query) {
        List<VoxelShape> shapes = SHAPE_BUFFER.get();
        shapes.clear();
        for (int i = 0, n = candidates.size(); i < n; i++) {
            Entity candidate = candidates.get(i);
            shapes.add(cacheShapes ? cachedShape(candidate) : Shapes.create(candidate.getBoundingBox()));
        }
        if (partsLevel != null) {
            Predicate<? super Entity> predicate = hardPredicate(self);
            for (PartEntity<?> part : partsLevel.getPartEntities()) {
                if (part == self || !part.getBoundingBox().intersects(query) || !predicate.test(part)) {
                    continue;
                }
                shapes.add(cacheShapes ? cachedShape(part) : Shapes.create(part.getBoundingBox()));
            }
        }
        return shapes.isEmpty() ? List.of() : shapes;
    }

    private static VoxelShape cachedShape(Entity entity) {
        CollisionShapeHolder holder = (CollisionShapeHolder) entity;
        AABB box = entity.getBoundingBox();
        AABB cachedBox = holder.nudge$shapeBox();
        if (cachedBox != null && cachedBox.equals(box)) {
            return holder.nudge$collisionShape();
        }
        VoxelShape shape = Shapes.create(box);
        holder.nudge$setShape(box, shape);
        return shape;
    }

    public static List<Entity> pushCandidates(Level level, Entity except, AABB box) {
        if (!(level instanceof ServerLevel serverLevel) || coveredCells(box) > MAX_QUERY_CELLS) {
            return null;
        }
        CollisionGrid grid = GRIDS.get(serverLevel);
        if (grid == null || !grid.initialized || !NudgeConfig.gridActive() || !NudgeConfig.pushIndexActive()
                || serverLevel.getServer() == null || !serverLevel.getServer().isSameThread()) {
            return null;
        }
        List<Entity> out = PUSH_BUFFER.get();
        out.clear();
        Predicate<? super Entity> predicate = pushPredicate(except);
        grid.collectPush(except, box, predicate, out);
        deliverParts(serverLevel, except, box, predicate, out);
        return out;
    }

    public static List<Entity> entities(Level level, Entity except, AABB area,
                                        Predicate<? super Entity> predicate) {
        if (!(level instanceof ServerLevel serverLevel) || coveredCells(area) > MAX_QUERY_CELLS) {
            return null;
        }
        CollisionGrid grid = GRIDS.get(serverLevel);
        if (grid == null || !grid.initialized || !NudgeConfig.gridActive() || !NudgeConfig.entityQueryActive()
                || grid.dragonPresent) {
            return null;
        }
        if (serverLevel.getServer() == null || !serverLevel.getServer().isSameThread()) {
            return null;
        }
        List<Entity> out = new ArrayList<>(8);
        grid.collectGeneric(except, area, predicate, out);
        deliverParts(serverLevel, except, area, predicate, out);
        return out;
    }

    private List<Entity> collectGeneric(Entity except, AABB query,
                                        Predicate<? super Entity> predicate, List<Entity> out) {
        long stamp = QUERY_ID.incrementAndGet();
        for (int cx = cellOf(query.minX) - 1; cx <= cellOf(query.maxX) + 1; cx++) {
            for (int cy = cellOf(query.minY) - 1; cy <= cellOf(query.maxY) + 1; cy++) {
                for (int cz = cellOf(query.minZ) - 1; cz <= cellOf(query.maxZ) + 1; cz++) {
                    Bucket bucket = buckets.get(BlockPos.asLong(cx, cy, cz));
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
        boolean pairAware = self != null && OVERRIDES_CAN_COLLIDE.get(self.getClass());
        Predicate<? super Entity> predicate = pairAware ? hardPredicate(self) : null;
        for (int cx = cellOf(query.minX) - 1; cx <= cellOf(query.maxX) + 1; cx++) {
            for (int cy = cellOf(query.minY) - 1; cy <= cellOf(query.maxY) + 1; cy++) {
                for (int cz = cellOf(query.minZ) - 1; cz <= cellOf(query.maxZ) + 1; cz++) {
                    Bucket bucket = buckets.get(BlockPos.asLong(cx, cy, cz));
                    if (bucket == null) {
                        continue;
                    }
                    deliver(bucket.collidable, self, query, predicate, out, stamp);
                    if (pairAware) {
                        deliver(bucket.pushable, self, query, predicate, out, stamp);
                        deliver(bucket.plain, self, query, predicate, out, stamp);
                    }
                }
            }
        }
        deliver(oversized, self, query, pairAware ? predicate : EntitySelector.CAN_BE_COLLIDED_WITH,
                out, stamp);
        return out;
    }

    private void collectPush(Entity except, AABB query, Predicate<? super Entity> predicate, List<Entity> out) {
        long stamp = QUERY_ID.incrementAndGet();
        for (int cx = cellOf(query.minX) - 1; cx <= cellOf(query.maxX) + 1; cx++) {
            for (int cy = cellOf(query.minY) - 1; cy <= cellOf(query.maxY) + 1; cy++) {
                for (int cz = cellOf(query.minZ) - 1; cz <= cellOf(query.maxZ) + 1; cz++) {
                    Bucket bucket = buckets.get(BlockPos.asLong(cx, cy, cz));
                    if (bucket != null) {
                        deliverPushing(bucket.pushable, except, query, predicate, out, stamp);
                    }
                }
            }
        }
        deliver(oversized, except, query, predicate, out, stamp);
    }

    private static void deliverPushing(Iterable<Entity> set, Entity except, AABB query,
                                       Predicate<? super Entity> predicate, List<Entity> out, long stamp) {
        for (Entity candidate : set) {
            if (candidate == except || !candidate.getBoundingBox().intersects(query)) {
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

    private static void deliver(Iterable<Entity> set, Entity except, AABB query,
                                Predicate<? super Entity> predicate, List<Entity> out, long stamp) {
        for (Entity candidate : set) {
            if (candidate == except || !candidate.getBoundingBox().intersects(query)) {
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

    private static Predicate<? super Entity> hardPredicate(Entity entity) {
        return entity == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : entity::canCollideWith;
    }

    private static Predicate<? super Entity> pushPredicate(Entity except) {
        return except == null
                ? EntitySelector.NO_SPECTATORS.and(Entity::isPushable)
                : EntitySelector.pushableBy(except);
    }

    private static void deliverParts(ServerLevel level, Entity except, AABB query,
                                     Predicate<? super Entity> predicate, List<Entity> out) {
        for (PartEntity<?> part : level.getPartEntities()) {
            if (part == except || !part.getBoundingBox().intersects(query)) {
                continue;
            }
            if (predicate == null || predicate.test(part)) {
                out.add(part);
            }
        }
    }

    private static List<Entity> fallback(Level level, Entity entity, AABB query) {
        return level.getEntities(entity, query, hardPredicate(entity));
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

    private static boolean closeEnough(AABB a, AABB b) {
        return Math.abs(a.minX - b.minX) <= AABB_SWEEP_EPSILON
                && Math.abs(a.minY - b.minY) <= AABB_SWEEP_EPSILON
                && Math.abs(a.minZ - b.minZ) <= AABB_SWEEP_EPSILON
                && Math.abs(a.maxX - b.maxX) <= AABB_SWEEP_EPSILON
                && Math.abs(a.maxY - b.maxY) <= AABB_SWEEP_EPSILON
                && Math.abs(a.maxZ - b.maxZ) <= AABB_SWEEP_EPSILON;
    }

}
