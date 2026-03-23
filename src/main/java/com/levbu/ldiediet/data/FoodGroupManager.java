package com.levbu.ldiediet.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.levbu.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;


public class FoodGroupManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FoodGroupManager instance;

    private final Map<ResourceLocation, FoodGroupDefinition> groups = new LinkedHashMap<>();

    private final Map<String, List<FoodGroupDefinition>> itemToGroups = new HashMap<>();

    public FoodGroupManager() {
        super(GSON, "food_groups");
        instance = this;
    }

    public static FoodGroupManager getInstance() {
        if (instance == null) {
            instance = new FoodGroupManager();
        }
        return instance;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectIn, ResourceManager resourceManagerIn, ProfilerFiller profilerIn) {
        groups.clear();
        itemToGroups.clear();

        objectIn.forEach((location, element) -> {
            try {
                if (element.isJsonObject()) {
                    FoodGroupDefinition group = parseGroup(location, element.getAsJsonObject());
                    if (group != null) {
                        groups.put(location, group);

                        for (String itemOrTag : group.getItems()) {
                            if (itemOrTag.startsWith("#")) {
                                try {
                                    ResourceLocation tagRL = new ResourceLocation(itemOrTag.substring(1));
                                    net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tagKey =
                                            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.BuiltInRegistries.ITEM.key(), tagRL);

                                    var tagContents = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tagKey);
                                    boolean tagFound = false;
                                    for (net.minecraft.core.Holder<net.minecraft.world.item.Item> holder : tagContents) {
                                        String idStr = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(holder.value()).toString();
                                        itemToGroups.computeIfAbsent(idStr, k -> new ArrayList<>()).add(group);
                                        tagFound = true;
                                    }
                                    if (!tagFound) {
                                    }
                                } catch (Exception e) {
                                }
                            } else {
                                try {
                                    ResourceLocation itemRL = new ResourceLocation(itemOrTag);
                                    if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemRL)) {
                                        itemToGroups.computeIfAbsent(itemOrTag, k -> new ArrayList<>()).add(group);
                                    } else {
                                    }
                                } catch (Exception e) {
                                }
                            }
                        }


                    }
                }
            } catch (Exception e) {
            }
        });


    }

    
    private FoodGroupDefinition parseGroup(ResourceLocation id, JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : id.getPath();
        String activeEffectText = json.has("active_effect_text") ? json.get("active_effect_text").getAsString() : "";
        String flag = json.has("flag") ? json.get("flag").getAsString() : "-o";
        String advancement = json.has("advancement") ? json.get("advancement").getAsString() : "";
        int threshold = json.has("threshold") ? json.get("threshold").getAsInt() : 0;
        boolean requireUnique = !json.has("require_unique") || json.get("require_unique").getAsBoolean();
        String tooltipIcon = json.has("tooltip_icon") ? json.get("tooltip_icon").getAsString() : "● ";
        String tooltipIconColor = json.has("tooltip_icon_color") ? json.get("tooltip_icon_color").getAsString() : "aqua";
        boolean hideActivationMessage = json.has("hide_activation_message") && json.get("hide_activation_message").getAsBoolean();

        List<String> items = new ArrayList<>();
        if (json.has("items") && json.get("items").isJsonArray()) {
            JsonArray itemsArray = json.getAsJsonArray("items");
            for (JsonElement itemEl : itemsArray) {
                items.add(itemEl.getAsString());
            }
        }

        List<FoodGroupDefinition.GroupEffect> effects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            JsonArray effectsArray = json.getAsJsonArray("effects");
            for (JsonElement effectEl : effectsArray) {
                if (effectEl.isJsonObject()) {
                    FoodGroupDefinition.GroupEffect effect = parseEffect(effectEl.getAsJsonObject());
                    if (effect != null) {
                        effects.add(effect);
                    }
                }
            }
        }

        return new FoodGroupDefinition(id, name, activeEffectText, flag, advancement, items, effects, threshold, requireUnique, tooltipIcon, tooltipIconColor, hideActivationMessage);
    }

    
    private FoodGroupDefinition.GroupEffect parseEffect(JsonObject json) {
        String durationType = "fixed";
        if (json.has("duration_type")) durationType = json.get("duration_type").getAsString();
        else if (json.has("type")) durationType = json.get("type").getAsString();

        String effectId = json.has("effect_id") ? json.get("effect_id").getAsString() : "";
        
        double value = 0.0;
        if (json.has("value") && json.get("value").isJsonPrimitive()) {
            if (json.get("value").getAsJsonPrimitive().isNumber()) {
                value = json.get("value").getAsDouble();
            }
        }
        
        int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
        int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 0;

        String effectType;
        if (json.has("effect_type")) {
            effectType = json.get("effect_type").getAsString();
        } else {
         
            if ("health".equalsIgnoreCase(effectId)) {
                effectType = "health";
            } else if (effectId.startsWith("diet:")) {
                effectType = "diet";
            } else if ("overlay".equalsIgnoreCase(effectId)) {
                effectType = "overlay";
            } else {
                effectType = "mob_effect";
            }
        }

        if ("overlay".equals(effectType) && json.has("value") && json.get("value").isJsonPrimitive() && json.get("value").getAsJsonPrimitive().isString()) {
            effectId = json.get("value").getAsString();
        }

        return new FoodGroupDefinition.GroupEffect(
                durationType, effectType, effectId,
                value, amplifier, durationTicks,
                parseIntArray(json, "interval_active_ticks"),
                parseIntArray(json, "interval_pause_ticks"),
                parseStringList(json, "cancel_on_groups"),
                parseStringList(json, "requires_active_groups"),
                parseStringMap(json, "duration_multipliers")
        );
    }

    
    private Map<String, Double> parseStringMap(JsonObject json, String key) {
        Map<String, Double> result = new HashMap<>();
        if (json.has(key) && json.get(key).isJsonObject()) {
            JsonObject obj = json.getAsJsonObject(key);
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    result.put(entry.getKey(), entry.getValue().getAsDouble());
                }
            }
        }
        return result;
    }

    private int[] parseIntArray(JsonObject json, String key) {
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray arr = json.getAsJsonArray(key);
            if (arr.size() >= 2) {
                return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt()};
            } else if (arr.size() == 1) {
                int val = arr.get(0).getAsInt();
                return new int[]{val, val};
            }
        }
        return new int[]{0, 0};
    }

    
    private List<String> parseStringList(JsonObject json, String key) {
        List<String> result = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray arr = json.getAsJsonArray(key);
            for (JsonElement el : arr) {
                result.add(el.getAsString());
            }
        }
        return result;
    }

    // ==================== API ====================

    public FoodGroupDefinition getGroup(ResourceLocation id) {
        return groups.get(id);
    }

    public FoodGroupDefinition getGroupByName(String name) {
        for (Map.Entry<ResourceLocation, FoodGroupDefinition> entry : groups.entrySet()) {
            if (entry.getKey().getPath().equals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public List<FoodGroupDefinition> getGroupsForItem(String itemId) {
        return itemToGroups.getOrDefault(itemId, Collections.emptyList());
    }

    public Collection<FoodGroupDefinition> getAllGroups() {
        return groups.values();
    }

    public Map<ResourceLocation, FoodGroupDefinition> getGroupsMap() {
        return groups;
    }

    public CompoundTag serializeToNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag listTag = new ListTag();
        
        for (FoodGroupDefinition group : groups.values()) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.putString("id", group.getId().toString());
            groupTag.putString("name", group.getName());
            groupTag.putString("activeEffectText", group.getActiveEffectText());
            groupTag.putString("flag", group.getFlag());
            groupTag.putString("advancement", group.getAdvancement());
            groupTag.putInt("threshold", group.getThreshold());
            groupTag.putBoolean("requireUnique", group.isRequireUnique());
            groupTag.putString("tooltipIcon", group.getTooltipIcon());
            groupTag.putString("tooltipIconColor", group.getTooltipIconColor());
            groupTag.putBoolean("hideActivationMessage", group.isHideActivationMessage());
            
            ListTag itemsList = new ListTag();
            for (String item : group.getItems()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putString("item", item);
                itemsList.add(itemTag);
            }
            groupTag.put("items", itemsList);
            
            ListTag effectsList = new ListTag();
            for (FoodGroupDefinition.GroupEffect effect : group.getEffects()) {
                CompoundTag effectTag = new CompoundTag();
                effectTag.putString("durationType", effect.getDurationType());
                effectTag.putString("effectType", effect.getEffectType());
                effectTag.putString("effectId", effect.getEffectId());
                effectTag.putDouble("value", effect.getValue());
                effectTag.putInt("amplifier", effect.getAmplifier());
                effectTag.putInt("durationTicks", effect.getDurationTicks());
                
                effectTag.putIntArray("intervalActiveTicks", effect.getIntervalActiveTicks());
                effectTag.putIntArray("intervalPauseTicks", effect.getIntervalPauseTicks());
                
                ListTag cancelGroupsList = new ListTag();
                for (String cancelGroup : effect.getCancelOnGroups()) {
                    CompoundTag cancelGroupTag = new CompoundTag();
                    cancelGroupTag.putString("group", cancelGroup);
                    cancelGroupsList.add(cancelGroupTag);
                }
                effectTag.put("cancelOnGroups", cancelGroupsList);
                
                ListTag requiresGroupsList = new ListTag();
                for (String reqGroup : effect.getRequiresActiveGroups()) {
                    CompoundTag reqGroupTag = new CompoundTag();
                    reqGroupTag.putString("group", reqGroup);
                    requiresGroupsList.add(reqGroupTag);
                }
                effectTag.put("requiresActiveGroups", requiresGroupsList);

                CompoundTag multipliersTag = new CompoundTag();
                for (Map.Entry<String, Double> entry : effect.getDurationMultipliers().entrySet()) {
                    multipliersTag.putDouble(entry.getKey(), entry.getValue());
                }
                effectTag.put("durationMultipliers", multipliersTag);
                
                effectsList.add(effectTag);
            }
            groupTag.put("effects", effectsList);
            
            listTag.add(groupTag);
        }
        
        tag.put("groups", listTag);
        return tag;
    }

    public void deserializeFromNBT(CompoundTag tag) {
        groups.clear();
        itemToGroups.clear();
        
        if (!tag.contains("groups")) return;
        
        ListTag listTag = tag.getList("groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag groupTag = listTag.getCompound(i);
            ResourceLocation id = new ResourceLocation(groupTag.getString("id"));
            String name = groupTag.getString("name");
            String activeEffectText = groupTag.getString("activeEffectText");
            String flag = groupTag.getString("flag");
            String advancement = groupTag.getString("advancement");
            int threshold = groupTag.getInt("threshold");
            boolean requireUnique = groupTag.getBoolean("requireUnique");
            String tooltipIcon = groupTag.contains("tooltipIcon") ? groupTag.getString("tooltipIcon") : "● ";
            String tooltipIconColor = groupTag.contains("tooltipIconColor") ? groupTag.getString("tooltipIconColor") : "aqua";
            boolean hideActivationMessage = groupTag.contains("hideActivationMessage") && groupTag.getBoolean("hideActivationMessage");
            
            List<String> items = new ArrayList<>();
            ListTag itemsList = groupTag.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < itemsList.size(); j++) {
                items.add(itemsList.getCompound(j).getString("item"));
            }
            
            List<FoodGroupDefinition.GroupEffect> effects = new ArrayList<>();
            ListTag effectsList = groupTag.getList("effects", Tag.TAG_COMPOUND);
            for (int k = 0; k < effectsList.size(); k++) {
                CompoundTag effectTag = effectsList.getCompound(k);
                
                List<String> cancelOnGroups = new ArrayList<>();
                ListTag cancelGroupsList = effectTag.getList("cancelOnGroups", Tag.TAG_COMPOUND);
                for (int m = 0; m < cancelGroupsList.size(); m++) {
                    cancelOnGroups.add(cancelGroupsList.getCompound(m).getString("group"));
                }
                
                List<String> requiresActiveGroups = new ArrayList<>();
                ListTag reqGroupsList = effectTag.getList("requiresActiveGroups", Tag.TAG_COMPOUND);
                for (int m = 0; m < reqGroupsList.size(); m++) {
                    requiresActiveGroups.add(reqGroupsList.getCompound(m).getString("group"));
                }

                Map<String, Double> durationMultipliers = new HashMap<>();
                if (effectTag.contains("durationMultipliers", Tag.TAG_COMPOUND)) {
                    CompoundTag multTag = effectTag.getCompound("durationMultipliers");
                    for (String key_ : multTag.getAllKeys()) {
                        durationMultipliers.put(key_, multTag.getDouble(key_));
                    }
                }
                
                FoodGroupDefinition.GroupEffect effect = new FoodGroupDefinition.GroupEffect(
                    effectTag.getString("durationType"),
                    effectTag.getString("effectType"),
                    effectTag.getString("effectId"),
                    effectTag.getDouble("value"),
                    effectTag.getInt("amplifier"),
                    effectTag.getInt("durationTicks"),
                    effectTag.getIntArray("intervalActiveTicks"),
                    effectTag.getIntArray("intervalPauseTicks"),
                    cancelOnGroups,
                    requiresActiveGroups,
                    durationMultipliers
                );
                effects.add(effect);
            }
            
            FoodGroupDefinition group = new FoodGroupDefinition(id, name, activeEffectText, flag, advancement, items, effects, threshold, requireUnique, tooltipIcon, tooltipIconColor, hideActivationMessage);
            groups.put(id, group);
            
            for (String itemOrTag : group.getItems()) {
                if (itemOrTag.startsWith("#")) {
                    try {
                        ResourceLocation tagRL = new ResourceLocation(itemOrTag.substring(1));
                        net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tagKey =
                                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.BuiltInRegistries.ITEM.key(), tagRL);

                        for (net.minecraft.core.Holder<net.minecraft.world.item.Item> holder :
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                            String idStr = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(holder.value()).toString();
                            itemToGroups.computeIfAbsent(idStr, k_ -> new ArrayList<>()).add(group);
                        }
                    } catch (Exception e) {
                   
                    }
                } else {
                    itemToGroups.computeIfAbsent(itemOrTag, k_ -> new ArrayList<>()).add(group);
                }
            }
        }
        

    }
}
