package com.spectate.service;

/**
 * 浮游摄像机系统 - 实现基于物理的自然浮游效果
 * 基于噪声生成 + 物理模拟 + 轨道运动的组合算法
 */
public class FloatingCamera {
    
    // 核心参数
    private double floatingStrength = 0.5;     // 浮游强度
    private double floatingSpeed = 0.3;        // 浮游速度
    private double orbitRadius = 8.0;          // 轨道半径
    private double heightVariation = 0.8;      // 高度变化
    private double breathingFrequency = 0.5;   // 呼吸频率
    private double rotationSpeed = 0.2;        // 旋转速度
    private double dampingFactor = 0.95;       // 阻尼系数
    private double attractionFactor = 0.3;     // 吸引系数
    private double predictionFactor = 2.0;     // 预测系数
    
    // 状态变量
    private double currentX, currentY, currentZ;         // 当前位置
    private double velocityX, velocityY, velocityZ;      // 当前速度
    private double orbitAngle = 0.0;                     // 轨道角度
    private double heightAngle = 0.0;                    // 高度角度
    private double breathingPhase = 0.0;                 // 呼吸相位
    private double noiseOffset = 0.0;                    // 噪声偏移
    private double lastTargetX, lastTargetY, lastTargetZ; // 上一帧目标位置
    private boolean initialized = false;
    
    // 噪声缓存（简化版的Perlin噪声实现）
    private static final int NOISE_SIZE = 256;
    private static final double[] NOISE_TABLE = new double[NOISE_SIZE];
    
    static {
        // 初始化噪声表
        java.util.Random random = new java.util.Random(12345);
        for (int i = 0; i < NOISE_SIZE; i++) {
            NOISE_TABLE[i] = random.nextDouble() * 2.0 - 1.0;
        }
    }
    
    /**
     * 简化的Perlin噪声函数
     */
    private double perlinNoise(double x, double y, double z) {
        int xi = (int) Math.floor(x) & (NOISE_SIZE - 1);
        int yi = (int) Math.floor(y) & (NOISE_SIZE - 1);
        int zi = (int) Math.floor(z) & (NOISE_SIZE - 1);
        
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);
        
