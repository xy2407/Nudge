package com.xy2407.nudge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xy2407.nudge.block.BlockSectionCache;
import com.xy2407.nudge.duck.NudgeSectionCacheHolder;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Holds the section block cache and invalidates it on every block change
 */
@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin implements NudgeSectionCacheHolder {

    @Unique
    private BlockSectionCache nudge$blockCache;

    @Override
    public BlockSectionCache nudge$getBlockCache() {
        return nudge$blockCache;
    }

    @Override
    public void nudge$setBlockCache(BlockSectionCache cache) {
        nudge$blockCache = cache;
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("TAIL"), require = 0)
    private void nudge$onBlockChanged(int x, int y, int z, BlockState state, boolean useLocks,
                                      CallbackInfoReturnable<BlockState> cir) {
        if (nudge$blockCache != null) {
            nudge$blockCache.update(x, y, z, state);
        }
    }
}
