package com.spectate.service;

/**
 * 视角模式枚举，定义不同的观察视角类型
 */
public enum ViewMode {
    ORBIT("orbit", false),                             // 原有的环绕模式
    FOLLOW("follow", false),                           // 跟随模式
    CINEMATIC_SLOW_ORBIT("slow_orbit", true),         // 慢速环绕
    CINEMATIC_AERIAL_VIEW("aerial_view", true),       // 高空俯瞰
    CINEMATIC_SPIRAL_UP("spiral_up", true),           // 螺旋上升
    CINEMATIC_FLOATING("floating", true);             // 浮游视角

    private final String name;
    private final boolean cinematic;

    ViewMode(String name, boolean cinematic) {
        this.name = name;
        this.cinematic = cinematic;
    }

    /**
     * 获取模式的内部名称。
     *
     * @return 模式名称字符串。
     */
    public String getName() {
        return name;
    }

    /**
     * 当前模式是否属于电影模式。
     */
    public boolean isCinematic() {
        return cinematic;
    }

    /**
     * 根据名称查找对应的 ViewMode。
     *
     * @param name 模式名称（不区分大小写）。
     * @return 对应的 ViewMode，如果未找到则返回默认的 ORBIT。
     */
    public static ViewMode fromString(String name) {
        if (name == null) {
            return ORBIT;
        }

        String normalizedName = name.trim().toLowerCase();
        switch (normalizedName) {
            case "orbit":
                return ORBIT;
            case "follow":
                return FOLLOW;
            case "slow_orbit":
                return CINEMATIC_SLOW_ORBIT;
            case "aerial_view":
                return CINEMATIC_AERIAL_VIEW;
            case "spiral_up":
                return CINEMATIC_SPIRAL_UP;
            case "floating":
                return CINEMATIC_FLOATING;
            default:
                return ORBIT;
        }
    }
}
