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

        public ClientCapability(boolean hasSmoothSpectate, int protocolVersion) {
            this.hasSmoothSpectate = hasSmoothSpectate;
            this.protocolVersion = protocolVersion;
        }
    }

    /**
     * 注册服务端包处理器
     */
    public void registerServerReceivers() {
        //#if MC >= 12005
        ServerPlayNetworking.registerGlobalReceiver(ClientCapabilityPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                handleClientCapability(context.player(), payload);
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
            smoothClients.put(playerId, new ClientCapability(true, payload.protocolVersion()));
            SpectateMod.LOGGER.info("Player {} has smooth spectate capability (protocol version: {})",
                    player.getName().getString(), payload.protocolVersion());
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
