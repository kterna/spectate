package com.spectate.mixin.client;

import com.spectate.client.CameraPosition;
import com.spectate.client.ClientSpectateManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 摄像机Mixin
 * 在客户端控制旁观时覆盖摄像机位置
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private boolean ready;

    @Shadow
    private BlockView area;

    @Shadow
    private Entity focusedEntity;

    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    /**
     * 在摄像机更新时注入，检查是否需要覆盖位置
     */
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson,
                          boolean inverseView, float tickDelta, CallbackInfo ci) {
        ClientSpectateManager manager = ClientSpectateManager.getInstance();

        if (manager.isSpectating()) {
            CameraPosition pos = manager.getCameraPosition(tickDelta);

            if (pos != null) {
                // 设置基本状态
                this.ready = true;
                this.area = area;
                this.focusedEntity = focusedEntity;
                this.thirdPerson = true; // 平滑旁观模式使用第三人称视角

                // 设置位置和朝向
                setPos(pos.x, pos.y, pos.z);
                setRotation(pos.yaw, pos.pitch);

                // 取消原版的更新逻辑
                ci.cancel();
            }
        }
    }
}
