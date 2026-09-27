package dev.rcvmod.rcv.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rcvmod.rcv.RCV;
import dev.rcvmod.rcv.core.EdgeType;
import dev.rcvmod.rcv.core.NcMode;
import dev.rcvmod.rcv.core.PpMode;
import dev.rcvmod.rcv.core.TypeMask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Client-side configuration, persisted as {@code config/rcv-client.json}. */
public final class RcvConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RcvConfig instance;

    public boolean alternatePalette = false;
    public float lineWidth = 2.0f;
    public int defaultDepth = 16;
    public boolean showHud = true;
    public int autoClearSeconds = 0;
    public String wandItem = "minecraft:purple_dye";
    public String ppMode = PpMode.OBSERVER_ONLY.name();
    public String ncMode = NcMode.OFF.name();
    public Map<String, Boolean> enabledTypes = new LinkedHashMap<>();
    public Map<String, Integer> colors = new LinkedHashMap<>();
    public Map<String, Integer> colorsColorBlind = new LinkedHashMap<>();

    public RcvConfig() {
        for (EdgeType type : EdgeType.values()) {
            this.enabledTypes.put(type.name(), true);
            this.colors.put(type.name(), defaultColor(type, false));
            this.colorsColorBlind.put(type.name(), defaultColor(type, true));
        }
    }

    public static RcvConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public TypeMask typeMask() {
        TypeMask mask = TypeMask.all();
        for (EdgeType type : EdgeType.values()) {
            Boolean enabled = this.enabledTypes.get(type.name());
            if (enabled != null && !enabled) {
                mask.set(type, false);
            }
        }
        return mask;
    }

    public PpMode ppMode() {
        return PpMode.valueOf(this.ppMode);
    }

    public NcMode ncMode() {
        return NcMode.valueOf(this.ncMode);
    }

    public int color(EdgeType type) {
        Map<String, Integer> map = this.alternatePalette ? this.colorsColorBlind : this.colors;
        Integer color = map.get(type.name());
        return color == null ? defaultColor(type, this.alternatePalette) : color;
    }

    public static int defaultColor(EdgeType type, boolean colorBlind) {
        if (colorBlind) {
            return switch (type) {
                case DIRECT_ACTIVATION -> 0xFFE69F00;
                case CIRCUIT -> 0xFFFFA500;
                case COMPARATOR_SIDE -> 0xFFFFB347;
                case REPEATER_SIDE -> 0xFFFFD27F;
                case ANALOG -> 0xFFCC79A7;
                case CHARGE -> 0xFFF0E442;
                case HALF -> 0xFFCC66FF;
                case TRIPWIRE -> 0xFF56B4E9;
                case PISTON -> 0xFF009E73;
                case DOOR_PAIR -> 0xFFFFFFFF;
                case RAIL -> 0xFF9E9E9E;
                case SHAPE -> 0xFF8D6E63;
                case DISTANCE -> 0xFF7CB342;
                case NC -> 0xFF8B4513;
                case PP -> 0xFF0072B2;
            };
        }
        return switch (type) {
            case DIRECT_ACTIVATION -> 0xFFFF3B30;
            case CIRCUIT -> 0xFFFF9500;
            case COMPARATOR_SIDE -> 0xFFFFC04D;
            case REPEATER_SIDE -> 0xFFFFDE85;
            case ANALOG -> 0xFFB45BFF;
            case CHARGE -> 0xFFFFE24D;
            case HALF -> 0xFFFF4DD2;
            case TRIPWIRE -> 0xFF33E0E0;
            case PISTON -> 0xFF33CC55;
            case DOOR_PAIR -> 0xFFFFFFFF;
            case RAIL -> 0xFFA0A0A0;
            case SHAPE -> 0xFF9C7B55;
            case DISTANCE -> 0xFF7FD14A;
            case NC -> 0xFFAA1111;
            case PP -> 0xFF3B7BFF;
        };
    }

    public static RcvConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                RcvConfig config = GSON.fromJson(json, RcvConfig.class);
                if (config != null) {
                    config.normalise();
                    return config;
                }
            } catch (Exception e) {
                RCV.LOGGER.warn("Failed to read {}, using defaults", path, e);
            }
        }
        RcvConfig config = new RcvConfig();
        config.save();
        return config;
    }

    private void normalise() {
        if (this.enabledTypes == null) {
            this.enabledTypes = new LinkedHashMap<>();
        }
        if (this.colors == null) {
            this.colors = new LinkedHashMap<>();
        }
        if (this.colorsColorBlind == null) {
            this.colorsColorBlind = new LinkedHashMap<>();
        }
        for (EdgeType type : EdgeType.values()) {
            this.enabledTypes.putIfAbsent(type.name(), true);
            this.colors.putIfAbsent(type.name(), defaultColor(type, false));
            this.colorsColorBlind.putIfAbsent(type.name(), defaultColor(type, true));
        }
    }

    public void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RCV.LOGGER.warn("Failed to write RCV client config", e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("rcv-client.json");
    }
}
