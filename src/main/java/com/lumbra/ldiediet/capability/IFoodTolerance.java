package com.lumbra.ldiediet.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import com.lumbra.ldiediet.data.FoodKey;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IFoodTolerance {

     
    float getMonotony(Item item);
    void setMonotony(Item item, float value);
    void addMonotony(Item item, float value);
    Map<Item, Float> getAllMonotony();
    default float getMonotony(FoodKey key) { return getMonotony(key.item()); }
    default void setMonotony(FoodKey key, float value) { setMonotony(key.item(), value); }
    default void addMonotony(FoodKey key, float value) { addMonotony(key.item(), value); }
    default Map<FoodKey, Float> getAllFoodMonotony() {
        return getAllMonotony().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> FoodKey.of(entry.getKey()), Map.Entry::getValue));
    }
    default void recordEaten(FoodKey key, long gameTime) { recordEaten(key.item(), gameTime); }
    default void recordEaten(FoodKey key, long gameTime, boolean resetRecovery) {
        int meals = getRecoveryMeals(key);
        recordEaten(key, gameTime);
        if (!resetRecovery) setRecoveryMeals(key, meals);
    }
    default int getRecoveryMeals(FoodKey key) { return 0; }
    default void setRecoveryMeals(FoodKey key, int meals) {}
    default void limitMonotony(float cap) {
        getAllFoodMonotony().forEach((key, value) -> { if (value > cap) setMonotony(key, cap); });
    }
    default long getLastEatenGameTime(FoodKey key) { return getLastEatenGameTime(key.item()); }
    default com.lumbra.ldiediet.consumption.ConsumptionSnapshot getUseSession() { return null; }
    default void setUseSession(com.lumbra.ldiediet.consumption.ConsumptionSnapshot session) {}
    void clearMonotony();
    void reduceMonotonyGlobally(float fraction);
    void recordEaten(Item item, long gameTime);
    
    @Nullable
    Item getLastEatenItem();
    long getLastEatenGameTime(Item item);

    default Map<FoodKey,Long> getEatenCounts() { return Map.of(); }
    default net.minecraft.world.item.ItemStack foodSample(FoodKey key){return net.minecraft.world.item.ItemStack.EMPTY;}
    default void rememberFood(FoodKey key,net.minecraft.world.item.ItemStack stack){}
    default double[] externalNutrition(FoodKey key){return null;}
    default void rememberExternalNutrition(FoodKey key,double[] values){}
    default boolean isEatenCountExact(FoodKey key) { return false; }
    default boolean hasDiscovered(String id) { return false; }
    default void discover(String id) {}

     
    float getNutrientValue(String nutrientId);
    void setNutrientValue(String nutrientId, float value);
    void addNutrientValue(String nutrientId, float value);
    Map<String, Float> getAllNutrients();
    void clearNutrients();
    String getDietSuite();
    void setDietSuite(String suite);
    boolean hasImportedDietState();
    void markImportedDietState();

     
    void addHistory(Item item);
    default void addHistory(FoodKey key) { addHistory(key.item()); }
    List<HistoryEntry> getEatingHistory();
    void clearHistory();

     
    void addPermanentReward(String rewardId);
    void removePermanentReward(String rewardId);
    boolean hasPermanentReward(String rewardId);
    Set<String> getPermanentRewards();
    void clearPermanentRewards();

     
    void addActiveEffect(ActiveGroupEffect effect);
    void removeActiveEffect(ActiveGroupEffect effect);
    List<ActiveGroupEffect> getActiveEffects();
    void removeActiveEffectsForGroup(String groupId);
    boolean hasActiveEffectsForGroup(String groupId);
    List<String> cancelEffectsOnGroupActivation(String activatedGroupId);
    void clearActiveEffects();
    void clearDeathEffects();

     
    int getConsumedHistoryTotal(String groupId);
    void setConsumedHistoryTotal(String groupId, int total);
    int getHistoryTotalCount();
    long getMealSequence();
    long getRewardCursor(String groupId);
    void setRewardCursor(String groupId, long cursor);
    boolean needsRewardMigration();
    void markRewardsMigrated();
    com.lumbra.ldiediet.rewards.MobEffectSources getMobEffectSources();

     
    void tick(long currentGameTime);
    void copyFrom(IFoodTolerance other);
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);
}
