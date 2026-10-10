package com.lumbra.ldiediet.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FoodProfileManager extends SimpleJsonResourceReloadListener {
    public static final FoodProfileManager INSTANCE = new FoodProfileManager();
    private Map<Item, JsonObject> profiles = Map.of();

    private FoodProfileManager() { super(new Gson(), "food_profiles"); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        AuthorDiagnostics.clearParseIssues("food_profiles/");
        Map<Item, JsonObject> next = new LinkedHashMap<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = entry.getKey();
            if (!ForgeRegistries.ITEMS.containsKey(id)) return;
            try {
                if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Root must be an object");
                JsonObject json = entry.getValue().getAsJsonObject();
                FoodProfile.parse(json, id.toString());
                next.put(ForgeRegistries.ITEMS.getValue(id), json.deepCopy());
            } catch (RuntimeException error) {
                AuthorDiagnostics.parseIssue("food_profiles/" + id, error.getMessage());
                LDieDiet.LOGGER.error("Cannot read datapack food profile {}", id, error);
            }
        });
        profiles = Map.copyOf(next);
        LDieDiet.LOGGER.info("Loaded {} datapack food profiles", profiles.size());
    }

    public void refresh() { DietConfigManager.setDatapackFoods(profiles); }
}
