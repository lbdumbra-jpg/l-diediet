package com.lumbra.ldiediet.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

 
public record FoodKey(ResourceLocation itemId, String variantId) {
    public FoodKey {
        java.util.Objects.requireNonNull(itemId);
        variantId = variantId == null ? "" : variantId;
    }
    public static FoodKey of(Item item) { return new FoodKey(BuiltInRegistries.ITEM.getKey(item), ""); }
    public Item item() { return BuiltInRegistries.ITEM.get(itemId); }
    public void write(CompoundTag tag) {
        tag.putString("item", itemId.toString());
        if (!variantId.isEmpty()) tag.putString("variant_id", variantId);
    }
    public static FoodKey read(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("item"));
        return id == null ? null : new FoodKey(id, tag.getString("variant_id"));
    }
}
