package com.xy2407.nudge.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Section collision occupancy bitmap, version agnostic core shared by every loader
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

    private final long[] occupancy = new long[64];
    private int nonEmptyCount;

    public BlockSectionCache() {
    }

    public boolean isEmpty() {
        return nonEmptyCount == 0;
    }

    public boolean isOccupied(int x, int y, int z) {
        int cell = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        return (occupancy[cell >> 6] & 1L << (cell & 63)) != 0L;
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

    public void scan(SectionStateReader reader) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (occupies(reader.get(x, y, z))) {
                        setBit(x, y, z);
                    }
                }
            }
        }
    }

    private void setBit(int x, int y, int z) {
        int cell = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        occupancy[cell >> 6] |= 1L << (cell & 63);
        nonEmptyCount++;
    }

    private static boolean occupies(BlockState state) {
        if (CONTEXT_DEPENDENT.get(state.getBlock().getClass())) {
            return true;
        }
        return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                CollisionContext.empty()).isEmpty();
    }
}
