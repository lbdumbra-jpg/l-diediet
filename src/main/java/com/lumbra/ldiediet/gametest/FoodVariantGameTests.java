package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.RewardHandler;
import com.lumbra.ldiediet.util.EatingContext;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FoodVariantGameTests {
    private static final String TEMPLATE = "bastion/blocks/air";
    private static final String BATCH = "ldiediet_variants";
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .001, message + ": expected " + expected + ", got " + actual);
    }
    private static double expectedRecovery(double m,double source,double target) {
        double t=(target-1)/3;
        return Math.max(0,m-(1+.25*(source-target))*(.125+.0056*m*m)/((1-t)/.94+t/2.4));
    }
    private static ItemStack dynamic(String flavor) {
        var stack = new ItemStack(ForgeRegistries.ITEMS.getValue(ConsumptionFixtureItems.DYNAMIC_FOOD));
        stack.getOrCreateTag().putString("Flavor", flavor); return stack;
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void recoveryUsesTheComplexityOfEachStoredVariant(GameTestHelper h) throws Exception {
        ConsumptionLogicGameTests.withConfig(h,
                "{\"minecraft:bread\":{\"cci\":2},\"ldiediet:test_dynamic_food\":{\"cci\":1,\"nbt_filters\":[{\"variant_id\":\"test:complex\",\"match\":\"{Flavor:'blue'}\",\"cci\":3}]}}",
                () -> {
            var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var red = DietConfigManager.getFoodKey(dynamic("red"), false);
                var blue = DietConfigManager.getFoodKey(dynamic("blue"), false);
                var cap = CapabilityHandler.get(player);
                cap.setMonotony(red, 10); cap.setMonotony(blue, 10);
                player.getFoodData().setFoodLevel(0); player.getFoodData().setSaturation(0);
                new ItemStack(Items.BREAD).finishUsingItem(player.level(), player);
                near(h, cap.getMonotony(red), expectedRecovery(10,2,1), "Simple food recovery used the eaten food CCI");
                near(h, cap.getMonotony(blue), expectedRecovery(10,2,3), "Complex variant recovery lost its own CCI");
                h.assertTrue(cap.getMonotony(blue) < cap.getMonotony(red), "Complex foods must recover faster than simple foods");
                h.succeed();
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void recoveryPreservesStoredFoodComplexityAfterReload(GameTestHelper h) throws Exception {
        ConsumptionLogicGameTests.withConfig(h,
                "{\"minecraft:bread\":{\"cci\":2.5,\"cci_source\":\"auto\"},\"minecraft:carrot\":{\"cci\":1},\"minecraft:cooked_beef\":{\"cci\":3}}",
                () -> {
            var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                double breadCci=DietConfigManager.getOrCalculateCCI(new ItemStack(Items.BREAD),p.getServer());
                h.assertTrue(breadCci>1,"Bread fixture has no complex recipe");
                var cap=CapabilityHandler.get(p);cap.setMonotony(Items.BREAD,10);cap.setMonotony(Items.CARROT,10);
                DietConfigManager.reloadRecipes(p.getServer());
                p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);
                new ItemStack(Items.COOKED_BEEF).finishUsingItem(p.level(),p);
                near(h,cap.getMonotony(Items.BREAD),expectedRecovery(10,3,breadCci),"Reload lost the stored food complexity during recovery");
                h.assertTrue(cap.getMonotony(Items.BREAD)<cap.getMonotony(Items.CARROT),"Reload made complex and simple foods recover equally");
                h.succeed();
            }finally{LDieDietGameTests.removePlayer(p);}
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void variantsHaveIndependentMonotonyRecoveryAndHistory(GameTestHelper h) throws Exception {
        ConsumptionLogicGameTests.withConfig(h, "{\"ldiediet:test_dynamic_food\":{\"cci\":1,\"food_value\":10,\"nbt_filters\":[{\"variant_id\":\"first:red\",\"match\":\"{Flavor:'red'}\"},{\"variant_id\":\"second:blue\",\"match\":\"{Flavor:'blue'}\",\"cci\":3}]}}", () -> {
            var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var red = dynamic("red"); var blue = dynamic("blue");
                FoodKey redKey = DietConfigManager.getFoodKey(red, false), blueKey = DietConfigManager.getFoodKey(blue, false);
                var cap = CapabilityHandler.get(player); cap.setMonotony(redKey, 12); cap.setMonotony(blueKey, 3);
                player.getFoodData().setFoodLevel(0); player.getFoodData().setSaturation(0);
                red.finishUsingItem(player.level(), player);
                near(h, cap.getMonotony(redKey), 12.36, "Red variant did not grow from its own M");
                near(h, cap.getMonotony(blueKey), expectedRecovery(3,1,3), "CCI1 recovery of blue variant failed");
                blue.finishUsingItem(player.level(), player);
                h.assertTrue(cap.getMonotony(redKey) < 12.36, "Complex blue variant did not actively recover red");
                near(h, cap.getMonotony(blueKey), expectedRecovery(3,1,3) + 5 / (125.0 / 9 - 6), "Blue inherited red monotony");
                h.assertTrue(cap.getEatingHistory().size() == 2 && cap.getEatingHistory().get(0).getKey().equals(blueKey)
                        && cap.getEatingHistory().get(1).getKey().equals(redKey), "History merged distinct variants");
                var group = new FoodGroupDefinition(new ResourceLocation("test", "variants"), "variants", "", "normal", "",
                        List.of(ConsumptionFixtureItems.DYNAMIC_FOOD.toString()), List.of(), 2, true, null, null, true);
                h.assertTrue(RewardHandler.countEligibleHistory(cap.getEatingHistory(), group, 0) == 2, "Unique food count discarded a configured variant");
                var restored = new FoodToleranceCapability(); restored.deserializeNBT(cap.serializeNBT());
                near(h, restored.getMonotony(redKey), cap.getMonotony(redKey), "NBT lost red M");
                near(h, restored.getMonotony(blueKey), cap.getMonotony(blueKey), "NBT lost blue M");
                h.assertTrue(restored.getEatingHistory().get(0).getKey().equals(blueKey), "NBT lost variant history"); h.succeed();
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void oldItemMonotonySeedsVariantsWithoutOverwritingThem(GameTestHelper h) {
        var old = new CompoundTag(); old.putInt("schema_version", 3);
        ListTag entries = new ListTag(); CompoundTag entry = new CompoundTag(); entry.putString("item", "minecraft:potion");
        CompoundTag data = new CompoundTag(); data.putFloat("monotony", 7); data.putLong("time", 88); entry.put("data", data); entries.add(entry);
        old.put("monotonyEntries", entries);
        var cap = new FoodToleranceCapability(); cap.deserializeNBT(old);
        var red = new FoodKey(new ResourceLocation("minecraft", "potion"), "first:red");
        var blue = new FoodKey(red.itemId(), "second:blue");
        near(h, cap.getMonotony(red), 7, "Legacy M was not exposed to a new variant");
        cap.addMonotony(red, 1); cap.recordEaten(red, 99);
        near(h, cap.getMonotony(blue), 7, "First variant changed the inherited value of another variant");
        var restored = new FoodToleranceCapability(); restored.deserializeNBT(cap.serializeNBT());
        near(h, restored.getMonotony(red), 8, "Migration overwrote an existing variant on reload");
        near(h, restored.getMonotony(blue), 7, "Migration lost the unused variant seed");
        h.assertTrue(restored.getLastEatenGameTime(red) == 99 && restored.getLastEatenGameTime(blue) == 88, "Migration lost inherited/per-variant timestamps");
        restored.setMonotony(blue, 2); near(h, restored.getMonotony(red), 8, "Setting blue changed red");
        restored.clearMonotony(); near(h, restored.getMonotony(blue), 0, "Reset left legacy seeds"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void exclusionBypassesEveryConsequenceAndCleansTheSession(GameTestHelper h) throws Exception {
        ConsumptionLogicGameTests.withConfig(h, "{\"ldiediet:test_dynamic_food\":{\"cci\":3,\"is_exclusion\":true,\"nutrients\":{\"test:excluded\":1}}}", () -> {
            var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var stack = dynamic("excluded"); stack.getOrCreateTag().putInt("FixtureAmplifier", 3);
                Item item = stack.getItem(); var cap = CapabilityHandler.get(player); cap.setMonotony(item, 20);
                player.getFoodData().setFoodLevel(0); player.getFoodData().setSaturation(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack); player.startUsingItem(InteractionHand.MAIN_HAND);
                h.assertTrue(player.getUseItemRemainingTicks() == 32, "Excluded food changed duration");
                stack.finishUsingItem(player.level(), player);
                near(h, player.getFoodData().getFoodLevel(), 4, "Excluded food changed hunger");
                near(h, player.getFoodData().getSaturationLevel(), 2.4, "Excluded food changed saturation");
                h.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "Excluded food blocked its buff");
                near(h, cap.getMonotony(item), 20, "Excluded food changed M");
                near(h, cap.getNutrientValue("test:excluded"), 0, "Excluded food added nutrients");
                h.assertTrue(cap.getHistoryTotalCount() == 0 && cap.getUseSession() == null && EatingContext.current() == null,
                        "Excluded food counted a meal or leaked state"); h.succeed();
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void missingModFoodNamespacesSurvivePlayerNbt(GameTestHelper h) {
        var first = new FoodKey(new ResourceLocation("missing_first", "soup"), "flavor:rich");
        var second = new FoodKey(new ResourceLocation("missing_second", "soup"), "flavor:rich");
        var cap = new FoodToleranceCapability(); cap.setMonotony(first, 9); cap.setMonotony(second, 3);
        cap.addHistory(first); cap.addHistory(second);
        var restored = new FoodToleranceCapability(); restored.deserializeNBT(cap.serializeNBT());
        near(h, restored.getMonotony(first), 9, "NBT dropped an absent food mod or its namespace");
        near(h, restored.getMonotony(second), 3, "NBT merged food namespaces");
        h.assertTrue(restored.getEatingHistory().get(0).getKey().equals(second), "NBT dropped absent-mod history"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void explicitAndLegacyVariantIdsSurviveRuleReorderingAndClampCci(GameTestHelper h) throws Exception {
        String first = "{\"minecraft:potion\":{\"cci\":1,\"nbt_filters\":[{\"match\":\"{Potion:'minecraft:healing'}\",\"cci\":100},{\"variant_id\":\"another:harm\",\"match\":\"{Potion:'minecraft:harming'}\",\"cci\":-100}]}}";
        String second = "{\"minecraft:potion\":{\"cci\":1,\"nbt_filters\":[{\"variant_id\":\"another:harm\",\"match\":\"{Potion:'minecraft:harming'}\",\"cci\":-100},{\"match\":\"{Potion:'minecraft:healing'}\",\"cci\":100}]}}";
        ConsumptionLogicGameTests.withConfig(h, first, () -> {
            var heal = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
            var harm = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HARMING);
            var healKey = DietConfigManager.getFoodKey(heal, false);
            h.assertTrue(healKey.variantId().startsWith("nbt:"), "Legacy rule has no stable generated ID");
            h.assertTrue(DietConfigManager.getFoodKey(harm, false).variantId().equals("another:harm"), "Explicit variant namespace was stripped");
            near(h, DietConfigManager.getCCI(heal), 4, "Variant CCI exceeded the configured maximum");
            near(h, DietConfigManager.getCCI(harm), 1, "Variant CCI fell below the configured minimum");
            var cap = new FoodToleranceCapability(); cap.setMonotony(healKey, 11);
            try {
                ConsumptionLogicGameTests.withConfig(h, second, () -> {
                    h.assertTrue(DietConfigManager.getFoodKey(heal, false).equals(healKey), "Moving the rule changed its legacy ID");
                    near(h, cap.getMonotony(DietConfigManager.getFoodKey(heal, false)), 11, "Moving the rule orphaned saved M");
                });
            } catch (Exception e) { throw new IllegalStateException(e); }
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void commandsSelectNbtVariantsAndLegacySetUpdatesTheWholeItem(GameTestHelper h) throws Exception {
        ConsumptionLogicGameTests.withConfig(h, "{\"ldiediet:test_dynamic_food\":{\"cci\":1,\"nbt_filters\":[{\"variant_id\":\"test:red\",\"match\":\"{Flavor:'red'}\"},{\"variant_id\":\"test:blue\",\"match\":\"{Flavor:'blue'}\"}]}}", () -> {
            var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var cap = CapabilityHandler.get(player);
                var red = DietConfigManager.getFoodKey(dynamic("red"), false);
                var blue = DietConfigManager.getFoodKey(dynamic("blue"), false);
                cap.setMonotony(blue, 3);
                var commands = player.getServer().getCommands(); var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
                h.assertTrue(commands.performPrefixedCommand(source, "ldiediet set_monotony @s ldiediet:test_dynamic_food{Flavor:'red'} 12") > 0,
                        "NBT variant command failed");
                near(h, cap.getMonotony(red), 12, "NBT command did not select red");
                near(h, cap.getMonotony(blue), 3, "NBT command changed blue");
                commands.performPrefixedCommand(source, "ldiediet add @s ldiediet:test_dynamic_food{Flavor:'red'} 2");
                near(h, cap.getMonotony(red), 14, "NBT add command did not select red");
                commands.performPrefixedCommand(source, "ldiediet set @s ldiediet:test_dynamic_food 7");
                near(h, cap.getMonotony(red), 7, "Legacy set left an existing variant unchanged");
                near(h, cap.getMonotony(blue), 7, "Legacy set left blue unchanged");
                h.succeed();
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }
}
