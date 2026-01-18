package com.spectate.service;

/**
 * 视角模式枚举，定义不同的观察视角类型
 */
public enum ViewMode {
    ORBIT("orbit"),           // 原有的环绕模式
    FOLLOW("follow"),         // 跟随模式 
    CINEMATIC("cinematic");   // 电影视角模式

    private final String name;

    ViewMode(String name) {
        this.name = name;
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
     * 根据名称查找对应的 ViewMode。
     *
     * @param name 模式名称（不区分大小写）。
     * @return 对应的 ViewMode，如果未找到则返回默认的 ORBIT。
     */
    public static ViewMode fromString(String name) {
        for (ViewMode mode : values()) {
            if (mode.name.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return ORBIT; // 默认模式
    }
}