package com.xy2407.collision.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xy2407.collision.block.LadderCache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Serves the NeoForge ladder hook inside onClimbable from the per-state cache
 */
@Mixin(LivingEntity.class)
public abstract class LadderCacheMixin {

    @Unique
    private BlockState nudge$ladderState;

    @Unique
    private byte nudge$ladderValue;

    @Unique
    private int nudge$ladderVersion;

    @WrapOperation(method = "onClimbable", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/CommonHooks;isLivingOnLadder(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;"),
            require = 0, expect = 0)
    private Optional<BlockPos> nudge$ladder(BlockState state, Level level, BlockPos pos, LivingEntity entity,
                                            Operation<Optional<BlockPos>> original) {
        if (!LadderCache.applies(state, level)) {
            nudge$ladderState = null;
            nudge$ladderValue = 0;
            return original.call(state, level, pos, entity);
        }
        int version = LadderCache.version();
        if (state == nudge$ladderState && nudge$ladderVersion == version) {
            byte memo = nudge$ladderValue;
            if (memo == 1) {
                return Optional.empty();
            }
            if (memo == 2) {
                return Optional.of(pos);
            }
        }
        Boolean cached = LadderCache.cached(state);
        if (cached != null) {
            nudge$ladderState = state;
            nudge$ladderValue = cached ? (byte) 2 : (byte) 1;
            nudge$ladderVersion = version;
            return cached ? Optional.of(pos) : Optional.empty();
        }
        Optional<BlockPos> result = original.call(state, level, pos, entity);
        boolean present = result.isPresent();
        LadderCache.store(state, present);
        nudge$ladderState = state;
        nudge$ladderValue = present ? (byte) 2 : (byte) 1;
        nudge$ladderVersion = version;
        return result;
    }
}
