package com.xy2407.collision.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xy2407.collision.block.BlockSectionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Skips empty blocks inside vanilla block collision scans through the section bitmap
 */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsMixin {

    @WrapOperation(method = "computeNext", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 0, expect = 0)
    private BlockState nudge$skipEmpty(BlockGetter blockGetter, BlockPos pos,
                                       Operation<BlockState> original) {
        BlockState fast = BlockSectionCache.fastState(blockGetter, pos);
        return fast != null ? fast : original.call(blockGetter, pos);
    }
}
