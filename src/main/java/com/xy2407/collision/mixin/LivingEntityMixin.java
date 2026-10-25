package com.xy2407.collision.mixin;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xy2407.collision.CollisionGrid;
import com.xy2407.collision.NudgeConfig;
import com.xy2407.collision.block.SupportBlockScan;
import com.xy2407.collision.duck.NudgeEntityHooks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Push throttling, grid-backed push candidates and idle travel throttling
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Unique
    private Vec3 nudge$lastTravelPos;

    @Unique
    private int nudge$stillTicks;

    @Unique
    private int nudge$supportTicks;

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void nudge$throttlePush(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player) {
            return;
        }
        if (NudgeConfig.crowdPushActive() && self.level() instanceof ServerLevel serverLevel) {
            int crowdSize = CollisionGrid.crowdPushSize(serverLevel, self);
            int threshold = NudgeConfig.crowdPushThreshold();
            int limit = NudgeConfig.crowdPushLimit();
            if (crowdSize > threshold && (limit <= 0 || crowdSize <= limit)
                    && CollisionGrid.crowdPush(serverLevel, self)) {
                ci.cancel();
                return;
            }
        }
        if (!NudgeConfig.pushThrottleActive()) {
            return;
        }
        int interval = Math.max(1, NudgeConfig.pushInterval());
        if (Math.floorMod(self.tickCount + self.getId(), interval) != 0) {
            nudge$tickCramming((LivingEntity) self);
            ci.cancel();
        }
    }

    @Unique
    private static void nudge$tickCramming(LivingEntity self) {
        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int max = serverLevel.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
        if (max <= 0) {
            return;
        }
        List<Entity> candidates = CollisionGrid.pushCandidates(serverLevel, self, self.getBoundingBox());
        if (candidates == null) {
            candidates = serverLevel.getEntities(self, self.getBoundingBox(), EntitySelector.pushableBy(self));
        }
        if (candidates.size() > max - 1 && self.getRandom().nextInt(4) == 0) {
            int count = 0;
            for (Entity candidate : candidates) {
                if (!candidate.isPassenger()) {
                    count++;
                }
            }
            if (count > max - 1) {
                self.hurt(self.damageSources().cramming(), 6.0F);
            }
        }
    }

    @WrapOperation(method = "pushEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 0, expect = 0)
    private List<Entity> nudge$pushCandidates(Level level, Entity except, AABB box,
                                              Predicate<? super Entity> predicate,
                                              Operation<List<Entity>> original) {
        List<Entity> candidates = CollisionGrid.pushCandidates(level, except, box);
        return candidates != null ? candidates : original.call(level, except, box, predicate);
    }

    @WrapMethod(method = "travel")
    private void nudge$travel(Vec3 travelVector, Operation<Void> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!NudgeConfig.travelThrottleActive() || self.level().isClientSide() || self instanceof Player) {
            original.call(travelVector);
            return;
        }
        if (!nudge$canThrottleTravel(self)) {
            nudge$lastTravelPos = null;
            nudge$stillTicks = 0;
            nudge$supportTicks = 0;
            original.call(travelVector);
            return;
        }
        Vec3 current = self.position();
        boolean stable = nudge$lastTravelPos != null
                && current.distanceToSqr(nudge$lastTravelPos) < 1.0E-7D;
        nudge$lastTravelPos = current;
        if (!stable) {
            nudge$stillTicks = 1;
            nudge$supportTicks = 0;
            original.call(travelVector);
            return;
        }
        nudge$stillTicks++;
        if (nudge$stillTicks < Math.max(1, NudgeConfig.stationaryInterval())) {
            nudge$supportTicks++;
            if (nudge$supportTicks >= Math.max(1, NudgeConfig.supportInterval())) {
                nudge$supportTicks = 0;
                if (!nudge$hasSupport(self)) {
                    nudge$lastTravelPos = null;
                    nudge$stillTicks = 0;
                    original.call(travelVector);
                    return;
                }
            }
            Vec3 movement = self.getDeltaMovement();
            self.setDeltaMovement(new Vec3(movement.x, 0.0D, movement.z));
            ((NudgeEntityHooks) (Object) self).nudge$tickStationaryBlockEffects();
            return;
        }
        nudge$stillTicks = 0;
        original.call(travelVector);
    }

    @Unique
    private static boolean nudge$hasSupport(LivingEntity self) {
        AABB box = self.getBoundingBox();
        AABB slab = new AABB(box.minX, box.minY - 1.0E-6D, box.minZ, box.maxX, box.minY, box.maxZ);
        Optional<BlockPos> fast = SupportBlockScan.findSupportingBlock(self.level(), self, slab);
        return fast != null ? fast.isPresent() : self.level().findSupportingBlock(self, slab).isPresent();
    }

    @Unique
    private static boolean nudge$canThrottleTravel(LivingEntity self) {
        if (!self.onGround() || self.isInWater() || self.isInLava() || self.onClimbable()) {
            return false;
        }
        if (self.isPassenger() || self.isVehicle()) {
            return false;
        }
        Vec3 movement = self.getDeltaMovement();
        if (movement.horizontalDistanceSqr() > 1.0E-4D) {
            return false;
        }
        return movement.y > -0.12D && movement.y < 0.05D;
    }
}
