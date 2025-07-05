package com.spectate.server;

import com.spectate.SpectatePointManager;
import com.spectate.data.SpectateStateSaver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 服务端观察管理器
 * 负责管理所有玩家的观察状态，包括单独的cycle控制
 */
public class ServerSpectateManager {
    private static ServerSpectateManager instance;
    private MinecraftServer server;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // 玩家观察状态
    private final Map<UUID, PlayerSpectateSession> activeSessions = new ConcurrentHashMap<>();
    
    // 玩家循环观察数据
    private final Map<UUID, PlayerCycleSession> cycleSessions = new ConcurrentHashMap<>();
    
    // 定时任务
    private final Map<UUID, ScheduledFuture<?>> cycleTimers = new ConcurrentHashMap<>();
    
    // 观察旋转定时器
    private final Map<UUID, ScheduledFuture<?>> spectateTimers = new ConcurrentHashMap<>();

    private ServerSpectateManager() {}

    public static ServerSpectateManager getInstance() {
        if (instance == null) {
            instance = new ServerSpectateManager();
        }
        return instance;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
    }

    /**
     * 玩家观察会话
     */
    public static class PlayerSpectateSession {
        public final UUID playerId;
        public final Vec3 originalPosition;
        public final float originalYaw;
        public final float originalPitch;
        public final GameType originalGameMode;
        public Vec3 spectateTarget;  // 改为非final，支持动态更新
        public final float spectateDistance;
        public final long startTime;
        
        // 旋转相关属性
        public float currentAngle = 0.0f;  // 当前角度
        public final float rotationSpeed = 0.1f;  // 旋转速度（度/tick）
        public float height;  // 观察高度，改为非final支持动态更新
        
        // 玩家跟踪相关
        public final UUID targetPlayerId;  // 被跟踪玩家的UUID，null表示固定位置观察
        public final boolean isTrackingPlayer;  // 是否在跟踪玩家

        // 固定位置观察构造函数
        public PlayerSpectateSession(UUID playerId, Vec3 originalPosition, float originalYaw, 
                                   float originalPitch, GameType originalGameMode, 
                                   Vec3 spectateTarget, float spectateDistance) {
            this.playerId = playerId;
            this.originalPosition = originalPosition;
            this.originalYaw = originalYaw;
            this.originalPitch = originalPitch;
            this.originalGameMode = originalGameMode;
            this.spectateTarget = spectateTarget;
            this.spectateDistance = spectateDistance;
            this.startTime = System.currentTimeMillis();
            this.height = (float)(spectateTarget.y + Math.max(5.0f, spectateDistance * 0.5f));
            this.targetPlayerId = null;
            this.isTrackingPlayer = false;
        }
        
        // 玩家跟踪构造函数
        public PlayerSpectateSession(UUID playerId, Vec3 originalPosition, float originalYaw, 
                                   float originalPitch, GameType originalGameMode, 
                                   UUID targetPlayerId, float spectateDistance) {
            this.playerId = playerId;
            this.originalPosition = originalPosition;
            this.originalYaw = originalYaw;
            this.originalPitch = originalPitch;
            this.originalGameMode = originalGameMode;
            this.targetPlayerId = targetPlayerId;
            this.spectateDistance = spectateDistance;
            this.startTime = System.currentTimeMillis();
            this.isTrackingPlayer = true;
            // 初始目标位置和高度将在updateTracking中设置
            this.spectateTarget = Vec3.ZERO;
            this.height = 0;
        }
        
        /**
         * 计算当前旋转位置
         */
        public Vec3 getCurrentPosition() {
            double radians = Math.toRadians(currentAngle);
            double x = spectateTarget.x + spectateDistance * Math.cos(radians);
            double z = spectateTarget.z + spectateDistance * Math.sin(radians);
            return new Vec3(x, height, z);
        }
        
        /**
         * 计算朝向目标的视角
         */
        public float[] getLookAngles() {
            Vec3 currentPos = getCurrentPosition();
            Vec3 lookDirection = spectateTarget.subtract(currentPos);
            
            // 计算水平距离
            double horizontalDistance = Math.sqrt(lookDirection.x * lookDirection.x + lookDirection.z * lookDirection.z);
            
            // 计算yaw角度（水平方向）
            float yaw = (float) Math.toDegrees(Math.atan2(-lookDirection.x, lookDirection.z));
            
            // 计算pitch角度（垂直方向）
            float pitch = (float) Math.toDegrees(Math.atan2(-lookDirection.y, horizontalDistance));
            
            return new float[]{yaw, pitch};
        }
        
        /**
         * 更新跟踪目标位置（仅用于玩家跟踪）
         */
        public boolean updateTracking(MinecraftServer server) {
            if (!isTrackingPlayer || targetPlayerId == null) {
                return false;
            }
            
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetPlayerId);
            if (targetPlayer == null) {
                return false; // 目标玩家不在线
            }
            
