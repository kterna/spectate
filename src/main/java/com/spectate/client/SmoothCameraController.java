package com.spectate.client;

import com.spectate.service.FloatingCamera;
import com.spectate.service.ViewMode;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side smooth camera controller.
 * Computes camera position each frame from server target updates.
 */
public class SmoothCameraController {
    private static final double TARGET_POSITION_SMOOTH_TIME_SEC = 0.10;
    private static final double TARGET_VELOCITY_SMOOTH_TIME_SEC = 0.12;
    private static final double MAX_PREDICTION_AHEAD_SEC = 0.20;

    // Camera params
    private double distance = 8.0;
    private double heightOffset = 2.0;
    private double rotationSpeed = 5.0;

    // Floating mode params
    private double floatingStrength = 0.5;
    private double floatingSpeed = 0.3;
    private double dampingFactor = 0.95;
    private double attractionFactor = 0.3;

    // State
    private ViewMode viewMode = ViewMode.ORBIT;
    private long startTimestamp;
    private double currentAngle;

    // Raw target state from network
    private double targetX, targetY, targetZ;
    private double targetVelX, targetVelY, targetVelZ;

    // Smoothed target state used for rendering
    private double smoothTargetX, smoothTargetY, smoothTargetZ;
    private double smoothTargetVelX, smoothTargetVelY, smoothTargetVelZ;
    private boolean targetStateInitialized;
    private long lastTargetUpdateTime;

    // Frame interpolation cache
    private CameraPosition lastPosition;
    private CameraPosition currentPosition;
    private long lastUpdateTime;

    private final FloatingCamera floatingCamera;

    public SmoothCameraController() {
        this.floatingCamera = new FloatingCamera();
        this.startTimestamp = System.currentTimeMillis();
        this.lastUpdateTime = System.currentTimeMillis();
        this.lastTargetUpdateTime = System.currentTimeMillis();
    }

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

