package com.spectate;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.spectate.command.SpectateCommand;

/**
 * SpectateMod 作为 Fabric Mod 的入口，负责在服务器启动时初始化各核心单例。
 */
public class SpectateMod implements ModInitializer {

    public static final String MOD_ID = "spectate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;

    public static MinecraftServer getServer() {
        return server;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Spectate Mod...");

        // 记录服务器实例，供其他单例使用
        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            LOGGER.info("Spectate Mod captured server instance");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(srv -> server = null);

        // 注册命令
        SpectateCommand.register();
    }
} 