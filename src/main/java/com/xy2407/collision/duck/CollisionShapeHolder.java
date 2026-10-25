package com.xy2407.collision.duck;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Caches the entity collision shape between bounding box changes
 */
public interface CollisionShapeHolder {

    AABB nudge$shapeBox();

    VoxelShape nudge$collisionShape();

    void nudge$setShape(AABB box, VoxelShape shape);
}
