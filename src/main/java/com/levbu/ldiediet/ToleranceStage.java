package com.levbu.ldiediet;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import com.levbu.ldiediet.capability.FoodToleranceEntry;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.LDieDietServerConfig;

/**
 * Стадии надоедания еды с их эффектами.
 */
public enum ToleranceStage {
    STAGE_0(0, true),
    STAGE_1(1, true),
    STAGE_2(2, false),
    STAGE_3(3, false),
    STAGE_4(4, false);

    private final int level;
    private final boolean allowBuffs;

    ToleranceStage(int level, boolean allowBuffs) {
        this.level = level;
        this.allowBuffs = allowBuffs;
    }

    public int getLevel() {
        return level;
    }

    public boolean allowsBuffs() {
        return allowBuffs;
    }

    public float getHungerMultiplier() {
        return (float) LDieDietServerConfig.getMultiplier(level);
    }

    public float getPositiveDietMultiplier() {
        return switch (level) {
            case 3 -> LDieDietServerConfig.STAGE3_POSITIVE_DIET_MULT.get().floatValue();
            case 4 -> LDieDietServerConfig.STAGE4_POSITIVE_DIET_MULT.get().floatValue();
            default -> 1.0f;
        };
    }

    public float getNegativeDietMultiplier() {
        return switch (level) {
            case 3 -> LDieDietServerConfig.STAGE3_NEGATIVE_DIET_MULT.get().floatValue();
            case 4 -> LDieDietServerConfig.STAGE4_NEGATIVE_DIET_MULT.get().floatValue();
            default -> 1.0f;
        };
    }

    public double getThreshold() {
        int[] thresholds = getBaseThresholds();
        return level > 0 && level <= thresholds.length ? thresholds[level - 1] : 0;
    }

    public static ToleranceStage fromEntry(FoodToleranceEntry entry, Item item, Player player) {
        if (entry == null)
            return STAGE_0;
        
        long currentTime = player.level().getGameTime();
        int baseRecoveryTicks = LDieDietServerConfig.RECOVERY_TIME_TICKS.get();
        
        IFoodTolerance cap = CapabilityHandler.get(player);
        double varietyMultiplier = (cap != null) ? cap.getVarietyRecoveryMultiplier() : 1.0;
        double dietMultiplier = entry.getRecoveryTimeMultiplier();
        
        int fullRecoveryTicks = (int) (baseRecoveryTicks * dietMultiplier / varietyMultiplier);
        
        return fromEntry(entry, currentTime, fullRecoveryTicks, item);
    }

    /**
     * Получить стадию с учётом восстановления по времени.
     */
    public static ToleranceStage fromEntry(FoodToleranceEntry entry, long currentTime, int fullRecoveryTicks, Item item) {
        if (entry == null)
            return STAGE_0;
        int effectiveStage = entry.calculateEffectiveStage(currentTime, fullRecoveryTicks,
                entry.getAdjustedThresholds(), item);
        return fromLevel(effectiveStage);
    }

    public static ToleranceStage fromLevel(int level) {
        return switch (level) {
            case 1 -> STAGE_1;
            case 2 -> STAGE_2;
            case 3 -> STAGE_3;
            case 4 -> STAGE_4;
            default -> STAGE_0;
        };
    }

    public static int[] getBaseThresholds() {
        var t = LDieDietServerConfig.STAGE_THRESHOLDS.get();
        return new int[] { t.get(0), t.get(1), t.get(2), t.get(3) };
    }
}
