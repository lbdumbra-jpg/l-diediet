package com.levbu.ldiediet.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import com.levbu.ldiediet.data.FoodGroupManager;
import com.levbu.ldiediet.data.FoodGroupDefinition;

import java.util.List;
import java.util.Random;


public class ActiveGroupEffect {

    private static final Random RANDOM = new Random();

    private final String groupId;

    private final String effectType;

    private final String effectId;

    private final double value;

    private final int amplifier;

    private final String durationType;

    private int remainingTicks;

    private int phaseTicksRemaining;

    private final int intervalActiveMin;
    private final int intervalActiveMax;
    private final int intervalPauseMin;
    private final int intervalPauseMax;

    private final String[] cancelOnGroups;

    private final java.util.Map<String, Double> durationMultipliers;

    private boolean currentlyActive;

    public ActiveGroupEffect(String groupId, String effectType, String effectId,
                             double value, int amplifier, String durationType,
                             int durationTicks,
                             int intervalActiveMin, int intervalActiveMax,
                             int intervalPauseMin, int intervalPauseMax,
                             String[] cancelOnGroups,
                             java.util.Map<String, Double> durationMultipliers) {
        this.groupId = groupId;
        this.effectType = effectType;
        this.effectId = effectId;
        this.value = value;
        this.amplifier = amplifier;
        this.durationType = durationType;
        this.remainingTicks = durationTicks;
        this.intervalActiveMin = intervalActiveMin;
        this.intervalActiveMax = intervalActiveMax;
        this.intervalPauseMin = intervalPauseMin;
        this.intervalPauseMax = intervalPauseMax;
        this.cancelOnGroups = cancelOnGroups;
        this.durationMultipliers = durationMultipliers != null ? durationMultipliers : new java.util.HashMap<>();

        if (isIntermittent()) {
            this.currentlyActive = true;
            this.phaseTicksRemaining = rollRandom(intervalActiveMin, intervalActiveMax);
        } else {
            this.currentlyActive = true;
            this.phaseTicksRemaining = 0;
        }
    }

    public String getGroupId() { return groupId; }
    public String getEffectType() { return effectType; }
    public String getEffectId() { return effectId; }
    public double getValue() { return value; }
    public int getAmplifier() { return amplifier; }
    public String getDurationType() { return durationType; }
    public int getRemainingTicks() { return remainingTicks; }
    public boolean isCurrentlyActive() { return currentlyActive; }
    public String[] getCancelOnGroups() { return cancelOnGroups; }

    public boolean isMobEffect() { return "mob_effect".equals(effectType); }
    public boolean isHealth() { return "health".equals(effectType); }
    public boolean isDiet() { return "diet".equals(effectType); }
    public boolean isOverlay() { return "overlay".equals(effectType); }

    public boolean isFixed() { return "fixed".equals(durationType); }
    public boolean isUntilDeath() { return "until_death".equals(durationType); }
    public boolean isIntermittent() {
        return "until_death_intermittent".equals(durationType) || "history_intermittent".equals(durationType);
    }

    public double getAppliedMultiplier(IFoodTolerance cap) {
        double multiplier = 1.0;
        if (durationMultipliers.isEmpty()) return 1.0;
        
        for (java.util.Map.Entry<String, Double> entry : durationMultipliers.entrySet()) {
            if (cap.hasActiveEffectsForGroup(entry.getKey())) {
                multiplier *= entry.getValue();
            }
        }
        return multiplier;
    }

