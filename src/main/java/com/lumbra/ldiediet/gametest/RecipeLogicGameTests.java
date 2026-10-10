package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.data.DietConfigManager;
import com.lumbra.ldiediet.LDieDietServerConfig;
import com.lumbra.ldiediet.data.FoodKey;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.loading.FMLPaths;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class RecipeLogicGameTests {
    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_recipes", timeoutTicks = 100)
    public static void savedAutomaticCciSurvivesRecipesReloadAndManualEditing(GameTestHelper helper) throws Exception {
        withConfiguration(helper, "{\"minecraft:bread\":{\"cci\":2.5,\"cci_source\":\"auto\",\"nbt_filters\":[{\"variant_id\":\"test:bread\",\"match\":\"{Flavor:'blue'}\",\"cci\":3.3}]},\"minecraft:apple\":{\"cci\":1}}", () -> {
            var server = helper.getLevel().getServer();
            FixtureConfig.set(LDieDietServerConfig.AUTOMATIC_CCI,true);
            replaceFoodRecipe(helper, Items.BREAD, 3);
            DietConfigManager.reloadRecipes(server);
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(new ItemStack(Items.BREAD), server) == 2.5,
                    "Recipe reload overwrote saved automatic CCI");
            DietConfigManager.flushPendingSave();
            DietConfigManager.loadConfig(server);
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(new ItemStack(Items.BREAD), server) == 2.5,
                    "Configuration load ignored saved automatic CCI");
            var blue = new ItemStack(Items.BREAD); blue.getOrCreateTag().putString("Flavor", "blue");
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(DietConfigManager.getFoodKey(blue, false), server) == 3.3,
                    "Persisted base CCI replaced an explicit variant");
            var json = readConfig(); json.getAsJsonObject("overrides").getAsJsonObject("minecraft:bread").addProperty("cci", 2.75);
            writeConfig(json); DietConfigManager.loadConfig(server); DietConfigManager.reloadRecipes(server);
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(new ItemStack(Items.BREAD), server) == 2.75,
                    "Automatic calculation overwrote a hand-edited saved value");
            helper.succeed();
        });
    }

    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_recipes", timeoutTicks = 100)
    public static void automaticCciIsOptInAndFillsOnlyMissingValues(GameTestHelper helper) throws Exception {
        withConfiguration(helper, "{\"minecraft:bread\":{\"cci\":2.5,\"cci_source\":\"auto\"},\"minecraft:apple\":{\"cci\":1},\"minecraft:carrot\":{}}", () -> {
            var server = helper.getLevel().getServer();
            replaceFoodRecipe(helper, Items.CARROT, 3); DietConfigManager.reloadRecipes(server);
            helper.assertTrue(!LDieDietServerConfig.AUTOMATIC_CCI.get(), "Automatic CCI must be off in this fixture");
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(new ItemStack(Items.CARROT), server) == 1,
                    "Disabled automatic CCI computed a recipe");
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(FoodKey.of(Items.CARROT), server) == 1,
                    "Stored food recovery bypassed disabled automatic CCI");
            DietConfigManager.updateConfig(server); DietConfigManager.flushPendingSave();
            helper.assertTrue(!readConfig().getAsJsonObject("overrides").getAsJsonObject("minecraft:carrot").has("cci"),
                    "Disabled mode saved a fallback as a calculated value");
            FixtureConfig.set(LDieDietServerConfig.AUTOMATIC_CCI,true);
            double calculated = DietConfigManager.getOrCalculateCCI(new ItemStack(Items.CARROT), server);
            helper.assertTrue(calculated > 1, "Enabled mode did not calculate missing CCI");
            DietConfigManager.flushPendingSave();
            helper.assertTrue(readConfig().getAsJsonObject("overrides").getAsJsonObject("minecraft:carrot").get("cci").getAsDouble() == calculated,
                    "Generated CCI was not saved");
            replaceFoodRecipe(helper, Items.CARROT, 1); DietConfigManager.reloadRecipes(server); DietConfigManager.loadConfig(server);
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(new ItemStack(Items.CARROT), server) == calculated,
                    "Enabled mode recomputed a saved value");
            helper.assertTrue(DietConfigManager.getCCI(Items.BREAD) == 2.5, "Missing-food generation overwrote an existing value");
            FixtureConfig.set(LDieDietServerConfig.AUTOMATIC_CCI,false);
            DietConfigManager.reloadRecipes(server); DietConfigManager.loadConfig(server);
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(new ItemStack(Items.CARROT), server) == calculated,
                    "Disabling automatic mode discarded generated CCI");
            try {
                DietConfigManager.deserializeFromNBT(DietConfigManager.serializeToNBT());
                helper.assertTrue(DietConfigManager.getClientCCI(new ItemStack(Items.CARROT)) == calculated,
                        "Client did not receive stored CCI");
            } finally { DietConfigManager.clearClientSnapshot(); }
            helper.succeed();
        });
    }

    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_recipes", timeoutTicks = 100)
    public static void explicitRecalculationWorksWhenAutomaticModeIsOff(GameTestHelper helper) throws Exception {
        withConfiguration(helper, "{\"minecraft:bread\":{\"cci\":1,\"cci_source\":\"auto\"},\"minecraft:apple\":{\"cci\":1},\"minecraft:carrot\":{\"cci\":2.6,\"cci_source\":\"manual\"},\"minecraft:potion\":{\"cci\":2.8,\"cci_source\":\"auto\",\"nbt_filters\":[{\"variant_id\":\"test:drink\",\"match\":\"{Flavor:'blue'}\",\"cci\":3.5}]}}", () -> {
            var server = helper.getLevel().getServer();
            replaceFoodRecipe(helper, Items.BREAD, 3); DietConfigManager.reloadRecipes(server);
            int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "ldiediet calc_all_cci");
            helper.assertTrue(result == 1, "Explicit CCI command failed with automatic mode off");
            helper.assertTrue(DietConfigManager.getCCI(Items.BREAD) > 1, "Explicit command retained obsolete automatic CCI");
            helper.assertTrue(DietConfigManager.getCCI(Items.CARROT) == 2.6, "Explicit command replaced a manual CCI lock");
            helper.assertTrue(DietConfigManager.getCCI(Items.POTION) == 1, "Explicit command skipped a non-edible drink");
            var blue = new ItemStack(Items.POTION); blue.getOrCreateTag().putString("Flavor", "blue");
            helper.assertTrue(DietConfigManager.getCCI(blue) == 3.5, "Bulk generation replaced an explicit drink variant");
            double bread = DietConfigManager.getCCI(Items.BREAD);
            DietConfigManager.loadConfig(server); DietConfigManager.reloadRecipes(server);
            helper.assertTrue(DietConfigManager.getOrCalculateCCI(new ItemStack(Items.BREAD), server) == bread,
                    "Command-generated CCI did not survive configuration load");
            helper.succeed();
        });
    }

    private static void replaceFoodRecipe(GameTestHelper helper, net.minecraft.world.item.Item result, int count) {
        var server = helper.getLevel().getServer();
        var recipes = new ArrayList<Recipe<?>>(server.getRecipeManager().getRecipes());
        recipes.removeIf(recipe -> recipe.getResultItem(server.registryAccess()).is(result));
        var slots = NonNullList.<Ingredient>create();
        for (int i = 0; i < count; i++) slots.add(Ingredient.of(Items.APPLE));
        recipes.add(new ShapelessRecipe(new ResourceLocation("test_rewards", "persistent_cci"), "",
                CraftingBookCategory.MISC, new ItemStack(result), slots));
        server.getRecipeManager().replaceRecipes(recipes);
    }

    private static JsonObject readConfig() {
        try { return JsonParser.parseString(Files.readString(FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json"), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private static void writeConfig(JsonObject json) {
        try { Files.writeString(FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json"), json.toString(), StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private static void withConfiguration(GameTestHelper helper, String json, Runnable action) throws Exception {
        var server = helper.getLevel().getServer();
        var original = new ArrayList<Recipe<?>>(server.getRecipeManager().getRecipes());
        boolean automatic = LDieDietServerConfig.AUTOMATIC_CCI.get();
        try {
            FixtureConfig.set(LDieDietServerConfig.AUTOMATIC_CCI,false);
            ConsumptionLogicGameTests.withConfig(helper, json, action);
        } finally {
            server.getRecipeManager().replaceRecipes(original);
            FixtureConfig.set(LDieDietServerConfig.AUTOMATIC_CCI,automatic);
            DietConfigManager.reloadRecipes(server);
        }
    }
}
