package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import java.util.*;

 
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class RewardLogicGameTests {
    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_rewards", timeoutTicks = 100)
    public static void revivedPlayerKeepsStateWithANewCapabilityHandle(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var oldHandle = player.getCapability(CapabilityHandler.FOOD_TOLERANCE);
            var original = oldHandle.orElseThrow(() -> new IllegalStateException("Missing capability"));
            original.setMonotony(Items.BREAD, 12);
            original.setNutrientValue("fruits", 37);
            player.invalidateCaps();
            h.assertFalse(oldHandle.isPresent(), "Old handle survived invalidation");
            h.assertFalse(player.getCapability(CapabilityHandler.FOOD_TOLERANCE).isPresent(), "Removed player exposed capability");
            player.reviveCaps();
            var revived = CapabilityHandler.get(player);
            h.assertTrue(revived == original, "Revival replaced or lost player data");
            h.assertTrue(revived.getMonotony(Items.BREAD) == 12, "Revival lost monotony");
            h.assertTrue(revived.getAllNutrients().get("fruits") == 37, "Revival lost nutrients");
            h.assertFalse(oldHandle.isPresent(), "Invalid handle was resurrected");
            h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }
    private static final String TEMPLATE = "bastion/blocks/air";
    private static final String BATCH = "ldiediet_rewards";
    private static FoodGroupDefinition group(GameTestHelper h, String name) {
        var definition = FoodGroupManager.getInstance().getGroup(new ResourceLocation("test_rewards", name));
        h.assertTrue(definition != null, "Missing isolated reward fixture " + name);
        return definition;
    }
    private static ActiveGroupEffect effect(IFoodTolerance cap, String name, int index) {
        return cap.getActiveEffects().stream().filter(e -> e.getGroupId().equals("test_rewards:" + name)
                && e.getDefinitionIndex() == index).findFirst().orElseThrow();
    }
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .001, message + ": expected " + expected + ", got " + actual);
    }
    private static void pass(GameTestHelper h, String test) { LDieDiet.LOGGER.info("[LDieDiet GameTest] PASS {}", test); h.succeed(); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void snapshotBeforeCancellationAndPartialCancellation(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(player); double health = player.getMaxHealth();
            double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            RewardHandler.activateGroupReward(player, cap, group(h, "source"));
            RewardHandler.activateGroupReward(player, cap, group(h, "partial"));
            RewardHandler.activateGroupReward(player, cap, group(h, "dependent"));
            h.assertTrue(effect(cap, "dependent", 0).isCurrentlyActive(), "Prerequisite was checked after cancellation");
            h.assertTrue(effect(cap, "source", 0).getState() == ActiveGroupEffect.State.CANCELLED, "Source was not cancelled");
            h.assertTrue(effect(cap, "partial", 0).getState() == ActiveGroupEffect.State.CANCELLED, "Selected partial effect survived");
            h.assertTrue(effect(cap, "partial", 1).isCurrentlyActive(), "Unselected effect was cancelled");
            near(h, player.getMaxHealth(), health + 4, "Wrong health effects after cancellation");
            near(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), damage + 2, "Uncancelled attribute was removed");
            RewardHandler.tickActiveEffects(player);
            h.assertTrue(effect(cap, "partial", 0).getState() == ActiveGroupEffect.State.CANCELLED, "Cancelled effect was renewed");
            pass(h, "snapshotBeforeCancellationAndPartialCancellation");
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void fixedTimerDoesNotRenewOrScaleHealth(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(player); double health = player.getMaxHealth();
            RewardHandler.activateGroupReward(player, cap, group(h, "multiplier"));
            RewardHandler.activateGroupReward(player, cap, group(h, "mixed"));
            var fixed = effect(cap, "mixed", 1);
            h.assertTrue(fixed.getRemainingTicks() == 9, "Duration factor was not applied to the timer");
            near(h, player.getMaxHealth(), health + 3, "Duration factor changed health strength or duplicate health definitions collapsed");
            for (int i = 0; i < 9; i++) fixed.tick();
            RewardHandler.tickActiveEffects(player);
            near(h, player.getMaxHealth(), health + 1, "Expired fixed bonus remained");
            var saved = cap.serializeNBT(); cap.deserializeNBT(saved);
            RewardHandler.tickActiveEffects(player);
            h.assertTrue(effect(cap, "mixed", 1).getState() == ActiveGroupEffect.State.EXPIRED, "Fixed effect renewed after save/load");
            near(h, player.getMaxHealth(), health + 1, "Fixed effect renewed alongside a permanent effect");
            pass(h, "fixedTimerDoesNotRenewOrScaleHealth");
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void permanentWaitingRetriesButCancelledAndFixedDoNot(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        var cap = CapabilityHandler.get(player);
        RewardHandler.activateGroupReward(player, cap, group(h, "waiting"));
        h.assertTrue(effect(cap, "waiting", 0).getState() == ActiveGroupEffect.State.WAITING, "Missing prerequisite not retained as waiting");
        h.assertTrue(effect(cap, "waiting", 1).getState() == ActiveGroupEffect.State.EXPIRED, "Missing fixed effect can renew late");
        RewardHandler.activateGroupReward(player, cap, group(h, "gate"));
        h.onEachTick(player::doTick);
        h.runAfterDelay(25, () -> {
            try {
                h.assertTrue(effect(cap, "waiting", 0).isCurrentlyActive(), "Permanent waiting effect was not retried");
                h.assertTrue(effect(cap, "waiting", 1).getState() == ActiveGroupEffect.State.EXPIRED, "Fixed effect appeared late");
                RewardHandler.activateGroupReward(player, cap, group(h, "stop"));
                h.assertTrue(effect(cap, "waiting", 0).getState() == ActiveGroupEffect.State.CANCELLED, "Cancellation tombstone missing");
                RewardHandler.tickActiveEffects(player);
                h.assertTrue(effect(cap, "waiting", 0).getState() == ActiveGroupEffect.State.CANCELLED, "Cancelled permanent effect renewed");
                pass(h, "permanentWaitingRetriesButCancelledAndFixedDoNot");
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void pausedGroupCountsAsActivated(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(player);
            RewardHandler.activateGroupReward(player, cap, group(h, "paused"));
            effect(cap, "paused", 0).tick();
            h.assertTrue(effect(cap, "paused", 0).getState() == ActiveGroupEffect.State.PAUSED, "Fixture did not pause");
            RewardHandler.activateGroupReward(player, cap, group(h, "paused_dependent"));
            h.assertTrue(effect(cap, "paused_dependent", 0).isCurrentlyActive(), "Pause incorrectly invalidated group activation");
            pass(h, "pausedGroupCountsAsActivated");
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void evictedHistoryDoesNotBlockNewActivation(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(player); cap.clearHistory();
            cap.addHistory(Items.APPLE); cap.addHistory(Items.BREAD);
            cap.setRewardCursor("test_rewards:window", cap.getMealSequence());
            for (int i = 0; i < 35; i++) cap.addHistory(Items.CARROT);
            cap.addHistory(Items.APPLE); cap.addHistory(Items.BREAD);
            var definition = group(h, "window");
            h.assertTrue(RewardHandler.countEligibleHistory(cap.getEatingHistory(), definition, cap.getRewardCursor(definition.getId().toString())) == 2,
                    "Old deactivation cursor subtracted meals from the new window");
            RewardHandler.checkAndApplyRewards(player, cap);
            h.assertTrue(cap.hasActiveEffectsForGroup(definition.getId().toString()), "New meals did not reactivate the reward");
            effect(cap, "window", 0).tick();
            RewardHandler.tickActiveEffects(player);
            RewardHandler.checkAndApplyRewards(player, cap);
            h.assertFalse(cap.hasActiveEffectsForGroup(definition.getId().toString()), "Same meals triggered the reward twice");
            var saved = cap.serializeNBT(); cap.deserializeNBT(saved);
            cap.addHistory(Items.BREAD); cap.addHistory(Items.APPLE);
            h.assertTrue(RewardHandler.countEligibleHistory(cap.getEatingHistory(), definition, cap.getRewardCursor(definition.getId().toString())) == 2,
                    "New portion sequences did not survive persistence");
            pass(h, "evictedHistoryDoesNotBlockNewActivation");
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void overlappingRewardsAndPotionsKeepIndependentSources(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(player);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
            RewardHandler.activateGroupReward(player, cap, group(h, "mob_a"));
            RewardHandler.activateGroupReward(player, cap, group(h, "mob_b"));
            h.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 3, "Strongest reward is missing");
             
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            RewardHandler.removeEffect(player, effect(cap, "mob_a", 0));
            h.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 2, "Ending one reward removed the other");
            var saved = cap.serializeNBT(); cap.deserializeNBT(saved);
            RewardHandler.removeEffect(player, effect(cap, "mob_b", 0));
            var potion = player.getEffect(MobEffects.MOVEMENT_SPEED);
            h.assertTrue(potion != null && potion.getAmplifier() == 1 && potion.getDuration() == 400,
                    "Existing/new potion did not survive removal and save/load of reward sources");
            pass(h, "overlappingRewardsAndPotionsKeepIndependentSources");
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void removedNutritionScaleDoesNotLeaveModifiers(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        var manager = NutritionScaleManager.getInstance(); var original = manager.serializeToNBT();
        try {
            var cap = CapabilityHandler.get(player); double health = player.getMaxHealth();
            cap.setNutrientValue("test_rewards:reload", 60);
            NutritionAttributeHandler.refresh(player, cap);
            near(h, player.getMaxHealth(), health + 3, "Nutrition fixture did not apply");
            manager.deserializeFromNBT(new CompoundTag());
            NutritionAttributeHandler.refresh(player, cap);
            near(h, player.getMaxHealth(), health, "Removed scale left its modifier behind");
            pass(h, "removedNutritionScaleDoesNotLeaveModifiers");
        } finally { manager.deserializeFromNBT(original); LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void invalidatedCapabilityClonesWithoutLosingState(GameTestHelper h) {
        var original = LDieDietGameTests.makeNetworkedTestPlayer(h);
        var next = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(original);
            cap.setMonotony(Items.APPLE, 13); cap.setNutrientValue("test:persistent", 17); cap.addHistory(Items.APPLE);
            RewardHandler.activateGroupReward(original, cap, group(h, "mixed"));
            var oldHandle = original.getCapability(CapabilityHandler.FOOD_TOLERANCE);
            original.invalidateCaps();
            h.assertFalse(oldHandle.isPresent(), "LazyOptional was not invalidated");
            CapabilityHandler.ForgeBusEvents.onPlayerClone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(next, original, false));
            var cloned = CapabilityHandler.get(next);
            near(h, cloned.getMonotony(Items.APPLE), 13, "Clone lost monotony");
            near(h, cloned.getNutrientValue("test:persistent"), 17, "Clone lost nutrients");
            h.assertTrue(cloned.getMealSequence() == cap.getMealSequence(), "Clone lost history sequence");
            h.assertTrue(cloned.hasActiveEffectsForGroup("test_rewards:mixed"), "Non-death clone lost rewards");
            h.assertFalse(original.getCapability(CapabilityHandler.FOOD_TOLERANCE).isPresent(), "Clone revived the removed original");
            pass(h, "invalidatedCapabilityClonesWithoutLosingState");
        } finally { LDieDietGameTests.removePlayer(original); LDieDietGameTests.removePlayer(next); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void legacyHistoryAndRewardOffsetsMigrateSafely(GameTestHelper h) {
        CompoundTag old = new CompoundTag(); ListTag history = new ListTag();
        CompoundTag entry = new CompoundTag(); entry.putString("item", "minecraft:apple"); entry.putInt("count", 4); history.add(entry);
        old.put("eatingHistory", history);
        CompoundTag offsets = new CompoundTag(); offsets.putInt("window", 4); old.put("consumedHistoryTotals", offsets);
        CompoundTag monotony = new CompoundTag(); monotony.putString("item", "minecraft:apple");
        CompoundTag values = new CompoundTag(); values.putFloat("monotony", 12); values.putLong("time", 55);
        monotony.put("data", values); ListTag entries = new ListTag(); entries.add(monotony); old.put("monotonyEntries", entries);
        var cap = new FoodToleranceCapability(); cap.deserializeNBT(old);
        h.assertTrue(cap.getMealSequence() == 4 && cap.getRewardCursor("window") == 4, "Legacy offset reused old meals");
        near(h, cap.getMonotony(Items.APPLE), 12, "Migration lost old monotony");
        h.assertTrue(cap.getLastEatenGameTime(Items.APPLE) == 55, "Migration lost food time");
        cap.addHistory(Items.APPLE);
        h.assertTrue(cap.getEatingHistory().get(0).countAfter(4) == 1, "RLE history did not isolate the new portion");
        pass(h, "legacyHistoryAndRewardOffsetsMigrateSafely");
    }

    private static FoodGroupDefinition withEffects(FoodGroupDefinition group, List<FoodGroupDefinition.GroupEffect> effects) {
        return new FoodGroupDefinition(group.getId(), group.getName(), group.getActiveEffectText(), group.getFlag(),
                group.getAdvancement(), group.getItems(), effects, group.getThreshold(), group.isRequireUnique(),
                group.getTooltipIcon(), group.getTooltipIconColor(), group.isHideActivationMessage());
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void legacyStatusDoesNotBecomeAPermanentForeignPotion(GameTestHelper h) {
        legacyPotionScenario(h, false);
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void legacyStatusHiddenUnderStrongerPotionIsRemoved(GameTestHelper h) {
        legacyPotionScenario(h, true);
    }
    private static void legacyPotionScenario(GameTestHelper h, boolean strongerForeign) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(player);
             
            if (!strongerForeign) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2_000_000, 1));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1_000_000, 3, false, false, true));
            if (strongerForeign) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 5));
            CompoundTag old = new CompoundTag(), effect = new CompoundTag();
            effect.putString("groupId", "test_rewards:mob_a"); effect.putString("effectType", "mob_effect");
            effect.putString("effectId", "minecraft:speed"); effect.putString("durationType", "until_death");
            effect.putBoolean("currentlyActive", true); effect.putInt("amplifier", 3);
            ListTag effects = new ListTag(); effects.add(effect); old.put("activeEffects", effects);
            old.put("permanentRewards", com.lumbra.ldiediet.util.NBTUtils.writeStringList(List.of("test_rewards:mob_a"), "id"));
            cap.deserializeNBT(old); RewardHandler.tickActiveEffects(player);
            cap.getActiveEffects().forEach(ActiveGroupEffect::cancel); cap.clearPermanentRewards();
            RewardHandler.tickActiveEffects(player);
            var remaining = player.getEffect(MobEffects.MOVEMENT_SPEED);
            h.assertTrue(remaining != null && remaining.getAmplifier() == (strongerForeign ? 5 : 1)
                    && remaining.getDuration() == (strongerForeign ? 400 : 2_000_000),
                    "Old owned effect survived or preserved foreign potion was lost");
            h.assertTrue(!remaining.save(new CompoundTag()).contains("HiddenEffect"), "Old owned status remained hidden");
            h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void removedLegacyGroupDoesNotDeleteNewInvisiblePotions(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(player); CompoundTag old = new CompoundTag(), record = new CompoundTag();
            record.putString("groupId", "removed:group"); record.putString("effectType", "mob_effect");
            record.putString("effectId", "minecraft:speed"); record.putString("durationType", "until_death");
            record.putBoolean("currentlyActive", true); ListTag effects = new ListTag(); effects.add(record); old.put("activeEffects", effects);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1_000_000, 3, false, false, true));
            cap.deserializeNBT(old); cap.deserializeNBT(cap.serializeNBT());
            RewardHandler.tickActiveEffects(player);
            h.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED) == null, "Old removed group's status was retained");
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 2, false, false, true));
            RewardHandler.tickActiveEffects(player); cap.deserializeNBT(cap.serializeNBT()); RewardHandler.tickActiveEffects(player);
            h.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED) != null && player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 100,
                    "Legacy cleanup repeated and removed a new foreign status");
            h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void legacyRewardsKeepTimersWhenDefinitionValuesChanged(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            double health = player.getMaxHealth();
            CompoundTag old = new CompoundTag();
            old.put("permanentRewards", com.lumbra.ldiediet.util.NBTUtils.writeStringList(List.of("test_rewards:mixed"), "id"));
            ListTag effects = new ListTag();
            for (String duration : List.of("until_death", "fixed")) {
                CompoundTag effect = new CompoundTag();
                effect.putString("groupId", "test_rewards:mixed");
                effect.putString("effectType", "health"); effect.putString("effectId", "health");
                effect.putString("durationType", duration); effect.putBoolean("currentlyActive", true);
                effect.putDouble("value", duration.equals("fixed") ? 8 : 9);
                effect.putInt("remainingTicks", duration.equals("fixed") ? 7 : 0);
                effects.add(effect);
            }
            old.put("activeEffects", effects);
            var cap = CapabilityHandler.get(player); cap.deserializeNBT(old);
            RewardHandler.tickActiveEffects(player);
            near(h, player.getMaxHealth(), health + 17, "Legacy reward migration lost or duplicated stored values");
            h.assertTrue(effect(cap, "mixed", 1).getRemainingTicks() == 6, "Migration reset the saved fixed timer");
            cap.deserializeNBT(cap.serializeNBT());
            RewardHandler.tickActiveEffects(player);
            near(h, player.getMaxHealth(), health + 17, "Reload duplicated migrated modifiers");
            h.assertTrue(effect(cap, "mixed", 1).getRemainingTicks() == 6, "Same-tick reload advanced the timer twice");
            pass(h, "legacyRewardsKeepTimersWhenDefinitionValuesChanged");
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void reloadAddsPermanentSlotsWithoutRenewingFixedSlots(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h); var cap = CapabilityHandler.get(player);
        var manager = FoodGroupManager.getInstance(); var original = group(h, "mixed");
        double before = player.getMaxHealth();
        RewardHandler.activateGroupReward(player, cap, original);
        for (int i = 0; i < 3; i++) effect(cap, "mixed", 1).tick();
        RewardHandler.tickActiveEffects(player);
        List<FoodGroupDefinition.GroupEffect> updated = new ArrayList<>(original.getEffects());
        updated.add(new FoodGroupDefinition.GroupEffect("until_death", "health", "health", 6, 0, 0,
                null, null, List.of(), List.of("gate"), Map.of(), null));
        manager.getGroupsMap().put(original.getId(), withEffects(original, updated));
        h.onEachTick(player::doTick);
        h.runAfterDelay(25, () -> {
            h.assertTrue(effect(cap, "mixed", 2).getState() == ActiveGroupEffect.State.WAITING, "New persistent slot was not discovered after reload");
            RewardHandler.activateGroupReward(player, cap, group(h, "gate"));
        });
        h.runAfterDelay(50, () -> {
            try {
                h.assertTrue(effect(cap, "mixed", 2).isCurrentlyActive(), "New persistent slot did not grant once prerequisites arrived");
                h.assertTrue(effect(cap, "mixed", 1).getState() == ActiveGroupEffect.State.EXPIRED, "Reload renewed a fixed slot");
                near(h, player.getMaxHealth(), before + 7, "Reload lost or duplicated reward modifiers");
                pass(h, "reloadAddsPermanentSlotsWithoutRenewingFixedSlots");
            } finally { manager.getGroupsMap().put(original.getId(), original); LDieDietGameTests.removePlayer(player); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void cancellationUsesTheCurrentDefinitionSlot(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        var manager = FoodGroupManager.getInstance(); var original = group(h, "source");
        try {
            var cap = CapabilityHandler.get(player);
            RewardHandler.activateGroupReward(player, cap, original);
            var definition = original.getEffects().get(0);
            var updated = new FoodGroupDefinition.GroupEffect(definition.getDurationType(), definition.getEffectType(),
                    definition.getEffectId(), definition.getValue(), definition.getAmplifier(), definition.getDurationTicks(),
                    definition.getIntervalActiveTicks(), definition.getIntervalPauseTicks(), List.of("stop"),
                    definition.getRequiresActiveGroups(), definition.getDurationMultipliers(), definition.getConditions());
            manager.getGroupsMap().put(original.getId(), withEffects(original, List.of(updated)));
            RewardHandler.activateGroupReward(player, cap, group(h, "dependent"));
            h.assertTrue(effect(cap, "source", 0).isCurrentlyActive(), "Stale cancellation rules overrode reloaded data");
            RewardHandler.activateGroupReward(player, cap, group(h, "stop"));
            h.assertTrue(effect(cap, "source", 0).getState() == ActiveGroupEffect.State.CANCELLED, "Updated cancellation rule was ignored");
            pass(h, "cancellationUsesTheCurrentDefinitionSlot");
        } finally { manager.getGroupsMap().put(original.getId(), original); LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void deathEndsUntilDeathEffectsAndAppliesConfiguredNutritionLoss(GameTestHelper h) {
        var original = LDieDietGameTests.makeNetworkedTestPlayer(h); var next = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap = CapabilityHandler.get(original);
            cap.setMonotony(Items.APPLE, 11); cap.setNutrientValue("test:persistent", 16); cap.addHistory(Items.APPLE);
            RewardHandler.activateGroupReward(original, cap, group(h, "mixed"));
            original.invalidateCaps();
            CapabilityHandler.ForgeBusEvents.onPlayerClone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(next, original, true));
            var restored = CapabilityHandler.get(next);
            near(h, restored.getMonotony(Items.APPLE), 11, "Death reset monotony");
            near(h, restored.getNutrientValue("test:persistent"), (float)(16*(1-com.lumbra.ldiediet.LDieDietServerConfig.NUTRITION_DEATH_LOSS_PERCENT.get()/100)), "Death nutrition loss differs from config");
            h.assertTrue(restored.getActiveEffects().stream().noneMatch(e -> e.isPersistent() && e.isLive()), "Until-death reward survived death");
            h.assertTrue(restored.getMobEffectSources().serializeNBT().getList("owned", Tag.TAG_COMPOUND).isEmpty(), "Death copied old potion sources");
            pass(h, "deathEndsUntilDeathEffectsAndAppliesConfiguredNutritionLoss");
        } finally { LDieDietGameTests.removePlayer(original); LDieDietGameTests.removePlayer(next); }
    }
}
