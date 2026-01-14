package com.spectate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.spectate.command.suggestion.CyclePointSuggestionProvider;
import com.spectate.command.suggestion.PointSuggestionProvider;
import com.spectate.config.ConfigManager;
import com.spectate.config.SpectateConfig;
import com.spectate.data.SpectatePointData;
import com.spectate.service.SpectatePointManager;
import com.spectate.service.ServerSpectateManager;
import com.spectate.service.ViewMode;
import com.spectate.service.CinematicMode;
//#if MC >= 11900
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
//#else
//$$import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
//$$import net.minecraft.text.LiteralText;
//$$import net.minecraft.text.Text;
//#endif
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.command.CommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.util.math.Vec3d;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 负责注册 /cspectate 系列命令。
 * 已重构为仅调用 ServerSpectateManager Facade。
 */
public class SpectateCommand {

    private static final ConfigManager CONFIG_MANAGER = ConfigManager.getInstance();
    private static final PointSuggestionProvider POINT_SUGGESTIONS = new PointSuggestionProvider();
    private static final CyclePointSuggestionProvider CYCLE_SUGGESTIONS = new CyclePointSuggestionProvider();

    // Helper method for cross-version sendFeedback
    private static void sendFeedback(ServerCommandSource source, Text text, boolean broadcastToOps) {
        //#if MC >= 11900
        source.sendFeedback(() -> text, broadcastToOps);
        //#else
        //$$ source.sendFeedback(text, broadcastToOps);
        //#endif
    }

    private static void sendError(ServerCommandSource source, Text text) {
        source.sendError(text);
    }

