package com.lumbra.ldiediet.compat;

import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.data.NutritionScaleManager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Map;

public final class DietLegacyState {
    private DietLegacyState() {}

     
    public static void migrate(Player player, IFoodTolerance cap) {
        if (player.level().isClientSide) return;
        removeLegacyModifiers(player);
        if (!ModList.get().isLoaded("diet")) {
            if (!cap.hasImportedDietState() && !DietCompatibilityData.INSTANCE.groups().isEmpty()) {
                DietCompatibilityData.INSTANCE.groups().forEach((name, group) -> {
                    String nutrient = DietCompatibilityData.INSTANCE.nutrientId(name);
                    if (cap.getAllNutrients().containsKey(nutrient)) return;
                    var scale = NutritionScaleManager.getInstance().get(nutrient);
                    try {
                        double value = DietSuiteRule.number(group, "default_value", 0);
                        cap.setNutrientValue(nutrient, (float) (value * (scale == null ? 100 : scale.maxValue())));
                    } catch (RuntimeException ignored) { }
                });
                cap.markImportedDietState();
            }
            return;
        }
        try {
            Class<?> capability = Class.forName("com.illusivesoulworks.diet.common.capability.DietCapability");
            LazyOptional<?> optional = (LazyOptional<?>) capability.getMethod("get", Player.class).invoke(null, player);
            Object tracker = optional.resolve().orElse(null);
            if (tracker == null) return;
            Class<?> type = tracker.getClass();
            if (!cap.hasImportedDietState()) {
                cap.setDietSuite((String) type.getMethod("getSuite").invoke(tracker));
                Map<?, ?> values = (Map<?, ?>) type.getMethod("getValues").invoke(tracker);
                for (var entry : values.entrySet()) {
                    String nutrient = DietCompatibilityData.INSTANCE.nutrientId(String.valueOf(entry.getKey()));
                    if (!cap.getAllNutrients().containsKey(nutrient) && entry.getValue() instanceof Number number) {
                        var scale = NutritionScaleManager.getInstance().get(nutrient);
                        cap.setNutrientValue(nutrient, number.floatValue() * (scale == null ? 100 : scale.maxValue()));
                    }
                }
                cap.markImportedDietState();
            }
             
            Map<?, ?> modifiers = (Map<?, ?>) type.getMethod("getModifiers").invoke(tracker);
            for (var entry : modifiers.entrySet()) {
                if (entry.getKey() instanceof net.minecraft.world.entity.ai.attributes.Attribute attribute
                        && entry.getValue() instanceof Iterable<?> ids) {
                    var instance = player.getAttribute(attribute);
                    if (instance != null) for (Object id : ids) if (id instanceof java.util.UUID uuid) instance.removeModifier(uuid);
                }
            }
            type.getMethod("setModifiers", Map.class).invoke(tracker, Map.of());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot migrate state from supported Diet version", e);
        }
    }

    private static void removeLegacyModifiers(Player player) {
        for (var attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            var instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (var modifier : java.util.List.copyOf(instance.getModifiers())) {
                if (modifier.getName().equals("Diet group effect")) instance.removeModifier(modifier.getId());
            }
        }
    }
}
