package com.spectate.service;

import com.spectate.SpectateMod;
import com.spectate.data.SpectatePointData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
//#if MC >= 11900
import net.minecraft.text.Text;
//#else
//$$import net.minecraft.text.LiteralText;
//$$import net.minecraft.text.Text;
//#endif
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.Collections;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.spectate.service.SpectatePointManager;
import net.minecraft.entity.Entity;

/**
 * ServerSpectateManager 管理所有动态会话和观察逻辑。
 */
public class ServerSpectateManager {

    private static final ServerSpectateManager INSTANCE = new ServerSpectateManager();
    public static ServerSpectateManager getInstance() { return INSTANCE; }

    // Helper method for cross-version Text creation
    private static Text createText(String message) {
//#if MC >= 11900
        return Text.literal(message);
//#else
        //$$return new LiteralText(message);
//#endif
    }

    // Helper method for cross-version player property access
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

    // Helper method for cross-version gamemode change
    private static void changeGameMode(ServerPlayerEntity player, GameMode gameMode) {
//#if MC >= 11900
        player.changeGameMode(gameMode);
//#else
        //$$player.setGameMode(gameMode);
//#endif
    }

    // Helper method for cross-version teleport
    private static void teleportPlayer(ServerPlayerEntity player, ServerWorld world, double x, double y, double z, float yaw, float pitch) {
//#if MC >= 11900
        player.teleport(world, x, y, z, Collections.emptySet(), yaw, pitch, false);
//#else
        //$$player.teleport(world, x, y, z, yaw, pitch);
//#endif
    }

    // Helper method for cross-version removed check
    private static boolean isPlayerRemoved(ServerPlayerEntity player) {
//#if MC >= 11900
        return player.isRemoved();
//#else
        //$$return player.removed;
//#endif
    }

    /* ------------------- Session 类 ------------------- */
    
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
     * 单个观察会话，可以是点或玩家
     */
    private static class SpectateSession {
        private final long startTime;
        private ScheduledFuture<?> orbitFuture;
        private SpectatePointData point; // 如果是观察点
        private ServerPlayerEntity targetPlayer; // 如果是观察玩家
        private final boolean isPoint; // true=观察点，false=观察玩家
        
        // 创建观察点会话
        SpectateSession(SpectatePointData point) {
            this.point = point;
            this.isPoint = true;
            this.startTime = System.currentTimeMillis();
        }
        
        // 创建观察玩家会话
        SpectateSession(ServerPlayerEntity target) {
            this.targetPlayer = target;
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
        
        SpectatePointData getPoint() {
            return point;
        }
        
        ServerPlayerEntity getTargetPlayer() {
            return targetPlayer;
        }
        
        long getStartTime() {
            return startTime;
        }
    }

    /* ------------------- Cycle Session ------------------- */
    private static class PlayerCycleSession {
        private final List<String> pointList;
        private int index;
        private long intervalSeconds;
        private ScheduledFuture<?> future;
        private boolean running;

        PlayerCycleSession() {
            this.pointList = new java.util.ArrayList<>();
            this.intervalSeconds = 600;
            this.index = 0;
            this.running = false;
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
            return java.util.Collections.unmodifiableList(pointList);
        }

        boolean isEmpty() {
            return pointList.isEmpty();
        }

        void setInterval(long seconds) {
            this.intervalSeconds = Math.max(1, seconds); // 最小1秒
        }
        
        void cancel() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
            running = false;
        }
    }

    /* ------------------- 核心方法 ------------------- */

    // 存储玩家原始状态
    private final Map<UUID, PlayerOriginalState> playerOriginalStates = new ConcurrentHashMap<>();
    
    // 存储当前活跃的观察会话
    private final Map<UUID, SpectateSession> activeSpectations = new ConcurrentHashMap<>();
    
