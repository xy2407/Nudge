package com.xy2407.collision.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.xy2407.collision.duck.EntityStamp;

import net.minecraft.world.entity.Entity;

/**
 * Adds the per-entity query stamp field used to deduplicate grid deliveries
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements EntityStamp {

    @Unique
    private long nudgeQueryStamp;

    @Override
    public long nudgeQueryStamp() {
        return nudgeQueryStamp;
    }

    @Override
    public void nudgeSetQueryStamp(long value) {
        nudgeQueryStamp = value;
    }
}
