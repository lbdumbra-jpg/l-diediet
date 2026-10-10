package com.lumbra.ldiediet.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class ExclusionManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ExclusionManager instance;
    private static final ExclusionManager CLIENT = new ExclusionManager();

    private final Map<Item, Integer> stageOverrides = new HashMap<>();

    public ExclusionManager() {
        super(GSON, "exclusions"); 
     
    }

    public static ExclusionManager getInstance() {
        if (instance == null) {
            instance = new ExclusionManager();
        }
        return instance;
    }
    public static ExclusionManager getClientInstance() { return CLIENT; }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectIn, ResourceManager resourceManagerIn, ProfilerFiller profilerIn) {
        stageOverrides.clear();
        
        objectIn.forEach((location, element) -> {
            try {
                if (element.isJsonObject()) {
                    JsonObject jsonObject = element.getAsJsonObject();
                    
                    if (jsonObject.has("overrides")) {
                        JsonObject overrides = jsonObject.getAsJsonObject("overrides");
                        overrides.entrySet().forEach(entry -> {
                            try {
                            String itemName = entry.getKey();
                            int stage = entry.getValue().getAsInt();
                            if (stage < 0 || stage > 4) throw new IllegalArgumentException("Stage must be 0..4");
                            
                            ResourceLocation itemLoc = new ResourceLocation(itemName);
                            Item item = ForgeRegistries.ITEMS.getValue(itemLoc);
                            
                            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                                stageOverrides.put(item, stage);
                            }
                            } catch (RuntimeException e) { LDieDiet.LOGGER.warn("Ignoring exclusion override {} in {}", entry.getKey(), location, e); }
                        });
                    }
                }
            } catch (Exception e) {
                LDieDiet.LOGGER.warn("Ignoring malformed exclusion resource {}", location, e);
            }
        });
        

    }

    
    public Map<Item, Integer> getStageOverrides() {
        return Collections.unmodifiableMap(stageOverrides);
    }

   
    public void setStageOverrides(Map<Item, Integer> overrides) {
        stageOverrides.clear();
        stageOverrides.putAll(overrides);
    }

    public CompoundTag serializeToNBT() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<Item, Integer> entry : stageOverrides.entrySet()) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(entry.getKey());
            if (itemId != null) {
                tag.putInt(itemId.toString(), entry.getValue());
            }
        }
        return tag;
    }
    public void deserializeFromNBT(CompoundTag tag) {
        stageOverrides.clear();
        for (String key : tag.getAllKeys()) {
            ResourceLocation itemId = ResourceLocation.tryParse(key);
            if (itemId == null || !tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                    || tag.getInt(key) < 0 || tag.getInt(key) > 4) continue;
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                stageOverrides.put(item, tag.getInt(key));
            }
        }
    }
}
