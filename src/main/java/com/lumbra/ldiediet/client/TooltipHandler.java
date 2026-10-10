package com.lumbra.ldiediet.client;

import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.consumption.ConsumptionSnapshot;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.math.FormulaManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.Map;

public class TooltipHandler {
 @SubscribeEvent public void onItemTooltip(ItemTooltipEvent event) {
  var player=Minecraft.getInstance().player;var stack=event.getItemStack();
  if(player==null||(!stack.is(Items.CAKE)&&!ConsumptionSnapshot.isConsumable(stack,player)))return;
  var cap=CapabilityHandler.get(player);if(cap==null)return;
  var portion=stack.is(Items.CAKE)?ConsumptionSnapshot.capture(player,stack,new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationMod(.1f).build()):ConsumptionSnapshot.capture(player,stack);
  if(portion.excluded())return;
  event.getToolTip().add(Component.translatable("ldiediet.tooltip.stage_label",portion.stage().getLevel()).withStyle(switch(portion.stage().getLevel()){case 0->ChatFormatting.GREEN;case 1->ChatFormatting.YELLOW;case 2->ChatFormatting.GOLD;default->ChatFormatting.RED;}));
  if(portion.foodValue()>0&&portion.nutrientMultiplier()>0)for(var nutrient:portion.nutrients().entrySet()){
   double multiplier=1;
   for(var effect:cap.getActiveEffects())if(effect.isCurrentlyActive()&&effect.isNutrientMultiplier()&&("all".equals(effect.getEffectId())||"*".equals(effect.getEffectId())||nutrient.getKey().equals(effect.getEffectId())))multiplier*=Math.max(0,1+effect.getValue());
   double gain=FormulaManager.clientNutrientGain(Map.of("base_n",portion.foodValue()*nutrient.getValue()*multiplier,"stage_multiplier",portion.nutrientMultiplier(),"M",(double)portion.monotonyBefore(),"monotony",(double)portion.monotonyBefore()));
   if(!Double.isFinite(gain)||gain==0||Math.abs(gain)>Float.MAX_VALUE)continue;
   var definition=NutritionScaleManager.getClientInstance().get(nutrient.getKey());
   event.getToolTip().add(Component.translatable("ldiediet.journal.nutrient_gain",definition==null?Component.literal(nutrient.getKey()):definition.displayName(),(gain>0?"+":"")+JournalSnapshot.number(gain)).withStyle(ChatFormatting.BLUE));
  }
  for(var group:FoodGroupManager.getClientInstance().getGroupsForItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())){
   if(!group.isKnown(cap))continue;
   var color=net.minecraft.network.chat.TextColor.parseColor(group.getTooltipIconColor()==null?"aqua":group.getTooltipIconColor());
   var line=Component.translatable("ldiediet.tooltip.group",group.getTooltipIcon()==null?"●":group.getTooltipIcon(),group.displayName());
   event.getToolTip().add(line.withStyle(style->color==null?style.withColor(ChatFormatting.AQUA):style.withColor(color)));
  }
 }
}
