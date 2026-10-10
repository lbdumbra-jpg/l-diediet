package com.lumbra.ldiediet.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

 




public class EffectConditionTest {

    @Test
    public void testNoConditions() {
        EffectCondition cond = new EffectCondition("AND", null, null, null, null);
        assertFalse(cond.hasAnyCondition(), "No conditions should report false for hasAnyCondition");
    }

    @Test
    public void testTemperatureCondition() {
        EffectCondition cond = new EffectCondition("AND", null, 0.15, null, null);
        assertTrue(cond.hasAnyCondition(), "Temperature max set should report hasAnyCondition");
        assertEquals(0.15, cond.getTemperatureMax(), 0.001);
        assertNull(cond.getTemperatureMin());
    }

    @Test
    public void testTemperatureRange() {
        EffectCondition cond = new EffectCondition("AND", 0.5, 1.5, null, null);
        assertTrue(cond.hasAnyCondition());
        assertEquals(0.5, cond.getTemperatureMin(), 0.001);
        assertEquals(1.5, cond.getTemperatureMax(), 0.001);
    }

    @Test
    public void testColdSweatCondition() {
        EffectCondition cond = new EffectCondition("AND", null, null, "freezing", null);
        assertTrue(cond.hasAnyCondition());
        assertEquals("freezing", cond.getColdSweatState());
    }

    @Test
    public void testSeasonCondition() {
        EffectCondition cond = new EffectCondition("OR", null, null, null, "winter");
        assertTrue(cond.hasAnyCondition());
        assertEquals("winter", cond.getSeason());
        assertEquals("OR", cond.getLogic());
    }

    @Test
    public void testAllConditions() {
        EffectCondition cond = new EffectCondition("AND", -0.5, 0.15, "freezing", "winter");
        assertTrue(cond.hasAnyCondition());
        assertEquals("AND", cond.getLogic());
        assertEquals(-0.5, cond.getTemperatureMin(), 0.001);
        assertEquals(0.15, cond.getTemperatureMax(), 0.001);
        assertEquals("freezing", cond.getColdSweatState());
        assertEquals("winter", cond.getSeason());
    }

    @Test
    public void testDefaultLogic() {
         
        EffectCondition cond = new EffectCondition(null, null, null, "cold", null);
        assertEquals("AND", cond.getLogic());

         
        EffectCondition cond2 = new EffectCondition("", null, null, "cold", null);
        assertEquals("AND", cond2.getLogic());
    }

    @Test
    public void testLogicCaseInsensitive() {
        EffectCondition cond = new EffectCondition("or", null, null, "cold", null);
        assertEquals("OR", cond.getLogic());
    }

    @Test
    public void testEmptyColdSweatStateNotCountedAsCondition() {
        EffectCondition cond = new EffectCondition("AND", null, null, "", null);
        assertFalse(cond.hasAnyCondition(), "Empty string coldSweatState should not count");
    }

    @Test
    public void testEmptySeasonNotCountedAsCondition() {
        EffectCondition cond = new EffectCondition("AND", null, null, null, "");
        assertFalse(cond.hasAnyCondition(), "Empty string season should not count");
    }
}
