package com.levbu.ldiediet.rewards;

import com.levbu.ldiediet.LDieDiet;
import com.levbu.ldiediet.capability.ActiveGroupEffect;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.HistoryEntry;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.data.FoodGroupDefinition;
import com.levbu.ldiediet.data.FoodGroupManager;
import com.levbu.ldiediet.compat.DietIntegration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.*;

public class RewardHandler {

    private static UUID getHealthModifierUUID(ActiveGroupEffect effect) {
        String key = "HealthBonus_" + effect.getGroupId() + "_" + effect.getEffectId();
        return UUID.nameUUIDFromBytes(key.getBytes());
    }

    public static void checkAndApplyRewards(Player player, IFoodTolerance capability) {
        List<HistoryEntry> history = capability.getEatingHistory();
        FoodGroupManager manager = FoodGroupManager.getInstance();

        Set<String> cancelledThisRound = new HashSet<>();

        for (FoodGroupDefinition group : manager.getAllGroups()) {
            String groupId = group.getId().getPath();

            if (cancelledThisRound.contains(groupId)) continue;

            int consumedTotal = capability.getConsumedHistoryTotal(groupId);
            int matchCount = countMatchedInHistory(history, group, consumedTotal);

            if (group.getThreshold() > 0 && matchCount >= group.getThreshold()) {
                if (!capability.hasActiveEffectsForGroup(groupId)
                        && !capability.hasPermanentReward(groupId)) {

                    Set<String> cancelled = activateGroupReward(player, capability, group);
                    cancelledThisRound.addAll(cancelled);
                }
            }
        }
    }

    private static Set<String> activateGroupReward(Player player, IFoodTolerance capability, FoodGroupDefinition group) {
        String groupId = group.getId().getPath();
        Set<String> cancelledGroups = new HashSet<>();

        // 1. Снимаем активные баффы с игрока
        List<ActiveGroupEffect> currentEffects = new ArrayList<>(capability.getActiveEffects());
        for (ActiveGroupEffect activeEffect : currentEffects) {
            if (activeEffect.shouldCancelOnGroup(groupId)) {
                cancelledGroups.add(activeEffect.getGroupId());
                removeEffect(player, activeEffect);
            }
        }

        // 2. Глубокая очистка данных капабилити (вернет даже те группы, у которых нет баффов)
        List<String> thoroughlyCancelled = capability.cancelEffectsOnGroupActivation(groupId);
        cancelledGroups.addAll(thoroughlyCancelled);

        // 3. Сброс истории для всех отмененных групп
        for (String cancelledGroupId : cancelledGroups) {
            onGroupDeactivated(player, capability, cancelledGroupId);
        }

        // 4. Добавление новых эффектов (с проверкой требований)
        for (FoodGroupDefinition.GroupEffect defEffect : group.getEffects()) {
            boolean meetsRequirement = true;
            for (String reqGroup : defEffect.getRequiresActiveGroups()) {
                if (!capability.hasActiveEffectsForGroup(reqGroup)) {
                    meetsRequirement = false;
                    break;
                }
            }

            if (meetsRequirement) {
                ActiveGroupEffect active = new ActiveGroupEffect(
                        groupId,
                        defEffect.getEffectType(),
                        defEffect.getEffectId(),
                        defEffect.getValue(),
                        defEffect.getAmplifier(),
                        defEffect.getDurationType(),
                        defEffect.getDurationTicks(),
                        defEffect.getIntervalActiveTicks()[0],
                        defEffect.getIntervalActiveTicks()[1],
                        defEffect.getIntervalPauseTicks()[0],
                        defEffect.getIntervalPauseTicks()[1],
                        defEffect.getCancelOnGroups().toArray(new String[0]),
                        defEffect.getDurationMultipliers()
                );
                
                capability.addActiveEffect(active);
                applyEffect(player, active);
            }
        }

        // 5. Достижения
        if (!group.getAdvancement().isEmpty() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            String advPath = group.getAdvancement();
            ResourceLocation advancementId;
            if (advPath.contains(":")) {
                advancementId = new ResourceLocation(advPath);
            } else {
                advancementId = new ResourceLocation("ldiediet", advPath);
            }
            net.minecraft.advancements.Advancement advancement = serverPlayer.server.getAdvancements().getAdvancement(advancementId);
            if (advancement != null) {
                net.minecraft.advancements.AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
                if (!progress.isDone()) {
                    for (String criterion : progress.getRemainingCriteria()) {
                        serverPlayer.getAdvancements().award(advancement, criterion);
                    }
                }
            }
        }

        if (!group.isHideActivationMessage()) {
            player.displayClientMessage(Component.translatable("ldiediet.gui.group_activated", group.getName()), true);
        }

        boolean isPersistent = group.getEffects().stream()
                .anyMatch(e -> e.isUntilDeath() || e.isUntilDeathIntermittent());

        if (isPersistent) {
            capability.addPermanentReward(groupId);
        }

        if (!isPersistent && !capability.hasActiveEffectsForGroup(groupId)) {
            onGroupDeactivated(player, capability, groupId);
        }

        CapabilityHandler.syncToClient(player);
        return cancelledGroups;
    }


