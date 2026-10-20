package com.xy2407.nudge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.xy2407.nudge.CollisionGrid;

/**
 * Routes collide-time entity collision lookups through the grid on remapped runtimes
 */
@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @Redirect(method = "collide", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 0)
    private List<VoxelShape> nudge$collideCollisions(Level level, Entity entity, AABB box) {
        return CollisionGrid.entityCollisions(level, entity, box);
    }
}
