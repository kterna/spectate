package com.spectate.client;

import com.spectate.SpectateMod;
import com.spectate.network.SpectateNetworking;
import com.spectate.network.packet.SpectateParamsPayload;
import com.spectate.network.packet.SpectateStatePayload;
import com.spectate.network.packet.TargetUpdatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端Mod入口
 * 负责客户端的初始化和网络包注册
 */
@Environment(EnvType.CLIENT)
public class SpectateModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SpectateMod.LOGGER.info("Spectate client mod initializing...");

        // 注册客户端网络包接收处理器
        registerClientPacketReceivers();

        // 注册客户端Tick事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSpectateManager.getInstance().onClientTick();
        });

        // 注册连接事件
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // 延迟发送能力声明，确保连接已建立
            client.execute(() -> {
                ClientSpectateManager.getInstance().onJoinServer();
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientSpectateManager.getInstance().onLeaveServer();
        });

        SpectateMod.LOGGER.info("Spectate client mod initialized");
    }

    /**
     * 注册客户端接收服务端包的处理器
     */
    private void registerClientPacketReceivers() {
        //#if MC >= 12005
        // 处理旁观状态包
        ClientPlayNetworking.registerGlobalReceiver(SpectateStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientSpectateManager.getInstance().handleStatePayload(payload);
            });
        });

        // 处理参数包
        ClientPlayNetworking.registerGlobalReceiver(SpectateParamsPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientSpectateManager.getInstance().handleParamsPayload(payload);
            });
        });

        // 处理目标更新包
        ClientPlayNetworking.registerGlobalReceiver(TargetUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientSpectateManager.getInstance().handleTargetUpdate(payload);
            });
        });
        //#else
        //$$// 旧版本使用不同的API
        //$$ClientPlayNetworking.registerGlobalReceiver(SpectateNetworking.STATE_PACKET_ID, (client, handler, buf, responseSender) -> {
        //$$    SpectateStatePayload payload = SpectateStatePayload.read(buf);
        //$$    client.execute(() -> {
        //$$        ClientSpectateManager.getInstance().handleStatePayload(payload);
        //$$    });
        //$$});
        //$$
        //$$ClientPlayNetworking.registerGlobalReceiver(SpectateNetworking.PARAMS_PACKET_ID, (client, handler, buf, responseSender) -> {
        //$$    SpectateParamsPayload payload = SpectateParamsPayload.read(buf);
        //$$    client.execute(() -> {
        //$$        ClientSpectateManager.getInstance().handleParamsPayload(payload);
        //$$    });
        //$$});
        //$$
        //$$ClientPlayNetworking.registerGlobalReceiver(SpectateNetworking.TARGET_UPDATE_PACKET_ID, (client, handler, buf, responseSender) -> {
        //$$    TargetUpdatePayload payload = TargetUpdatePayload.read(buf);
        //$$    client.execute(() -> {
        //$$        ClientSpectateManager.getInstance().handleTargetUpdate(payload);
        //$$    });
        //$$});
        //#endif
    }
}
