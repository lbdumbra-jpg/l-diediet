package com.lumbra.ldiediet.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;


public class FoodGroupDefinition {

    private final ResourceLocation id;
    private final String name;
    private final String activeEffectText;
    private final String flag; 
    private final String advancement;
    private final List<String> items;
    private final List<GroupEffect> effects;
    private final int threshold;
    private final boolean requireUnique;
    private final String tooltipIcon;
    private final String tooltipIconColor;
    private final boolean hideActivationMessage;

    public FoodGroupDefinition(ResourceLocation id, String name, String activeEffectText,
                               String flag, String advancement, List<String> items,
                               List<GroupEffect> effects, int threshold, boolean requireUnique,
                               String tooltipIcon, String tooltipIconColor, boolean hideActivationMessage) {
        this.id = id;
        this.name = name;
        this.activeEffectText = activeEffectText;
        this.flag = flag;
        this.advancement = advancement;
        this.items = items;
        this.effects = effects;
        this.threshold = threshold;
        this.requireUnique = requireUnique;
        this.tooltipIcon = tooltipIcon;
        this.tooltipIconColor = tooltipIconColor;
        this.hideActivationMessage = hideActivationMessage;
    }

    private String nameKey="", activeEffectTextKey="", icon="";
    private int order;
    private boolean secret;
    public FoodGroupDefinition secret(boolean value) { secret=value;return this; }
    public boolean isSecret() { return secret; }
    public boolean isKnown(com.lumbra.ldiediet.capability.IFoodTolerance cap) { return !secret || cap.hasDiscovered("group:"+id); }
    public FoodGroupDefinition presentation(String nameKey,String textKey,String icon,int order) {
        this.nameKey=nameKey;this.activeEffectTextKey=textKey;this.icon=icon;this.order=order;return this;
    }
    public String getNameKey() { return nameKey; }
    public String getActiveEffectTextKey() { return activeEffectTextKey; }
    public String getIcon() { return icon; }
    public int getOrder() { return order; }
    private String defaultTextKey(String suffix) { return "ldiediet.group."+id.getNamespace()+"."+id.getPath().replace('/','.')+"."+suffix; }
    public net.minecraft.network.chat.Component displayName() { return com.lumbra.ldiediet.util.PackText.of(nameKey.isBlank()?defaultTextKey("name"):nameKey,name); }
    public net.minecraft.network.chat.Component displayActiveText() { return com.lumbra.ldiediet.util.PackText.of(activeEffectTextKey.isBlank()?defaultTextKey("active"):activeEffectTextKey,activeEffectText); }
    public ResourceLocation getId() { return id; }
    public String getName() { return name; }
    public String getActiveEffectText() { return activeEffectText; }
    public String getFlag() { return flag; }
    public String getAdvancement() { return advancement; }
    public List<String> getItems() { return items; }
    public List<GroupEffect> getEffects() { return effects; }
    public int getThreshold() { return threshold; }
    public boolean isRequireUnique() { return requireUnique; }
    public String getTooltipIcon() { return tooltipIcon; }
    public String getTooltipIconColor() { return tooltipIconColor; }
    public boolean isHideActivationMessage() { return hideActivationMessage; }

   
    public boolean isConsecutive() { return "-c".equalsIgnoreCase(flag); }

    
    public boolean containsItem(String itemId) {
        for (String groupItem : items) {
            if (groupItem.startsWith("#")) {
                if (isItemInTag(itemId, groupItem.substring(1))) {
                    return true;
                }
            } else if (groupItem.equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isItemInTag(String itemId, String tagId) {
        try {
            ResourceLocation itemRL = new ResourceLocation(itemId);
            ResourceLocation tagRL = new ResourceLocation(tagId);
            Item item = BuiltInRegistries.ITEM.get(itemRL);
            if (item == Items.AIR && !itemId.equals("minecraft:air")) return false;

            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            
            return BuiltInRegistries.ITEM.getHolder(
                    BuiltInRegistries.ITEM.getResourceKey(item).get())
                    .map(holder -> holder.is(tagKey))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

   
    public static class GroupEffect {
        private final String durationType;   
        private final String effectType;    
        private final String effectId;       
        private final double value;          
        private final int amplifier;        
        private final int durationTicks;    
        private final int[] intervalActiveTicks;  
        private final int[] intervalPauseTicks;   
        private final List<String> cancelOnGroups; 
        private final List<String> requiresActiveGroups; 
        private final java.util.Map<String, Double> durationMultipliers; 
        private final EffectCondition conditions;

        public GroupEffect(String durationType, String effectType, String effectId,
                           double value, int amplifier, int durationTicks,
                           int[] intervalActiveTicks, int[] intervalPauseTicks,
                           List<String> cancelOnGroups, List<String> requiresActiveGroups,
                           java.util.Map<String, Double> durationMultipliers,
                           EffectCondition conditions) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite group effect value");
            if (durationMultipliers != null) durationMultipliers = durationMultipliers.entrySet().stream()
                    .filter(e -> e.getValue() != null && Double.isFinite(e.getValue()) && e.getValue() > 0)
                    .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
            this.durationType = durationType;
            this.effectType = effectType;
            this.effectId = effectId;
            this.value = value;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.intervalActiveTicks = (intervalActiveTicks != null && intervalActiveTicks.length >= 2) ? intervalActiveTicks : new int[]{0, 0};
            this.intervalPauseTicks = (intervalPauseTicks != null && intervalPauseTicks.length >= 2) ? intervalPauseTicks : new int[]{0, 0};
            this.cancelOnGroups = cancelOnGroups;
            this.requiresActiveGroups = requiresActiveGroups;
            this.durationMultipliers = durationMultipliers != null ? durationMultipliers : new java.util.HashMap<>();
            this.conditions = conditions;
        }

        private net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
        private boolean continuousConditions;
        public GroupEffect options(String operation,boolean continuousConditions) {
            this.operation=NutritionScaleDefinition.parseOperation(operation);this.continuousConditions=continuousConditions;return this;
        }
        public net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation getOperation() { return operation; }
        public boolean isContinuousConditions() { return continuousConditions; }
        public String getDurationType() { return durationType; }
        public String getEffectType() { return effectType; }
        public String getEffectId() { return effectId; }
        public double getValue() { return value; }
        public int getAmplifier() { return amplifier; }
        public int getDurationTicks() { return durationTicks; }
        public int[] getIntervalActiveTicks() { return intervalActiveTicks; }
        public int[] getIntervalPauseTicks() { return intervalPauseTicks; }
        public List<String> getCancelOnGroups() { return cancelOnGroups; }
        public List<String> getRequiresActiveGroups() { return requiresActiveGroups; }
        public java.util.Map<String, Double> getDurationMultipliers() { return durationMultipliers; }
        public EffectCondition getConditions() { return conditions; }

        public GroupEffect withValues(double value,int amplifier,int durationTicks) {
            if(amplifier<0||amplifier>255||durationTicks<0)throw new IllegalArgumentException("Invalid amplifier/duration");
            return new GroupEffect(durationType,effectType,effectId,value,amplifier,durationTicks,intervalActiveTicks,intervalPauseTicks,cancelOnGroups,requiresActiveGroups,durationMultipliers,conditions)
                    .options(operation.name().toLowerCase(java.util.Locale.ROOT),continuousConditions);
        }
        public boolean isFixed() { return "fixed".equals(durationType); }
        public boolean isUntilDeath() { return "until_death".equals(durationType); }
        public boolean isUntilDeathIntermittent() { return "until_death_intermittent".equals(durationType); }
        public boolean isHistoryIntermittent() { return "history_intermittent".equals(durationType); }

        public boolean isMobEffect() { return "mob_effect".equals(effectType); }
        public boolean isHealth() { return "health".equals(effectType); }
        public boolean isAttribute() { return "attribute".equals(effectType); }
        public boolean isNutrientMultiplier() { return "nutrient_multiplier".equals(effectType); }
        public boolean isOverlay() { return "overlay".equals(effectType); }
    }
}
