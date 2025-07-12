package com.spectate.data;

// Minecraft 位置相关类根据不同 MC 版本可能在不同包名，
// 这里统一使用 net.minecraft.util.math.BlockPos 作为存储类型。
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import java.util.Objects;

/**
 * SpectatePointData 描述一个静态观察点。
 *
 * dimension: 维度标识符。
 * position: 目标世界坐标（BlockPos）。
 * distance: 摄像机与目标的水平距离（方块单位）。
 * heightOffset: 摄像机相对目标的垂直高度偏移。
 * rotationSpeedDegPerSec: 每秒旋转角速度，单位：度/秒，0 表示不旋转。
 * description: 人类可读的描述信息，用于命令列表展示。
 */
public class SpectatePointData {
    private final String dimension;
    private final BlockPos position;
    private final double distance;
    private final double heightOffset;
    /** 每秒旋转角速度，单位：度/秒，0 表示不旋转 */
    private final double rotationSpeedDegPerSec;
    private final String description;

    public SpectatePointData(String dimension, BlockPos position, double distance, double heightOffset, double rotationSpeedDegPerSec, String description) {
        this.dimension = dimension;
        this.position = position;
        this.distance = distance;
        this.heightOffset = heightOffset;
        this.rotationSpeedDegPerSec = rotationSpeedDegPerSec;
        this.description = description;
    }

    /**
     * 兼容旧代码的构造函数，默认旋转速度 1°/s。
     */
    public SpectatePointData(String dimension, BlockPos position, double distance, double heightOffset, String description) {
        this(dimension, position, distance, heightOffset, 1, description);
    }

    public String getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public double getDistance() {
        return distance;
    }

    public double getHeightOffset() {
        return heightOffset;
    }

    public double getRotationSpeed() {
        return rotationSpeedDegPerSec;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "SpectatePointData{" +
                "dimension='" + dimension + '\'' +
                ", position=" + position +
                ", distance=" + distance +
                ", heightOffset=" + heightOffset +
                ", rotationSpeed=" + rotationSpeedDegPerSec +
                ", description='" + description + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpectatePointData)) return false;
        SpectatePointData that = (SpectatePointData) o;
        return Double.compare(that.distance, distance) == 0 &&
                Double.compare(that.heightOffset, heightOffset) == 0 &&
                Double.compare(that.rotationSpeedDegPerSec, rotationSpeedDegPerSec) == 0 &&
                Objects.equals(dimension, that.dimension) &&
                Objects.equals(position, that.position) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, position, distance, heightOffset, rotationSpeedDegPerSec, description);
    }
} 