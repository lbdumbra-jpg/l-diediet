package com.lumbra.ldiediet.consumption;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodPenaltyTest {
    @Test void scalesBeforeTheHungerCapInsteadOfScalingTheCappedGain() {
        var result = FoodPenalty.apply(19, 0, 8, 12, .5, .5, true);
        assertEquals(20, result.hunger()); assertEquals(6, result.saturation(), .0001);
    }
    @Test void zeroNutritionDoesNotCreateTheConfiguredMinimum() {
        var result = FoodPenalty.apply(0, 0, 0, 0, .25, 5, true);
        assertEquals(0, result.hunger()); assertEquals(0, result.saturation());
        assertEquals(1, FoodPenalty.apply(0, 0, 4, 2.4, .25, .5, true).hunger());
    }
    @Test void saturationNeverExceedsResultingHungerAndCanKeepExistingSaturation() {
        var result = FoodPenalty.apply(0, 0, 2, 10, .5, .5, true);
        assertEquals(1, result.hunger()); assertEquals(1, result.saturation());
        assertEquals(5, FoodPenalty.apply(10, 4, 4, 2, .5, .5, true).saturation());
    }
    @Test void saturationConfigurationAffectsOnlyTheSaturationGrant() {
        var result = FoodPenalty.apply(10, 1, 4, 2.4, .25, .5, false);
        assertEquals(11, result.hunger()); assertEquals(3.4, result.saturation(), .0001);
    }
}
