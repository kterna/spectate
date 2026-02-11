package com.spectate.client;

import com.spectate.service.FloatingCamera;
import com.spectate.service.ViewMode;
import net.minecraft.util.math.Vec3d;

/**
 * 平滑摄像机控制器
 * 复用 FloatingCamera 物理算法，在客户端每帧计算摄像机位置
 */
public class SmoothCameraController {

    // 摄像机参数
    private double distance = 8.0;
    private double heightOffset = 2.0;
    private double rotationSpeed = 5.0;

    // FloatingCamera 参数
    private double floatingStrength = 0.5;
    private double floatingSpeed = 0.3;
    private double dampingFactor = 0.95;
    private double attractionFactor = 0.3;

    // 状态变量
    private ViewMode viewMode = ViewMode.ORBIT;
    private long startTimestamp;
    private double currentAngle;

    // 目标位置和速度（用于预测）
    private double targetX, targetY, targetZ;
    private double targetVelX, targetVelY, targetVelZ;
    private long lastTargetUpdateTime;

    // 位置缓存（用于帧间插值）
    private CameraPosition lastPosition;
    private CameraPosition currentPosition;
    private long lastUpdateTime;

    // FloatingCamera 实例（用于 FLOATING 模式）
    private FloatingCamera floatingCamera;

    public SmoothCameraController() {
        this.floatingCamera = new FloatingCamera();
        this.startTimestamp = System.currentTimeMillis();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 设置摄像机参数
     */
    public void setParams(double distance, double heightOffset, double rotationSpeed,
                          double floatingStrength, double floatingSpeed,
                          double dampingFactor, double attractionFactor,
                          double initialAngle, long startTimestamp) {
        this.distance = distance;
        this.heightOffset = heightOffset;
        this.rotationSpeed = rotationSpeed;
        this.floatingStrength = floatingStrength;
        this.floatingSpeed = floatingSpeed;
        this.dampingFactor = dampingFactor;
        this.attractionFactor = attractionFactor;
        this.currentAngle = initialAngle;
        this.startTimestamp = startTimestamp;

        // 更新 FloatingCamera 参数
        floatingCamera.setFloatingStrength(floatingStrength);
        floatingCamera.setFloatingSpeed(floatingSpeed);
        floatingCamera.setDampingFactor(dampingFactor);
        floatingCamera.setAttractionFactor(attractionFactor);
        floatingCamera.setOrbitRadius(distance);
    }

    /**
     * 设置视角模式
     */
    public void setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode;

        // 如果切换模式，重置浮游摄像机
        if (viewMode == ViewMode.CINEMATIC_FLOATING) {
            floatingCamera.reset();
        }
    }

