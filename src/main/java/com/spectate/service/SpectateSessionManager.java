package com.spectate.service;

import com.spectate.SpectateMod;
import com.spectate.data.SpectatePointData;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SpectateSessionManager 负责管理所有独立的、非循环的观察会话。
 */
public class SpectateSessionManager {

    private static final SpectateSessionManager INSTANCE = new SpectateSessionManager();
    public static SpectateSessionManager getInstance() { return INSTANCE; }

    private final Map<UUID, PlayerOriginalState> playerOriginalStates = new ConcurrentHashMap<>();
    private final Map<UUID, SpectateSession> activeSpectations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    private SpectateSessionManager() {
        this.scheduler = CycleService.getInstance().getScheduler();
    }

    // Helper methods for cross-version compatibility
    private static Text createText(String message) {
        //#if MC >= 11900
        return Text.literal(message);
        //#else
        //$$return new net.minecraft.text.LiteralText(message);
        //#endif
    }

    private static float getPlayerYaw(ServerPlayerEntity player) {
        //#if MC >= 11900
        return player.getYaw();
        //#else
        //$$return player.yaw;
        //#endif
    }

    private static float getPlayerPitch(ServerPlayerEntity player) {
        //#if MC >= 11900
        return player.getPitch();
        //#else
        //$$return player.pitch;
        //#endif
    }

    // Make this public static so other services can use it for cross-version compatibility
    public static void changeGameMode(ServerPlayerEntity player, GameMode gameMode) {
        //#if MC >= 11900
        player.changeGameMode(gameMode);
        //#else
        //$$player.setGameMode(gameMode);
        //#endif
    }

    public static void teleportPlayer(ServerPlayerEntity player, ServerWorld world, double x, double y, double z, float yaw, float pitch) {
        //#if MC >= 11900
        player.teleport(world, x, y, z, Collections.emptySet(), yaw, pitch, false);
        //#else
        //$$player.teleport(world, x, y, z, yaw, pitch);
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
     * 存储玩家的原始状态，用于恢复
     */
    private static class PlayerOriginalState {
        private final GameMode gameMode;
        private final Vec3d position;
        private final float yaw;
        private final float pitch;
        private final ServerWorld world;
        private final Entity camera;

        PlayerOriginalState(ServerPlayerEntity player) {
            this.gameMode = player.interactionManager.getGameMode();
            this.position = player.getPos();
            this.yaw = getPlayerYaw(player);
            this.pitch = getPlayerPitch(player);
            this.world = player.getServerWorld();
            this.camera = player.getCameraEntity();
        }

        void restore(ServerPlayerEntity player) {
            changeGameMode(player, gameMode);
            teleportPlayer(player, world, position.x, position.y, position.z, yaw, pitch);
            player.setCameraEntity(camera);
        }
    }

    /**
     * 单个观察会话，对包内可见
     */
    static class SpectateSession {
        private final long startTime;
        private ScheduledFuture<?> orbitFuture;
        private final SpectatePointData spectatePointData;
        private final ServerPlayerEntity targetPlayer;
        private final boolean isPoint;

        SpectateSession(SpectatePointData pointData) {
            this.spectatePointData = pointData;
            this.targetPlayer = null;
            this.isPoint = true;
            this.startTime = System.currentTimeMillis();
        }

        SpectateSession(ServerPlayerEntity target) {
            this.targetPlayer = target;
            this.spectatePointData = null;
            this.isPoint = false;
            this.startTime = System.currentTimeMillis();
        }

        void cancel() {
            if (orbitFuture != null) {
                orbitFuture.cancel(false);
                orbitFuture = null;
            }
        }

        boolean isObservingPoint() {
            return isPoint;
        }

        SpectatePointData getSpectatePointData() {
            return spectatePointData;
        }

        ServerPlayerEntity getTargetPlayer() {
            return targetPlayer;
        }
    }

    private void savePlayerOriginalState(ServerPlayerEntity player) {
        playerOriginalStates.putIfAbsent(player.getUuid(), new PlayerOriginalState(player));
    }

    private void cancelCurrentSpectation(UUID playerId) {
        SpectateSession session = activeSpectations.remove(playerId);
        if (session != null) {
            session.cancel();
        }
    }

    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point, boolean force) {
        if (point == null) {
            player.sendMessage(createText("观察点数据为空。"), false);
            return;
        }

        if (!force && isSpectating(player.getUuid())) {
            player.sendMessage(createText("已在观察中。请先使用 /cspectate stop 停止。"), false);
            return;
        }

        savePlayerOriginalState(player);
        cancelCurrentSpectation(player.getUuid());

        SpectateSession session = new SpectateSession(point);
        activeSpectations.put(player.getUuid(), session);

        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;

        server.execute(() -> {
            changeGameMode(player, GameMode.SPECTATOR);
            player.sendMessage(createText("正在观察点: " + point.getDescription()), false);
            updateOrbitingPosition(player, session, 0);

            double speedDeg = point.getRotationSpeed();
            if (speedDeg > 0) {
                session.orbitFuture = scheduler.scheduleAtFixedRate(() -> {
                    if (isPlayerRemoved(player)) {
                        cancelCurrentSpectation(player.getUuid());
                        return;
                    }
                    double elapsed = (System.currentTimeMillis() - session.startTime) / 1000.0;
                    updateOrbitingPosition(player, session, elapsed);
                }, 50, 50, TimeUnit.MILLISECONDS);
            }
        });
    }

