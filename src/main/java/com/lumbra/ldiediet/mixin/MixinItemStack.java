package com.lumbra.ldiediet.mixin;

import com.lumbra.ldiediet.consumption.FoodConsumption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStack.class)
public abstract class MixinItemStack implements com.lumbra.ldiediet.consumption.UseDurationOverride {
    @org.spongepowered.asm.mixin.Unique private int ldiediet$duration;
    public void ldiediet$setDuration(int ticks) { ldiediet$duration=ticks; }
    public int ldiediet$getDuration() { return ldiediet$duration; }
    @org.spongepowered.asm.mixin.injection.Inject(method="getUseDuration",at=@At("RETURN"),cancellable=true)
    private void ldiediet$actualDuration(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        if(ldiediet$duration>0)cir.setReturnValue(ldiediet$duration);
    }
    @Redirect(method = "finishUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack ldiediet$consume(Item item, ItemStack stack, Level level, LivingEntity entity) {
        return entity instanceof Player player
                ? FoodConsumption.finish(player, stack, () -> item.finishUsingItem(stack, level, entity))
                : item.finishUsingItem(stack, level, entity);
    }
}
