package com.xy2407.collision.block;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.xy2407.collision.NudgeConfig;

/**
 * Exact vanilla-semantics ground support scan over section bitmaps, null = fallback
 */
public final class SupportBlockScan {

    private static final double EPSILON = 1.0E-7D;
    private static final int MAX_SCAN_CELLS = 512;

    private SupportBlockScan() {
    }

    public static Optional<BlockPos> findSupportingBlock(Level level, Entity entity, AABB slab) {
        if (!NudgeConfig.supportScanActive() || entity == null || level.isClientSide()) {
            return null;
        }
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()) {
            return null;
        }
        int minX = Mth.floor(slab.minX - EPSILON);
        int maxX = Mth.floor(slab.maxX + EPSILON);
        int minY = Mth.floor(slab.minY - EPSILON);
        int maxY = Mth.floor(slab.maxY + EPSILON);
        int minZ = Mth.floor(slab.minZ - EPSILON);
        int maxZ = Mth.floor(slab.maxZ + EPSILON);
        long cells = (long) (maxX - minX + 3) * (maxY - minY + 3) * (maxZ - minZ + 3);
        if (cells > MAX_SCAN_CELLS) {
            return null;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        VoxelShape boxShape = Shapes.create(slab);
        net.minecraft.world.phys.Vec3 center = entity.position();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        LevelChunk chunk = null;
        int chunkX = Integer.MIN_VALUE;
        int chunkZ = Integer.MIN_VALUE;
        BlockSectionCache cache = null;
        int cacheSection = Integer.MIN_VALUE;

        for (int y = minY - 1; y <= maxY + 1; y++) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                for (int x = minX - 1; x <= maxX + 1; x++) {
                    int edges = (x < minX || x > maxX ? 1 : 0)
                            + (y < minY || y > maxY ? 1 : 0)
                            + (z < minZ || z > maxZ ? 1 : 0);
                    if (edges == 3) {
                        continue;
                    }
                    if (chunk == null || chunkX != (x >> 4) || chunkZ != (z >> 4)) {
                        if (!(level.getChunkForCollisions(x >> 4, z >> 4) instanceof LevelChunk loaded)) {
                            continue;
                        }
                        chunk = loaded;
                        chunkX = x >> 4;
                        chunkZ = z >> 4;
                        cache = null;
                        cacheSection = Integer.MIN_VALUE;
                    }
                    int section = SectionPos.blockToSectionCoord(y) - chunk.getMinSection();
                    if (section != cacheSection) {
                        cache = BlockSectionCache.sectionCache(level, chunk, y);
                        cacheSection = section;
                    }
                    if (cache == null) {
                        return null;
                    }
                    if (cache.empty() || !cache.occupied(x, y, z)) {
                        continue;
                    }
                    BlockState state = chunk.getBlockState(cursor.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    if (edges == 1) {
                        if (!state.hasLargeCollisionShape()) {
                            continue;
                        }
                    } else if (edges == 2) {
                        if (!state.is(Blocks.MOVING_PISTON)) {
                            continue;
                        }
                    }
                    if (BlockSectionCache.contextDependent(state)) {
                        return null;
                    }
                    VoxelShape shape = state.getCollisionShape(level, cursor);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    if (shape == Shapes.block()) {
                        if (!slab.intersects(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D)) {
                            continue;
                        }
                    } else {
                        VoxelShape moved = shape.move(x, y, z);
                        if (moved.isEmpty() || !Shapes.joinIsNotEmpty(moved, boxShape, BooleanOp.AND)) {
                            continue;
                        }
                    }
                    BlockPos candidate = cursor.immutable();
                    double dist = candidate.distToCenterSqr(center);
                    if (dist < bestDist || (dist == bestDist && (bestPos == null || bestPos.compareTo(candidate) < 0))) {
                        bestPos = candidate;
                        bestDist = dist;
                    }
                }
            }
        }
        return Optional.ofNullable(bestPos);
    }
}
