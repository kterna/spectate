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
import com.spectate.service.SpectateSessionManager;
import com.spectate.service.ViewMode;
import com.spectate.data.SpectateStatsManager;
import com.spectate.data.SpectateStats;
import java.util.UUID;
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

    // 跨版本 sendFeedback 的辅助方法
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

    /**
     * 注册 /cspectate 命令及其别名。
     * 应在模组初始化时调用。
     */
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
        // 注册完整命令 /cspectate
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("cspectate");
        addAllSubcommands(root);
        dispatcher.register(root);

        // 注册短别名 /cs，同时支持缩写子命令
        LiteralArgumentBuilder<ServerCommandSource> alias = CommandManager.literal("cs");
        addAllSubcommands(alias);
        addAbbreviatedSubcommands(alias);
        dispatcher.register(alias);
    }

    /**
     * 添加所有完整子命令到指定的根命令
     */
    private static void addAllSubcommands(LiteralArgumentBuilder<ServerCommandSource> root) {
        root.then(buildPointsCommand());
        root.then(buildPointCommand());
        root.then(buildStopCommand());
        root.then(buildCycleCommand());
        root.then(buildPlayerCommand());
        root.then(buildCoordsCommand());
        root.then(buildConfigCommand());
        root.then(buildWhoCommand());
        root.then(buildStatsCommand());
        root.then(buildTopCommand());
    }

    /**
     * 添加缩写子命令到 /cs 别名
     * 缩写设计：
     *   /cs p <player>  = player
     *   /cs pt <name>   = point
     *   /cs pts         = points
     *   /cs co <x y z>  = coords
     *   /cs cy          = cycle
     *   /cs s           = stop
     *   /cs w           = who
     */
    private static void addAbbreviatedSubcommands(LiteralArgumentBuilder<ServerCommandSource> root) {
        // p -> player
        root.then(buildPlayerCommandWithLiteral("p"));
        // pt -> point
        root.then(buildPointCommandWithLiteral("pt"));
        // pts -> points
        root.then(buildPointsCommandWithLiteral("pts"));
        // co -> coords
        root.then(buildCoordsCommandWithLiteral("co"));
        // cy -> cycle
        root.then(buildCycleCommandWithLiteral("cy"));
        // s -> stop
        root.then(buildStopCommandWithLiteral("s"));
        // w -> who
        root.then(buildWhoCommandWithLiteral("w"));
    }

    /* ---------------- 缩写命令构建器 ---------------- */

    private static LiteralArgumentBuilder<ServerCommandSource> buildPlayerCommandWithLiteral(String literal) {
        return CommandManager.literal(literal)
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
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                                    "follow", "slow_orbit", "aerial_view", "spiral_up", "floating", "orbit"
                                }, b))
                                .executes(ctx->{
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    String modeStr = StringArgumentType.getString(ctx, "mode");
                                    ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                    if(target==null){
                                        sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("player_not_found", Map.of("name", targetName)));
                                        return 0;
                                    }
                                    ViewMode viewMode = ViewMode.fromString(modeStr);
                                    ServerSpectateManager.getInstance().spectatePlayer(ctx.getSource().getPlayer(), target,
                                        viewMode);
                                    return 1;
                                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPointCommandWithLiteral(String literal) {
        return CommandManager.literal(literal)
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
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                                    "slow_orbit", "aerial_view", "spiral_up", "floating", "orbit"
                                }, b))
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    String modeStr = StringArgumentType.getString(ctx, "mode");
                                    SpectatePointData point = SpectatePointManager.getInstance().getPoint(name);
                                    if(point==null){
                                        sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_not_found", Map.of("name", name)));
                                        return 0;
                                    }
                                    ViewMode viewMode = ViewMode.fromString(modeStr);
                                    ServerSpectateManager.getInstance().spectatePoint(ctx.getSource().getPlayer(), point,
                                        viewMode);
                                    return 1;
                                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPointsCommandWithLiteral(String literal) {
        LiteralArgumentBuilder<ServerCommandSource> points = CommandManager.literal(literal);

        // pts add <name> <pos> [desc]
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
                    SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed, name, "default");
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
                            
                            String group = "default";
                            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("group:(\\S+)").matcher(desc);
                            if (matcher.find()) {
                                group = matcher.group(1);
                                desc = desc.replace(matcher.group(0), "").trim();
                            }
                            if (desc.isEmpty()) desc = name;

                            SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed, desc, group);
                            SpectatePointManager.getInstance().addPoint(name, data);
                            sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_added", Map.of("name", name)), false);
                            return 1;
                        }));

        points.then(CommandManager.literal("add").then(nameArg.then(posArg)));

        // pts remove <name>
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

        // pts list [group]
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
                })
                .then(CommandManager.argument("group", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(SpectatePointManager.getInstance().listGroups(), b))
                        .executes(ctx -> {
                             String group = StringArgumentType.getString(ctx, "group");
                             Collection<String> pointNames = SpectatePointManager.getInstance().listPointNamesByGroup(group);
                             if (pointNames.isEmpty()) {
                                 sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_list_group_empty", Map.of("group", group)), false);
                             } else {
                                 sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_list_group_header", Map.of("group", group)), false);
                                 pointNames.forEach(name ->
                                     sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_list_item", Map.of("name", name)), false));
                             }
                             return 1;
                        })));

        return points;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCoordsCommandWithLiteral(String literal) {
        RequiredArgumentBuilder<ServerCommandSource, ?> posArg = CommandManager.argument("pos", Vec3ArgumentType.vec3());

        posArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed);
            return 1;
        });

        RequiredArgumentBuilder<ServerCommandSource, ?> distArg = CommandManager.argument("distance", DoubleArgumentType.doubleArg(1));
        distArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, settings.spectate_height_offset, settings.spectate_rotation_speed);
            return 1;
        });

        RequiredArgumentBuilder<ServerCommandSource, ?> heightArg = CommandManager.argument("heightOffset", DoubleArgumentType.doubleArg());
        heightArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, settings.spectate_rotation_speed);
            return 1;
        });

        RequiredArgumentBuilder<ServerCommandSource, ?> rotArg = CommandManager.argument("rotationSpeed", DoubleArgumentType.doubleArg(0));
        rotArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            double rot = DoubleArgumentType.getDouble(ctx, "rotationSpeed");
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, rot);
            return 1;
        });

        heightArg.then(rotArg);
        distArg.then(heightArg);
        posArg.then(distArg);

        return CommandManager.literal(literal).then(posArg);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCycleCommandWithLiteral(String literal) {
        LiteralArgumentBuilder<ServerCommandSource> cycle = CommandManager.literal(literal);
        ServerSpectateManager manager = ServerSpectateManager.getInstance();

        cycle.then(CommandManager.literal("add")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(POINT_SUGGESTIONS)
                        .executes(ctx -> {
                            manager.addCyclePoint(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })));

        cycle.then(CommandManager.literal("addgroup")
                .then(CommandManager.argument("group", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(SpectatePointManager.getInstance().listGroups(), b))
                        .executes(ctx -> {
                            manager.addCycleGroup(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "group"));
                            return 1;
                        })));

        cycle.then(CommandManager.literal("addplayer")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(c.getSource().getServer().getPlayerManager().getPlayerNames(), b))
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            manager.addCyclePoint(ctx.getSource().getPlayer(), "player_" + playerName);
                            return 1;
                        })));

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

        cycle.then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(CYCLE_SUGGESTIONS)
                        .executes(ctx -> {
                            manager.removeCyclePoint(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })));

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

        cycle.then(CommandManager.literal("clear")
                .executes(ctx -> {
                    manager.clearCyclePoints(ctx.getSource().getPlayer());
                    return 1;
                }));

        cycle.then(CommandManager.literal("interval")
                .then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(1))
                        .executes(ctx -> {
                            manager.setCycleInterval(ctx.getSource().getPlayer(), (long) DoubleArgumentType.getDouble(ctx, "seconds"));
                            return 1;
                        })));

        cycle.then(CommandManager.literal("start")
                .executes(ctx -> {
                    manager.startCycle(ctx.getSource().getPlayer());
                    return 1;
                })
                .then(CommandManager.argument("mode", StringArgumentType.word())
                        .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                            "follow", "slow_orbit", "aerial_view", "spiral_up", "floating", "orbit"
                        }, b))
                        .executes(ctx -> {
                            String modeStr = StringArgumentType.getString(ctx, "mode");
                            ViewMode viewMode = ViewMode.fromString(modeStr);
                            manager.startCycle(ctx.getSource().getPlayer(), viewMode);
                            return 1;
                        })));

        cycle.then(CommandManager.literal("next")
                .executes(ctx -> {
                    manager.nextCyclePoint(ctx.getSource().getPlayer());
                    return 1;
                }));

        return cycle;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildStopCommandWithLiteral(String literal) {
        return CommandManager.literal(literal)
                .executes(ctx->{
                    ServerSpectateManager.getInstance().stopSpectating(ctx.getSource().getPlayer());
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildWhoCommandWithLiteral(String literal) {
        return CommandManager.literal(literal)
                .executes(ctx -> executeWhoCommand(ctx.getSource()));
    }

    /**
     * 执行 /cspectate who 命令逻辑。
     * 显示当前所有正在旁观的玩家及其目标。
     *
     * @param source 命令源。
     * @return 命令执行状态码（通常为 1）。
     */
    private static int executeWhoCommand(ServerCommandSource source) {
        ServerSpectateManager manager = ServerSpectateManager.getInstance();
        java.util.Set<java.util.UUID> spectatingIds = manager.getSpectatingPlayerIds();

        if (spectatingIds.isEmpty()) {
            sendFeedback(source, CONFIG_MANAGER.getMessage("who_empty"), false);
            return 1;
        }

        sendFeedback(source, CONFIG_MANAGER.getMessage("who_header"), false);

        for (java.util.UUID playerId : spectatingIds) {
            ServerPlayerEntity viewer = source.getServer().getPlayerManager().getPlayer(playerId);
            if (viewer != null) {
                String viewerName = viewer.getName().getString();
                String targetInfo = manager.getSpectateTargetInfo(playerId);
                String modeInfo = manager.getSpectateViewModeInfo(playerId);

                if (targetInfo == null) targetInfo = "未知";
                if (modeInfo == null) modeInfo = "未知";

                sendFeedback(source, CONFIG_MANAGER.getFormattedMessage("who_item",
                    Map.of("viewer", viewerName, "target", targetInfo, "mode", modeInfo)), false);
            }
        }

        return 1;
    }

    /* ---------------- 辅助构建器 ---------------- */

    private static LiteralArgumentBuilder<ServerCommandSource> buildPointsCommand() {
        LiteralArgumentBuilder<ServerCommandSource> points = CommandManager.literal("points");

        // points add <名称> <坐标> [描述]
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
                    SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed, name, "default");
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
                            
                            String group = "default";
                            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("group:(\\S+)").matcher(desc);
                            if (matcher.find()) {
                                group = matcher.group(1);
                                desc = desc.replace(matcher.group(0), "").trim();
                            }
                            if (desc.isEmpty()) desc = name;

                            SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed, desc, group);
                            SpectatePointManager.getInstance().addPoint(name, data);
                            sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_added", Map.of("name", name)), false);
                            return 1;
                        }));
        
        points.then(CommandManager.literal("add").then(nameArg.then(posArg)));

        // points remove <名称>
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

        // points list [group]
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
                })
                .then(CommandManager.argument("group", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(SpectatePointManager.getInstance().listGroups(), b))
                        .executes(ctx -> {
                             String group = StringArgumentType.getString(ctx, "group");
                             Collection<String> pointNames = SpectatePointManager.getInstance().listPointNamesByGroup(group);
                             if (pointNames.isEmpty()) {
                                 sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_list_group_empty", Map.of("group", group)), false);
                             } else {
                                 sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_list_group_header", Map.of("group", group)), false);
                                 pointNames.forEach(name ->
                                     sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_list_item", Map.of("name", name)), false));
                             }
                             return 1;
                        })));

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
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                                    "slow_orbit", "aerial_view", "spiral_up", "floating", "orbit"
                                }, b))
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    String modeStr = StringArgumentType.getString(ctx, "mode");
                                    SpectatePointData point = SpectatePointManager.getInstance().getPoint(name);
                                    if(point==null){
                                        sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_not_found", Map.of("name", name)));
                                        return 0;
                                    }
                                    ViewMode viewMode = ViewMode.fromString(modeStr);
                                    ServerSpectateManager.getInstance().spectatePoint(ctx.getSource().getPlayer(), point, 
                                        viewMode);
                                    return 1;
                                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildStopCommand(){
        return CommandManager.literal("stop")
                .executes(ctx->{
                    ServerSpectateManager.getInstance().stopSpectating(ctx.getSource().getPlayer());
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildWhoCommand(){
        return CommandManager.literal("who")
                .executes(ctx -> executeWhoCommand(ctx.getSource()));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCycleCommand() {
        LiteralArgumentBuilder<ServerCommandSource> cycle = CommandManager.literal("cycle");
        ServerSpectateManager manager = ServerSpectateManager.getInstance();

        // cycle add <名称>
        cycle.then(CommandManager.literal("add")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(POINT_SUGGESTIONS)
                        .executes(ctx -> {
                            manager.addCyclePoint(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })));

        // cycle addgroup <分组>
        cycle.then(CommandManager.literal("addgroup")
                .then(CommandManager.argument("group", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(SpectatePointManager.getInstance().listGroups(), b))
                        .executes(ctx -> {
                            manager.addCycleGroup(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "group"));
                            return 1;
                        })));

        // cycle addplayer <玩家名>
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

        // cycle remove <名称>
        cycle.then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(CYCLE_SUGGESTIONS)
                        .executes(ctx -> {
                            manager.removeCyclePoint(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })));

        // cycle list (列出)
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

        // cycle clear (清空)
        cycle.then(CommandManager.literal("clear")
                .executes(ctx -> {
                    manager.clearCyclePoints(ctx.getSource().getPlayer());
                    return 1;
                }));

        // cycle interval <秒数>
        cycle.then(CommandManager.literal("interval")
                .then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(1))
                        .executes(ctx -> {
                            manager.setCycleInterval(ctx.getSource().getPlayer(), (long) DoubleArgumentType.getDouble(ctx, "seconds"));
                            return 1;
                        })));

        // cycle start (开始)
        cycle.then(CommandManager.literal("start")
                .executes(ctx -> {
                    manager.startCycle(ctx.getSource().getPlayer());
                    return 1;
                })
                .then(CommandManager.argument("mode", StringArgumentType.word())
                        .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                            "follow", "slow_orbit", "aerial_view", "spiral_up", "floating", "orbit"
                        }, b))
                        .executes(ctx -> {
                            String modeStr = StringArgumentType.getString(ctx, "mode");
                            ViewMode viewMode = ViewMode.fromString(modeStr);
                            manager.startCycle(ctx.getSource().getPlayer(), viewMode);
                            return 1;
                        })));

        // cycle next (下一个)
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
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((c,b)->CommandSource.suggestMatching(new String[]{
                                    "follow", "slow_orbit", "aerial_view", "spiral_up", "floating", "orbit"
                                }, b))
                                .executes(ctx->{
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    String modeStr = StringArgumentType.getString(ctx, "mode");
                                    ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                    if(target==null){
                                        sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("player_not_found", Map.of("name", targetName)));
                                        return 0;
                                    }
                                    ViewMode viewMode = ViewMode.fromString(modeStr);
                                    ServerSpectateManager.getInstance().spectatePlayer(ctx.getSource().getPlayer(), target, 
                                        viewMode);
                                    return 1;
                                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCoordsCommand() {
        RequiredArgumentBuilder<ServerCommandSource, ?> posArg = CommandManager.argument("pos", Vec3ArgumentType.vec3());

        // 基础命令: /cspectate coords <pos>
        posArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, settings.spectate_distance, settings.spectate_height_offset, settings.spectate_rotation_speed);
            return 1;
        });

        // 可选距离: /cspectate coords <pos> <distance>
        RequiredArgumentBuilder<ServerCommandSource, ?> distArg = CommandManager.argument("distance", DoubleArgumentType.doubleArg(1));
        distArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, settings.spectate_height_offset, settings.spectate_rotation_speed);
            return 1;
        });

        // 可选高度: /cspectate coords <pos> <distance> <heightOffset>
        RequiredArgumentBuilder<ServerCommandSource, ?> heightArg = CommandManager.argument("heightOffset", DoubleArgumentType.doubleArg());
        heightArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            SpectateConfig.Settings settings = CONFIG_MANAGER.getConfig().settings;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, settings.spectate_rotation_speed);
            return 1;
        });

        // 可选旋转: /cspectate coords <pos> <distance> <heightOffset> <rotationSpeed>
        RequiredArgumentBuilder<ServerCommandSource, ?> rotArg = CommandManager.argument("rotationSpeed", DoubleArgumentType.doubleArg(0));
        rotArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            double rot = DoubleArgumentType.getDouble(ctx, "rotationSpeed");
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, rot);
            return 1;
        });

        // 链式参数
        heightArg.then(rotArg);
        distArg.then(heightArg);
        posArg.then(distArg);

        return CommandManager.literal("coords").then(posArg);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildConfigCommand() {
        LiteralArgumentBuilder<ServerCommandSource> config = CommandManager.literal("config");

        // config reload (重载)
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

        // config get <路径>
        config.then(CommandManager.literal("get")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("path", StringArgumentType.string())
                        .suggests((c, b) -> {
                            // 提供 settings 和 lang 的字段建议
                            java.util.List<String> suggestions = new java.util.ArrayList<>();
                            
                            // 添加 settings 字段
                            java.lang.reflect.Field[] settingsFields = SpectateConfig.Settings.class.getFields();
                            for (java.lang.reflect.Field field : settingsFields) {
                                suggestions.add("settings." + field.getName());
                            }
                            
                            // 添加 lang 字段
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

        // config set <路径> <值>
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

        // config list [分类]
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

    /**
     * 获取配置字段的中文注释说明。
     * 用于 /cspectate config list 命令的输出。
     *
     * @param fieldName 字段名称。
     * @return 注释字符串，如果没有对应注释则返回空字符串。
     */
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

    private static LiteralArgumentBuilder<ServerCommandSource> buildStatsCommand() {
        LiteralArgumentBuilder<ServerCommandSource> stats = CommandManager.literal("stats");

        // stats (self)
        stats.executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayer();
            showStats(ctx.getSource(), player.getUuid(), player.getName().getString(), true);
            return 1;
        });

        // stats <player>
        stats.then(CommandManager.argument("target", StringArgumentType.word())
                .suggests((c,b)->CommandSource.suggestMatching(c.getSource().getServer().getPlayerManager().getPlayerNames(), b))
                .executes(ctx -> {
                    String targetName = StringArgumentType.getString(ctx, "target");
                    
                    ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                    UUID targetId = null;
                    if (target != null) {
                        targetId = target.getUuid();
                    } else {
                        // Try offline lookup
                        //#if MC >= 11700
                        com.mojang.authlib.GameProfile profile = ctx.getSource().getServer().getUserCache().findByName(targetName).orElse(null);
                        //#else
                        //$$com.mojang.authlib.GameProfile profile = ctx.getSource().getMinecraftServer().getUserCache().findByName(targetName);
                        //#endif
                        if (profile != null) {
                            targetId = profile.getId();
                        }
                    }
                    
                    if (targetId == null) {
                        sendError(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("player_not_found", Map.of("name", targetName)));
                        return 0;
                    }
                    
                    showStats(ctx.getSource(), targetId, targetName, false);
                    return 1;
                }));
                
        return stats;
    }

    private static void showStats(ServerCommandSource source, UUID playerId, String name, boolean isSelf) {
        SpectateStats stats = SpectateStatsManager.getInstance().getStats(playerId);
        long storedViewing = stats.totalSpectatingTime;
        long storedWatched = stats.totalSpectatedTime;
        
        long currentViewing = SpectateSessionManager.getInstance().getCurrentSpectatingDuration(playerId);
        long currentWatched = SpectateSessionManager.getInstance().getCurrentBeingSpectatedDuration(playerId);
        
        long totalViewing = storedViewing + currentViewing;
        long totalWatched = storedWatched + currentWatched;
        
        if (totalViewing == 0 && totalWatched == 0) {
             sendError(source, CONFIG_MANAGER.getMessage("stats_not_found"));
             return;
        }

        if (isSelf) {
            sendFeedback(source, CONFIG_MANAGER.getMessage("stats_header_self"), false);
        } else {
            sendFeedback(source, CONFIG_MANAGER.getFormattedMessage("stats_header_other", Map.of("name", name)), false);
        }
        
        sendFeedback(source, CONFIG_MANAGER.getFormattedMessage("stats_viewing_time", Map.of("time", formatTime(totalViewing))), false);
        sendFeedback(source, CONFIG_MANAGER.getFormattedMessage("stats_watched_time", Map.of("time", formatTime(totalWatched))), false);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildTopCommand() {
        LiteralArgumentBuilder<ServerCommandSource> top = CommandManager.literal("top");
        
        top.executes(ctx -> {
            // Default to viewing
            showTop(ctx.getSource(), "viewing");
            return 1;
        });
        
        top.then(CommandManager.literal("viewing").executes(ctx -> {
            showTop(ctx.getSource(), "viewing");
            return 1;
        }));
        
        top.then(CommandManager.literal("watched").executes(ctx -> {
            showTop(ctx.getSource(), "watched");
            return 1;
        }));
        
        return top;
    }
    
    private static void showTop(ServerCommandSource source, String type) {
        List<Map.Entry<UUID, Long>> list;
        boolean viewing = "viewing".equals(type);
        
        if (viewing) {
            list = SpectateStatsManager.getInstance().getTopViewing(10);
            sendFeedback(source, CONFIG_MANAGER.getMessage("top_header_viewing"), false);
        } else {
            list = SpectateStatsManager.getInstance().getTopWatched(10);
            sendFeedback(source, CONFIG_MANAGER.getMessage("top_header_watched"), false);
        }
        
        if (list.isEmpty()) {
            sendFeedback(source, CONFIG_MANAGER.getMessage("top_empty"), false);
            return;
        }
        
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : list) {
            String name = SpectateStatsManager.getInstance().getName(entry.getKey());
            sendFeedback(source, CONFIG_MANAGER.getFormattedMessage("top_item", 
                Map.of("rank", String.valueOf(rank), "name", name, "time", formatTime(entry.getValue()))), false);
            rank++;
        }
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%dh %dm %ds", h, m, s);
        if (m > 0) return String.format("%dm %ds", m, s);
        return String.format("%ds", s);
    }
}
