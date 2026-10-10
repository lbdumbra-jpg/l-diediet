package com.lumbra.ldiediet.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Comparator;

 
public final class NutritionAttributeSummary {
    public static final String KEY = "nutrition_attribute_modifiers";
    private NutritionAttributeSummary() {}

    public static ListTag capture(Player player) {
        var result = new ListTag();
        ForgeRegistries.ATTRIBUTES.getValues().stream()
                .sorted(Comparator.comparing(a -> String.valueOf(ForgeRegistries.ATTRIBUTES.getKey(a))))
                .forEach(attribute -> {
                    var instance = player.getAttribute(attribute);
                    if (instance == null) return;
                    var owned = instance.getModifiers().stream().filter(modifier -> isOwned(modifier.getName())
                                    && Double.isFinite(modifier.getAmount()) && modifier.getAmount() != 0)
                            .sorted(Comparator.comparing(m -> m.getId().toString())).toList();
                    if (owned.isEmpty()) return;
                    var row = new CompoundTag();
                    row.putString("attribute", ForgeRegistries.ATTRIBUTES.getKey(attribute).toString());
                    row.putDouble("value", instance.getValue());
                    var modifiers = new ListTag();
                    for (AttributeModifier modifier : owned) {
                        var tag = new CompoundTag();
                        tag.putString("source",modifier.getName());
                        tag.putDouble("amount", modifier.getAmount());
                        tag.putInt("operation", modifier.getOperation().toValue());
                        modifiers.add(tag);
                    }
                    row.put("modifiers", modifiers); result.add(row);
                });
        return result;
    }

    private static boolean isOwned(String name) {
        return name.startsWith("ldiediet_quota:") || name.startsWith("ldiediet_nutrient_") || name.startsWith("ldiediet_diet:")
                || name.startsWith("ldiediet_reward_");
    }
}
