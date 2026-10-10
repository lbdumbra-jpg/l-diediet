package com.lumbra.ldiediet.command;

import com.lumbra.ldiediet.api.PackApi;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.data.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.function.IntSupplier;

 
public final class AuthorCommands {
    private AuthorCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ldiediet").requires(s->s.hasPermission(2))
            .then(Commands.literal("reload").executes(c->{
                var source=c.getSource();
                source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds()).whenComplete((result,error)->
                    source.getServer().execute(()->{
                        if(error!=null)source.sendFailure(Component.translatable("ldiediet.command.reload_failed",String.valueOf(error.getMessage())));
                        else if(!DietConfigManager.canWriteConfiguration())source.sendFailure(Component.translatable("ldiediet.command.config_invalid"));
                        else source.sendSuccess(()->Component.translatable("ldiediet.command.reloaded"),true);
                    }));return 1;
            }))
            .then(Commands.literal("validate").executes(c->{
                var issues=AuthorDiagnostics.validate(c.getSource().getServer());
                c.getSource().sendSuccess(()->Component.translatable("ldiediet.command.validation",issues.size()),false);
                issues.stream().limit(40).forEach(s->c.getSource().sendSuccess(()->Component.literal(s),false));
                if(issues.size()>40)c.getSource().sendSuccess(()->Component.translatable("ldiediet.command.validation_more"),false);
                AuthorDiagnostics.logValidation(c.getSource().getServer());return issues.isEmpty()?1:0;
            }))
            .then(Commands.literal("nutrition").then(Commands.argument("player",EntityArgument.player())
                .then(Commands.argument("nutrient",ResourceLocationArgument.id())
                    .suggests((c,b)->SharedSuggestionProvider.suggest(NutritionScaleManager.getInstance().getAll().stream().map(NutritionScaleDefinition::nutrientKey),b))
                    .executes(c->{var p=EntityArgument.getPlayer(c,"player");String n=ResourceLocationArgument.getId(c,"nutrient").toString();return safe(c.getSource(),()->{
                        double value=PackApi.getNutrient(p,n);c.getSource().sendSuccess(()->Component.translatable("ldiediet.command.nutrient",n,p.getDisplayName(),value),false);return 1;});})
                    .then(Commands.argument("value",DoubleArgumentType.doubleArg(0)).executes(c->{var p=EntityArgument.getPlayer(c,"player");String n=ResourceLocationArgument.getId(c,"nutrient").toString();
                        return safe(c.getSource(),()->{PackApi.setNutrient(p,n,DoubleArgumentType.getDouble(c,"value"));c.getSource().sendSuccess(()->Component.translatable("ldiediet.command.nutrient",n,p.getDisplayName(),PackApi.getNutrient(p,n)),true);return 1;});})))))
            .then(Commands.literal("suite").then(Commands.argument("player",EntityArgument.player())
                .then(Commands.argument("suite",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE.suiteNames(),b))
                    .executes(c->{var p=EntityArgument.getPlayer(c,"player");String suite=StringArgumentType.getString(c,"suite");return safe(c.getSource(),()->{
                        PackApi.setSuite(p,suite);c.getSource().sendSuccess(()->Component.translatable("ldiediet.command.suite",p.getDisplayName(),suite),true);return 1;});}))))
            .then(Commands.literal("group").then(Commands.argument("player",EntityArgument.player())
                .then(Commands.argument("group",ResourceLocationArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggest(FoodGroupManager.getInstance().getAllGroups().stream().map(g->g.getId().toString()),b))
                    .executes(c->{var p=EntityArgument.getPlayer(c,"player");String id=ResourceLocationArgument.getId(c,"group").toString();return safe(c.getSource(),()->{
                        var group=FoodGroupManager.getInstance().getGroup(ResourceLocation.tryParse(com.lumbra.ldiediet.util.GroupIds.canonical(id)));
                        if(group==null)throw new IllegalArgumentException("Unknown group: "+id);
                        for(String line:AuthorDiagnostics.explain(p,CapabilityHandler.get(p),group))c.getSource().sendSuccess(()->Component.literal(line),false);return 1;});})
                    .then(Commands.literal("grant").executes(c->{var p=EntityArgument.getPlayer(c,"player");String id=ResourceLocationArgument.getId(c,"group").toString();return safe(c.getSource(),()->{
                        PackApi.grantGroup(p,id);c.getSource().sendSuccess(()->Component.translatable("ldiediet.command.granted",id,p.getDisplayName()),true);return 1;});})))))
            .then(Commands.literal("rule").then(Commands.argument("player",EntityArgument.player())
                .then(Commands.argument("rule",ResourceLocationArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggest(NutritionRuleManager.INSTANCE.getAll().stream().map(r->r.id().toString()),b))
                    .executes(c->{var p=EntityArgument.getPlayer(c,"player");var id=ResourceLocation.tryParse(ResourceLocationArgument.getId(c,"rule").toString());return safe(c.getSource(),()->{
                        var rule=NutritionRuleManager.INSTANCE.get(id);if(rule==null)throw new IllegalArgumentException("Unknown nutrition rule: "+id);
                        var cap=CapabilityHandler.get(p);
                        c.getSource().sendSuccess(()->Component.translatable("ldiediet.command.rule",rule.displayName(),rule.multiplier(cap)),false);
                        for(var condition:rule.conditions())c.getSource().sendSuccess(()->Component.literal(condition.toString()+" -> "+condition.matches(cap)),false);
                        return 1;});}))))
        );
    }
    private static int safe(CommandSourceStack source,IntSupplier action) {
        try{return action.getAsInt();}catch(IllegalArgumentException|IllegalStateException e){source.sendFailure(Component.translatable("ldiediet.command.error",e.getMessage()));return 0;}
    }
}
