package com.levbu.ldiediet.capability;

import com.levbu.ldiediet.LDieDietServerConfig;
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

    private final Map<Item, FoodToleranceEntry> toleranceMap = new HashMap<>();
    private Item lastEatenItem = null;
    private long lastTickTime = 0;
    private final LinkedList<HistoryEntry> eatingHistory = new LinkedList<>();
    private int historyTotalCount = 0;
    private final Set<String> permanentRewards = new HashSet<>();
    private final List<ActiveGroupEffect> activeEffects = new ArrayList<>();
    private final Map<String, Integer> consumedHistoryTotals = new HashMap<>();

    @Override
    public void clearHistory() {
        eatingHistory.clear();
        historyTotalCount = 0;
        consumedHistoryTotals.clear();
    }

    @Override
    public void addPermanentReward(String rewardId) {
        permanentRewards.add(rewardId);
    }

    @Override
    public void removePermanentReward(String rewardId) {
        permanentRewards.remove(rewardId);
    }

    @Override
    public boolean hasPermanentReward(String rewardId) {
        return permanentRewards.contains(rewardId);
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
    public FoodToleranceEntry getEntry(Item item) {
        return toleranceMap.computeIfAbsent(item, k -> new FoodToleranceEntry());
    }

    @Override
    @Nullable
    public FoodToleranceEntry getEntryOrNull(Item item) {
        return toleranceMap.get(item);
    }

    @Override
    public void recordEaten(Item item, long gameTime) {
        // Записываем факт поедания
        FoodToleranceEntry entry = getEntry(item);
        entry.incrementEaten();
        entry.setLastEatenGameTime(gameTime);
        lastEatenItem = item;
    }

    @Override
    @Nullable
    public Item getLastEatenItem() {
        return lastEatenItem;
    }

    @Override
    public void addHistory(Item item) {
        if (!eatingHistory.isEmpty() && eatingHistory.getFirst().getItem() == item) {
            eatingHistory.getFirst().increment();
        } else {
            eatingHistory.addFirst(new HistoryEntry(item, 1));
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
    public void tick(long currentGameTime) {

        if (currentGameTime - lastTickTime < 1200)
            return;
        lastTickTime = currentGameTime;

        int baseRecoveryTicks = LDieDietServerConfig.RECOVERY_TIME_TICKS.get();

       
        double varietyMultiplier = getVarietyRecoveryMultiplier();

    
        Iterator<Map.Entry<Item, FoodToleranceEntry>> iterator = toleranceMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Item, FoodToleranceEntry> mapEntry = iterator.next();
            FoodToleranceEntry entry = mapEntry.getValue();
            // Учитываем множитель diet групп для каждого продукта
            double dietMultiplier = entry.getRecoveryTimeMultiplier();
            int effectiveRecoveryTicks = (int) (baseRecoveryTicks * dietMultiplier / varietyMultiplier);
            if (entry.tryRecover(currentGameTime, effectiveRecoveryTicks, mapEntry.getKey())) {
                iterator.remove();
            }
        }
    }

    // ==================== ИСТОРИЯ ПОТРЕБЛЕНИЯ ДЛЯ ГРУПП ====================

    @Override
    public int getConsumedHistoryTotal(String groupId) {
        return consumedHistoryTotals.getOrDefault(groupId, 0);
    }

    @Override
    public void setConsumedHistoryTotal(String groupId, int total) {
        if (total <= 0) {
            consumedHistoryTotals.remove(groupId);
        } else {
            consumedHistoryTotals.put(groupId, total);
        }
    }

    @Override
    public int getHistoryTotalCount() {
        return historyTotalCount;
    }

    // ==================== АКТИВНЫЕ ЭФФЕКТЫ ГРУПП ====================

    @Override
    public void addActiveEffect(ActiveGroupEffect effect) {
        activeEffects.add(effect);
    }

    @Override
    public List<ActiveGroupEffect> getActiveEffects() {
        return Collections.unmodifiableList(activeEffects);
    }

    @Override
    public void removeActiveEffectsForGroup(String groupId) {
        activeEffects.removeIf(e -> e.getGroupId().equals(groupId));
    }

    @Override
    public boolean hasActiveEffectsForGroup(String groupId) {
        for (ActiveGroupEffect e : activeEffects) {
            if (e.getGroupId().equals(groupId)) return true;
        }
        return false;
    }

    @Override
    public java.util.List<String> cancelEffectsOnGroupActivation(String activatedGroupId) {
        java.util.List<String> canceledGroups = new java.util.ArrayList<>();
        
        // Сначала удаляем из активных баффов
        activeEffects.removeIf(e -> {
            if (e.shouldCancelOnGroup(activatedGroupId)) {
                if (!canceledGroups.contains(e.getGroupId())) {
                    canceledGroups.add(e.getGroupId());
                }
                return true;
            }
            return false;
        });

        // Теперь проверяем ПЕРМАНЕНТНЫЕ награды, даже если у них нет активных баффов прямо сейчас
        // (например, из-за невыполненных требований requires_active_groups)
        java.util.List<String> allPermanents = new java.util.ArrayList<>(permanentRewards);
        com.levbu.ldiediet.data.FoodGroupManager manager = com.levbu.ldiediet.data.FoodGroupManager.getInstance();
        
        for (String permGroupId : allPermanents) {
            if (canceledGroups.contains(permGroupId)) {
                permanentRewards.remove(permGroupId);
                continue;
            }

            com.levbu.ldiediet.data.FoodGroupDefinition def = manager.getGroup(new net.minecraft.resources.ResourceLocation("ldiediet", permGroupId));
            if (def == null) def = manager.getGroup(new net.minecraft.resources.ResourceLocation(permGroupId));

            if (def != null) {
                boolean shouldCancel = false;
                for (com.levbu.ldiediet.data.FoodGroupDefinition.GroupEffect defEffect : def.getEffects()) {
                    java.util.List<String> cancelList = defEffect.getCancelOnGroups();
                    if (cancelList != null && cancelList.contains(activatedGroupId)) {
                        shouldCancel = true;
                        break;
                    }
                }
                if (shouldCancel) {
                    permanentRewards.remove(permGroupId);
                    canceledGroups.add(permGroupId);
                }
            }
        }
        
        return canceledGroups;
    }

    @Override
    public void clearActiveEffects() {
        activeEffects.clear();
    }

    @Override
    public void clearDeathEffects() {
        clearActiveEffects();
    }

    // ==================== ОБЩЕЕ ====================

    @Override
    public double getVarietyRecoveryMultiplier() {
        int varietyCount = 0;
        for (Map.Entry<Item, FoodToleranceEntry> mapEntry : toleranceMap.entrySet()) {
            if (mapEntry.getValue().calculateStage(mapEntry.getKey()) >= 1) {
                varietyCount++;
            }
        }
        double bonusPerFood = LDieDietServerConfig.VARIETY_RECOVERY_BONUS_PER_FOOD.get();
        return 1.0 + (bonusPerFood * varietyCount);
    }

    @Override
    public void copyFrom(IFoodTolerance other) {
        if (other instanceof FoodToleranceCapability otherCap) {
            toleranceMap.clear();
            for (Map.Entry<Item, FoodToleranceEntry> entry : otherCap.toleranceMap.entrySet()) {
                toleranceMap.put(entry.getKey(), FoodToleranceEntry.fromNBT(entry.getValue().serializeNBT()));
            }
            lastEatenItem = otherCap.lastEatenItem;
            lastTickTime = otherCap.lastTickTime;
            eatingHistory.clear();
            for (HistoryEntry entry : otherCap.eatingHistory) {
                eatingHistory.add(new HistoryEntry(entry.getItem(), entry.getCount()));
            }
            historyTotalCount = otherCap.historyTotalCount;

            // Копируем активные эффекты
            activeEffects.clear();
            for (ActiveGroupEffect effect : otherCap.activeEffects) {
                activeEffects.add(ActiveGroupEffect.fromNBT(effect.serializeNBT()));
            }

            permanentRewards.clear();
            permanentRewards.addAll(otherCap.permanentRewards);

            consumedHistoryTotals.clear();
            consumedHistoryTotals.putAll(otherCap.consumedHistoryTotals);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        ListTag entriesList = new ListTag();
        for (Map.Entry<Item, FoodToleranceEntry> entry : toleranceMap.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("item", BuiltInRegistries.ITEM.getKey(entry.getKey()).toString());
            entryTag.put("data", entry.getValue().serializeNBT());
            entriesList.add(entryTag);
        }
        tag.put("entries", entriesList);

        if (lastEatenItem != null) {
            tag.putString("lastEatenItem", BuiltInRegistries.ITEM.getKey(lastEatenItem).toString());
        }
        tag.putLong("lastTickTime", lastTickTime);

        // Сериализация истории
        ListTag historyList = new ListTag();
        for (HistoryEntry entry : eatingHistory) {
            CompoundTag historyTag = new CompoundTag();
            historyTag.putString("item", BuiltInRegistries.ITEM.getKey(entry.getItem()).toString());
            historyTag.putInt("count", entry.getCount());
            historyList.add(historyTag);
        }
        tag.put("eatingHistory", historyList);
        tag.putInt("historyTotalCount", historyTotalCount);

        // Сериализация постоянных наград
        ListTag rewardsList = new ListTag();
        for (String rewardId : permanentRewards) {
            CompoundTag rewardTag = new CompoundTag();
            rewardTag.putString("id", rewardId);
            rewardsList.add(rewardTag);
        }
        tag.put("permanentRewards", rewardsList);

        // Сериализация активных эффектов
        ListTag effectsList = new ListTag();
        for (ActiveGroupEffect effect : activeEffects) {
            effectsList.add(effect.serializeNBT());
        }
        tag.put("activeEffects", effectsList);

        // Сериализация потреблённой истории
        if (!consumedHistoryTotals.isEmpty()) {
            CompoundTag consumedTag = new CompoundTag();
            for (Map.Entry<String, Integer> entry : consumedHistoryTotals.entrySet()) {
                consumedTag.putInt(entry.getKey(), entry.getValue());
            }
            tag.put("consumedHistoryTotals", consumedTag);
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        toleranceMap.clear();

        ListTag entriesList = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entriesList.size(); i++) {
            CompoundTag entryTag = entriesList.getCompound(i);
            ResourceLocation itemId = new ResourceLocation(entryTag.getString("item"));
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item != Items.AIR) {
                toleranceMap.put(item, FoodToleranceEntry.fromNBT(entryTag.getCompound("data")));
            }
        }

        if (tag.contains("lastEatenItem")) {
            ResourceLocation lastItemId = new ResourceLocation(tag.getString("lastEatenItem"));
            lastEatenItem = BuiltInRegistries.ITEM.get(lastItemId);
            if (lastEatenItem == Items.AIR) {
                lastEatenItem = null;
            }
        }

        lastTickTime = tag.getLong("lastTickTime");

        // Десериализация истории
        eatingHistory.clear();
        historyTotalCount = 0;
        if (tag.contains("eatingHistory")) {
            ListTag historyList = tag.getList("eatingHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < historyList.size(); i++) {
                CompoundTag historyTag = historyList.getCompound(i);
                ResourceLocation itemId = new ResourceLocation(historyTag.getString("item"));
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != Items.AIR) {
                    int count = historyTag.contains("count") ? historyTag.getInt("count") : 1;
                    eatingHistory.add(new HistoryEntry(item, count));
                    historyTotalCount += count;
                }
            }
        } else if (tag.contains("historyTotalCount")) {
             // Фолбэк на всякий случай
             historyTotalCount = tag.getInt("historyTotalCount");
        }

        // Десериализация постоянных наград
        permanentRewards.clear();
        if (tag.contains("permanentRewards")) {
            ListTag rewardsList = tag.getList("permanentRewards", Tag.TAG_COMPOUND);
            for (int i = 0; i < rewardsList.size(); i++) {
                permanentRewards.add(rewardsList.getCompound(i).getString("id"));
            }
        }

        // Десериализация активных эффектов
        activeEffects.clear();
        if (tag.contains("activeEffects")) {
            ListTag effectsList = tag.getList("activeEffects", Tag.TAG_COMPOUND);
            for (int i = 0; i < effectsList.size(); i++) {
                activeEffects.add(ActiveGroupEffect.fromNBT(effectsList.getCompound(i)));
            }
        }

        // Десериализация потреблённой истории
        consumedHistoryTotals.clear();
        if (tag.contains("consumedHistoryTotals")) {
            CompoundTag consumedTag = tag.getCompound("consumedHistoryTotals");
            for (String key : consumedTag.getAllKeys()) {
                consumedHistoryTotals.put(key, consumedTag.getInt(key));
            }
        }
    }
}
