package com.xy2407.collision.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xy2407.collision.block.SupportBlockScan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Serves checkSupportingBlock lookups from section bitmaps when provably safe
 */
@Mixin(Entity.class)
public abstract class SupportBlockMixin {

    @WrapOperation(method = "checkSupportingBlock(ZLnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;findSupportingBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/Optional;"),
            require = 0, expect = 0)
    private Optional<BlockPos> nudge$supportingBlock(Level level, Entity entity, AABB box,
                                                     Operation<Optional<BlockPos>> original) {
        Optional<BlockPos> fast = SupportBlockScan.findSupportingBlock(level, entity, box);
        return fast != null ? fast : original.call(level, entity, box);
    }
}
