package com.spectate.service;

/**
 * 电影视角子模式枚举，定义不同的电影效果
 */
public enum CinematicMode {
    SLOW_ORBIT("slow_orbit"),         // 慢速环绕
    AERIAL_VIEW("aerial_view"),       // 高空俯瞰
    SPIRAL_UP("spiral_up"),           // 螺旋上升
    FLOATING("floating");             // 浮游视角

    private final String name;

    CinematicMode(String name) {
        this.name = name;
    }

    /**
     * 获取子模式的内部名称。
     *
     * @return 模式名称字符串。
     */
    public String getName() {
        return name;
    }

    /**
     * 根据名称查找对应的 CinematicMode。
     *
     * @param name 模式名称（不区分大小写）。
     * @return 对应的 CinematicMode，如果未找到则返回默认的 SLOW_ORBIT。
     */
    public static CinematicMode fromString(String name) {
        for (CinematicMode mode : values()) {
            if (mode.name.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return SLOW_ORBIT; // 默认电影模式
    }
}