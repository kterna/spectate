package com.spectate.service;

import com.spectate.SpectateMod;
import com.spectate.config.ConfigManager;
import com.spectate.data.PlayerPreference;
import com.spectate.data.SpectateStateSaver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
//#if MC >= 11900
import net.minecraft.text.Text;
//#else
//$$import net.minecraft.text.LiteralText;
//$$import net.minecraft.text.Text;
//#endif

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


    /**
     * 获取用于调度循环任务的线程池。
     * @return 调度执行器服务
     */
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
        private volatile long currentPointStartTime; // 记录当前观察点开始的时间戳

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
            this.autoAddAllPlayers = false;
            this.excludePrefix = null;
            this.excludeSuffix = null;
            this.currentPointStartTime = 0;
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
            this.intervalSeconds = Math.max(1, seconds); // 最小间隔 1 秒
        }

        void start() {
            this.running = true;
            this.index = 0;
            this.currentPointStartTime = System.currentTimeMillis();
        }
        
        void stop() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
            running = false;
        }

        void setViewMode(ViewMode viewMode) {
            this.viewMode = viewMode != null ? viewMode : ViewMode.ORBIT;
        }

        ViewMode getViewMode() {
            return viewMode;
        }

        long getTimeRemaining() {
            if (!running) return 0;
            long elapsed = System.currentTimeMillis() - currentPointStartTime;
            long remaining = (intervalSeconds * 1000) - elapsed;
            return Math.max(0, remaining);
        }

        void updateStartTime() {
            this.currentPointStartTime = System.currentTimeMillis();
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

    /**
     * 向玩家的循环列表中添加一个观察点。
     *
     * @param player 目标玩家。
     * @param pointName 要添加的观察点名称（可以是预定义的点，也可以是 "player_Name" 格式）。
     */
    public void addCyclePoint(ServerPlayerEntity player, String pointName) {
        getOrCreateSession(player.getUuid()).addPoint(pointName);
        player.sendMessage(configManager.getFormattedMessage("cycle_point_added", Map.of("name", pointName)), false);
    }

    /**
     * 将指定分组中的所有观察点添加到玩家的循环列表中。
     *
     * @param player 目标玩家。
     * @param group 分组名称。
     */
    public void addCycleGroup(ServerPlayerEntity player, String group) {
        java.util.Collection<String> groupPoints = SpectatePointManager.getInstance().listPointNamesByGroup(group);
        if (groupPoints.isEmpty()) {
            //#if MC >= 11900
            player.sendMessage(Text.literal("§c[Spectate] 分组 " + group + " 中没有任何观察点。"), false);
            //#else
            //$$player.sendMessage(new LiteralText("§c[Spectate] 分组 " + group + " 中没有任何观察点。"), false);
            //#endif
            return;
        }

        PlayerCycleSession session = getOrCreateSession(player.getUuid());
        int count = 0;
        for (String pointName : groupPoints) {
            if (!session.pointList.contains(pointName)) {
                session.addPoint(pointName);
                count++;
            }
        }
        
        //#if MC >= 11900
        player.sendMessage(Text.literal("§a[Spectate] 已将分组 " + group + " 中的 " + count + " 个观察点添加到循环列表。"), false);
        //#else
        //$$player.sendMessage(new LiteralText("§a[Spectate] 已将分组 " + group + " 中的 " + count + " 个观察点添加到循环列表。"), false);
        //#endif
    }

    /**
     * 从玩家的循环列表中移除一个观察点。
     * 如果循环正在运行，会暂停循环，移除点后重新计算状态。
     *
     * @param player 目标玩家。
     * @param pointName 要移除的观察点名称。
     */
    public void removeCyclePoint(ServerPlayerEntity player, String pointName) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null) {
            boolean wasRunning = session.running;
            if (wasRunning) {
                stopCycle(player); // 停止当前任务
            }

            session.removePoint(pointName);
            player.sendMessage(configManager.getFormattedMessage("cycle_point_removed", Map.of("name", pointName)), false);

            if (wasRunning && !session.isEmpty()) {
                startCycle(player); // 使用修改后的列表重新启动
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

    /**
     * 清空玩家的循环列表。
     *
     * @param player 目标玩家。
     */
    public void clearCyclePoints(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null) {
            session.clearPoints();
            player.sendMessage(configManager.getMessage("cycle_cleared"), false);
        } else {
            player.sendMessage(configManager.getMessage("cycle_list_empty"), false);
        }
    }

    /**
     * 列出玩家当前循环列表中的所有点。
     *
     * @param player 目标玩家。
     * @return 观察点名称列表。
     */
    public List<String> listCyclePoints(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        return session != null ? session.getPoints() : Collections.emptyList();
    }

    /**
     * 设置玩家循环模式下的切换间隔。
     *
     * @param player 目标玩家。
     * @param intervalSeconds 间隔秒数（最小为 1）。
     */
    public void setCycleInterval(ServerPlayerEntity player, long intervalSeconds) {
        getOrCreateSession(player.getUuid()).setInterval(intervalSeconds);
        player.sendMessage(configManager.getFormattedMessage("cycle_interval_set", Map.of("seconds", String.valueOf(intervalSeconds))), false);
    }

    /**
     * 开始玩家的循环观察。使用默认视角模式或上次的偏好。
     * 如果列表为空，会发送提示消息。
     *
     * @param player 目标玩家。
     */
    public void startCycle(ServerPlayerEntity player) {
        // 加载偏好
        PlayerPreference pref = SpectateStateSaver.getInstance().getPlayerPreference(player.getUuid());
        ViewMode viewMode = pref.lastCycleViewMode != null ? pref.lastCycleViewMode : ViewMode.ORBIT;

        startCycle(player, viewMode);
    }

    /**
     * 开始玩家的循环观察，并指定视角模式。
     * 同时保存该偏好。
     *
     * @param player 目标玩家。
     * @param viewMode 视角模式。
     */
    public void startCycle(ServerPlayerEntity player, ViewMode viewMode) {
        ViewMode normalizedViewMode = viewMode != null ? viewMode : ViewMode.ORBIT;

        // 保存偏好
        PlayerPreference pref = SpectateStateSaver.getInstance().getPlayerPreference(player.getUuid());
        pref.lastCycleViewMode = normalizedViewMode;
        SpectateStateSaver.getInstance().savePlayerPreference(player.getUuid(), pref);

        PlayerCycleSession session = getOrCreateSession(player.getUuid());
        if (session.isEmpty()) {
            player.sendMessage(configManager.getMessage("cycle_list_empty"), false);
            return;
        }

        if (session.running) {
            session.stop();
        }
        
        // 设置视角模式
        session.setViewMode(normalizedViewMode);
        session.start();
        
        // 宣布开始并附带模式信息
        String modeMessage = getViewModeMessage(normalizedViewMode);
        player.sendMessage(configManager.getFormattedMessage("cycle_started_with_mode", 
            Map.of("mode", modeMessage)), false);

        // 立即切换到第一个点
        ServerSpectateManager.getInstance().switchToCyclePoint(player);

        // 调度后续切换
        session.future = scheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = SpectateMod.getServer();
            if (server == null) return;
            server.execute(() -> {
                ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(player.getUuid());
                if (onlinePlayer != null && isCycling(onlinePlayer.getUuid())) {
                    nextCyclePoint(onlinePlayer, true); // 自动切换
                }
            });
        }, session.intervalSeconds, session.intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 停止玩家的循环观察任务。
     * 注意：这只会停止调度任务，具体的玩家状态恢复由 {@link ServerSpectateManager} 处理。
     *
     * @param player 目标玩家。
     */
    public void stopCycle(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null && session.running) {
            session.stop();
            // 玩家状态的实际恢复由 ServerSpectateManager 处理
        }
    }

    /**
     * 切换到循环列表中的下一个观察点。
     *
     * @param player 目标玩家。
     * @param isAuto 是否为定时任务自动触发（如果是 false，则表示玩家手动输入命令触发）。
     */
    public void nextCyclePoint(ServerPlayerEntity player, boolean isAuto) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null || !session.running || session.isEmpty()) {
            if (!isAuto) {
                player.sendMessage(configManager.getMessage("cycle_not_running"), false);
            }
            return;
        }

        session.index = (session.index + 1) % session.pointList.size();
        session.updateStartTime(); // 更新开始时间
        ServerSpectateManager.getInstance().switchToCyclePoint(player);

        if (!isAuto) {
            player.sendMessage(configManager.getFormattedMessage("cycle_next_point", Map.of(
                "index", String.valueOf(session.index + 1),
                "total", String.valueOf(session.pointList.size())
            )), false);
        }
    }

    /**
     * 获取玩家当前循环周期的剩余时间（毫秒）。
     *
     * @param playerId 玩家的 UUID。
     * @return 剩余毫秒数。如果没有在循环，返回 0。
     */
    public long getTimeRemaining(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        return session != null ? session.getTimeRemaining() : 0;
    }

    /**
     * 检查玩家是否正在进行循环观察。
     *
     * @param playerId 玩家的 UUID。
     * @return 如果正在循环观察，返回 true。
     */
    public boolean isCycling(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        return session != null && session.running;
    }

    /**
     * 获取玩家当前正在观察的循环点名称。
     *
     * @param playerId 玩家的 UUID。
     * @return 当前点名称，如果未开始或列表为空则返回 null。
     */
    public String getCurrentCyclePointName(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        if (session == null || session.isEmpty()) {
            return null;
        }
        return session.pointList.get(session.index);
    }

    /**
     * 获取玩家当前循环会话的视角模式。
     */
    public ViewMode getCurrentViewMode(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        return session != null ? session.getViewMode() : ViewMode.ORBIT;
    }

    private String getViewModeMessage(ViewMode viewMode) {
        if (viewMode == null) {
            return "普通模式";
        }

        switch (viewMode) {
            case ORBIT:
                return "环绕模式";
            case FOLLOW:
                return "跟随模式";
            case CINEMATIC_SLOW_ORBIT:
                return "慢速环绕";
            case CINEMATIC_AERIAL_VIEW:
                return "高空俯瞰";
            case CINEMATIC_SPIRAL_UP:
                return "螺旋上升";
            case CINEMATIC_FLOATING:
                return "浮游视角";
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

        //#if MC >= 11900
        player.sendMessage(Text.literal("§a[Spectate] " + msg.toString()), false);
        player.sendMessage(Text.literal("§7当前循环列表中有 " + session.getPoints().size() + " 个目标"), false);
        //#else
        //$$player.sendMessage(new LiteralText("§a[Spectate] " + msg.toString()), false);
        //$$player.sendMessage(new LiteralText("§7当前循环列表中有 " + session.getPoints().size() + " 个目标"), false);
        //#endif
    }

    /**
     * 禁用自动添加所有玩家功能。
     */
    public void disableAutoAddAllPlayers(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null) {
            session.setAutoAddAllPlayers(false, null, null);
            //#if MC >= 11900
            player.sendMessage(Text.literal("§a[Spectate] 已禁用自动添加所有玩家"), false);
            //#else
            //$$player.sendMessage(new LiteralText("§a[Spectate] 已禁用自动添加所有玩家"), false);
            //#endif
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
