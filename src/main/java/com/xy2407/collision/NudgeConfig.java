package com.xy2407.collision;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config toggles for the grid, push index and travel throttling
 */
public final class NudgeConfig {

    private static final ModConfigSpec.BooleanValue ENABLE_GRID;
    private static final ModConfigSpec.BooleanValue ENABLE_PUSH_INDEX;
    private static final ModConfigSpec.BooleanValue ENABLE_ENTITY_QUERY;
    private static final ModConfigSpec.BooleanValue ENABLE_PUSH_THROTTLE;
    private static final ModConfigSpec.IntValue PUSH_INTERVAL;
    private static final ModConfigSpec.BooleanValue ENABLE_TRAVEL_THROTTLE;
    private static final ModConfigSpec.BooleanValue ENABLE_BLOCK_CACHE;
    private static final ModConfigSpec.IntValue STATIONARY_INTERVAL;
    private static final ModConfigSpec.IntValue SUPPORT_INTERVAL;
    public static final ModConfigSpec SPEC;

    private static volatile boolean gridEnabled = true;
    private static volatile boolean pushIndexEnabled = true;
    private static volatile boolean entityQueryEnabled = true;
    private static volatile boolean pushThrottleEnabled = true;
    private static volatile int pushInterval = 10;
    private static volatile boolean travelThrottleEnabled = true;
    private static volatile boolean blockCacheEnabled = true;
    private static volatile int stationaryInterval = 40;
    private static volatile int supportInterval = 10;

    private NudgeConfig() {
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLE_GRID = builder.comment("Use the incremental entity grid for collision and entity queries")
                .define("enableCollisionGrid", true);
        ENABLE_PUSH_INDEX = builder.comment("Serve LivingEntity push candidates from the grid")
                .define("enablePushIndex", true);
        ENABLE_ENTITY_QUERY = builder.comment("Serve generic Level#getEntities area queries from the grid")
                .define("enableEntityQueryIndex", true);
        ENABLE_PUSH_THROTTLE = builder.comment("Throttle LivingEntity pushEntities to once every pushTickInterval ticks")
                .define("enablePushThrottle", true);
        PUSH_INTERVAL = builder.comment("Push throttle interval in ticks, staggered by entity id")
                .defineInRange("pushTickInterval", 10, 1, 200);
        ENABLE_TRAVEL_THROTTLE = builder.comment("Run travel for stationary entities once every stationaryTravelInterval ticks")
                .define("enableTravelThrottle", true);
        ENABLE_BLOCK_CACHE = builder.comment("Skip empty blocks in block collision scans with per-section occupancy bitmaps")
                .define("enableBlockOccupancyCache", true);
        STATIONARY_INTERVAL = builder.comment("Interval in ticks between travel runs of a stationary entity")
                .defineInRange("stationaryTravelInterval", 40, 1, 1200);
        SUPPORT_INTERVAL = builder.comment("Interval in ticks between ground support checks of a throttled entity")
                .defineInRange("supportCheckInterval", 10, 1, 1200);
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
        gridEnabled = loaded ? ENABLE_GRID.get() : ENABLE_GRID.getDefault();
        pushIndexEnabled = loaded ? ENABLE_PUSH_INDEX.get() : ENABLE_PUSH_INDEX.getDefault();
        entityQueryEnabled = loaded ? ENABLE_ENTITY_QUERY.get() : ENABLE_ENTITY_QUERY.getDefault();
        pushThrottleEnabled = loaded ? ENABLE_PUSH_THROTTLE.get() : ENABLE_PUSH_THROTTLE.getDefault();
        pushInterval = loaded ? PUSH_INTERVAL.get() : PUSH_INTERVAL.getDefault();
        travelThrottleEnabled = loaded ? ENABLE_TRAVEL_THROTTLE.get() : ENABLE_TRAVEL_THROTTLE.getDefault();
        blockCacheEnabled = loaded ? ENABLE_BLOCK_CACHE.get() : ENABLE_BLOCK_CACHE.getDefault();
        stationaryInterval = loaded ? STATIONARY_INTERVAL.get() : STATIONARY_INTERVAL.getDefault();
        supportInterval = loaded ? SUPPORT_INTERVAL.get() : SUPPORT_INTERVAL.getDefault();
    }

    public static boolean gridActive() {
        return SPEC.isLoaded() && gridEnabled;
    }

    public static boolean pushIndexActive() {
        return SPEC.isLoaded() && pushIndexEnabled;
    }

    public static boolean entityQueryActive() {
        return SPEC.isLoaded() && entityQueryEnabled;
    }

    public static boolean pushThrottleActive() {
        return SPEC.isLoaded() && pushThrottleEnabled;
    }

    public static int pushInterval() {
        return pushInterval;
    }

    public static boolean travelThrottleActive() {
        return SPEC.isLoaded() && travelThrottleEnabled;
    }

    public static boolean blockCacheActive() {
        return SPEC.isLoaded() && blockCacheEnabled;
    }

    public static int stationaryInterval() {
        return stationaryInterval;
    }

    public static int supportInterval() {
        return supportInterval;
    }
}
