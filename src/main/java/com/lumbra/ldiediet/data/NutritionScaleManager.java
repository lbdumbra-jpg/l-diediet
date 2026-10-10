package com.lumbra.ldiediet.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NutritionScaleManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final NutritionScaleManager INSTANCE = new NutritionScaleManager();
    private static final NutritionScaleManager CLIENT = new NutritionScaleManager();
    private final Map<ResourceLocation, NutritionScaleDefinition> definitions = new LinkedHashMap<>();
    private final Map<ResourceLocation, JsonElement> rawDefinitions = new LinkedHashMap<>();
    private final Map<ResourceLocation, NutritionScaleDefinition> importedDefinitions = new LinkedHashMap<>();

    private NutritionScaleManager() {
        super(GSON, "nutrition_scales");
    }

    public static NutritionScaleManager getInstance() { return INSTANCE; }
    public static NutritionScaleManager getClientInstance() { return CLIENT; }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        AuthorDiagnostics.clearParseIssues("nutrition_scales/");
        definitions.clear();
        importedDefinitions.clear();
        rawDefinitions.clear();
        resources.forEach((id, element) -> {
            try {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Root must be an object");
                definitions.put(id, NutritionScaleDefinition.parse(id, element.getAsJsonObject()));
                rawDefinitions.put(id, element.deepCopy());
            } catch (RuntimeException e) {
                AuthorDiagnostics.parseIssue("nutrition_scales/"+id,e.getMessage());
                LDieDiet.LOGGER.error("Failed to parse nutrition scale {}", id, e);
            }
        });
        LDieDiet.LOGGER.info("Loaded {} nutrition scales", definitions.size());
    }

    public NutritionScaleDefinition get(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null && definitions.containsKey(location)) return definitions.get(location);
        if (location != null && importedDefinitions.containsKey(location)) return importedDefinitions.get(location);
        ResourceLocation local = ResourceLocation.tryParse(LDieDiet.MOD_ID + ":" + id);
        return local == null ? null : definitions.get(local);
    }

    public Collection<NutritionScaleDefinition> getAll() {
        java.util.List<NutritionScaleDefinition> all = new java.util.ArrayList<>(definitions.values());
        importedDefinitions.forEach((id, definition) -> { if (!definitions.containsKey(id)) all.add(definition); });
        return java.util.List.copyOf(all);
    }

    public void setImportedScales(Map<String, com.google.gson.JsonObject> groups, Map<String, String> ids) {
        importedDefinitions.clear();
        groups.forEach((name, json) -> {
            ResourceLocation id = ResourceLocation.tryParse(ids.get(name));
            if (id != null && !definitions.containsKey(id) && ids.get(name).contains(":")) {
                importedDefinitions.put(id, new NutritionScaleDefinition(id, name, 100, java.util.List.of()));
            }
        });
    }

    public double decayMultiplier(NutritionScaleDefinition scale) {
        var json = rawDefinitions.get(scale.id());
        if (json != null && json.getAsJsonObject().has("decay_multiplier"))
            return json.getAsJsonObject().get("decay_multiplier").getAsDouble();
        return com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE.decayMultiplier(scale.nutrientKey());
    }

    public float clamp(String id, float value) {
        NutritionScaleDefinition definition = get(id);
        return Math.max(0.0f, definition == null ? value : Math.min(definition.maxValue(), value));
    }

    public CompoundTag serializeToNBT() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        rawDefinitions.forEach((id, json) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.put("definition", com.lumbra.ldiediet.util.JsonNbt.encode(json));
            list.add(entry);
        });
        root.put("definitions", list);
        importedDefinitions.forEach((id, definition) -> {
            if (definitions.containsKey(id)) return;
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("name", definition.name()); json.addProperty("max_value", definition.maxValue());
            entry.put("definition", com.lumbra.ldiediet.util.JsonNbt.encode(json));
            list.add(entry);
        });
        return root;
    }

    public void deserializeFromNBT(CompoundTag root) {
        definitions.clear();
        importedDefinitions.clear();
        rawDefinitions.clear();
        ListTag list = root.getList("definitions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null) continue;
            try {
                JsonElement json = entry.contains("definition") ? com.lumbra.ldiediet.util.JsonNbt.decode(entry.getCompound("definition"))
                        : JsonParser.parseString(entry.getString("json"));
                definitions.put(id, NutritionScaleDefinition.parse(id, json.getAsJsonObject()));
                rawDefinitions.put(id, json);
            } catch (RuntimeException e) {
                LDieDiet.LOGGER.error("Failed to deserialize nutrition scale {}", id, e);
            }
        }
    }
}
