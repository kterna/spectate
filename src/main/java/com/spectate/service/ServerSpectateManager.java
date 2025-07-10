package com.spectate.service;

import com.spectate.data.SpectatePointData;
import com.spectate.data.SpectateStateSaver;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
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

    private ServerSpectateManager() {}

    // Helper method for cross-version Text creation
    private static Text createText(String message) {
        //#if MC >= 11900
        return Text.literal(message);
        //#else
        //$$return new net.minecraft.text.LiteralText(message);
        //#endif
    }
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
            player.sendMessage(createText("Cycle list is empty or invalid."), false);
            return;
        }

        if (pointName.startsWith("player:")) {
            String targetName = pointName.substring("player:".length());
            ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
            if (target == null) {
                player.sendMessage(createText("Player not online: " + targetName), false);
            } else {
                sessionManager.spectatePlayer(player, target, true); // Force switch
            }
        } else {
            SpectatePointData point = pointManager.getPoint(pointName);
            if (point == null) {
                player.sendMessage(createText("Spectate point not found: " + pointName), false);
            } else {
                sessionManager.spectatePoint(player, point, true); // Force switch
            }
        }
    }

    /* ------------------- Event Handlers ------------------- */

    public void onPlayerDisconnect(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (!sessionManager.isSpectating(playerId)) {
            return;
        }

        String stateToSave = null;
        if (cycleService.isCycling(playerId)) {
            stateToSave = "cycle";
        } else {
            SpectateSessionManager.SpectateSession session = sessionManager.getActiveSession(playerId);
            if (session != null) {
                if (session.isObservingPoint() && session.getSpectatePointData() != null) {
                    stateToSave = "point:" + session.getSpectatePointData().getDescription();
                } else if (session.getTargetPlayer() != null && !isPlayerRemoved(session.getTargetPlayer())) {
                    stateToSave = "player:" + session.getTargetPlayer().getUuidAsString();
                }
            }
        }

        if (stateToSave != null) {
            stateSaver.savePlayerState(playerId, stateToSave);
        }

        // Clean up in-memory session state regardless
        cycleService.stopCycle(player);
        sessionManager.stopSpectating(player);
    }

    public void onPlayerConnect(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        String state = stateSaver.getPlayerState(playerId);

        if (state != null) {
            stateSaver.removePlayerState(playerId); // Consume the state

            player.getServer().execute(() -> {
                if ("cycle".equals(state)) {
                    cycleService.startCycle(player);
                } else if (state.startsWith("point:")) {
                    String pointName = state.substring("point:".length());
                    SpectatePointData point = pointManager.getPoint(pointName);
                    if (point != null) {
                        sessionManager.spectatePoint(player, point, true);
                    }
                } else if (state.startsWith("player:")) {
                    try {
                        UUID targetId = UUID.fromString(state.substring("player:".length()));
                        ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetId);
                        if (target != null) {
                            sessionManager.spectatePlayer(player, target, true);
                        }
                    } catch (IllegalArgumentException e) {
                        // Invalid UUID, ignore.
                    }
                }
            });
        }
    }
}