    public boolean tick() {
        if (isFixed()) {
            remainingTicks--;
            return remainingTicks <= 0;
        }

        if (isIntermittent()) {
            if (intervalActiveMax <= 0 && intervalPauseMax <= 0) {
                currentlyActive = true;
                return false;
            }

            phaseTicksRemaining--;
            if (phaseTicksRemaining <= 0) {
               
                currentlyActive = !currentlyActive;
                
                if (currentlyActive) {
                    if (intervalActiveMax <= 0) {
                       
                        phaseTicksRemaining = 1; 
                    } else {
                        phaseTicksRemaining = Math.max(1, rollRandom(intervalActiveMin, intervalActiveMax));
                    }
                } else {
                    if (intervalPauseMax <= 0) {
                       
                        phaseTicksRemaining = 1;
                    } else {
                        phaseTicksRemaining = Math.max(1, rollRandom(intervalPauseMin, intervalPauseMax));
                    }
                }
            }
        }

       
        return false;
    }

    
    public boolean shouldCancelOnGroup(String activatedGroupId) {
       
        FoodGroupManager manager = FoodGroupManager.getInstance();
        if (manager != null) {
            FoodGroupDefinition group = manager.getGroup(new ResourceLocation("ldiediet", this.groupId));
            if (group == null) {
                group = manager.getGroup(new ResourceLocation(this.groupId));
            }
            if (group != null) {
                for (FoodGroupDefinition.GroupEffect defEffect : group.getEffects()) {
                    if (defEffect.getEffectId().equals(this.effectId) && defEffect.getEffectType().equals(this.effectType)) {
                        List<String> liveCancelGroups = defEffect.getCancelOnGroups();
                        if (liveCancelGroups != null) {
                            for (String cg : liveCancelGroups) {
                                if (cg.equals(activatedGroupId)) return true;
                            }
                           
                            return false;
                        }
                    }
                }
            }
        }

        
        if (cancelOnGroups == null || cancelOnGroups.length == 0) return false;
        for (String group : cancelOnGroups) {
            if (group.equals(activatedGroupId)) return true;
        }
        return false;
    }

    private static int rollRandom(int min, int max) {
        if (min >= max) return min;
        return min + RANDOM.nextInt(max - min + 1);
    }

   

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("groupId", groupId);
        tag.putString("effectType", effectType);
        tag.putString("effectId", effectId);
        tag.putDouble("value", value);
        tag.putInt("amplifier", amplifier);
        tag.putString("durationType", durationType);
        tag.putInt("remainingTicks", remainingTicks);
        tag.putBoolean("currentlyActive", currentlyActive);
        tag.putInt("phaseTicksRemaining", phaseTicksRemaining);
        tag.putInt("intervalActiveMin", intervalActiveMin);
        tag.putInt("intervalActiveMax", intervalActiveMax);
        tag.putInt("intervalPauseMin", intervalPauseMin);
        tag.putInt("intervalPauseMax", intervalPauseMax);

        if (cancelOnGroups != null && cancelOnGroups.length > 0) {
            tag.putString("cancelOnGroups", String.join(",", cancelOnGroups));
        }

        if (!durationMultipliers.isEmpty()) {
            CompoundTag multTag = new CompoundTag();
            for (java.util.Map.Entry<String, Double> entry : durationMultipliers.entrySet()) {
                multTag.putDouble(entry.getKey(), entry.getValue());
            }
            tag.put("durationMultipliers", multTag);
        }

        return tag;
    }

    public static ActiveGroupEffect fromNBT(CompoundTag tag) {
        String[] cancelGroups = new String[0];
        if (tag.contains("cancelOnGroups")) {
            cancelGroups = tag.getString("cancelOnGroups").split(",");
        }

        java.util.Map<String, Double> multipliers = new java.util.HashMap<>();
        if (tag.contains("durationMultipliers")) {
            CompoundTag multTag = tag.getCompound("durationMultipliers");
            for (String key : multTag.getAllKeys()) {
                multipliers.put(key, multTag.getDouble(key));
            }
        }

        ActiveGroupEffect effect = new ActiveGroupEffect(
                tag.getString("groupId"),
                tag.getString("effectType"),
                tag.getString("effectId"),
                tag.getDouble("value"),
                tag.getInt("amplifier"),
                tag.getString("durationType"),
                tag.getInt("remainingTicks"),
                tag.getInt("intervalActiveMin"),
                tag.getInt("intervalActiveMax"),
                tag.getInt("intervalPauseMin"),
                tag.getInt("intervalPauseMax"),
                cancelGroups,
                multipliers
        );
        effect.currentlyActive = tag.getBoolean("currentlyActive");
        effect.phaseTicksRemaining = tag.getInt("phaseTicksRemaining");
        return effect;
    }
}
