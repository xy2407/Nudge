package com.xy2407.collision.block;

import java.util.ArrayDeque;
import java.util.Queue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.xy2407.collision.NudgeConfig;
import com.xy2407.collision.duck.NudgeSectionCacheHolder;

/**
 * Section collision occupancy bitmap built lazily and invalidated on block changes
 */
public final class BlockSectionCache {

    private static final ClassValue<Boolean> CONTEXT_DEPENDENT = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != BlockBehaviour.class; c = c.getSuperclass()) {
                try {
                    c.getDeclaredMethod("getCollisionShape", BlockState.class, BlockGetter.class,
                            BlockPos.class, CollisionContext.class);
                    return true;
                } catch (NoSuchMethodException missing) {
                }
            }
            return false;
        }
    };

    private static final Queue<BlockSectionCache> PENDING = new ArrayDeque<>();
    private static final int BUILD_BUDGET = 1024;

    private final long[] occupancy = new long[64];
    private int nonEmptyCount;
    private LevelChunkSection section;
    private boolean built;
    private int cursor;

    private BlockSectionCache() {
    }

    public static void tickBuilds() {
        int budget = BUILD_BUDGET;
        while (budget > 0 && !PENDING.isEmpty()) {
            BlockSectionCache cache = PENDING.peek();
            int processed = cache.buildStep(budget);
            budget -= processed;
            if (cache.built || processed == 0) {
                PENDING.poll();
            }
        }
    }

    public static BlockState fastState(BlockGetter blockGetter, BlockPos pos) {
        if (!NudgeConfig.blockCacheActive() || !(blockGetter instanceof LevelChunk chunk)) {
            return null;
        }
        Level level = chunk.getLevel();
        if (level.isClientSide()) {
            return null;
        }
        LevelChunkSection[] sections = chunk.getSections();
        int section = SectionPos.blockToSectionCoord(pos.getY()) - chunk.getMinSection();
        if (section < 0 || section >= sections.length) {
            return null;
        }
        NudgeSectionCacheHolder holder = (NudgeSectionCacheHolder) sections[section];
        BlockSectionCache cache = holder.nudge$getBlockCache();
        if (cache == null) {
            cache = tryBuild(holder, sections[section], level);
            if (cache == null) {
                return null;
            }
        }
        if (!cache.built) {
            return null;
        }
        if (cache.nonEmptyCount == 0) {
            return Blocks.AIR.defaultBlockState();
        }
        int cell = (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | (pos.getX() & 15);
        return (cache.occupancy[cell >> 6] & 1L << (cell & 63)) == 0L
                ? Blocks.AIR.defaultBlockState() : null;
    }

    public static boolean contextDependent(BlockState state) {
        return CONTEXT_DEPENDENT.get(state.getBlock().getClass());
    }

    public static BlockSectionCache sectionCache(Level level, LevelChunk chunk, int y) {
        if (!NudgeConfig.blockCacheActive()) {
            return null;
        }
        LevelChunkSection[] sections = chunk.getSections();
        int section = SectionPos.blockToSectionCoord(y) - chunk.getMinSection();
        if (section < 0 || section >= sections.length) {
            return null;
        }
        NudgeSectionCacheHolder holder = (NudgeSectionCacheHolder) sections[section];
        BlockSectionCache cache = holder.nudge$getBlockCache();
        if (cache == null) {
            cache = tryBuild(holder, sections[section], level);
        }
        return cache != null && cache.built ? cache : null;
    }

    boolean empty() {
        return nonEmptyCount == 0;
    }

    boolean occupied(int x, int y, int z) {
        int cell = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        return (occupancy[cell >> 6] & 1L << (cell & 63)) != 0L;
    }

    private static BlockSectionCache tryBuild(NudgeSectionCacheHolder holder,
                                              LevelChunkSection section, Level level) {
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()) {
            return null;
        }
        BlockSectionCache cache = new BlockSectionCache();
        cache.section = section;
        holder.nudge$setBlockCache(cache);
        PENDING.add(cache);
        return cache;
    }

    private int buildStep(int budget) {
        int processed = 0;
        while (processed < budget && cursor < 4096) {
            int cell = cursor++;
            int x = cell & 15;
            int z = (cell >> 4) & 15;
            int y = cell >> 8;
            if (occupies(section.getBlockState(x, y, z))) {
                setBit(x, y, z);
            }
            processed++;
        }
        if (cursor >= 4096) {
            built = true;
        }
        return processed;
    }

    public void update(int x, int y, int z, BlockState state) {
        boolean occupied = occupies(state);
        int cell = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        long bit = 1L << (cell & 63);
        if (occupied) {
            if ((occupancy[cell >> 6] & bit) == 0L) {
                occupancy[cell >> 6] |= bit;
                nonEmptyCount++;
            }
        } else if ((occupancy[cell >> 6] & bit) != 0L) {
            occupancy[cell >> 6] &= ~bit;
            nonEmptyCount--;
        }
    }

    private void setBit(int x, int y, int z) {
        int cell = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        long bit = 1L << (cell & 63);
        if ((occupancy[cell >> 6] & bit) == 0L) {
            occupancy[cell >> 6] |= bit;
            nonEmptyCount++;
        }
    }

    private static boolean occupies(BlockState state) {
        if (CONTEXT_DEPENDENT.get(state.getBlock().getClass())) {
            return true;
        }
        return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                CollisionContext.empty()).isEmpty();
    }
}
