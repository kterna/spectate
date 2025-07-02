package com.spectate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spectate.server.ServerSpectateManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class SpectateCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cspectate")
                .requires(source -> source.hasPermission(0)) // 所有玩家都可以使用
                
                // 观察玩家
                .then(Commands.literal("player")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(SpectateCommand::spectatePlayer)))
                
                // 观察坐标
                .then(Commands.literal("coords")
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(context -> spectateCoords(context, 50.0f))
                                .then(Commands.argument("distance", FloatArgumentType.floatArg(5.0f, 200.0f))
                                        .executes(SpectateCommand::spectateCoordsWithDistance))))
                
                // 观察预设点
                .then(Commands.literal("point")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    SpectatePointManager.getInstance().getPointNames().stream()
                                            .filter(name -> name.toLowerCase().startsWith(input))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(SpectateCommand::spectatePoint)))
                
                // 停止观察
                .then(Commands.literal("stop")
                        .executes(SpectateCommand::stopSpectating))
                
                // 观察点管理
                .then(Commands.literal("points")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("position", Vec3Argument.vec3())
                                                .executes(context -> addPoint(context, 50.0f, 0.0f, null))
                                                .then(Commands.argument("distance", FloatArgumentType.floatArg(5.0f, 200.0f))
                                                        .executes(context -> addPoint(context, 
                                                                FloatArgumentType.getFloat(context, "distance"), 0.0f, null))
                                                        .then(Commands.argument("height", FloatArgumentType.floatArg(-50.0f, 50.0f))
                                                                .executes(context -> addPoint(context,
                                                                        FloatArgumentType.getFloat(context, "distance"),
                                                                        FloatArgumentType.getFloat(context, "height"), null))
                                                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                                                        .executes(context -> addPoint(context,
                                                                                FloatArgumentType.getFloat(context, "distance"),
                                                                                FloatArgumentType.getFloat(context, "height"),
                                                                                StringArgumentType.getString(context, "description")))))))))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            String input = builder.getRemaining().toLowerCase();
                                            SpectatePointManager.getInstance().getPointNames().stream()
                                                    .filter(name -> name.toLowerCase().startsWith(input))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("position", Vec3Argument.vec3())
                                                .executes(context -> editPoint(context, 50.0f, 0.0f, null))
                                                .then(Commands.argument("distance", FloatArgumentType.floatArg(5.0f, 200.0f))
                                                        .executes(context -> editPoint(context, 
                                                                FloatArgumentType.getFloat(context, "distance"), 0.0f, null))
                                                        .then(Commands.argument("height", FloatArgumentType.floatArg(-50.0f, 50.0f))
                                                                .executes(context -> editPoint(context,
                                                                        FloatArgumentType.getFloat(context, "distance"),
                                                                        FloatArgumentType.getFloat(context, "height"), null))
                                                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                                                        .executes(context -> editPoint(context,
                                                                                FloatArgumentType.getFloat(context, "distance"),
                                                                                FloatArgumentType.getFloat(context, "height"),
                                                                                StringArgumentType.getString(context, "description")))))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            String input = builder.getRemaining().toLowerCase();
                                            SpectatePointManager.getInstance().getPointNames().stream()
                                                    .filter(name -> name.toLowerCase().startsWith(input))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(SpectateCommand::removePoint)))
                        .then(Commands.literal("list")
                                .executes(SpectateCommand::listPoints)))
                
                // 循环观察管理
                .then(Commands.literal("cycle")
                        .then(Commands.literal("add")
                                .then(Commands.literal("point")
                                        .then(Commands.argument("pointName", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    String input = builder.getRemaining().toLowerCase();
                                                    SpectatePointManager.getInstance().getPointNames().stream()
                                                            .filter(name -> name.toLowerCase().startsWith(input))
                                                            .forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .executes(SpectateCommand::addPointToCycle)))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(SpectateCommand::addPlayerToCycle))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("pointName", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            try {
                                                ServerPlayer player = context.getSource().getPlayerOrException();
                                                String input = builder.getRemaining().toLowerCase();
                                                com.spectate.data.SpectateStateSaver.getInstance()
                                                        .getPlayerCycleList(player.getUUID()).stream()
                                                        .filter(name -> name.toLowerCase().startsWith(input))
                                                        .forEach(builder::suggest);
                                            } catch (Exception e) {
                                                // 忽略异常，不影响补全
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(SpectateCommand::removeFromCycle)))
                        .then(Commands.literal("clear")
                                .executes(SpectateCommand::clearCycle))
                        .then(Commands.literal("list")
                                .executes(SpectateCommand::listCycle))
                        .then(Commands.literal("duration")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 3600))
                                        .executes(SpectateCommand::setCycleDuration)))
                        .then(Commands.literal("start")
                                .executes(SpectateCommand::startCycle))
                        .then(Commands.literal("stop")
                                .executes(SpectateCommand::stopCycle))
                        .then(Commands.literal("next")
                                .executes(SpectateCommand::nextCyclePoint))
                        .then(Commands.literal("status")
                                .executes(SpectateCommand::cycleStatus)))
                
                .executes(SpectateCommand::showUsage)
        );
    }

    private static int showUsage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "§e=== Spectate Mod 服务端命令 ===\n" +
                "§f观察命令:\n" +
                "  /cspectate player <玩家> - 实时跟踪指定玩家\n" +
                "  /cspectate coords <x> <y> <z> [距离] - 观察指定坐标\n" +
                "  /cspectate point <名称> - 观察预设点\n" +
                "  /cspectate stop - 停止观察模式\n\n" +
                "§f观察点管理:\n" +
                "  /cspectate points add <名称> <坐标> [距离] [高度] [描述]\n" +
                "  /cspectate points edit <名称> <坐标> [距离] [高度] [描述]\n" +
                "  /cspectate points remove <名称>\n" +
                "  /cspectate points list\n\n" +
                "§f循环观察:\n" +
                "  /cspectate cycle add point <观察点> - 添加观察点到循环列表\n" +
                "  /cspectate cycle add player <玩家> - 添加玩家跟踪到循环列表\n" +
                "  /cspectate cycle remove <观察点> - 从循环列表移除\n" +
                "  /cspectate cycle duration <秒数> - 设置每个点的观察时长\n" +
                "  /cspectate cycle start - 开始循环观察\n" +
                "  /cspectate cycle stop - 停止循环观察\n" +
                "  /cspectate cycle next - 跳到下一个观察点\n" +
                "  /cspectate cycle status - 查看循环状态"
        ), false);
        return 1;
    }

    private static int spectatePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer executor = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        
        boolean success = ServerSpectateManager.getInstance().startTrackingPlayer(executor, target, 20.0f);
        
        if (success) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a开始跟踪玩家: " + target.getName().getString()
            ), false);
        } else {
            context.getSource().sendFailure(Component.literal("§c跟踪玩家失败"));
        }
        
        return success ? 1 : 0;
    }

    private static int spectateCoords(CommandContext<CommandSourceStack> context, float distance) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 position = Vec3Argument.getVec3(context, "position");
        
        boolean success = ServerSpectateManager.getInstance().startSpectating(player, position, distance);
        return success ? 1 : 0;
    }

    private static int spectateCoordsWithDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float distance = FloatArgumentType.getFloat(context, "distance");
        return spectateCoords(context, distance);
    }

    private static int spectatePoint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String pointName = StringArgumentType.getString(context, "name");
        
        boolean success = ServerSpectateManager.getInstance().spectatePoint(player, pointName);
        return success ? 1 : 0;
    }

    private static int stopSpectating(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean success = ServerSpectateManager.getInstance().stopSpectating(player, true);
        return success ? 1 : 0;
    }

    private static int addPoint(CommandContext<CommandSourceStack> context, float distance, float height, String description) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        Vec3 position = Vec3Argument.getVec3(context, "position");
        
        final String finalDescription = (description == null) ? name : description;
        
        boolean success = SpectatePointManager.getInstance().addPoint(name, position, distance, height, finalDescription);
        
        if (success) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a已添加观察点: " + name + " §7(" + finalDescription + ")"
            ), false);
        } else {
            context.getSource().sendFailure(Component.literal("§c添加观察点失败"));
        }
        
        return success ? 1 : 0;
    }

    private static int removePoint(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean success = SpectatePointManager.getInstance().removePoint(name);
        
        if (success) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a已删除观察点: " + name
            ), false);
        } else {
            context.getSource().sendFailure(Component.literal("§c观察点不存在: " + name));
        }
        
        return success ? 1 : 0;
    }

    private static int listPoints(CommandContext<CommandSourceStack> context) {
        var points = SpectatePointManager.getInstance().getAllPoints();
        
        if (points.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§e没有保存的观察点"), false);
            return 1;
        }
        
        context.getSource().sendSuccess(() -> Component.literal("§e=== 观察点列表 ==="), false);
        for (var entry : points.entrySet()) {
            String name = entry.getKey();
            var point = entry.getValue();
            context.getSource().sendSuccess(() -> Component.literal(
                    "§f" + name + ": §7" + point.toString()
            ), false);
        }
        context.getSource().sendSuccess(() -> Component.literal("§e总计: " + points.size() + " 个观察点"), false);
        
        return 1;
    }

    // 编辑观察点
    private static int editPoint(CommandContext<CommandSourceStack> context, float distance, float height, String description) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        Vec3 position = Vec3Argument.getVec3(context, "position");
        
        // 检查观察点是否存在
        if (!SpectatePointManager.getInstance().hasPoint(name)) {
            context.getSource().sendFailure(Component.literal("§c观察点 '" + name + "' 不存在"));
            return 0;
        }
        
        final String finalDescription = (description == null) ? 
            SpectatePointManager.getInstance().getPoint(name).description : description;
        
        boolean success = SpectatePointManager.getInstance().updatePoint(name, position, distance, height, finalDescription);
        
        if (success) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a已编辑观察点: " + name + " §7(" + finalDescription + ")"
            ), false);
        } else {
            context.getSource().sendFailure(Component.literal("§c编辑观察点失败"));
        }
        
        return success ? 1 : 0;
    }

    // 循环观察相关命令
    private static int addPointToCycle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String pointName = StringArgumentType.getString(context, "pointName");
        
        // 检查观察点是否存在
        if (!SpectatePointManager.getInstance().hasPoint(pointName)) {
            context.getSource().sendFailure(Component.literal("§c观察点 '" + pointName + "' 不存在"));
            return 0;
        }
        
        boolean success = com.spectate.data.SpectateStateSaver.getInstance().addPointToCycle(player.getUUID(), pointName);
        
        if (success) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a已将观察点 '" + pointName + "' 添加到您的循环列表"
            ), false);
        } else {
            context.getSource().sendFailure(Component.literal("§c添加失败"));
        }
        
        return success ? 1 : 0;
    }

    private static int addPlayerToCycle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        
        // 创建玩家跟踪标识符，使用 "player:" 前缀表示这是玩家跟踪而不是固定点
        String playerTrackingName = "player:" + target.getName().getString().toLowerCase();
        
        // 添加到循环列表（不需要在SpectatePointManager中创建，因为这是动态跟踪）
        boolean success = com.spectate.data.SpectateStateSaver.getInstance().addPointToCycle(player.getUUID(), playerTrackingName);
        
        if (success) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a已将玩家跟踪 '" + target.getName().getString() + "' 添加到您的循环列表"
            ), false);
        } else {
            context.getSource().sendFailure(Component.literal("§c添加失败，可能已存在"));
        }
        
        return success ? 1 : 0;
    }

    private static int removeFromCycle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String pointName = StringArgumentType.getString(context, "pointName");
        
        boolean success = com.spectate.data.SpectateStateSaver.getInstance().removePointFromCycle(player.getUUID(), pointName);
        
        if (success) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a已从循环列表中移除观察点 '" + pointName + "'"
            ), false);
        } else {
            context.getSource().sendFailure(Component.literal("§c移除失败"));
        }
        
        return success ? 1 : 0;
    }

    private static int clearCycle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        
        com.spectate.data.SpectateStateSaver.getInstance().clearPlayerCycle(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("§a已清空您的循环列表"), false);
        
        return 1;
    }

    private static int listCycle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        
        var points = com.spectate.data.SpectateStateSaver.getInstance().getPlayerCycleList(player.getUUID());
        
        if (points.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§e您的循环列表为空"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("§e=== 您的循环列表 ==="), false);
            for (int i = 0; i < points.size(); i++) {
                final String point = points.get(i);
                final int index = i + 1;
                
                // 格式化显示名称
                String displayName;
                if (point.startsWith("player:")) {
                    String playerName = point.substring(7); // 移除 "player:" 前缀
                    displayName = point + " §7(跟踪玩家: " + playerName + ")";
                } else {
                    displayName = point + " §7(观察点)";
                }
                
                context.getSource().sendSuccess(() -> Component.literal(
                        "§f" + index + ". " + displayName
                ), false);
            }
        }
        
        return 1;
    }

    private static int setCycleDuration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int duration = IntegerArgumentType.getInteger(context, "seconds");
        
        if (duration < 10) {
            context.getSource().sendFailure(Component.literal("§c设置失败，时长必须至少为10秒"));
            return 0;
        }
        
        com.spectate.data.SpectateStateSaver.getInstance().setPlayerCycleDuration(player.getUUID(), duration);
        context.getSource().sendSuccess(() -> Component.literal(
                "§a已设置每个观察点的观察时长为 " + duration + " 秒"
        ), false);
        
        return 1;
    }

    private static int startCycle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean success = ServerSpectateManager.getInstance().startCycle(player);
        return success ? 1 : 0;
    }

    private static int stopCycle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean success = ServerSpectateManager.getInstance().stopCycle(player, true);
        return success ? 1 : 0;
    }

    private static int nextCyclePoint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean success = ServerSpectateManager.getInstance().nextCyclePoint(player);
        return success ? 1 : 0;
    }

    private static int cycleStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String status = ServerSpectateManager.getInstance().getCycleStatus(player.getUUID());
        
        context.getSource().sendSuccess(() -> Component.literal("§e循环状态: " + status), false);
        return 1;
    }
}
