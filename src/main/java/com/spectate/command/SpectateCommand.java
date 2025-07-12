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
                    SpectateConfig.Defaults defaults = CONFIG_MANAGER.getConfig().defaults;
                    //#if MC >= 11900
                    String dimension = ctx.getSource().getPlayer().getWorld().getRegistryKey().getValue().toString();
                    //#else
                    //$$String dimension = ctx.getSource().getPlayer().getServerWorld().getRegistryKey().getValue().toString();
                    //#endif
                    SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), defaults.spectate_distance, defaults.spectate_height_offset, defaults.spectate_rotation_speed, name);
                    SpectatePointManager.getInstance().addPoint(name, data);
                    sendFeedback(ctx.getSource(), CONFIG_MANAGER.getFormattedMessage("point_added", Map.of("name", name)), false);
                    return 1;
                })
                .then(CommandManager.argument("description", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
                            String desc = StringArgumentType.getString(ctx, "description");
                            SpectateConfig.Defaults defaults = CONFIG_MANAGER.getConfig().defaults;
                            //#if MC >= 11900
                            String dimension = ctx.getSource().getPlayer().getWorld().getRegistryKey().getValue().toString();
                            //#else
                            //$$String dimension = ctx.getSource().getPlayer().getServerWorld().getRegistryKey().getValue().toString();
                            //#endif
                            SpectatePointData data = new SpectatePointData(dimension, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), defaults.spectate_distance, defaults.spectate_height_offset, defaults.spectate_rotation_speed, desc);
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
                                            "slow_orbit", "dolly_zoom", "aerial_view", "spiral_up", 
                                            "figure_eight", "pendulum", "smooth_follow", "floating"
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
                            manager.addCyclePoint(ctx.getSource().getPlayer(), "player:" + playerName);
                            return 1;
                        })));

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
                }));

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
                                            "slow_orbit", "dolly_zoom", "aerial_view", "spiral_up", 
                                            "figure_eight", "pendulum", "smooth_follow", "floating"
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
            SpectateConfig.Defaults defaults = CONFIG_MANAGER.getConfig().defaults;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, defaults.spectate_distance, defaults.spectate_height_offset, defaults.spectate_rotation_speed);
            return 1;
        });

        // Optional distance: /cspectate coords <pos> <distance>
        RequiredArgumentBuilder<ServerCommandSource, ?> distArg = CommandManager.argument("distance", DoubleArgumentType.doubleArg(1));
        distArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            SpectateConfig.Defaults defaults = CONFIG_MANAGER.getConfig().defaults;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, defaults.spectate_height_offset, defaults.spectate_rotation_speed);
            return 1;
        });

        // Optional height: /cspectate coords <pos> <distance> <heightOffset>
        RequiredArgumentBuilder<ServerCommandSource, ?> heightArg = CommandManager.argument("heightOffset", DoubleArgumentType.doubleArg());
        heightArg.executes(ctx -> {
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            SpectateConfig.Defaults defaults = CONFIG_MANAGER.getConfig().defaults;
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, defaults.spectate_rotation_speed);
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
}
