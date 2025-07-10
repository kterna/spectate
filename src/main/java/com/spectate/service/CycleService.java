package com.spectate.service;

import com.spectate.SpectateMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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

    private CycleService() {}


    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    private static class PlayerCycleSession {
        private final List<String> pointList;
        private int index;
        private long intervalSeconds;
        private ScheduledFuture<?> future;
        private boolean running;

        PlayerCycleSession() {
            this.pointList = new ArrayList<>();
            this.intervalSeconds = 600; // Default interval
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
            return Collections.unmodifiableList(pointList);
        }

        boolean isEmpty() {
            return pointList.isEmpty();
        }

        void setInterval(long seconds) {
            this.intervalSeconds = Math.max(1, seconds); // Minimum 1 second
        }

        void start() {
            this.running = true;
            this.index = 0;
        }
        
        void stop() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
            running = false;
        }
    }

    private PlayerCycleSession getOrCreateSession(UUID playerId) {
        return cycleSessions.computeIfAbsent(playerId, k -> new PlayerCycleSession());
    }

    // Helper method for cross-version Text creation
    private static Text createText(String message) {
        //#if MC >= 11900
        return Text.literal(message);
        //#else
        //$$return new net.minecraft.text.LiteralText(message);
        //#endif
    }

    public void addCyclePoint(ServerPlayerEntity player, String pointName) {
        getOrCreateSession(player.getUuid()).addPoint(pointName);
        player.sendMessage(createText("已添加至循环: " + pointName), false);
    }

    public void removeCyclePoint(ServerPlayerEntity player, String pointName) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null) {
            session.removePoint(pointName);
            player.sendMessage(createText("已从循环中移除: " + pointName), false);
        } else {
            player.sendMessage(createText("您没有设置循环列表。"), false);
        }
    }

    public void clearCyclePoints(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null) {
            session.clearPoints();
            player.sendMessage(createText("已清空循环列表。"), false);
        } else {
            player.sendMessage(createText("您没有设置循环列表。"), false);
        }
    }

    public List<String> listCyclePoints(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        return session != null ? session.getPoints() : Collections.emptyList();
    }

    public void setCycleInterval(ServerPlayerEntity player, long intervalSeconds) {
        getOrCreateSession(player.getUuid()).setInterval(intervalSeconds);
        player.sendMessage(createText("循环间隔已设为 " + intervalSeconds + " 秒。"), false);
    }

    public void startCycle(ServerPlayerEntity player) {
        PlayerCycleSession session = getOrCreateSession(player.getUuid());
        if (session.isEmpty()) {
            player.sendMessage(createText("循环列表为空。请使用 /cspectate cycle add <名称> 添加观察点。"), false);
            return;
        }

        if (session.running) {
            session.stop();
        }
        
        session.start();
        
        // Announce start
        player.sendMessage(createText("已开始观察循环。"), false);

        // Switch to the first point immediately
        ServerSpectateManager.getInstance().switchToCyclePoint(player);

        // Schedule subsequent switches
        session.future = scheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = SpectateMod.getServer();
            if (server == null) return;
            server.execute(() -> {
                ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(player.getUuid());
                if (onlinePlayer != null && isCycling(onlinePlayer.getUuid())) {
                    nextCyclePoint(onlinePlayer, true); // Auto-switch
                }
            });
        }, session.intervalSeconds, session.intervalSeconds, TimeUnit.SECONDS);
    }

    public void stopCycle(ServerPlayerEntity player) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session != null && session.running) {
            session.stop();
            // The actual restoration of player state is handled by ServerSpectateManager
        }
    }

    public void nextCyclePoint(ServerPlayerEntity player, boolean isAuto) {
        PlayerCycleSession session = cycleSessions.get(player.getUuid());
        if (session == null || !session.running || session.isEmpty()) {
            if (!isAuto) {
                player.sendMessage(createText("您不在循环模式中，或您的循环列表为空。"), false);
            }
            return;
        }

        session.index = (session.index + 1) % session.pointList.size();
        ServerSpectateManager.getInstance().switchToCyclePoint(player);

        if (!isAuto) {
            player.sendMessage(createText("已切换至下个观察点: " + (session.index + 1) + "/" + session.pointList.size()), false);
        }
    }

    public boolean isCycling(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        return session != null && session.running;
    }

    public String getCurrentCyclePointName(UUID playerId) {
        PlayerCycleSession session = cycleSessions.get(playerId);
        if (session == null || session.isEmpty()) {
            return null;
        }
        return session.pointList.get(session.index);
    }
}
