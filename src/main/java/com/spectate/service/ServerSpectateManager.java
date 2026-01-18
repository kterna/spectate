package com.spectate.service;

import com.spectate.config.ConfigManager;
import com.spectate.data.PlayerPreference;
import com.spectate.data.SpectatePointData;
import com.spectate.data.SpectateStateSaver;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ServerSpectateManager 是一个 Facade，为命令层和其他模块提供统一的接口。
 * 它协调 SpectateSessionManager 和 CycleService 来执行观察任务。
 */
public class ServerSpectateManager {

    private static final ServerSpectateManager INSTANCE = new ServerSpectateManager();
    public static ServerSpectateManager getInstance() { return INSTANCE; }

    private static final String PLAYER_PREFIX = "player_";

    private final SpectateSessionManager sessionManager = SpectateSessionManager.getInstance();
    private final CycleService cycleService = CycleService.getInstance();
    private final SpectatePointManager pointManager = SpectatePointManager.getInstance();
    private final SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();
    private final ConfigManager configManager = ConfigManager.getInstance();

    private ServerSpectateManager() {}

    private static boolean isPlayerRemoved(ServerPlayerEntity player) {
        //#if MC >= 11900
        return player.isRemoved();
        //#else
        //$$return player.removed;
        //#endif
    }

