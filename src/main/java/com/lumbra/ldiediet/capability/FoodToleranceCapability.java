package com.lumbra.ldiediet.capability;

import com.lumbra.ldiediet.LDieDietServerConfig;
import com.lumbra.ldiediet.util.GroupIds;
import com.lumbra.ldiediet.data.FoodKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.*;

public class FoodToleranceCapability implements IFoodTolerance {

    private final Map<FoodKey, MonotonyEntry> monotonyMap = new HashMap<>();
    private final Map<ResourceLocation, MonotonyEntry> legacyItemSeeds = new HashMap<>();
    private final Set<String> discoveries = new HashSet<>();
    @Override public boolean hasDiscovered(String id) { return discoveries.contains(id); }
    @Override public void discover(String id) { discoveries.add(id); }
    @Override public Map<FoodKey,Long> getEatenCounts() {
        Map<FoodKey,Long> result=new HashMap<>();monotonyMap.forEach((key,entry)->{if(entry.getEatenCount()>0)result.put(key,entry.getEatenCount());});return Map.copyOf(result);
    }
    @Override public boolean isEatenCountExact(FoodKey key) { return monotonyMap.containsKey(key)&&monotonyMap.get(key).isCountExact(); }
    @Override public net.minecraft.world.item.ItemStack foodSample(FoodKey key){var e=monotonyMap.get(key);return e==null?net.minecraft.world.item.ItemStack.EMPTY:e.sample();}
    @Override public void rememberFood(FoodKey key,net.minecraft.world.item.ItemStack stack){getOrCreateEntry(key).sample(stack);}
    @Override public double[] externalNutrition(FoodKey key){var entry=monotonyMap.get(key);return entry==null?null:entry.externalNutrition();}
    @Override public void rememberExternalNutrition(FoodKey key,double[] values){var entry=getOrCreateEntry(key);entry.externalNutrition(values);updateLegacySeed(key,entry);}
    private FoodKey lastEatenKey;
    private com.lumbra.ldiediet.consumption.ConsumptionSnapshot useSession;
    private final Map<String, Float> nutrientsMap = new HashMap<>();
    private String dietSuite = "builtin";
    private boolean importedDietState;

    private Item lastEatenItem = null;
    private long lastTickTime = 0;
    
    private final LinkedList<HistoryEntry> eatingHistory = new LinkedList<>();
    private int historyTotalCount = 0;
    private long mealSequence;
    private final Map<String, Long> rewardCursors = new HashMap<>();
    private boolean legacyRewardsMigration;
    private final com.lumbra.ldiediet.rewards.MobEffectSources mobEffectSources = new com.lumbra.ldiediet.rewards.MobEffectSources();
    
    private final Set<String> permanentRewards = new HashSet<>();
    private final List<ActiveGroupEffect> activeEffects = new ArrayList<>();
    private final Map<String, Integer> consumedHistoryTotals = new HashMap<>();

     
    private MonotonyEntry getOrCreateEntry(FoodKey key) {
        return monotonyMap.computeIfAbsent(key, ignored -> {
            MonotonyEntry inherited = legacyItemSeeds.get(key.itemId());
            if(inherited==null)return new MonotonyEntry();
            var seed=inherited.serializeNBT();seed.remove("eatenCount");seed.remove("countExact");seed.remove("sample");seed.remove("recoveryMeals");seed.remove("externalHunger");seed.remove("externalSaturation");return MonotonyEntry.fromNBT(seed);
        });
    }
    private void updateLegacySeed(FoodKey key, MonotonyEntry entry) {
        if (key.variantId().isEmpty() && legacyItemSeeds.containsKey(key.itemId())) {
            legacyItemSeeds.put(key.itemId(), MonotonyEntry.fromNBT(entry.serializeNBT()));
        }
    }
    @Override public float getMonotony(FoodKey key) {
        MonotonyEntry entry = monotonyMap.get(key);
        if (entry == null) entry = legacyItemSeeds.get(key.itemId());
        return entry == null ? 0 : entry.getMonotony();
    }
    @Override public void setMonotony(FoodKey key, float value) {
        MonotonyEntry entry = getOrCreateEntry(key); entry.setMonotony(value); updateLegacySeed(key, entry);
    }
    @Override public void addMonotony(FoodKey key, float value) {
        MonotonyEntry entry = getOrCreateEntry(key); entry.addMonotony(value); updateLegacySeed(key, entry);
    }
    @Override public int getRecoveryMeals(FoodKey key) {
        var entry=monotonyMap.get(key); return entry==null?0:entry.getRecoveryMeals();
    }
    @Override public void setRecoveryMeals(FoodKey key, int meals) {
        var entry=getOrCreateEntry(key);entry.setRecoveryMeals(meals);updateLegacySeed(key,entry);
    }
    @Override public void limitMonotony(float limit) {
        if (!Float.isFinite(limit) || limit<=0) return;
        monotonyMap.values().forEach(entry -> { if(entry.getMonotony()>limit)entry.setMonotony(limit); });
        legacyItemSeeds.values().forEach(entry -> { if(entry.getMonotony()>limit)entry.setMonotony(limit); });
    }
    @Override public Map<FoodKey, Float> getAllFoodMonotony() {
        Map<FoodKey, Float> values = new HashMap<>();
        monotonyMap.forEach((key, entry) -> values.put(key, entry.getMonotony()));
        return Collections.unmodifiableMap(values);
    }
     
