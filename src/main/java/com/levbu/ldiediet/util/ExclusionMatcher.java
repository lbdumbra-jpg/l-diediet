package com.levbu.ldiediet.util;

import com.levbu.ldiediet.data.ExclusionManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ExclusionMatcher {

    public static final TagKey<Item> EXCLUDED_FOODS_TAG = ItemTags.create(new ResourceLocation("ldiediet", "excluded_foods"));

    public static boolean isExcluded(Item item) {
        return BuiltInRegistries.ITEM.getHolder(
                BuiltInRegistries.ITEM.getResourceKey(item).orElse(null))
                .map(holder -> holder.is(EXCLUDED_FOODS_TAG))
                .orElse(false);
    }

    public static int getStageOverride(Item item) {
        return ExclusionManager.getInstance().getStageOverride(item);
    }
}
