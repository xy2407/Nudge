package com.xy2407.nudge;

/**
 * Loader agnostic core: shared config values and the entry points loaders call into
 */
public final class NudgeCore {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("nudge");

    private static volatile boolean gridEnabled = true;
    private static volatile boolean pushIndexEnabled = true;
    private static volatile boolean entityQueryEnabled = true;
    private static volatile boolean pushThrottleEnabled = true;
    private static volatile int pushInterval = 10;
    private static volatile boolean travelThrottleEnabled = true;
    private static volatile int stationaryInterval = 40;
    private static volatile int supportInterval = 10;
    private static volatile boolean blockCacheEnabled = true;

    private NudgeCore() {
    }

    public static void init() {
        LOGGER.info("[Nudge] core loaded, grid={} pushIndex={} entityQuery={}",
                gridEnabled, pushIndexEnabled, entityQueryEnabled);
    }

    public static boolean gridActive() {
        return gridEnabled;
    }

    public static boolean pushIndexActive() {
        return pushIndexEnabled;
    }

    public static boolean entityQueryActive() {
        return entityQueryEnabled;
    }

    public static boolean pushThrottleActive() {
        return pushThrottleEnabled;
    }

    public static int pushInterval() {
        return pushInterval;
    }

    public static boolean travelThrottleActive() {
        return travelThrottleEnabled;
    }

    public static int stationaryInterval() {
        return stationaryInterval;
    }

    public static int supportInterval() {
        return supportInterval;
    }

    public static boolean blockCacheActive() {
        return blockCacheEnabled;
    }

    public static void setGridEnabled(boolean value) {
        gridEnabled = value;
    }

    public static void setPushIndexEnabled(boolean value) {
        pushIndexEnabled = value;
    }

    public static void setEntityQueryEnabled(boolean value) {
        entityQueryEnabled = value;
    }

    public static void setPushThrottleEnabled(boolean value) {
        pushThrottleEnabled = value;
    }

    public static void setPushInterval(int value) {
        pushInterval = Math.max(1, value);
    }

    public static void setTravelThrottleEnabled(boolean value) {
        travelThrottleEnabled = value;
    }

    public static void setStationaryInterval(int value) {
        stationaryInterval = Math.max(1, value);
    }

    public static void setSupportInterval(int value) {
        supportInterval = Math.max(1, value);
    }

    public static void setBlockCacheEnabled(boolean value) {
        blockCacheEnabled = value;
    }
}