        // 简化的三线性插值
        int i = (xi + yi * 7 + zi * 13) & (NOISE_SIZE - 1);
        return NOISE_TABLE[i] * (1.0 - xf) * (1.0 - yf) * (1.0 - zf);
    }
    
    /**
     * 多层次噪声合成
     */
    private double generateNoise(double time, double offset) {
        double baseNoise = perlinNoise(time * 0.5 + offset, 0, 0) * 1.0;
        double detailNoise = perlinNoise(time * 2.0 + offset, 0, 0) * 0.5;
        double fineNoise = perlinNoise(time * 8.0 + offset, 0, 0) * 0.1;
        
        return (baseNoise + detailNoise + fineNoise) * floatingStrength;
    }
    
    /**
     * 计算三轴噪声力
     */
    private void calculateNoiseForces(double deltaTime, double[] noiseForces) {
        noiseOffset += floatingSpeed * deltaTime;
        
        noiseForces[0] = generateNoise(noiseOffset, 0);
        noiseForces[1] = generateNoise(noiseOffset, 100);
        noiseForces[2] = generateNoise(noiseOffset, 200);
    }
    
    /**
     * 计算轨道位置
     */
    private void calculateOrbitPosition(double targetX, double targetY, double targetZ, 
                                      double deltaTime, double[] orbitPos) {
        // 更新角度
        orbitAngle += rotationSpeed * deltaTime;
        heightAngle += rotationSpeed * 0.7 * deltaTime;
        
        // 计算轨道偏移
        double orbitOffsetX = Math.cos(orbitAngle) * orbitRadius;
        double orbitOffsetZ = Math.sin(orbitAngle) * orbitRadius;
        double orbitOffsetY = Math.sin(heightAngle) * heightVariation;
        
        orbitPos[0] = targetX + orbitOffsetX;
        orbitPos[1] = targetY + orbitOffsetY;
        orbitPos[2] = targetZ + orbitOffsetZ;
    }
    
    /**
     * 计算呼吸效果
     */
    private double calculateBreathingEffect(double deltaTime) {
        breathingPhase += breathingFrequency * deltaTime;
        return Math.sin(breathingPhase) * 0.5;
    }
    
    /**
     * 计算预测位置
     */
    private void calculatePredictivePosition(double targetX, double targetY, double targetZ,
                                           double deltaTime, double[] predictPos) {
        if (!initialized) {
            predictPos[0] = targetX;
            predictPos[1] = targetY;
            predictPos[2] = targetZ;
            return;
        }
        
        // 计算目标速度
        double targetVelX = (targetX - lastTargetX) / deltaTime;
        double targetVelY = (targetY - lastTargetY) / deltaTime;
        double targetVelZ = (targetZ - lastTargetZ) / deltaTime;
        
        double targetSpeed = Math.sqrt(targetVelX * targetVelX + targetVelY * targetVelY + targetVelZ * targetVelZ);
        
        if (targetSpeed > 0.1) {
            // 目标在移动，进行预测
            predictPos[0] = targetX + targetVelX * predictionFactor;
            predictPos[1] = targetY + targetVelY * predictionFactor;
            predictPos[2] = targetZ + targetVelZ * predictionFactor;
        } else {
            // 目标静止
            predictPos[0] = targetX;
            predictPos[1] = targetY;
            predictPos[2] = targetZ;
        }
    }
    
    /**
     * 更新浮游摄像机位置
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @param targetZ 目标Z坐标
     * @param deltaTime 时间增量（秒）
     * @param result 输出的摄像机位置 [x, y, z, yaw, pitch]
     */
    public void updatePosition(double targetX, double targetY, double targetZ, 
                             double deltaTime, double[] result) {
        // 初始化位置
        if (!initialized) {
            currentX = targetX + orbitRadius;
            currentY = targetY + 2.0;
            currentZ = targetZ;
            velocityX = velocityY = velocityZ = 0.0;
            lastTargetX = targetX;
            lastTargetY = targetY;
            lastTargetZ = targetZ;
            initialized = true;
        }
        
        // 1. 计算轨道位置
        double[] orbitPos = new double[3];
        calculateOrbitPosition(targetX, targetY, targetZ, deltaTime, orbitPos);
        
        // 2. 生成噪声力
        double[] noiseForces = new double[3];
        calculateNoiseForces(deltaTime, noiseForces);
        
        // 3. 计算呼吸效果
        double breathingOffset = calculateBreathingEffect(deltaTime);
        
        // 4. 计算预测位置
        double[] predictPos = new double[3];
        calculatePredictivePosition(targetX, targetY, targetZ, deltaTime, predictPos);
        
        // 5. 合成目标位置
        double finalTargetX = orbitPos[0] + noiseForces[0];
        double finalTargetY = orbitPos[1] + noiseForces[1] + breathingOffset;
        double finalTargetZ = orbitPos[2] + noiseForces[2];
        
        // 6. 计算吸引力
        double attractionX = (finalTargetX - currentX) * attractionFactor;
        double attractionY = (finalTargetY - currentY) * attractionFactor;
        double attractionZ = (finalTargetZ - currentZ) * attractionFactor;
        
        // 7. 更新速度（物理模拟）
        velocityX += (attractionX + noiseForces[0] * 0.1) * deltaTime;
        velocityY += (attractionY + noiseForces[1] * 0.1) * deltaTime;
        velocityZ += (attractionZ + noiseForces[2] * 0.1) * deltaTime;
        
        // 8. 应用阻尼
        velocityX *= dampingFactor;
        velocityY *= dampingFactor;
        velocityZ *= dampingFactor;
        
        // 9. 更新位置
        currentX += velocityX * deltaTime;
        currentY += velocityY * deltaTime;
        currentZ += velocityZ * deltaTime;
        
        // 10. 计算朝向（始终看向目标）
        double dx = targetX - currentX;
        double dy = targetY - currentY;
        double dz = targetZ - currentZ;
        
        // 添加微小的旋转噪声
        double rotationNoiseX = perlinNoise(noiseOffset * 0.5, 100, 0) * 0.05;
        double rotationNoiseY = perlinNoise(noiseOffset * 0.5, 200, 0) * 0.05;
        
        dx += rotationNoiseX;
        dy += rotationNoiseY;
        
        double yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        double pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        
        // 11. 更新历史位置
        lastTargetX = targetX;
        lastTargetY = targetY;
        lastTargetZ = targetZ;
        
        // 12. 输出结果
        result[0] = currentX;
        result[1] = currentY;
        result[2] = currentZ;
        result[3] = yaw;
        result[4] = pitch;
    }
    
    /**
     * 重置摄像机状态
     */
    public void reset() {
        initialized = false;
        velocityX = velocityY = velocityZ = 0.0;
        orbitAngle = heightAngle = breathingPhase = noiseOffset = 0.0;
    }
    
    // Getter和Setter方法用于参数调整
    public void setFloatingStrength(double floatingStrength) {
        this.floatingStrength = Math.max(0.1, Math.min(3.0, floatingStrength));
    }
    
    public void setFloatingSpeed(double floatingSpeed) {
        this.floatingSpeed = Math.max(0.1, Math.min(2.0, floatingSpeed));
    }
    
    public void setOrbitRadius(double orbitRadius) {
        this.orbitRadius = Math.max(3.0, Math.min(20.0, orbitRadius));
    }
    
    public void setHeightVariation(double heightVariation) {
        this.heightVariation = Math.max(0.0, Math.min(5.0, heightVariation));
    }
    
    public void setBreathingFrequency(double breathingFrequency) {
        this.breathingFrequency = Math.max(0.1, Math.min(3.0, breathingFrequency));
    }
    
    public void setRotationSpeed(double rotationSpeed) {
        this.rotationSpeed = Math.max(0.1, Math.min(1.0, rotationSpeed));
    }
    
    public void setDampingFactor(double dampingFactor) {
        this.dampingFactor = Math.max(0.8, Math.min(0.99, dampingFactor));
    }
    
    public void setAttractionFactor(double attractionFactor) {
        this.attractionFactor = Math.max(0.1, Math.min(1.0, attractionFactor));
    }
    
    public void setPredictionFactor(double predictionFactor) {
        this.predictionFactor = Math.max(1.0, Math.min(5.0, predictionFactor));
    }
}