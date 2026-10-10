package com.lumbra.ldiediet.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.illusivesoulworks.diet.DietForgeMod$ClientModEvents", remap = false)
public abstract class DietKeysMixin {
    @Inject(method = "registerKeys", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ldiediet$disableKey(CallbackInfo ci) { ci.cancel(); }
}