    private void updateOrbitingPosition(ServerPlayerEntity player, SpectateSession session, double elapsedSeconds) {
        SpectatePointData point = session.getSpectatePointData();
        if (point == null) return;

        double angleRad = 0;
        if (point.getRotationSpeed() > 0) {
            double periodSec = 360.0 / point.getRotationSpeed();
            angleRad = (elapsedSeconds % periodSec) / periodSec * 2 * Math.PI;
        }

        double camXo = Math.sin(angleRad) * point.getDistance();
        double camZo = Math.cos(angleRad) * point.getDistance();
        double camXn = point.getPosition().getX() + 0.5 + camXo;
        double camYn = point.getPosition().getY() + 0.5 + point.getHeightOffset();
        double camZn = point.getPosition().getZ() + 0.5 + camZo;
        double dx = point.getPosition().getX() + 0.5 - camXn;
        double dy = point.getPosition().getY() + 0.5 - camYn;
        double dz = point.getPosition().getZ() + 0.5 - camZn;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        if (isPlayerRemoved(player)) return;
        teleportPlayer(player, player.getServerWorld(), camXn, camYn, camZn, yaw, pitch);
    }

    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target, boolean force) {
        if (!force && isSpectating(viewer.getUuid())) {
            viewer.sendMessage(createText("已在观察中。请先使用 /cspectate stop 停止。"), false);
            return;
        }

        savePlayerOriginalState(viewer);
        cancelCurrentSpectation(viewer.getUuid());

        SpectateSession session = new SpectateSession(target);
        activeSpectations.put(viewer.getUuid(), session);

        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;

        server.execute(() -> {
            changeGameMode(viewer, GameMode.SPECTATOR);
            viewer.setCameraEntity(target);
            viewer.sendMessage(createText("正在观察玩家: " + target.getName().getString()), false);
        });
    }

    public boolean stopSpectating(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        cancelCurrentSpectation(playerId);

        PlayerOriginalState originalState = playerOriginalStates.remove(playerId);
        if (originalState == null) {
            // Don't send a message if there was nothing to stop.
            return false;
        }

        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return false;

        server.execute(() -> {
            originalState.restore(player);
            player.sendMessage(createText("已停止观察。"), false);
        });
        return true;
    }

    public boolean isSpectating(UUID playerId) {
        return playerOriginalStates.containsKey(playerId);
    }

    SpectateSession getActiveSession(UUID playerId) {
        return activeSpectations.get(playerId);
    }
}