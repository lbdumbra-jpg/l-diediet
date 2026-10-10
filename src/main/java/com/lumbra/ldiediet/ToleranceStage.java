package com.lumbra.ldiediet;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.capability.CapabilityHandler;

 








public enum ToleranceStage {
    STAGE_0(0),
    STAGE_1(1),
    STAGE_2(2),
    STAGE_3(3),
    STAGE_4(4);

    private final int level;

    ToleranceStage(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean allowsBuffs() {
        return LDieDietServerConfig.ALLOW_FOOD_BUFFS.get().get(level);
    }

    public float getHungerMultiplier() {
        return (float) LDieDietServerConfig.getMultiplier(level);
    }

     



    public float getChewingMultiplier() {
        return LDieDietServerConfig.CHEWING_MULTIPLIERS.get().get(level).floatValue();
    }

     



    public float getDietScaleMultiplier() {
        return LDieDietServerConfig.NUTRIENT_MULTIPLIERS.get().get(level).floatValue();
    }

    public static ToleranceStage fromMonotony(float monotony) {
        int[] thresholds = getBaseThresholds();
        for (int i = thresholds.length - 1; i >= 0; i--) {
            if (monotony >= thresholds[i]) {
                return fromLevel(i + 1);
            }
        }
        return STAGE_0;
    }

    public static ToleranceStage getStage(Player player, Item item) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null) return STAGE_0;
        return forItem(item, cap.getMonotony(item), player.level().isClientSide);
    }
    public static ToleranceStage getStage(Player player, net.minecraft.world.item.ItemStack stack) {
        var cap = CapabilityHandler.get(player);
        var key = com.lumbra.ldiediet.data.DietConfigManager.getFoodKey(stack, player.level().isClientSide);
        return forStack(stack, cap == null ? 0 : cap.getMonotony(key), player.level().isClientSide);
    }

    public static ToleranceStage forItem(Item item, float monotony) {
        return forItem(item, monotony, false);
    }
    public static ToleranceStage forItem(Item item, float monotony, boolean client) {
        Integer override = (client ? com.lumbra.ldiediet.data.ExclusionManager.getClientInstance() : com.lumbra.ldiediet.data.ExclusionManager.getInstance())
                .getStageOverrides().get(item);
        return override == null ? fromLevel(com.lumbra.ldiediet.data.FoodStageProfiles.forStack(new net.minecraft.world.item.ItemStack(item),client).stage(monotony)) : fromLevel(override);
    }

    public static ToleranceStage forStack(net.minecraft.world.item.ItemStack stack,float monotony,boolean client) {
        if(com.lumbra.ldiediet.util.ExclusionMatcher.isExcluded(stack,client))return STAGE_0;
        Integer override=(client?com.lumbra.ldiediet.data.ExclusionManager.getClientInstance():com.lumbra.ldiediet.data.ExclusionManager.getInstance()).getStageOverrides().get(stack.getItem());
        return override==null?fromLevel(com.lumbra.ldiediet.data.FoodStageProfiles.forStack(stack,client).stage(monotony)):fromLevel(override);
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
        return com.lumbra.ldiediet.math.StageThresholds.validated(LDieDietServerConfig.STAGE_THRESHOLDS.get())
                .stream().mapToInt(Integer::intValue).toArray();
    }
}
