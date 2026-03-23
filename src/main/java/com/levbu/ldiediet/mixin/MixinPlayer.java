package com.levbu.ldiediet.mixin;

import com.levbu.ldiediet.LDieDiet;
import com.levbu.ldiediet.compat.DietIntegration;
import com.levbu.ldiediet.util.EatingContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Миксин для Player — устанавливает контекст еды и сохраняет Diet-значения ДО еды.
 */
@Mixin(Player.class)
public abstract class MixinPlayer {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ldiediet$onInit(Level level, net.minecraft.core.BlockPos pos, float yaw,
                                  com.mojang.authlib.GameProfile profile, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        EatingContext.link(player.getFoodData(), player);
    }

    @Inject(method = "eat", at = @At("HEAD"))
    private void ldiediet$beforeEat(Level level, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        Player player = (Player) (Object) this;
        EatingContext.set(player, stack.getItem());

        // Сохраняем текущие Diet-значения ДО еды (для коррекции после)
        if (LDieDiet.isDietLoaded()) {
            EatingContext.setDietValues(DietIntegration.captureDietValues(player, stack));
        }
    }
}
