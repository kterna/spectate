package com.spectate.network;

import com.spectate.SpectateMod;
import com.spectate.network.packet.ClientCapabilityPayload;
import com.spectate.network.packet.SpectateParamsPayload;
import com.spectate.network.packet.SpectateStatePayload;
import com.spectate.network.packet.TargetUpdatePayload;
import net.minecraft.util.Identifier;

//#if MC >= 12005
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//#endif

/**
 * 网络包注册和ID定义
 */
public class SpectateNetworking {

    // 网络包ID
    //#if MC >= 12005
    public static final Identifier STATE_PACKET_ID = Identifier.of(SpectateMod.MOD_ID, "state");
    public static final Identifier PARAMS_PACKET_ID = Identifier.of(SpectateMod.MOD_ID, "params");
    public static final Identifier TARGET_UPDATE_PACKET_ID = Identifier.of(SpectateMod.MOD_ID, "target_update");
    public static final Identifier CAPABILITY_PACKET_ID = Identifier.of(SpectateMod.MOD_ID, "capability");
    //#else
    //$$public static final Identifier STATE_PACKET_ID = new Identifier(SpectateMod.MOD_ID, "state");
    //$$public static final Identifier PARAMS_PACKET_ID = new Identifier(SpectateMod.MOD_ID, "params");
    //$$public static final Identifier TARGET_UPDATE_PACKET_ID = new Identifier(SpectateMod.MOD_ID, "target_update");
    //$$public static final Identifier CAPABILITY_PACKET_ID = new Identifier(SpectateMod.MOD_ID, "capability");
    //#endif

    // 协议版本，用于版本兼容性检查
    public static final int PROTOCOL_VERSION = 2;

    /**
     * 注册服务端发送的包 (S2C)
     * 应在服务端初始化时调用
     */
    public static void registerServerPackets() {
        //#if MC >= 12005
        PayloadTypeRegistry.playS2C().register(SpectateStatePayload.ID, SpectateStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpectateParamsPayload.ID, SpectateParamsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TargetUpdatePayload.ID, TargetUpdatePayload.CODEC);
        //#endif

        SpectateMod.LOGGER.info("Spectate server packets registered");
    }

    /**
     * 注册客户端发送的包 (C2S)
     * 应在服务端初始化时调用
     */
    public static void registerClientPackets() {
        //#if MC >= 12005
        PayloadTypeRegistry.playC2S().register(ClientCapabilityPayload.ID, ClientCapabilityPayload.CODEC);
        //#endif

        SpectateMod.LOGGER.info("Spectate client packets registered");
    }

    /**
     * 注册所有网络包
     */
    public static void registerAll() {
        registerServerPackets();
        registerClientPackets();
    }
}
