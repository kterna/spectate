package com.spectate.mixin.client;

import com.spectate.SpectateMod;
import com.spectate.client.ClientSpectateManager;
import com.spectate.client.TiltShiftSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在世界渲染后应用自定义移轴后处理。
 */
@Mixin(net.minecraft.client.render.GameRenderer.class)
public abstract class GameRendererMixin {

    //#if MC >= 12100
    private static final net.minecraft.util.Identifier SPECTATE_TILTSHIFT_EFFECT_ID = net.minecraft.util.Identifier.of(SpectateMod.MOD_ID, "tiltshift");
    //#else
    //$$ private static final net.minecraft.util.Identifier SPECTATE_TILTSHIFT_EFFECT_ID = new net.minecraft.util.Identifier(SpectateMod.MOD_ID, "tiltshift");
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
            //$$ // 1.21.7+ API 不再提供 setUniforms/RenderPass consumer，这里使用资源默认参数渲染。
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
    //#endif
}
