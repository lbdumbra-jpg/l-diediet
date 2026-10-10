package com.lumbra.ldiediet.mixin;

import com.lumbra.ldiediet.consumption.FoodConsumption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.FoodProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    @org.spongepowered.asm.mixin.Shadow protected abstract void triggerItemUseEffects(ItemStack stack,int count);
    @Inject(method="updateUsingItem",at=@At("HEAD"))
    private void ldiediet$chewThroughWholeUse(ItemStack stack,CallbackInfo ci) {
        LivingEntity entity=(LivingEntity)(Object)this;
        if(com.lumbra.ldiediet.consumption.ChewingTiming.isSlowed(entity,stack)
                && com.lumbra.ldiediet.consumption.ChewingTiming.shouldPulse(entity,stack)) triggerItemUseEffects(stack,5);
    }
    @Redirect(method="updateUsingItem",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;triggerItemUseEffects(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void ldiediet$avoidLateSoundBurst(LivingEntity entity,ItemStack stack,int count) {
        if(!com.lumbra.ldiediet.consumption.ChewingTiming.isSlowed(entity,stack))triggerItemUseEffects(stack,count);
    }
    @Unique private com.lumbra.ldiediet.consumption.ConsumptionSnapshot ldiediet$finishingPortion;
    @Redirect(method = "completeUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack ldiediet$rememberFinish(ItemStack stack, net.minecraft.world.level.Level level, LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            var cap = com.lumbra.ldiediet.capability.CapabilityHandler.get(player);
            ldiediet$finishingPortion = cap == null ? null : cap.getUseSession();
            if (ldiediet$finishingPortion == null) {
                ldiediet$finishingPortion = com.lumbra.ldiediet.consumption.ConsumptionSnapshot.captureIfConsumable(player, stack);
                if (cap != null) cap.setUseSession(ldiediet$finishingPortion);
            }
        }
        try { return stack.finishUsingItem(level, entity); }
        catch (RuntimeException | Error e) { ldiediet$finishingPortion = null; throw e; }
    }
    @Redirect(method = "completeUsingItem", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onItemUseFinish(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack ldiediet$finishEvent(LivingEntity entity, ItemStack original, int duration, ItemStack result) {
        var portion = ldiediet$finishingPortion; ldiediet$finishingPortion = null;
        if (portion != null && entity instanceof net.minecraft.world.entity.player.Player player) {
            try (var scope = com.lumbra.ldiediet.util.EatingContext.open(player, portion)) {
                return net.minecraftforge.event.ForgeEventFactory.onItemUseFinish(entity, original, duration, result);
            }
        }
        return net.minecraftforge.event.ForgeEventFactory.onItemUseFinish(entity, original, duration, result);
    }
    @Redirect(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean ldiediet$filterAndRememberPotion(LivingEntity entity, MobEffectInstance effect) {
        if (FoodConsumption.blocks(entity, effect.getEffect())) return false;
        boolean accepted = entity.canBeAffected(effect);
        if (accepted) com.lumbra.ldiediet.rewards.MobEffectSources.captureExternal(entity, effect);
        return accepted;
    }
    @Redirect(method = "addEatEffect", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/item/ItemStack;getFoodProperties(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/food/FoodProperties;"))
    private FoodProperties ldiediet$portionProperties(ItemStack stack, LivingEntity entity) {
        return FoodConsumption.properties(stack, entity);
    }
    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void ldiediet$clearUse(CallbackInfo ci) {
        ldiediet$finishingPortion = null;
        FoodConsumption.stop((LivingEntity) (Object) this);
    }
}
