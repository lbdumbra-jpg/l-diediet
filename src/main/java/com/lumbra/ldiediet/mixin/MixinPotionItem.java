package com.lumbra.ldiediet.mixin;

import com.lumbra.ldiediet.consumption.FoodConsumption;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PotionItem.class)
public abstract class MixinPotionItem {
    @Redirect(method = "finishUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"))
    private void ldiediet$filterInstant(MobEffect effect, Entity direct, Entity indirect, LivingEntity target,
                                       int amplifier, double factor) {
        if (!FoodConsumption.blocks(target, effect)) effect.applyInstantenousEffect(direct, indirect, target, amplifier, factor);
    }
}
