package com.lumbra.ldiediet.rewards;

import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.util.GroupIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RewardHandler {
    private RewardHandler() {}
    public static Set<String> activatedGroups(IFoodTolerance cap) {
        Set<String> result = new LinkedHashSet<>(cap.getPermanentRewards());
        for (ActiveGroupEffect effect : cap.getActiveEffects()) if (effect.isLive()) result.add(effect.getGroupId());
        return Set.copyOf(result);
    }
    public static void checkAndApplyRewards(Player player, IFoodTolerance cap) {
        if (player.level().isClientSide) return;
        migrateLegacyRewards(player, cap);
        closeEndedActivations(cap);
        Set<String> cancelledThisMeal = new HashSet<>();
        List<FoodGroupDefinition> groups = FoodGroupManager.getInstance().getAllGroups().stream()
                .sorted(Comparator.comparing(g -> g.getId().toString())).toList();
        for (FoodGroupDefinition group : groups) {
            String id = group.getId().toString();
            if (!cancelledThisMeal.contains(id) && !activatedGroups(cap).contains(id) && group.getThreshold() > 0
                    && countEligibleHistory(cap.getEatingHistory(), group, cap.getRewardCursor(id)) >= group.getThreshold()) {
                cancelledThisMeal.addAll(activateGroupReward(player, cap, group));
            }
        }
        reconcileApplied(player, cap);
    }

     
    public static Set<String> activateGroupReward(Player player, IFoodTolerance cap, FoodGroupDefinition group) {
        var attempt=new com.lumbra.ldiediet.api.DietEvents.GroupAttempt(player,group);
        if(com.lumbra.ldiediet.api.DietEvents.post(attempt))return Set.of();
        migrateLegacyRewards(player, cap);
        String id = group.getId().toString();
        Set<String> before = activatedGroups(cap);
        Set<String> changed = new LinkedHashSet<>();
        for (ActiveGroupEffect effect : cap.getActiveEffects()) {
            if (effect.isLive() && shouldCancelOnActivation(effect, id)) {
                effect.cancel(); changed.add(effect.getGroupId());
            }
        }
        for (String cancelled : changed) reconcileActivation(cap, cancelled);
        cap.removeActiveEffectsForGroup(id);
        cap.removePermanentReward(id);
        int index = 0;
        for (FoodGroupDefinition.GroupEffect definition : attempt.getEffects()) {
            int slot=index++;ActiveGroupEffect effect = ActiveGroupEffect.waiting(id, slot, definition);
            if(attempt.isEffectCancelled(slot))effect.cancel();
            else if (canGrant(player, id, definition, before)) effect.start(before, player.level().getGameTime());
            else if (effect.isFixed()) effect.expire();
            cap.addActiveEffect(effect);
        }
        reconcileActivation(cap, id);
        if (!group.getAdvancement().isEmpty() && player instanceof ServerPlayer serverPlayer) {
            ResourceLocation advancementId = ResourceLocation.tryParse(GroupIds.canonical(group.getAdvancement(), group.getId().getNamespace()));
            var advancement = advancementId == null ? null : serverPlayer.server.getAdvancements().getAdvancement(advancementId);
            if (advancement != null) {
                var progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
                for (String criterion : progress.getRemainingCriteria()) serverPlayer.getAdvancements().award(advancement, criterion);
            }
        }
        if (cap.getActiveEffects().stream().anyMatch(e->e.getGroupId().equals(id)&&e.isCurrentlyActive())) com.lumbra.ldiediet.api.DietEvents.discover(player,cap,"group:"+id);
        if (!group.isHideActivationMessage() && group.isKnown(cap)) player.displayClientMessage(
                Component.translatable("ldiediet.gui.group_activated", group.displayName()), true);
        reconcileApplied(player, cap);
        CapabilityHandler.syncToClient(player);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.lumbra.ldiediet.api.GroupRewardEvent(player,group.getId()));
        return Set.copyOf(changed);
    }

    private static boolean canGrant(Player player, String group, FoodGroupDefinition.GroupEffect definition, Set<String> before) {
        for (String required : definition.getRequiresActiveGroups()) {
            if (!before.contains(GroupIds.canonical(required, GroupIds.namespace(group)))) return false;
        }
        return definition.getConditions() == null || definition.getConditions().evaluate(player);
    }

    public static void tickActiveEffects(Player player) {
        if (player.level().isClientSide) return;
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null) return;
        migrateLegacyRewards(player, cap);
        boolean sync = timeForRetry(player) && ensurePersistentSlots(cap);
        Set<String> affected = new LinkedHashSet<>();
        long time = player.level().getGameTime();
        Set<String> before = activatedGroups(cap);
        for (ActiveGroupEffect effect : cap.getActiveEffects()) {
            String groupId = effect.getGroupId();
            affected.add(groupId);
            if (!effect.isLive()) continue;
            FoodGroupDefinition group = definition(groupId);
            if (group == null) { effect.cancel(); sync = true; continue; }
            int slot = effect.getDefinitionIndex();
            if (slot < 0 || slot >= group.getEffects().size()
                    || !group.getEffects().get(slot).getEffectType().equals(effect.getEffectType())
                    || !group.getEffects().get(slot).getEffectId().equals(effect.getEffectId())) {
                effect.cancel(); sync = true; continue;
            }
            if ("history_intermittent".equals(effect.getDurationType()) && time % 20 == 0
                    && countEligibleHistory(cap.getEatingHistory(), group, cap.getRewardCursor(groupId)) < group.getThreshold()) {
                effect.expire(); sync = true; continue;
            }
            var definition = group.getEffects().get(slot);
            effect.setConditionsMet(!definition.isContinuousConditions() || canGrant(player,groupId,definition,before));
            boolean started = false;
            if (effect.getState() == ActiveGroupEffect.State.WAITING && time % 20 == 0) {
                int index = effect.getDefinitionIndex();
                if (index >= 0 && index < group.getEffects().size()
                        && canGrant(player, groupId, group.getEffects().get(index), before)) {
                    effect.start(before, time); started = true; sync = true;
                }
            }
            ActiveGroupEffect.State state = effect.getState();
            if (!started) effect.tick(time);
            if (state != effect.getState()) sync = true;
        }
        for (String group : affected) reconcileActivation(cap, group);
        reconcileApplied(player, cap);
        if (sync) CapabilityHandler.syncToClient(player);
    }

    private static void reconcileActivation(IFoodTolerance cap, String group) {
        boolean persistent = cap.getActiveEffects().stream()
                .anyMatch(e -> e.getGroupId().equals(group) && e.isLive() && e.isPersistent());
        if (persistent) cap.addPermanentReward(group); else cap.removePermanentReward(group);
        if (!cap.hasActiveEffectsForGroup(group)) {
            cap.removePermanentReward(group);
            var records = cap.getActiveEffects().stream().filter(e -> e.getGroupId().equals(group)).toList();
            if (records.isEmpty() || records.stream().anyMatch(e -> !e.isActivationClosed())) {
                cap.setRewardCursor(group, cap.getMealSequence());
                records.forEach(ActiveGroupEffect::markActivationClosed);
            }
        }
    }
    private static boolean timeForRetry(Player player) { return player.level().getGameTime() % 20 == 0; }
    private static boolean ensurePersistentSlots(IFoodTolerance cap) {
        boolean changed = false;
        for (String id : Set.copyOf(cap.getPermanentRewards())) {
            FoodGroupDefinition group = definition(id);
            if (group == null) continue;
            Set<Integer> present = new HashSet<>();
            cap.getActiveEffects().stream().filter(e -> e.getGroupId().equals(id)).forEach(e -> present.add(e.getDefinitionIndex()));
            for (int i = 0; i < group.getEffects().size(); i++) {
                if (present.contains(i)) continue;
                ActiveGroupEffect record = ActiveGroupEffect.waiting(id, i, group.getEffects().get(i));
                if (!record.isPersistent()) record.expire();
                cap.addActiveEffect(record); changed = true;
            }
        }
        return changed;
    }
    public static boolean shouldCancelOnActivation(ActiveGroupEffect effect, String activated) {
        FoodGroupDefinition group = definition(effect.getGroupId());
        int slot = effect.getDefinitionIndex();
        if (group != null && slot >= 0 && slot < group.getEffects().size()) {
            var definition = group.getEffects().get(slot);
            if (definition.getEffectType().equals(effect.getEffectType()) && definition.getEffectId().equals(effect.getEffectId())) {
                return definition.getCancelOnGroups().stream().anyMatch(id -> GroupIds.canonical(id, GroupIds.namespace(effect.getGroupId()))
                        .equals(GroupIds.canonical(activated)));
            }
        }
        return effect.shouldCancelOnGroup(activated);
    }
    private static void closeEndedActivations(IFoodTolerance cap) {
        cap.getActiveEffects().stream().map(ActiveGroupEffect::getGroupId).distinct().toList()
                .forEach(group -> reconcileActivation(cap, group));
    }
    public static void onDeath(IFoodTolerance cap, boolean resetGroups) {
        Set<String> affected = new HashSet<>();
        for (ActiveGroupEffect effect : cap.getActiveEffects()) {
            if (resetGroups || effect.isPersistent()) { effect.expire(); affected.add(effect.getGroupId()); }
        }
        if (resetGroups) {
            affected.addAll(cap.getPermanentRewards());
            FoodGroupManager.getInstance().getAllGroups().forEach(group -> affected.add(group.getId().toString()));
            cap.clearPermanentRewards();
        }
        for (String group : affected) reconcileActivation(cap, group);
    }

     
    private static void migrateLegacyRewards(Player player, IFoodTolerance cap) {
        if (!cap.needsRewardMigration() && cap.getActiveEffects().stream().noneMatch(e -> e.getDefinitionIndex() < 0)) return;
        cap.getMobEffectSources().migrateLegacy(player, cap.getActiveEffects());
        for (String id : activatedGroups(cap)) {
            FoodGroupDefinition group = definition(id);
            if (group == null) continue;
            Set<Integer> used = new HashSet<>();
            for (ActiveGroupEffect effect : cap.getActiveEffects()) {
                if (!effect.getGroupId().equals(id)) continue;
                if (effect.getDefinitionIndex() >= 0) { used.add(effect.getDefinitionIndex()); continue; }
                for (int i = 0; i < group.getEffects().size(); i++) {
                    var candidate = group.getEffects().get(i);
                    if (!used.contains(i) && candidate.getEffectType().equals(effect.getEffectType())
                            && candidate.getEffectId().equals(effect.getEffectId())
                            && candidate.getValue() == effect.getValue() && candidate.getAmplifier() == effect.getAmplifier()) {
                        effect.migrateDefinitionIndex(i); used.add(i); break;
                    }
                }
                 
                if (effect.getDefinitionIndex() < 0) {
                    for (int i = 0; i < group.getEffects().size(); i++) {
                        var candidate = group.getEffects().get(i);
                        if (!used.contains(i) && candidate.getEffectType().equals(effect.getEffectType())
                                && candidate.getEffectId().equals(effect.getEffectId())
                                && candidate.getDurationType().equals(effect.getDurationType())) {
                            effect.migrateDefinitionIndex(i); used.add(i); break;
                        }
                    }
                }
            }
            if (cap.hasPermanentReward(id)) {
                for (int i = 0; i < group.getEffects().size(); i++) {
                    if (used.contains(i)) continue;
                    var record = ActiveGroupEffect.waiting(id, i, group.getEffects().get(i));
                    if (!record.isPersistent()) record.expire();
                    cap.addActiveEffect(record);
                }
            }
        }
        cap.markRewardsMigrated();
    }

    public static void reconcileApplied(Player player, IFoodTolerance cap) {
        Set<UUID> desiredModifiers = new HashSet<>();
        Set<String> desiredMobSources = new HashSet<>();
        for (ActiveGroupEffect effect : cap.getActiveEffects()) {
            if (!effect.isCurrentlyActive()) continue;
            com.lumbra.ldiediet.api.DietEvents.discover(player,cap,"group:"+effect.getGroupId());
            if (effect.isMobEffect()) {
                ResourceLocation id = ResourceLocation.tryParse(effect.getEffectId());
                var mob = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
                if (mob != null && effect.mobDuration() != 0) {
                    String source = effect.sourceId(); desiredMobSources.add(source);
                    cap.getMobEffectSources().put(player, source, new MobEffectInstance(mob,
                            effect.mobDuration(), effect.getAmplifier(), false, false, true));
                }
            } else if (effect.isHealth() || "attribute".equals(effect.getEffectType())) {
                Attribute attribute = effect.isHealth() ? Attributes.MAX_HEALTH : attribute(effect.getEffectId());
                AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
                if (instance == null) continue;
                UUID id = uuid(effect); desiredModifiers.add(id);
                AttributeModifier current = instance.getModifier(id);
                if (current == null || current.getAmount() != effect.getValue() || current.getOperation() != effect.getOperation()) {
                    instance.removeModifier(id);
                    instance.addTransientModifier(new AttributeModifier(id, "ldiediet_reward_" + effect.sourceId(),
                            effect.getValue(), effect.getOperation()));
                }
            }
        }
        cap.getMobEffectSources().removePrefixExcept("reward:", desiredMobSources);
        for (Attribute attribute : net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                String name = modifier.getName();
                if ((name.startsWith("ldiediet_reward_") && !desiredModifiers.contains(modifier.getId()))
                        || name.startsWith("ldiediet_health_") || name.startsWith("ldiediet_attr_")) {
                    instance.removeModifier(modifier.getId());
                }
            }
        }
        cap.getMobEffectSources().reconcile(player);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

     
    public static void removeEffect(Player player, ActiveGroupEffect effect) {
        effect.cancel();
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
            reconcileActivation(cap, effect.getGroupId());
            cap.getMobEffectSources().remove(effect.sourceId());
            reconcileApplied(player, cap);
        } else {
            if (effect.isHealth()) removeHealthBonus(player, effect);
            else if ("attribute".equals(effect.getEffectType())) removeAttributeBonus(player, effect);
        }
    }
    public static void removeMobEffect(Player player, ActiveGroupEffect effect) { removeEffect(player, effect); }
    public static void removeHealthBonus(Player player, ActiveGroupEffect effect) {
        var instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance != null) instance.removeModifier(uuid(effect));
    }
    public static void removeAttributeBonus(Player player, ActiveGroupEffect effect) {
        var attr = attribute(effect.getEffectId());
        var instance = attr == null ? null : player.getAttribute(attr);
        if (instance != null) instance.removeModifier(uuid(effect));
    }
    private static UUID uuid(ActiveGroupEffect effect) {
        return UUID.nameUUIDFromBytes(("ldiediet:" + effect.sourceId() + ":" + effect.getEffectType()
                + ":" + effect.getEffectId()).getBytes(StandardCharsets.UTF_8));
    }
    private static Attribute attribute(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id == null ? null : BuiltInRegistries.ATTRIBUTE.get(id);
    }
    private static FoodGroupDefinition definition(String id) {
        ResourceLocation location = ResourceLocation.tryParse(GroupIds.canonical(id));
        return location == null ? null : FoodGroupManager.getInstance().getGroup(location);
    }
    public static int countEligibleHistory(List<HistoryEntry> history, FoodGroupDefinition group, long cursor) {
        int count = 0; Set<com.lumbra.ldiediet.data.FoodKey> unique = new HashSet<>();
        for (HistoryEntry entry : history) {
            int available = entry.countAfter(cursor);
            if (available == 0) continue;
            String id = entry.getKey().itemId().toString();
            if (group.containsItem(id)) {
                if (!group.isRequireUnique()) count += available;
                else if (unique.add(entry.getKey())) count++;
            } else if (group.isConsecutive()) break;
        }
        return count;
    }
     
    public static int countMatchedInHistory(List<HistoryEntry> history, FoodGroupDefinition group, int consumedCount) {
        return Math.max(0, countEligibleHistory(history, group, -1) - Math.max(0, consumedCount));
    }
}
