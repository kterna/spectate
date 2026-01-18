package com.spectate.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.spectate.service.ServerSpectateManager;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家个人循环列表的建议提供者。
 */
public class CyclePointSuggestionProvider implements SuggestionProvider<ServerCommandSource> {

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        //#if MC >= 11900
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            return CommandSource.suggestMatching(ServerSpectateManager.getInstance().listCyclePoints(player), builder);
        }
        //#else
        //$$ try {
        //$$     ServerPlayerEntity player = context.getSource().getPlayer();
        //$$     if (player != null) {
        //$$         return CommandSource.suggestMatching(ServerSpectateManager.getInstance().listCyclePoints(player), builder);
        //$$     }
        //$$ } catch (CommandSyntaxException e) {
        //$$     // Ignored
        //$$ }
        //#endif
        return CommandSource.suggestMatching(Collections.emptyList(), builder);
    }
}