package com.spectate.network.packet;

import com.spectate.network.SpectateNetworking;
import net.minecraft.network.PacketByteBuf;

//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif

/**
 * 目标位置更新包 (S2C)
 * 服务端定期发送目标位置更新（约200ms一次）
 */
//#if MC >= 12005
public record TargetUpdatePayload(
        double x,
        double y,
        double z,
        double velX,
        double velY,
        double velZ,
        long serverTime
) implements CustomPayload {

    public static final CustomPayload.Id<TargetUpdatePayload> ID =
            new CustomPayload.Id<>(SpectateNetworking.TARGET_UPDATE_PACKET_ID);

    public static final PacketCodec<PacketByteBuf, TargetUpdatePayload> CODEC =
            PacketCodec.of(TargetUpdatePayload::write, TargetUpdatePayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(velX);
        buf.writeDouble(velY);
        buf.writeDouble(velZ);
        buf.writeLong(serverTime);
    }

    public static TargetUpdatePayload read(PacketByteBuf buf) {
        return new TargetUpdatePayload(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readLong()
        );
    }

    // 静态工厂方法
    public static TargetUpdatePayload of(double x, double y, double z, double velX, double velY, double velZ) {
        return new TargetUpdatePayload(x, y, z, velX, velY, velZ, System.currentTimeMillis());
    }

    public static TargetUpdatePayload ofStatic(double x, double y, double z) {
        return new TargetUpdatePayload(x, y, z, 0, 0, 0, System.currentTimeMillis());
    }
}
//#else
//$$public class TargetUpdatePayload {
//$$
//$$    private final double x;
//$$    private final double y;
//$$    private final double z;
//$$    private final double velX;
//$$    private final double velY;
//$$    private final double velZ;
//$$    private final long serverTime;
//$$
//$$    public TargetUpdatePayload(double x, double y, double z, double velX, double velY, double velZ, long serverTime) {
//$$        this.x = x;
//$$        this.y = y;
//$$        this.z = z;
//$$        this.velX = velX;
//$$        this.velY = velY;
//$$        this.velZ = velZ;
//$$        this.serverTime = serverTime;
//$$    }
//$$
//$$    public double x() { return x; }
//$$    public double y() { return y; }
//$$    public double z() { return z; }
//$$    public double velX() { return velX; }
//$$    public double velY() { return velY; }
//$$    public double velZ() { return velZ; }
//$$    public long serverTime() { return serverTime; }
//$$
//$$    public void write(PacketByteBuf buf) {
//$$        buf.writeDouble(x);
//$$        buf.writeDouble(y);
//$$        buf.writeDouble(z);
//$$        buf.writeDouble(velX);
//$$        buf.writeDouble(velY);
//$$        buf.writeDouble(velZ);
//$$        buf.writeLong(serverTime);
//$$    }
//$$
//$$    public static TargetUpdatePayload read(PacketByteBuf buf) {
//$$        return new TargetUpdatePayload(
//$$                buf.readDouble(),
//$$                buf.readDouble(),
//$$                buf.readDouble(),
//$$                buf.readDouble(),
//$$                buf.readDouble(),
//$$                buf.readDouble(),
//$$                buf.readLong()
//$$        );
//$$    }
//$$
//$$    public static TargetUpdatePayload of(double x, double y, double z, double velX, double velY, double velZ) {
//$$        return new TargetUpdatePayload(x, y, z, velX, velY, velZ, System.currentTimeMillis());
//$$    }
//$$
//$$    public static TargetUpdatePayload ofStatic(double x, double y, double z) {
//$$        return new TargetUpdatePayload(x, y, z, 0, 0, 0, System.currentTimeMillis());
//$$    }
//$$}
//#endif