    public static void tickActiveEffects(Player player) {
        if (player.level().isClientSide()) return;

        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null) return;

        List<ActiveGroupEffect> effects = new ArrayList<>(cap.getActiveEffects());
        boolean needsSync = false;
        long gameTime = player.level().getGameTime();
        FoodGroupManager manager = FoodGroupManager.getInstance();

        // 1. Динамическая перепроверка требований для перманентных наград
        // (Решает проблему с -ХП, когда условия не были соблюдены при активации)
        if (gameTime % 20 == 0) {
            for (String permGroupId : cap.getPermanentRewards()) {
                FoodGroupDefinition def = manager.getGroup(new ResourceLocation("ldiediet", permGroupId));
                if (def == null) def = manager.getGroup(new ResourceLocation(permGroupId));
                
                if (def != null) {
                    for (FoodGroupDefinition.GroupEffect defEffect : def.getEffects()) {
                        // Если эффект уже активен — пропускаем
                        boolean alreadyInList = effects.stream().anyMatch(e -> 
                            e.getGroupId().equals(permGroupId) && 
                            e.getEffectId().equals(defEffect.getEffectId()) && 
                            e.getEffectType().equals(defEffect.getEffectType())
                        );
                        if (alreadyInList) continue;

                        // Проверяем требования
                        boolean meetsRequirement = true;
                        for (String reqGroup : defEffect.getRequiresActiveGroups()) {
                            if (!cap.hasActiveEffectsForGroup(reqGroup)) {
                                meetsRequirement = false;
                                break;
                            }
                        }

                        if (meetsRequirement) {
                            ActiveGroupEffect active = new ActiveGroupEffect(
                                permGroupId, defEffect.getEffectType(), defEffect.getEffectId(),
                                defEffect.getValue(), defEffect.getAmplifier(), defEffect.getDurationType(),
                                defEffect.getDurationTicks(), defEffect.getIntervalActiveTicks()[0],
                                defEffect.getIntervalActiveTicks()[1], defEffect.getIntervalPauseTicks()[0],
                                defEffect.getIntervalPauseTicks()[1], defEffect.getCancelOnGroups().toArray(new String[0]),
                                defEffect.getDurationMultipliers()
                            );
                            cap.addActiveEffect(active);
                            applyEffect(player, active);
                            effects.add(active); // Чтобы другие эффекты могли на него сослаться в этом же тике
                            needsSync = true;
                        }
                    }
                }
            }
        }

        if (effects.isEmpty() && !needsSync) return;

        List<ActiveGroupEffect> toRemove = new ArrayList<>();
        Set<String> groupsBeforeTick = new HashSet<>();
        for (ActiveGroupEffect effect : effects) {
            groupsBeforeTick.add(effect.getGroupId());
        }

