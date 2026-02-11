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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
//#if MC < 11900
//$$ import net.minecraft.text.LiteralText;
//#endif
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * 客户端Mod入口
 * 负责客户端的初始化和网络包注册
 */
@Environment(EnvType.CLIENT)
public class SpectateModClient implements ClientModInitializer {

    private static final String KEY_CATEGORY = "key.categories.spectate";
    private static final String MESSAGE_PREFIX = "[Spectate] ";

    private KeyBinding toggleTiltShiftKey;
    private KeyBinding decreaseTiltShiftKey;
    private KeyBinding increaseTiltShiftKey;

    @Override
    public void onInitializeClient() {
        SpectateMod.LOGGER.info("Spectate client mod initializing...");

        // 注册客户端网络包接收处理器
        registerClientPacketReceivers();
        registerClientKeyBindings();

        // 注册客户端Tick事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleTiltShiftHotkeys(client);
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

    private void registerClientKeyBindings() {
        this.toggleTiltShiftKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spectate.tiltshift.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_T, KEY_CATEGORY)
        );
        this.decreaseTiltShiftKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spectate.tiltshift.decrease", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, KEY_CATEGORY)
        );
        this.increaseTiltShiftKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spectate.tiltshift.increase", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, KEY_CATEGORY)
        );
    }

    private void handleTiltShiftHotkeys(MinecraftClient client) {
        if (client.currentScreen != null || client.player == null) {
            return;
        }
        if (toggleTiltShiftKey == null || decreaseTiltShiftKey == null || increaseTiltShiftKey == null) {
            return;
        }

        ClientSpectateManager manager = ClientSpectateManager.getInstance();

        while (toggleTiltShiftKey.wasPressed()) {
            if (!manager.isSpectating()) {
                sendClientOverlayMessage(client, "移轴快捷键仅在旁观时可用");
                continue;
            }
            boolean enabled = manager.getTiltShiftSettings().toggleEnabled();
            sendClientOverlayMessage(client, "移轴效果: " + (enabled ? "已开启" : "已关闭"));
        }

        while (decreaseTiltShiftKey.wasPressed()) {
            handleTiltShiftAdjust(manager, client, -1.0);
        }
        while (increaseTiltShiftKey.wasPressed()) {
            handleTiltShiftAdjust(manager, client, 1.0);
        }
    }

    private void handleTiltShiftAdjust(ClientSpectateManager manager, MinecraftClient client, double direction) {
        if (!manager.isSpectating()) {
            sendClientOverlayMessage(client, "移轴快捷键仅在旁观时可用");
            return;
        }

        TiltShiftSettings settings = manager.getTiltShiftSettings();
        if (isShiftPressed(client)) {
            double blurRadius = settings.adjustBlurRadius(direction * 0.5);
            sendClientOverlayMessage(client, "移轴模糊强度: " + format2(blurRadius));
            return;
        }

        double focusY = settings.adjustFocusY(direction * 0.02);
        sendClientOverlayMessage(client, "移轴焦点位置: " + format2(focusY));
    }

    private boolean isShiftPressed(MinecraftClient client) {
        long handle = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private String format2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void sendClientOverlayMessage(MinecraftClient client, String message) {
        //#if MC >= 11900
        client.player.sendMessage(Text.literal(MESSAGE_PREFIX + message), true);
        //#else
        //$$ client.player.sendMessage(new LiteralText(MESSAGE_PREFIX + message), true);
        //#endif
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
