package com.lumbra.ldiediet.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

 
@Pseudo
@Mixin(targets = "com.illusivesoulworks.diet.common.capability.PlayerDietTracker", remap = false)
public abstract class DietTrackerMixin {
    @Inject(method = {"tick", "consume", "applyEffects", "decay", "apply", "sync", "setActive", "copy"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ldiediet$disableGameplay(CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void ldiediet$inactive(CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(false); }
}
