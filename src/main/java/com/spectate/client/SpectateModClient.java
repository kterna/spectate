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
 * Client-side mod entry point.
 * Handles client initialization and packet receivers.
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

        // Register client packet receivers.
        registerClientPacketReceivers();
        registerClientKeyBindings();

        // Register end-client-tick callback.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleTiltShiftHotkeys(client);
            ClientSpectateManager.getInstance().onClientTick();
        });

        // Register connection lifecycle events.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Delay capability declaration until after the join flow is ready.
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
                new KeyBinding("key.spectate.tiltshift.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, KEY_CATEGORY)
        );
        this.decreaseTiltShiftKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spectate.tiltshift.decrease", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, KEY_CATEGORY)
        );
        this.increaseTiltShiftKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spectate.tiltshift.increase", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, KEY_CATEGORY)
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
                sendClientOverlayMessage(client, tr("message.spectate.tiltshift.only_spectating"));
                continue;
            }
            boolean enabled = manager.getTiltShiftSettings().toggleEnabled();
            sendClientOverlayMessage(client, enabled ? tr("message.spectate.tiltshift.enabled") : tr("message.spectate.tiltshift.disabled"));
            if (enabled) {
                sendClientOverlayMessage(client, tr("warning.spectate.tiltshift.experimental"));
            }
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
            sendClientOverlayMessage(client, tr("message.spectate.tiltshift.only_spectating"));
            return;
        }

        TiltShiftSettings settings = manager.getTiltShiftSettings();
        if (isShiftPressed(client)) {
            double blurRadius = settings.adjustBlurRadius(direction * 0.5);
            sendClientOverlayMessage(client, String.format(Locale.ROOT, tr("message.spectate.tiltshift.blur_radius"), format2(blurRadius)));
            return;
        }

        double focusY = settings.adjustFocusY(direction * 0.02);
        sendClientOverlayMessage(client, String.format(Locale.ROOT, tr("message.spectate.tiltshift.focus_y"), format2(focusY)));
    }

    private boolean isShiftPressed(MinecraftClient client) {
        long handle = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private String format2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String tr(String key) {
        //#if MC >= 11900
        return Text.translatable(key).getString();
        //#else
        //$$ return new net.minecraft.text.TranslatableText(key).getString();
        //#endif
    }

    private void sendClientOverlayMessage(MinecraftClient client, String message) {
        //#if MC >= 11900
        client.player.sendMessage(Text.literal(MESSAGE_PREFIX + message), false);
        //#else
        //$$ client.player.sendMessage(new LiteralText(MESSAGE_PREFIX + message), false);
        //#endif
    }

    /**
     * Register handlers for packets sent by the server.
     */
    private void registerClientPacketReceivers() {
        //#if MC >= 12005
        // Spectate state updates
        ClientPlayNetworking.registerGlobalReceiver(SpectateStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientSpectateManager.getInstance().handleStatePayload(payload);
            });
        });

        // Spectate param updates
        ClientPlayNetworking.registerGlobalReceiver(SpectateParamsPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientSpectateManager.getInstance().handleParamsPayload(payload);
            });
        });

        // Target updates
        ClientPlayNetworking.registerGlobalReceiver(TargetUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientSpectateManager.getInstance().handleTargetUpdate(payload);
            });
        });
        //#else
        //$$// Legacy API path for older versions
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
