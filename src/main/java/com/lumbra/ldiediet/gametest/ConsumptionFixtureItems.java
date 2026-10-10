package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

 
@Mod.EventBusSubscriber(modid = LDieDiet.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConsumptionFixtureItems {
    public static final ResourceLocation DYNAMIC_FOOD = new ResourceLocation(LDieDiet.MOD_ID, "test_dynamic_food");
    @SubscribeEvent public static void register(RegisterEvent event) {
        if (!Boolean.getBoolean("ldiediet.enableConsumptionFixtures") || !event.getRegistryKey().equals(Registries.ITEM)) return;
        event.register(Registries.ITEM, helper -> helper.register(DYNAMIC_FOOD, new DynamicFood()));
    }
    private static final class DynamicFood extends Item {
        DynamicFood() { super(new Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(.3f).build())); }
        @Override public FoodProperties getFoodProperties(ItemStack stack, LivingEntity entity) {
            var tag = stack.getTag();
            int hunger = tag != null && tag.contains("FixtureNutrition") ? tag.getInt("FixtureNutrition") : 4;
            float saturation = tag != null && tag.contains("FixtureSaturation") ? tag.getFloat("FixtureSaturation") : .3f;
            var builder = new FoodProperties.Builder().nutrition(hunger).saturationMod(saturation);
            if (tag != null && tag.contains("FixtureAmplifier")) {
                int amplifier = tag.getInt("FixtureAmplifier");
                builder.effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, amplifier), 1);
            }
            if (tag != null && tag.getBoolean("FixturePoison")) builder.effect(() -> new MobEffectInstance(MobEffects.POISON, 80), 1);
            return builder.build();
        }
    }
}