            Vec3 newTarget = targetPlayer.position();
            this.spectateTarget = newTarget;
            this.height = (float)(newTarget.y + Math.max(5.0f, spectateDistance * 0.5f));
            return true;
        }
        
        /**
         * 更新旋转角度
         */
        public void updateRotation() {
            currentAngle += rotationSpeed;
            if (currentAngle >= 360.0f) {
                currentAngle -= 360.0f;
            }
        }
    }

    /**
     * 玩家循环观察会话
     */
    public static class PlayerCycleSession {
        public final UUID playerId;
        public final List<String> pointNames;
        public int currentIndex;
        public final int watchDuration; // 秒
        public final long startTime;
        public long currentPointStartTime;

        public PlayerCycleSession(UUID playerId, List<String> pointNames, int watchDuration) {
            this.playerId = playerId;
            this.pointNames = new ArrayList<>(pointNames);
            this.currentIndex = 0;
            this.watchDuration = watchDuration;
            this.startTime = System.currentTimeMillis();
            this.currentPointStartTime = System.currentTimeMillis();
        }

        public String getCurrentPointName() {
            if (pointNames.isEmpty()) return null;
            return pointNames.get(currentIndex);
        }

        public void nextPoint() {
            if (!pointNames.isEmpty()) {
                currentIndex = (currentIndex + 1) % pointNames.size();
                currentPointStartTime = System.currentTimeMillis();
            }
        }

        public long getRemainingTime() {
            long elapsed = (System.currentTimeMillis() - currentPointStartTime) / 1000;
            return Math.max(0, watchDuration - elapsed);
        }
    }

    /**
     * 开始跟踪指定玩家
     */
    public boolean startTrackingPlayer(ServerPlayer spectator, ServerPlayer target, float distance) {
        if (spectator == null || target == null) return false;

        UUID spectatorId = spectator.getUUID();
        UUID targetId = target.getUUID();
        
        // 不能跟踪自己
        if (spectatorId.equals(targetId)) {
            spectator.sendSystemMessage(Component.literal("§c不能跟踪自己"));
            return false;
        }
        
        // 如果玩家已在观察中，先停止
        if (isSpectating(spectatorId)) {
            stopSpectating(spectator, false);
        }

        // 保存观察者当前状态
        Vec3 spectatorPos = spectator.position();
        float yaw = spectator.getYRot();
        float pitch = spectator.getXRot();
        GameType gameMode = spectator.gameMode.getGameModeForPlayer();

        // 创建玩家跟踪会话
        PlayerSpectateSession session = new PlayerSpectateSession(
            spectatorId, spectatorPos, yaw, pitch, gameMode, targetId, distance
        );
        
        // 初始化跟踪位置
        if (!session.updateTracking(server)) {
            spectator.sendSystemMessage(Component.literal("§c目标玩家不在线"));
            return false;
        }
        
        activeSessions.put(spectatorId, session);

        try {
            // 切换到观察者模式
            spectator.setGameMode(GameType.SPECTATOR);
            
            // 初始位置和视角
            updatePlayerSpectatePosition(spectator, session);

            spectator.sendSystemMessage(Component.literal(
                "§a开始跟踪玩家: §e" + target.getName().getString() + " §7正在360度旋转跟踪..."
            ));

            // 开始旋转定时器
            startSpectateRotation(spectator);

            return true;
        } catch (Exception e) {
            // 出错时清理会话
            activeSessions.remove(spectatorId);
            System.err.println("开始跟踪玩家时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 开始观察指定坐标
     */
    public boolean startSpectating(ServerPlayer player, Vec3 position, float distance) {
        if (player == null || position == null) return false;

        UUID playerId = player.getUUID();
        
        // 如果玩家已在观察中，先停止
        if (isSpectating(playerId)) {
            stopSpectating(player, false);
        }

        // 保存玩家当前状态
        Vec3 playerPos = player.position();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        GameType gameMode = player.gameMode.getGameModeForPlayer();

        // 创建观察会话
        PlayerSpectateSession session = new PlayerSpectateSession(
            playerId, playerPos, yaw, pitch, gameMode, position, distance
        );
        activeSessions.put(playerId, session);

        try {
            // 切换到观察者模式
            player.setGameMode(GameType.SPECTATOR);
            
            // 初始位置和视角
            updatePlayerSpectatePosition(player, session);

            player.sendSystemMessage(Component.literal(
                "§a开始观察模式 §7[" + String.format("%.1f, %.1f, %.1f", position.x, position.y, position.z) + "] §e正在360度旋转观察..."
            ));

            // 开始旋转定时器
            startSpectateRotation(player);

            return true;
        } catch (Exception e) {
            // 出错时清理会话
            activeSessions.remove(playerId);
            System.err.println("开始观察时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 观察指定观察点
     */
    public boolean spectatePoint(ServerPlayer player, String pointName) {
        SpectatePointManager.SpectatePoint point = SpectatePointManager.getInstance().getPoint(pointName);
        if (point == null) {
            player.sendSystemMessage(Component.literal("§c观察点 '" + pointName + "' 不存在"));
            return false;
        }

        Vec3 adjustedPos = point.position.add(0, point.heightOffset, 0);
        boolean success = startSpectating(player, adjustedPos, point.distance);
        
        if (success) {
            player.sendSystemMessage(Component.literal(
                "§a正在观察点: §e" + pointName + " §7(" + point.description + ")"
            ));
        }
        
        return success;
    }

    /**
     * 更新玩家观察位置和视角
     */
    private void updatePlayerSpectatePosition(ServerPlayer player, PlayerSpectateSession session) {
        Vec3 currentPos = session.getCurrentPosition();
        float[] angles = session.getLookAngles();
        
        // 分别更新位置和视角，确保同步
        player.teleportTo(currentPos.x, currentPos.y, currentPos.z);
        player.setYRot(angles[0]);
        player.setXRot(angles[1]);
        
        // 重新发送位置更新包，确保客户端同步
        player.connection.teleport(currentPos.x, currentPos.y, currentPos.z, angles[0], angles[1]);
    }

    /**
     * 开始观察旋转定时器
     */
    private void startSpectateRotation(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        // 每tick更新位置（20tps = 50ms间隔）
        ScheduledFuture<?> timer = scheduler.scheduleAtFixedRate(() -> {
            server.execute(() -> {
                PlayerSpectateSession session = activeSessions.get(playerId);
                if (session != null && isSpectating(playerId)) {
                    // 如果是玩家跟踪，先更新目标位置
                    if (session.isTrackingPlayer) {
                        if (!session.updateTracking(server)) {
                            // 目标玩家不在线，停止跟踪
                            player.sendSystemMessage(Component.literal("§c目标玩家已离线，停止跟踪"));
                            stopSpectating(player, false);
                            return;
                        }
                    }
                    
                    session.updateRotation();
                    updatePlayerSpectatePosition(player, session);
                } else {
                    // 会话已结束，停止定时器
                    stopSpectateRotation(playerId);
                }
            });
        }, 50, 50, TimeUnit.MILLISECONDS);
        
        spectateTimers.put(playerId, timer);
    }

    /**
     * 停止观察旋转定时器
     */
    private void stopSpectateRotation(UUID playerId) {
        ScheduledFuture<?> timer = spectateTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    /**
     * 停止观察模式
     */
    public boolean stopSpectating(ServerPlayer player, boolean sendMessage) {
        if (player == null) return false;

        UUID playerId = player.getUUID();
        PlayerSpectateSession session = activeSessions.remove(playerId);
        
        // 停止旋转定时器
        stopSpectateRotation(playerId);
        
        if (session == null) {
            if (sendMessage) {
                player.sendSystemMessage(Component.literal("§c您当前不在观察模式中"));
            }
            return false;
        }

        try {
            // 恢复原始位置和状态
            player.teleportTo(session.originalPosition.x, session.originalPosition.y, session.originalPosition.z);
            player.setYRot(session.originalYaw);
            player.setXRot(session.originalPitch);
            player.setGameMode(session.originalGameMode);

            if (sendMessage) {
                long duration = (System.currentTimeMillis() - session.startTime) / 1000;
                String modeText = session.isTrackingPlayer ? "跟踪模式" : "观察模式";
                player.sendSystemMessage(Component.literal(
                    "§a已退出" + modeText + " §7(时长: " + duration + "秒)"
                ));
            }

            return true;
        } catch (Exception e) {
            System.err.println("停止观察时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 开始循环观察
     */
    public boolean startCycle(ServerPlayer player) {
        if (player == null) return false;

        UUID playerId = player.getUUID();
        
        // 从持久化存储获取循环列表
        List<String> points = SpectateStateSaver.getInstance().getPlayerCycleList(playerId);
        int duration = SpectateStateSaver.getInstance().getPlayerCycleDuration(playerId);
        
        if (points.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c您的循环列表为空，请先添加观察点"));
            return false;
        }

        // 如果已在循环中，先停止
        if (isInCycle(playerId)) {
            stopCycle(player, false);
        }

        // 创建循环会话
        PlayerCycleSession cycleSession = new PlayerCycleSession(playerId, points, duration);
        cycleSessions.put(playerId, cycleSession);

        // 开始第一个观察点
        boolean success = switchToCurrentCyclePoint(player);
        
        if (success) {
            // 设置定时器
            scheduleCycleTimer(player);
            player.sendSystemMessage(Component.literal(
                "§a开始循环观察 §7(共 " + points.size() + " 个观察点，每个观察 " + duration + " 秒)"
            ));
        } else {
            cycleSessions.remove(playerId);
        }

        return success;
    }

    /**
     * 停止循环观察
     */
    public boolean stopCycle(ServerPlayer player, boolean sendMessage) {
        if (player == null) return false;

        UUID playerId = player.getUUID();
        PlayerCycleSession cycleSession = cycleSessions.remove(playerId);
        
        // 取消定时器
        ScheduledFuture<?> timer = cycleTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }

        if (cycleSession == null) {
            if (sendMessage) {
                player.sendSystemMessage(Component.literal("§c您当前不在循环观察中"));
            }
            return false;
        }

        // 停止当前观察
        boolean stopped = stopSpectating(player, false);
        
        if (sendMessage) {
            long totalTime = (System.currentTimeMillis() - cycleSession.startTime) / 1000;
            player.sendSystemMessage(Component.literal(
                "§a已停止循环观察 §7(总时长: " + totalTime + "秒)"
            ));
        }

        return stopped;
    }

    /**
     * 切换到循环中的下一个观察点
     */
    public boolean nextCyclePoint(ServerPlayer player) {
        if (player == null) return false;

        UUID playerId = player.getUUID();
        PlayerCycleSession cycleSession = cycleSessions.get(playerId);
        
        if (cycleSession == null) {
            player.sendSystemMessage(Component.literal("§c您当前不在循环观察中"));
            return false;
        }

        cycleSession.nextPoint();
        
        // 取消当前定时器
        ScheduledFuture<?> timer = cycleTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }

        // 切换到新观察点
        boolean success = switchToCurrentCyclePoint(player);
        
        if (success) {
            // 重新设置定时器
            scheduleCycleTimer(player);
        }

        return success;
    }

    /**
     * 切换到当前循环索引的观察点
     */
    private boolean switchToCurrentCyclePoint(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerCycleSession cycleSession = cycleSessions.get(playerId);
        
        if (cycleSession == null) return false;

        String pointName = cycleSession.getCurrentPointName();
        if (pointName == null) return false;

        // 检查是否是玩家跟踪（以 "player:" 开头）
        if (pointName.startsWith("player:")) {
            String targetPlayerName = pointName.substring(7); // 移除 "player:" 前缀
            ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetPlayerName);
            
            if (targetPlayer == null) {
                player.sendSystemMessage(Component.literal("§c目标玩家 '" + targetPlayerName + "' 不在线，跳过跟踪"));
                return false;
            }
            
            // 开始跟踪玩家
            return startTrackingPlayer(player, targetPlayer, 20.0f);
        } else {
            // 普通观察点
            return spectatePoint(player, pointName);
        }
    }

    /**
     * 安排循环定时器
     */
    private void scheduleCycleTimer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerCycleSession cycleSession = cycleSessions.get(playerId);
        
        if (cycleSession == null) return;

        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            // 在主线程中执行
            server.execute(() -> {
                if (cycleSessions.containsKey(playerId)) {
                    nextCyclePoint(player);
                }
            });
        }, cycleSession.watchDuration, TimeUnit.SECONDS);

        cycleTimers.put(playerId, timer);
    }

    /**
     * 检查玩家是否在观察中
     */
    public boolean isSpectating(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * 检查玩家是否在循环观察中
     */
    public boolean isInCycle(UUID playerId) {
        return cycleSessions.containsKey(playerId);
    }

    /**
     * 获取玩家的循环状态信息
     */
    public String getCycleStatus(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        if (session == null) {
            return "§c不在循环观察中";
        }

        String currentPoint = session.getCurrentPointName();
        long remaining = session.getRemainingTime();
        
        return String.format(
            "§a当前: §e%s §7| §b剩余: %d秒 §7| §f进度: %d/%d",
            currentPoint, remaining, session.currentIndex + 1, session.pointNames.size()
        );
    }

    /**
     * 获取服务器实例
     */
    public MinecraftServer getServer() {
        return server;
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        // 停止所有活动会话
        for (UUID playerId : new ArrayList<>(activeSessions.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                stopSpectating(player, false);
            }
        }

        // 停止所有循环会话
        for (UUID playerId : new ArrayList<>(cycleSessions.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                stopCycle(player, false);
            }
        }

        // 关闭调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 停止所有观察定时器
        for (ScheduledFuture<?> timer : spectateTimers.values()) {
            timer.cancel(false);
        }
        
        activeSessions.clear();
        cycleSessions.clear();
        cycleTimers.clear();
        spectateTimers.clear();
    }
} 