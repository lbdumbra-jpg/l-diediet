package com.levbu.ldiediet.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.levbu.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;


public class ExclusionManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ExclusionManager instance;

    private final Map<Item, Integer> stageOverrides = new HashMap<>();

    public ExclusionManager() {
        super(GSON, "exclusions"); 
     
        instance = this;
    }

    public static ExclusionManager getInstance() {
        if (instance == null) {
            instance = new ExclusionManager();
        }
        return instance;
    }

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
                            String itemName = entry.getKey();
                            int stage = entry.getValue().getAsInt();
                            
                            ResourceLocation itemLoc = new ResourceLocation(itemName);
                            Item item = ForgeRegistries.ITEMS.getValue(itemLoc);
                            
                            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                                stageOverrides.put(item, stage);
                            }
                        });
                    }
                }
            } catch (Exception e) {
            }
        });
        

    }

    
    public int getStageOverride(Item item) {
        return stageOverrides.getOrDefault(item, 0);
    }
    
   
    public Map<Item, Integer> getStageOverrides() {
        return stageOverrides;
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
            ResourceLocation itemId = new ResourceLocation(key);
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                stageOverrides.put(item, tag.getInt(key));
            }
        }
    }
}
