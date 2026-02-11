package com.spectate.mixin.client;

import com.spectate.SpectateMod;
import com.spectate.client.ClientSpectateManager;
import com.spectate.client.TiltShiftSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies tilt-shift post processing while client-side spectating is active.
 */
@Mixin(net.minecraft.client.render.GameRenderer.class)
public abstract class GameRendererMixin {

    //#if MC >= 12104
    private static final net.minecraft.util.Identifier SPECTATE_TILTSHIFT_EFFECT_ID = net.minecraft.util.Identifier.of(SpectateMod.MOD_ID, "tiltshift");
    //#elseif MC >= 12100
    //$$ private static final net.minecraft.util.Identifier SPECTATE_TILTSHIFT_EFFECT_ID = net.minecraft.util.Identifier.of(SpectateMod.MOD_ID, "shaders/post/tiltshift.json");
    //#else
    //$$ private static final net.minecraft.util.Identifier SPECTATE_TILTSHIFT_EFFECT_ID = new net.minecraft.util.Identifier(SpectateMod.MOD_ID, "tiltshift");
    //#endif

    //#if MC == 12100
    //$$ private net.minecraft.client.gl.PostEffectProcessor spectate$legacyTiltShiftEffect;
    //$$ private int spectate$legacyTiltShiftWidth = -1;
    //$$ private int spectate$legacyTiltShiftHeight = -1;
    //$$
    //$$ private net.minecraft.client.gl.PostEffectProcessor spectate$getLegacyTiltShiftEffect(net.minecraft.client.MinecraftClient client) throws Exception {
    //$$     if (spectate$legacyTiltShiftEffect == null) {
    //$$         spectate$legacyTiltShiftEffect = new net.minecraft.client.gl.PostEffectProcessor(
    //$$                 client.getTextureManager(),
    //$$                 client.getResourceManager(),
    //$$                 client.getFramebuffer(),
    //$$                 SPECTATE_TILTSHIFT_EFFECT_ID
    //$$         );
    //$$         spectate$legacyTiltShiftWidth = -1;
    //$$         spectate$legacyTiltShiftHeight = -1;
    //$$     }
    //$$
    //$$     int width = client.getWindow().getFramebufferWidth();
    //$$     int height = client.getWindow().getFramebufferHeight();
    //$$     if (width != spectate$legacyTiltShiftWidth || height != spectate$legacyTiltShiftHeight) {
    //$$         spectate$legacyTiltShiftEffect.setupDimensions(width, height);
    //$$         spectate$legacyTiltShiftWidth = width;
    //$$         spectate$legacyTiltShiftHeight = height;
    //$$     }
    //$$
    //$$     return spectate$legacyTiltShiftEffect;
    //$$ }
    //#elseif MC == 12101
    //$$ private net.minecraft.client.gl.PostEffectProcessor spectate$legacyTiltShiftEffect;
    //$$ private int spectate$legacyTiltShiftWidth = -1;
    //$$ private int spectate$legacyTiltShiftHeight = -1;
    //$$
    //$$ private net.minecraft.client.gl.PostEffectProcessor spectate$getLegacyTiltShiftEffect(net.minecraft.client.MinecraftClient client) throws Exception {
    //$$     if (spectate$legacyTiltShiftEffect == null) {
    //$$         spectate$legacyTiltShiftEffect = new net.minecraft.client.gl.PostEffectProcessor(
    //$$                 client.getTextureManager(),
    //$$                 client.getResourceManager(),
    //$$                 client.getFramebuffer(),
    //$$                 SPECTATE_TILTSHIFT_EFFECT_ID
    //$$         );
    //$$         spectate$legacyTiltShiftWidth = -1;
    //$$         spectate$legacyTiltShiftHeight = -1;
    //$$     }
    //$$
    //$$     int width = client.getWindow().getFramebufferWidth();
    //$$     int height = client.getWindow().getFramebufferHeight();
    //$$     if (width != spectate$legacyTiltShiftWidth || height != spectate$legacyTiltShiftHeight) {
    //$$         spectate$legacyTiltShiftEffect.setupDimensions(width, height);
    //$$         spectate$legacyTiltShiftWidth = width;
    //$$         spectate$legacyTiltShiftHeight = height;
    //$$     }
    //$$
    //$$     return spectate$legacyTiltShiftEffect;
    //$$ }
    //#endif

