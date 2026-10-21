package com.xy2407.collision.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reserves block collision capacity in collectColliders to avoid builder regrowth
 */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {

    @WrapOperation(method = "collectColliders", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableList;builderWithExpectedSize(I)Lcom/google/common/collect/ImmutableList$Builder;"),
            require = 0, expect = 0)
    private static ImmutableList.Builder<VoxelShape> nudge$reserve(int expected,
            Operation<ImmutableList.Builder<VoxelShape>> original) {
        return original.call(expected + 32);
    }
}
