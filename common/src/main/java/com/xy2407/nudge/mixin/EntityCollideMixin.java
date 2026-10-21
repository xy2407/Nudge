package com.xy2407.nudge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reserves block collision capacity to avoid builder regrowth on 1.20.1 and 1.21.1
 */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {

    @Redirect(method = "collideBoundingBox", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableList;builderWithExpectedSize(I)Lcom/google/common/collect/ImmutableList$Builder;"),
            require = 0)
    private static ImmutableList.Builder<VoxelShape> nudge$reserve1201(int expected) {
        return ImmutableList.builderWithExpectedSize(expected + 32);
    }

    @Redirect(method = "collectColliders", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableList;builderWithExpectedSize(I)Lcom/google/common/collect/ImmutableList$Builder;"),
            require = 0)
    private static ImmutableList.Builder<VoxelShape> nudge$reserve1211(int expected) {
        return ImmutableList.builderWithExpectedSize(expected + 32);
    }
}
