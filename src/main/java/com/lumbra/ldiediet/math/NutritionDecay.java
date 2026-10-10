package com.lumbra.ldiediet.math;
 
public final class NutritionDecay {
    private NutritionDecay() {}
     
    public static float hunger(float current, float maximum, int foodLost, int nonEmptyScales,
                               int penaltyPerGroup, double multiplier) {
        if (!Float.isFinite(current) || !Float.isFinite(maximum) || maximum <= 0 || foodLost < 0
                || nonEmptyScales < 0 || penaltyPerGroup < 0 || penaltyPerGroup > 100
                || !Double.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("Invalid hunger decay input");
        if (foodLost == 0 || nonEmptyScales == 0 || current <= 0 || multiplier == 0) return Math.max(0, current);
        float value = Math.min(1, current / maximum);
        float amount = (float)((float)foodLost / nonEmptyScales * Math.pow(1.0f - penaltyPerGroup / 100.0f, nonEmptyScales - 1));
        float loss = (float)(Math.exp(value) * amount * multiplier / 100.0);
        return Math.max(0, value - loss) * maximum;
    }
    public static float apply(float current, float maximum, double percentPerMinute, int ticks) {
        if (!Float.isFinite(current) || !Float.isFinite(maximum) || !Double.isFinite(percentPerMinute)
                || maximum <= 0 || percentPerMinute < 0 || ticks < 0) throw new IllegalArgumentException("Invalid decay input");
        return (float)Math.max(0, Math.min(maximum,current) - maximum * percentPerMinute / 100.0 * ticks / 1200.0);
    }
}
