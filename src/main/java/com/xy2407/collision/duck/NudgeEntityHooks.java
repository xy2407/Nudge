package com.xy2407.collision.duck;

/**
 * Extra per-tick hooks for entities whose travel is throttled
 */
public interface NudgeEntityHooks {

    void nudge$tickStationaryBlockEffects();
}
