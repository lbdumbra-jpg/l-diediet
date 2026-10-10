package com.lumbra.ldiediet.rewards;

import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.data.NutritionRuleManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class NutritionRuleHandler {
    private static final String PREFIX="ldiediet_quota:";
    private NutritionRuleHandler() {}
    private record Desired(Attribute attribute,AttributeModifier modifier) {}
    public static void refresh(Player player,IFoodTolerance cap) {
        if(player.level().isClientSide)return;
        Map<UUID,Desired> wanted=new HashMap<>();Set<String> sources=new HashSet<>();Set<String> quotas=new HashSet<>();
        for(var rule:NutritionRuleManager.INSTANCE.getAll()) {
            String quota="rule:"+rule.id();quotas.add(quota);
            int raw=rule.multiplier(cap);
            if(rule.worldConditions()!=null&&!rule.worldConditions().evaluate(player))raw=0;
            int multiplier=com.lumbra.ldiediet.api.DietEvents.quota(player,quota,raw);if(multiplier==0)continue;
            int index=0;
            for(var spec:rule.attributes()) {
                String source=PREFIX+rule.id()+":"+index++;
                var attribute=ForgeRegistries.ATTRIBUTES.getValue(spec.attribute());
                double amount=spec.amount()*multiplier;
                if(attribute==null || player.getAttribute(attribute)==null || !Double.isFinite(amount))continue;
                UUID id=UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
                wanted.put(id,new Desired(attribute,new AttributeModifier(id,source,amount,spec.operation())));
            }
            index=0;
            for(var spec:rule.effects()) {
                var effect=ForgeRegistries.MOB_EFFECTS.getValue(spec.effect());if(effect==null)continue;
                String source=PREFIX+rule.id()+":effect:"+index++;sources.add(source);
                cap.getMobEffectSources().put(player,source,new MobEffectInstance(effect,60,spec.amplifier(),false,false,true));
            }
            if(!rule.advancement().isBlank() && player instanceof ServerPlayer serverPlayer) {
                ResourceLocation id=ResourceLocation.tryParse(rule.advancement());
                var advancement=id==null?null:serverPlayer.server.getAdvancements().getAdvancement(id);
                if(advancement!=null) for(String criterion:serverPlayer.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria())
                    serverPlayer.getAdvancements().award(advancement,criterion);
            }
        }
        com.lumbra.ldiediet.api.DietEvents.clearQuotaPrefix(player,"rule:",quotas);
        for(var attribute:ForgeRegistries.ATTRIBUTES.getValues()) {
            var instance=player.getAttribute(attribute);if(instance==null)continue;
            for(var existing:List.copyOf(instance.getModifiers())) {
                if(!existing.getName().startsWith(PREFIX))continue;
                var next=wanted.get(existing.getId());
                if(next==null || next.attribute()!=attribute || existing.getAmount()!=next.modifier().getAmount()
                        || existing.getOperation()!=next.modifier().getOperation())instance.removeModifier(existing.getId());
            }
        }
        wanted.forEach((id,d)->{var instance=player.getAttribute(d.attribute());if(instance.getModifier(id)==null)instance.addTransientModifier(d.modifier());});
        cap.getMobEffectSources().removePrefixExcept(PREFIX,sources);
        cap.getMobEffectSources().reconcile(player);
        if(player.getHealth()>player.getMaxHealth())player.setHealth(player.getMaxHealth());
        com.lumbra.ldiediet.api.DietEvents.commitQuotas(player,"rule:");
    }
}