    /**
     * 更新目标位置（从服务端接收）
     */
    public void updateTarget(double x, double y, double z, double velX, double velY, double velZ, long serverTime) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetVelX = velX;
        this.targetVelY = velY;
        this.targetVelZ = velZ;
        this.lastTargetUpdateTime = serverTime;
    }

    /**
     * 更新目标位置（静态目标）
     */
    public void updateTarget(double x, double y, double z) {
        updateTarget(x, y, z, 0, 0, 0, System.currentTimeMillis());
    }

    /**
     * 获取预测的目标位置
     */
    private Vec3d getPredictedTargetPosition() {
        long now = System.currentTimeMillis();
        double deltaSeconds = (now - lastTargetUpdateTime) / 1000.0;

        // 限制预测时间，避免目标位置跑太远
        deltaSeconds = Math.min(deltaSeconds, 0.5);

        double predX = targetX + targetVelX * deltaSeconds;
        double predY = targetY + targetVelY * deltaSeconds;
        double predZ = targetZ + targetVelZ * deltaSeconds;

        return new Vec3d(predX, predY, predZ);
    }

    /**
     * 每帧更新摄像机位置
     * @param deltaTime 帧间隔时间（秒）
     */
    public void update(double deltaTime) {
        lastPosition = currentPosition;
        Vec3d target = getPredictedTargetPosition();

        switch (viewMode) {
            case ORBIT:
                currentPosition = updateOrbit(target, deltaTime);
                break;
            case FOLLOW:
                currentPosition = updateFollow(target, deltaTime);
                break;
            case CINEMATIC_FLOATING:
                currentPosition = updateFloating(target, deltaTime);
                break;
            case CINEMATIC_AERIAL_VIEW:
            case CINEMATIC_SPIRAL_UP:
            case CINEMATIC_SLOW_ORBIT:
                currentPosition = updateCinematicOther(target, deltaTime);
                break;
            default:
                currentPosition = updateOrbit(target, deltaTime);
                break;
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 轨道视角更新
     */
    private CameraPosition updateOrbit(Vec3d target, double deltaTime) {
        double elapsedSeconds = (System.currentTimeMillis() - startTimestamp) / 1000.0;

        double angleRad = 0;
        if (rotationSpeed > 0) {
            double periodSec = 360.0 / rotationSpeed;
            angleRad = (elapsedSeconds % periodSec) / periodSec * 2 * Math.PI;
        }

        double camXo = Math.sin(angleRad) * distance;
        double camZo = Math.cos(angleRad) * distance;
        double camX = target.x + camXo;
        double camY = target.y + heightOffset;
        double camZ = target.z + camZo;

        // 计算朝向
        double dx = target.x - camX;
        double dy = target.y - camY;
        double dz = target.z - camZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        return new CameraPosition(camX, camY, camZ, yaw, pitch);
    }

    /**
     * 跟随视角更新
     */
    private CameraPosition updateFollow(Vec3d target, double deltaTime) {
        double followDistance = 5.0;
        double followHeight = 1.5;

        // 跟随视角需要目标朝向，这里简化为始终在目标后方
        // 实际使用时可以从服务端获取目标朝向
        double camX = target.x;
        double camY = target.y + followHeight;
        double camZ = target.z - followDistance;

        // 计算朝向
        double dx = target.x - camX;
        double dy = target.y - camY;
        double dz = target.z - camZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        return new CameraPosition(camX, camY, camZ, yaw, pitch);
    }

    /**
     * 浮游视角更新（使用 FloatingCamera 物理模拟）
     */
    private CameraPosition updateFloating(Vec3d target, double deltaTime) {
        double[] result = new double[5];
        floatingCamera.updatePosition(target.x, target.y, target.z, deltaTime, result);

        return new CameraPosition(result[0], result[1], result[2], (float) result[3], (float) result[4]);
    }

    /**
     * 其他电影模式更新
     */
    private CameraPosition updateCinematicOther(Vec3d target, double deltaTime) {
        double elapsedSeconds = (System.currentTimeMillis() - startTimestamp) / 1000.0;
        double camX, camY, camZ;

        if (viewMode == ViewMode.CINEMATIC_AERIAL_VIEW) {
            // 高空俯瞰
            camX = target.x;
            camY = target.y + 25.0;
            camZ = target.z;
        } else if (viewMode == ViewMode.CINEMATIC_SPIRAL_UP) {
            // 螺旋上升
            double spiralSpeed = 1.0;
            double riseSpeed = 0.3;
            double angleRad = (elapsedSeconds * spiralSpeed) * Math.PI / 180.0;
            double currentHeight = heightOffset + (elapsedSeconds * riseSpeed);

            double camXo = Math.sin(angleRad) * distance;
            double camZo = Math.cos(angleRad) * distance;
            camX = target.x + camXo;
            camY = target.y + currentHeight;
            camZ = target.z + camZo;
        } else {
            // SLOW_ORBIT 或默认
            double slowRotSpeed = 0.5;
            double angleRad = (elapsedSeconds * slowRotSpeed) * Math.PI / 180.0;

            double camXo = Math.sin(angleRad) * Math.max(distance, 8.0);
            double camZo = Math.cos(angleRad) * Math.max(distance, 8.0);
            camX = target.x + camXo;
            camY = target.y + heightOffset + 2.0;
            camZ = target.z + camZo;
        }

        // 计算朝向
        double dx = target.x - camX;
        double dy = target.y - camY;
        double dz = target.z - camZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        return new CameraPosition(camX, camY, camZ, yaw, pitch);
    }

    /**
     * 获取帧间插值的摄像机位置
     * @param tickDelta 帧内插值因子 (0.0 - 1.0)
     * @return 插值后的摄像机位置
     */
    public CameraPosition getInterpolated(float tickDelta) {
        return CameraPosition.lerp(lastPosition, currentPosition, tickDelta);
    }

    /**
     * 获取当前摄像机位置（不插值）
     */
    public CameraPosition getCurrentPosition() {
        return currentPosition;
    }

    /**
     * 重置控制器状态
     */
    public void reset() {
        lastPosition = null;
        currentPosition = null;
        floatingCamera.reset();
        currentAngle = 0;
        startTimestamp = System.currentTimeMillis();
    }
}