    @Override public float getMonotony(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        float value = legacyItemSeeds.containsKey(id) ? legacyItemSeeds.get(id).getMonotony() : 0;
        for (var entry : monotonyMap.entrySet()) if (entry.getKey().itemId().equals(id)) value = Math.max(value, entry.getValue().getMonotony());
        return value;
    }
    @Override public void setMonotony(Item item, float value) {
        FoodKey key = FoodKey.of(item);
        MonotonyEntry entry = getOrCreateEntry(key); entry.setMonotony(value);
        legacyItemSeeds.put(key.itemId(), MonotonyEntry.fromNBT(entry.serializeNBT()));
        monotonyMap.forEach((variant, data) -> { if (variant.itemId().equals(key.itemId())) data.setMonotony(value); });
    }
    @Override public void addMonotony(Item item, float value) {
        FoodKey key = FoodKey.of(item); MonotonyEntry base = getOrCreateEntry(key);
        monotonyMap.forEach((variant, data) -> { if (variant.itemId().equals(key.itemId())) data.addMonotony(value); });
        legacyItemSeeds.put(key.itemId(), MonotonyEntry.fromNBT(base.serializeNBT()));
    }
    @Override public Map<Item, Float> getAllMonotony() {
        Map<Item, Float> values = new HashMap<>();
        monotonyMap.forEach((key, data) -> {
            if (BuiltInRegistries.ITEM.containsKey(key.itemId())) values.merge(key.item(), data.getMonotony(), Math::max);
        });
        return Collections.unmodifiableMap(values);
    }
    @Override public void clearMonotony() {
        monotonyMap.clear(); legacyItemSeeds.clear(); lastEatenItem = null; lastEatenKey = null;
    }
    @Override public void reduceMonotonyGlobally(float fraction) {
        if (!Float.isFinite(fraction) || fraction <= 0) return;
        float factor = Math.max(0, 1 - fraction);
        monotonyMap.values().forEach(entry -> entry.setMonotony(entry.getMonotony() * factor));
        legacyItemSeeds.values().forEach(entry -> entry.setMonotony(entry.getMonotony() * factor));
    }
    @Override public void recordEaten(FoodKey key, long gameTime) {
        recordEaten(key, gameTime, true);
    }
    @Override public void recordEaten(FoodKey key, long gameTime, boolean resetRecovery) {
        MonotonyEntry entry = getOrCreateEntry(key);
        entry.recordMeal(resetRecovery);entry.setLastEatenGameTime(gameTime); updateLegacySeed(key, entry);
        lastEatenKey = key; lastEatenItem = key.item();
    }
    @Override public void recordEaten(Item item, long gameTime) { recordEaten(FoodKey.of(item), gameTime); }
    @Override @Nullable public Item getLastEatenItem() { return lastEatenItem; }
    @Override public long getLastEatenGameTime(FoodKey key) {
        MonotonyEntry entry = monotonyMap.get(key);
        if (entry == null) entry = legacyItemSeeds.get(key.itemId());
        return entry == null ? 0 : entry.getLastEatenGameTime();
    }
    @Override public long getLastEatenGameTime(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item); long latest = 0;
        for (var entry : monotonyMap.entrySet()) if (entry.getKey().itemId().equals(id)) latest = Math.max(latest, entry.getValue().getLastEatenGameTime());
        return latest;
    }
    @Override public com.lumbra.ldiediet.consumption.ConsumptionSnapshot getUseSession() { return useSession; }
    @Override public void setUseSession(com.lumbra.ldiediet.consumption.ConsumptionSnapshot session) { useSession = session; }

     
    @Override
    public float getNutrientValue(String nutrientId) {
        return nutrientsMap.getOrDefault(nutrientId, 0.0f);
    }

    @Override
    public void setNutrientValue(String nutrientId, float value) {
        if (nutrientId == null || nutrientId.isBlank() || !Float.isFinite(value)) return;
        float clamped = com.lumbra.ldiediet.data.NutritionScaleManager.getInstance().clamp(nutrientId, value);
        if (clamped <= 0.0001f) nutrientsMap.remove(nutrientId);
        else nutrientsMap.put(nutrientId, clamped);
    }

    @Override
    public void addNutrientValue(String nutrientId, float value) {
        setNutrientValue(nutrientId, getNutrientValue(nutrientId) + value);
    }

    @Override
    public Map<String, Float> getAllNutrients() {
        return Collections.unmodifiableMap(nutrientsMap);
    }

    @Override
    public void clearNutrients() {
        nutrientsMap.clear();
    }

     
    @Override public String getDietSuite() { return dietSuite; }
    @Override public void setDietSuite(String suite) { if (suite != null && !suite.isBlank()) dietSuite = suite; }
    @Override public boolean hasImportedDietState() { return importedDietState; }
    @Override public void markImportedDietState() { importedDietState = true; }

    @Override
    public void addHistory(Item item) { addHistory(FoodKey.of(item)); }

    @Override
    public void addHistory(FoodKey key) {
        mealSequence++;
        if (!eatingHistory.isEmpty() && eatingHistory.getFirst().getKey().equals(key)) {
            eatingHistory.getFirst().increment();
        } else {
            eatingHistory.addFirst(new HistoryEntry(key, 1, mealSequence));
        }
        historyTotalCount++;
        
        int maxSize = LDieDietServerConfig.REWARD_HISTORY_LENGTH.get();
        while (historyTotalCount > maxSize && !eatingHistory.isEmpty()) {
            HistoryEntry last = eatingHistory.getLast();
            if (last.getCount() > 1) {
                last.decrement();
                historyTotalCount--;
            } else {
                eatingHistory.removeLast();
                historyTotalCount--;
            }
        }
    }

    @Override
    public List<HistoryEntry> getEatingHistory() {
        return Collections.unmodifiableList(eatingHistory);
    }

    @Override
    public void clearHistory() {
        eatingHistory.clear();
        historyTotalCount = 0;
        consumedHistoryTotals.clear();
        rewardCursors.clear();
    }

     
    @Override
    public void addPermanentReward(String rewardId) {
        String id = GroupIds.canonical(rewardId);
        if (!id.isEmpty()) permanentRewards.add(id);
    }

    @Override
    public void removePermanentReward(String rewardId) {
        permanentRewards.remove(GroupIds.canonical(rewardId));
    }

    @Override
    public boolean hasPermanentReward(String rewardId) {
        return permanentRewards.contains(GroupIds.canonical(rewardId));
    }

    @Override
    public Set<String> getPermanentRewards() {
        return Collections.unmodifiableSet(permanentRewards);
    }

    @Override
    public void clearPermanentRewards() {
        permanentRewards.clear();
    }

     
    @Override
    public void addActiveEffect(ActiveGroupEffect effect) {
        activeEffects.add(effect);
    }

    @Override
    public void removeActiveEffect(ActiveGroupEffect effect) {
        activeEffects.remove(effect);
    }

    @Override
    public List<ActiveGroupEffect> getActiveEffects() {
        return Collections.unmodifiableList(activeEffects);
    }

    @Override
    public void removeActiveEffectsForGroup(String groupId) {
        activeEffects.removeIf(e -> e.getGroupId().equals(GroupIds.canonical(groupId)));
    }

    @Override
    public boolean hasActiveEffectsForGroup(String groupId) {
        for (ActiveGroupEffect e : activeEffects) {
            if (e.isLive() && e.getGroupId().equals(GroupIds.canonical(groupId))) return true;
        }
        return false;
    }

    @Override
    public List<String> cancelEffectsOnGroupActivation(String activatedGroupId) {
        Set<String> changed = new LinkedHashSet<>();
        for (ActiveGroupEffect effect : activeEffects) {
            if (effect.isLive() && com.lumbra.ldiediet.rewards.RewardHandler.shouldCancelOnActivation(effect, activatedGroupId)) {
                effect.cancel(); changed.add(effect.getGroupId());
            }
        }
        for (String group : changed) {
            boolean persistent = activeEffects.stream().anyMatch(e -> e.getGroupId().equals(group) && e.isLive() && e.isPersistent());
            if (!persistent) permanentRewards.remove(group);
            if (!hasActiveEffectsForGroup(group)) {
                setRewardCursor(group, mealSequence);
                activeEffects.stream().filter(e -> e.getGroupId().equals(group)).forEach(ActiveGroupEffect::markActivationClosed);
            }
        }
        return List.copyOf(changed);
    }

    @Override
    public void clearActiveEffects() {
        activeEffects.clear();
    }

    @Override
    public void clearDeathEffects() {
        clearActiveEffects();
    }

     
    @Override
    public int getConsumedHistoryTotal(String groupId) {
        return consumedHistoryTotals.getOrDefault(GroupIds.canonical(groupId), 0);
    }

    @Override
    public void setConsumedHistoryTotal(String groupId, int total) {
        groupId = GroupIds.canonical(groupId);
        if (total <= 0) {
            consumedHistoryTotals.remove(groupId);
        } else {
            consumedHistoryTotals.put(groupId, total);
        }
        setRewardCursor(groupId, total > 0 ? mealSequence : 0);
    }

    @Override public long getMealSequence() { return mealSequence; }
    @Override public long getRewardCursor(String groupId) { return rewardCursors.getOrDefault(GroupIds.canonical(groupId), 0L); }
    @Override public void setRewardCursor(String groupId, long cursor) {
        String id = GroupIds.canonical(groupId);
        if (!id.isEmpty()) rewardCursors.put(id, Math.max(0, Math.min(mealSequence, cursor)));
    }
    @Override public boolean needsRewardMigration() { return legacyRewardsMigration; }
    @Override public void markRewardsMigrated() { legacyRewardsMigration = false; }
    @Override public com.lumbra.ldiediet.rewards.MobEffectSources getMobEffectSources() { return mobEffectSources; }

    @Override
    public int getHistoryTotalCount() {
        return historyTotalCount;
    }

     
     





    @Override
    public void tick(long currentGameTime) {
        lastTickTime = currentGameTime;
    }

    @Override
    public void copyFrom(IFoodTolerance other) {
        if (other != this) deserializeNBT(other.serializeNBT());
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", 7);
        tag.put("discoveries",com.lumbra.ldiediet.util.NBTUtils.writeStringList(new ArrayList<>(discoveries),"id"));
        tag.putLong("mealSequence", mealSequence);
        tag.putBoolean("legacyRewardsMigration", legacyRewardsMigration);
        tag.put("mobEffectSources", mobEffectSources.serializeNBT());
        tag.putString("dietSuite", dietSuite);
        tag.putBoolean("dietStateImported", importedDietState);

        ListTag entriesList = new ListTag();
        for (Map.Entry<FoodKey, MonotonyEntry> entry : monotonyMap.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entry.getKey().write(entryTag);
            entryTag.put("data", entry.getValue().serializeNBT());
            entriesList.add(entryTag);
        }
        tag.put("monotonyEntries", entriesList);
        ListTag seedList = new ListTag();
        legacyItemSeeds.forEach((id, data) -> {
            CompoundTag seed = new CompoundTag(); seed.putString("item", id.toString());
            seed.put("data", data.serializeNBT()); seedList.add(seed);
        });
        tag.put("legacyItemSeeds", seedList);

        if (!nutrientsMap.isEmpty()) {
            CompoundTag nutrientsTag = new CompoundTag();
            for (Map.Entry<String, Float> entry : nutrientsMap.entrySet()) {
                nutrientsTag.putFloat(entry.getKey(), entry.getValue());
            }
            tag.put("nutrients", nutrientsTag);
        }

        if (lastEatenKey != null) {
            tag.putString("lastEatenItem", lastEatenKey.itemId().toString());
            tag.putString("lastEatenVariant", lastEatenKey.variantId());
        }
        tag.putLong("lastTickTime", lastTickTime);

        ListTag historyList = new ListTag();
        for (HistoryEntry entry : eatingHistory) {
            CompoundTag historyTag = new CompoundTag();
            entry.getKey().write(historyTag);
            historyTag.putInt("count", entry.getCount());
            historyTag.putLong("firstSequence", entry.getFirstSequence());
            historyList.add(historyTag);
        }
        tag.put("eatingHistory", historyList);
        tag.putInt("historyTotalCount", historyTotalCount);

        tag.put("permanentRewards", com.lumbra.ldiediet.util.NBTUtils.writeStringList(new ArrayList<>(permanentRewards), "id"));

        ListTag effectsList = new ListTag();
        for (ActiveGroupEffect effect : activeEffects) {
            effectsList.add(effect.serializeNBT());
        }
        tag.put("activeEffects", effectsList);
        CompoundTag cursors = new CompoundTag(); rewardCursors.forEach(cursors::putLong);
        tag.put("rewardCursors", cursors);

        if (!consumedHistoryTotals.isEmpty()) {
            tag.put("consumedHistoryTotals", com.lumbra.ldiediet.util.NBTUtils.writeStringIntMap(consumedHistoryTotals));
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        boolean legacy = tag.getInt("schema_version") < 3;
        boolean legacyVariants = tag.getInt("schema_version") < 4;
        discoveries.clear();discoveries.addAll(com.lumbra.ldiediet.util.NBTUtils.readStringList(tag.getList("discoveries",10),"id"));
        useSession = null;
        legacyItemSeeds.clear();
        legacyRewardsMigration = legacy || tag.getBoolean("legacyRewardsMigration");
        mealSequence = Math.max(0, tag.getLong("mealSequence"));
        mobEffectSources.deserializeNBT(tag.getCompound("mobEffectSources"));
        dietSuite = tag.contains("dietSuite") ? tag.getString("dietSuite") : "builtin";
        importedDietState = tag.getBoolean("dietStateImported");
        monotonyMap.clear();

        if (tag.contains("monotonyEntries")) {
            ListTag entriesList = tag.getList("monotonyEntries", Tag.TAG_COMPOUND);
            for (int i = 0; i < entriesList.size(); i++) {
                CompoundTag entryTag = entriesList.getCompound(i);
                ResourceLocation itemId = ResourceLocation.tryParse(entryTag.getString("item"));
                if (itemId == null) continue;
                if (!itemId.equals(new ResourceLocation("minecraft", "air"))) {
                    FoodKey key = new FoodKey(itemId, entryTag.getString("variant_id"));
                    MonotonyEntry entry = MonotonyEntry.fromNBT(entryTag.getCompound("data"));
                    monotonyMap.put(key, entry);
                    if (legacyVariants) legacyItemSeeds.put(itemId, MonotonyEntry.fromNBT(entry.serializeNBT()));
                }
            }
        }

        if (!legacyVariants) {
            for (Tag raw : tag.getList("legacyItemSeeds", Tag.TAG_COMPOUND)) {
                CompoundTag seed = (CompoundTag) raw;
                ResourceLocation id = ResourceLocation.tryParse(seed.getString("item"));
                if (id != null && !id.equals(new ResourceLocation("minecraft", "air"))) {
                    legacyItemSeeds.put(id, MonotonyEntry.fromNBT(seed.getCompound("data")));
                }
            }
        }
        nutrientsMap.clear();
        if (tag.contains("nutrients", Tag.TAG_COMPOUND)) {
            CompoundTag nutrientsTag = tag.getCompound("nutrients");
            for (String key : nutrientsTag.getAllKeys()) {
                float value = nutrientsTag.getFloat(key);
                if (Float.isFinite(value) && value > 0) nutrientsMap.put(key, value);
            }
        }

        lastEatenItem = null;
        if (tag.contains("lastEatenItem")) {
            ResourceLocation lastItemId = ResourceLocation.tryParse(tag.getString("lastEatenItem"));
            lastEatenItem = lastItemId == null ? null : BuiltInRegistries.ITEM.get(lastItemId);
            if (lastEatenItem == Items.AIR) {
                lastEatenItem = null;
            }
        }

        ResourceLocation savedLast = ResourceLocation.tryParse(tag.getString("lastEatenItem"));
        lastEatenKey = savedLast == null ? null : new FoodKey(savedLast, tag.getString("lastEatenVariant"));
        lastTickTime = tag.getLong("lastTickTime");

        eatingHistory.clear();
        historyTotalCount = 0;
        if (tag.contains("eatingHistory")) {
            ListTag historyList = tag.getList("eatingHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < historyList.size(); i++) {
                CompoundTag historyTag = historyList.getCompound(i);
                ResourceLocation itemId = ResourceLocation.tryParse(historyTag.getString("item"));
                if (itemId == null) continue;
                if (!itemId.equals(new ResourceLocation("minecraft", "air"))) {
                    int count = historyTag.contains("count") ? historyTag.getInt("count") : 1;
                    if (count <= 0) continue;
                    count = Math.min(count, LDieDietServerConfig.REWARD_HISTORY_LENGTH.get());
                    long sequence = historyTag.getLong("firstSequence");
                    eatingHistory.add(new HistoryEntry(new FoodKey(itemId, historyTag.getString("variant_id")), count, sequence));
                    historyTotalCount += count;
                }
            }
        }
        if (legacy || eatingHistory.stream().anyMatch(e -> e.getFirstSequence() <= 0)) {
            long sequence = 0;
            for (int i = eatingHistory.size() - 1; i >= 0; i--) {
                HistoryEntry entry = eatingHistory.get(i);
                eatingHistory.set(i, new HistoryEntry(entry.getKey(), entry.getCount(), sequence + 1));
                sequence += entry.getCount();
            }
            mealSequence = Math.max(mealSequence, sequence);
        } else {
            for (HistoryEntry entry : eatingHistory) mealSequence = Math.max(mealSequence, entry.getLastSequence());
        }

        if(tag.getInt("schema_version")<5) {
            Map<FoodKey,Long> counts=new HashMap<>();for(var meal:eatingHistory)counts.merge(meal.getKey(),(long)meal.getCount(),Long::sum);
            counts.forEach((key,count)->getOrCreateEntry(key).migrateCount(count));
            monotonyMap.values().forEach(entry->{if(entry.getLastEatenGameTime()>0)entry.migrateCount(1);});
        }
        int historyLimit = LDieDietServerConfig.REWARD_HISTORY_LENGTH.get();
        while (historyTotalCount > historyLimit && !eatingHistory.isEmpty()) {
            HistoryEntry oldest = eatingHistory.getLast();
            if (oldest.getCount() > 1) oldest.decrement(); else eatingHistory.removeLast();
            historyTotalCount--;
        }

        permanentRewards.clear();
        if (tag.contains("permanentRewards")) {
            for (String id : com.lumbra.ldiediet.util.NBTUtils.readStringList(tag.getList("permanentRewards", Tag.TAG_COMPOUND), "id")) addPermanentReward(id);
        }

        activeEffects.clear();
        if (tag.contains("activeEffects")) {
            ListTag effectsList = tag.getList("activeEffects", Tag.TAG_COMPOUND);
            for (int i = 0; i < effectsList.size(); i++) {
                try {
                    ActiveGroupEffect effect = ActiveGroupEffect.fromNBT(effectsList.getCompound(i));
                    if (!effect.getGroupId().isEmpty()) activeEffects.add(effect);
                } catch (RuntimeException e) { com.lumbra.ldiediet.LDieDiet.LOGGER.warn("Ignoring damaged saved reward effect", e); }
            }
        }

        consumedHistoryTotals.clear();
        rewardCursors.clear();
        if (tag.contains("consumedHistoryTotals")) {
            Map<String, Integer> old = new HashMap<>();
            com.lumbra.ldiediet.util.NBTUtils.readStringIntMap(tag.getCompound("consumedHistoryTotals"), old);
            old.forEach((id, value) -> {
                String canonical = GroupIds.canonical(id);
                if (!canonical.isEmpty()) consumedHistoryTotals.put(canonical, value);
                if (legacy && value > 0) setRewardCursor(canonical, mealSequence);
            });
        }
        if (!legacy) {
            CompoundTag cursors = tag.getCompound("rewardCursors");
            for (String key : cursors.getAllKeys()) setRewardCursor(key, cursors.getLong(key));
        }
    }
}
