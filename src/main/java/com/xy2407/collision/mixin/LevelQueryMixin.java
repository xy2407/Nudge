package com.xy2407.collision.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.xy2407.collision.CollisionGrid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Routes Level area entity queries through the grid, with vanilla fallback
 */
@Mixin(Level.class)
public abstract class LevelQueryMixin {

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            require = 0, expect = 0)
    private List<Entity> nudge$entityQuery(Entity except, AABB area, Predicate<? super Entity> predicate,
                                           Operation<List<Entity>> original) {
        List<Entity> fast = CollisionGrid.entities((Level) (Object) this, except, area, predicate);
        return fast != null ? fast : original.call(except, area, predicate);
    }
}
