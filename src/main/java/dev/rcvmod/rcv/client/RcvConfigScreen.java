package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.DustTrapdoorMode;
import dev.rcvmod.rcv.core.EdgeType;
import dev.rcvmod.rcv.core.GraphOptions;
import dev.rcvmod.rcv.core.NcMode;
import dev.rcvmod.rcv.core.PpMode;
import java.util.List;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Cloth Config based configuration GUI, reachable from ModMenu and {@code /rcv config}. */
public final class RcvConfigScreen {

    private RcvConfigScreen() {
    }

    public static Screen create(Screen parent) {
        RcvConfig config = RcvConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("rcv.config.title"))
                .setSavingRunnable(config::save);
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("rcv.config.general"));
        general.addEntry(entries.startBooleanToggle(Component.translatable("rcv.config.colorblind"),
                        config.alternatePalette)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("rcv.config.colorblind.tooltip"))
                .setSaveConsumer(value -> config.alternatePalette = value)
                .build());
        general.addEntry(entries.startBooleanToggle(Component.translatable("rcv.config.show_hud"), config.showHud)
                .setDefaultValue(true)
                .setSaveConsumer(value -> config.showHud = value)
                .build());
        general.addEntry(entries.startIntSlider(Component.translatable("rcv.config.default_depth"), config.defaultDepth, 1,
                        GraphOptions.MAX_DEPTH)
                .setDefaultValue(16)
                .setSaveConsumer(value -> config.defaultDepth = value)
                .build());
        general.addEntry(entries.startFloatField(Component.translatable("rcv.config.node_width"), config.nodeLineWidth)
                .setDefaultValue(2.0f)
                .setSaveConsumer(value -> config.nodeLineWidth = value)
                .build());
        general.addEntry(entries.startFloatField(Component.translatable("rcv.config.edge_width"), config.edgeLineWidth)
                .setDefaultValue(10.0f)
                .setSaveConsumer(value -> config.edgeLineWidth = value)
                .build());
        general.addEntry(entries.startIntField(Component.translatable("rcv.config.autoclear"),
                        config.autoClearSeconds)
                .setDefaultValue(0)
                .setSaveConsumer(value -> config.autoClearSeconds = value)
                .build());
        general.addEntry(entries.startStrField(Component.translatable("rcv.config.wand_item"), config.wandItem)
                .setDefaultValue("minecraft:purple_dye")
                .setSaveConsumer(value -> config.wandItem = value)
                .build());
        general.addEntry(entries.startStringDropdownMenu(Component.translatable("rcv.config.pp_mode"), config.ppMode)
                .setSelections(List.of(PpMode.OFF.name(), PpMode.OBSERVER_ONLY.name(), PpMode.ALL.name()))
                .setDefaultValue(PpMode.OBSERVER_ONLY.name())
                .setSaveConsumer(value -> config.ppMode = value)
                .build());
        general.addEntry(entries.startStringDropdownMenu(Component.translatable("rcv.config.nc_mode"), config.ncMode)
                .setSelections(List.of(NcMode.OFF.name(), NcMode.ALL.name()))
                .setDefaultValue(NcMode.OFF.name())
                .setSaveConsumer(value -> config.ncMode = value)
                .build());
        general.addEntry(entries.startStringDropdownMenu(Component.translatable("rcv.config.dust_trapdoor"),
                        config.dustTrapdoorMode().name())
                .setSelections(List.of(DustTrapdoorMode.AUTO.name(), DustTrapdoorMode.ON.name(),
                        DustTrapdoorMode.OFF.name()))
                .setDefaultValue(DustTrapdoorMode.AUTO.name())
                .setTooltip(Component.translatable("rcv.config.dust_trapdoor.tooltip"))
                .setSaveConsumer(value -> config.dustTrapdoor = value)
                .build());

        ConfigCategory types = builder.getOrCreateCategory(Component.translatable("rcv.config.types"));
        for (EdgeType type : EdgeType.values()) {
            types.addEntry(entries.startBooleanToggle(Component.translatable(type.translationKey()),
                            config.enabledTypes.getOrDefault(type.name(), true))
                    .setDefaultValue(true)
                    .setSaveConsumer(value -> config.enabledTypes.put(type.name(), value))
                    .build());
        }

        ConfigCategory colors = builder.getOrCreateCategory(Component.translatable("rcv.config.colors"));
        for (EdgeType type : EdgeType.values()) {
            // Cloth's color field is RGB only ("transparency is not allowed"), so strip/store the alpha here.
            int rgb = config.colors.getOrDefault(type.name(), RcvConfig.defaultColor(type, false)) & 0xFFFFFF;
            colors.addEntry(entries.startColorField(Component.translatable(type.translationKey()), rgb)
                    .setDefaultValue(RcvConfig.defaultColor(type, false) & 0xFFFFFF)
                    .setSaveConsumer(value -> config.colors.put(type.name(), 0xFF000000 | (value & 0xFFFFFF)))
                    .build());
        }

        return builder.build();
    }
}
