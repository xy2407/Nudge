package com.xy2407.nudge.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.xy2407.nudge.CollisionGrid;

/**
 * Routes Level area entity queries through the grid, with vanilla fallback
 */
@Mixin(Level.class)
public abstract class LevelQueryMixin {

    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void nudge$entityQuery(Entity except, AABB area, Predicate<? super Entity> predicate,
                                   CallbackInfoReturnable<List<Entity>> cir) {
        List<Entity> fast = CollisionGrid.entities((Level) (Object) this, except, area, predicate);
        if (fast != null) {
            cir.setReturnValue(fast);
        }
    }
}
