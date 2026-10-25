package com.xy2407.collision.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.xy2407.collision.duck.CollisionShapeHolder;
import com.xy2407.collision.duck.EntityStamp;
import com.xy2407.collision.duck.NudgeEntityHooks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Adds the query stamp and cached collision shape fields used by the grid
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements EntityStamp, CollisionShapeHolder, NudgeEntityHooks {

    @Shadow
    protected abstract void tryCheckInsideBlocks();

    @Unique
    private long nudgeQueryStamp;

    @Unique
    private AABB nudgeShapeBox;

    @Unique
    private VoxelShape nudgeCollisionShape;

    @Override
    public long nudgeQueryStamp() {
        return nudgeQueryStamp;
    }

    @Override
    public void nudgeSetQueryStamp(long value) {
        nudgeQueryStamp = value;
    }

    @Override
    public AABB nudge$shapeBox() {
        return nudgeShapeBox;
    }

    @Override
    public VoxelShape nudge$collisionShape() {
        return nudgeCollisionShape;
    }

    @Override
    public void nudge$setShape(AABB box, VoxelShape shape) {
        nudgeShapeBox = box;
        nudgeCollisionShape = shape;
    }

    @Override
    public void nudge$tickStationaryBlockEffects() {
        Entity self = (Entity) (Object) this;
        if (self.onGround()) {
            BlockPos pos = self.getOnPosLegacy();
            BlockState state = self.level().getBlockState(pos);
            state.getBlock().stepOn(self.level(), pos, state, self);
        }
        this.tryCheckInsideBlocks();
    }
}
