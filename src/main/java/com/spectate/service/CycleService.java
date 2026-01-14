package com.spectate.service;

import com.spectate.SpectateMod;
import com.spectate.config.ConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * CycleService 负责管理所有与循环观察相关的逻辑，
 * 包括循环列表的管理、任务调度和状态切换。
 */
public class CycleService {

    private static final CycleService INSTANCE = new CycleService();
    public static CycleService getInstance() { return INSTANCE; }

    private final Map<UUID, PlayerCycleSession> cycleSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "SpectateCycleScheduler"));
    private final ConfigManager configManager = ConfigManager.getInstance();

    private CycleService() {}


    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    private static class PlayerCycleSession {
        private final List<String> pointList;
        private int index;
        private long intervalSeconds;
        private ScheduledFuture<?> future;
        private boolean running;
        private ViewMode viewMode;
        private CinematicMode cinematicMode;

        // 自动添加所有玩家相关
        private boolean autoAddAllPlayers;
        private String excludePrefix;
        private String excludeSuffix;

        PlayerCycleSession(ConfigManager configManager) {
            this.pointList = new ArrayList<>();
            this.intervalSeconds = configManager.getConfig().settings.cycle_interval_seconds;
            this.index = 0;
            this.running = false;
            this.viewMode = ViewMode.ORBIT; // 默认环绕模式
            this.cinematicMode = null;
            this.autoAddAllPlayers = false;
            this.excludePrefix = null;
            this.excludeSuffix = null;
        }

        void addPoint(String pointName) {
            if (!pointList.contains(pointName)) {
                pointList.add(pointName);
            }
        }

        void removePoint(String pointName) {
            pointList.remove(pointName);
        }

        void clearPoints() {
            pointList.clear();
        }

        List<String> getPoints() {
            return Collections.unmodifiableList(pointList);
        }

        boolean isEmpty() {
            return pointList.isEmpty();
        }

        void setInterval(long seconds) {
            this.intervalSeconds = Math.max(1, seconds); // Minimum 1 second
        }

        void start() {
            this.running = true;
            this.index = 0;
        }
        
        void stop() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
            running = false;
        }

        void setViewMode(ViewMode viewMode, CinematicMode cinematicMode) {
            this.viewMode = viewMode != null ? viewMode : ViewMode.ORBIT;
            this.cinematicMode = cinematicMode;
        }

        ViewMode getViewMode() {
            return viewMode;
        }

        CinematicMode getCinematicMode() {
            return cinematicMode;
        }

        void setAutoAddAllPlayers(boolean enabled, String excludePrefix, String excludeSuffix) {
            this.autoAddAllPlayers = enabled;
            this.excludePrefix = excludePrefix;
            this.excludeSuffix = excludeSuffix;
        }

        boolean isAutoAddAllPlayers() {
            return autoAddAllPlayers;
        }

        boolean shouldIncludePlayer(String playerName) {
            if (excludePrefix != null && playerName.startsWith(excludePrefix)) {
                return false;
            }
            if (excludeSuffix != null && playerName.endsWith(excludeSuffix)) {
                return false;
            }
            return true;
        }

        String getExcludePrefix() {
            return excludePrefix;
        }

        String getExcludeSuffix() {
            return excludeSuffix;
        }
    }

    private PlayerCycleSession getOrCreateSession(UUID playerId) {
        return cycleSessions.computeIfAbsent(playerId, k -> new PlayerCycleSession(configManager));
    }

    public void addCyclePoint(ServerPlayerEntity player, String pointName) {
        getOrCreateSession(player.getUuid()).addPoint(pointName);
        player.sendMessage(configManager.getFormattedMessage("cycle_point_added", Map.of("name", pointName)), false);
    }

    public void removeCyclePoint(ServerPlayerEntity player, String pointName) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null) {
            boolean wasRunning = session.running;
            if (wasRunning) {
                stopCycle(player); // Stop the current task
            }

            session.removePoint(pointName);
            player.sendMessage(configManager.getFormattedMessage("cycle_point_removed", Map.of("name", pointName)), false);

            if (wasRunning && !session.isEmpty()) {
                startCycle(player); // Restart with the modified list
            }
        } else {
            player.sendMessage(configManager.getMessage("cycle_list_empty"), false);
        }
    }

    /**
     * 静默移除循环点（不发送消息，不重启循环）。
     * 用于在循环过程中自动移除不在线的玩家。
     */
    public void removeCyclePointSilent(UUID playerId, String pointName) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        if (session != null) {
            session.removePoint(pointName);
            // 调整索引，防止越界
            if (session.index >= session.pointList.size() && !session.pointList.isEmpty()) {
                session.index = 0;
            }
        }
    }

    public void clearCyclePoints(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null) {
            session.clearPoints();
            player.sendMessage(configManager.getMessage("cycle_cleared"), false);
        } else {
            player.sendMessage(configManager.getMessage("cycle_list_empty"), false);
        }
    }

    public List<String> listCyclePoints(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        return session != null ? session.getPoints() : Collections.emptyList();
    }

    public void setCycleInterval(ServerPlayerEntity player, long intervalSeconds) {
        getOrCreateSession(player.getUuid()).setInterval(intervalSeconds);
        player.sendMessage(configManager.getFormattedMessage("cycle_interval_set", Map.of("seconds", String.valueOf(intervalSeconds))), false);
    }

    public void startCycle(ServerPlayerEntity player) {
        startCycle(player, ViewMode.ORBIT, null);
    }

    public void startCycle(ServerPlayerEntity player, ViewMode viewMode, CinematicMode cinematicMode) {
        PlayerCycleSession session = getOrCreateSession(player.getUuid());
        if (session.isEmpty()) {
            player.sendMessage(configManager.getMessage("cycle_list_empty"), false);
            return;
        }

        if (session.running) {
            session.stop();
        }
        
        // 设置视角模式
        session.setViewMode(viewMode, cinematicMode);
        session.start();
        
        // Announce start with mode info
        String modeMessage = getViewModeMessage(viewMode, cinematicMode);
        player.sendMessage(configManager.getFormattedMessage("cycle_started_with_mode", 
            Map.of("mode", modeMessage)), false);

        // Switch to the first point immediately
        ServerSpectateManager.getInstance().switchToCyclePoint(player);

        // Schedule subsequent switches
        session.future = scheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = SpectateMod.getServer();
            if (server == null) return;
            server.execute(() -> {
                ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(player.getUuid());
                if (onlinePlayer != null && isCycling(onlinePlayer.getUuid())) {
                    nextCyclePoint(onlinePlayer, true); // Auto-switch
                }
            });
        }, session.intervalSeconds, session.intervalSeconds, TimeUnit.SECONDS);
    }

    public void stopCycle(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null && session.running) {
            session.stop();
            // The actual restoration of player state is handled by ServerSpectateManager
        }
    }

    public void nextCyclePoint(ServerPlayerEntity player, boolean isAuto) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null || !session.running || session.isEmpty()) {
            if (!isAuto) {
                player.sendMessage(configManager.getMessage("cycle_not_running"), false);
            }
            return;
        }

        session.index = (session.index + 1) % session.pointList.size();
        ServerSpectateManager.getInstance().switchToCyclePoint(player);

        if (!isAuto) {
            player.sendMessage(configManager.getFormattedMessage("cycle_next_point", Map.of(
                "index", String.valueOf(session.index + 1),
                "total", String.valueOf(session.pointList.size())
            )), false);
        }
    }

    public boolean isCycling(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        return session != null && session.running;
    }

    public String getCurrentCyclePointName(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        if (session == null || session.isEmpty()) {
            return null;
        }
        return session.pointList.get(session.index);
    }

    public ViewMode getCurrentViewMode(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        return session != null ? session.getViewMode() : ViewMode.ORBIT;
    }

    public CinematicMode getCurrentCinematicMode(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        return session != null ? session.getCinematicMode() : null;
    }

    private String getViewModeMessage(ViewMode viewMode, CinematicMode cinematicMode) {
        switch (viewMode) {
            case ORBIT:
                return "环绕模式";
            case FOLLOW:
                return "跟随模式";
            case CINEMATIC:
                if (cinematicMode != null) {
                    switch (cinematicMode) {
                        case SLOW_ORBIT: return "电影模式 - 慢速环绕";
                        case AERIAL_VIEW: return "电影模式 - 高空俯瞰";
                        case SPIRAL_UP: return "电影模式 - 螺旋上升";
                        case FLOATING: return "电影模式 - 浮游视角";
                        default: return "电影模式";
                    }
                }
                return "电影模式";
            default:
                return "普通模式";
        }
    }

    /* ------------------- Auto Add All Players ------------------- */

    /**
     * 启用自动添加所有玩家到循环列表的功能。
     * @param player 执行命令的玩家
     * @param excludePrefix 排除的前缀（可为null）
     * @param excludeSuffix 排除的后缀（可为null）
     */
    public void enableAutoAddAllPlayers(ServerPlayerEntity player, String excludePrefix, String excludeSuffix) {
        PlayerCycleSession session = getOrCreateSession(player.getUuid());
        session.setAutoAddAllPlayers(true, excludePrefix, excludeSuffix);

        // 添加当前在线的所有玩家（排除自己）
        MinecraftServer server = SpectateMod.getServer();
        if (server != null) {
            for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
                if (!onlinePlayer.getUuid().equals(player.getUuid())) {
                    String playerName = onlinePlayer.getName().getString();
                    if (session.shouldIncludePlayer(playerName)) {
                        String pointName = "player_" + playerName;
                        session.addPoint(pointName);
                    }
                }
            }
        }

        // 构建消息
        StringBuilder msg = new StringBuilder("已启用自动添加所有玩家");
        if (excludePrefix != null || excludeSuffix != null) {
            msg.append(" (排除: ");
            if (excludePrefix != null) {
                msg.append("前缀 '").append(excludePrefix).append("'");
            }
            if (excludePrefix != null && excludeSuffix != null) {
                msg.append(", ");
            }
            if (excludeSuffix != null) {
                msg.append("后缀 '").append(excludeSuffix).append("'");
            }
            msg.append(")");
        }

        player.sendMessage(Text.literal("§a[Spectate] " + msg.toString()), false);
        player.sendMessage(Text.literal("§7当前循环列表中有 " + session.getPoints().size() + " 个目标"), false);
    }

    /**
     * 禁用自动添加所有玩家功能。
     */
    public void disableAutoAddAllPlayers(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null) {
            session.setAutoAddAllPlayers(false, null, null);
            player.sendMessage(Text.literal("§a[Spectate] 已禁用自动添加所有玩家"), false);
        }
    }

    /**
     * 当新玩家加入服务器时调用，检查是否需要自动添加到某些玩家的循环列表中。
     */
    public void onPlayerJoin(ServerPlayerEntity joinedPlayer) {
        String joinedName = joinedPlayer.getName().getString();

        for (Map.Entry<UUID, PlayerCycleSession> entry : cycleSessions.entrySet()) {
            PlayerCycleSession session = entry.getValue();
            if (session.isAutoAddAllPlayers() && !entry.getKey().equals(joinedPlayer.getUuid())) {
                if (session.shouldIncludePlayer(joinedName)) {
                    String pointName = "player_" + joinedName;
                    session.addPoint(pointName);
                }
            }
        }
    }

    /**
     * 当玩家离开服务器时调用，从循环列表中移除该玩家。
     */
    public void onPlayerLeave(ServerPlayerEntity leftPlayer) {
        String leftName = leftPlayer.getName().getString();
        String pointName = "player_" + leftName;

        for (PlayerCycleSession session : cycleSessions.values()) {
            if (session.pointList.contains(pointName)) {
                session.removePoint(pointName);
            }
        }
    }

    /**
     * 检查玩家是否启用了自动添加所有玩家功能。
     */
    public boolean isAutoAddAllPlayersEnabled(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        return session != null && session.isAutoAddAllPlayers();
    }
}
