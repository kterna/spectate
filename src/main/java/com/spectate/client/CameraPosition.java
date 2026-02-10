package com.spectate.client;

/**
 * 摄像机位置数据类
 * 存储摄像机的位置和朝向信息
 */
public class CameraPosition {
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;

    public CameraPosition(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * 线性插值两个摄像机位置
     * @param from 起始位置
     * @param to 目标位置
     * @param t 插值因子 (0.0 - 1.0)
     * @return 插值后的位置
     */
    public static CameraPosition lerp(CameraPosition from, CameraPosition to, float t) {
        if (from == null) return to;
        if (to == null) return from;

        double x = from.x + (to.x - from.x) * t;
        double y = from.y + (to.y - from.y) * t;
        double z = from.z + (to.z - from.z) * t;

        // 对yaw进行角度插值（处理绕圈问题）
        float yaw = lerpAngle(from.yaw, to.yaw, t);
        float pitch = from.pitch + (to.pitch - from.pitch) * t;

        return new CameraPosition(x, y, z, yaw, pitch);
    }

    /**
     * 角度插值，处理角度环绕问题
     */
    private static float lerpAngle(float from, float to, float t) {
        float diff = to - from;

        // 规范化角度差到 -180 到 180 范围
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;

        return from + diff * t;
    }

    @Override
    public String toString() {
        return String.format("CameraPosition[x=%.2f, y=%.2f, z=%.2f, yaw=%.2f, pitch=%.2f]",
                x, y, z, yaw, pitch);
    }
}
