package com.lumbra.ldiediet.rewards;

import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.data.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import java.nio.charset.StandardCharsets;
import java.util.*;

 
public final class NutritionAttributeHandler {
    private NutritionAttributeHandler() {}
    private record Desired(String name, NutritionScaleDefinition.ModifierSpec spec) {}
    public static void refresh(Player player, IFoodTolerance tolerance) {
        if (player.level().isClientSide) return;
        Map<Attribute, Map<UUID, Desired>> desired = new HashMap<>();
        Set<String> quotas=new HashSet<>();
        for (NutritionScaleDefinition scale : NutritionScaleManager.getInstance().getAll()) {
            String key = scale.id().getNamespace().equals("ldiediet") ? scale.id().getPath() : scale.id().toString();
            float value = tolerance.getNutrientValue(key);
            for (int i = 0; i < scale.thresholds().size(); i++) {
                var threshold = scale.thresholds().get(i);
                String quota="scale:"+scale.id()+":"+i;quotas.add(quota);
                int multiplier=com.lumbra.ldiediet.api.DietEvents.quota(player,quota,threshold.includes(value)?1:0);
                if(multiplier==0)continue;
                for (int j = 0; j < threshold.modifiers().size(); j++) {
                    var original=threshold.modifiers().get(j);
                    var spec = new NutritionScaleDefinition.ModifierSpec(original.attribute(),original.amount()*multiplier,original.operation());
                    Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(spec.attribute());
                    if (attribute != null && Double.isFinite(spec.amount())) desired.computeIfAbsent(attribute, ignored -> new HashMap<>())
                            .put(uuid(scale, i, j), new Desired("ldiediet_nutrient_" + scale.id(), spec));
                }
            }
        }
        com.lumbra.ldiediet.api.DietEvents.clearQuotaPrefix(player,"scale:",quotas);
        for (Attribute attribute : net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValues()) {
            var instance = player.getAttribute(attribute);
            if (instance == null) continue;
            Map<UUID, Desired> wanted = desired.getOrDefault(attribute, Map.of());
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                Desired next = wanted.get(modifier.getId());
                if (modifier.getName().startsWith("ldiediet_nutrient_") && (next == null
                        || next.spec.amount() != modifier.getAmount() || next.spec.operation() != modifier.getOperation())) {
                    instance.removeModifier(modifier.getId());
                }
            }
            wanted.forEach((id, next) -> {
                if (instance.getModifier(id) == null) instance.addTransientModifier(
                        new AttributeModifier(id, next.name, next.spec.amount(), next.spec.operation()));
            });
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        com.lumbra.ldiediet.api.DietEvents.commitQuotas(player,"scale:");
    }
    private static UUID uuid(NutritionScaleDefinition scale, int thresholdIndex, int modifierIndex) {
        return UUID.nameUUIDFromBytes((scale.id() + ":" + thresholdIndex + ":" + modifierIndex).getBytes(StandardCharsets.UTF_8));
    }
}
