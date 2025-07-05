package com.spectate.fabric;

import com.spectate.SpectateMod;
import com.spectate.SpectateCommand;
import com.spectate.server.ServerSpectateManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class SpectateFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // 通用初始化
        SpectateMod.init();
        
        // 服务端启动时初始化服务端管理器
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerSpectateManager.getInstance().initialize(server);
        });
        
        // 服务端停止时清理资源
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerSpectateManager.getInstance().shutdown();
        });
        
        // 注册服务端命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SpectateCommand.register(dispatcher);
        });
    }
} 