package com.spectate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.spectate.data.SpectatePointData;
import com.spectate.service.SpectatePointManager;
import com.spectate.service.ServerSpectateManager;
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
import java.util.Arrays;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.util.math.Vec3d;
import java.util.Collections;
import java.util.List;

/**
 * 负责注册 /cspectate points 系列命令。
 */
public class SpectateCommand {

    // Helper method for cross-version Text creation
    private static Text createText(String message) {
//#if MC >= 11900
        return Text.literal(message);
//#else
        //$$return new LiteralText(message);
//#endif
    }

    // Helper method for cross-version sendFeedback
    private static void sendFeedback(ServerCommandSource source, Text text, boolean broadcastToOps) {
//#if MC >= 11900
        source.sendFeedback(() -> text, broadcastToOps);
//#else
        //$$source.sendFeedback(text, broadcastToOps);
//#endif
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
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("cspectate")
                .requires(src -> src.hasPermissionLevel(2));

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
        points.then(buildPointsAddCommand());

        // remove
        points.then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(SpectatePointManager.getInstance().listPointNames(), b))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            if (SpectatePointManager.getInstance().removePoint(name) != null) {
                                sendFeedback(ctx.getSource(), createText("Removed point " + name), false);
                                return 1;
                            } else {
                                ctx.getSource().sendError(createText("Point not found: " + name));
                                return 0;
                            }
                        })));

        // list
        points.then(CommandManager.literal("list")
                .executes(ctx -> {
                    sendFeedback(ctx.getSource(), createText("Spectate Points:"), false);
                    SpectatePointManager.getInstance().listPointNames().forEach(name ->
                        sendFeedback(ctx.getSource(), createText(" - " + name), false));
                    return 1;
                }));

        return points;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPointsAddCommand() {
        // /cspectate points add <name> <pos> [distance] [height] [rotation] [description]
        RequiredArgumentBuilder<ServerCommandSource, String> nameArg = CommandManager.argument("name", StringArgumentType.word());

        // pos mandatory
        RequiredArgumentBuilder<ServerCommandSource, ?> posArg = CommandManager.argument("pos", Vec3ArgumentType.vec3());

        // base execute (defaults)
        posArg.executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            SpectatePointData data = new SpectatePointData(new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), 20, 0, 1, name);
            SpectatePointManager.getInstance().addPoint(name, data);
            sendFeedback(ctx.getSource(), createText("Added spectate point " + name), false);
            return 1;
        });

        // distance optional
        RequiredArgumentBuilder<ServerCommandSource, ?> distArg = CommandManager.argument("distance", DoubleArgumentType.doubleArg(1));
        distArg.executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double distance = DoubleArgumentType.getDouble(ctx, "distance");
            SpectatePointData data = new SpectatePointData(new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), distance, 0, 1, name);
            SpectatePointManager.getInstance().addPoint(name, data);
            sendFeedback(ctx.getSource(), createText("Added spectate point " + name), false);
            return 1;
        });

        // height optional
        RequiredArgumentBuilder<ServerCommandSource, ?> heightArg = CommandManager.argument("heightOffset", DoubleArgumentType.doubleArg());

        // when height provided, default rotation
        heightArg.executes(ctx->{
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, 1);
            return 1;
        });

        // rotation arg
        RequiredArgumentBuilder<ServerCommandSource, ?> rotArg = CommandManager.argument("rotationSpeed", DoubleArgumentType.doubleArg(0));
        rotArg.executes(ctx->{
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            double rot = DoubleArgumentType.getDouble(ctx, "rotationSpeed");
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, rot);
            return 1;
        });

        // description optional (greedy string)
        RequiredArgumentBuilder<ServerCommandSource, ?> descArg = CommandManager.argument("description", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
                    double dist = DoubleArgumentType.getDouble(ctx, "distance");
                    double height = DoubleArgumentType.getDouble(ctx, "heightOffset");
                    double rot = DoubleArgumentType.getDouble(ctx, "rotationSpeed");
                    String desc = StringArgumentType.getString(ctx, "description");
                    SpectatePointData data = new SpectatePointData(new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), dist, height, rot, desc);
                    SpectatePointManager.getInstance().addPoint(name, data);
                    sendFeedback(ctx.getSource(), createText("Added spectate point " + name), false);
                    return 1;
                });

        // chain the optional args
        rotArg.then(descArg);
        heightArg.then(rotArg);
        distArg.then(heightArg);
        posArg.then(distArg);

        return CommandManager.literal("add").then(nameArg.then(posArg));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPointCommand() {
        return CommandManager.literal("point")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((c,b)->CommandSource.suggestMatching(SpectatePointManager.getInstance().listPointNames(), b))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            SpectatePointData point = SpectatePointManager.getInstance().getPoint(name);
                            if(point==null){
                                ctx.getSource().sendError(createText("Unknown point: "+name));
                                return 0;
                            }
                            ServerSpectateManager.getInstance().spectatePoint(ctx.getSource().getPlayer(), point);
                            return 1;
                        }));
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

        // /cspectate cycle add <name> - 添加观察点到列表
        cycle.then(CommandManager.literal("add")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(SpectatePointManager.getInstance().listPointNames(), b))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            ServerSpectateManager.getInstance().addCyclePoint(ctx.getSource().getPlayer(), name);
                            return 1;
                        })));

        // /cspectate cycle add player:<name> - 添加玩家到列表
        cycle.then(CommandManager.literal("addplayer")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(c.getSource().getServer().getPlayerManager().getPlayerNames(), b))
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            ServerSpectateManager.getInstance().addCyclePoint(ctx.getSource().getPlayer(), "player:" + playerName);
                            return 1;
                        })));

        // /cspectate cycle remove <name> - 从列表移除
        cycle.then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            ServerPlayerEntity player = c.getSource().getPlayer();
                            if (player != null) {
                                return CommandSource.suggestMatching(ServerSpectateManager.getInstance().listCyclePoints(player), b);
                            }
                            return CommandSource.suggestMatching(Collections.emptyList(), b);
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            ServerSpectateManager.getInstance().removeCyclePoint(ctx.getSource().getPlayer(), name);
                            return 1;
                        })));

        // /cspectate cycle list - 列出所有点
        cycle.then(CommandManager.literal("list")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    List<String> points = ServerSpectateManager.getInstance().listCyclePoints(player);
                    if (points.isEmpty()) {
                        sendFeedback(ctx.getSource(), createText("Your cycle list is empty."), false);
                    } else {
                        sendFeedback(ctx.getSource(), createText("Your cycle list:"), false);
                        int index = 1;
                        for (String point : points) {
                            final int i = index++;
                            sendFeedback(ctx.getSource(), createText(i + ". " + point), false);
                        }
                    }
                    return 1;
                }));

        // /cspectate cycle clear - 清空列表
        cycle.then(CommandManager.literal("clear")
                .executes(ctx -> {
                    ServerSpectateManager.getInstance().clearCyclePoints(ctx.getSource().getPlayer());
                    return 1;
                }));

        // /cspectate cycle interval <seconds> - 设置间隔
        cycle.then(CommandManager.literal("interval")
                .then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(1))
                        .executes(ctx -> {
                            double seconds = DoubleArgumentType.getDouble(ctx, "seconds");
                            ServerSpectateManager.getInstance().setCycleInterval(ctx.getSource().getPlayer(), (long) seconds);
                            return 1;
                        })));

        // /cspectate cycle start - 开始循环
        cycle.then(CommandManager.literal("start")
                .executes(ctx -> {
                    ServerSpectateManager.getInstance().startCycle(ctx.getSource().getPlayer());
                    return 1;
                })
                // 兼容旧版本的直接列表参数
                .then(CommandManager.argument("list", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String listStr = StringArgumentType.getString(ctx, "list");
                            java.util.List<String> names = java.util.Arrays.asList(listStr.split(","));
                            ServerSpectateManager.getInstance().startCycle(ctx.getSource().getPlayer(), names, 10);
                            return 1;
                        })));

        // /cspectate cycle next - 切换到下一个点
        cycle.then(CommandManager.literal("next")
                .executes(ctx -> {
                    ServerSpectateManager.getInstance().nextCyclePoint(ctx.getSource().getPlayer());
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
                                ctx.getSource().sendError(createText("Player not found: "+targetName));
                                return 0;
                            }
                            ServerSpectateManager.getInstance().spectatePlayer(ctx.getSource().getPlayer(), target);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCoordsCommand(){
        RequiredArgumentBuilder<ServerCommandSource, ?> posArg = CommandManager.argument("pos", Vec3ArgumentType.vec3());

        // only pos
        posArg.executes(ctx->{
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, 20, 0);
            return 1;
        });

        RequiredArgumentBuilder<ServerCommandSource, ?> distArg = CommandManager.argument("distance", DoubleArgumentType.doubleArg(1));
        distArg.executes(ctx->{
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, 0);
            return 1;
        });

        RequiredArgumentBuilder<ServerCommandSource, ?> heightArg = CommandManager.argument("heightOffset", DoubleArgumentType.doubleArg());

        // when height provided, default rotation
        heightArg.executes(ctx->{
            Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double h = DoubleArgumentType.getDouble(ctx, "heightOffset");
            ServerSpectateManager.getInstance().spectateCoords(ctx.getSource().getPlayer(), pos.x, pos.y, pos.z, dist, h, 1);
            return 1;
        });

        // rotation arg
        RequiredArgumentBuilder<ServerCommandSource, ?> rotArg = CommandManager.argument("rotationSpeed", DoubleArgumentType.doubleArg(0));
        rotArg.executes(ctx->{
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

        return CommandManager.literal("coords").then(posArg);
    }
}
