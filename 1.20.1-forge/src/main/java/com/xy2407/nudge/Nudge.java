package com.xy2407.nudge;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entry point for Minecraft 1.20.1
 */
@Mod(Nudge.MOD_ID)
public final class Nudge {

    public static final String MOD_ID = "nudge";

    public Nudge() {
        NudgeCore.init();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, NudgeConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(NudgeConfig::onConfigEvent);
    }
}
