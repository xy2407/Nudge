package com.xy2407.nudge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * NeoForge entry point for Minecraft 1.21.1
 */
@Mod(Nudge.MOD_ID)
public final class Nudge {

    public static final String MOD_ID = "nudge";

    public Nudge(IEventBus modEventBus, ModContainer modContainer) {
        NudgeCore.init();
        modContainer.registerConfig(ModConfig.Type.SERVER, NudgeConfig.SPEC);
        modEventBus.addListener(NudgeConfig::onConfigEvent);
    }
}
