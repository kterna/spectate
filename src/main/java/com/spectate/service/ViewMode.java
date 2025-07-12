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

    public String getName() {
        return name;
    }

    public static ViewMode fromString(String name) {
        for (ViewMode mode : values()) {
            if (mode.name.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return ORBIT; // 默认模式
    }
}