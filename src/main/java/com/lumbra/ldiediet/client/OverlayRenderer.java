package com.lumbra.ldiediet.client;

import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.HashSet;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class OverlayRenderer {
    private PostChain owned;
    private final Set<ResourceLocation> missing = new HashSet<>();
    private static long reloadGeneration;
    private long generation;
    public static void resourcesReloaded() { reloadGeneration++; }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        var renderer = mc.gameRenderer;
        var accessor = (GameRendererAccessor) renderer;
        PostChain current = accessor.ldiediet$getPostEffect();
        if (generation != reloadGeneration) { missing.clear(); generation = reloadGeneration; }
        if (current != owned) owned = null;
        if (mc.level == null || mc.player == null) {
            if (owned != null) renderer.shutdownEffect();
            owned = null; missing.clear(); return;
        }
        if (mc.isPaused() || current != null && current != owned) return;
        IFoodTolerance cap = CapabilityHandler.get(mc.player);
        ResourceLocation desired = cap == null ? null : desired(cap);
        if (desired == null) {
            if (owned != null) renderer.shutdownEffect();
            owned = null; return;
        }
        if (owned != null && desired.toString().equals(owned.getName()) && accessor.ldiediet$isEffectActive()) return;
        if (missing.contains(desired)) return;
        if (mc.getResourceManager().getResource(desired).isEmpty()) {
            if (missing.add(desired)) LDieDiet.LOGGER.warn("Missing reward shader {}", desired);
            if (owned != null) renderer.shutdownEffect();
            owned = null; return;
        }
        try {
            renderer.loadEffect(desired);
            owned = accessor.ldiediet$getPostEffect();
        } catch (RuntimeException e) {
            if (missing.add(desired)) LDieDiet.LOGGER.warn("Cannot load reward shader {}", desired, e);
            owned = accessor.ldiediet$getPostEffect();
        }
    }

    private ResourceLocation desired(IFoodTolerance cap) {
        for (var effect : cap.getActiveEffects()) {
            if (!"overlay".equals(effect.getEffectType()) || !effect.isCurrentlyActive()) continue;
            String id = effect.getEffectId();
            String path = switch (id.toLowerCase(java.util.Locale.ROOT)) {
                case "grayscale", "desaturate", "bw", "b&w" -> UiAssets.DESATURATE_SHADER;
                default -> id.contains("/") ? id : UiAssets.postShader(id);
            };
            ResourceLocation result = ResourceLocation.tryParse(path);
            if (result != null) return result;
        }
        return null;
    }
}
