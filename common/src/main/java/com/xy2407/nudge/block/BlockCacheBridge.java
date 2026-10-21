package com.xy2407.nudge.block;

import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.xy2407.nudge.NudgeCore;

/**
 * Loader installed resolver turning block lookups into occupancy cache hits
 */
public final class BlockCacheBridge {

    private static volatile BiFunction<BlockGetter, BlockPos, BlockState> resolver;

    private BlockCacheBridge() {
    }

    public static void install(BiFunction<BlockGetter, BlockPos, BlockState> function) {
        resolver = function;
    }

    public static BlockState fastState(BlockGetter blockGetter, BlockPos pos) {
        if (!NudgeCore.blockCacheActive()) {
            return null;
        }
        BiFunction<BlockGetter, BlockPos, BlockState> function = resolver;
        return function == null ? null : function.apply(blockGetter, pos);
    }
}
