package com.xy2407.nudge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.xy2407.nudge.block.BlockCacheBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Skips empty blocks inside vanilla block collision scans through the section bitmap
 */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsMixin {

    @Redirect(method = "computeNext", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 0)
    private BlockState nudge$skipEmpty(BlockGetter blockGetter, BlockPos pos) {
        BlockState fast = BlockCacheBridge.fastState(blockGetter, pos);
        return fast != null ? fast : blockGetter.getBlockState(pos);
    }
}
