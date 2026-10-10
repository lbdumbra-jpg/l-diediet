package com.lumbra.ldiediet.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodKeyTest {
    @Test void fullNamespacesAndVariantsSurviveSerialization() {
        var key = new FoodKey(new ResourceLocation("example", "soup"), "another:rich");
        var tag = new CompoundTag(); key.write(tag);
        assertEquals(key, FoodKey.read(tag));
        assertNotEquals(key, new FoodKey(new ResourceLocation("other", "soup"), "another:rich"));
        assertNotEquals(key, new FoodKey(key.itemId(), "other:rich"));
    }
}
