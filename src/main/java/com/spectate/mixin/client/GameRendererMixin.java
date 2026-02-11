package com.spectate.mixin.client;

import com.spectate.SpectateMod;
import com.spectate.client.ClientSpectateManager;
import com.spectate.client.TiltShiftSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在世界渲染后应用自定义移轴后处理。
 */
@Mixin(net.minecraft.client.render.GameRenderer.class)
public abstract class GameRendererMixin {

    //#if MC >= 12005
    private static final net.minecraft.util.Identifier SPECTATE_TILTSHIFT_EFFECT_ID = net.minecraft.util.Identifier.of(SpectateMod.MOD_ID, "tiltshift");
    //#else
    //$$ private static final net.minecraft.util.Identifier SPECTATE_TILTSHIFT_EFFECT_ID = new net.minecraft.util.Identifier(SpectateMod.MOD_ID, "tiltshift");
    //#endif

    //#if MC >= 12005
    @Shadow
    @Final
    private net.minecraft.client.MinecraftClient client;

    @Shadow
    @Final
    private net.minecraft.client.util.Pool pool;

    @Shadow
    public abstract net.minecraft.util.Identifier getPostProcessorId();

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V",
                    shift = At.Shift.AFTER
            )
    )
    private void spectate$renderTiltShift(net.minecraft.client.render.RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (client.world == null) {
            return;
        }

        ClientSpectateManager manager = ClientSpectateManager.getInstance();
        TiltShiftSettings tiltShift = manager.getTiltShiftSettings();
        if (!manager.isSpectating() || !tiltShift.isEnabled()) {
            return;
        }

        // 避免覆盖其他已启用的后处理效果
        net.minecraft.util.Identifier activePostEffect = getPostProcessorId();
        if (activePostEffect != null && !SPECTATE_TILTSHIFT_EFFECT_ID.equals(activePostEffect)) {
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

            postEffect.render(client.getFramebuffer(), this.pool, renderPass -> {
                renderPass.setUniform("FocusY", new float[]{(float) tiltShift.getFocusY()});
                renderPass.setUniform("FocusWidth", new float[]{(float) tiltShift.getFocusWidth()});
                renderPass.setUniform("BlurRadius", new float[]{(float) tiltShift.getBlurRadius()});
                renderPass.setUniform("Falloff", new float[]{(float) tiltShift.getFalloff()});
                renderPass.setUniform("SaturationBoost", new float[]{(float) tiltShift.getSaturationBoost()});
            });
        } catch (Exception e) {
            SpectateMod.LOGGER.warn("[Spectate] Failed to apply tilt-shift post effect", e);
        }
    }
    //#endif
}

