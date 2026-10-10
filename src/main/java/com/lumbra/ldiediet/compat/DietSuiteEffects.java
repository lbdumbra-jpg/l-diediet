package com.lumbra.ldiediet.compat;

import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.data.NutritionScaleManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;
import java.util.*;

 
public final class DietSuiteEffects {
    private static final String PREFIX = "ldiediet_diet:";
    private DietSuiteEffects() {}

    public static void refresh(Player player, IFoodTolerance cap) {
        if (player.level().isClientSide) return;
        for (var attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            var instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (var modifier : List.copyOf(instance.getModifiers())) {
                if (modifier.getName().startsWith(PREFIX)) instance.removeModifier(modifier.getId());
            }
        }
        String suite = cap.getDietSuite();
        Set<String> members = DietCompatibilityData.INSTANCE.suiteGroups(suite);
        Map<String, Double> values = new HashMap<>();
        for (String group : members) {
            String nutrient = DietCompatibilityData.INSTANCE.nutrientId(group);
            var scale = NutritionScaleManager.getInstance().get(nutrient);
            values.put(group, cap.getNutrientValue(nutrient) / (double) (scale == null ? 100 : scale.maxValue()));
        }
        int index = 0;
        Set<String> sources = new HashSet<>();Set<String> quotas=new HashSet<>();
        for (DietSuiteRule rule : DietCompatibilityData.INSTANCE.rules(suite)) {
            int multiplier = rule.multiplier(values);
            int ruleIndex = index++;
            String quota="diet:"+suite+":"+ruleIndex;quotas.add(quota);
            multiplier=com.lumbra.ldiediet.api.DietEvents.quota(player,quota,multiplier);
            if (multiplier == 0) continue;
            int attributeIndex = 0;
            for (var spec : rule.attributes()) {
                ResourceLocation id = ResourceLocation.tryParse(spec.name());
                var attribute = id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
                var instance = attribute == null ? null : player.getAttribute(attribute);
                String key = suite + ":" + ruleIndex + ":" + attributeIndex++;
                if (instance == null) continue;
                double amount = spec.amount() + (multiplier - 1) * spec.increment();
                if (!Double.isFinite(amount)) continue;
                AttributeModifier.Operation operation = switch (spec.operation()) {
                    case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
                    case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
                    default -> AttributeModifier.Operation.ADDITION;
                };
                UUID uuid = UUID.nameUUIDFromBytes((PREFIX + key).getBytes(StandardCharsets.UTF_8));
                instance.addTransientModifier(new AttributeModifier(uuid, PREFIX + key, amount, operation));
            }
            int statusIndex = 0;
            for (var spec : rule.statuses()) {
                String source = "diet_suite:" + suite + ":" + ruleIndex + ":" + statusIndex++;
                ResourceLocation id = ResourceLocation.tryParse(spec.name());
                var effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
                if (effect == null) continue;
                long power = (long) spec.power() + (long) (multiplier - 1) * spec.increment();
                 
                int duration = effect == MobEffects.NIGHT_VISION || effect == MobEffects.CONFUSION ? 300 : 100;
                sources.add(source);
                cap.getMobEffectSources().put(player, source,
                        new MobEffectInstance(effect, duration, (int) Math.max(0, Math.min(255, power)), true, false));
            }
        }
        com.lumbra.ldiediet.api.DietEvents.clearQuotaPrefix(player,"diet:",quotas);
        cap.getMobEffectSources().removePrefixExcept("diet_suite:", sources);
        cap.getMobEffectSources().reconcile(player);
        com.lumbra.ldiediet.api.DietEvents.commitQuotas(player,"diet:");
    }
}
