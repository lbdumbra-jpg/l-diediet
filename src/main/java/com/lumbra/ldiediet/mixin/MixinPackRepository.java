package com.lumbra.ldiediet.mixin;

import com.lumbra.ldiediet.compat.DietResourceCache;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

 
@Mixin(PackRepository.class)
public abstract class MixinPackRepository {
    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void ldiediet$keepBaselineBelowDatapacks(Collection<String> ids, CallbackInfoReturnable<List<Pack>> cir) {
        var selected = cir.getReturnValue();
        for (int i = 1; i < selected.size(); i++) {
            if (selected.get(i).getId().equals(DietResourceCache.PACK_ID)) {
                var ordered = new ArrayList<>(selected);
                ordered.add(0, ordered.remove(i));
                cir.setReturnValue(List.copyOf(ordered));
                return;
            }
        }
    }
}
