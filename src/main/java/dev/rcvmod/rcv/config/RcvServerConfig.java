package dev.rcvmod.rcv.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.rcvmod.rcv.RCV;
import dev.rcvmod.rcv.core.GraphOptions;
import dev.rcvmod.rcv.core.NcMode;
import dev.rcvmod.rcv.core.PpMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Server-side configuration, persisted as {@code config/rcv-server.json} and hot-reloadable. */
public final class RcvServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RcvServerConfig instance;

    public boolean enabled = true;
    public boolean requireOp = true;
    public boolean allowNonOp = false;
    public int defaultDepth = 16;
    public int maxDepth = 64;
    public int maxNodes = GraphOptions.MAX_NODES;
    public int maxEdges = GraphOptions.MAX_EDGES;
    public String ppMode = PpMode.OBSERVER_ONLY.name();
    public String ncMode = NcMode.OFF.name();
    public int railRange = 8;

    public static RcvServerConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Hot reload from disk ({@code /rcv reload}). */
    public static RcvServerConfig reload() {
        instance = load();
        return instance;
    }

    public PpMode ppMode() {
        return PpMode.valueOf(this.ppMode);
    }

    public NcMode ncMode() {
        return NcMode.valueOf(this.ncMode);
    }

    public static RcvServerConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                RcvServerConfig config = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                        RcvServerConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                RCV.LOGGER.warn("Failed to read {}, using defaults", path, e);
            }
        }
        RcvServerConfig config = new RcvServerConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RCV.LOGGER.warn("Failed to write RCV server config", e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("rcv-server.json");
    }
}
