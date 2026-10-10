package com.lumbra.ldiediet.capability;

import com.lumbra.ldiediet.data.FoodGroupDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActiveGroupEffectTest {
    private static FoodGroupDefinition.GroupEffect definition(String duration, int ticks, Map<String, Double> multipliers) {
        return new FoodGroupDefinition.GroupEffect(duration, "health", "health", 2, 3, ticks,
                new int[]{2, 2}, new int[]{3, 3}, List.of("cancel"), List.of(), multipliers, null);
    }
    @Test void multiplierChangesLifetimeAndNeverStrength() {
        var effect = ActiveGroupEffect.waiting("pack:reward", 0, definition("fixed", 4, Map.of("prereq", 3.0)));
        effect.start(Set.of("pack:prereq"));
        assertEquals(12, effect.getRemainingTicks()); assertEquals(2, effect.getValue()); assertEquals(3, effect.getAmplifier());
        for (int i = 0; i < 11; i++) assertFalse(effect.tick());
        assertTrue(effect.tick()); assertEquals(ActiveGroupEffect.State.EXPIRED, effect.getState());
        effect.start(Set.of("pack:prereq")); assertEquals(ActiveGroupEffect.State.EXPIRED, effect.getState());
    }
    @Test void cancellationAndExpiryCannotRestart() {
        var effect = ActiveGroupEffect.waiting("pack:reward", 1, definition("until_death", 0, Map.of()));
        assertTrue(effect.shouldCancelOnGroup("pack:cancel")); assertFalse(effect.shouldCancelOnGroup("other:cancel"));
        effect.cancel(); effect.start(Set.of()); assertEquals(ActiveGroupEffect.State.CANCELLED, effect.getState());
        assertFalse(effect.isCurrentlyActive()); assertFalse(effect.isLive());
    }
    @Test void pausedEffectRemainsLiveAndNbtKeepsPhase() {
        var effect = ActiveGroupEffect.waiting("pack:reward", 0, definition("until_death_intermittent", 0, Map.of()));
        effect.start(Set.of()); effect.tick(); effect.tick();
        assertEquals(ActiveGroupEffect.State.PAUSED, effect.getState()); assertTrue(effect.isLive());
        var restored = ActiveGroupEffect.fromNBT(effect.serializeNBT());
        assertEquals(effect.getState(), restored.getState()); assertEquals(3, restored.getPhaseTicksRemaining());
        restored.tick(); restored.tick(); restored.tick(); assertTrue(restored.isCurrentlyActive());
    }
    @Test void grantingTickAndDuplicateTicksDoNotConsumeLifetime() {
        var effect = ActiveGroupEffect.waiting("pack:reward", 0, definition("fixed", 2, Map.of()));
        effect.start(Set.of(), 100); effect.tick(100); assertEquals(2, effect.getRemainingTicks());
        effect.tick(101); effect.tick(101); assertEquals(1, effect.getRemainingTicks());
        effect.tick(102); assertTrue(effect.isTerminal());
    }
    @Test void terminalStatesSurviveSaveAndLegacyActiveStateMigrates() {
        var effect = ActiveGroupEffect.waiting("pack:reward", 4, definition("fixed", 10, Map.of()));
        effect.cancel(); assertEquals(ActiveGroupEffect.State.CANCELLED, ActiveGroupEffect.fromNBT(effect.serializeNBT()).getState());
        var nbt = effect.serializeNBT(); nbt.remove("state"); nbt.remove("definitionIndex");
        nbt.putString("groupId", "legacy_group"); nbt.putBoolean("currentlyActive", true); nbt.putInt("remainingTicks", 7);
        var legacy = ActiveGroupEffect.fromNBT(nbt);
        assertEquals("ldiediet:legacy_group", legacy.getGroupId()); assertEquals(7, legacy.getRemainingTicks());
        assertEquals(-1, legacy.getDefinitionIndex());
    }
}
