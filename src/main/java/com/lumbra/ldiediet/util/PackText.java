package com.lumbra.ldiediet.util;
import net.minecraft.network.chat.Component;
 
public final class PackText {
    private PackText() {}
    public static Component of(String key, String fallback) {
        return key == null || key.isBlank() ? Component.literal(fallback == null ? "" : fallback)
                : Component.translatableWithFallback(key, fallback == null || fallback.isBlank() ? key : fallback);
    }
}
