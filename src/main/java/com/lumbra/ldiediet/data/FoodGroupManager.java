package com.lumbra.ldiediet.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumbra.ldiediet.LDieDiet;
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
    private static final FoodGroupManager CLIENT = new FoodGroupManager();

    private final Map<ResourceLocation, FoodGroupDefinition> groups = new LinkedHashMap<>();

    private final Map<String, List<FoodGroupDefinition>> itemToGroups = new HashMap<>();

    public FoodGroupManager() {
        super(GSON, "food_groups");
    }

    public static FoodGroupManager getInstance() {
        if (instance == null) {
            instance = new FoodGroupManager();
        }
        return instance;
    }
    public static FoodGroupManager getClientInstance() { return CLIENT; }

    public void refreshMemberships() {
        itemToGroups.clear();
        for (FoodGroupDefinition group : groups.values()) {
            Set<String> matched = new LinkedHashSet<>();
            for (String itemOrTag : group.getItems()) {
                ResourceLocation id = ResourceLocation.tryParse(itemOrTag.startsWith("#") ? itemOrTag.substring(1) : itemOrTag);
                if (id == null) continue;
                if (itemOrTag.startsWith("#")) {
                    var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id);
                    for (var holder : net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tag))
                        matched.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(holder.value()).toString());
                } else if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) matched.add(id.toString());
            }
            for (String item : matched) itemToGroups.computeIfAbsent(item, ignored -> new ArrayList<>()).add(group);
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectIn, ResourceManager resourceManagerIn, ProfilerFiller profilerIn) {
        AuthorDiagnostics.clearParseIssues("food_groups/");
        groups.clear();
        itemToGroups.clear();

        objectIn.forEach((location, element) -> {
            try {
                if (element.isJsonObject()) {
                    FoodGroupDefinition group = parseGroup(location, element.getAsJsonObject());
                    if (group != null) {
                        groups.put(location, group);

                    }
                }
            } catch (Exception e) {
                AuthorDiagnostics.parseIssue("food_groups/"+location,e.getMessage());
                LDieDiet.LOGGER.error("Failed to parse food group {}", location, e);
            }
        });
        refreshMemberships();
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
                try {
                    String item = itemEl.getAsString();
                    if (ResourceLocation.tryParse(item.startsWith("#") ? item.substring(1) : item) != null) items.add(item);
                } catch (RuntimeException e) { LDieDiet.LOGGER.warn("Ignoring malformed item in group {}", id); }
            }
        }

        List<FoodGroupDefinition.GroupEffect> effects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            JsonArray effectsArray = json.getAsJsonArray("effects");
            for (JsonElement effectEl : effectsArray) {
                try {
                if (effectEl.isJsonObject()) {
                    FoodGroupDefinition.GroupEffect effect = parseEffect(effectEl.getAsJsonObject());
                    if (effect != null) {
                        effects.add(effect);
                    }
                }
                } catch (RuntimeException e) { AuthorDiagnostics.parseIssue("food_groups/"+id,"effects: "+e.getMessage()); LDieDiet.LOGGER.warn("Ignoring malformed effect in group {}", id, e); }
            }
        }

        return new FoodGroupDefinition(id, name, activeEffectText, flag, advancement, items, effects, threshold, requireUnique, tooltipIcon, tooltipIconColor, hideActivationMessage)
                .presentation(string(json,"name_key",""),string(json,"active_effect_text_key",""),string(json,"icon",""),json.has("order")?json.get("order").getAsInt():0).secret(json.has("secret")&&json.get("secret").getAsBoolean());
    }

    
    private static String string(JsonObject json,String key,String fallback) { return json.has(key)?json.get(key).getAsString():fallback; }
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

         
        if ("diet".equals(effectType)) {
            effectType = "nutrient_multiplier";
            if (effectId.startsWith("diet:")) effectId = effectId.substring("diet:".length());
        }

        if ("overlay".equals(effectType) && json.has("value") && json.get("value").isJsonPrimitive() && json.get("value").getAsJsonPrimitive().isString()) {
            effectId = json.get("value").getAsString();
        }

        if (!Set.of("fixed","until_death","until_death_intermittent","history_intermittent").contains(durationType)) throw new IllegalArgumentException("Unknown duration_type: "+durationType);
        if (!Set.of("mob_effect","health","attribute","nutrient_multiplier","overlay").contains(effectType)) throw new IllegalArgumentException("Unknown effect_type: "+effectType);
        return new FoodGroupDefinition.GroupEffect(
                durationType, effectType, effectId,
                value, amplifier, durationTicks,
                parseIntArray(json, "interval_active_ticks"),
                parseIntArray(json, "interval_pause_ticks"),
                parseStringList(json, "cancel_on_groups"),
                parseStringList(json, "requires_active_groups"),
                parseStringMap(json, "duration_multipliers"),
                parseConditions(json)
        ).options(string(json,"operation","addition"),json.has("continuous_conditions") && json.get("continuous_conditions").getAsBoolean());
    }

     


    private EffectCondition parseConditions(JsonObject json) {
        if (!json.has("conditions") || !json.get("conditions").isJsonObject()) {
            return null;
        }
        JsonObject cond = json.getAsJsonObject("conditions");
        String logic = cond.has("logic") ? cond.get("logic").getAsString() : "AND";
        Double tempMin = null;
        Double tempMax = null;
        if (cond.has("temperature") && cond.get("temperature").isJsonObject()) {
            JsonObject tempObj = cond.getAsJsonObject("temperature");
            if (tempObj.has("min")) tempMin = tempObj.get("min").getAsDouble();
            if (tempObj.has("max")) tempMax = tempObj.get("max").getAsDouble();
        }
        String coldSweatState = cond.has("cold_sweat_state") ? cond.get("cold_sweat_state").getAsString() : null;
        String season = cond.has("season") ? cond.get("season").getAsString() : null;
        return new EffectCondition(logic, tempMin, tempMax, coldSweatState, season).extended(cond);
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
            groupTag.putString("nameKey",group.getNameKey());groupTag.putString("textKey",group.getActiveEffectTextKey());
            groupTag.putString("icon",group.getIcon());groupTag.putInt("order",group.getOrder());groupTag.putBoolean("secret",group.isSecret());
            groupTag.putString("activeEffectText", group.getActiveEffectText());
            groupTag.putString("flag", group.getFlag());
            groupTag.putString("advancement", group.getAdvancement());
            groupTag.putInt("threshold", group.getThreshold());
            groupTag.putBoolean("requireUnique", group.isRequireUnique());
            groupTag.putString("tooltipIcon", group.getTooltipIcon());
            groupTag.putString("tooltipIconColor", group.getTooltipIconColor());
            groupTag.putBoolean("hideActivationMessage", group.isHideActivationMessage());
            
            groupTag.put("items", com.lumbra.ldiediet.util.NBTUtils.writeStringList(group.getItems(), "item"));
            List<String> resolvedItems = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues().stream()
                    .map(item -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString())
                    .filter(group::containsItem).sorted().toList();
            groupTag.put("resolvedItems", com.lumbra.ldiediet.util.NBTUtils.writeStringList(resolvedItems, "item"));
            
            ListTag effectsList = new ListTag();
            for (FoodGroupDefinition.GroupEffect effect : group.getEffects()) {
                CompoundTag effectTag = new CompoundTag();
                effectTag.putString("durationType", effect.getDurationType());
                effectTag.putString("effectType", effect.getEffectType());
                effectTag.putString("operation",effect.getOperation().name().toLowerCase(java.util.Locale.ROOT));
                effectTag.putBoolean("continuousConditions",effect.isContinuousConditions());
                effectTag.putString("effectId", effect.getEffectId());
                effectTag.putDouble("value", effect.getValue());
                effectTag.putInt("amplifier", effect.getAmplifier());
                effectTag.putInt("durationTicks", effect.getDurationTicks());
                
                effectTag.putIntArray("intervalActiveTicks", effect.getIntervalActiveTicks());
                effectTag.putIntArray("intervalPauseTicks", effect.getIntervalPauseTicks());
                
                effectTag.put("cancelOnGroups", com.lumbra.ldiediet.util.NBTUtils.writeStringList(effect.getCancelOnGroups(), "group"));
                
                effectTag.put("requiresActiveGroups", com.lumbra.ldiediet.util.NBTUtils.writeStringList(effect.getRequiresActiveGroups(), "group"));

                effectTag.put("durationMultipliers", com.lumbra.ldiediet.util.NBTUtils.writeStringDoubleMap(effect.getDurationMultipliers()));

                 
                if (effect.getConditions() != null) {
                    CompoundTag condTag = new CompoundTag();
                    condTag.put("extra",com.lumbra.ldiediet.util.JsonNbt.encode(effect.getConditions().additionalJson()));
                    condTag.putString("logic", effect.getConditions().getLogic());
                    if (effect.getConditions().getTemperatureMin() != null)
                        condTag.putDouble("tempMin", effect.getConditions().getTemperatureMin());
                    if (effect.getConditions().getTemperatureMax() != null)
                        condTag.putDouble("tempMax", effect.getConditions().getTemperatureMax());
                    if (effect.getConditions().getColdSweatState() != null)
                        condTag.putString("coldSweatState", effect.getConditions().getColdSweatState());
                    if (effect.getConditions().getSeason() != null)
                        condTag.putString("season", effect.getConditions().getSeason());
                    effectTag.put("conditions", condTag);
                }
                
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
            try {
            CompoundTag groupTag = listTag.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(groupTag.getString("id"));
            if (id == null) continue;
            String name = groupTag.getString("name");
            String activeEffectText = groupTag.getString("activeEffectText");
            String flag = groupTag.getString("flag");
            String advancement = groupTag.getString("advancement");
            int threshold = groupTag.getInt("threshold");
            boolean requireUnique = groupTag.getBoolean("requireUnique");
            String tooltipIcon = groupTag.contains("tooltipIcon") ? groupTag.getString("tooltipIcon") : "● ";
            String tooltipIconColor = groupTag.contains("tooltipIconColor") ? groupTag.getString("tooltipIconColor") : "aqua";
            boolean hideActivationMessage = groupTag.contains("hideActivationMessage") && groupTag.getBoolean("hideActivationMessage");
            
            List<String> items = com.lumbra.ldiediet.util.NBTUtils.readStringList(groupTag.getList(
                    groupTag.contains("resolvedItems") ? "resolvedItems" : "items", Tag.TAG_COMPOUND), "item");
            
            List<FoodGroupDefinition.GroupEffect> effects = new ArrayList<>();
            ListTag effectsList = groupTag.getList("effects", Tag.TAG_COMPOUND);
            for (int k = 0; k < effectsList.size(); k++) {
                try {
                CompoundTag effectTag = effectsList.getCompound(k);
                
                List<String> cancelOnGroups = com.lumbra.ldiediet.util.NBTUtils.readStringList(effectTag.getList("cancelOnGroups", Tag.TAG_COMPOUND), "group");
                
                List<String> requiresActiveGroups = com.lumbra.ldiediet.util.NBTUtils.readStringList(effectTag.getList("requiresActiveGroups", Tag.TAG_COMPOUND), "group");

                Map<String, Double> durationMultipliers = new HashMap<>();
                if (effectTag.contains("durationMultipliers", Tag.TAG_COMPOUND)) {
                    com.lumbra.ldiediet.util.NBTUtils.readStringDoubleMap(effectTag.getCompound("durationMultipliers"), durationMultipliers);
                }
                
                 
                EffectCondition conditions = null;
                if (effectTag.contains("conditions", Tag.TAG_COMPOUND)) {
                    CompoundTag condTag = effectTag.getCompound("conditions");
                    String condLogic = condTag.contains("logic") ? condTag.getString("logic") : "AND";
                    Double condTempMin = condTag.contains("tempMin") ? condTag.getDouble("tempMin") : null;
                    Double condTempMax = condTag.contains("tempMax") ? condTag.getDouble("tempMax") : null;
                    String condColdSweat = condTag.contains("coldSweatState") ? condTag.getString("coldSweatState") : null;
                    String condSeason = condTag.contains("season") ? condTag.getString("season") : null;
                    conditions = new EffectCondition(condLogic, condTempMin, condTempMax, condColdSweat, condSeason);
                    if(condTag.contains("extra"))conditions.extended(com.lumbra.ldiediet.util.JsonNbt.decode(condTag.getCompound("extra")).getAsJsonObject());
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
                    durationMultipliers,
                    conditions
                );
                effect.options(effectTag.contains("operation")?effectTag.getString("operation"):"addition",effectTag.getBoolean("continuousConditions"));
                effects.add(effect);
                } catch (RuntimeException e) { LDieDiet.LOGGER.warn("Ignoring malformed synchronized effect in {}", id, e); }
            }
            
            FoodGroupDefinition group = new FoodGroupDefinition(id, name, activeEffectText, flag, advancement, items, effects, threshold, requireUnique, tooltipIcon, tooltipIconColor, hideActivationMessage);
            group.presentation(groupTag.getString("nameKey"),groupTag.getString("textKey"),groupTag.getString("icon"),groupTag.getInt("order")).secret(groupTag.getBoolean("secret"));
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
            } catch (RuntimeException e) { LDieDiet.LOGGER.warn("Ignoring malformed synchronized group at index {}", i, e); }
        }
        

    }
}
