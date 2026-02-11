package com.spectate.client.config;

import com.spectate.SpectateMod;
import com.spectate.client.ClientSpectateManager;
import com.spectate.config.ConfigManager;
import com.spectate.config.SpectateConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
//#if MC < 11900
//$$ import net.minecraft.text.LiteralText;
//#endif

/**
 * Cloth Config screen factory for client-side editable settings.
 */
@Environment(EnvType.CLIENT)
public final class SpectateConfigScreenFactory {

    private SpectateConfigScreenFactory() {
    }

    public static Screen create(Screen parent) {
        ConfigManager manager = ConfigManager.getInstance();
        SpectateConfig config = manager.getConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(literal("Spectate Config"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory generalCategory = builder.getOrCreateCategory(literal("General"));
        ConfigCategory floatingCategory = builder.getOrCreateCategory(literal("Floating Camera"));
        ConfigCategory tiltShiftCategory = builder.getOrCreateCategory(literal("Tilt-Shift"));

        generalCategory.addEntry(entryBuilder
                .startIntField(literal("Cycle Interval Seconds"), config.settings.cycle_interval_seconds)
                .setDefaultValue(60)
                .setSaveConsumer(value -> saveInt(manager, "settings.cycle_interval_seconds", value))
                .build());

        generalCategory.addEntry(entryBuilder
                .startDoubleField(literal("Spectate Distance"), config.settings.spectate_distance)
                .setDefaultValue(20.0)
                .setSaveConsumer(value -> saveDouble(manager, "settings.spectate_distance", value))
                .build());

        generalCategory.addEntry(entryBuilder
                .startDoubleField(literal("Spectate Height Offset"), config.settings.spectate_height_offset)
                .setDefaultValue(5.0)
                .setSaveConsumer(value -> saveDouble(manager, "settings.spectate_height_offset", value))
                .build());

        generalCategory.addEntry(entryBuilder
                .startDoubleField(literal("Spectate Rotation Speed"), config.settings.spectate_rotation_speed)
                .setDefaultValue(1.0)
                .setSaveConsumer(value -> saveDouble(manager, "settings.spectate_rotation_speed", value))
                .build());

        floatingCategory.addEntry(entryBuilder
                .startDoubleField(literal("Floating Strength"), config.settings.floating_strength)
                .setDefaultValue(0.5)
                .setSaveConsumer(value -> saveDouble(manager, "settings.floating_strength", value))
                .build());

        floatingCategory.addEntry(entryBuilder
                .startDoubleField(literal("Floating Speed"), config.settings.floating_speed)
                .setDefaultValue(0.3)
                .setSaveConsumer(value -> saveDouble(manager, "settings.floating_speed", value))
                .build());

        floatingCategory.addEntry(entryBuilder
                .startDoubleField(literal("Floating Orbit Radius"), config.settings.floating_orbit_radius)
                .setDefaultValue(8.0)
                .setSaveConsumer(value -> saveDouble(manager, "settings.floating_orbit_radius", value))
                .build());

        floatingCategory.addEntry(entryBuilder
                .startDoubleField(literal("Floating Height Variation"), config.settings.floating_height_variation)
                .setDefaultValue(0.8)
                .setSaveConsumer(value -> saveDouble(manager, "settings.floating_height_variation", value))
                .build());

        floatingCategory.addEntry(entryBuilder
                .startDoubleField(literal("Floating Breathing Frequency"), config.settings.floating_breathing_frequency)
                .setDefaultValue(0.5)
                .setSaveConsumer(value -> saveDouble(manager, "settings.floating_breathing_frequency", value))
                .build());

        floatingCategory.addEntry(entryBuilder
                .startDoubleField(literal("Floating Damping Factor"), config.settings.floating_damping_factor)
                .setDefaultValue(0.95)
                .setSaveConsumer(value -> saveDouble(manager, "settings.floating_damping_factor", value))
                .build());

        floatingCategory.addEntry(entryBuilder
                .startDoubleField(literal("Floating Attraction Factor"), config.settings.floating_attraction_factor)
                .setDefaultValue(0.3)
                .setSaveConsumer(value -> saveDouble(manager, "settings.floating_attraction_factor", value))
                .build());

        floatingCategory.addEntry(entryBuilder
                .startDoubleField(literal("Floating Prediction Factor"), config.settings.floating_prediction_factor)
                .setDefaultValue(2.0)
                .setSaveConsumer(value -> saveDouble(manager, "settings.floating_prediction_factor", value))
                .build());

        tiltShiftCategory.addEntry(entryBuilder
                .startBooleanToggle(literal("Enable Tilt-Shift"), config.settings.tiltshift_enabled)
                .setDefaultValue(false)
                .setSaveConsumer(value -> saveBoolean(manager, "settings.tiltshift_enabled", value))
                .build());

        tiltShiftCategory.addEntry(entryBuilder
                .startDoubleField(literal("Focus Y"), config.settings.tiltshift_focus_y)
                .setDefaultValue(0.5)
                .setMin(0.0)
                .setMax(1.0)
                .setSaveConsumer(value -> saveDouble(manager, "settings.tiltshift_focus_y", value))
                .build());

        tiltShiftCategory.addEntry(entryBuilder
                .startDoubleField(literal("Focus Width"), config.settings.tiltshift_focus_width)
                .setDefaultValue(0.3)
                .setMin(0.1)
                .setMax(0.8)
                .setSaveConsumer(value -> saveDouble(manager, "settings.tiltshift_focus_width", value))
                .build());

        tiltShiftCategory.addEntry(entryBuilder
                .startDoubleField(literal("Blur Radius"), config.settings.tiltshift_blur_radius)
                .setDefaultValue(8.0)
                .setMin(1.0)
                .setMax(20.0)
                .setSaveConsumer(value -> saveDouble(manager, "settings.tiltshift_blur_radius", value))
                .build());

        tiltShiftCategory.addEntry(entryBuilder
                .startDoubleField(literal("Falloff"), config.settings.tiltshift_falloff)
                .setDefaultValue(0.5)
                .setMin(0.1)
                .setMax(1.0)
                .setSaveConsumer(value -> saveDouble(manager, "settings.tiltshift_falloff", value))
                .build());

        tiltShiftCategory.addEntry(entryBuilder
                .startDoubleField(literal("Saturation Boost"), config.settings.tiltshift_saturation_boost)
                .setDefaultValue(1.15)
                .setMin(1.0)
                .setMax(1.5)
                .setSaveConsumer(value -> saveDouble(manager, "settings.tiltshift_saturation_boost", value))
                .build());

        return builder.build();
    }

    private static void saveBoolean(ConfigManager manager, String path, boolean value) {
        boolean saved = manager.setConfigValue(path, Boolean.toString(value));
        if (!saved) {
            SpectateMod.LOGGER.warn("[Spectate] Failed to save config path {} with value {}", path, value);
        }
        ClientSpectateManager.getInstance().reloadClientConfig();
    }

    private static void saveInt(ConfigManager manager, String path, int value) {
        boolean saved = manager.setConfigValue(path, Integer.toString(value));
        if (!saved) {
            SpectateMod.LOGGER.warn("[Spectate] Failed to save config path {} with value {}", path, value);
        }
        ClientSpectateManager.getInstance().reloadClientConfig();
    }

    private static void saveDouble(ConfigManager manager, String path, double value) {
        boolean saved = manager.setConfigValue(path, Double.toString(value));
        if (!saved) {
            SpectateMod.LOGGER.warn("[Spectate] Failed to save config path {} with value {}", path, value);
        }
        ClientSpectateManager.getInstance().reloadClientConfig();
    }

    private static Text literal(String value) {
        //#if MC >= 11900
        return Text.literal(value);
        //#else
        //$$ return new LiteralText(value);
        //#endif
    }
}
