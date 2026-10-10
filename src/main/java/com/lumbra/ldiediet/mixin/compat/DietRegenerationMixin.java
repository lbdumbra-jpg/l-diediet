package com.lumbra.ldiediet.mixin.compat;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.illusivesoulworks.diet.common.util.DietRegeneration", remap = false)
public abstract class DietRegenerationMixin {
    @Inject(method = "hasRegen", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ldiediet$vanillaRegeneration(Player player, boolean vanilla,
                                                    CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(vanilla);
    }
}
