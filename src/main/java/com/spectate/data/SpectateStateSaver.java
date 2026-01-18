package com.spectate.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.spectate.SpectateMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpectateStateSaver 负责将观察点和循环列表持久化到磁盘。
 * 单例实现，线程安全。
 */
public class SpectateStateSaver {

    private static final String POINTS_FILE_NAME = "spectate_points.json";
    private static final String CYCLE_FILE_NAME = "cycle_lists.json";
    private static final String PLAYER_STATES_FILE_NAME = "player_spectate_states.json";
    private static final String PREFERENCES_FILE_NAME = "player_preferences.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SpectateStateSaver INSTANCE = new SpectateStateSaver();

    public static SpectateStateSaver getInstance() {
        return INSTANCE;
    }

    // 观察点缓存：名称 -> 数据
    private final Map<String, SpectatePointData> pointCache = new ConcurrentHashMap<>();
    // 玩家循环列表缓存：玩家UUID字符串 -> 点名称列表
    private final Map<String, List<String>> cycleCache = new ConcurrentHashMap<>();
    // 玩家观察状态缓存：玩家UUID字符串 -> 状态字符串
    private final Map<String, String> playerStateCache = new ConcurrentHashMap<>();
    // 玩家偏好缓存：玩家UUID字符串 -> 偏好对象
    private final Map<String, PlayerPreference> preferenceCache = new ConcurrentHashMap<>();

    private final Path pointsFile;
    private final Path cycleFile;
    private final Path playerStatesFile;
    private final Path preferencesFile;

    private SpectateStateSaver() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path spectateDir = configDir.resolve("spectate"); // 创建专用子目录
        try {
            Files.createDirectories(spectateDir);
        } catch (IOException e) {
            // 对于严重的初始化失败，记录日志并抛出运行时异常
            String errorMessage = "[Spectate] 创建配置目录失败: " + spectateDir;
            SpectateMod.LOGGER.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
        this.pointsFile = spectateDir.resolve(POINTS_FILE_NAME);
        this.cycleFile = spectateDir.resolve(CYCLE_FILE_NAME);
        this.playerStatesFile = spectateDir.resolve(PLAYER_STATES_FILE_NAME);
        this.preferencesFile = spectateDir.resolve(PREFERENCES_FILE_NAME);
    }

