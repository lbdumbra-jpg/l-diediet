package com.lumbra.ldiediet.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

public record NutritionScaleDefinition(ResourceLocation id, String name, float maxValue,
                                       List<Threshold> thresholds, String nameKey, String icon, Integer color,
                                       int order, Double decayPercentPerMinute) {
    public NutritionScaleDefinition(ResourceLocation id,String name,float maxValue,List<Threshold> thresholds) {
        this(id,name,maxValue,thresholds,"","",null,0,null);
    }
    public String nutrientKey() { return id.getNamespace().equals("ldiediet") ? id.getPath() : id.toString(); }
    public net.minecraft.network.chat.Component displayName() {
        String key = nameKey;
        if (key.isEmpty() && (id.getNamespace().equals("ldiediet") || id.getNamespace().equals("diet"))
                && name.equalsIgnoreCase(id.getPath())) key = "ldiediet.nutrition."+id.getPath();
        return com.lumbra.ldiediet.util.PackText.of(key,name);
    }
    public static NutritionScaleDefinition parse(ResourceLocation id, JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : id.getPath();
        float maxValue = json.has("max_value") ? json.get("max_value").getAsFloat() : 100.0f;
        if (!Float.isFinite(maxValue) || maxValue <= 0) throw new IllegalArgumentException("Nutrition max_value must be finite and positive");
        List<Threshold> thresholds = new ArrayList<>();
        if (json.has("thresholds") && json.get("thresholds").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("thresholds")) {
                try {
                if (!element.isJsonObject()) continue;
                JsonObject threshold = element.getAsJsonObject();
                float min = threshold.has("min") ? threshold.get("min").getAsFloat() : 0.0f;
                float max = threshold.has("max") ? threshold.get("max").getAsFloat() : maxValue;
                if (!Float.isFinite(min) || !Float.isFinite(max)) throw new IllegalArgumentException("Non-finite nutrition threshold");
                List<ModifierSpec> modifiers = new ArrayList<>();
                JsonArray modifierArray = threshold.has("modifiers") && threshold.get("modifiers").isJsonArray()
                        ? threshold.getAsJsonArray("modifiers") : new JsonArray();
                for (JsonElement modifierElement : modifierArray) {
                    try {
                    if (!modifierElement.isJsonObject()) continue;
                    JsonObject modifier = modifierElement.getAsJsonObject();
                    ResourceLocation attribute = ResourceLocation.tryParse(
                            modifier.has("attribute") ? modifier.get("attribute").getAsString() : "");
                    if (attribute == null) continue;
                    double amount = modifier.has("amount") ? modifier.get("amount").getAsDouble() : 0.0;
                    if (!Double.isFinite(amount)) throw new IllegalArgumentException("Non-finite attribute amount");
                    String operationName = modifier.has("operation")
                            ? modifier.get("operation").getAsString() : "addition";
                    modifiers.add(new ModifierSpec(attribute, amount, parseOperation(operationName)));
                    } catch (RuntimeException e) { AuthorDiagnostics.parseIssue("nutrition_scales/"+id,"modifiers: "+e.getMessage()); com.lumbra.ldiediet.LDieDiet.LOGGER.warn("Ignoring malformed nutrition modifier in {}", id, e); }
                }
                if (min > max) throw new IllegalArgumentException("min exceeds max");
                thresholds.add(new Threshold(min,max,List.copyOf(modifiers),
                        !threshold.has("min_inclusive") || threshold.get("min_inclusive").getAsBoolean(),
                        !threshold.has("max_inclusive") || threshold.get("max_inclusive").getAsBoolean()));
                } catch (RuntimeException e) { AuthorDiagnostics.parseIssue("nutrition_scales/"+id,"thresholds: "+e.getMessage()); com.lumbra.ldiediet.LDieDiet.LOGGER.warn("Ignoring malformed nutrition threshold in {}", id, e); }
            }
        }
        if (json.has("decay_multiplier")) {
            double multiplier = json.get("decay_multiplier").getAsDouble();
            if (!Double.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("Invalid decay multiplier");
        }
        Double decay = json.has("decay_percent_per_minute") ? json.get("decay_percent_per_minute").getAsDouble() : null;
        if (decay != null && (!Double.isFinite(decay) || decay < 0 || decay > 100)) throw new IllegalArgumentException("Invalid decay rate");
        return new NutritionScaleDefinition(id,name,maxValue,List.copyOf(thresholds),
                string(json,"name_key",""),string(json,"icon",""),json.has("color") ? parseColor(json.get("color").getAsString()) : null,
                json.has("order") ? json.get("order").getAsInt() : 0,decay);
    }

    private static String string(JsonObject json,String key,String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
    private static int parseColor(String value) {
        if (!value.matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Color must be #RRGGBB");
        return Integer.parseInt(value.replace("#",""),16);
    }
    public static AttributeModifier.Operation parseOperation(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "multiply_base", "multiply_base_value" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total", "multiply_total_value" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            case "addition", "add" -> AttributeModifier.Operation.ADDITION;
            default -> throw new IllegalArgumentException("Unknown attribute operation: "+value);
        };
    }

    public record Threshold(float min, float max, List<ModifierSpec> modifiers, boolean minInclusive, boolean maxInclusive) {
        public Threshold(float min,float max,List<ModifierSpec> modifiers) { this(min,max,modifiers,true,true); }
        public boolean includes(float value) { return (minInclusive ? value>=min : value>min) && (maxInclusive ? value<=max : value<max); }
    }

    public record ModifierSpec(ResourceLocation attribute, double amount,
                               AttributeModifier.Operation operation) {}
}
