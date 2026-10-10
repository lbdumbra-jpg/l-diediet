package com.lumbra.ldiediet.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.util.NbtMatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

 
final class FoodProfile {
    static final FoodProfile DEFAULT = new FoodProfile(null, null, null, false, Map.of(), List.of());

    private final Double cci;
    private final Double baseMonotony;
    private final Double foodValue;
    private final boolean exclusion;
    private final Map<String, Double> nutrients;
    private final List<Variant> variants;

    private FoodProfile(Double cci, Double baseMonotony, Double foodValue, boolean exclusion,
                        Map<String, Double> nutrients, List<Variant> variants) {
        this.cci = cci;
        this.baseMonotony = baseMonotony;
        this.foodValue = foodValue;
        this.exclusion = exclusion;
        this.nutrients = Collections.unmodifiableMap(new LinkedHashMap<>(nutrients));
        this.variants = List.copyOf(variants);
    }

    static FoodProfile parse(JsonObject json, String itemId) {
        Double cci = finiteDouble(json, "cci");
        Double baseMonotony = finiteDouble(json, "base_monotony");
        Double foodValue = finiteDouble(json, "food_value");
        if (foodValue != null) foodValue = Math.max(0, foodValue);
        boolean exclusion = json.has("is_exclusion") && json.get("is_exclusion").getAsBoolean();
        Map<String, Double> nutrients = parseNutrients(json.get("nutrients"));
        List<Variant> variants = new ArrayList<>();
        if (json.has("nbt_filters") && json.get("nbt_filters").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("nbt_filters");
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonObject()) continue;
                JsonObject rule = element.getAsJsonObject();
                try {
                    String match = rule.has("match") ? rule.get("match").getAsString()
                            : rule.has("nbt") ? rule.get("nbt").getAsString() : "{}";
                    CompoundTag condition = TagParser.parseTag(match);
                    String variantId = rule.has("variant_id") ? rule.get("variant_id").getAsString() : "";
                    if (variantId.isBlank()) variantId = com.lumbra.ldiediet.util.NbtVariantIds.fromMatch(condition);
                    if (variantId.length() > 256) throw new IllegalArgumentException("variant_id exceeds 256 characters");
                    variants.add(new Variant(
                            variantId, condition,
                            finiteDouble(rule, "cci"),
                            finiteDouble(rule, "base_monotony"),
                            finiteDouble(rule, "food_value"),
                            rule.has("is_exclusion") ? rule.get("is_exclusion").getAsBoolean() : null,
                            parseNutrients(rule.get("nutrients"))));
                } catch (Exception e) {
                    LDieDiet.LOGGER.error("Invalid nbt_filters[{}] for {}", i, itemId, e);
                }
            }
        }
        return new FoodProfile(cci, baseMonotony, foodValue, exclusion, nutrients, variants);
    }

    Double baseCci() { return cci; }

    Resolved resolve(ItemStack stack, double fallbackCci) {
        double resolvedCci = cci != null ? cci : fallbackCci;
        double resolvedBase = baseMonotony != null ? baseMonotony : 10.0;
        Double resolvedFoodValue = foodValue;
        boolean resolvedExclusion = exclusion;
        Map<String, Double> resolvedNutrients = new LinkedHashMap<>(nutrients);
        String variantId = "";
        for (Variant variant : variants) {
            if (!NbtMatcher.matches(stack.getTag(), variant.match())) continue;
            variantId = variant.variantId();
            if (variant.cci() != null) resolvedCci = variant.cci();
            if (variant.baseMonotony() != null) resolvedBase = variant.baseMonotony();
            if (variant.foodValue() != null) resolvedFoodValue = Math.max(0, variant.foodValue());
            if (variant.exclusion() != null) resolvedExclusion = variant.exclusion();
            resolvedNutrients.putAll(variant.nutrients());
            break;
        }
        return new Resolved(variantId, resolvedCci, resolvedBase, resolvedFoodValue, resolvedExclusion,
                Collections.unmodifiableMap(resolvedNutrients));
    }

    java.util.Set<String> nutrientKeys() {
        var keys=new java.util.HashSet<>(nutrients.keySet());for(var v:variants)keys.addAll(v.nutrients().keySet());return keys;
    }
    ItemStack representative(FoodKey key) {
        var stack=new ItemStack(key.item());
        if(!key.variantId().isEmpty())for(var v:variants)if(v.variantId().equals(key.variantId())){stack.setTag(v.match().copy());break;}
        return stack;
    }
    double cciForVariant(String variantId, double fallbackCci) {
        double base = cci != null ? cci : fallbackCci;
        if (variantId.isEmpty()) return base;
        for (Variant variant : variants) {
            if (variant.variantId().equals(variantId)) return variant.cci() != null ? variant.cci() : base;
        }
        return base;
    }

    private static Map<String, Double> parseNutrients(JsonElement element) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) return result;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            try {
                double value = entry.getValue().getAsDouble();
                if (Double.isFinite(value) && value != 0.0) result.put(entry.getKey(), value);
            } catch (RuntimeException ignored) {
                LDieDiet.LOGGER.warn("Ignoring invalid nutrient value for '{}'", entry.getKey());
            }
        }
        return result;
    }

    private static Double finiteDouble(JsonObject json, String key) {
        if (!json.has(key)) return null;
        try {
            double value = json.get(key).getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    record Resolved(String variantId, double cci, double baseMonotony, Double foodValue, boolean exclusion, Map<String, Double> nutrients) {}
    private record Variant(String variantId, CompoundTag match, Double cci, Double baseMonotony,
                           Double foodValue, Boolean exclusion, Map<String, Double> nutrients) {}
}