    /**
     * 初始化状态保存器，从磁盘加载所有数据。
     * 应在服务器启动时调用一次。
     */
    public void initialize() {
        try {
            loadPoints();
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] 从文件加载观察点失败: {}", pointsFile, e);
        }
        try {
            loadCycles();
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] 从文件加载循环列表失败: {}", cycleFile, e);
        }
        try {
            loadPlayerStates();
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] 从文件加载玩家状态失败: {}", playerStatesFile, e);
        }
        try {
            loadPreferences();
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] 从文件加载玩家偏好失败: {}", preferencesFile, e);
        }
    }

    /* ------------------- 观察点 ------------------- */

    /**
     * 添加一个新的观察点到缓存中，并可选择是否立即保存到磁盘。
     *
     * @param name 观察点的唯一名称。
     * @param data 包含观察点详细信息的 {@link SpectatePointData} 对象。
     * @param save 如果为 true，则在添加后立即保存所有点到磁盘。
     * @throws NullPointerException 如果 name 或 data 为 null。
     */
    public synchronized void addSpectatePoint(String name, SpectatePointData data, boolean save) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(data, "data");
        pointCache.put(name, data);
        if (save) {
            savePoints();
        }
    }

    /**
     * 添加一个新的观察点到缓存中，并立即保存到磁盘。
     *
     * @param name 观察点的唯一名称。
     * @param data 包含观察点详细信息的 {@link SpectatePointData} 对象。
     */
    public synchronized void addSpectatePoint(String name, SpectatePointData data) {
        addSpectatePoint(name, data, true);
    }

    /**
     * 从缓存中移除指定的观察点，并在成功移除后更新磁盘文件。
     *
     * @param name 要移除的观察点名称。
     * @return 如果存在并被移除，返回被移除的 {@link SpectatePointData}；否则返回 null。
     */
    public synchronized SpectatePointData removeSpectatePoint(String name) {
        SpectatePointData removed = pointCache.remove(name);
        if (removed != null) {
            savePoints();
        }
        return removed;
    }

    /**
     * 获取指定名称的观察点数据。
     *
     * @param name 观察点名称。
     * @return 对应的 {@link SpectatePointData} 对象，如果不存在则返回 null。
     */
    public SpectatePointData getSpectatePoint(String name) {
        return pointCache.get(name);
    }

    /**
     * 获取所有已注册观察点的名称列表。
     *
     * @return 包含所有点名称的不可变集合。
     */
    public Collection<String> listPointNames() {
        return Collections.unmodifiableSet(pointCache.keySet());
    }

    /* ------------------- 循环列表 ------------------- */
    
    /**
     * 设置玩家的个人循环观察列表，并立即保存到磁盘。
     *
     * @param playerUUID 玩家的 UUID。
     * @param list 观察点名称的列表。
     * @throws NullPointerException 如果 playerUUID 或 list 为 null。
     */
    public synchronized void setPlayerCycleList(UUID playerUUID, List<String> list) {
        Objects.requireNonNull(playerUUID);
        Objects.requireNonNull(list);
        cycleCache.put(playerUUID.toString(), new ArrayList<>(list));
        saveCycles();
    }

    /**
     * 获取玩家的个人循环观察列表。
     *
     * @param playerUUID 玩家的 UUID。
     * @return 观察点名称的列表，如果玩家没有设置过，则返回空列表。
     */
    public List<String> getPlayerCycleList(UUID playerUUID) {
        return cycleCache.getOrDefault(playerUUID.toString(), Collections.emptyList());
    }

    /* ------------------- 玩家状态 ------------------- */

    /**
     * 保存玩家在开始旁观前的状态，以便后续恢复。
     *
     * @param playerUUID 玩家的 UUID。
     * @param state 序列化后的状态字符串。
     */
    public synchronized void savePlayerState(UUID playerUUID, String state) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(state, "state");
        playerStateCache.put(playerUUID.toString(), state);
        savePlayerStates();
    }

    /**
     * 移除并返回玩家保存的状态。通常在玩家停止旁观时调用。
     *
     * @param playerUUID 玩家的 UUID。
     */
    public synchronized void removePlayerState(UUID playerUUID) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        if (playerStateCache.remove(playerUUID.toString()) != null) {
            savePlayerStates();
        }
    }

    /**
     * 获取玩家保存的状态。
     *
     * @param playerUUID 玩家的 UUID。
     * @return 状态字符串，如果不存在则返回 null。
     */
    public String getPlayerState(UUID playerUUID) {
        return playerStateCache.get(playerUUID.toString());
    }

    /* ------------------- 玩家偏好 ------------------- */

    /**
     * 获取玩家的偏好设置。如果不存在则返回默认值。
     *
     * @param playerUUID 玩家的 UUID。
     * @return 玩家的偏好对象。
     */
    public PlayerPreference getPlayerPreference(UUID playerUUID) {
        return preferenceCache.computeIfAbsent(playerUUID.toString(), k -> new PlayerPreference());
    }

    /**
     * 更新并保存玩家的偏好设置。
     *
     * @param playerUUID 玩家的 UUID。
     * @param preference 新的偏好对象。
     */
    public synchronized void savePlayerPreference(UUID playerUUID, PlayerPreference preference) {
        preferenceCache.put(playerUUID.toString(), preference);
        savePreferences();
    }

    /* ------------------- 内部加载 / 保存 ------------------- */

    private void loadPoints() throws IOException {
        if (Files.notExists(pointsFile)) {
            createDefaultPoint();
            return;
        }
        try (FileReader reader = new FileReader(pointsFile.toFile())) {
            Type type = new TypeToken<Map<String, SpectatePointData>>() {}.getType();
            Map<String, SpectatePointData> loadedPoints = GSON.fromJson(reader, type);
            if (loadedPoints != null) {
                pointCache.putAll(loadedPoints);
            }
        }
        if (pointCache.isEmpty()) {
            createDefaultPoint();
        }
    }

    private void savePoints() {
        try {
            Files.createDirectories(pointsFile.getParent());
            try (FileWriter writer = new FileWriter(pointsFile.toFile())) {
                GSON.toJson(pointCache, writer);
            }
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] Failed to save spectate points.", e);
        }
    }

    private void loadCycles() throws IOException {
        if (Files.notExists(cycleFile)) {
            return;
        }
        try (FileReader reader = new FileReader(cycleFile.toFile())) {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> loadedCycles = GSON.fromJson(reader, type);
            if (loadedCycles != null) {
                cycleCache.putAll(loadedCycles);
            }
        }
    }

    private void saveCycles() {
        try {
            Files.createDirectories(cycleFile.getParent());
            try (FileWriter writer = new FileWriter(cycleFile.toFile())) {
                GSON.toJson(cycleCache, writer);
            }
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] Failed to save cycle lists.", e);
        }
    }

    private void loadPlayerStates() throws IOException {
        if (Files.notExists(playerStatesFile)) {
            return;
        }
        try (FileReader reader = new FileReader(playerStatesFile.toFile())) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loadedStates = GSON.fromJson(reader, type);
            if (loadedStates != null) {
                playerStateCache.putAll(loadedStates);
            }
        }
    }

    private void savePlayerStates() {
        try {
            Files.createDirectories(playerStatesFile.getParent());
            try (FileWriter writer = new FileWriter(playerStatesFile.toFile())) {
                GSON.toJson(playerStateCache, writer);
            }
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] Failed to save player states.", e);
        }
    }

    private void loadPreferences() throws IOException {
        if (Files.notExists(preferencesFile)) {
            return;
        }
        try (FileReader reader = new FileReader(preferencesFile.toFile())) {
            Type type = new TypeToken<Map<String, PlayerPreference>>() {}.getType();
            Map<String, PlayerPreference> loadedPreferences = GSON.fromJson(reader, type);
            if (loadedPreferences != null) {
                preferenceCache.putAll(loadedPreferences);
            }
        }
    }

    private void savePreferences() {
        try {
            Files.createDirectories(preferencesFile.getParent());
            try (FileWriter writer = new FileWriter(preferencesFile.toFile())) {
                GSON.toJson(preferenceCache, writer);
            }
        } catch (IOException e) {
            SpectateMod.LOGGER.error("[Spectate] Failed to save player preferences.", e);
        }
    }

    /* ------------------- 默认点 ------------------- */
    private void createDefaultPoint() {
        addSpectatePoint("origin", new SpectatePointData("minecraft:overworld", BlockPos.ORIGIN, 10.0, 3.0, "(auto) world spawn"));
    }
}
