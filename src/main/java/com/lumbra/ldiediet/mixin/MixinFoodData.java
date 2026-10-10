package com.lumbra.ldiediet.mixin;

import com.lumbra.ldiediet.consumption.FoodConsumption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class MixinFoodData {
    @Inject(method = "eat(IF)V", at = @At("HEAD"), cancellable = true)
    private void ldiediet$rawFood(int hunger, float saturationModifier, CallbackInfo ci) {
        if (FoodConsumption.applyRawFood((FoodData) (Object) this, hunger, saturationModifier)) ci.cancel();
    }
    @Inject(method = "eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), remap = false, cancellable = true)
    private void ldiediet$eatPortion(Item item, ItemStack stack, LivingEntity entity, CallbackInfo ci) {
        if (FoodConsumption.applyFood((FoodData) (Object) this, stack, entity)) ci.cancel();
    }
}
