package com.levbu.ldiediet.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("postEffect")
    PostChain ldiediet$getPostEffect();

    @Accessor("effectActive")
    boolean ldiediet$isEffectActive();
}
