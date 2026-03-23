package com.levbu.ldiediet.client;

import com.levbu.ldiediet.LDieDiet;
import com.levbu.ldiediet.capability.ActiveGroupEffect;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class OverlayRenderer {

    private static final String DESATURATE_SHADER = "ldiediet:shaders/post/desaturate.json";

    private final Set<String> myShaders = new HashSet<>();
 
    private String lastAppliedShader = null;

    public OverlayRenderer() {
        myShaders.add(DESATURATE_SHADER);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null) return;

        Player player = mc.player;
        if (player == null) return;

        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null) return;

        GameRenderer renderer = mc.gameRenderer;
        GameRendererAccessor accessor = (GameRendererAccessor) renderer;

        PostChain postEffect = accessor.ldiediet$getPostEffect();
        String rendererShader = postEffect == null ? null : postEffect.getName();

        if (rendererShader != null && !myShaders.contains(rendererShader)) {
            return;
        }

        if (rendererShader == null && lastAppliedShader != null) {
            lastAppliedShader = null;
        }

        String newShader = getDesiredShader(cap);

        boolean effectActive = accessor.ldiediet$isEffectActive();

        if (newShader != null && (!newShader.equals(rendererShader) || !effectActive)) {
            renderer.loadEffect(ResourceLocation.tryParse(newShader));
            lastAppliedShader = newShader;
        } else if (rendererShader != null && newShader == null) {
           
            renderer.shutdownEffect();
            lastAppliedShader = null;
        }
    }

   
    private String getDesiredShader(IFoodTolerance cap) {
        List<ActiveGroupEffect> effects = cap.getActiveEffects();
        for (ActiveGroupEffect effect : effects) {
            if ("overlay".equals(effect.getEffectType()) && effect.isCurrentlyActive()) {
                return resolveShaderPath(effect.getEffectId());
            }
        }
        return null;
    }

    
    private String resolveShaderPath(String overlayId) {
        if ("grayscale".equalsIgnoreCase(overlayId) || "desaturate".equalsIgnoreCase(overlayId)
                || "bw".equalsIgnoreCase(overlayId) || "b&w".equalsIgnoreCase(overlayId)) {
            return DESATURATE_SHADER;
        } else if (overlayId.contains(":") && overlayId.contains("/")) {
            return overlayId;
        } else if (overlayId.contains(":")) {
            ResourceLocation loc = new ResourceLocation(overlayId);
            return loc.getNamespace() + ":shaders/post/" + loc.getPath() + ".json";
        } else {
            return "ldiediet:shaders/post/" + overlayId + ".json";
        }
    }
}
