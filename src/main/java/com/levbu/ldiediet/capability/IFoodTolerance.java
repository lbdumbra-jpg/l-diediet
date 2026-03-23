package com.levbu.ldiediet.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;


public interface IFoodTolerance {

   
    FoodToleranceEntry getEntry(Item item);

   
    @Nullable
    FoodToleranceEntry getEntryOrNull(Item item);

    
    void recordEaten(Item item, long gameTime);

   
    @Nullable
    Item getLastEatenItem();

    void addHistory(Item item);

   
    java.util.List<HistoryEntry> getEatingHistory();

    
    void clearHistory();

    
    void addPermanentReward(String rewardId);

    
    void removePermanentReward(String rewardId);

    boolean hasPermanentReward(String rewardId);

    java.util.Set<String> getPermanentRewards();

    void clearPermanentRewards();

    // ==================== АКТИВНЫЕ ЭФФЕКТЫ ГРУПП ====================

   
    void addActiveEffect(ActiveGroupEffect effect);

    java.util.List<ActiveGroupEffect> getActiveEffects();

    void removeActiveEffectsForGroup(String groupId);

    boolean hasActiveEffectsForGroup(String groupId);

    java.util.List<String> cancelEffectsOnGroupActivation(String activatedGroupId);

    void clearActiveEffects();

    void clearDeathEffects();

    // ==================== ИСТОРИЯ ПОТРЕБЛЕНИЯ ДЛЯ ГРУПП ====================

    int getConsumedHistoryTotal(String groupId);

    void setConsumedHistoryTotal(String groupId, int total);

    int getHistoryTotalCount();

    // ==================== ОБЩЕЕ ====================

    void tick(long currentGameTime);

    double getVarietyRecoveryMultiplier();

    void copyFrom(IFoodTolerance other);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
