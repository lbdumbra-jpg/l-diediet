package com.lumbra.ldiediet.consumption;

import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.event.FoodEatingHandler;
import com.lumbra.ldiediet.util.EatingContext;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.function.Supplier;

 
public final class FoodConsumption {
    private FoodConsumption() {}
    private static final ThreadLocal<java.util.Set<Player>> ACTIVE=ThreadLocal.withInitial(java.util.HashSet::new);
    private static void release(Player player){ACTIVE.get().remove(player);if(ACTIVE.get().isEmpty())ACTIVE.remove();}
    public static ItemStack finish(Player player, ItemStack stack, Supplier<ItemStack> action) {
        var current = EatingContext.current();
        if(ACTIVE.get().contains(player))return action.get();
        var cap = CapabilityHandler.get(player);
        var pending = cap == null ? null : cap.getUseSession();
        var portion = pending != null && player.getUseItem() == stack && pending.key().item().equals(stack.getItem())
                ? pending : ConsumptionSnapshot.captureIfConsumable(player, stack);
        if (portion == null) return action.get();
        if (cap != null && portion == pending) cap.setUseSession(null);
        ACTIVE.get().add(player);
        try {
        if(!player.level().isClientSide) {
            var pre=new com.lumbra.ldiediet.api.DietEvents.MealPre(player,portion,"item");
            if(com.lumbra.ldiediet.api.DietEvents.post(pre))return stack;
            portion=pre.getPortion();
        }
        ItemStack result;
        int hungerBefore = player.getFoodData().getFoodLevel();
        try (var scope = EatingContext.open(player, portion)) {
            result = action.get();
        }
        int hungerRestored = player.getFoodData().getFoodLevel() - hungerBefore;
        try (var scope = EatingContext.suspend()) { FoodEatingHandler.complete(player, portion, hungerRestored); }
        if(!player.level().isClientSide)com.lumbra.ldiediet.api.DietEvents.post(new com.lumbra.ldiediet.api.DietEvents.MealPost(player,portion,"item",hungerRestored));
        return result;
        } finally { release(player);if(pending!=null&&portion==pending)ChewingTiming.clear(player); }
    }
    public static boolean applyFood(FoodData data, ItemStack stack, LivingEntity entity) {
        Player player = entity instanceof Player direct ? direct : null;
        var frame = EatingContext.current();
        if (player == null && frame != null && frame.player().getFoodData() == data) player = frame.player();
        if (player == null) player = EatingContext.getPlayerFromData(data);
        if (player == null || player.getFoodData() != data) return false;
        ConsumptionSnapshot portion = frame != null && frame.player() == player ? frame.portion() : null;
        if (portion == null) {
            var cap = CapabilityHandler.get(player);
            portion = cap == null ? null : cap.getUseSession();
            if (portion == null || player.getUseItem() != stack || portion.key().item() != stack.getItem()) portion = ConsumptionSnapshot.capture(player, stack);
        }
        if (portion.excluded() || portion.properties() == null) return false;
        applyLevels(data, portion);
        return true;
    }
    private static void applyLevels(FoodData data, ConsumptionSnapshot portion) {
        applyLevels(data, portion, portion.hunger(), portion.saturation());
    }
    public static boolean applyRawFood(FoodData data, int hunger, float saturationModifier) {
        var frame = EatingContext.current();
        if (frame == null || frame.player().getFoodData() != data || frame.portion().excluded()) return false;
        applyLevels(data, frame.portion());
        return true;
    }
    private static void applyLevels(FoodData data, ConsumptionSnapshot portion, double hunger, double saturation) {
        var policy = portion.penalty();
        var levels = FoodPenalty.apply(data.getFoodLevel(), data.getSaturationLevel(), hunger,
                saturation*(portion.excluded()?1:portion.rules().saturation().get(portion.stage().getLevel())),
                policy.multiplier(), policy.minimumHunger(), false);
        data.setFoodLevel(levels.hunger()); data.setSaturation(levels.saturation());
    }
    public static void cake(Player player, FoodData data, int hunger, float saturationModifier) {
        cake(player,data,hunger,saturationModifier,null);
    }
    public static void cake(Player player, FoodData data, int hunger, float saturationModifier,ConsumptionSnapshot prepared) {
        var food = new FoodProperties.Builder().nutrition(hunger).saturationMod(saturationModifier).build();
        var portion = prepared==null?ConsumptionSnapshot.capture(player, new ItemStack(Items.CAKE), food):prepared;
        if(prepared==null&&!player.level().isClientSide){var pre=new com.lumbra.ldiediet.api.DietEvents.MealPre(player,portion,"cake");if(com.lumbra.ldiediet.api.DietEvents.post(pre))return;portion=pre.getPortion();}
        int hungerBefore = data.getFoodLevel();
        try (var scope = EatingContext.open(player, portion)) {
            if (portion.excluded()) data.eat(hunger, saturationModifier);
            else applyLevels(data, portion);
        }
        int restored=data.getFoodLevel()-hungerBefore;
        try (var scope = EatingContext.suspend()) { FoodEatingHandler.complete(player, portion, restored); }
        if(!player.level().isClientSide)com.lumbra.ldiediet.api.DietEvents.post(new com.lumbra.ldiediet.api.DietEvents.MealPost(player,portion,"cake",restored));
    }
    public static boolean blocks(LivingEntity target, MobEffect effect) {
        var frame = EatingContext.current();
        return frame != null && frame.player() == target && !com.lumbra.ldiediet.rewards.MobEffectSources.isApplyingOwnedEffect()
                && !frame.portion().excluded()
                && frame.portion().rules().blocks(effect,frame.portion().stage().getLevel());
    }
    public static FoodProperties properties(ItemStack stack, LivingEntity entity) {
        var frame = EatingContext.current();
        return frame != null && frame.player() == entity && !frame.portion().excluded()
                && frame.portion().key().item() == stack.getItem()
                ? frame.portion().properties() : stack.getFoodProperties(entity);
    }
    public static void stop(LivingEntity entity) {
        ChewingTiming.clear(entity);
        var cap = CapabilityHandler.get(entity);
        if (cap != null) cap.setUseSession(null);
    }
    public static boolean external(net.minecraft.server.level.ServerPlayer player,ItemStack stack,double hunger,double saturation,Runnable callback) {
        if(ACTIVE.get().contains(player)||EatingContext.current()!=null)throw new IllegalStateException("External consumption must not nest inside another portion");
        var original=stack.getFoodProperties(player);
        var portion=ConsumptionSnapshot.capture(player,stack,original==null?new FoodProperties.Builder().nutrition(0).saturationMod(0).build():original).withNutrition(hunger,saturation);
        ACTIVE.get().add(player);try {
        var pre=new com.lumbra.ldiediet.api.DietEvents.MealPre(player,portion,"external");
        if(com.lumbra.ldiediet.api.DietEvents.post(pre))return false;portion=pre.getPortion();
        int before=player.getFoodData().getFoodLevel();float beforeSaturation=player.getFoodData().getSaturationLevel();
        try(var scope=EatingContext.open(player,portion)){callback.run();
             
            var levels=FoodPenalty.apply(before,beforeSaturation,portion.hunger(),portion.saturation()*(portion.excluded()?1:portion.rules().saturation().get(portion.stage().getLevel())),portion.excluded()?1:portion.penalty().multiplier(),portion.excluded()?0:portion.penalty().minimumHunger(),false);
            player.getFoodData().setFoodLevel(levels.hunger());player.getFoodData().setSaturation(levels.saturation());
        }
        int restored=player.getFoodData().getFoodLevel()-before;
        try(var scope=EatingContext.suspend()){FoodEatingHandler.complete(player,portion,restored,true);}
        com.lumbra.ldiediet.api.DietEvents.post(new com.lumbra.ldiediet.api.DietEvents.MealPost(player,portion,"external",restored));return true;
        }finally{release(player);}
    }
}
