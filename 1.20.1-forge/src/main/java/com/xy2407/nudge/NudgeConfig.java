package com.xy2407.nudge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Server config for grid, push index and travel throttling
 */
public final class NudgeConfig {

    private static final ForgeConfigSpec.BooleanValue ENABLE_GRID;
    private static final ForgeConfigSpec.BooleanValue ENABLE_PUSH_INDEX;
    private static final ForgeConfigSpec.BooleanValue ENABLE_ENTITY_QUERY;
    private static final ForgeConfigSpec.BooleanValue ENABLE_PUSH_THROTTLE;
    private static final ForgeConfigSpec.IntValue PUSH_INTERVAL;
    private static final ForgeConfigSpec.BooleanValue ENABLE_TRAVEL_THROTTLE;
    private static final ForgeConfigSpec.IntValue STATIONARY_INTERVAL;
    public static final ForgeConfigSpec SPEC;

    private NudgeConfig() {
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLE_GRID = builder.comment("Use the incremental entity grid for collision and entity queries")
                .define("enableCollisionGrid", true);
        ENABLE_PUSH_INDEX = builder.comment("Serve LivingEntity push candidates from the grid")
                .define("enablePushIndex", true);
        ENABLE_ENTITY_QUERY = builder.comment("Serve generic Level area entity queries from the grid")
                .define("enableEntityQueryIndex", true);
        ENABLE_PUSH_THROTTLE = builder.comment("Throttle LivingEntity pushEntities per entity")
                .define("enablePushThrottle", true);
        PUSH_INTERVAL = builder.comment("Ticks between push runs of one entity")
                .defineInRange("pushTickInterval", 10, 1, 200);
        ENABLE_TRAVEL_THROTTLE = builder.comment("Skip physics for stationary entities")
                .define("enableTravelThrottle", true);
        STATIONARY_INTERVAL = builder.comment("Ticks between travel runs of a stationary entity")
                .defineInRange("stationaryTravelInterval", 20, 1, 1200);
        SPEC = builder.build();
        refresh();
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            refresh();
        }
    }

    public static void refresh() {
        boolean loaded = SPEC.isLoaded();
        NudgeCore.setGridEnabled(loaded ? ENABLE_GRID.get() : ENABLE_GRID.getDefault());
        NudgeCore.setPushIndexEnabled(loaded ? ENABLE_PUSH_INDEX.get() : ENABLE_PUSH_INDEX.getDefault());
        NudgeCore.setEntityQueryEnabled(loaded ? ENABLE_ENTITY_QUERY.get() : ENABLE_ENTITY_QUERY.getDefault());
        NudgeCore.setPushThrottleEnabled(loaded ? ENABLE_PUSH_THROTTLE.get() : ENABLE_PUSH_THROTTLE.getDefault());
        NudgeCore.setPushInterval(loaded ? PUSH_INTERVAL.get() : PUSH_INTERVAL.getDefault());
        NudgeCore.setTravelThrottleEnabled(loaded ? ENABLE_TRAVEL_THROTTLE.get() : ENABLE_TRAVEL_THROTTLE.getDefault());
        NudgeCore.setStationaryInterval(loaded ? STATIONARY_INTERVAL.get() : STATIONARY_INTERVAL.getDefault());
    }
}
