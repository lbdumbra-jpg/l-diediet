package com.lumbra.ldiediet.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.illusivesoulworks.diet.client.DietClientEventsListener", remap = false)
public abstract class DietClientEventsMixin {
    @Inject(method = {"initGui", "tick", "tooltip", "renderTooltip"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ldiediet$disableUi(CallbackInfo ci) { ci.cancel(); }
}
