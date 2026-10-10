package com.lumbra.ldiediet.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

 
public final class NbtVariantIds {
    private NbtVariantIds() {}
    public static String fromMatch(CompoundTag match) {
        StringBuilder canonical = new StringBuilder();
        append(match, canonical);
        try {
            return "nbt:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void append(Tag tag, StringBuilder output) {
        output.append(tag.getId()).append(':');
        if (tag instanceof CompoundTag compound) {
            output.append('{');
            for (String key : compound.getAllKeys().stream().sorted().toList()) {
                output.append(key.length()).append(':').append(key);
                append(compound.get(key), output);
            }
            output.append('}');
        } else if (tag instanceof ListTag list) {
            output.append('[');
            for (Tag child : list) append(child, output);
            output.append(']');
        } else {
            String value = tag.toString();
            output.append(value.length()).append(':').append(value);
        }
    }
}
