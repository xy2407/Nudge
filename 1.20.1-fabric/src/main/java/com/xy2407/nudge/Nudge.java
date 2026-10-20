package com.xy2407.nudge;

import net.fabricmc.api.ModInitializer;

import com.xy2407.nudge.event.CommonEvents;

/**
 * Fabric entry point for Minecraft 1.21.1
 */
public final class Nudge implements ModInitializer {

    public static final String MOD_ID = "nudge";

    @Override
    public void onInitialize() {
        NudgeCore.init();
        NudgeConfig.load();
        CommonEvents.register();
    }
}