        for (ActiveGroupEffect effect : effects) {
            boolean wasActive = effect.isCurrentlyActive();
            boolean expired = effect.tick();

            // Проверка для history_intermittent
            if (!expired && "history_intermittent".equals(effect.getDurationType()) && gameTime % 20 == 0) {
                FoodGroupDefinition group = manager.getGroup(new ResourceLocation("ldiediet", effect.getGroupId()));
                if (group == null) group = manager.getGroup(new ResourceLocation(effect.getGroupId()));
                
                if (group != null) {
                    int consumedTotal = cap.getConsumedHistoryTotal(effect.getGroupId());
                    int historyCount = countMatchedInHistory(cap.getEatingHistory(), group, consumedTotal);
                    if (historyCount == 0) expired = true;
                }
            }

            if (expired) {
                toRemove.add(effect);
                removeEffect(player, effect);
                needsSync = true;
                continue;
            }

            if (effect.isCurrentlyActive()) {
                ensureApplied(player, effect);
            }

            if (effect.isIntermittent()) {
                boolean isNowActive = effect.isCurrentlyActive();
                if (wasActive != isNowActive) {
                    if (isNowActive) applyEffect(player, effect);
                    else removeEffect(player, effect);
                    needsSync = true;
                }
            }
        }

        if (!toRemove.isEmpty()) {
            for (ActiveGroupEffect removed : toRemove) {
                cap.removeActiveEffectsForGroup(removed.getGroupId());
            }

            for (String groupId : groupsBeforeTick) {
                if (!cap.hasActiveEffectsForGroup(groupId) && !cap.hasPermanentReward(groupId)) {
                    onGroupDeactivated(player, cap, groupId);
                }
            }
        }
        
