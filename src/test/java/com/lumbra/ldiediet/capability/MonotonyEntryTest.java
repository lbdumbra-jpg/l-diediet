package com.lumbra.ldiediet.capability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonotonyEntryTest {
    @Test void recoveryCounterSurvivesNbtAndResetsOnEatingOrFullRecovery() {
        var entry=new MonotonyEntry();entry.setMonotony(6);entry.setRecoveryMeals(20);
        var restored=MonotonyEntry.fromNBT(entry.serializeNBT());
        assertEquals(20,restored.getRecoveryMeals());
        restored.recordMeal();assertEquals(0,restored.getRecoveryMeals());
        restored.setRecoveryMeals(21);restored.setMonotony(0);assertEquals(0,restored.getRecoveryMeals());
        var legacy=entry.serializeNBT();legacy.remove("recoveryMeals");
        assertEquals(0,MonotonyEntry.fromNBT(legacy).getRecoveryMeals());
        entry.setRecoveryMeals(-10);assertEquals(0,entry.getRecoveryMeals());
    }
    @Test void anInvalidValueDoesNotEraseSavedMonotony() {
        var entry = new MonotonyEntry(); entry.setMonotony(17);
        entry.setMonotony(Float.NaN); entry.addMonotony(Float.POSITIVE_INFINITY);
        assertEquals(17, entry.getMonotony());
    }
    @Test void aLargeIncreaseDoesNotOverflowBackToZero() {
        var entry = new MonotonyEntry(); entry.setMonotony(Float.MAX_VALUE); entry.addMonotony(Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, entry.getMonotony());
    }
}
