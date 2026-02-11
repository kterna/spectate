package com.spectate.network.packet;

import com.spectate.network.SpectateNetworking;
import com.spectate.service.ViewMode;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif

import java.util.UUID;

/**
 * 旁观状态包 (S2C)
 * 服务端发送给客户端，通知开始/更新/停止旁观
 */
//#if MC >= 12005
public record SpectateStatePayload(
        Action action,
        boolean isPoint,
        @Nullable UUID targetId,
        @Nullable BlockPos pointPos,
        String dimension,
        ViewMode viewMode
) implements CustomPayload {

    public static final CustomPayload.Id<SpectateStatePayload> ID =
            new CustomPayload.Id<>(SpectateNetworking.STATE_PACKET_ID);

    public static final PacketCodec<PacketByteBuf, SpectateStatePayload> CODEC =
            PacketCodec.of(SpectateStatePayload::write, SpectateStatePayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(action);
        buf.writeBoolean(isPoint);
        buf.writeBoolean(targetId != null);
        if (targetId != null) {
            buf.writeUuid(targetId);
        }
        buf.writeBoolean(pointPos != null);
        if (pointPos != null) {
            buf.writeBlockPos(pointPos);
        }
        buf.writeString(dimension);
        buf.writeEnumConstant(viewMode);
    }

    public static SpectateStatePayload read(PacketByteBuf buf) {
        Action action = buf.readEnumConstant(Action.class);
        boolean isPoint = buf.readBoolean();
        UUID targetId = buf.readBoolean() ? buf.readUuid() : null;
        BlockPos pointPos = buf.readBoolean() ? buf.readBlockPos() : null;
        String dimension = buf.readString();
        ViewMode viewMode = buf.readEnumConstant(ViewMode.class);

        return new SpectateStatePayload(action, isPoint, targetId, pointPos, dimension, viewMode);
    }

    /**
     * 旁观动作类型
     */
    public enum Action {
        START,  // 开始旁观
        UPDATE, // 更新旁观参数
        STOP    // 停止旁观
    }

    // 静态工厂方法
    public static SpectateStatePayload start(boolean isPoint, @Nullable UUID targetId,
            @Nullable BlockPos pointPos, String dimension, ViewMode viewMode) {
        return new SpectateStatePayload(Action.START, isPoint, targetId, pointPos, dimension, viewMode);
    }

    public static SpectateStatePayload stop() {
        return new SpectateStatePayload(Action.STOP, false, null, null, "", ViewMode.ORBIT);
    }
}
//#else
//$$public class SpectateStatePayload {
//$$
//$$    private final Action action;
//$$    private final boolean isPoint;
//$$    @Nullable
//$$    private final UUID targetId;
//$$    @Nullable
//$$    private final BlockPos pointPos;
//$$    private final String dimension;
//$$    private final ViewMode viewMode;
//$$
//$$    public SpectateStatePayload(Action action, boolean isPoint, @Nullable UUID targetId,
//$$            @Nullable BlockPos pointPos, String dimension, ViewMode viewMode) {
//$$        this.action = action;
//$$        this.isPoint = isPoint;
//$$        this.targetId = targetId;
//$$        this.pointPos = pointPos;
//$$        this.dimension = dimension;
//$$        this.viewMode = viewMode;
//$$    }
//$$
//$$    public Action action() { return action; }
//$$    public boolean isPoint() { return isPoint; }
//$$    @Nullable public UUID targetId() { return targetId; }
//$$    @Nullable public BlockPos pointPos() { return pointPos; }
//$$    public String dimension() { return dimension; }
//$$    public ViewMode viewMode() { return viewMode; }
//$$
//$$    public void write(PacketByteBuf buf) {
//$$        buf.writeEnumConstant(action);
//$$        buf.writeBoolean(isPoint);
//$$        buf.writeBoolean(targetId != null);
//$$        if (targetId != null) {
//$$            buf.writeUuid(targetId);
//$$        }
//$$        buf.writeBoolean(pointPos != null);
//$$        if (pointPos != null) {
//$$            buf.writeBlockPos(pointPos);
//$$        }
//$$        buf.writeString(dimension);
//$$        buf.writeEnumConstant(viewMode);
//$$    }
//$$
//$$    public static SpectateStatePayload read(PacketByteBuf buf) {
//$$        Action action = buf.readEnumConstant(Action.class);
//$$        boolean isPoint = buf.readBoolean();
//$$        UUID targetId = buf.readBoolean() ? buf.readUuid() : null;
//$$        BlockPos pointPos = buf.readBoolean() ? buf.readBlockPos() : null;
//$$        String dimension = buf.readString();
//$$        ViewMode viewMode = buf.readEnumConstant(ViewMode.class);
//$$        return new SpectateStatePayload(action, isPoint, targetId, pointPos, dimension, viewMode);
//$$    }
//$$
//$$    public enum Action {
//$$        START,
//$$        UPDATE,
//$$        STOP
//$$    }
//$$
//$$    public static SpectateStatePayload start(boolean isPoint, @Nullable UUID targetId,
//$$            @Nullable BlockPos pointPos, String dimension, ViewMode viewMode) {
//$$        return new SpectateStatePayload(Action.START, isPoint, targetId, pointPos, dimension, viewMode);
//$$    }
//$$
//$$    public static SpectateStatePayload stop() {
//$$        return new SpectateStatePayload(Action.STOP, false, null, null, "", ViewMode.ORBIT);
//$$    }
//$$}
//#endif