        if (needsSync) {
            CapabilityHandler.syncToClient(player);
        }
    }


    private static void ensureApplied(Player player, ActiveGroupEffect effect) {
        switch (effect.getEffectType()) {
            case "mob_effect" -> {
                ResourceLocation id = new ResourceLocation(effect.getEffectId());
                MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(id);
                if (mobEffect != null) {
                    MobEffectInstance active = player.getEffect(mobEffect);
                    if (active == null || (active.getDuration() > 0 && active.getDuration() < 100)) {
                        applyEffect(player, effect);
                    }
                }
            }
            case "health" -> {
                AttributeInstance healthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
                if (healthAttribute != null) {
                    if (healthAttribute.getModifier(getHealthModifierUUID(effect)) == null) {
                        applyEffect(player, effect);
                    }
                }
            }
        }
    }

   
    private static void applyEffect(Player player, ActiveGroupEffect effect) {
        switch (effect.getEffectType()) {
            case "mob_effect" -> applyMobEffect(player, effect);
            case "health" -> applyHealthBonus(player, effect);
            case "diet" -> applyDietBonus(player, effect);
            case "overlay" -> {

            
            }
        }
    }

    public static void removeEffect(Player player, ActiveGroupEffect effect) {
        switch (effect.getEffectType()) {
            case "mob_effect" -> removeMobEffect(player, effect);
            case "health" -> removeHealthBonus(player, effect);
            case "diet" -> { /* One-time adjustments don't need removal for now */ }
            case "overlay" -> { /* Handled on client by sync (lack of effect removes it) */ }
        }
    }

    // ==================== ПРИМЕНЕНИЕ ЭФФЕКТОВ ====================

    private static void applyMobEffect(Player player, ActiveGroupEffect effect) {
        ResourceLocation id = new ResourceLocation(effect.getEffectId());
        MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(id);
        if (mobEffect == null) return;

        IFoodTolerance capability = CapabilityHandler.get(player);
        double multiplier = (capability != null) ? effect.getAppliedMultiplier(capability) : 1.0;

        int duration;
        if (effect.isFixed()) {
            duration = effect.getRemainingTicks();
        } else if (effect.isUntilDeath()) {
            duration = 1000000;
        } else {
            duration = 600;
        }

        if (duration > 0 && duration < 500000) {
            duration = (int) Math.round(duration * multiplier);
            duration += 1;
        }

        int amplifiedLevel = (int) Math.max(0, Math.round((effect.getAmplifier() + 1) * multiplier + 0.05) - 1);

        MobEffectInstance instance = new MobEffectInstance(
                mobEffect, duration, amplifiedLevel, false, false, true);
        player.addEffect(instance);
    }

    public static void removeMobEffect(Player player, ActiveGroupEffect effect) {
        ResourceLocation id = new ResourceLocation(effect.getEffectId());
        MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(id);
        if (mobEffect != null) {
            player.removeEffect(mobEffect);
        }
    }

    private static void applyHealthBonus(Player player, ActiveGroupEffect effect) {
        AttributeInstance healthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttribute == null) return;

        UUID modifierUuid = getHealthModifierUUID(effect);

        healthAttribute.removeModifier(modifierUuid);

        IFoodTolerance capability = CapabilityHandler.get(player);
        double multiplier = (capability != null) ? effect.getAppliedMultiplier(capability) : 1.0;
        double finalValue = effect.getValue() * multiplier;

        AttributeModifier modifier = new AttributeModifier(
                modifierUuid,
                "ldiediet_health_" + effect.getGroupId(),
                finalValue,
                AttributeModifier.Operation.ADDITION
        );
        healthAttribute.addTransientModifier(modifier);
    }

    public static void removeHealthBonus(Player player, ActiveGroupEffect effect) {
        AttributeInstance healthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.removeModifier(getHealthModifierUUID(effect));
        }
    }

    private static void applyDietBonus(Player player, ActiveGroupEffect effect) {
        if (!LDieDiet.isDietLoaded()) return;

        IFoodTolerance capability = CapabilityHandler.get(player);
        double multiplier = (capability != null) ? effect.getAppliedMultiplier(capability) : 1.0;
        double finalValue = effect.getValue() * multiplier;

        String dietGroup = effect.getEffectId().replace("diet:", "");

        if ("all".equalsIgnoreCase(dietGroup)) {
            // Применяем ко всем группам игрока (через Diet API)
            for (com.illusivesoulworks.diet.api.type.IDietGroup group :
                    com.illusivesoulworks.diet.api.DietApi.getInstance().getGroups()) {
                String groupName = group.getName().toLowerCase();
                float current = DietIntegration.getDietValue(player, groupName);
                DietIntegration.setDietValue(player, groupName, (float) (current + finalValue));
            }
        } else {
            float current = DietIntegration.getDietValue(player, dietGroup);
            DietIntegration.setDietValue(player, dietGroup, (float) (current + finalValue));
        }
    }

    // ==================== УТИЛИТЫ ====================
    public static int countMatchedInHistory(List<HistoryEntry> history, FoodGroupDefinition group, int consumedCount) {
        int totalMatched = 0;
        Set<String> uniqueMatched = new HashSet<>();

        if (group.isConsecutive()) {
            for (HistoryEntry entry : history) {
                String itemId = BuiltInRegistries.ITEM.getKey(entry.getItem()).toString();
                if (group.containsItem(itemId)) {
                    if (group.isRequireUnique()) {

                        if (uniqueMatched.add(itemId)) {
                            totalMatched++;
                        }

                    } else {
                        totalMatched += entry.getCount();
                    }
                } else {

                    break;
                }
            }
        } else {

            for (HistoryEntry entry : history) {
                String itemId = BuiltInRegistries.ITEM.getKey(entry.getItem()).toString();
                if (group.containsItem(itemId)) {
                    if (group.isRequireUnique()) {
                        if (uniqueMatched.add(itemId)) {
                            totalMatched++;
                        }
                    } else {
                        totalMatched += entry.getCount();
                    }
                }
            }
        }
        

        return Math.max(0, totalMatched - consumedCount);
    }

    private static void onGroupDeactivated(Player player, IFoodTolerance cap, String groupId) {

        cap.removePermanentReward(groupId);

        FoodGroupManager manager = FoodGroupManager.getInstance();
        FoodGroupDefinition group = manager.getGroup(new ResourceLocation("ldiediet", groupId));
        if (group == null) {
            group = manager.getGroup(new ResourceLocation(groupId));
        }

        if (group != null) {

            int totalInHistory = countMatchedInHistory(cap.getEatingHistory(), group, 0);

            cap.setConsumedHistoryTotal(groupId, totalInHistory);
           
        }
    }
}
