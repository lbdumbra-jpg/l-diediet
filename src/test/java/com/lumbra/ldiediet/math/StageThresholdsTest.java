package com.lumbra.ldiediet.math;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StageThresholdsTest {
    @Test
    void invalidWholeSettingRestoresFourThresholds() {
        for (List<Integer> invalid : List.of(List.<Integer>of(), List.of(5, 10),
                List.of(5, 10, 15, 20, 25), List.of(-1, 10, 15, 20), List.of(5, 15, 10, 20))) {
            assertFalse(StageThresholds.isValid(invalid));
            assertEquals(List.of(5, 10, 15, 20), StageThresholds.validated(invalid));
        }
        assertFalse(StageThresholds.isValid(List.of(5.0, 10.0, 15.0, 20.0)));
        assertTrue(StageThresholds.isValid(List.of(0, 0, 10, 20)));
        assertEquals(List.of(2, 7, 13, 19), StageThresholds.validated(List.of(2, 7, 13, 19)));
    }
}
