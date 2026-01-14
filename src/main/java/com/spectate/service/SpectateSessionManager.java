package com.spectate.service;

import com.spectate.SpectateMod;
import com.spectate.config.ConfigManager;
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
    private final ConfigManager configManager = ConfigManager.getInstance();

    private SpectateSessionManager() {
        this.scheduler = CycleService.getInstance().getScheduler();
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
        //#if MC == 12100
        //$$player.teleport(world, x, y, z, Collections.emptySet(), yaw, pitch);
        //#elseif MC >= 11900
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
        private final ViewMode viewMode;
        private final CinematicMode cinematicMode;
        private FloatingCamera floatingCamera; // 浮游摄像机实例

        SpectateSession(SpectatePointData pointData) {
            this.spectatePointData = pointData;
            this.targetPlayer = null;
            this.isPoint = true;
            this.viewMode = ViewMode.ORBIT;
            this.cinematicMode = null;
            this.startTime = System.currentTimeMillis();
            initializeFloatingCamera();
        }

        SpectateSession(SpectatePointData pointData, ViewMode viewMode, CinematicMode cinematicMode) {
            this.spectatePointData = pointData;
            this.targetPlayer = null;
            this.isPoint = true;
            this.viewMode = viewMode != null ? viewMode : ViewMode.ORBIT;
            this.cinematicMode = cinematicMode;
            this.startTime = System.currentTimeMillis();
            initializeFloatingCamera();
        }

        SpectateSession(ServerPlayerEntity target) {
            this.targetPlayer = target;
            this.spectatePointData = null;
            this.isPoint = false;
            this.viewMode = ViewMode.ORBIT;
            this.cinematicMode = null;
            this.startTime = System.currentTimeMillis();
            initializeFloatingCamera();
        }

        SpectateSession(ServerPlayerEntity target, ViewMode viewMode, CinematicMode cinematicMode) {
            this.targetPlayer = target;
            this.spectatePointData = null;
            this.isPoint = false;
            this.viewMode = viewMode != null ? viewMode : ViewMode.ORBIT;
            this.cinematicMode = cinematicMode;
            this.startTime = System.currentTimeMillis();
            initializeFloatingCamera();
        }

        private void initializeFloatingCamera() {
            if (cinematicMode == CinematicMode.FLOATING) {
                floatingCamera = new FloatingCamera();
                // 可以根据需要调整参数
                floatingCamera.setFloatingStrength(0.5);
                floatingCamera.setFloatingSpeed(0.3);
                floatingCamera.setOrbitRadius(8.0);
            }
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

        ViewMode getViewMode() {
            return viewMode;
        }

        CinematicMode getCinematicMode() {
            return cinematicMode;
        }

        FloatingCamera getFloatingCamera() {
            return floatingCamera;
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
        spectatePoint(player, point, force, ViewMode.ORBIT, null);
    }

    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point, boolean force, ViewMode viewMode, CinematicMode cinematicMode) {
        if (point == null) {
            // This case should ideally not happen if called from commands, more of a safeguard.
            player.sendMessage(configManager.getMessage("point_not_found"), false);
            return;
        }

        if (!force && isSpectating(player.getUuid())) {
            player.sendMessage(configManager.getMessage("spectate_already_running"), false);
            return;
        }

        savePlayerOriginalState(player);
        cancelCurrentSpectation(player.getUuid());

        SpectateSession session = new SpectateSession(point, viewMode, cinematicMode);
        activeSpectations.put(player.getUuid(), session);

        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;

        server.execute(() -> {
            changeGameMode(player, GameMode.SPECTATOR);
            
            String modeMessage = getViewModeMessage(viewMode, cinematicMode);
            player.sendMessage(configManager.getFormattedMessage("spectate_start_point_with_mode", 
                Map.of("name", point.getDescription(), "mode", modeMessage)), false);
            
            updateOrbitingPosition(player, session, 0);

            double speedDeg = point.getRotationSpeed();
            if (speedDeg > 0 || viewMode != ViewMode.ORBIT) {
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

        ViewMode viewMode = session.getViewMode();
        
        switch (viewMode) {
            case ORBIT:
                updateOrbitPointPosition(player, session, elapsedSeconds);
                break;
            case CINEMATIC:
                updateCinematicPointPosition(player, session, elapsedSeconds);
                break;
            default:
                updateOrbitPointPosition(player, session, elapsedSeconds);
                break;
        }
    }

    private void updateOrbitPointPosition(ServerPlayerEntity player, SpectateSession session, double elapsedSeconds) {
        SpectatePointData point = session.getSpectatePointData();
        if (point == null) return;

        // 确保在正确的维度
        ServerWorld targetWorld = player.getServerWorld();
        try {
            String dimensionStr = point.getDimension();
            if (dimensionStr.contains(":")) {
                String[] parts = dimensionStr.split(":");
                //#if MC >= 12005
                net.minecraft.util.Identifier dimensionId = net.minecraft.util.Identifier.of(parts[0], parts[1]);
                //#else
                //$$net.minecraft.util.Identifier dimensionId = new net.minecraft.util.Identifier(parts[0], parts[1]);
                //#endif
                for (ServerWorld world : player.getServer().getWorlds()) {
                    if (world.getRegistryKey().getValue().equals(dimensionId)) {
                        targetWorld = world;
                        break;
                    }
                }
            }
            //#if MC >= 11900
            if (targetWorld != null && !player.getWorld().equals(targetWorld)) {
            //#else
            //$$if (targetWorld != null && !player.getServerWorld().equals(targetWorld)) {
            //#endif
                teleportPlayer(player, targetWorld, player.getX(), player.getY(), player.getZ(), 0, 0);
            }
        } catch (Exception e) {
            // 如果维度解析失败，使用当前世界
            targetWorld = player.getServerWorld();
        }

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
        ServerWorld world = targetWorld != null ? targetWorld : player.getServerWorld();
        teleportPlayer(player, world, camXn, camYn, camZn, yaw, pitch);
    }

    private void updateCinematicPointPosition(ServerPlayerEntity player, SpectateSession session, double elapsedSeconds) {
        SpectatePointData point = session.getSpectatePointData();
        if (point == null) return;
        
        CinematicMode cinematicMode = session.getCinematicMode();
        if (cinematicMode == null) {
            cinematicMode = CinematicMode.SLOW_ORBIT;
        }

        // 确保在正确的维度
        ServerWorld targetWorld = player.getServerWorld();
        try {
            String dimensionStr = point.getDimension();
            if (dimensionStr.contains(":")) {
                String[] parts = dimensionStr.split(":");
                //#if MC >= 12005
                net.minecraft.util.Identifier dimensionId = net.minecraft.util.Identifier.of(parts[0], parts[1]);
                //#else
                //$$net.minecraft.util.Identifier dimensionId = new net.minecraft.util.Identifier(parts[0], parts[1]);
                //#endif
                for (ServerWorld world : player.getServer().getWorlds()) {
                    if (world.getRegistryKey().getValue().equals(dimensionId)) {
                        targetWorld = world;
                        break;
                    }
                }
            }
            //#if MC >= 11900
            if (targetWorld != null && !player.getWorld().equals(targetWorld)) {
            //#else
            //$$if (targetWorld != null && !player.getServerWorld().equals(targetWorld)) {
            //#endif
                teleportPlayer(player, targetWorld, player.getX(), player.getY(), player.getZ(), 0, 0);
            }
        } catch (Exception e) {
            // 如果维度解析失败，使用当前世界
            targetWorld = player.getServerWorld();
        }

        double centerX = point.getPosition().getX() + 0.5;
        double centerY = point.getPosition().getY() + 0.5;
        double centerZ = point.getPosition().getZ() + 0.5;
        
        double camXn, camYn, camZn;
        float yaw, pitch;
        double distance, heightOffset, rotationSpeed, angleRad, camXo, camZo;
        double spiralSpeed, riseSpeed;
        double dx, dy, dz;
        
        switch (cinematicMode) {
            case SLOW_ORBIT:
                // 慢速环绕，忽略原点配置的旋转速度
                distance = Math.max(point.getDistance(), 8.0);
                heightOffset = point.getHeightOffset() + 2.0;
                rotationSpeed = 0.5; // 很慢的旋转速度
                
                angleRad = (elapsedSeconds * rotationSpeed) * Math.PI / 180.0;
                camXo = Math.sin(angleRad) * distance;
                camZo = Math.cos(angleRad) * distance;
                camXn = centerX + camXo;
                camYn = centerY + heightOffset;
                camZn = centerZ + camZo;
                break;
                
            case AERIAL_VIEW:
                // 高空俯瞰
                camXn = centerX;
                camYn = centerY + 25.0; // 高空视角
                camZn = centerZ;
                break;
                
            case SPIRAL_UP:
                // 螺旋上升
                distance = Math.max(point.getDistance(), 8.0);
                spiralSpeed = 1.0; // 度/秒
                riseSpeed = 0.3; // 每秒上升格数
                
                angleRad = (elapsedSeconds * spiralSpeed) * Math.PI / 180.0;
                heightOffset = point.getHeightOffset() + (elapsedSeconds * riseSpeed);
                
                camXo = Math.sin(angleRad) * distance;
                camZo = Math.cos(angleRad) * distance;
                camXn = centerX + camXo;
                camYn = centerY + heightOffset;
                camZn = centerZ + camZo;
                break;

            case FLOATING:
                // 浮游视角
                FloatingCamera floatingCam = session.getFloatingCamera();
                if (floatingCam != null) {
                    double deltaTime = Math.min(0.1, elapsedSeconds - ((System.currentTimeMillis() - session.startTime) / 1000.0 - 0.05));
                    if (deltaTime <= 0) deltaTime = 0.05; // 默认50ms
                    
                    double[] result = new double[5];
                    floatingCam.updatePosition(centerX, centerY, centerZ, deltaTime, result);
                    
                    camXn = result[0];
                    camYn = result[1];
                    camZn = result[2];
                    
                    // 浮游视角有自己的yaw/pitch计算
                    yaw = (float) result[3];
                    pitch = (float) result[4];
                    
                    if (isPlayerRemoved(player)) return;
                    ServerWorld world = targetWorld != null ? targetWorld : player.getServerWorld();
                    teleportPlayer(player, world, camXn, camYn, camZn, yaw, pitch);
                    return; // 直接返回，不需要下面的通用视角计算
                } else {
                    // 如果浮游摄像机未初始化，回退到慢速环绕
                    distance = Math.max(point.getDistance(), 8.0);
                    heightOffset = point.getHeightOffset() + 2.0;
                    rotationSpeed = 0.5;
                    
                    angleRad = (elapsedSeconds * rotationSpeed) * Math.PI / 180.0;
                    camXo = Math.sin(angleRad) * distance;
                    camZo = Math.cos(angleRad) * distance;
                    camXn = centerX + camXo;
                    camYn = centerY + heightOffset;
                    camZn = centerZ + camZo;
                    
                    // 计算视角方向，始终看向观察点中心
                    dx = centerX - camXn;
                    dy = centerY - camYn;
                    dz = centerZ - camZn;
                    yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
                    pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

                    if (isPlayerRemoved(player)) return;
                    ServerWorld world = targetWorld != null ? targetWorld : player.getServerWorld();
                    teleportPlayer(player, world, camXn, camYn, camZn, yaw, pitch);
                    return;
                }
                
            default:
                // 默认使用慢速环绕
                distance = Math.max(point.getDistance(), 8.0);
                heightOffset = point.getHeightOffset() + 2.0;
                rotationSpeed = 0.5;
                
                angleRad = (elapsedSeconds * rotationSpeed) * Math.PI / 180.0;
                camXo = Math.sin(angleRad) * distance;
                camZo = Math.cos(angleRad) * distance;
                camXn = centerX + camXo;
                camYn = centerY + heightOffset;
                camZn = centerZ + camZo;
                break;
        }
        
        // 计算视角方向，始终看向观察点中心
        dx = centerX - camXn;
        dy = centerY - camYn;
        dz = centerZ - camZn;
        yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        if (isPlayerRemoved(player)) return;
        ServerWorld world = targetWorld != null ? targetWorld : player.getServerWorld();
        teleportPlayer(player, world, camXn, camYn, camZn, yaw, pitch);
    }

    private void updatePlayerSpectatePosition(ServerPlayerEntity viewer, ServerPlayerEntity target, double elapsedSeconds) {
        SpectateSession session = activeSpectations.get(viewer.getUuid());
        if (session == null) return;

        ViewMode viewMode = session.getViewMode();
        
        switch (viewMode) {
            case ORBIT:
                updateOrbitPlayerPosition(viewer, target, elapsedSeconds);
                break;
            case FOLLOW:
                updateFollowPlayerPosition(viewer, target, elapsedSeconds);
                break;
            case CINEMATIC:
                updateCinematicPlayerPosition(viewer, target, elapsedSeconds, session.getCinematicMode());
                break;
        }
    }

    private void updateOrbitPlayerPosition(ServerPlayerEntity viewer, ServerPlayerEntity target, double elapsedSeconds) {
        // 默认的玩家旁观参数
        double distance = 8.0;  // 默认距离
        double heightOffset = 2.0;  // 默认高度偏移
        double rotationSpeed = 5.0;  // 默认每秒旋转5度
        
        double angleRad = 0;
        if (rotationSpeed > 0) {
            double periodSec = 360.0 / rotationSpeed;
            angleRad = (elapsedSeconds % periodSec) / periodSec * 2 * Math.PI;
        }

        double camXo = Math.sin(angleRad) * distance;
        double camZo = Math.cos(angleRad) * distance;
        double camXn = target.getX() + camXo;
        double camYn = target.getY() + heightOffset;
        double camZn = target.getZ() + camZo;
        
        // 计算视角方向，始终看向目标玩家
        double dx = target.getX() - camXn;
        double dy = target.getY() - camYn;
        double dz = target.getZ() - camZn;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        if (isPlayerRemoved(viewer) || isPlayerRemoved(target)) return;
        //#if MC >= 11900
        teleportPlayer(viewer, (ServerWorld) target.getWorld(), camXn, camYn, camZn, yaw, pitch);
        //#else
        //$$teleportPlayer(viewer, (ServerWorld) target.getServerWorld(), camXn, camYn, camZn, yaw, pitch);
        //#endif
    }

    private void updateFollowPlayerPosition(ServerPlayerEntity viewer, ServerPlayerEntity target, double elapsedSeconds) {
        double distance = 5.0;  // 跟随距离
        double heightOffset = 1.5;  // 高度偏移
        
        // 获取目标朝向，在其后方跟随
        float targetYaw = getPlayerYaw(target);
        double camXn = target.getX() - Math.sin(Math.toRadians(targetYaw)) * distance;
        double camZn = target.getZ() + Math.cos(Math.toRadians(targetYaw)) * distance;
        double camYn = target.getY() + heightOffset;
        
        // 始终看向目标
        double dx = target.getX() - camXn;
        double dy = target.getY() - camYn;
        double dz = target.getZ() - camZn;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        if (isPlayerRemoved(viewer) || isPlayerRemoved(target)) return;
        //#if MC >= 11900
        teleportPlayer(viewer, (ServerWorld) target.getWorld(), camXn, camYn, camZn, yaw, pitch);
        //#else
        //$$teleportPlayer(viewer, (ServerWorld) target.getServerWorld(), camXn, camYn, camZn, yaw, pitch);
        //#endif
    }

    private void updateCinematicPlayerPosition(ServerPlayerEntity viewer, ServerPlayerEntity target, double elapsedSeconds, CinematicMode cinematicMode) {
        if (cinematicMode == null) {
            cinematicMode = CinematicMode.SLOW_ORBIT;
        }
        
        double camXn, camYn, camZn;
        float yaw, pitch;
        double distance, heightOffset, rotationSpeed, angleRad, camXo, camZo;
        double spiralSpeed, riseSpeed;
        
        switch (cinematicMode) {
            case SLOW_ORBIT:
                // 慢速环绕
                distance = 12.0;
                heightOffset = 3.0;
                rotationSpeed = 1.0; // 1度/秒
                
                angleRad = 0;
                if (rotationSpeed > 0) {
                    double periodSec = 360.0 / rotationSpeed;
                    angleRad = (elapsedSeconds % periodSec) / periodSec * 2 * Math.PI;
                }
                
                camXo = Math.sin(angleRad) * distance;
                camZo = Math.cos(angleRad) * distance;
                camXn = target.getX() + camXo;
                camYn = target.getY() + heightOffset;
                camZn = target.getZ() + camZo;
                break;
                
            case AERIAL_VIEW:
                // 高空俯瞰
                camXn = target.getX();
                camYn = target.getY() + 20.0;
                camZn = target.getZ();
                break;
                
            case SPIRAL_UP:
                // 螺旋上升
                distance = 8.0;
                spiralSpeed = 2.0; // 2度/秒
                riseSpeed = 0.5; // 每秒上升0.5格
                
                angleRad = (elapsedSeconds * spiralSpeed) * Math.PI / 180.0;
                heightOffset = 2.0 + (elapsedSeconds * riseSpeed);
                
                camXo = Math.sin(angleRad) * distance;
                camZo = Math.cos(angleRad) * distance;
                camXn = target.getX() + camXo;
                camYn = target.getY() + heightOffset;
                camZn = target.getZ() + camZo;
                break;

            case FLOATING:
                // 浮游视角
                SpectateSession session = activeSpectations.get(viewer.getUuid());
                if (session != null) {
                    FloatingCamera floatingCam = session.getFloatingCamera();
                    if (floatingCam != null) {
                        double deltaTime = Math.min(0.1, elapsedSeconds - ((System.currentTimeMillis() - session.startTime) / 1000.0 - 0.05));
                        if (deltaTime <= 0) deltaTime = 0.05; // 默认50ms
                        
                        double[] result = new double[5];
                        floatingCam.updatePosition(target.getX(), target.getY(), target.getZ(), deltaTime, result);
                        
                        camXn = result[0];
                        camYn = result[1];
                        camZn = result[2];
                        
                        // 浮游视角有自己的yaw/pitch计算
                        yaw = (float) result[3];
                        pitch = (float) result[4];
                        
                        if (isPlayerRemoved(viewer) || isPlayerRemoved(target)) return;
                        //#if MC >= 11900
                        teleportPlayer(viewer, (ServerWorld) target.getWorld(), camXn, camYn, camZn, yaw, pitch);
                        //#else
                        //$$teleportPlayer(viewer, (ServerWorld) target.getServerWorld(), camXn, camYn, camZn, yaw, pitch);
                        //#endif
                        return; // 直接返回，不需要下面的通用视角计算
                    } else {
                        // 如果浮游摄像机未初始化，回退到慢速环绕
                        updateCinematicPlayerPosition(viewer, target, elapsedSeconds, CinematicMode.SLOW_ORBIT);
                        return;
                    }
                } else {
                    // 如果session未找到，回退到慢速环绕
                    updateCinematicPlayerPosition(viewer, target, elapsedSeconds, CinematicMode.SLOW_ORBIT);
                    return;
                }
                
            default:
                // 默认使用慢速环绕
                updateCinematicPlayerPosition(viewer, target, elapsedSeconds, CinematicMode.SLOW_ORBIT);
                return;
        }
        
        // 计算视角方向，始终看向目标玩家
        double dx = target.getX() - camXn;
        double dy = target.getY() - camYn;
        double dz = target.getZ() - camZn;
        yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        if (isPlayerRemoved(viewer) || isPlayerRemoved(target)) return;
        //#if MC >= 11900
        teleportPlayer(viewer, (ServerWorld) target.getWorld(), camXn, camYn, camZn, yaw, pitch);
        //#else
        //$$teleportPlayer(viewer, (ServerWorld) target.getServerWorld(), camXn, camYn, camZn, yaw, pitch);
        //#endif
    }

    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target, boolean force) {
        spectatePlayer(viewer, target, force, ViewMode.ORBIT, null);
    }

    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target, boolean force, ViewMode viewMode, CinematicMode cinematicMode) {
        if (!force && isSpectating(viewer.getUuid())) {
            viewer.sendMessage(configManager.getMessage("spectate_already_running"), false);
            return;
        }

        savePlayerOriginalState(viewer);
        cancelCurrentSpectation(viewer.getUuid());

        SpectateSession session = new SpectateSession(target, viewMode, cinematicMode);
        activeSpectations.put(viewer.getUuid(), session);

        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;

        server.execute(() -> {
            changeGameMode(viewer, GameMode.SPECTATOR);
            
            String modeMessage = getViewModeMessage(viewMode, cinematicMode);
            viewer.sendMessage(configManager.getFormattedMessage("spectate_start_player_with_mode", 
                Map.of("name", target.getName().getString(), "mode", modeMessage)), false);
            
            // 初始位置设置
            updatePlayerSpectatePosition(viewer, target, 0);
            
            // 开始周期性更新位置
            session.orbitFuture = scheduler.scheduleAtFixedRate(() -> {
                if (isPlayerRemoved(viewer) || isPlayerRemoved(target)) {
                    cancelCurrentSpectation(viewer.getUuid());
                    return;
                }
                
                //#if MC >= 11900
                if (!target.getWorld().equals(viewer.getWorld())) {
                //#else
                //$$if (!target.getServerWorld().equals(viewer.getServerWorld())) {
                //#endif
                    // 如果目标切换维度，跟随切换
                    //#if MC >= 11900
                    teleportPlayer(viewer, (ServerWorld) target.getWorld(), viewer.getX(), viewer.getY(), viewer.getZ(), 0, 0);
                    //#else
                    //$$teleportPlayer(viewer, (ServerWorld) target.getServerWorld(), viewer.getX(), viewer.getY(), viewer.getZ(), 0, 0);
                    //#endif
                }
                
                double elapsed = (System.currentTimeMillis() - session.startTime) / 1000.0;
                updatePlayerSpectatePosition(viewer, target, elapsed);
            }, 50, 50, TimeUnit.MILLISECONDS);
        });
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
            player.sendMessage(configManager.getMessage("spectate_stop"), false);
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
