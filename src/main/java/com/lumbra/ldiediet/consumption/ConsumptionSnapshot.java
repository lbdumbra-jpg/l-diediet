package com.lumbra.ldiediet.consumption;

import com.lumbra.ldiediet.ToleranceStage;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.data.DietConfigManager;
import com.lumbra.ldiediet.data.FoodKey;
import com.lumbra.ldiediet.util.ExclusionMatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import java.util.Map;

 
public record ConsumptionSnapshot(ItemStack stack, FoodKey key, FoodProperties properties,
        double hunger, double saturation, Double qualityOverride, double cci, double baseMonotony,
        float monotonyBefore, ToleranceStage stage, FoodPenalty.Policy penalty, boolean excluded, Map<String, Double> nutrients, com.lumbra.ldiediet.data.FoodStageProfiles.Policy rules) {
    public ConsumptionSnapshot {
        stack = stack.copy(); stack.setCount(1); nutrients = Map.copyOf(nutrients);
    }
    @Override public ItemStack stack() { return stack.copy(); }
    public double foodValue() { return qualityOverride == null ? hunger + saturation : qualityOverride; }

    public static boolean isConsumable(ItemStack stack, Player player) {
        return stack.getFoodProperties(player) != null || stack.getUseAnimation() == UseAnim.EAT
                || stack.getUseAnimation() == UseAnim.DRINK;
    }
    public static ConsumptionSnapshot capture(Player player, ItemStack stack) {
        return capture(player, stack, stack.getFoodProperties(player));
    }
    public static ConsumptionSnapshot captureIfConsumable(Player player, ItemStack stack) {
        if (stack.isEmpty()) return null;
        FoodProperties food = stack.getFoodProperties(player);
        if (food == null && stack.getUseAnimation() != UseAnim.EAT && stack.getUseAnimation() != UseAnim.DRINK) return null;
        return capture(player, stack, food);
    }
    public static ConsumptionSnapshot capture(Player player, ItemStack stack, FoodProperties food) {
        boolean client = player.level().isClientSide;
        boolean excluded = ExclusionMatcher.isExcluded(stack, client);
        if (!client && !excluded) DietConfigManager.getOrCalculateCCI(stack, player.getServer());
        var settings = DietConfigManager.describeFood(stack, client);
        var cap = CapabilityHandler.get(player);
        float monotony = cap == null ? 0 : cap.getMonotony(settings.key());
        ToleranceStage stage = excluded ? ToleranceStage.STAGE_0 : ToleranceStage.forStack(stack, monotony, client);
        var rules = com.lumbra.ldiediet.data.FoodStageProfiles.forStack(stack, client);
        var penalty = new FoodPenalty.Policy(excluded?1:rules.hunger().get(stage.getLevel()),
                rules.minimumHunger(),
                com.lumbra.ldiediet.LDieDietServerConfig.ENABLE_SATURATION_PENALTY.get());
        double hunger = food == null ? 0 : Math.max(0, food.getNutrition());
        double saturation = food == null ? 0 : Math.max(0, hunger * food.getSaturationModifier() * 2);
        if (!Double.isFinite(saturation)) saturation = 0;
        return new ConsumptionSnapshot(stack, settings.key(), freezeProperties(food), hunger, saturation, settings.foodValue(),
                settings.cci(), settings.baseMonotony(), monotony, stage, penalty, excluded, settings.nutrients(),rules);
    }
    public double chewingMultiplier() { return excluded ? 1 : rules.chewing().get(stage.getLevel()); }
    public double nutrientMultiplier() { return excluded ? 1 : rules.nutrients().get(stage.getLevel()); }
    public boolean allowsBuffs() { return excluded || rules.buffs().get(stage.getLevel()); }
    public ConsumptionSnapshot withNutrition(double newHunger,double newSaturation) {
        if(!Double.isFinite(newHunger+newSaturation)||newHunger<0||newSaturation<0)throw new IllegalArgumentException("Portion values must be finite and non-negative");
        return new ConsumptionSnapshot(stack,key,properties,newHunger,newSaturation,qualityOverride,cci,baseMonotony,monotonyBefore,stage,penalty,excluded,nutrients,rules);
    }
    private static FoodProperties freezeProperties(FoodProperties original) {
        if (original == null) return null;
        var builder = new FoodProperties.Builder().nutrition(original.getNutrition()).saturationMod(original.getSaturationModifier());
        if (original.isMeat()) builder.meat();
        if (original.canAlwaysEat()) builder.alwaysEat();
        if (original.isFastFood()) builder.fast();
        for (var pair : original.getEffects()) {
            if (pair.getFirst() == null) continue;
            var copy = new net.minecraft.world.effect.MobEffectInstance(pair.getFirst());
            builder.effect(() -> new net.minecraft.world.effect.MobEffectInstance(copy), pair.getSecond());
        }
        return builder.build();
    }
}
