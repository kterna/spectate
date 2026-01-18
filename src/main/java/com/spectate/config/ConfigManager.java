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

    /**
     * 获取全局 ConfigManager 实例。
     * @return 单例实例。
     */
    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取当前的配置对象。
     * @return 包含所有配置数据的 {@link SpectateConfig} 对象。
     */
    public SpectateConfig getConfig() {
        return config;
    }

    /**
     * 从磁盘重新加载配置文件。
     * 如果文件不存在或损坏，将尝试创建默认配置或修复。
     */
    public void reloadConfig() {
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (Files.notExists(configFile)) {
                SpectateMod.LOGGER.info("[Spectate] 未找到配置文件，正在创建默认配置。");
                createDefaultConfig();
            }
            try (FileReader reader = new FileReader(configFile.toFile())) {
                config = GSON.fromJson(reader, SpectateConfig.class);
            }

            if (config == null) {
                SpectateMod.LOGGER.warn("[Spectate] 配置文件为空或损坏，正在创建一个新的。");
                createDefaultConfig();
            }
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] 加载或创建配置文件失败。", e);
            // 如果所有方法都失败，使用内存中的默认配置
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
    
    /**
     * 获取指定消息键的消息文本。
     * 这是一个简便方法，相当于调用 {@code getFormattedMessage(messageKey, null)}。
     *
     * @param messageKey 消息的键，来自 {@link SpectateConfig.Messages}。
     * @return 对应的 Text 对象。
     */
    public Text getMessage(String messageKey) {
        return getFormattedMessage(messageKey, null);
    }

    private String getMessageTemplate(String key) {
        try {
            // 使用反射从配置对象中获取消息
            return (String) SpectateConfig.Messages.class.getField(key).get(config.lang);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            SpectateMod.LOGGER.warn("[Spectate] 配置中缺少消息键: " + key);
            return "Missing message: " + key;
        }
    }

    /**
     * 使用反射获取配置值。支持 "category.key" 格式的路径。
     *
     * @param path 配置路径，例如 "settings.cycle_interval_seconds"。
     * @return 配置值对象，如果路径无效则返回 null。
     */
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

    /**
     * 使用反射设置配置值，并保存到文件。
     * 支持自动类型转换（String -> int/double/boolean）。
     *
     * @param path 配置路径，例如 "settings.cycle_interval_seconds"。
     * @param value 新的配置值（字符串形式）。
     * @return 如果设置成功并保存，则返回 true；否则返回 false。
     */
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