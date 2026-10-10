package com.lumbra.ldiediet.client;

import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;

 
public final class UiAssets {
    public static final String DESATURATE_SHADER =
            LDieDiet.MOD_ID + ":shaders/post/desaturate.json";

    private UiAssets() {}

    public static String postShader(String overlayId) {
        ResourceLocation id = ResourceLocation.tryParse(overlayId.contains(":") ? overlayId : LDieDiet.MOD_ID + ":" + overlayId);
        if (id == null) return LDieDiet.MOD_ID + ":shaders/post/" + overlayId + ".json";
        return id.getNamespace() + ":shaders/post/" + id.getPath() + ".json";
    }
}
