package com.spectate.network;

import com.spectate.SpectateMod;
import com.spectate.network.packet.ClientCapabilityPayload;
import com.spectate.network.packet.SpectateParamsPayload;
import com.spectate.network.packet.SpectateStatePayload;
import com.spectate.network.packet.TargetUpdatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端网络处理器
 * 处理客户端能力声明，管理平滑客户端列表，发送旁观相关包
 */
public class ServerNetworkHandler {

    private static final ServerNetworkHandler INSTANCE = new ServerNetworkHandler();

    public static ServerNetworkHandler getInstance() {
        return INSTANCE;
    }

    // 拥有平滑旁观能力的客户端
    private final Map<UUID, ClientCapability> smoothClients = new ConcurrentHashMap<>();

    private ServerNetworkHandler() {
    }

    /**
     * 客户端能力信息
     */
    public static class ClientCapability {
        public final boolean hasSmoothSpectate;
        public final int protocolVersion;
        public final double spectateDistance;
        public final double spectateHeightOffset;
        public final double spectateRotationSpeed;
        public final double floatingStrength;
        public final double floatingSpeed;
        public final double floatingDampingFactor;
        public final double floatingAttractionFactor;

        public ClientCapability(boolean hasSmoothSpectate, int protocolVersion,
                                double spectateDistance, double spectateHeightOffset, double spectateRotationSpeed,
                                double floatingStrength, double floatingSpeed, double floatingDampingFactor,
                                double floatingAttractionFactor) {
            this.hasSmoothSpectate = hasSmoothSpectate;
            this.protocolVersion = protocolVersion;
            this.spectateDistance = spectateDistance;
            this.spectateHeightOffset = spectateHeightOffset;
            this.spectateRotationSpeed = spectateRotationSpeed;
            this.floatingStrength = floatingStrength;
            this.floatingSpeed = floatingSpeed;
            this.floatingDampingFactor = floatingDampingFactor;
            this.floatingAttractionFactor = floatingAttractionFactor;
        }

        public boolean hasPlayerRuntimeConfig() {
            return Double.isFinite(spectateDistance)
                    && Double.isFinite(spectateHeightOffset)
                    && Double.isFinite(spectateRotationSpeed)
                    && Double.isFinite(floatingStrength)
                    && Double.isFinite(floatingSpeed)
                    && Double.isFinite(floatingDampingFactor)
                    && Double.isFinite(floatingAttractionFactor);
        }
    }

    /**
     * 注册服务端包处理器
     */
    public void registerServerReceivers() {
        //#if MC >= 12005
        ServerPlayNetworking.registerGlobalReceiver(ClientCapabilityPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getServer().execute(() -> {
                handleClientCapability(player, payload);
            });
        });
        //#else
        //$$ServerPlayNetworking.registerGlobalReceiver(SpectateNetworking.CAPABILITY_PACKET_ID, (server, player, handler, buf, responseSender) -> {
        //$$    ClientCapabilityPayload payload = ClientCapabilityPayload.read(buf);
        //$$    server.execute(() -> {
        //$$        handleClientCapability(player, payload);
        //$$    });
        //$$});
        //#endif
    }

    /**
     * 处理客户端能力声明
     */
    private void handleClientCapability(ServerPlayerEntity player, ClientCapabilityPayload payload) {
        UUID playerId = player.getUuid();

        if (payload.hasSmoothSpectate()) {
            ClientCapability capability = new ClientCapability(
                    true,
                    payload.protocolVersion(),
                    payload.spectateDistance(),
                    payload.spectateHeightOffset(),
                    payload.spectateRotationSpeed(),
                    payload.floatingStrength(),
                    payload.floatingSpeed(),
                    payload.floatingDampingFactor(),
                    payload.floatingAttractionFactor()
            );
            smoothClients.put(playerId, capability);
            SpectateMod.LOGGER.info(
                    "Player {} has smooth spectate capability (protocol version: {}, playerConfig={})",
                    player.getName().getString(),
                    payload.protocolVersion(),
                    capability.hasPlayerRuntimeConfig()
            );
        } else {
            smoothClients.remove(playerId);
        }
    }

    /**
     * 玩家断开连接时清理
     */
    public void onPlayerDisconnect(UUID playerId) {
        smoothClients.remove(playerId);
    }

    /**
     * 检查玩家是否拥有平滑旁观能力
     */
    public boolean hasSmoothCapability(UUID playerId) {
        ClientCapability cap = smoothClients.get(playerId);
        return cap != null && cap.hasSmoothSpectate;
    }

    /**
     * 获取所有拥有平滑能力的玩家ID
     */
    public Set<UUID> getSmoothClientIds() {
        return smoothClients.keySet();
    }

    /**
     * 获取玩家能力详情（用于读取玩家级运行参数）。
     */
    public ClientCapability getClientCapability(UUID playerId) {
        return smoothClients.get(playerId);
    }

    /**
     * 发送旁观状态包到客户端
     */
    public void sendStatePacket(ServerPlayerEntity player, SpectateStatePayload payload) {
        if (!hasSmoothCapability(player.getUuid())) {
            return;
        }

        //#if MC >= 12005
        ServerPlayNetworking.send(player, payload);
        //#else
        //$$net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        //$$payload.write(buf);
        //$$ServerPlayNetworking.send(player, SpectateNetworking.STATE_PACKET_ID, buf);
        //#endif
    }

    /**
     * 发送参数包到客户端
     */
    public void sendParamsPacket(ServerPlayerEntity player, SpectateParamsPayload payload) {
        if (!hasSmoothCapability(player.getUuid())) {
            return;
        }

        //#if MC >= 12005
        ServerPlayNetworking.send(player, payload);
        //#else
        //$$net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        //$$payload.write(buf);
        //$$ServerPlayNetworking.send(player, SpectateNetworking.PARAMS_PACKET_ID, buf);
        //#endif
    }

    /**
     * 发送目标位置更新包到客户端
     */
    public void sendTargetUpdatePacket(ServerPlayerEntity player, TargetUpdatePayload payload) {
        if (!hasSmoothCapability(player.getUuid())) {
            return;
        }

        //#if MC >= 12005
        ServerPlayNetworking.send(player, payload);
        //#else
        //$$net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        //$$payload.write(buf);
        //$$ServerPlayNetworking.send(player, SpectateNetworking.TARGET_UPDATE_PACKET_ID, buf);
        //#endif
    }
}