    public static void register() {
//#if MC >= 11900
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerRoot(dispatcher));
//#else
        //$$CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
        //$$        registerRoot(dispatcher));
//#endif
    }

    private static void registerRoot(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("cspectate");

        root.then(buildPointsCommand());
        root.then(buildPointCommand());
        root.then(buildStopCommand());
        root.then(buildCycleCommand());
        root.then(buildPlayerCommand());
        root.then(buildCoordsCommand());
        root.then(buildConfigCommand());

        dispatcher.register(root);
    }

    /* ---------------- Helper builders ---------------- */

    private static LiteralArgumentBuilder<ServerCommandSource> buildPointsCommand() {
        LiteralArgumentBuilder<ServerCommandSource> points = CommandManager.literal("points");

        // points add <name> <pos> [desc]
        RequiredArgumentBuilder<ServerCommandSource, String> nameArg = CommandManager.argument("name", StringArgumentType.word());
        RequiredArgumentBuilder<ServerCommandSource, ?> posArg = CommandManager.argument("pos", Vec3ArgumentType.vec3())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
                    SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
                    //#if MC >= 11900
                    String dimension = ctx.getSource().getPlayer().getWorld().getRegistryKey().getValue().toString();
                    //#else
                    //$$String dimension = ctx.getSource().getPlayer().getServerWorld().getRegistryKey().getValue().toString();
                    //#endif
                    SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed, name);
                    SpectatePointManager.getInstance().addPoint(name, data);
                    sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_added", Map.of("name", name)), false);
                    return 1;
                })
                .then(CommandManager.argument("description", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
                            String desc = StringArgumentType.getString(ctx, "description");
                            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
                            //#if MC >= 11900
                            String dimension = ctx.getSource().getPlayer().getWorld().getRegistryKey().getValue().toString();
                            //#else
                            //$$String dimension = ctx.getSource().getPlayer().getServerWorld().getRegistryKey().getValue().toString();
                            //#endif
                            SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed, desc);
                            SpectatePointManager.getInstance().addPoint(name, data);
                            sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_added", Map.of("name", name)), false);
                            return 1;
                        }));
        
        points.then(CommandManager.literal("add").then(nameArg.then(posArg)));

        // points remove <name>
        points.then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(POINT_SUGGESTIONS)
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            if (SpectatePointManager.getInstance().removePoint(name) != null) {
                                sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_removed", Map.of("name", name)), false);
                                return 1;
                            } else {
                                sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_not_found", Map.of("name", name)));
                                return 0;
                            }
                        })));

        // points list
        points.then(CommandManager.literal("list")
                .executes(ctx -> {
                    Collection<String> pointNames = SpectatePointManager.getInstance().listPointNames();
                    if (pointNames.isEmpty()) {
                        sendFeedback(ctx.getSource(), CONFIG_MANAGER.getMessage("point_list_empty"), false);
                    } else {
                        sendFeedback(ctx.getSource(), CONFIG_MANAGER.getMessage("point_list_header"), false);
                        pointNames.forEach(name ->
                            sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_list_item", Map.of("name", name)), false));
                    }
                    return 1;
                }));

        return points;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPointCommand() {
        return CommandManager.literal("point")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(POINT_SUGGESTIONS)
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            SpectatePointData point = SpectatePointManager.getInstance().getPoint(name);
                            if(point==null){
                                sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_not_found", Map.of("name", name)));
                                return 0;
                            }
                            ServerSpectateManager.getInstance().spectatePoint(ctx.getSource().getPlayer(), point);
                            return 1;
                        })
                        .then(CommandManager.literal("cinematic")
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    SpectatePointData point = SpectatePointManager.getInstance().getPoint(name);
                                    if(point==null){
                                        sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_not_found", Map.of("name", name)));
                                        return 0;
                                    }
                                    ServerSpectateManager.getInstance().spectatePoint(ctx.getSource().getPlayer(), point, 
                                        ViewMode.CINEMATIC, CinematicMode.SLOW_ORBIT);
                                    return 1;
                                })
                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                        .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                                            "slow_orbit", "aerial_view", "spiral_up", "floating"
                                        }, b))
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String modeStr = StringArgumentType.getString(ctx, "mode");
                                            SpectatePointData point = SpectatePointManager.getInstance().getPoint(name);
                                            if(point==null){
                                                sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_not_found", Map.of("name", name)));
                                                return 0;
                                            }
                                            CinematicMode cinematicMode = CinematicMode.fromString(modeStr);
                                            ServerSpectateManager.getInstance().spectatePoint(ctx.getSource().getPlayer(), point, 
                                                ViewMode.CINEMATIC, cinematicMode);
                                            return 1;
                                        }))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildStopCommand(){
        return CommandManager.literal("stop")
                .executes(ctx->{
                    ServerSpectateManager.getInstance().stopSpectating(ctx.getSource().getPlayer());
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCycleCommand() {
        LiteralArgumentBuilder<ServerCommandSource> cycle = CommandManager.literal("cycle");
        ServerSpectateManager manager = ServerSpectateManager.getInstance();

        // cycle add <name>
        cycle.then(CommandManager.literal("add")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(POINT_SUGGESTIONS)
                        .executes(ctx -> {
                            manager.addCyclePoint(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })));

        // cycle addplayer <name>
        cycle.then(CommandManager.literal("addplayer")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(c.getSource().getServer().getPlayerManager().getPlayerNames(), b))
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            manager.addCyclePoint(ctx.getSource().getPlayer(), "player_" + playerName);
                            return 1;
                        })));

        // cycle addplayerall - 自动添加所有玩家（包括未来加入的）
        cycle.then(CommandManager.literal("addplayerall")
                .executes(ctx -> {
                    manager.enableAutoAddAllPlayers(ctx.getSource().getPlayer(), null, null);
                    return 1;
                })
                .then(CommandManager.literal("prefix")
                        .then(CommandManager.argument("excludePrefix", StringArgumentType.word())
                                .executes(ctx -> {
                                    String prefix = StringArgumentType.getString(ctx, "excludePrefix");
                                    manager.enableAutoAddAllPlayers(ctx.getSource().getPlayer(), prefix, null);
                                    return 1;
                                })))
                .then(CommandManager.literal("suffix")
                        .then(CommandManager.argument("excludeSuffix", StringArgumentType.word())
                                .executes(ctx -> {
                                    String suffix = StringArgumentType.getString(ctx, "excludeSuffix");
                                    manager.enableAutoAddAllPlayers(ctx.getSource().getPlayer(), null, suffix);
                                    return 1;
                                }))));

        // cycle remove <name>
        cycle.then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(CYCLE_SUGGESTIONS)
                        .executes(ctx -> {
                            manager.removeCyclePoint(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })));

        // cycle list
        cycle.then(CommandManager.literal("list")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    List<String> points = manager.listCyclePoints(player);
                    if (points.isEmpty()) {
                        sendFeedback(ctx.getSource(), CONFIG_MANAGER.getMessage("cycle_list_empty"), false);
                    } else {
                        sendFeedback(ctx.getSource(), CONFIG_MANAGER.getMessage("cycle_list_header"), false);
                        for (int i = 0; i < points.size(); i++) {
                            sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("cycle_list_item", Map.of("index", String.valueOf(i + 1), "name", points.get(i))), false);
                        }
                    }
                    return 1;
                }));

        // cycle clear
        cycle.then(CommandManager.literal("clear")
                .executes(ctx -> {
                    manager.clearCyclePoints(ctx.getSource().getPlayer());
                    return 1;
                }));

        // cycle interval <seconds>
        cycle.then(CommandManager.literal("interval")
                .then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(1))
                        .executes(ctx -> {
                            manager.setCycleInterval(ctx.getSource().getPlayer(), (long) DoubleArgumentType.getDouble(ctx, "seconds"));
                            return 1;
                        })));

        // cycle start
        cycle.then(CommandManager.literal("start")
                .executes(ctx -> {
                    manager.startCycle(ctx.getSource().getPlayer());
                    return 1;
                })
                .then(CommandManager.literal("cinematic")
                        .executes(ctx -> {
                            manager.startCycle(ctx.getSource().getPlayer(), ViewMode.CINEMATIC, CinematicMode.SLOW_ORBIT);
                            return 1;
                        })
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                                    "slow_orbit", "aerial_view", "spiral_up", "floating"
                                }, b))
                                .executes(ctx -> {
                                    String modeStr = StringArgumentType.getString(ctx, "mode");
                                    CinematicMode cinematicMode = CinematicMode.fromString(modeStr);
                                    manager.startCycle(ctx.getSource().getPlayer(), ViewMode.CINEMATIC, cinematicMode);
                                    return 1;
                                })))
                .then(CommandManager.literal("follow")
                        .executes(ctx -> {
                            manager.startCycle(ctx.getSource().getPlayer(), ViewMode.FOLLOW, null);
                            return 1;
                        })));

        // cycle next
        cycle.then(CommandManager.literal("next")
                .executes(ctx -> {
                    manager.nextCyclePoint(ctx.getSource().getPlayer());
                    return 1;
                }));

        return cycle;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPlayerCommand(){
        return CommandManager.literal("player")
                .then(CommandManager.argument("target", StringArgumentType.word())
                        .suggests((c,b)->CommandSource.suggestMatching(c.getSource().getServer().getPlayerManager().getPlayerNames(), b))
                        .executes(ctx->{
                            String targetName = StringArgumentType.getString(ctx, "target");
                            ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                            if(target==null){
                                sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("player_not_found", Map.of("name", targetName)));
                                return 0;
                            }
                            ServerSpectateManager.getInstance().spectatePlayer(ctx.getSource().getPlayer(), target);
                            return 1;
                        })
                        .then(CommandManager.literal("follow")
                                .executes(ctx->{
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                    if(target==null){
                                        sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("player_not_found", Map.of("name", targetName)));
                                        return 0;
                                    }
                                    ServerSpectateManager.getInstance().spectatePlayer(ctx.getSource().getPlayer(), target, 
                                        ViewMode.FOLLOW, null);
                                    return 1;
                                }))
                        .then(CommandManager.literal("cinematic")
                                .executes(ctx->{
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                    if(target==null){
                                        sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("player_not_found", Map.of("name", targetName)));
                                        return 0;
                                    }
                                    ServerSpectateManager.getInstance().spectatePlayer(ctx.getSource().getPlayer(), target, 
                                        ViewMode.CINEMATIC, CinematicMode.SLOW_ORBIT);
                                    return 1;
                                })
                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                        .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                                            "slow_orbit", "aerial_view", "spiral_up", "floating"
                                        }, b))
                                        .executes(ctx->{
                                            String targetName = StringArgumentType.getString(ctx, "target");
                                            String modeStr = StringArgumentType.getString(ctx, "mode");
                                            ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                            if(target==null){
                                                sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("player_not_found", Map.of("name", targetName)));
                                                return 0;
                                            }
                                            CinematicMode cinematicMode = CinematicMode.fromString(modeStr);
                                            ServerSpectateManager.getInstance().spectatePlayer(ctx.getSource().getPlayer(), target, 
                                                ViewMode.CINEMATIC, cinematicMode);
                                            return 1;
                                        }))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCoordsCommand() {
        RequiredArgumentBuilder<ServerCommandSource, ?> posArg = CommandManager.argument("pos", Vec3ArgumentType.vec3());

        // Base command: /cspectate coords <pos>
        posArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed);
            return 1;
        });

        // Optional distance: /cspectate coords <pos> <distance>
        RequiredArgumentBuilder<ServerCommandSource, ?> distArg = CommandManager.argument("distance", DoubleArgumentType.doubleArg(1));
        distArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, settings.spectate_height_offset, settings.spectate_rotation_speed);
            return 1;
        });

        // Optional height: /cspectate coords <pos> <distance> <heightOffset>
        RequiredArgumentBuilder<ServerCommandSource, ?> heightArg = CommandManager.argument("heightOffset", DoubleArgumentType.doubleArg());
        heightArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, settings.spectate_rotation_speed);
            return 1;
        });

        // Optional rotation: /cspectate coords <pos> <distance> <heightOffset> <rotationSpeed>
        RequiredArgumentBuilder<ServerCommandSource, ?> rotArg = CommandManager.argument("rotationSpeed", DoubleArgumentType.doubleArg(0));
        rotArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            double rot = DoubleArgumentType.getDouble(ctx, "rotationSpeed");
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, rot);
            return 1;
        });

        // Chain the arguments
        heightArg.then(rotArg);
        distArg.then(heightArg);
        posArg.then(distArg);

        return CommandManager.literal("coords").then(posArg);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildConfigCommand() {
        LiteralArgumentBuilder<ServerCommandSource> config = CommandManager.literal("config");

        // config reload
        config.then(CommandManager.literal("reload")
                .requires(source -> source.hasPermissionLevel(2)) // 需要OP权限
                .executes(ctx -> {
                    CONFIG_MANAGER.reloadConfig();
                    sendFeedback(ctx.getSource(), 
                        //#if MC >= 11900
                        Text.literal("§a[Spectate] 配置已重新加载")
                        //#else
                        //$$new LiteralText("§a[Spectate] 配置已重新加载")
                        //#endif
                        , false);
                    return 1;
                }));

        // config get <path>
        config.then(CommandManager.literal("get")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("path", StringArgumentType.string())
                        .suggests((c, b) -> {
                            // 提供settings和lang的字段建议
                            java.util.List<String> suggestions = new java.util.ArrayList<>();
                            
                            // 添加settings字段
                            java.lang.reflect.Field[] settingsFields = SpectateConfig.Settings.class.getFields();
                            for (java.lang.reflect.Field field : settingsFields) {
                                suggestions.add("settings." + field.getName());
                            }
                            
                            // 添加lang字段
                            java.lang.reflect.Field[] langFields = SpectateConfig.Messages.class.getFields();
                            for (java.lang.reflect.Field field : langFields) {
                                suggestions.add("lang." + field.getName());
                            }
                            
                            return CommandSource.suggestMatching(suggestions, b);
                        })
                        .executes(ctx -> {
                            String path = StringArgumentType.getString(ctx, "path");
                            Object value = CONFIG_MANAGER.getConfigValue(path);
                            if (value == null) {
                                sendError(ctx.getSource(), 
                                    //#if MC >= 11900
                                    Text.literal("§c[Spectate] 配置路径不存在: " + path)
                                    //#else
                                    //$$new LiteralText("§c[Spectate] 配置路径不存在: " + path)
                                    //#endif
                                );
                                return 0;
                            }
                            sendFeedback(ctx.getSource(), 
                                //#if MC >= 11900
                                Text.literal("§a[Spectate] " + path + " = " + value)
                                //#else
                                //$$new LiteralText("§a[Spectate] " + path + " = " + value)
                                //#endif
                                , false);
                            return 1;
                        })));

        // config set <path> <value>
        config.then(CommandManager.literal("set")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("path", StringArgumentType.string())
                        .suggests((c, b) -> {
                            // 提供settings和lang的字段建议
                            java.util.List<String> suggestions = new java.util.ArrayList<>();
                            
                            // 添加settings字段
                            java.lang.reflect.Field[] settingsFields = SpectateConfig.Settings.class.getFields();
                            for (java.lang.reflect.Field field : settingsFields) {
                                suggestions.add("settings." + field.getName());
                            }
                            
                            // 添加lang字段
                            java.lang.reflect.Field[] langFields = SpectateConfig.Messages.class.getFields();
                            for (java.lang.reflect.Field field : langFields) {
                                suggestions.add("lang." + field.getName());
                            }
                            
                            return CommandSource.suggestMatching(suggestions, b);
                        })
                        .then(CommandManager.argument("value", StringArgumentType.string())
                                .executes(ctx -> {
                                    String path = StringArgumentType.getString(ctx, "path");
                                    String value = StringArgumentType.getString(ctx, "value");
                                    boolean success = CONFIG_MANAGER.setConfigValue(path, value);
                                    if (success) {
                                        sendFeedback(ctx.getSource(), 
                                            //#if MC >= 11900
                                            Text.literal("§a[Spectate] 配置已更新: " + path + " = " + value)
                                            //#else
                                            //$$new LiteralText("§a[Spectate] 配置已更新: " + path + " = " + value)
                                            //#endif
                                            , false);
                                        return 1;
                                    } else {
                                        sendError(ctx.getSource(), 
                                            //#if MC >= 11900
                                            Text.literal("§c[Spectate] 配置更新失败: " + path)
                                            //#else
                                            //$$new LiteralText("§c[Spectate] 配置更新失败: " + path)
                                            //#endif
                                        );
                                        return 0;
                                    }
                                }))));

        // config list [category]
        config.then(CommandManager.literal("list")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> {
                    // 列出所有defaults配置
                    sendFeedback(ctx.getSource(), 
                        //#if MC >= 11900
                        Text.literal("§e[Spectate] 功能设置:")
                        //#else
                        //$$new LiteralText("§e[Spectate] 默认配置:")
                        //#endif
                        , false);
                    java.lang.reflect.Field[] settingsFields = SpectateConfig.Settings.class.getFields();
                    for (java.lang.reflect.Field field : settingsFields) {
                        try {
                            Object value = field.get(CONFIG_MANAGER.getConfig().settings);
                            String comment = getFieldComment(field.getName());
                            
                            String displayText = "  §7settings." + field.getName() + " = " + value;
                            if (!comment.isEmpty()) {
                                displayText += " §8# " + comment;
                            }
                            
                            sendFeedback(ctx.getSource(), 
                                //#if MC >= 11900
                                Text.literal(displayText)
                                //#else
                                //$$new LiteralText(displayText)
                                //#endif
                                , false);
                        } catch (IllegalAccessException e) {
                            sendFeedback(ctx.getSource(), 
                                //#if MC >= 11900
                                Text.literal("  §7settings." + field.getName() + " = [无法访问]")
                                //#else
                                //$$new LiteralText("  §7settings." + field.getName() + " = [无法访问]")
                                //#endif
                                , false);
                        }
                    }

                    // 列出所有messages配置
                    sendFeedback(ctx.getSource(), 
                        //#if MC >= 11900
                        Text.literal("§e[Spectate] 消息配置:")
                        //#else
                        //$$new LiteralText("§e[Spectate] 消息配置:")
                        //#endif
                        , false);
                    java.lang.reflect.Field[] messageFields = SpectateConfig.Messages.class.getFields();
                    for (java.lang.reflect.Field field : messageFields) {
                        try {
                            Object value = field.get(CONFIG_MANAGER.getConfig().lang);
                            sendFeedback(ctx.getSource(), 
                                //#if MC >= 11900
                                Text.literal("  §7lang." + field.getName() + " = \"" + value + "\"")
                                //#else
                                //$$new LiteralText("  §7messages." + field.getName() + " = \"" + value + "\"")
                                //#endif
                                , false);
                        } catch (IllegalAccessException e) {
                            // 忽略
                        }
                    }
                    return 1;
                })
                .then(CommandManager.argument("category", StringArgumentType.string())
                        .suggests((c, b) -> CommandSource.suggestMatching(new String[]{"settings", "lang"}, b))
                        .executes(ctx -> {
                            String category = StringArgumentType.getString(ctx, "category");
                            java.lang.reflect.Field[] fields;

                            if ("settings".equals(category)) {
                                fields = SpectateConfig.Settings.class.getFields();
                                sendFeedback(ctx.getSource(), 
                                    //#if MC >= 11900
                                    Text.literal("§e[Spectate] 功能设置:")
                                    //#else
                                    //$$new LiteralText("§e[Spectate] 默认配置:")
                                    //#endif
                                    , false);
                                for (java.lang.reflect.Field field : fields) {
                                    try {
                                        Object value = field.get(CONFIG_MANAGER.getConfig().settings);
                                        String comment = getFieldComment(field.getName());
                                        
                                        String displayText = "  §7settings." + field.getName() + " = " + value;
                                        if (!comment.isEmpty()) {
                                            displayText += " §8# " + comment;
                                        }
                                        
                                        sendFeedback(ctx.getSource(), 
                                            //#if MC >= 11900
                                            Text.literal(displayText)
                                            //#else
                                            //$$new LiteralText(displayText)
                                            //#endif
                                            , false);
                                    } catch (IllegalAccessException e) {
                                        sendFeedback(ctx.getSource(), 
                                            //#if MC >= 11900
                                            Text.literal("  §7settings." + field.getName() + " = [无法访问]")
                                            //#else
                                            //$$new LiteralText("  §7settings." + field.getName() + " = [无法访问]")
                                            //#endif
                                            , false);
                                    }
                                }
                            } else if ("lang".equals(category)) {
                                fields = SpectateConfig.Messages.class.getFields();
                                sendFeedback(ctx.getSource(), 
                                    //#if MC >= 11900
                                    Text.literal("§e[Spectate] 语言消息:")
                                    //#else
                                    //$$new LiteralText("§e[Spectate] 消息配置:")
                                    //#endif
                                    , false);
                                for (java.lang.reflect.Field field : fields) {
                                    try {
                                        Object value = field.get(CONFIG_MANAGER.getConfig().lang);
                                        String comment = "";
                                        if (field.getName().equals("point_added")) comment = "添加观察点时的提示消息";
                                        else if (field.getName().equals("point_removed")) comment = "移除观察点时的提示消息";
                                        else if (field.getName().equals("cycle_started")) comment = "开始循环时的提示消息";
                                        else if (field.getName().equals("spectate_stop")) comment = "停止旁观时的提示消息";
                                        
                                        String displayText = "  §7lang." + field.getName() + " = \"" + value + "\"";
                                        if (!comment.isEmpty()) {
                                            displayText += " §8# " + comment;
                                        }
                                        
                                        sendFeedback(ctx.getSource(), 
                                            //#if MC >= 11900
                                            Text.literal(displayText)
                                            //#else
                                            //$$new LiteralText(displayText)
                                            //#endif
                                            , false);
                                    } catch (IllegalAccessException e) {
                                        sendFeedback(ctx.getSource(), 
                                            //#if MC >= 11900
                                            Text.literal("  §7lang." + field.getName() + " = [无法访问]")
                                            //#else
                                            //$$new LiteralText("  §7lang." + field.getName() + " = [无法访问]")
                                            //#endif
                                            , false);
                                    }
                                }
                            } else {
                                sendError(ctx.getSource(), 
                                    //#if MC >= 11900
                                    Text.literal("§c[Spectate] 无效的分类: " + category)
                                    //#else
                                    //$$new LiteralText("§c[Spectate] 无效的分类: " + category)
                                    //#endif
                                );
                                return 0;
                            }
                            return 1;
                        })));

        return config;
    }

    private static String getFieldComment(String fieldName) {
        switch (fieldName) {
            case "cycle_interval_seconds": return "循环模式下，每个观察点停留的秒数";
            case "spectate_distance": return "默认旁观距离，单位：方块";
            case "spectate_height_offset": return "默认旁观高度偏移，单位：方块";
            case "spectate_rotation_speed": return "默认旋转速度，数值越大越快";
            case "floating_strength": return "浮游视角强度，控制摄像机运动的幅度 (0.1-1.0)";
            case "floating_speed": return "浮游视角速度，控制摄像机运动的速度 (0.1-2.0)";
            case "floating_orbit_radius": return "浮游视角轨道半径，摄像机围绕目标的轨道半径 (1-20)";
            case "floating_height_variation": return "浮游视角高度变化，垂直方向的运动幅度 (0.1-2.0)";
            case "floating_breathing_frequency": return "浮游视角呼吸频率，控制运动节律 (0.1-2.0)";
            case "floating_damping_factor": return "浮游视角阻尼因子，数值越大运动越平稳 (0.1-1.0)";
            case "floating_attraction_factor": return "浮游视角吸引力因子，控制回中力量 (0.1-1.0)";
            case "floating_prediction_factor": return "浮游视角预测因子，控制对目标移动的预测程度 (0.5-5.0)";
            default: return "";
        }
    }
}
