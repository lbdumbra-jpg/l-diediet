package com.lumbra.ldiediet.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

 
public final class NbtMatcher {
    private NbtMatcher() {}

    public static boolean matches(CompoundTag actual, CompoundTag expected) {
        if (expected == null || expected.isEmpty()) return true;
        if (actual == null) return false;
        for (String key : expected.getAllKeys()) {
            if (!actual.contains(key) || !matchesTag(actual.get(key), expected.get(key))) return false;
        }
        return true;
    }

    private static boolean matchesTag(Tag actual, Tag expected) {
        if (actual == null || expected == null || actual.getId() != expected.getId()) return false;
        if (expected instanceof CompoundTag expectedCompound && actual instanceof CompoundTag actualCompound) {
            return matches(actualCompound, expectedCompound);
        }
        if (expected instanceof ListTag expectedList && actual instanceof ListTag actualList) {
            if (actualList.size() < expectedList.size()) return false;
            for (int i = 0; i < expectedList.size(); i++) {
                if (!matchesTag(actualList.get(i), expectedList.get(i))) return false;
            }
            return true;
        }
        return expected.equals(actual);
    }
}
