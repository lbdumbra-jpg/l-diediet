package com.lumbra.ldiediet.command;

import com.lumbra.ldiediet.LDieDietServerConfig;

import com.lumbra.ldiediet.capability.ActiveGroupEffect;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.rewards.RewardHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.lumbra.ldiediet.data.DietConfigManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class LDieDietCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext context) {
        AuthorCommands.register(dispatcher);
        dispatcher.register(Commands.literal("ldiediet")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                        )
                )
                .then(Commands.literal("clear_history")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> clearHistory(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                        )
                )
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .executes(ctx -> getMonotony(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ItemArgument.getItem(ctx, "item").createItemStack(1, false)))
                                )
                        )
                )
                .then(Commands.literal("get_monotony")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .executes(ctx -> getMonotony(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ItemArgument.getItem(ctx, "item").createItemStack(1, false))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                                .executes(ctx -> setMonotony(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), FloatArgumentType.getFloat(ctx, "value")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("set_monotony")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                                .executes(ctx -> setMonotony(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), FloatArgumentType.getFloat(ctx, "value")))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                .executes(ctx -> addMonotony(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), FloatArgumentType.getFloat(ctx, "value")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("calc_all_cci")
                        .executes(ctx -> calcAllCCI(ctx.getSource()))
                )
                .then(Commands.literal("update_config")
                        .executes(ctx -> updateConfig(ctx.getSource()))
                )
                .then(Commands.literal("clean_config")
                        .executes(ctx -> cleanConfigCommand(ctx.getSource()))
                )
        );
    }

    private static int getMonotony(CommandSourceStack source, ServerPlayer player, ItemStack stack) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
            Item item = stack.getItem();
            float m = stack.hasTag() ? cap.getMonotony(DietConfigManager.getFoodKey(stack, false)) : cap.getMonotony(item);
            source.sendSuccess(() -> Component.translatable("ldiediet.command.monotony",item.getDescription(),player.getDisplayName(),m), false);
        }
        return 1;
    }

    private static int setMonotony(CommandSourceStack source, ServerPlayer player, ItemStack stack, float value) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
            Item item = stack.getItem();
            if(stack.hasTag())com.lumbra.ldiediet.api.PackApi.setMonotony(player,stack,value);
            else {var keys=new java.util.HashSet<>(cap.getAllFoodMonotony().keySet());keys.add(com.lumbra.ldiediet.data.FoodKey.of(item));
                for(var key:keys)if(key.item().equals(item))com.lumbra.ldiediet.api.PackApi.setMonotony(player,key,value);}
            CapabilityHandler.syncToClient(player);
            source.sendSuccess(() -> Component.translatable("ldiediet.command.monotony_set",item.getDescription(),player.getDisplayName(),value), true);
        }
        return 1;
    }

    private static int addMonotony(CommandSourceStack source, ServerPlayer player, ItemStack stack, float value) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
            Item item = stack.getItem();
            if(stack.hasTag())com.lumbra.ldiediet.api.PackApi.addMonotony(player,stack,value);
            else {var keys=new java.util.HashSet<>(cap.getAllFoodMonotony().keySet());keys.add(com.lumbra.ldiediet.data.FoodKey.of(item));
                for(var key:keys)if(key.item().equals(item))com.lumbra.ldiediet.api.PackApi.setMonotony(player,key,(double)cap.getMonotony(key)+value);}

            CapabilityHandler.syncToClient(player);
            source.sendSuccess(() -> Component.translatable("ldiediet.command.monotony_add",value,item.getDescription(),player.getDisplayName()), true);
        }
        return 1;
    }

    private static int resetPlayer(CommandSourceStack source, ServerPlayer player) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
            List<ActiveGroupEffect> effects = new ArrayList<>(cap.getActiveEffects());
            for (ActiveGroupEffect effect : effects) {
                RewardHandler.removeEffect(player, effect);
            }
            cap.clearHistory();
            cap.clearMonotony();
            cap.clearNutrients();
            cap.clearActiveEffects();
            cap.clearPermanentRewards();
            com.lumbra.ldiediet.rewards.NutritionAttributeHandler.refresh(player, cap);
            com.lumbra.ldiediet.rewards.NutritionRuleHandler.refresh(player,cap);
            com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(player, cap);
            CapabilityHandler.syncToClient(player);
            CapabilityHandler.syncResourcesToClient(player);
            source.sendSuccess(() -> Component.translatable("ldiediet.command.reset",player.getDisplayName()), true);
        }
        return 1;
    }

    private static int clearHistory(CommandSourceStack source, ServerPlayer player) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
            cap.clearHistory();
            CapabilityHandler.syncToClient(player);
            source.sendSuccess(() -> Component.translatable("ldiediet.command.history",player.getDisplayName()), true);
        }
        return 1;
    }


    private static int calcAllCCI(CommandSourceStack source) {
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.translatable("ldiediet.command.no_server"));
            return 0;
        }
        if(!reloadBeforeWrite(source)) return 0;
        int count = com.lumbra.ldiediet.data.DietConfigManager.recalculateAllCCI(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) CapabilityHandler.syncResourcesToClient(player);
        source.sendSuccess(() -> Component.translatable("ldiediet.command.cci",count), true);
        return 1;
    }

    private static int updateConfig(CommandSourceStack source) {
        net.minecraft.server.MinecraftServer server = source.getServer();
        if(!reloadBeforeWrite(source)) return 0;
        int count = com.lumbra.ldiediet.data.DietConfigManager.updateConfig(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) CapabilityHandler.syncResourcesToClient(player);
        source.sendSuccess(() -> Component.translatable("ldiediet.command.updated",count), true);
        return 1;
    }

    private static int cleanConfigCommand(CommandSourceStack source) {
        if(!reloadBeforeWrite(source)) return 0;
        int removed = com.lumbra.ldiediet.data.DietConfigManager.cleanConfig();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) CapabilityHandler.syncResourcesToClient(player);
        source.sendSuccess(() -> Component.translatable("ldiediet.command.cleaned",removed), true);
        return 1;
    }

    private static boolean reloadBeforeWrite(CommandSourceStack source) {
        if(DietConfigManager.reloadConfig(source.getServer())) return true;
        source.sendFailure(Component.translatable("ldiediet.command.config_invalid"));return false;
    }
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }
}
