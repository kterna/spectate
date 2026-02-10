package com.spectate.network.packet;

import com.spectate.network.SpectateNetworking;
import net.minecraft.network.PacketByteBuf;

//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif

/**
 * 旁观参数包 (S2C)
 * 服务端发送给客户端的摄像机参数
 */
//#if MC >= 12005
public record SpectateParamsPayload(
        double distance,
        double heightOffset,
        double rotationSpeed,
        // FloatingCamera 参数
        double floatingStrength,
        double floatingSpeed,
        double dampingFactor,
        double attractionFactor,
        // 同步用
        double initialAngle,
        long startTimestamp
) implements CustomPayload {

    public static final CustomPayload.Id<SpectateParamsPayload> ID =
            new CustomPayload.Id<>(SpectateNetworking.PARAMS_PACKET_ID);

    public static final PacketCodec<PacketByteBuf, SpectateParamsPayload> CODEC =
            PacketCodec.of(SpectateParamsPayload::write, SpectateParamsPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(distance);
        buf.writeDouble(heightOffset);
        buf.writeDouble(rotationSpeed);
        buf.writeDouble(floatingStrength);
        buf.writeDouble(floatingSpeed);
        buf.writeDouble(dampingFactor);
        buf.writeDouble(attractionFactor);
        buf.writeDouble(initialAngle);
        buf.writeLong(startTimestamp);
    }

    public static SpectateParamsPayload read(PacketByteBuf buf) {
        return new SpectateParamsPayload(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readLong()
        );
    }

    // 静态工厂方法 - 从观察点创建
    public static SpectateParamsPayload forPoint(double distance, double heightOffset, double rotationSpeed,
            double floatingStrength, double floatingSpeed, double dampingFactor, double attractionFactor,
            double initialAngle, long startTimestamp) {
        return new SpectateParamsPayload(distance, heightOffset, rotationSpeed, floatingStrength,
                floatingSpeed, dampingFactor, attractionFactor, initialAngle, startTimestamp);
    }

    // 默认参数
    public static SpectateParamsPayload defaultParams(long startTimestamp) {
        return new SpectateParamsPayload(8.0, 2.0, 5.0, 0.5, 0.3, 0.95, 0.3, 0.0, startTimestamp);
    }
}
//#else
//$$public class SpectateParamsPayload {
//$$
//$$    private final double distance;
//$$    private final double heightOffset;
//$$    private final double rotationSpeed;
//$$    private final double floatingStrength;
//$$    private final double floatingSpeed;
//$$    private final double dampingFactor;
//$$    private final double attractionFactor;
//$$    private final double initialAngle;
//$$    private final long startTimestamp;
//$$
//$$    public SpectateParamsPayload(double distance, double heightOffset, double rotationSpeed,
//$$            double floatingStrength, double floatingSpeed, double dampingFactor, double attractionFactor,
//$$            double initialAngle, long startTimestamp) {
//$$        this.distance = distance;
//$$        this.heightOffset = heightOffset;
//$$        this.rotationSpeed = rotationSpeed;
//$$        this.floatingStrength = floatingStrength;
//$$        this.floatingSpeed = floatingSpeed;
//$$        this.dampingFactor = dampingFactor;
//$$        this.attractionFactor = attractionFactor;
//$$        this.initialAngle = initialAngle;
//$$        this.startTimestamp = startTimestamp;
//$$    }
//$$
//$$    public double distance() { return distance; }
//$$    public double heightOffset() { return heightOffset; }
//$$    public double rotationSpeed() { return rotationSpeed; }
//$$    public double floatingStrength() { return floatingStrength; }
//$$    public double floatingSpeed() { return floatingSpeed; }
//$$    public double dampingFactor() { return dampingFactor; }
//$$    public double attractionFactor() { return attractionFactor; }
//$$    public double initialAngle() { return initialAngle; }
//$$    public long startTimestamp() { return startTimestamp; }
//$$
//$$    public void write(PacketByteBuf buf) {
//$$        buf.writeDouble(distance);
//$$        buf.writeDouble(heightOffset);
//$$        buf.writeDouble(rotationSpeed);
//$$        buf.writeDouble(floatingStrength);
//$$        buf.writeDouble(floatingSpeed);
//$$        buf.writeDouble(dampingFactor);
//$$        buf.writeDouble(attractionFactor);
//$$        buf.writeDouble(initialAngle);
//$$        buf.writeLong(startTimestamp);
//$$    }
//$$
//$$    public static SpectateParamsPayload read(PacketByteBuf buf) {
//$$        return new SpectateParamsPayload(
//$$                buf.readDouble(),
//$$                buf.readDouble(),
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
//$$    public static SpectateParamsPayload forPoint(double distance, double heightOffset, double rotationSpeed,
//$$            double floatingStrength, double floatingSpeed, double dampingFactor, double attractionFactor,
//$$            double initialAngle, long startTimestamp) {
//$$        return new SpectateParamsPayload(distance, heightOffset, rotationSpeed, floatingStrength,
//$$                floatingSpeed, dampingFactor, attractionFactor, initialAngle, startTimestamp);
//$$    }
//$$
//$$    public static SpectateParamsPayload defaultParams(long startTimestamp) {
//$$        return new SpectateParamsPayload(8.0, 2.0, 5.0, 0.5, 0.3, 0.95, 0.3, 0.0, startTimestamp);
//$$    }
//$$}
//#endif
