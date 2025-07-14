package com.spectate.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spectate.SpectateMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
//#if MC < 11900
//$$ import net.minecraft.text.LiteralText;
//#endif

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConfigManager 负责加载、创建和提供对 config.json 的全局访问。
 * 单例实现，线程安全。
 */
public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConfigManager INSTANCE = new ConfigManager();
    private static final String CONFIG_FILE_NAME = "config.json";

    private final Path configFile;
    private SpectateConfig config;

    private ConfigManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path spectateDir = configDir.resolve("spectate");
        this.configFile = spectateDir.resolve(CONFIG_FILE_NAME);
        loadConfig();
    }

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    public SpectateConfig getConfig() {
        return config;
    }

    public void reloadConfig() {
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (Files.notExists(configFile)) {
                SpectateMod.LOGGER.info("[Spectate] Config file not found, creating a default one.");
                createDefaultConfig();
            }
            try (FileReader reader = new FileReader(configFile.toFile())) {
                config = GSON.fromJson(reader, SpectateConfig.class);
            }

            if (config == null) {
                SpectateMod.LOGGER.warn("[Spectate] Config file is empty or corrupted, creating a new one.");
                createDefaultConfig();
            }
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] Failed to load or create config file.", e);
            // Use an in-memory default if all else fails
            config = new SpectateConfig();
        }
    }

    private void createDefaultConfig() throws IOException {
        config = new SpectateConfig();
        Files.createDirectories(configFile.getParent());
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(config, writer);
        }
    }

    /**
     * 获取并格式化消息文本。
     * @param messageKey 消息的键，来自 SpectateConfig.Messages
     * @param placeholders 占位符及其替换值
     * @return 格式化后的 Text 对象
     */
    public Text getFormattedMessage(String messageKey, Map<String, String> placeholders) {
        String messageTemplate = getMessageTemplate(messageKey);
        String formatted = messageTemplate;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        //#if MC >= 11900
        return Text.literal(formatted);
        //#else
        //$$ return new LiteralText(formatted);
        //#endif
    }
    
    public Text getMessage(String messageKey) {
        return getFormattedMessage(messageKey, null);
    }

    private String getMessageTemplate(String key) {
        try {
            // Use reflection to get the message from the config object
            return (String) SpectateConfig.Messages.class.getField(key).get(config.lang);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            SpectateMod.LOGGER.warn("[Spectate] Missing message key in config: " + key);
            return "Missing message: " + key;
        }
    }

    public Object getConfigValue(String path) {
        try {
            String[] parts = path.split("\\.");
            if (parts.length < 2) {
                return null;
            }

            String category = parts[0];
            String key = parts[1];

            if ("settings".equals(category)) {
                return SpectateConfig.Settings.class.getField(key).get(config.settings);
            } else if ("lang".equals(category)) {
                return SpectateConfig.Messages.class.getField(key).get(config.lang);
            }
            return null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            SpectateMod.LOGGER.warn("[Spectate] Missing config key: " + path);
            return null;
        }
    }

    public boolean setConfigValue(String path, String value) {
        try {
            String[] parts = path.split("\\.");
            if (parts.length < 2) {
                return false;
            }

            String category = parts[0];
            String key = parts[1];

            Object targetObject;
            java.lang.reflect.Field field;

            if ("settings".equals(category)) {
                targetObject = config.settings;
                field = SpectateConfig.Settings.class.getField(key);
            } else if ("lang".equals(category)) {
                targetObject = config.lang;
                field = SpectateConfig.Messages.class.getField(key);
            } else {
                return false;
            }

            Class<?> fieldType = field.getType();
            Object convertedValue;

            if (fieldType == int.class) {
                convertedValue = Integer.parseInt(value);
            } else if (fieldType == double.class) {
                convertedValue = Double.parseDouble(value);
            } else if (fieldType == float.class) {
                convertedValue = Float.parseFloat(value);
            } else if (fieldType == boolean.class) {
                convertedValue = Boolean.parseBoolean(value);
            } else if (fieldType == String.class) {
                convertedValue = value;
            } else {
                return false;
            }

            field.set(targetObject, convertedValue);

            // 保存配置到文件
            try (FileWriter writer = new FileWriter(configFile.toFile())) {
                GSON.toJson(config, writer);
                return true;
            }
        } catch (Exception e) {
            SpectateMod.LOGGER.warn("[Spectate] Failed to set config value: " + path + " = " + value, e);
            return false;
        }
    }
}