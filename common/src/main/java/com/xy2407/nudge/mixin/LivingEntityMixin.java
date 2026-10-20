package com.xy2407.nudge.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.xy2407.nudge.CollisionGrid;
import com.xy2407.nudge.NudgeCore;

/**
 * Push throttling, grid backed push candidates and idle travel throttling
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Unique
    private Vec3 nudge$lastTravelPos;

    @Unique
    private int nudge$stillTicks;

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true, require = 0)
    private void nudge$throttlePush(CallbackInfo ci) {
        if (!NudgeCore.pushThrottleActive()) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() || self instanceof Player) {
            return;
        }
        int interval = Math.max(1, NudgeCore.pushInterval());
        if (Math.floorMod(self.tickCount + self.getId(), interval) != 0) {
            ci.cancel();
        }
    }

    @Redirect(method = "pushEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 0)
    private List<Entity> nudge$pushCandidates(Level level, Entity except, AABB box,
                                              Predicate<? super Entity> predicate) {
        List<Entity> fast = CollisionGrid.pushCandidates(level, except, box);
        return fast != null ? fast : level.getEntities(except, box, predicate);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 0)
    private void nudge$travel(Vec3 travelVector, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!NudgeCore.travelThrottleActive() || self.level().isClientSide() || self instanceof Player) {
            return;
        }
        if (!nudge$canThrottleTravel(self)) {
            nudge$lastTravelPos = null;
            nudge$stillTicks = 0;
            return;
        }
        Vec3 current = self.position();
        boolean stable = nudge$lastTravelPos != null
                && current.distanceToSqr(nudge$lastTravelPos) < 1.0E-7D;
        nudge$lastTravelPos = current;
        if (!stable) {
            nudge$stillTicks = 1;
            return;
        }
        nudge$stillTicks++;
        if (nudge$stillTicks < Math.max(1, NudgeCore.stationaryInterval())) {
            Vec3 movement = self.getDeltaMovement();
            self.setDeltaMovement(new Vec3(movement.x, 0.0D, movement.z));
            ci.cancel();
            return;
        }
        nudge$stillTicks = 0;
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
