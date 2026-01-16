package com.spectate.service;

import com.spectate.config.ConfigManager;
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
     */
    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point) {
        sessionManager.spectatePoint(player, point, false);
    }

    /**
     * 使用指定视角模式观察一个已定义的点。
     */
    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point, ViewMode viewMode, CinematicMode cinematicMode) {
        sessionManager.spectatePoint(player, point, false, viewMode, cinematicMode);
    }

    /**
     * 观察另一个玩家。
     */
    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        sessionManager.spectatePlayer(viewer, target, false);
    }

    /**
     * 使用指定视角模式观察另一个玩家。
     */
    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target, ViewMode viewMode, CinematicMode cinematicMode) {
        sessionManager.spectatePlayer(viewer, target, false, viewMode, cinematicMode);
    }

    /**
     * 观察任意坐标。
     */
    public void spectateCoords(ServerPlayerEntity player, double x, double y, double z, double distance, double height, double rotation) {
        String pointName = String.format("coords(%.0f,%.0f,%.0f)", x, y, z);
        //#if MC >= 11900
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        //#else
        //$$String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        //#endif
        SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)x, (int)y, (int)z), distance, height, rotation, pointName);
        sessionManager.spectatePoint(player, data, false);
    }

    /**
     * 停止所有观察活动。
     */
    public void stopSpectating(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (cycleService.isCycling(playerId)) {
            cycleService.stopCycle(player);
        }
        // stopSpectating will send its own message if a session was active.
        sessionManager.stopSpectating(player);
    }

    public boolean isSpectating(ServerPlayerEntity player) {
        return sessionManager.isSpectating(player.getUuid());
    }

    /* ------------------- Cycle Management Facade ------------------- */

    public void addCyclePoint(ServerPlayerEntity player, String pointName) {
        cycleService.addCyclePoint(player, pointName);
    }

    public void removeCyclePoint(ServerPlayerEntity player, String pointName) {
        cycleService.removeCyclePoint(player, pointName);
    }

    public void clearCyclePoints(ServerPlayerEntity player) {
        cycleService.clearCyclePoints(player);
    }

    public List<String> listCyclePoints(ServerPlayerEntity player) {
        return cycleService.listCyclePoints(player);
    }

    public void setCycleInterval(ServerPlayerEntity player, long intervalSeconds) {
        cycleService.setCycleInterval(player, intervalSeconds);
    }

    public void startCycle(ServerPlayerEntity player) {
        cycleService.startCycle(player);
    }

    public void startCycle(ServerPlayerEntity player, ViewMode viewMode, CinematicMode cinematicMode) {
        cycleService.startCycle(player, viewMode, cinematicMode);
    }

    public void nextCyclePoint(ServerPlayerEntity player) {
        cycleService.nextCyclePoint(player, false); // Manual switch
    }

    public void enableAutoAddAllPlayers(ServerPlayerEntity player, String excludePrefix, String excludeSuffix) {
        cycleService.enableAutoAddAllPlayers(player, excludePrefix, excludeSuffix);
    }

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

    public void onPlayerDisconnect(ServerPlayerEntity player) {
        // If a player is spectating and disconnects, stop their session.
        // This will restore their original state before the server saves their data,
        // ensuring they log back in at their original position.
        if (isSpectating(player)) {
            stopSpectating(player);
        }
        // 通知 CycleService 玩家离开，从其他玩家的循环列表中移除
        cycleService.onPlayerLeave(player);
    }

    public void onPlayerConnect(ServerPlayerEntity player) {
        // Safety check for "zombie" sessions after a server crash.
        // If a player is in spectator mode but the plugin has no record of them spectating,
        // it means they were likely stuck during a crash.
        if (player.isSpectator() && !isSpectating(player)) {
            // Restore them to a sane state.
            player.getServer().execute(() -> {
                ServerWorld world = player.getServer().getOverworld();
                GameMode defaultGameMode = world.getServer().getDefaultGameMode();

                // Use the cross-version-compatible method from SpectateSessionManager
                SpectateSessionManager.changeGameMode(player, defaultGameMode);
                player.setCameraEntity(player); // Unset any camera target

                // Teleport to spawn to avoid being stuck in a weird location
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
