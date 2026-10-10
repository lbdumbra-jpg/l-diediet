package com.lumbra.ldiediet.math;

import java.util.List;

 
public final class StageThresholds {
    public static final List<Integer> DEFAULT = List.of(5, 10, 15, 20);
    private StageThresholds() {}

    public static boolean isValid(Object value) {
        if (!(value instanceof List<?> list) || list.size() != 4) return false;
        int previous = 0;
        for (Object entry : list) {
            if (!(entry instanceof Integer threshold) || threshold < previous) return false;
            previous = threshold;
        }
        return true;
    }

    public static List<Integer> validated(List<? extends Integer> value) {
        return isValid(value) ? List.copyOf(value) : DEFAULT;
    }
}
