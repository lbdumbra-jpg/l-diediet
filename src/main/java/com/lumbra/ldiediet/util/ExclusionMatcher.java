package com.lumbra.ldiediet.util;

import com.lumbra.ldiediet.data.DietConfigManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ExclusionMatcher {

    public static final TagKey<Item> EXCLUDED_FOODS_TAG = ItemTags.create(new ResourceLocation("ldiediet", "excluded_foods"));

    public static boolean isExcluded(Item item) {
        return isExcluded(new ItemStack(item));
    }

    public static boolean isExcluded(ItemStack stack) {
        return isExcluded(stack, false);
    }
    public static boolean isExcluded(ItemStack stack, boolean client) {
        return stack.is(EXCLUDED_FOODS_TAG) || (client ? DietConfigManager.isClientExcluded(stack) : DietConfigManager.isExcluded(stack));
    }

}
