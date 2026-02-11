package com.spectate.client;

import com.spectate.SpectateMod;
import com.spectate.config.ConfigManager;
import com.spectate.config.SpectateConfig;

/**
 * 客户端移轴参数控制器。
 * 负责参数读取、边界约束和回写配置文件。
 */
public class TiltShiftSettings {

    private static final double MIN_FOCUS_Y = 0.0;
    private static final double MAX_FOCUS_Y = 1.0;
    private static final double MIN_FOCUS_WIDTH = 0.01;
    private static final double MAX_FOCUS_WIDTH = 0.8;
    private static final double MIN_BLUR_RADIUS = 1.0;
    private static final double MAX_BLUR_RADIUS = 100.0;
    private static final double MIN_FALLOFF = 0.1;
    private static final double MAX_FALLOFF = 1.0;
    private static final double MIN_SATURATION_BOOST = 1.0;
    private static final double MAX_SATURATION_BOOST = 2.0;

    private static final double DEFAULT_FOCUS_Y = 0.5;
    private static final double DEFAULT_FOCUS_WIDTH = 0.1;
    private static final double DEFAULT_BLUR_RADIUS = 40.0;
    private static final double DEFAULT_FALLOFF = 0.5;
    private static final double DEFAULT_SATURATION_BOOST = 1.5;

    private final ConfigManager configManager;

    private boolean enabled;
    private double focusY;
    private double focusWidth;
    private double blurRadius;
    private double falloff;
    private double saturationBoost;

    public TiltShiftSettings() {
        this.configManager = ConfigManager.getInstance();
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        SpectateConfig.Settings settings = configManager.getConfig().settings;

        migrateMissingDefaults(settings);

        this.enabled = settings.tiltshift_enabled;
        this.focusY = clamp(settings.tiltshift_focus_y, MIN_FOCUS_Y, MAX_FOCUS_Y);
        this.focusWidth = clamp(settings.tiltshift_focus_width, MIN_FOCUS_WIDTH, MAX_FOCUS_WIDTH);
        this.blurRadius = clamp(settings.tiltshift_blur_radius, MIN_BLUR_RADIUS, MAX_BLUR_RADIUS);
        this.falloff = clamp(settings.tiltshift_falloff, MIN_FALLOFF, MAX_FALLOFF);
        this.saturationBoost = clamp(settings.tiltshift_saturation_boost, MIN_SATURATION_BOOST, MAX_SATURATION_BOOST);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean toggleEnabled() {
        enabled = !enabled;
        persistBoolean("tiltshift_enabled", enabled);
        return enabled;
    }

    public double getFocusY() {
        return focusY;
    }

    public double adjustFocusY(double delta) {
        focusY = clamp(focusY + delta, MIN_FOCUS_Y, MAX_FOCUS_Y);
        persistDouble("tiltshift_focus_y", focusY);
        return focusY;
    }

    public double getBlurRadius() {
        return blurRadius;
    }

    public double adjustBlurRadius(double delta) {
        blurRadius = clamp(blurRadius + delta, MIN_BLUR_RADIUS, MAX_BLUR_RADIUS);
        persistDouble("tiltshift_blur_radius", blurRadius);
        return blurRadius;
    }

    public double getFocusWidth() {
        return focusWidth;
    }

    public double getFalloff() {
        return falloff;
    }

    public double getSaturationBoost() {
        return saturationBoost;
    }

    private void migrateMissingDefaults(SpectateConfig.Settings settings) {
        boolean likelyMissingTiltShiftFields =
                settings.tiltshift_focus_width <= 0.0
                        && settings.tiltshift_blur_radius <= 0.0
                        && settings.tiltshift_falloff <= 0.0
                        && settings.tiltshift_saturation_boost <= 0.0;
        if (!likelyMissingTiltShiftFields) {
            return;
        }

        persistBoolean("tiltshift_enabled", false);
        persistDouble("tiltshift_focus_y", DEFAULT_FOCUS_Y);
        persistDouble("tiltshift_focus_width", DEFAULT_FOCUS_WIDTH);
        persistDouble("tiltshift_blur_radius", DEFAULT_BLUR_RADIUS);
        persistDouble("tiltshift_falloff", DEFAULT_FALLOFF);
        persistDouble("tiltshift_saturation_boost", DEFAULT_SATURATION_BOOST);
    }

    private void persistBoolean(String key, boolean value) {
        boolean saved = configManager.setConfigValue("settings." + key, Boolean.toString(value));
        if (!saved) {
            SpectateMod.LOGGER.warn("[Spectate] Failed to save tiltshift boolean: {}={}", key, value);
        }
    }

    private void persistDouble(String key, double value) {
        boolean saved = configManager.setConfigValue("settings." + key, Double.toString(value));
        if (!saved) {
            SpectateMod.LOGGER.warn("[Spectate] Failed to save tiltshift double: {}={}", key, value);
        }
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}

