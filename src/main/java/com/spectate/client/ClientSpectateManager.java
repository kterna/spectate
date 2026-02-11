package com.spectate.client;

import com.spectate.SpectateMod;
import com.spectate.network.SpectateNetworking;
import com.spectate.network.packet.ClientCapabilityPayload;
import com.spectate.network.packet.SpectateParamsPayload;
import com.spectate.network.packet.SpectateStatePayload;
import com.spectate.network.packet.TargetUpdatePayload;
import com.spectate.service.ViewMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 客户端旁观状态管理器
 * 管理客户端的旁观状态，控制平滑摄像机
 */
public class ClientSpectateManager {

    private static final ClientSpectateManager INSTANCE = new ClientSpectateManager();

    public static ClientSpectateManager getInstance() {
        return INSTANCE;
    }

    // 旁观状态
    private boolean isSpectating = false;
    private boolean isPoint = false;
    @Nullable
    private UUID targetId = null;
    @Nullable
    private BlockPos pointPos = null;
    private String dimension = "";
    private ViewMode viewMode = ViewMode.ORBIT;

    // 平滑摄像机控制器
    private final SmoothCameraController cameraController;
    // 客户端移轴参数控制
    private final TiltShiftSettings tiltShiftSettings;

    // 上一帧时间（用于计算deltaTime）
    private long lastFrameTime = System.currentTimeMillis();

    private ClientSpectateManager() {
        this.cameraController = new SmoothCameraController();
        this.tiltShiftSettings = new TiltShiftSettings();
    }

    /**
     * 是否正在客户端控制的旁观中
     */
    public boolean isSpectating() {
        return isSpectating;
    }

    /**
     * 获取当前帧的摄像机位置
     * @param tickDelta 帧内插值因子
     * @return 摄像机位置，如果不在旁观中则返回null
     */
    @Nullable
    public CameraPosition getCameraPosition(float tickDelta) {
        if (!isSpectating) {
            return null;
        }
        return cameraController.getInterpolated(tickDelta);
    }

    /**
     * 每帧更新（在客户端主循环中调用）
     */
    public void onClientTick() {
        if (!isSpectating) {
            return;
        }

        long now = System.currentTimeMillis();
        double deltaTime = (now - lastFrameTime) / 1000.0;
        lastFrameTime = now;

        // 限制deltaTime，避免帧率过低时跳跃
        deltaTime = Math.min(deltaTime, 0.1);

        cameraController.update(deltaTime);
    }

    /**
     * 处理服务端发来的旁观状态包
     */
    public void handleStatePayload(SpectateStatePayload payload) {
        switch (payload.action()) {
            case START:
                startSpectating(payload);
                break;
            case UPDATE:
                updateSpectating(payload);
                break;
            case STOP:
                stopSpectating();
                break;
        }
    }

    /**
     * 处理服务端发来的参数包
     */
    public void handleParamsPayload(SpectateParamsPayload payload) {
        cameraController.setParams(
                payload.distance(),
                payload.heightOffset(),
                payload.rotationSpeed(),
                payload.floatingStrength(),
                payload.floatingSpeed(),
                payload.dampingFactor(),
                payload.attractionFactor(),
                payload.initialAngle(),
                payload.startTimestamp()
        );
    }

    /**
     * 处理服务端发来的目标位置更新包
     */
    public void handleTargetUpdate(TargetUpdatePayload payload) {
        cameraController.updateTarget(
                payload.x(),
                payload.y(),
                payload.z(),
                payload.velX(),
                payload.velY(),
                payload.velZ(),
                payload.serverTime()
        );
    }

    private void startSpectating(SpectateStatePayload payload) {
        this.isSpectating = true;
        this.isPoint = payload.isPoint();
        this.targetId = payload.targetId();
        this.pointPos = payload.pointPos();
        this.dimension = payload.dimension();
        this.viewMode = payload.viewMode();

        cameraController.reset();
        cameraController.setViewMode(viewMode);

        // 设置初始目标位置
        if (isPoint && pointPos != null) {
            cameraController.updateTarget(
                    pointPos.getX() + 0.5,
                    pointPos.getY() + 0.5,
                    pointPos.getZ() + 0.5
            );
        }

        lastFrameTime = System.currentTimeMillis();

        SpectateMod.LOGGER.info("Client smooth spectate started - isPoint: {}, viewMode: {}",
                isPoint, viewMode.getName());
    }

    private void updateSpectating(SpectateStatePayload payload) {
        this.viewMode = payload.viewMode();
        cameraController.setViewMode(viewMode);
    }

    private void stopSpectating() {
        this.isSpectating = false;
        this.isPoint = false;
        this.targetId = null;
        this.pointPos = null;
        this.dimension = "";
        this.viewMode = ViewMode.ORBIT;

        cameraController.reset();

        SpectateMod.LOGGER.info("Client smooth spectate stopped");
    }

    /**
     * 当玩家加入服务器时调用，发送能力声明
     */
    public void onJoinServer() {
        // 发送能力声明包到服务端
        sendCapabilityPacket();
    }

    /**
     * 当玩家离开服务器时调用
     */
    public void onLeaveServer() {
        stopSpectating();
    }

    /**
     * 客户端配置变更后（例如设置页保存）刷新本地参数缓存。
     */
    public void reloadClientConfig() {
        tiltShiftSettings.reloadFromConfig();
        // 将最新玩家配置重新上报给服务端，支持在线热更新玩家级参数。
        sendCapabilityPacket();
    }

    /**
     * 发送能力声明包
     */
    private void sendCapabilityPacket() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return;
        }

        //#if MC >= 12005
        ClientCapabilityPayload payload = ClientCapabilityPayload.withSmoothSpectate();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
        //#else
        //$$// 旧版本使用 PacketByteBuf
        //$$net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        //$$ClientCapabilityPayload.withSmoothSpectate().write(buf);
        //$$net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(SpectateNetworking.CAPABILITY_PACKET_ID, buf);
        //#endif

        SpectateMod.LOGGER.info("Sent smooth spectate capability to server");
    }

    // Getters for current state
    public boolean isPoint() {
        return isPoint;
    }

    @Nullable
    public UUID getTargetId() {
        return targetId;
    }

    @Nullable
    public BlockPos getPointPos() {
        return pointPos;
    }

    public String getDimension() {
        return dimension;
    }

    public ViewMode getViewMode() {
        return viewMode;
    }

    public TiltShiftSettings getTiltShiftSettings() {
        return tiltShiftSettings;
    }
}