    // 玩家循环会话
    private final Map<UUID, PlayerCycleSession> cycleSessions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "SpectateCycleScheduler"));

    private ServerSpectateManager() {}

    /**
     * 保存玩家原始状态（如果尚未保存）
     */
    private void savePlayerOriginalState(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (!playerOriginalStates.containsKey(playerId)) {
            playerOriginalStates.put(playerId, new PlayerOriginalState(player));
        }
    }
    
    /**
     * 取消当前观察会话（如果有）
     */
    private void cancelCurrentSpectation(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        SpectateSession session = activeSpectations.remove(playerId);
        if (session != null) {
            session.cancel();
        }
    }
    
    /**
     * 开始观察指定坐标点
     */
    public void spectatePoint(ServerPlayerEntity player, SpectatePointData point) {
        // 检查是否已经在循环模式中
        PlayerCycleSession cycleSession = cycleSessions.get(player.getUuid());
        boolean inCycleMode = cycleSession != null && cycleSession.running;
        
        // 如果不在循环模式且已有观察会话，提示错误
        if (!inCycleMode && activeSpectations.containsKey(player.getUuid())) {
            player.sendMessage(createText("Already spectating. Use /cspectate stop first."), false);
            return;
        }
        
        // 保存玩家原始状态（如果尚未保存）
        savePlayerOriginalState(player);
        
        // 取消当前观察（如果有）
        cancelCurrentSpectation(player);
        
        // 创建新的观察会话
        SpectateSession session = new SpectateSession(point);
        activeSpectations.put(player.getUuid(), session);
        
        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;

        // 计算摄像机位置: 这里简单地放置在目标位置正北 direction (0, 0, -distance) 并加上高度偏移
        BlockPos target = point.getPosition();
        double camX = target.getX();
        double camY = target.getY() + point.getHeightOffset();
        double camZ = target.getZ() - point.getDistance();

        // 计算朝向目标的角度
        double dx0 = point.getPosition().getX() + 0.5 - (camX + 0.5);
        double dy0 = point.getPosition().getY() + 0.5 - (camY + 0.5);
        double dz0 = point.getPosition().getZ() + 0.5 - (camZ + 0.5);
        float yaw0 = (float)(Math.atan2(dz0, dx0) * 180.0 / Math.PI) - 90f;
        float pitch0 = (float)(-Math.toDegrees(Math.atan2(dy0, Math.sqrt(dx0*dx0 + dz0*dz0))));
        teleportPlayer(player, player.getServerWorld(), camX + 0.5, camY + 0.5, camZ + 0.5, yaw0, pitch0);

        server.execute(() -> {
            changeGameMode(player, GameMode.SPECTATOR);
            player.sendMessage(createText("Now spectating point: " + point.getDescription()), false);

            // 如果旋转速度为 0，则不安排环绕任务
            double speedDeg = point.getRotationSpeed();
            if (speedDeg > 0) {
                session.orbitFuture = scheduler.scheduleAtFixedRate(() -> {
                    double periodSec = 360.0 / speedDeg; // 完成一圈所需秒数
                    double elapsed = (System.currentTimeMillis() - session.startTime) / 1000.0;
                    double angle = (elapsed % periodSec) / periodSec * 2 * Math.PI;
                    double camXo = Math.sin(angle) * point.getDistance();
                    double camZo = Math.cos(angle) * point.getDistance();
                    double camXn = point.getPosition().getX() + 0.5 + camXo;
                    double camYn = point.getPosition().getY() + 0.5 + point.getHeightOffset();
                    double camZn = point.getPosition().getZ() + 0.5 + camZo;
                    double dx = point.getPosition().getX() + 0.5 - camXn;
                    double dy = point.getPosition().getY() + 0.5 - camYn;
                    double dz = point.getPosition().getZ() + 0.5 - camZn;
                    float yaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
                    float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz))));
                    if (isPlayerRemoved(player)) return;
                    teleportPlayer(player, player.getServerWorld(), camXn, camYn, camZn, yaw, pitch);
                }, 100, 50, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * 结束观察，恢复玩家状态
     */
    public void stopSpectating(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        
        // 取消当前观察
        cancelCurrentSpectation(player);
        
        // 停止循环模式
        PlayerCycleSession cycleSession = cycleSessions.get(playerId);
        if (cycleSession != null && cycleSession.running) {
            cycleSession.cancel();
        }
        
        // 恢复玩家原始状态
        PlayerOriginalState originalState = playerOriginalStates.remove(playerId);
        if (originalState == null) {
            player.sendMessage(createText("Not spectating."), false);
            return;
        }
        
        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;
        
        server.execute(() -> {
            originalState.restore(player);
            player.sendMessage(createText("Stopped spectating."), false);
        });
    }

    public boolean isSpectating(PlayerEntity player) {
        return playerOriginalStates.containsKey(player.getUuid());
    }

    /**
     * 向玩家的循环列表中添加一个观察点
     */
    public void addCyclePoint(ServerPlayerEntity player, String pointName) {
        PlayerCycleSession session = cycleSessions.computeIfAbsent(player.getUuid(), k -> new PlayerCycleSession());
        session.addPoint(pointName);
        player.sendMessage(createText("Added '" + pointName + "' to cycle list."), false);
    }

    /**
     * 从玩家的循环列表中移除一个观察点
     */
    public void removeCyclePoint(ServerPlayerEntity player, String pointName) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null) {
            player.sendMessage(createText("You don't have a cycle list."), false);
            return;
        }
        session.removePoint(pointName);
        player.sendMessage(createText("Removed '" + pointName + "' from cycle list."), false);
    }

    /**
     * 清空玩家的循环列表
     */
    public void clearCyclePoints(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null) {
            player.sendMessage(createText("You don't have a cycle list."), false);
            return;
        }
        session.clearPoints();
        player.sendMessage(createText("Cleared cycle list."), false);
    }

    /**
     * 列出玩家的循环列表
     */
    public List<String> listCyclePoints(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null) {
            return java.util.Collections.emptyList();
        }
        return session.getPoints();
    }

    /**
     * 设置循环间隔时间
     */
    public void setCycleInterval(ServerPlayerEntity player, long intervalSeconds) {
        PlayerCycleSession session = cycleSessions.computeIfAbsent(player.getUuid(), k -> new PlayerCycleSession());
        session.setInterval(intervalSeconds);
        player.sendMessage(createText("Set cycle interval to " + intervalSeconds + " seconds."), false);
    }

    /**
     * 开始循环观察
     */
    public void startCycle(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null || session.isEmpty()) {
            player.sendMessage(createText("Cycle list is empty. Add points with /cspectate cycle add <name>"), false);
            return;
        }
        
        // 如果已经在运行，先停止
        if (session.running) {
            session.cancel();
        }
        
        // 保存玩家原始状态（如果尚未保存）
        savePlayerOriginalState(player);
        
        session.running = true;

        // 开始首个点
        switchToCurrentCyclePoint(player);

        // 安排下一个
        session.future = scheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = SpectateMod.getServer();
            if (server == null) return;
            server.execute(() -> {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(player.getUuid());
                if (sp != null) {
                    autoNextCyclePoint(sp);
                }
            });
        }, session.intervalSeconds, session.intervalSeconds, TimeUnit.SECONDS);
        
        player.sendMessage(createText("Started cycle with " + session.pointList.size() + " points, interval: " + session.intervalSeconds + "s"), false);
    }

    /**
     * 开始循环观察（兼容旧版本，直接提供点列表）
     */
    public void startCycle(ServerPlayerEntity player, List<String> list, long intervalSeconds) {
        // 清空现有列表
        PlayerCycleSession session = cycleSessions.computeIfAbsent(player.getUuid(), k -> new PlayerCycleSession());
        session.clearPoints();
        
        // 添加所有点
        for (String point : list) {
            session.addPoint(point);
        }
        
        // 设置间隔
        session.setInterval(intervalSeconds);
        
        // 启动循环
        if (session.isEmpty()) {
            player.sendMessage(createText("Cycle list is empty"), false);
            return;
        }
        
        // 如果已经在运行，先停止
        if (session.running) {
            session.cancel();
        }
        
        session.running = true;

        // 开始首个点
        switchToCurrentCyclePoint(player);
    }



    private void switchToCurrentCyclePoint(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null) return;
        
        if (session.pointList.isEmpty()) {
            player.sendMessage(createText("Cycle list is empty."), false);
            return;
        }
        
        String name = session.pointList.get(session.index);
        if (name.startsWith("player:")) {
            String targetName = name.substring("player:".length());
            ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
            if (target == null) {
                player.sendMessage(createText("Player not online: " + targetName), false);
                return;
            }
            spectatePlayer(player, target);
            return;
        }
        
        SpectatePointData point = SpectatePointManager.getInstance().getPoint(name);
        if (point == null) {
            player.sendMessage(createText("Point not found in cycle: " + name), false);
            return;
        }
        
        spectatePoint(player, point);
    }

    /**
     * 观察其他玩家（第一人称附身）
     */
    public void spectatePlayer(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        // 检查是否已经在循环模式中
        PlayerCycleSession cycleSession = cycleSessions.get(viewer.getUuid());
        boolean inCycleMode = cycleSession != null && cycleSession.running;
        
        // 如果不在循环模式且已有观察会话，提示错误
        if (!inCycleMode && activeSpectations.containsKey(viewer.getUuid())) {
            viewer.sendMessage(createText("Already spectating. Use /cspectate stop first."), false);
            return;
        }
        
        // 保存玩家原始状态（如果尚未保存）
        savePlayerOriginalState(viewer);
        
        // 取消当前观察（如果有）
        cancelCurrentSpectation(viewer);
        
        // 创建新的观察会话
        SpectateSession session = new SpectateSession(target);
        activeSpectations.put(viewer.getUuid(), session);
        
        MinecraftServer server = SpectateMod.getServer();
        if (server == null) return;
        
        server.execute(() -> {
            changeGameMode(viewer, GameMode.SPECTATOR);
            viewer.setCameraEntity(target);
            viewer.sendMessage(createText("Now spectating player: " + target.getName().getString()), false);
        });
    }

    /**
     * 观察任意坐标
     */
    public void spectateCoords(ServerPlayerEntity player, double x, double y, double z, double distance, double height) {
        spectateCoords(player, x, y, z, distance, height, 0.1);
    }

    /**
     * 观察任意坐标（可指定旋转速度）
     */
    public void spectateCoords(ServerPlayerEntity player, double x, double y, double z, double distance, double height, double rotation) {
        SpectatePointData data = new SpectatePointData(new BlockPos((int)x,(int)y,(int)z), distance, height, rotation, String.format("coords(%.0f,%.0f,%.0f)",x,y,z));
        spectatePoint(player, data);
    }

    /**
     * 手动切换到下一个循环点
     */
    public void nextCyclePoint(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null || !session.running || session.isEmpty()) {
            player.sendMessage(createText("You are not in cycle mode or your cycle list is empty."), false);
            return;
        }
        
        // 切换到下一个点
        session.index = (session.index + 1) % session.pointList.size();
        switchToCurrentCyclePoint(player);
        player.sendMessage(createText("Switched to next point: " + (session.index + 1) + "/" + session.pointList.size()), false);
    }

    // 内部使用的自动切换方法
    private void autoNextCyclePoint(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null) return;
        session.index = (session.index + 1) % session.pointList.size();
        switchToCurrentCyclePoint(player);
    }
}