    //#if MC >= 12104
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V",
                    shift = At.Shift.AFTER
            )
    )
    @SuppressWarnings("deprecation")
    private void spectate$renderTiltShift(net.minecraft.client.render.RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        ClientSpectateManager manager = ClientSpectateManager.getInstance();
        TiltShiftSettings tiltShift = manager.getTiltShiftSettings();
        if (!manager.isSpectating() || !tiltShift.isEnabled()) {
            return;
        }

        try {
            net.minecraft.client.gl.PostEffectProcessor postEffect = client.getShaderLoader()
                    .loadPostEffect(
                            SPECTATE_TILTSHIFT_EFFECT_ID,
                            net.minecraft.client.render.DefaultFramebufferSet.MAIN_ONLY
                    );
            if (postEffect == null) {
                return;
            }
            //#if MC == 12105
            postEffect.render(client.getFramebuffer(), net.minecraft.client.util.ObjectAllocator.TRIVIAL, renderPass -> {
                renderPass.setUniform("FocusY", new float[]{(float) tiltShift.getFocusY()});
                renderPass.setUniform("FocusWidth", new float[]{(float) tiltShift.getFocusWidth()});
                renderPass.setUniform("BlurRadius", new float[]{(float) tiltShift.getBlurRadius()});
                renderPass.setUniform("Falloff", new float[]{(float) tiltShift.getFalloff()});
                renderPass.setUniform("SaturationBoost", new float[]{(float) tiltShift.getSaturationBoost()});
            });
            //#elseif MC >= 12107
            //$$ // 1.21.7+ removed runtime uniform updates; use defaults from post_effect json.
            //$$ postEffect.render(client.getFramebuffer(), net.minecraft.client.util.ObjectAllocator.TRIVIAL);
            //#else
            //$$ postEffect.setUniforms("FocusY", (float) tiltShift.getFocusY());
            //$$ postEffect.setUniforms("FocusWidth", (float) tiltShift.getFocusWidth());
            //$$ postEffect.setUniforms("BlurRadius", (float) tiltShift.getBlurRadius());
            //$$ postEffect.setUniforms("Falloff", (float) tiltShift.getFalloff());
            //$$ postEffect.setUniforms("SaturationBoost", (float) tiltShift.getSaturationBoost());
            //$$ postEffect.render(client.getFramebuffer(), net.minecraft.client.util.ObjectAllocator.TRIVIAL);
            //#endif
        } catch (Exception e) {
            SpectateMod.LOGGER.warn("[Spectate] Failed to apply tilt-shift post effect", e);
        }
    }
    //#elseif MC == 12100
    //$$ @Inject(
    //$$         method = "render",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V",
    //$$                 shift = At.Shift.AFTER
    //$$         )
    //$$ )
    //$$ private void spectate$renderTiltShiftLegacy(net.minecraft.client.render.RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
    //$$     net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
    //$$     if (client.world == null) {
    //$$         return;
    //$$     }
    //$$
    //$$     ClientSpectateManager manager = ClientSpectateManager.getInstance();
    //$$     TiltShiftSettings tiltShift = manager.getTiltShiftSettings();
    //$$     if (!manager.isSpectating() || !tiltShift.isEnabled()) {
    //$$         return;
    //$$     }
    //$$
    //$$     try {
    //$$         net.minecraft.client.gl.PostEffectProcessor postEffect = spectate$getLegacyTiltShiftEffect(client);
    //$$         postEffect.setUniforms("FocusY", (float) tiltShift.getFocusY());
    //$$         postEffect.setUniforms("FocusWidth", (float) tiltShift.getFocusWidth());
    //$$         postEffect.setUniforms("BlurRadius", (float) tiltShift.getBlurRadius());
    //$$         postEffect.setUniforms("Falloff", (float) tiltShift.getFalloff());
    //$$         postEffect.setUniforms("SaturationBoost", (float) tiltShift.getSaturationBoost());
    //$$         postEffect.render(tickCounter.getTickDelta(false));
    //$$     } catch (Exception e) {
    //$$         spectate$legacyTiltShiftEffect = null;
    //$$         SpectateMod.LOGGER.warn("[Spectate] Failed to apply legacy tilt-shift post effect", e);
    //$$     }
    //$$ }
    //#elseif MC == 12101
    //$$ @Inject(
    //$$         method = "render",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V",
    //$$                 shift = At.Shift.AFTER
    //$$         )
    //$$ )
    //$$ private void spectate$renderTiltShiftLegacy(net.minecraft.client.render.RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
    //$$     net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
    //$$     if (client.world == null) {
    //$$         return;
    //$$     }
    //$$
    //$$     ClientSpectateManager manager = ClientSpectateManager.getInstance();
    //$$     TiltShiftSettings tiltShift = manager.getTiltShiftSettings();
    //$$     if (!manager.isSpectating() || !tiltShift.isEnabled()) {
    //$$         return;
    //$$     }
    //$$
    //$$     try {
    //$$         net.minecraft.client.gl.PostEffectProcessor postEffect = spectate$getLegacyTiltShiftEffect(client);
    //$$         postEffect.setUniforms("FocusY", (float) tiltShift.getFocusY());
    //$$         postEffect.setUniforms("FocusWidth", (float) tiltShift.getFocusWidth());
    //$$         postEffect.setUniforms("BlurRadius", (float) tiltShift.getBlurRadius());
    //$$         postEffect.setUniforms("Falloff", (float) tiltShift.getFalloff());
    //$$         postEffect.setUniforms("SaturationBoost", (float) tiltShift.getSaturationBoost());
    //$$         postEffect.render(tickCounter.getTickDelta(false));
    //$$     } catch (Exception e) {
    //$$         spectate$legacyTiltShiftEffect = null;
    //$$         SpectateMod.LOGGER.warn("[Spectate] Failed to apply legacy tilt-shift post effect", e);
    //$$     }
    //$$ }
    //#endif
}
