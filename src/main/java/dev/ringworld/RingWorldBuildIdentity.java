package dev.ringworld;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Loader-neutral identity embedded into every runtime jar. */
public final class RingWorldBuildIdentity {
    private static final String RESOURCE = "/ringworld-build.properties";
    private static final Identity CURRENT = load();

    private RingWorldBuildIdentity() { }

    public static String artifactVersion() {
        return CURRENT.artifactVersion();
    }

    public static String releaseLabel() {
        return CURRENT.releaseLabel();
    }

    public static String displayLabel() {
        return releaseLabel() + " · " + artifactVersion();
    }

    private static Identity load() {
        try (InputStream input = RingWorldBuildIdentity.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("missing " + RESOURCE);
            Properties properties = new Properties();
            properties.load(input);
            return new Identity(required(properties, "artifactVersion"),
                    required(properties, "releaseLabel"));
        } catch (IOException | IllegalArgumentException exception) {
            RingWorldMod.LOGGER.error("Could not read RingWorld build identity", exception);
            return new Identity("unknown", "Development build");
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key, "").trim();
        if (value.isEmpty() || value.contains("${")) {
            throw new IllegalArgumentException("missing or unexpanded build property " + key);
        }
        return value;
    }

    private record Identity(String artifactVersion, String releaseLabel) { }
}
