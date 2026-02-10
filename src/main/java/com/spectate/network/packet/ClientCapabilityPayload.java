package com.spectate.network.packet;

import com.spectate.network.SpectateNetworking;
import net.minecraft.network.PacketByteBuf;

//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif

/**
 * 客户端能力声明包 (C2S)
 * 客户端在连接时发送，告知服务端自己具有smooth spectate能力
 */
//#if MC >= 12005
public record ClientCapabilityPayload(
        boolean hasSmoothSpectate,
        int protocolVersion
) implements CustomPayload {

    public static final CustomPayload.Id<ClientCapabilityPayload> ID =
            new CustomPayload.Id<>(SpectateNetworking.CAPABILITY_PACKET_ID);

    public static final PacketCodec<PacketByteBuf, ClientCapabilityPayload> CODEC =
            PacketCodec.of(ClientCapabilityPayload::write, ClientCapabilityPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(hasSmoothSpectate);
        buf.writeVarInt(protocolVersion);
    }

    public static ClientCapabilityPayload read(PacketByteBuf buf) {
        return new ClientCapabilityPayload(
                buf.readBoolean(),
                buf.readVarInt()
        );
    }

    // 静态工厂方法
    public static ClientCapabilityPayload withSmoothSpectate() {
        return new ClientCapabilityPayload(true, SpectateNetworking.PROTOCOL_VERSION);
    }
}
//#else
//$$public class ClientCapabilityPayload {
//$$
//$$    private final boolean hasSmoothSpectate;
//$$    private final int protocolVersion;
//$$
//$$    public ClientCapabilityPayload(boolean hasSmoothSpectate, int protocolVersion) {
//$$        this.hasSmoothSpectate = hasSmoothSpectate;
//$$        this.protocolVersion = protocolVersion;
//$$    }
//$$
//$$    public boolean hasSmoothSpectate() { return hasSmoothSpectate; }
//$$    public int protocolVersion() { return protocolVersion; }
//$$
//$$    public void write(PacketByteBuf buf) {
//$$        buf.writeBoolean(hasSmoothSpectate);
//$$        buf.writeVarInt(protocolVersion);
//$$    }
//$$
//$$    public static ClientCapabilityPayload read(PacketByteBuf buf) {
//$$        return new ClientCapabilityPayload(
//$$                buf.readBoolean(),
//$$                buf.readVarInt()
//$$        );
//$$    }
//$$
//$$    public static ClientCapabilityPayload withSmoothSpectate() {
//$$        return new ClientCapabilityPayload(true, SpectateNetworking.PROTOCOL_VERSION);
//$$    }
//$$}
//#endif
