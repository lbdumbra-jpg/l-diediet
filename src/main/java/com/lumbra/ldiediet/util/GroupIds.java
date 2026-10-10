package com.lumbra.ldiediet.util;

import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;

 
public final class GroupIds {
    private GroupIds() {}
    public static String canonical(String value) { return canonical(value, LDieDiet.MOD_ID); }
    public static String canonical(String value, String namespace) {
        if (value == null || value.isBlank()) return "";
        ResourceLocation id = ResourceLocation.tryParse(value.contains(":") ? value : namespace + ":" + value);
        return id == null ? "" : id.toString();
    }
    public static String namespace(String group) {
        ResourceLocation id = ResourceLocation.tryParse(canonical(group));
        return id == null ? LDieDiet.MOD_ID : id.getNamespace();
    }
}
