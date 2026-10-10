package com.lumbra.ldiediet.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.illusivesoulworks.diet.common.capability.DietCapabilityEventsListener", remap = false)
public abstract class DietGameplayEventsMixin {
    @Inject(method = {"playerClone", "playerRespawned", "playerLoggedIn", "playerDimensionTravel", "playerTick", "finishItemUse"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ldiediet$disableEvents(CallbackInfo ci) { ci.cancel(); }
}
