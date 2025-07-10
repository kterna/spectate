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

    private final Path pointsFile;
    private final Path cycleFile;
    private final Path playerStatesFile;

    private SpectateStateSaver() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path spectateDir = configDir.resolve("spectate"); // Create a dedicated subdirectory
        try {
            Files.createDirectories(spectateDir);
        } catch (IOException e) {
            System.err.println("[Spectate] Failed to create config directory: " + spectateDir);
            e.printStackTrace();
        }
        this.pointsFile = spectateDir.resolve(POINTS_FILE_NAME);
        this.cycleFile = spectateDir.resolve(CYCLE_FILE_NAME);
        this.playerStatesFile = spectateDir.resolve(PLAYER_STATES_FILE_NAME);
    }

    /**
     * Initializes the state saver by loading all data from disk.
     * This should be called once when the server starts.
     */
    public void initialize() {
        try {
            loadPoints();
        } catch (IOException e) {
            System.err.println("[Spectate] Failed to load spectate points.");
            e.printStackTrace();
        }
        try {
            loadCycles();
        } catch (IOException e) {
            System.err.println("[Spectate] Failed to load cycle lists.");
            e.printStackTrace();
        }
        try {
            loadPlayerStates();
        } catch (IOException e) {
            System.err.println("[Spectate] Failed to load player states.");
            e.printStackTrace();
        }
    }

    /* ------------------- 观察点 ------------------- */

    public synchronized void addSpectatePoint(String name, SpectatePointData data, boolean save) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(data, "data");
        pointCache.put(name, data);
        if (save) {
            savePoints();
        }
    }

    public synchronized void addSpectatePoint(String name, SpectatePointData data) {
        addSpectatePoint(name, data, true);
    }

    public synchronized SpectatePointData removeSpectatePoint(String name) {
        SpectatePointData removed = pointCache.remove(name);
        if (removed != null) {
            savePoints();
        }
        return removed;
    }

    public SpectatePointData getSpectatePoint(String name) {
        return pointCache.get(name);
    }

    public Collection<String> listPointNames() {
        return Collections.unmodifiableSet(pointCache.keySet());
    }

    /* ------------------- 循环列表 ------------------- */
    public synchronized void setPlayerCycleList(UUID playerUUID, List<String> list) {
        Objects.requireNonNull(playerUUID);
        Objects.requireNonNull(list);
        cycleCache.put(playerUUID.toString(), new ArrayList<>(list));
        saveCycles();
    }

    public List<String> getPlayerCycleList(UUID playerUUID) {
        return cycleCache.getOrDefault(playerUUID.toString(), Collections.emptyList());
    }

    /* ------------------- 玩家状态 ------------------- */

    public synchronized void savePlayerState(UUID playerUUID, String state) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(state, "state");
        playerStateCache.put(playerUUID.toString(), state);
        savePlayerStates();
    }

    public synchronized void removePlayerState(UUID playerUUID) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        if (playerStateCache.remove(playerUUID.toString()) != null) {
            savePlayerStates();
        }
    }

    public String getPlayerState(UUID playerUUID) {
        return playerStateCache.get(playerUUID.toString());
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

    /* ------------------- 默认点 ------------------- */
    private void createDefaultPoint() {
        addSpectatePoint("origin", new SpectatePointData(BlockPos.ORIGIN, 10.0, 3.0, "(auto) world spawn"));
    }
}
