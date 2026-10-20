package com.xy2407.collision.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import com.xy2407.collision.CollisionGrid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Routes Level hard collision queries through the grid, with vanilla fallback
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    public List<VoxelShape> getEntityCollisions(Entity entity, AABB collisionBox) {
        return CollisionGrid.entityCollisions((Level) (Object) this, entity, collisionBox);
    }
}
