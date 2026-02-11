package com.spectate.network.packet;

import com.spectate.config.ConfigManager;
import com.spectate.config.SpectateConfig;
import com.spectate.network.SpectateNetworking;
import net.minecraft.network.PacketByteBuf;

//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif

/**
 * 客户端能力声明包 (C2S)
 * 客户端在连接时发送，告知服务端自己具有 smooth spectate 能力，
 * 并携带该玩家的客户端旁观参数，供服务端按玩家下发。
 */
//#if MC >= 12005
public record ClientCapabilityPayload(
        boolean hasSmoothSpectate,
        int protocolVersion,
        double spectateDistance,
        double spectateHeightOffset,
        double spectateRotationSpeed,
        double floatingStrength,
        double floatingSpeed,
        double floatingDampingFactor,
        double floatingAttractionFactor
) implements CustomPayload {

    private static final int EXTRA_CAMERA_FIELDS = 7;
    private static final int EXTRA_CAMERA_BYTES = EXTRA_CAMERA_FIELDS * Double.BYTES;

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
        buf.writeDouble(spectateDistance);
        buf.writeDouble(spectateHeightOffset);
        buf.writeDouble(spectateRotationSpeed);
        buf.writeDouble(floatingStrength);
        buf.writeDouble(floatingSpeed);
        buf.writeDouble(floatingDampingFactor);
        buf.writeDouble(floatingAttractionFactor);
    }

    public static ClientCapabilityPayload read(PacketByteBuf buf) {
        boolean hasSmooth = buf.readBoolean();
        int protocol = buf.readVarInt();
        if (buf.readableBytes() >= EXTRA_CAMERA_BYTES) {
            return new ClientCapabilityPayload(
                    hasSmooth,
                    protocol,
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            );
        }
        return new ClientCapabilityPayload(
                hasSmooth,
                protocol,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN
        );
    }

    // 静态工厂方法
    public static ClientCapabilityPayload withSmoothSpectate() {
        SpectateConfig.Settings settings = ConfigManager.getInstance().getConfig().settings;
        return new ClientCapabilityPayload(
                true,
                SpectateNetworking.PROTOCOL_VERSION,
                settings.spectate_distance,
                settings.spectate_height_offset,
                settings.spectate_rotation_speed,
                settings.floating_strength,
                settings.floating_speed,
                settings.floating_damping_factor,
                settings.floating_attraction_factor
        );
    }
}
//#else
//$$public class ClientCapabilityPayload {
//$$
//$$    private final boolean hasSmoothSpectate;
//$$    private final int protocolVersion;
//$$    private final double spectateDistance;
//$$    private final double spectateHeightOffset;
//$$    private final double spectateRotationSpeed;
//$$    private final double floatingStrength;
//$$    private final double floatingSpeed;
//$$    private final double floatingDampingFactor;
//$$    private final double floatingAttractionFactor;
//$$
//$$    private static final int EXTRA_CAMERA_FIELDS = 7;
//$$    private static final int EXTRA_CAMERA_BYTES = EXTRA_CAMERA_FIELDS * Double.BYTES;
//$$
//$$    public ClientCapabilityPayload(boolean hasSmoothSpectate, int protocolVersion,
//$$            double spectateDistance, double spectateHeightOffset, double spectateRotationSpeed,
//$$            double floatingStrength, double floatingSpeed, double floatingDampingFactor, double floatingAttractionFactor) {
//$$        this.hasSmoothSpectate = hasSmoothSpectate;
//$$        this.protocolVersion = protocolVersion;
//$$        this.spectateDistance = spectateDistance;
//$$        this.spectateHeightOffset = spectateHeightOffset;
//$$        this.spectateRotationSpeed = spectateRotationSpeed;
//$$        this.floatingStrength = floatingStrength;
//$$        this.floatingSpeed = floatingSpeed;
//$$        this.floatingDampingFactor = floatingDampingFactor;
//$$        this.floatingAttractionFactor = floatingAttractionFactor;
//$$    }
//$$
//$$    public boolean hasSmoothSpectate() { return hasSmoothSpectate; }
//$$    public int protocolVersion() { return protocolVersion; }
//$$    public double spectateDistance() { return spectateDistance; }
//$$    public double spectateHeightOffset() { return spectateHeightOffset; }
//$$    public double spectateRotationSpeed() { return spectateRotationSpeed; }
//$$    public double floatingStrength() { return floatingStrength; }
//$$    public double floatingSpeed() { return floatingSpeed; }
//$$    public double floatingDampingFactor() { return floatingDampingFactor; }
//$$    public double floatingAttractionFactor() { return floatingAttractionFactor; }
//$$
//$$    public void write(PacketByteBuf buf) {
//$$        buf.writeBoolean(hasSmoothSpectate);
//$$        buf.writeVarInt(protocolVersion);
//$$        buf.writeDouble(spectateDistance);
//$$        buf.writeDouble(spectateHeightOffset);
//$$        buf.writeDouble(spectateRotationSpeed);
//$$        buf.writeDouble(floatingStrength);
//$$        buf.writeDouble(floatingSpeed);
//$$        buf.writeDouble(floatingDampingFactor);
//$$        buf.writeDouble(floatingAttractionFactor);
//$$    }
//$$
//$$    public static ClientCapabilityPayload read(PacketByteBuf buf) {
//$$        boolean hasSmooth = buf.readBoolean();
//$$        int protocol = buf.readVarInt();
//$$        if (buf.readableBytes() >= EXTRA_CAMERA_BYTES) {
//$$            return new ClientCapabilityPayload(
//$$                    hasSmooth,
//$$                    protocol,
//$$                    buf.readDouble(),
//$$                    buf.readDouble(),
//$$                    buf.readDouble(),
//$$                    buf.readDouble(),
//$$                    buf.readDouble(),
//$$                    buf.readDouble(),
//$$                    buf.readDouble()
//$$            );
//$$        }
//$$        return new ClientCapabilityPayload(
//$$                hasSmooth,
//$$                protocol,
//$$                Double.NaN,
//$$                Double.NaN,
//$$                Double.NaN,
//$$                Double.NaN,
//$$                Double.NaN,
//$$                Double.NaN,
//$$                Double.NaN
//$$        );
//$$    }
//$$
//$$    public static ClientCapabilityPayload withSmoothSpectate() {
//$$        SpectateConfig.Settings settings = ConfigManager.getInstance().getConfig().settings;
//$$        return new ClientCapabilityPayload(
//$$                true,
//$$                SpectateNetworking.PROTOCOL_VERSION,
//$$                settings.spectate_distance,
//$$                settings.spectate_height_offset,
//$$                settings.spectate_rotation_speed,
//$$                settings.floating_strength,
//$$                settings.floating_speed,
//$$                settings.floating_damping_factor,
//$$                settings.floating_attraction_factor
//$$        );
//$$    }
//$$}
//#endif
