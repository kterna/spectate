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
     * 观察另一个玩家。
     */
    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        sessionManager.spectatePlayer(viewer, target, false);
    }

    /**
     * 观察任意坐标。
     */
    public void spectateCoords(ServerPlayerEntity player, double x, double y, double z, double distance, double height, double rotation) {
        String pointName = String.format("coords(%.0f,%.0f,%.0f)", x, y, z);
        SpectatePointData data = new SpectatePointData(new BlockPos((int)x, (int)y, (int)z), distance, height, rotation, pointName);
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

    public void nextCyclePoint(ServerPlayerEntity player) {
        cycleService.nextCyclePoint(player, false); // Manual switch
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

        if (pointName.startsWith("player:")) {
            String targetName = pointName.substring("player:".length());
            ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
            if (target == null) {
                player.sendMessage(configManager.getFormattedMessage("player_not_found", Map.of("name", targetName)), false);
            } else {
                sessionManager.spectatePlayer(player, target, true); // Force switch
            }
        } else {
            SpectatePointData point = pointManager.getPoint(pointName);
            if (point == null) {
                player.sendMessage(configManager.getFormattedMessage("point_not_found", Map.of("name", pointName)), false);
            } else {
                sessionManager.spectatePoint(player, point, true); // Force switch
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
    }
}
