package com.spectate;

import com.spectate.config.ConfigManager;
import com.spectate.service.ServerSpectateManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import com.spectate.command.SpectateCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SpectateMod 作为 Fabric Mod 的入口，负责在服务器启动时初始化各核心单例。
 */
public class SpectateMod implements ModInitializer {

    public static final String MOD_ID = "spectate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;

    /**
     * 获取当前的 MinecraftServer 实例。
     *
     * @return 服务器实例，如果服务器未运行则可能为 null。
     */
    public static MinecraftServer getServer() {
        return server;
    }

    @Override
    public void onInitialize() {

        // 记录服务器实例，供其他单例使用
        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            // 初始化各个管理器
            ConfigManager.getInstance(); // 加载配置
            com.spectate.data.SpectateStateSaver.getInstance().initialize(); // 加载数据
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(srv -> server = null);

        // 注册命令
        SpectateCommand.register();

        // 注册玩家事件
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // 逻辑已委托给 ServerSpectateManager 门面模式处理
            ServerSpectateManager.getInstance().onPlayerDisconnect(handler.player);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 逻辑已委托给 ServerSpectateManager 门面模式处理
            ServerSpectateManager.getInstance().onPlayerConnect(handler.player);
        });
    }
}