    /**
     * 观察一个已定义的点。
     * 优先使用玩家上次的偏好设置。
     */
    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point) {
        PlayerPreference pref = stateSaver.getPlayerPreference(player.getUuid());
        ViewMode viewMode = pref.lastSpectateViewMode != null ? pref.lastSpectateViewMode : ViewMode.ORBIT;
        CinematicMode cinematicMode = pref.lastSpectateCinematicMode;
        
        sessionManager.spectatePoint(player, point, false, viewMode, cinematicMode);
    }

    /**
     * 使用指定视角模式观察一个已定义的点。
     * 同时保存该偏好。
     */
    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point, ViewMode viewMode, CinematicMode cinematicMode) {
        // 保存偏好
        PlayerPreference pref = stateSaver.getPlayerPreference(player.getUuid());
        pref.lastSpectateViewMode = viewMode;
        pref.lastSpectateCinematicMode = cinematicMode;
        stateSaver.savePlayerPreference(player.getUuid(), pref);

        sessionManager.spectatePoint(player, point, false, viewMode, cinematicMode);
    }

    /**
     * 观察另一个玩家。
     * 优先使用玩家上次的偏好设置。
     */
    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        PlayerPreference pref = stateSaver.getPlayerPreference(viewer.getUuid());
        ViewMode viewMode = pref.lastSpectateViewMode != null ? pref.lastSpectateViewMode : ViewMode.ORBIT;
        CinematicMode cinematicMode = pref.lastSpectateCinematicMode;

        sessionManager.spectatePlayer(viewer, target, false, viewMode, cinematicMode);
    }

    /**
     * 使用指定视角模式观察另一个玩家。
     * 同时保存该偏好。
     */
    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target, ViewMode viewMode, CinematicMode cinematicMode) {
        // 保存偏好
        PlayerPreference pref = stateSaver.getPlayerPreference(viewer.getUuid());
        pref.lastSpectateViewMode = viewMode;
        pref.lastSpectateCinematicMode = cinematicMode;
        stateSaver.savePlayerPreference(viewer.getUuid(), pref);
        
        sessionManager.spectatePlayer(viewer, target, false, viewMode, cinematicMode);
    }

    /**
     * 观察任意坐标。
     * 使用默认的或上次的旁观模式。
     */
    public void spectateCoords(ServerPlayerEntity player, double x, double y, double z, double distance, double height, double rotation) {
        String pointName = String.format("coords(%.0f,%.0f,%.0f)", x, y, z);
        //#if MC >= 11900
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        //#else
        //$$String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        //#endif
        SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)x, (int)y, (int)z), distance, height, rotation, pointName);
        
        // 同样使用普通旁观的偏好
        PlayerPreference pref = stateSaver.getPlayerPreference(player.getUuid());
        ViewMode viewMode = pref.lastSpectateViewMode != null ? pref.lastSpectateViewMode : ViewMode.ORBIT;
        CinematicMode cinematicMode = pref.lastSpectateCinematicMode;

        sessionManager.spectatePoint(player, data, false, viewMode, cinematicMode);
    }

    /**
     * 停止所有观察活动。
     *
     * @param player 要停止旁观的玩家。
     */
    public void stopSpectating(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (cycleService.isCycling(playerId)) {
            cycleService.stopCycle(player);
        }
        // 如果会话处于活动状态，stopSpectating 将发送它自己的消息。
        sessionManager.stopSpectating(player);
    }

    /**
     * 检查玩家是否正在进行旁观。
     *
     * @param player 玩家实体。
     * @return 如果正在旁观，返回 true。
     */
    public boolean isSpectating(ServerPlayerEntity player) {
        return sessionManager.isSpectating(player.getUuid());
    }

    /* ------------------- Cycle Management Facade ------------------- */

    /**
     * 向玩家的循环列表中添加一个观察点。
     *
     * @param player 目标玩家。
     * @param pointName 观察点名称。
     */
    public void addCyclePoint(ServerPlayerEntity player, String pointName) {
        cycleService.addCyclePoint(player, pointName);
    }

    /**
     * 从玩家的循环列表中移除一个观察点。
     *
     * @param player 目标玩家。
     * @param pointName 观察点名称。
     */
    public void removeCyclePoint(ServerPlayerEntity player, String pointName) {
        cycleService.removeCyclePoint(player, pointName);
    }

    /**
     * 清空玩家的循环列表。
     *
     * @param player 目标玩家。
     */
    public void clearCyclePoints(ServerPlayerEntity player) {
        cycleService.clearCyclePoints(player);
    }

    /**
     * 获取玩家的循环列表。
     *
     * @param player 目标玩家。
     * @return 观察点名称列表。
     */
    public List<String> listCyclePoints(ServerPlayerEntity player) {
        return cycleService.listCyclePoints(player);
    }

    /**
     * 设置循环间隔。
     *
     * @param player 目标玩家。
     * @param intervalSeconds 间隔秒数。
     */
    public void setCycleInterval(ServerPlayerEntity player, long intervalSeconds) {
        cycleService.setCycleInterval(player, intervalSeconds);
    }

    /**
     * 开始循环观察（默认视角）。
     *
     * @param player 目标玩家。
     */
    public void startCycle(ServerPlayerEntity player) {
        cycleService.startCycle(player);
    }

    /**
     * 开始循环观察（指定视角）。
     *
     * @param player 目标玩家。
     * @param viewMode 视角模式。
     * @param cinematicMode 电影模式。
     */
    public void startCycle(ServerPlayerEntity player, ViewMode viewMode, CinematicMode cinematicMode) {
        cycleService.startCycle(player, viewMode, cinematicMode);
    }

    /**
     * 手动切换到下一个观察点。
     *
     * @param player 目标玩家。
     */
    public void nextCyclePoint(ServerPlayerEntity player) {
        cycleService.nextCyclePoint(player, false); // 手动切换
    }

    /**
     * 启用自动添加所有玩家到循环列表。
     *
     * @param player 目标玩家。
     * @param excludePrefix 排除的前缀。
     * @param excludeSuffix 排除的后缀。
     */
    public void enableAutoAddAllPlayers(ServerPlayerEntity player, String excludePrefix, String excludeSuffix) {
        cycleService.enableAutoAddAllPlayers(player, excludePrefix, excludeSuffix);
    }

    /**
     * 禁用自动添加所有玩家。
     *
     * @param player 目标玩家。
     */
    public void disableAutoAddAllPlayers(ServerPlayerEntity player) {
        cycleService.disableAutoAddAllPlayers(player);
    }

    /**
     * 由 CycleService 内部调用，用于切换到当前循环索引指向的点。
     */
    public void switchToCyclePoint(ServerPlayerEntity player) {
        String pointName = cycleService.getCurrentCyclePointName(player.getUuid());
        if (pointName == null) {
            player.sendMessage(configManager.getMessage("cycle_list_empty"), false);
            return;
        }

        // 获取当前循环的视角模式
        ViewMode viewMode = cycleService.getCurrentViewMode(player.getUuid());
        CinematicMode cinematicMode = cycleService.getCurrentCinematicMode(player.getUuid());

        if (pointName.startsWith(PLAYER_PREFIX)) {
            String targetName = pointName.substring(PLAYER_PREFIX.length());
            ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
            if (target == null) {
                // 玩家不在线，自动从列表中移除并跳到下一个
                cycleService.removeCyclePointSilent(player.getUuid(), pointName);
                player.sendMessage(configManager.getFormattedMessage("player_not_found_removed",
                    Map.of("name", targetName)), false);
                // 如果列表还有其他目标，继续切换
                if (!cycleService.listCyclePoints(player).isEmpty()) {
                    switchToCyclePoint(player);
                } else {
                    player.sendMessage(configManager.getMessage("cycle_list_empty"), false);
                }
            } else {
                sessionManager.spectatePlayer(player, target, true, viewMode, cinematicMode);
            }
        } else {
            SpectatePointData point = pointManager.getPoint(pointName);
            if (point == null) {
                player.sendMessage(configManager.getFormattedMessage("point_not_found", Map.of("name", pointName)), false);
            } else {
                sessionManager.spectatePoint(player, point, true, viewMode, cinematicMode);
            }
        }
    }

    /* ------------------- Event Handlers ------------------- */

    /**
     * 处理玩家断开连接事件。
     * 如果玩家正在旁观，则停止旁观并恢复状态。
     *
     * @param player 断开连接的玩家。
     */
    public void onPlayerDisconnect(ServerPlayerEntity player) {
        // 如果玩家正在旁观并断开连接，停止其会话。
        // 这将在服务器保存数据之前恢复其原始状态，
        // 确保他们登录时回到原来的位置。
        if (isSpectating(player)) {
            stopSpectating(player);
        }
        // 通知 CycleService 玩家离开，从其他玩家的循环列表中移除
        cycleService.onPlayerLeave(player);
    }

    /**
     * 处理玩家连接事件。
     * 检查并修复异常状态，处理自动添加逻辑。
     *
     * @param player 连接的玩家。
     */
    public void onPlayerConnect(ServerPlayerEntity player) {
        // 服务器崩溃后的“僵尸”会话安全检查。
        // 如果玩家处于旁观者模式，但插件没有记录他们在旁观，
        // 这意味着他们可能在崩溃期间被卡住了。
        if (player.isSpectator() && !isSpectating(player)) {
            // 将他们恢复到正常状态。
            player.getServer().execute(() -> {
                ServerWorld world = player.getServer().getOverworld();
                GameMode defaultGameMode = world.getServer().getDefaultGameMode();

                // 使用 SpectateSessionManager 中的跨版本兼容方法
                SpectateSessionManager.changeGameMode(player, defaultGameMode);
                player.setCameraEntity(player); // 取消任何摄像机目标

                // 传送到出生点，以避免被卡在奇怪的位置
                BlockPos spawnPoint = world.getSpawnPos();
                SpectateSessionManager.teleportPlayer(player, world, spawnPoint.getX() + 0.5, spawnPoint.getY(), spawnPoint.getZ() + 0.5, 0, 0);

                player.sendMessage(configManager.getMessage("spectate_stop"), false);
            });
        }
        // 通知 CycleService 新玩家加入，自动添加到启用了 autoAddAllPlayers 的循环列表中
        cycleService.onPlayerJoin(player);
    }

    /* ------------------- Who Command Support ------------------- */

    /**
     * 获取所有正在旁观的玩家UUID列表
     */
    public java.util.Set<UUID> getSpectatingPlayerIds() {
        return sessionManager.getSpectatingPlayerIds();
    }

    /**
     * 获取指定玩家的旁观目标信息
     */
    public String getSpectateTargetInfo(UUID playerId) {
        return sessionManager.getSpectateTargetInfo(playerId);
    }

    /**
     * 获取指定玩家的旁观视角模式信息
     */
    public String getSpectateViewModeInfo(UUID playerId) {
        return sessionManager.getSpectateViewModeInfo(playerId);
    }
}
