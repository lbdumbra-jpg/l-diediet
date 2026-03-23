package com.levbu.ldiediet.command;

import com.levbu.ldiediet.capability.ActiveGroupEffect;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.rewards.RewardHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;


public class LDieDietCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ldiediet")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reset")
                        .executes(context -> resetPlayer(context.getSource().getPlayerOrException()))
                )
                .then(Commands.literal("clear_history")
                        .executes(context -> clearHistory(context.getSource().getPlayerOrException()))
                )
                .then(Commands.literal("clear_effects")
                        .executes(context -> clearEffects(context.getSource().getPlayerOrException()))
                )
        );
    }

    private static int resetPlayer(ServerPlayer player) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
          
            List<ActiveGroupEffect> effects = new ArrayList<>(cap.getActiveEffects());
            for (ActiveGroupEffect effect : effects) {
                RewardHandler.removeEffect(player, effect);
            }
            
            
            cap.clearHistory();
            cap.clearActiveEffects();
            cap.clearPermanentRewards();
            
            CapabilityHandler.syncToClient(player);
            player.sendSystemMessage(Component.translatable("ldiediet.command.reset"));
        }
        return 1;
    }

    private static int clearHistory(ServerPlayer player) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
            cap.clearHistory();
            CapabilityHandler.syncToClient(player);
            player.sendSystemMessage(Component.translatable("ldiediet.command.clear_history"));
        }
        return 1;
    }

    private static int clearEffects(ServerPlayer player) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
          
            List<ActiveGroupEffect> effects = new ArrayList<>(cap.getActiveEffects());
            for (ActiveGroupEffect effect : effects) {
                RewardHandler.removeEffect(player, effect);
            }
            
            cap.clearActiveEffects();
            CapabilityHandler.syncToClient(player);
            player.sendSystemMessage(Component.translatable("ldiediet.command.clear_effects"));
        }
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }
}
