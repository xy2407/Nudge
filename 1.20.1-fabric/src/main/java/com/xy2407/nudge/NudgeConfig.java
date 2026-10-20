package com.xy2407.nudge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server config file backed by java properties, written on first launch
 */
public final class NudgeConfig {

    private static final String FILE_NAME = "nudge.properties";

    private NudgeConfig() {
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException ignored) {
                props.clear();
            }
        }
        props.putIfAbsent("enableCollisionGrid", "true");
        props.putIfAbsent("enablePushIndex", "true");
        props.putIfAbsent("enableEntityQueryIndex", "true");
        props.putIfAbsent("enablePushThrottle", "true");
        props.putIfAbsent("pushTickInterval", "10");
        props.putIfAbsent("enableTravelThrottle", "true");
        props.putIfAbsent("stationaryTravelInterval", "20");
        NudgeCore.setGridEnabled(readBool(props, "enableCollisionGrid", true));
        NudgeCore.setPushIndexEnabled(readBool(props, "enablePushIndex", true));
        NudgeCore.setEntityQueryEnabled(readBool(props, "enableEntityQueryIndex", true));
        NudgeCore.setPushThrottleEnabled(readBool(props, "enablePushThrottle", true));
        NudgeCore.setPushInterval(readInt(props, "pushTickInterval", 10));
        NudgeCore.setTravelThrottleEnabled(readBool(props, "enableTravelThrottle", true));
        NudgeCore.setStationaryInterval(readInt(props, "stationaryTravelInterval", 20));
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "Nudge server config");
        } catch (IOException ignored) {
            // keep defaults when the file cannot be written
        }
    }

    private static boolean readBool(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static int readInt(Properties props, String key, int fallback) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException broken) {
            return fallback;
        }
    }
}
