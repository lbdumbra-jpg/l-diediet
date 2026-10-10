package com.lumbra.ldiediet.math;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NutritionDecayTest {
    @Test void dietReferenceForFiveHalfFilledGroups() {
         
        assertEquals(49.82787, NutritionDecay.hunger(50,100,1,5,15,1),.00002);
    }
    @Test void dietRateDependsOnCurrentFillAndDiversity() {
        assertEquals(97.28172,NutritionDecay.hunger(100,100,1,1,15,1),.00002);
        assertTrue(NutritionDecay.hunger(50,100,1,5,15,1)>NutritionDecay.hunger(50,100,1,1,15,1));
        assertEquals(NutritionDecay.hunger(50,100,2,5,15,1)*2,NutritionDecay.hunger(100,200,2,5,15,1),.00001);
    }
    @Test void hungerDecayHandlesDisabledMultiplierAndFloor() {
        assertEquals(65.12345f,NutritionDecay.hunger(65.12345f,100,1,5,15,0));
        assertEquals(0,NutritionDecay.hunger(.01f,100,20,1,15,1));
        assertEquals(50,NutritionDecay.hunger(50,100,0,5,15,1));
    }
    @Test void hungerDecayRejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,()->NutritionDecay.hunger(50,100,1,5,101,1));
        assertThrows(IllegalArgumentException.class,()->NutritionDecay.hunger(50,100,1,5,15,Double.NaN));
    }
    @Test void dayConsumesTwentyPercentagePoints() { assertEquals(80,NutritionDecay.apply(100,100,1,24000),0.0001); }
    @Test void maximumDefinesRateNotCurrentValue() { assertEquals(48,NutritionDecay.apply(50,200,1,1200),0.0001); }
    @Test void zeroRateAndFloor() { assertEquals(5,NutritionDecay.apply(5,100,0,24000));assertEquals(0,NutritionDecay.apply(5,100,20,24000)); }
    @Test void invalidInputsCannotPoisonPlayerData() { assertThrows(IllegalArgumentException.class,()->NutritionDecay.apply(20,100,Double.NaN,20));assertThrows(IllegalArgumentException.class,()->NutritionDecay.apply(20,100,-1,20)); }
}
