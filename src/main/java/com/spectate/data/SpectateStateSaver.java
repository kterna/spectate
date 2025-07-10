package com.spectate.data;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SpectateStateSaver 负责将观察点和循环列表持久化到磁盘。
 * 单例实现，线程安全。
 */
public class SpectateStateSaver {

    private static final String POINTS_FILE_NAME = "spectate_points.properties";
    private static final String CYCLE_FILE_NAME = "cycle_lists.properties";
    private static final String PLAYER_STATES_FILE_NAME = "player_spectate_states.properties";

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
        this.pointsFile = configDir.resolve(POINTS_FILE_NAME);
        this.cycleFile = configDir.resolve(CYCLE_FILE_NAME);
        this.playerStatesFile = configDir.resolve(PLAYER_STATES_FILE_NAME);
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
        if (!Files.exists(pointsFile)) {
            createDefaultPoint();
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(pointsFile.toFile())) {
            props.load(fis);
        }
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            SpectatePointData data = parsePoint(value);
            if (data != null) {
                pointCache.put(key, data);
            }
        }
        if (pointCache.isEmpty()) {
            createDefaultPoint();
        }
    }

    private void savePoints() {
        Properties props = new Properties();
        for (Map.Entry<String, SpectatePointData> entry : pointCache.entrySet()) {
            props.setProperty(entry.getKey(), serializePoint(entry.getValue()));
        }
        try {
            Files.createDirectories(pointsFile.getParent());
            try (FileOutputStream fos = new FileOutputStream(pointsFile.toFile())) {
                props.store(fos, "Spectate Points");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadCycles() throws IOException {
        if (!Files.exists(cycleFile)) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(cycleFile.toFile())) {
            props.load(fis);
        }
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            List<String> list = Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            cycleCache.put(key, list);
        }
    }

    private void saveCycles() {
        Properties props = new Properties();
        for (Map.Entry<String, List<String>> entry : cycleCache.entrySet()) {
            String joined = String.join(",", entry.getValue());
            props.setProperty(entry.getKey(), joined);
        }
        try {
            Files.createDirectories(cycleFile.getParent());
            try (FileOutputStream fos = new FileOutputStream(cycleFile.toFile())) {
                props.store(fos, "Cycle Lists");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPlayerStates() throws IOException {
        if (!Files.exists(playerStatesFile)) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(playerStatesFile.toFile())) {
            props.load(fis);
        }
        for (String key : props.stringPropertyNames()) {
            playerStateCache.put(key, props.getProperty(key));
        }
    }

    private void savePlayerStates() {
        Properties props = new Properties();
        for (Map.Entry<String, String> entry : playerStateCache.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        try {
            Files.createDirectories(playerStatesFile.getParent());
            try (FileOutputStream fos = new FileOutputStream(playerStatesFile.toFile())) {
                props.store(fos, "Player Spectate States");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* ------------------- 序列化帮助 ------------------- */

    private static String serializePoint(SpectatePointData data) {
        BlockPos pos = data.getPosition();
        return String.format(Locale.ROOT, "%d,%d,%d,%.2f,%.2f,%s",
                pos.getX(), pos.getY(), pos.getZ(),
                data.getDistance(), data.getHeightOffset(),
                escape(data.getDescription()));
    }

    private static SpectatePointData parsePoint(String str) {
        try {
            String[] parts = str.split(",", 6); // description 可能包含逗号，限制分割次数
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            double dist = Double.parseDouble(parts[3]);
            double h = Double.parseDouble(parts[4]);
            String desc = unescape(parts.length >= 6 ? parts[5] : "");
            return new SpectatePointData(new BlockPos(x, y, z), dist, h, desc);
        } catch (Exception e) {
            System.err.println("[Spectate] Failed to parse spectate point: " + str);
            return null;
        }
    }

    /* ------------------- 默认点 ------------------- */
    private void createDefaultPoint() {
        addSpectatePoint("origin", new SpectatePointData(BlockPos.ORIGIN, 10.0, 3.0, "(auto) world spawn"));
    }

    /* ------------------- 文本转义简单实现 ------------------- */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(",", "\\,");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (char c : s.toCharArray()) {
            if (escaping) {
                sb.append(c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
