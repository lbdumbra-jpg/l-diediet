package com.lumbra.ldiediet.consumption;

 
public final class FoodPenalty {
    public record Levels(int hunger, float saturation) {}
    public record Policy(double multiplier, double minimumHunger, boolean penalizeSaturation) {}
    private FoodPenalty() {}
    public static Levels apply(int oldHunger, float oldSaturation, double rawHunger, double rawSaturation,
                               double multiplier, double minimumHunger, boolean penalizeSaturation) {
        double hunger = Double.isFinite(rawHunger) ? Math.max(0, rawHunger) : 0;
        double saturation = Double.isFinite(rawSaturation) ? Math.max(0, rawSaturation) : 0;
        int grant = hunger <= 0 ? 0 : (int) Math.min(20,
                Math.max(Math.round(hunger * multiplier), Math.ceil(minimumHunger)));
        int food = Math.min(20, Math.max(0, Math.min(20, oldHunger)) + grant);
        double before = Float.isFinite(oldSaturation) ? Math.max(0, oldSaturation) : 0;
        float finalSaturation = (float) Math.min(food, before + saturation * (penalizeSaturation ? multiplier : 1));
        return new Levels(food, finalSaturation);
    }
}
