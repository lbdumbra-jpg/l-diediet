package com.levbu.ldiediet.mixin;

import com.levbu.ldiediet.LDieDietServerConfig;
import com.levbu.ldiediet.ToleranceStage;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.FoodToleranceEntry;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.util.EatingContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для FoodData - применяет штрафы к голоду/насыщению.
 */
@Mixin(FoodData.class)
public abstract class MixinFoodData {

    @Shadow
    private int foodLevel;
    @Shadow
    private float saturationLevel;

    @Unique
    private int ldiediet$lastFoodLevel;
    @Unique
    private float ldiediet$lastSaturationLevel;

    @Inject(method = "eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void ldiediet$beforeEat(Item item, ItemStack stack, CallbackInfo ci) {
        ldiediet$lastFoodLevel = foodLevel;
        ldiediet$lastSaturationLevel = saturationLevel;
    }

    @Inject(method = "eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private void ldiediet$afterEat(Item item, ItemStack stack, CallbackInfo ci) {
        Player player = EatingContext.getPlayerFromData((FoodData)(Object)this);
        if (player == null) player = EatingContext.getPlayer();

        if (player == null) {
            return;
        }

        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null)
            return;

        FoodToleranceEntry entry = cap.getEntryOrNull(item);
        ToleranceStage stage = ToleranceStage.fromEntry(entry, item, player);

        if (stage == ToleranceStage.STAGE_0)
            return;

        int hungerGained = foodLevel - ldiediet$lastFoodLevel;
        float saturationGained = saturationLevel - ldiediet$lastSaturationLevel;

        if (hungerGained <= 0 && saturationGained <= 0) {
            return;
        }

        float multiplier = stage.getHungerMultiplier();

        int penalizedHunger = (int) Math.max(
                1,
                Math.floor(hungerGained * multiplier * 2) / 2);
        
        double minConfig = LDieDietServerConfig.MIN_HUNGER_RESTORED.get();
        if (penalizedHunger < minConfig) {
            penalizedHunger = (int) Math.max(1, Math.ceil(minConfig));
        }

        if (hungerGained > 0 && penalizedHunger < 1) penalizedHunger = 1;

        float penalizedSaturation = saturationGained;
        if (LDieDietServerConfig.ENABLE_SATURATION_PENALTY.get()) {
            penalizedSaturation = stage.getLevel() >= 3 ? 0 : saturationGained * multiplier;
        }

        foodLevel = Math.max(0, foodLevel - (hungerGained - penalizedHunger));
        saturationLevel = Math.max(0, saturationLevel - (saturationGained - penalizedSaturation));
    }
}
