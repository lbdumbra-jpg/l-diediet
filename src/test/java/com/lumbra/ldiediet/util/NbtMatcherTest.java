package com.lumbra.ldiediet.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtMatcherTest {
    @Test
    void matchesPartialNestedCompounds() {
        CompoundTag actual = new CompoundTag();
        actual.putString("Potion", "minecraft:water");
        CompoundTag display = new CompoundTag();
        display.putString("Name", "Water");
        display.putInt("Color", 42);
        actual.put("display", display);

        CompoundTag expected = new CompoundTag();
        CompoundTag expectedDisplay = new CompoundTag();
        expectedDisplay.putInt("Color", 42);
        expected.put("display", expectedDisplay);
        assertTrue(NbtMatcher.matches(actual, expected));

        expectedDisplay.putInt("Color", 7);
        assertFalse(NbtMatcher.matches(actual, expected));
    }

    @Test
    void listPrefixCanBeMatched() {
        ListTag actualList = new ListTag();
        actualList.add(StringTag.valueOf("a"));
        actualList.add(StringTag.valueOf("b"));
        CompoundTag actual = new CompoundTag();
        actual.put("values", actualList);

        ListTag expectedList = new ListTag();
        expectedList.add(StringTag.valueOf("a"));
        CompoundTag expected = new CompoundTag();
        expected.put("values", expectedList);
        assertTrue(NbtMatcher.matches(actual, expected));
    }
}