        floatingCamera.setFloatingStrength(floatingStrength);
        floatingCamera.setFloatingSpeed(floatingSpeed);
        floatingCamera.setDampingFactor(dampingFactor);
        floatingCamera.setAttractionFactor(attractionFactor);
        floatingCamera.setOrbitRadius(distance);
    }

    public void setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode;
        if (viewMode == ViewMode.CINEMATIC_FLOATING) {
            floatingCamera.reset();
        }
    }

    public void updateTarget(double x, double y, double z, double velX, double velY, double velZ, long serverTime) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetVelX = velX;
        this.targetVelY = velY;
        this.targetVelZ = velZ;

        // Use client receive time as the baseline for prediction.
        this.lastTargetUpdateTime = System.currentTimeMillis();

        if (!targetStateInitialized) {
            this.smoothTargetX = x;
            this.smoothTargetY = y;
            this.smoothTargetZ = z;
            this.smoothTargetVelX = velX;
            this.smoothTargetVelY = velY;
            this.smoothTargetVelZ = velZ;
            this.targetStateInitialized = true;
        }
    }

    public void updateTarget(double x, double y, double z) {
        updateTarget(x, y, z, 0, 0, 0, System.currentTimeMillis());
    }

    private void updateTargetSmoothing(double deltaTime) {
        if (!targetStateInitialized || deltaTime <= 0) {
            return;
        }

        double clampedDelta = Math.min(deltaTime, 0.1);
        double posAlpha = 1.0 - Math.exp(-clampedDelta / TARGET_POSITION_SMOOTH_TIME_SEC);
        double velAlpha = 1.0 - Math.exp(-clampedDelta / TARGET_VELOCITY_SMOOTH_TIME_SEC);

        smoothTargetX += (targetX - smoothTargetX) * posAlpha;
        smoothTargetY += (targetY - smoothTargetY) * posAlpha;
        smoothTargetZ += (targetZ - smoothTargetZ) * posAlpha;
        smoothTargetVelX += (targetVelX - smoothTargetVelX) * velAlpha;
        smoothTargetVelY += (targetVelY - smoothTargetVelY) * velAlpha;
        smoothTargetVelZ += (targetVelZ - smoothTargetVelZ) * velAlpha;
    }

    private Vec3d getPredictedTargetPosition() {
        if (!targetStateInitialized) {
            return new Vec3d(targetX, targetY, targetZ);
        }

        long now = System.currentTimeMillis();
        double deltaSeconds = (now - lastTargetUpdateTime) / 1000.0;
        deltaSeconds = Math.max(0.0, Math.min(deltaSeconds, MAX_PREDICTION_AHEAD_SEC));

        double predX = smoothTargetX + smoothTargetVelX * deltaSeconds;
        double predY = smoothTargetY + smoothTargetVelY * deltaSeconds;
        double predZ = smoothTargetZ + smoothTargetVelZ * deltaSeconds;

        return new Vec3d(predX, predY, predZ);
    }

    public void update(double deltaTime) {
        lastPosition = currentPosition;
        updateTargetSmoothing(deltaTime);
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

        double dx = target.x - camX;
        double dy = target.y - camY;
        double dz = target.z - camZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        return new CameraPosition(camX, camY, camZ, yaw, pitch);
    }

    private CameraPosition updateFollow(Vec3d target, double deltaTime) {
        double followDistance = 5.0;
        double followHeight = 1.5;

        double camX = target.x;
        double camY = target.y + followHeight;
        double camZ = target.z - followDistance;

        double dx = target.x - camX;
        double dy = target.y - camY;
        double dz = target.z - camZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        return new CameraPosition(camX, camY, camZ, yaw, pitch);
    }

    private CameraPosition updateFloating(Vec3d target, double deltaTime) {
        double[] result = new double[5];
        floatingCamera.updatePosition(target.x, target.y, target.z, deltaTime, result);
        return new CameraPosition(result[0], result[1], result[2], (float) result[3], (float) result[4]);
    }

    private CameraPosition updateCinematicOther(Vec3d target, double deltaTime) {
        double elapsedSeconds = (System.currentTimeMillis() - startTimestamp) / 1000.0;
        double camX;
        double camY;
        double camZ;

        if (viewMode == ViewMode.CINEMATIC_AERIAL_VIEW) {
            camX = target.x;
            camY = target.y + 25.0;
            camZ = target.z;
        } else if (viewMode == ViewMode.CINEMATIC_SPIRAL_UP) {
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
            double slowRotSpeed = 0.5;
            double angleRad = (elapsedSeconds * slowRotSpeed) * Math.PI / 180.0;

            double camXo = Math.sin(angleRad) * Math.max(distance, 8.0);
            double camZo = Math.cos(angleRad) * Math.max(distance, 8.0);
            camX = target.x + camXo;
            camY = target.y + heightOffset + 2.0;
            camZ = target.z + camZo;
        }

        double dx = target.x - camX;
        double dy = target.y - camY;
        double dz = target.z - camZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        return new CameraPosition(camX, camY, camZ, yaw, pitch);
    }

    public CameraPosition getInterpolated(float tickDelta) {
        return CameraPosition.lerp(lastPosition, currentPosition, tickDelta);
    }

    public CameraPosition getCurrentPosition() {
        return currentPosition;
    }

    public void reset() {
        lastPosition = null;
        currentPosition = null;
        floatingCamera.reset();
        currentAngle = 0;
        startTimestamp = System.currentTimeMillis();

        targetX = targetY = targetZ = 0;
        targetVelX = targetVelY = targetVelZ = 0;
        smoothTargetX = smoothTargetY = smoothTargetZ = 0;
        smoothTargetVelX = smoothTargetVelY = smoothTargetVelZ = 0;
        targetStateInitialized = false;
        lastTargetUpdateTime = System.currentTimeMillis();
    }
}
