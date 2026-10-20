package com.xy2407.nudge;

import net.minecraft.world.entity.Entity;

/**
 * Version shim for version specific entity predicates
 */
public final class NudgeCompat {

    private NudgeCompat() {
    }

    public static boolean collidable(Entity entity) {
        return entity.canBeCollidedWith();
    }
}
