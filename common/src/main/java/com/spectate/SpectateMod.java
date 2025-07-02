package com.spectate;

public final class SpectateMod {
    public static final String MOD_ID = "spectate";

    public static void init() {
        // 通用初始化代码
        // 初始化坐标点管理器(会自动加载配置文件)
        SpectatePointManager.getInstance();
        
        // 平台特定的初始化将在平台模块中处理
    }
}
