package com.levbu.ldiediet.capability;

import com.levbu.ldiediet.LDieDietServerConfig;
import net.minecraft.nbt.CompoundTag;

import com.levbu.ldiediet.util.ExclusionMatcher;
import net.minecraft.world.item.Item;


public class FoodToleranceEntry {
    private int consecutiveEaten; 
    private long lastEatenGameTime; 
    private int dietGroupCount = 1; 

    public FoodToleranceEntry() {
        this.consecutiveEaten = 0;
        this.lastEatenGameTime = 0;
    }

   
    public int getConsecutiveEaten() {
        return consecutiveEaten;
    }

    public long getLastEatenGameTime() {
        return lastEatenGameTime;
    }

    public int getDietGroupCount() {
        return dietGroupCount;
    }

   
    public int[] getAdjustedThresholds() {
        return LDieDietServerConfig.getThresholdsForDietStats(dietGroupCount);
    }

   
    public double getRecoveryTimeMultiplier() {
        return LDieDietServerConfig.getRecoveryMultiplierForDietStats(dietGroupCount);
    }

    public void incrementEaten() {
        consecutiveEaten++;
    }

    public void setLastEatenGameTime(long time) {
        lastEatenGameTime = time;
    }

    public void setDietGroupCount(int count) {
        this.dietGroupCount = Math.max(1, Math.min(5, count));
    }

    public void reset() {
        consecutiveEaten = 0;
    }

   
    
    public int calculateRecoveredStages(long currentTime, int fullRecoveryTicks) {
        if (consecutiveEaten == 0 || lastEatenGameTime == 0) {
            return 0;
        }

        long ticksPassed = currentTime - lastEatenGameTime;
        if (ticksPassed <= 0) {
            return 0;
        }

      
        int ticksPerStage = fullRecoveryTicks / 4;
        if (ticksPerStage <= 0) {
            return 0;
        }

        int recoveredStages = (int) (ticksPassed / ticksPerStage);
        return Math.min(4, recoveredStages);
    }

    
    public boolean tryRecover(long currentTime, int fullRecoveryTicks, Item item) {
        if (consecutiveEaten == 0)
            return true;

        int effectiveStage = calculateEffectiveStage(currentTime, fullRecoveryTicks, item);

        if (effectiveStage <= 0) {
            reset();
            return true;
        }

        return false;
    }

    
    public int calculateBaseStage(Item item) {
        return calculateBaseStage(item, getAdjustedThresholds());
    }

    public int calculateBaseStage(Item item, int[] thresholds) {
        int overrideStage = ExclusionMatcher.getStageOverride(item);
        if (overrideStage > 0) {
            return overrideStage;
        }

        for (int i = thresholds.length - 1; i >= 0; i--) {
            if (consecutiveEaten >= thresholds[i]) {
                return i + 1;
            }
        }
        return 0;
    }

   
    public int calculateEffectiveStage(long currentTime, int fullRecoveryTicks, Item item) {
        return calculateEffectiveStage(currentTime, fullRecoveryTicks, getAdjustedThresholds(), item);
    }
    
    public int calculateEffectiveStage(long currentTime, int fullRecoveryTicks, int[] thresholds, Item item) {
        int baseStage = calculateBaseStage(item, thresholds);
        int recoveredStages = calculateRecoveredStages(currentTime, fullRecoveryTicks);
        return Math.max(0, baseStage - recoveredStages);
    }

    
    public int calculateStage(Item item) {
        return calculateBaseStage(item);
    }

    public int calculateStage(Item item, int[] thresholds) {
        return calculateBaseStage(item, thresholds);
    }

   
    public int getNextThreshold(Item item, int[] thresholds) {
        int baseStage = calculateBaseStage(item, thresholds);
        if (baseStage >= thresholds.length) {
            return thresholds[thresholds.length - 1];
        }
        return thresholds[baseStage];
    }

    public long getTicksUntilNextStageRecovery(long currentTime, int fullRecoveryTicks) {
        int ticksPerStage = fullRecoveryTicks / 4;
        if (ticksPerStage <= 0)
            return 0;

        long ticksPassed = currentTime - lastEatenGameTime;
        int currentRecovered = (int) (ticksPassed / ticksPerStage);

        long ticksForNextRecovery = (long) (currentRecovered + 1) * ticksPerStage;
        return Math.max(0, ticksForNextRecovery - ticksPassed);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("eaten", consecutiveEaten);
        tag.putLong("time", lastEatenGameTime);
        tag.putInt("dietGroups", dietGroupCount);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        consecutiveEaten = tag.getInt("eaten");
        lastEatenGameTime = tag.getLong("time");
        dietGroupCount = tag.contains("dietGroups") ? tag.getInt("dietGroups") : 1;
    }

    public static FoodToleranceEntry fromNBT(CompoundTag tag) {
        FoodToleranceEntry entry = new FoodToleranceEntry();
        entry.deserializeNBT(tag);
        return entry;
    }
}
