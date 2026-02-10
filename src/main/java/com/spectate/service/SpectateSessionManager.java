package com.spectate.service;

import com.spectate.SpectateMod;
import com.spectate.config.ConfigManager;
import com.spectate.data.SpectatePointData;
import com.spectate.data.SpectateStatsManager;
import com.spectate.network.ServerNetworkHandler;
import com.spectate.network.packet.SpectateParamsPayload;
import com.spectate.network.packet.SpectateStatePayload;
import com.spectate.network.packet.TargetUpdatePayload;
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

    // 将其设为 public static，以便其他服务可以使用它来实现跨版本兼容性
    public static void changeGameMode(ServerPlayerEntity player, GameMode gameMode) {
        //#if MC >= 11900
        player.changeGameMode(gameMode);
        //#else
        //$$player.setGameMode(gameMode);
        //#endif
    }

    /**
     * 将玩家跨维度传送到指定位置。
     *
     * @param player 目标玩家
     * @param world 目标世界
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @param yaw 偏航角
     * @param pitch 俯仰角
     */
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
        private ScheduledFuture<?> targetUpdateFuture; // 目标位置更新任务
        private final SpectatePointData spectatePointData;
        private final ServerPlayerEntity targetPlayer;
        private final boolean isPoint;
        private final ViewMode viewMode;
        private final CinematicMode cinematicMode;
        private FloatingCamera floatingCamera; // 浮游摄像机实例
        private boolean useSmoothClient; // 是否使用客户端平滑

        // 目标位置跟踪（用于计算速度）
        private double lastTargetX, lastTargetY, lastTargetZ;
        private long lastTargetTime;

        SpectateSession(SpectatePointData pointData) {
            this.spectatePointData = pointData;
            this.targetPlayer = null;
            this.isPoint = true;
            this.viewMode = ViewMode.ORBIT;
            this.cinematicMode = null;
            this.startTime = System.currentTimeMillis();
            this.useSmoothClient = false;
            initializeFloatingCamera();
        }

        SpectateSession(SpectatePointData pointData, ViewMode viewMode, CinematicMode cinematicMode) {
            this.spectatePointData = pointData;
            this.targetPlayer = null;
            this.isPoint = true;
            this.viewMode = viewMode != null ? viewMode : ViewMode.ORBIT;
            this.cinematicMode = cinematicMode;
            this.startTime = System.currentTimeMillis();
            this.useSmoothClient = false;
            initializeFloatingCamera();
        }

        SpectateSession(ServerPlayerEntity target) {
            this.targetPlayer = target;
            this.spectatePointData = null;
            this.isPoint = false;
            this.viewMode = ViewMode.ORBIT;
            this.cinematicMode = null;
            this.startTime = System.currentTimeMillis();
            this.useSmoothClient = false;
            initializeFloatingCamera();
        }

        SpectateSession(ServerPlayerEntity target, ViewMode viewMode, CinematicMode cinematicMode) {
            this.targetPlayer = target;
            this.spectatePointData = null;
            this.isPoint = false;
            this.viewMode = viewMode != null ? viewMode : ViewMode.ORBIT;
            this.cinematicMode = cinematicMode;
            this.startTime = System.currentTimeMillis();
            this.useSmoothClient = false;
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
            if (targetUpdateFuture != null) {
                targetUpdateFuture.cancel(false);
                targetUpdateFuture = null;
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

        boolean isUseSmoothClient() {
            return useSmoothClient;
        }

        void setUseSmoothClient(boolean useSmoothClient) {
            this.useSmoothClient = useSmoothClient;
        }

        long getStartTime() {
            return startTime;
        }
    }

    /**
     * 将当前玩家的原始状态保存到内存中。
     * 只会在没有保存过的情况下保存，避免多次覆盖。
     *
     * @param player 要保存状态的玩家。
     */
    private void savePlayerOriginalState(ServerPlayerEntity player) {
        playerOriginalStates.putIfAbsent(player.getUuid(), new PlayerOriginalState(player));
    }

    /**
     * 取消指定玩家当前的旁观任务（如果有）。
     * 这会停止位置更新的调度任务。
     *
     * @param playerId 玩家的 UUID。
     */
    private void cancelCurrentSpectation(UUID playerId) {
        SpectateSession session = activeSpectations.remove(playerId);
        if (session != null) {
            session.cancel();
            
            // 记录统计数据
            long duration = System.currentTimeMillis() - session.startTime;
            SpectateStatsManager.getInstance().addSpectatingTime(playerId, duration);
            
            if (!session.isObservingPoint() && session.getTargetPlayer() != null) {
                 SpectateStatsManager.getInstance().addSpectatedTime(session.getTargetPlayer().getUuid(), duration);
                 SpectateStatsManager.getInstance().updateName(session.getTargetPlayer().getUuid(), session.getTargetPlayer().getName().getString());
            }
        }
    }

    /**
     * 开始让玩家旁观指定的观察点。
     * 使用默认的 ORBIT 视角模式。
     *
     * @param player 执行旁观的玩家。
     * @param point 目标观察点数据。
     * @param force 是否强制开始（忽略“已在旁观中”的检查）。
     */
    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point, boolean force) {
        spectatePoint(player, point, force, ViewMode.ORBIT, null);
    }

    /**
     * 开始让玩家旁观指定的观察点，并指定视角模式。
     *
     * @param player 执行旁观的玩家。
     * @param point 目标观察点数据。
     * @param force 是否强制开始。
     * @param viewMode 视角模式（如 ORBIT, CINEMATIC）。
     * @param cinematicMode 如果是 CINEMATIC 模式，指定具体的电影效果子模式。
     */
    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point, boolean force, ViewMode viewMode, CinematicMode cinematicMode) {
        if (point == null) {
            // 如果从命令调用，这种情况理想情况下不应发生，更多的是一种保护措施。
            player.sendMessage(configManager.getMessage("point_not_found"), false);
            return;
        }

        if (!force && isSpectating(player.getUuid())) {
            player.sendMessage(configManager.getMessage("spectate_already_running"), false);
            return;
        }

        // 更新统计用的名字
        SpectateStatsManager.getInstance().updateName(player.getUuid(), player.getName().getString());

        savePlayerOriginalState(player);
        cancelCurrentSpectation(player.getUuid());

        SpectateSession session = new SpectateSession(point, viewMode, cinematicMode);
        activeSpectations.put(player.getUuid(), session);

        // 检查客户端是否有平滑能力
        boolean hasSmoothClient = ServerNetworkHandler.getInstance().hasSmoothCapability(player.getUuid());
        session.setUseSmoothClient(hasSmoothClient);

        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;

        server.execute(() -> {
            changeGameMode(player, GameMode.SPECTATOR);

            String modeMessage = getViewModeMessage(viewMode, cinematicMode);
            player.sendMessage(configManager.getFormattedMessage("spectate_start_point_with_mode",
                Map.of("name", point.getDescription(), "mode", modeMessage)), false);

            // 如果客户端有平滑能力，发送状态和参数包
            if (hasSmoothClient) {
                sendSmoothSpectateStart(player, session, point);
            }

            // 初始位置设置（无论是否smooth都需要）
            updateOrbitingPosition(player, session, 0);

            // 启动定时任务（位置更新 + ActionBar）
            session.orbitFuture = scheduler.scheduleAtFixedRate(() -> {
                if (isPlayerRemoved(player)) {
                    cancelCurrentSpectation(player.getUuid());
                    return;
                }

                double elapsed = (System.currentTimeMillis() - session.startTime) / 1000.0;

                // 只有非smooth客户端才需要服务端teleport
                if (!session.isUseSmoothClient()) {
                    if (point.getRotationSpeed() > 0 || viewMode != ViewMode.ORBIT) {
                        updateOrbitingPosition(player, session, elapsed);
                    }
                }

                // 发送 ActionBar 信息
                sendActionBarInfo(player, session);

            }, 50, 50, TimeUnit.MILLISECONDS);

            // 如果是smooth客户端，启动目标位置更新任务（200ms一次）
            if (hasSmoothClient) {
                session.targetUpdateFuture = scheduler.scheduleAtFixedRate(() -> {
                    if (isPlayerRemoved(player)) {
                        return;
                    }
                    sendTargetUpdate(player, point);
                }, 200, 200, TimeUnit.MILLISECONDS);
            }
        });
    }

    private void sendActionBarInfo(ServerPlayerEntity player, SpectateSession session) {
        String message = "";
        if (session.isObservingPoint()) {
            SpectatePointData point = session.getSpectatePointData();
            if (point != null) {
                message = "§e正在观察: §f" + point.getDescription();
            }
        } else {
            ServerPlayerEntity target = session.getTargetPlayer();
            if (target != null && !isPlayerRemoved(target)) {
                float health = target.getHealth();
                float maxHealth = target.getMaxHealth();
                String hpColor = health < maxHealth * 0.3 ? "§c" : (health < maxHealth * 0.7 ? "§e" : "§a");
                
                message = String.format("§e正在观察: §f%s  %s❤ %.1f/%.1f  §b[%.0f, %.0f, %.0f]", 
                    target.getName().getString(), hpColor, health, maxHealth, target.getX(), target.getY(), target.getZ());
            } else {
                message = "§c目标已离线";
            }
        }
        
        // 如果正在循环模式，添加倒计时
        if (CycleService.getInstance().isCycling(player.getUuid())) {
            long remainingMillis = CycleService.getInstance().getTimeRemaining(player.getUuid());
            long remainingSeconds = Math.max(0, remainingMillis / 1000);
            message += String.format("  §d[循环: %ds]", remainingSeconds);
        }
        
        if (!message.isEmpty()) {
            //#if MC >= 11900
            player.sendMessage(Text.literal(message), true);
            //#else
            //$$player.sendMessage(new net.minecraft.text.LiteralText(message), true);
            //#endif
        }
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

    /**
     * 开始让玩家旁观另一个玩家。
     * 使用默认的 ORBIT 视角模式。
     *
     * @param viewer 旁观者。
     * @param target 被观察的目标玩家。
     * @param force 是否强制开始。
     */
    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target, boolean force) {
        spectatePlayer(viewer, target, force, ViewMode.ORBIT, null);
    }

    /**
     * 开始让玩家旁观另一个玩家，并指定视角模式。
     *
     * @param viewer 旁观者。
     * @param target 被观察的目标玩家。
     * @param force 是否强制开始。
     * @param viewMode 视角模式。
     * @param cinematicMode 电影模式子选项。
     */
    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target, boolean force, ViewMode viewMode, CinematicMode cinematicMode) {
        if (!force && isSpectating(viewer.getUuid())) {
            viewer.sendMessage(configManager.getMessage("spectate_already_running"), false);
            return;
        }

        // 更新统计用的名字
        SpectateStatsManager.getInstance().updateName(viewer.getUuid(), viewer.getName().getString());
        SpectateStatsManager.getInstance().updateName(target.getUuid(), target.getName().getString());

        savePlayerOriginalState(viewer);
        cancelCurrentSpectation(viewer.getUuid());

        SpectateSession session = new SpectateSession(target, viewMode, cinematicMode);
        activeSpectations.put(viewer.getUuid(), session);

        // 检查客户端是否有平滑能力
        boolean hasSmoothClient = ServerNetworkHandler.getInstance().hasSmoothCapability(viewer.getUuid());
        session.setUseSmoothClient(hasSmoothClient);

        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;

        server.execute(() -> {
            changeGameMode(viewer, GameMode.SPECTATOR);

            String modeMessage = getViewModeMessage(viewMode, cinematicMode);
            viewer.sendMessage(configManager.getFormattedMessage("spectate_start_player_with_mode",
                Map.of("name", target.getName().getString(), "mode", modeMessage)), false);

            // 如果客户端有平滑能力，发送状态和参数包
            if (hasSmoothClient) {
                sendSmoothSpectateStartPlayer(viewer, session, target);
            }

            // 初始位置设置
            updatePlayerSpectatePosition(viewer, target, 0);

            // 开始周期性更新位置和信息
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

                // 只有非smooth客户端才需要服务端teleport
                if (!session.isUseSmoothClient()) {
                    updatePlayerSpectatePosition(viewer, target, elapsed);
                }

                // 发送 ActionBar 信息
                sendActionBarInfo(viewer, session);

            }, 50, 50, TimeUnit.MILLISECONDS);

            // 如果是smooth客户端，启动目标位置更新任务（200ms一次）
            if (hasSmoothClient) {
                session.targetUpdateFuture = scheduler.scheduleAtFixedRate(() -> {
                    if (isPlayerRemoved(viewer) || isPlayerRemoved(target)) {
                        return;
                    }
                    sendTargetUpdatePlayer(viewer, target, session);
                }, 200, 200, TimeUnit.MILLISECONDS);
            }
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

    /**
     * 停止指定玩家的旁观会话，并将其恢复到旁观前的状态（位置、游戏模式等）。
     *
     * @param player 要停止旁观的玩家。
     * @return 如果成功停止了一个活动会话，则返回 true；如果玩家并未在旁观，则返回 false。
     */
    public boolean stopSpectating(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        // 发送停止包给smooth客户端
        SpectateSession session = activeSpectations.get(playerId);
        if (session != null && session.isUseSmoothClient()) {
            ServerNetworkHandler.getInstance().sendStatePacket(player, SpectateStatePayload.stop());
        }

        cancelCurrentSpectation(playerId);

        PlayerOriginalState originalState = playerOriginalStates.remove(playerId);
        if (originalState == null) {
            // 如果没有什么可以停止的，不要发送消息。
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

    /**
     * 检查玩家是否正在进行旁观。
     *
     * @param playerId 玩家的 UUID。
     * @return 如果正在旁观，返回 true。
     */
    public boolean isSpectating(UUID playerId) {
        return playerOriginalStates.containsKey(playerId);
    }

    /**
     * 获取指定玩家当前正在进行的旁观会话时长（毫秒）。
     *
     * @param playerId 玩家的 UUID。
     * @return 时长（毫秒），如果没有在旁观则返回 0。
     */
    public long getCurrentSpectatingDuration(UUID playerId) {
        SpectateSession session = activeSpectations.get(playerId);
        if (session != null) {
            return System.currentTimeMillis() - session.startTime;
        }
        return 0;
    }

    /**
     * 获取指定玩家当前正在被旁观的总时长（毫秒）。
     * 可能会有多个玩家同时旁观同一个目标，这里累加所有会话的时长。
     *
     * @param targetId 目标玩家的 UUID。
     * @return 时长（毫秒）。
     */
    public long getCurrentBeingSpectatedDuration(UUID targetId) {
        long total = 0;
        long now = System.currentTimeMillis();
        for (SpectateSession session : activeSpectations.values()) {
            if (!session.isObservingPoint() && session.getTargetPlayer() != null && session.getTargetPlayer().getUuid().equals(targetId)) {
                total += (now - session.startTime);
            }
        }
        return total;
    }

    /**
     * 获取指定玩家当前的活动旁观会话。
     *
     * @param playerId 玩家的 UUID。
     * @return 活动会话对象，如果不存在则返回 null。
     */
    SpectateSession getActiveSession(UUID playerId) {
        return activeSpectations.get(playerId);
    }

    /**
     * 获取所有正在旁观的玩家UUID列表
     */
    public java.util.Set<UUID> getSpectatingPlayerIds() {
        return Collections.unmodifiableSet(playerOriginalStates.keySet());
    }

    /**
     * 获取指定玩家的旁观目标信息
     * @return 目标描述字符串，如果不在旁观则返回null
     */
    public String getSpectateTargetInfo(UUID playerId) {
        SpectateSession session = activeSpectations.get(playerId);
        if (session == null) {
            return null;
        }

        if (session.isObservingPoint()) {
            SpectatePointData point = session.getSpectatePointData();
            if (point != null) {
                return "观察点: " + point.getDescription();
            }
            return "观察点: 未知";
        } else {
            ServerPlayerEntity target = session.getTargetPlayer();
            if (target != null && !isPlayerRemoved(target)) {
                return "玩家: " + target.getName().getString();
            }
            return "玩家: 已离线";
        }
    }

    /**
     * 获取指定玩家的旁观视角模式信息
     */
    public String getSpectateViewModeInfo(UUID playerId) {
        SpectateSession session = activeSpectations.get(playerId);
        if (session == null) {
            return null;
        }
        return getViewModeMessage(session.getViewMode(), session.getCinematicMode());
    }

    // ==================== Smooth Client 相关方法 ====================

    /**
     * 发送开始旁观观察点的包给smooth客户端
     */
    private void sendSmoothSpectateStart(ServerPlayerEntity player, SpectateSession session, SpectatePointData point) {
        ServerNetworkHandler handler = ServerNetworkHandler.getInstance();

        // 发送状态包
        SpectateStatePayload statePayload = SpectateStatePayload.start(
                true,
                null,
                point.getPosition(),
                point.getDimension(),
                session.getViewMode(),
                session.getCinematicMode()
        );
        handler.sendStatePacket(player, statePayload);

        // 发送参数包
        SpectateParamsPayload paramsPayload = SpectateParamsPayload.forPoint(
                point.getDistance(),
                point.getHeightOffset(),
                point.getRotationSpeed(),
                0.5, // floatingStrength
                0.3, // floatingSpeed
                0.95, // dampingFactor
                0.3, // attractionFactor
                0.0, // initialAngle
                session.getStartTime()
        );
        handler.sendParamsPacket(player, paramsPayload);
    }

    /**
     * 发送开始旁观玩家的包给smooth客户端
     */
    private void sendSmoothSpectateStartPlayer(ServerPlayerEntity viewer, SpectateSession session, ServerPlayerEntity target) {
        ServerNetworkHandler handler = ServerNetworkHandler.getInstance();

        // 发送状态包
        //#if MC >= 11900
        String dimension = target.getWorld().getRegistryKey().getValue().toString();
        //#else
        //$$String dimension = target.getServerWorld().getRegistryKey().getValue().toString();
        //#endif

        SpectateStatePayload statePayload = SpectateStatePayload.start(
                false,
                target.getUuid(),
                null,
                dimension,
                session.getViewMode(),
                session.getCinematicMode()
        );
        handler.sendStatePacket(viewer, statePayload);

        // 发送参数包
        SpectateParamsPayload paramsPayload = SpectateParamsPayload.defaultParams(session.getStartTime());
        handler.sendParamsPacket(viewer, paramsPayload);
    }

    /**
     * 发送观察点目标位置更新给smooth客户端
     */
    private void sendTargetUpdate(ServerPlayerEntity player, SpectatePointData point) {
        TargetUpdatePayload payload = TargetUpdatePayload.ofStatic(
                point.getPosition().getX() + 0.5,
                point.getPosition().getY() + 0.5,
                point.getPosition().getZ() + 0.5
        );
        ServerNetworkHandler.getInstance().sendTargetUpdatePacket(player, payload);
    }

    /**
     * 发送玩家目标位置更新给smooth客户端
     */
    private void sendTargetUpdatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target, SpectateSession session) {
        // 计算目标速度
        long now = System.currentTimeMillis();
        double velX = 0, velY = 0, velZ = 0;

        if (session.lastTargetTime > 0) {
            double deltaTime = (now - session.lastTargetTime) / 1000.0;
            if (deltaTime > 0 && deltaTime < 1.0) {
                velX = (target.getX() - session.lastTargetX) / deltaTime;
                velY = (target.getY() - session.lastTargetY) / deltaTime;
                velZ = (target.getZ() - session.lastTargetZ) / deltaTime;
            }
        }

        // 更新历史位置
        session.lastTargetX = target.getX();
        session.lastTargetY = target.getY();
        session.lastTargetZ = target.getZ();
        session.lastTargetTime = now;

        TargetUpdatePayload payload = TargetUpdatePayload.of(
                target.getX(),
                target.getY(),
                target.getZ(),
                velX,
                velY,
                velZ
        );
        ServerNetworkHandler.getInstance().sendTargetUpdatePacket(viewer, payload);
    }
}
