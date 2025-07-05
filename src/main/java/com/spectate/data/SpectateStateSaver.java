package com.spectate.data;

import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class SpectateStateSaver {
    private static SpectateStateSaver instance;
    private static final String DATA_DIR = "config/spectate_data";
    private static final String PLAYER_DATA_FILE = "player_states.properties";
    private static final String SPECTATE_POINTS_FILE = "spectate_points.properties";
    private static final String CYCLE_LISTS_FILE = "cycle_lists.properties";
    
    // 玩家状态数据
    public HashMap<UUID, PlayerData> players = new HashMap<>();
    
    // 观察点数据
    public HashMap<String, SpectatePointData> spectatePoints = new HashMap<>();
    
    // 玩家循环列表数据 (UUID -> 观察点名称列表)
    public HashMap<UUID, List<String>> playerCycleLists = new HashMap<>();
    
    // 玩家循环配置数据 (UUID -> 观察时长(秒))
    public HashMap<UUID, Integer> playerCycleConfigs = new HashMap<>();

    private SpectateStateSaver() {
        createDataDirectory();
        loadAllData();
        createDefaultPointsIfNeeded();
    }

    public static SpectateStateSaver getInstance() {
        if (instance == null) {
            instance = new SpectateStateSaver();
        }
        return instance;
    }

    private void createDataDirectory() {
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    private void createDefaultPointsIfNeeded() {
        if (spectatePoints.isEmpty()) {
            // 添加一些示例观察点            
            SpectatePointData origin = new SpectatePointData(
                new Vec3(0, 64, 0), 8.0f, 3.0f, "世界原点"
            );
            spectatePoints.put("origin", origin);
            
            saveSpectatePoints(); // 保存默认观察点
        }
    }

    // 加载所有数据
    private void loadAllData() {
        loadPlayerStates();
        loadSpectatePoints();
        loadCycleLists();
    }

    // 保存所有数据
    public void saveAllData() {
        savePlayerStates();
        saveSpectatePoints();
        saveCycleLists();
    }

    // 玩家状态持久化
    private void loadPlayerStates() {
        File file = new File(DATA_DIR, PLAYER_DATA_FILE);
        if (!file.exists()) {
            return;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            
            for (String key : props.stringPropertyNames()) {
                if (key.endsWith(".gameMode")) {
                    String uuidStr = key.substring(0, key.lastIndexOf(".gameMode"));
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        
                        String gameMode = props.getProperty(uuidStr + ".gameMode", "SURVIVAL");
                        double posX = Double.parseDouble(props.getProperty(uuidStr + ".posX", "0"));
                        double posY = Double.parseDouble(props.getProperty(uuidStr + ".posY", "0"));
                        double posZ = Double.parseDouble(props.getProperty(uuidStr + ".posZ", "0"));
                        float yaw = Float.parseFloat(props.getProperty(uuidStr + ".yaw", "0"));
                        float pitch = Float.parseFloat(props.getProperty(uuidStr + ".pitch", "0"));
                        
                        // 使用GameType.byName()来安全地解析游戏模式，兼容大小写
                        GameType gameTypeEnum = GameType.byName(gameMode, GameType.SURVIVAL);
                        
                        PlayerData playerData = new PlayerData(
                            gameTypeEnum,
                            new Vec3(posX, posY, posZ),
                            yaw,
                            pitch
                        );
                        
                        players.put(uuid, playerData);
                    } catch (Exception e) {
                        System.err.println("加载玩家状态时出错 " + uuidStr + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("读取玩家状态文件失败: " + e.getMessage());
        }
    }

    private void savePlayerStates() {
        File file = new File(DATA_DIR, PLAYER_DATA_FILE);
        Properties props = new Properties();
        
        for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
            String uuidStr = entry.getKey().toString();
            PlayerData data = entry.getValue();
            
            // 保存为大写格式，便于后续解析
            props.setProperty(uuidStr + ".gameMode", data.gameMode.name());
            props.setProperty(uuidStr + ".posX", String.valueOf(data.position.x));
            props.setProperty(uuidStr + ".posY", String.valueOf(data.position.y));
            props.setProperty(uuidStr + ".posZ", String.valueOf(data.position.z));
            props.setProperty(uuidStr + ".yaw", String.valueOf(data.yaw));
            props.setProperty(uuidStr + ".pitch", String.valueOf(data.pitch));
        }
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "Spectate Mod - 玩家状态数据");
        } catch (IOException e) {
            System.err.println("保存玩家状态文件失败: " + e.getMessage());
        }
    }

    // 观察点持久化
    private void loadSpectatePoints() {
        File file = new File(DATA_DIR, SPECTATE_POINTS_FILE);
        if (!file.exists()) {
            return;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            
            for (String key : props.stringPropertyNames()) {
                if (key.endsWith(".posX")) {
                    String pointName = key.substring(0, key.lastIndexOf(".posX"));
                    try {
                        double posX = Double.parseDouble(props.getProperty(pointName + ".posX", "0"));
                        double posY = Double.parseDouble(props.getProperty(pointName + ".posY", "0"));
                        double posZ = Double.parseDouble(props.getProperty(pointName + ".posZ", "0"));
                        float distance = Float.parseFloat(props.getProperty(pointName + ".distance", "50"));
                        float heightOffset = Float.parseFloat(props.getProperty(pointName + ".heightOffset", "0"));
                        String description = props.getProperty(pointName + ".description", pointName);
                        
                        SpectatePointData pointData = new SpectatePointData(
                            new Vec3(posX, posY, posZ),
                            distance,
                            heightOffset,
                            description
                        );
                        
                        spectatePoints.put(pointName.toLowerCase(), pointData);
                    } catch (Exception e) {
                        System.err.println("加载观察点时出错 " + pointName + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("读取观察点文件失败: " + e.getMessage());
        }
    }

    private void saveSpectatePoints() {
        File file = new File(DATA_DIR, SPECTATE_POINTS_FILE);
        Properties props = new Properties();
        
        for (Map.Entry<String, SpectatePointData> entry : spectatePoints.entrySet()) {
            String pointName = entry.getKey();
            SpectatePointData data = entry.getValue();
            
            props.setProperty(pointName + ".posX", String.valueOf(data.position.x));
            props.setProperty(pointName + ".posY", String.valueOf(data.position.y));
            props.setProperty(pointName + ".posZ", String.valueOf(data.position.z));
            props.setProperty(pointName + ".distance", String.valueOf(data.distance));
            props.setProperty(pointName + ".heightOffset", String.valueOf(data.heightOffset));
            props.setProperty(pointName + ".description", data.description);
        }
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "Spectate Mod - 观察点数据");
        } catch (IOException e) {
            System.err.println("保存观察点文件失败: " + e.getMessage());
        }
    }

    // 玩家数据访问方法
    public PlayerData getPlayerState(UUID playerUUID) {
        return players.computeIfAbsent(playerUUID, uuid -> new PlayerData());
    }

    public void setPlayerState(UUID playerUUID, PlayerData playerData) {
        players.put(playerUUID, playerData);
        savePlayerStates(); // 立即保存
    }

    public void clearPlayerState(UUID playerUUID) {
        players.remove(playerUUID);
        savePlayerStates(); // 立即保存
    }

    // 检查玩家是否有未完成的观察任务
    public boolean hasPlayerState(UUID playerUUID) {
        return players.containsKey(playerUUID);
    }

    // 观察点数据访问方法
    public SpectatePointData getSpectatePoint(String name) {
        return spectatePoints.get(name.toLowerCase());
    }

    public boolean addSpectatePoint(String name, SpectatePointData pointData) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        spectatePoints.put(name.toLowerCase(), pointData);
        saveSpectatePoints(); // 立即保存
        return true;
    }

    public boolean removeSpectatePoint(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        boolean removed = spectatePoints.remove(name.toLowerCase()) != null;
        if (removed) {
            saveSpectatePoints(); // 立即保存
        }
        return removed;
    }

    public Map<String, SpectatePointData> getAllSpectatePoints() {
        return new HashMap<>(spectatePoints);
    }

    public boolean hasSpectatePoint(String name) {
        return spectatePoints.containsKey(name.toLowerCase());
    }

    // 获取所有有未完成观察任务的玩家UUID
    public Map<UUID, PlayerData> getAllPendingPlayerStates() {
        return new HashMap<>(players);
    }

    // ========== 循环列表持久化 ==========
    
    // 加载循环列表数据
    private void loadCycleLists() {
        File file = new File(DATA_DIR, CYCLE_LISTS_FILE);
        if (!file.exists()) {
            return;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            
            for (String key : props.stringPropertyNames()) {
                if (key.endsWith(".points")) {
                    String uuidStr = key.substring(0, key.lastIndexOf(".points"));
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        
                        String pointsStr = props.getProperty(uuidStr + ".points", "");
                        int watchDuration = Integer.parseInt(props.getProperty(uuidStr + ".duration", "600"));
                        
                        List<String> pointNames = new ArrayList<>();
                        if (!pointsStr.trim().isEmpty()) {
                            String[] points = pointsStr.split(",");
                            for (String point : points) {
                                String trimmed = point.trim();
                                if (!trimmed.isEmpty()) {
                                    pointNames.add(trimmed);
                                }
                            }
                        }
                        
                        if (!pointNames.isEmpty()) {
                            playerCycleLists.put(uuid, pointNames);
                        }
                        playerCycleConfigs.put(uuid, watchDuration);
                        
                    } catch (Exception e) {
                        System.err.println("加载玩家循环列表时出错 " + uuidStr + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("读取循环列表文件失败: " + e.getMessage());
        }
    }

    // 保存循环列表数据
    private void saveCycleLists() {
        File file = new File(DATA_DIR, CYCLE_LISTS_FILE);
        Properties props = new Properties();
        
        // 保存所有玩家的循环列表
        for (Map.Entry<UUID, List<String>> entry : playerCycleLists.entrySet()) {
            String uuidStr = entry.getKey().toString();
            List<String> pointNames = entry.getValue();
            int duration = playerCycleConfigs.getOrDefault(entry.getKey(), 600);
            
            // 将观察点列表转为逗号分隔的字符串
            String pointsStr = String.join(",", pointNames);
            props.setProperty(uuidStr + ".points", pointsStr);
            props.setProperty(uuidStr + ".duration", String.valueOf(duration));
        }
        
        // 保存只有配置但没有观察点的玩家
        for (Map.Entry<UUID, Integer> entry : playerCycleConfigs.entrySet()) {
            String uuidStr = entry.getKey().toString();
            if (!playerCycleLists.containsKey(entry.getKey())) {
                props.setProperty(uuidStr + ".points", "");
                props.setProperty(uuidStr + ".duration", String.valueOf(entry.getValue()));
            }
        }
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "Spectate Mod - 玩家循环列表数据");
        } catch (IOException e) {
            System.err.println("保存循环列表文件失败: " + e.getMessage());
        }
    }

    // ========== 循环列表数据访问方法 ==========
    
    public List<String> getPlayerCycleList(UUID playerUUID) {
        return playerCycleLists.getOrDefault(playerUUID, new ArrayList<>());
    }

    public void setPlayerCycleList(UUID playerUUID, List<String> pointNames) {
        if (pointNames == null || pointNames.isEmpty()) {
            playerCycleLists.remove(playerUUID);
        } else {
            playerCycleLists.put(playerUUID, new ArrayList<>(pointNames));
        }
        saveCycleLists(); // 立即保存
    }

    public int getPlayerCycleDuration(UUID playerUUID) {
        return playerCycleConfigs.getOrDefault(playerUUID, 600); // 默认10分钟
    }

    public void setPlayerCycleDuration(UUID playerUUID, int durationSeconds) {
        playerCycleConfigs.put(playerUUID, durationSeconds);
        saveCycleLists(); // 立即保存
    }

    public boolean addPointToCycle(UUID playerUUID, String pointName) {
        List<String> currentList = getPlayerCycleList(playerUUID);
        if (!currentList.contains(pointName)) {
            currentList.add(pointName);
            setPlayerCycleList(playerUUID, currentList);
            return true;
        }
        return false;
    }

    public boolean removePointFromCycle(UUID playerUUID, String pointName) {
        List<String> currentList = getPlayerCycleList(playerUUID);
        boolean removed = currentList.remove(pointName);
        if (removed) {
            setPlayerCycleList(playerUUID, currentList);
        }
        return removed;
    }

    public void clearPlayerCycle(UUID playerUUID) {
        playerCycleLists.remove(playerUUID);
        // 保留时长配置，只清除观察点列表
        saveCycleLists();
    }

    // 清理并保存数据（在程序关闭时调用）
    public void shutdown() {
        saveAllData();
    }
} 