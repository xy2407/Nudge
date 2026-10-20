package com.xy2407.collision;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Mod entry point: registers the server config and its reload listener
 */
@Mod(Nudge.MOD_ID)
public final class Nudge {

    public static final String MOD_ID = "nudge";

    public Nudge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, NudgeConfig.SPEC);
        modEventBus.addListener(NudgeConfig::onConfigEvent);
    }
}
