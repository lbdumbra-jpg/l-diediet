package com.lumbra.ldiediet.gametest;

import com.mojang.authlib.GameProfile;
import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.ToleranceStage;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.capability.FoodToleranceCapability;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.data.DietConfigManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.UUID;

 



@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class LDieDietGameTests {
    private static final String TEMPLATE = "bastion/blocks/air";
    private static final String BATCH = "ldiediet_in_game";

    private LDieDietGameTests() {
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void optionalDietSuppression(GameTestHelper helper) throws Exception {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("diet")) {
            pass(helper, "optionalDietSuppression (Diet absent)");
            return;
        }
        ServerPlayer player = makeNetworkedTestPlayer(helper);
        try {
            Class<?> capability = Class.forName("com.illusivesoulworks.diet.common.capability.DietCapability");
            var optional = (net.minecraftforge.common.util.LazyOptional<?>) capability.getMethod("get", net.minecraft.world.entity.player.Player.class).invoke(null, player);
            Object tracker = optional.resolve().orElseThrow();
            Class<?> type = tracker.getClass();
            helper.assertFalse((Boolean) type.getMethod("isActive").invoke(tracker), "Diet tracker is active");
            type.getMethod("setActive", boolean.class).invoke(tracker, true);
            helper.assertFalse((Boolean) type.getMethod("isActive").invoke(tracker), "Diet was reactivated");
            Object before = type.getMethod("getValues").invoke(tracker);
            type.getMethod("consume", ItemStack.class, int.class, float.class).invoke(tracker, new ItemStack(Items.APPLE), 4, .3f);
            type.getMethod("tick").invoke(tracker);
            helper.assertTrue(before.equals(type.getMethod("getValues").invoke(tracker)), "Diet modified nutrient values");
            Class<?> regeneration = Class.forName("com.illusivesoulworks.diet.common.util.DietRegeneration");
            var regen = regeneration.getMethod("hasRegen", net.minecraft.world.entity.player.Player.class, boolean.class);
            helper.assertTrue((Boolean) regen.invoke(null, player, true), "Diet blocked vanilla regeneration");
            helper.assertFalse((Boolean) regen.invoke(null, player, false), "Diet enabled non-vanilla regeneration");
            pass(helper, "optionalDietSuppression");
        } finally { removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void importedDietAssignments(GameTestHelper helper) {
        var data = com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE;
        if (data.groups().isEmpty()) { pass(helper, "importedDietAssignments (no legacy resources)"); return; }
        int checked = 0;
        for (var entry : data.groups().entrySet()) {
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    new net.minecraft.resources.ResourceLocation("diet", entry.getKey()));
            for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues()) {
                ItemStack stack = new ItemStack(item);
                if (!stack.is(tag)) continue;
                var nutrients = DietConfigManager.getNutrients(stack);
                helper.assertTrue(nutrients != null, "Imported food cannot be resolved");
                checked++;
            }
        }
        helper.assertTrue(checked > 0, "Diet tags contain no available food");
         
        if (data.groups().containsKey("compat_fixture")) {
            ItemStack stack = new ItemStack(Items.BREAD);
            helper.assertTrue(DietConfigManager.getNutrients(stack).containsKey(data.nutrientId("compat_fixture")),
                    "Fixture food assignment was not imported");
            helper.assertTrue(Double.valueOf(10).equals(DietConfigManager.getFoodValueOverride(stack)),
                    "Diet foodOverrides was not imported");
            helper.assertTrue(com.lumbra.ldiediet.data.NutritionScaleManager.getInstance().get(data.nutrientId("compat_fixture")) != null,
                    "Custom Diet scale was not imported");
            ServerPlayer player = makeNetworkedTestPlayer(helper);
            try {
                IFoodTolerance cap = requireCapability(helper, player);
                cap.setDietSuite("compat_suite");
                cap.setNutrientValue(data.nutrientId("compat_fixture"), 60);
                double before = player.getMaxHealth();
                com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(player, cap);
                assertNear(helper, player.getMaxHealth(), (float) before + 3, .001f, "Imported suite effect not applied");
                com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(player, cap);
                assertNear(helper, player.getMaxHealth(), (float) before + 3, .001f, "Imported suite effect duplicated");
                cap.setNutrientValue(data.nutrientId("compat_fixture"), 40);
                com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(player, cap);
                assertNear(helper, player.getMaxHealth(), (float) before, .001f, "Expired suite condition kept reward");
            } finally { removePlayer(player); }
        }
        pass(helper, "importedDietAssignments");
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void clientSnapshotsDoNotChangeServerConfig(GameTestHelper helper) {
        var stack = new ItemStack(Items.APPLE);
        double cciBefore = DietConfigManager.getCCI(stack);
        var nutrientsBefore = java.util.Map.copyOf(DietConfigManager.getNutrients(stack));
        var originalClient = DietConfigManager.serializeToNBT();
        try {
            var root = new com.google.gson.JsonObject();
            var overrides = new com.google.gson.JsonObject();
            var food = new com.google.gson.JsonObject();
            food.addProperty("cci", 3.5);
            var nutrients = new com.google.gson.JsonObject(); nutrients.addProperty("test:native_override", .125);
            food.add("nutrients", nutrients);
            overrides.add("minecraft:apple", food); root.add("overrides", overrides);
            var nbt = new net.minecraft.nbt.CompoundTag();
            nbt.put("config", com.lumbra.ldiediet.util.JsonNbt.encode(root));
            DietConfigManager.deserializeFromNBT(nbt);
            helper.assertTrue(DietConfigManager.getClientCCI(stack) == 3.5, "Client snapshot was not installed");
            helper.assertTrue(DietConfigManager.getCCI(stack) == cciBefore, "Client snapshot overwrote server CCI");
            helper.assertTrue(DietConfigManager.getNutrients(stack).equals(nutrientsBefore), "Client snapshot overwrote server nutrients");
        } finally { DietConfigManager.deserializeFromNBT(originalClient); }
        pass(helper, "clientSnapshotsDoNotChangeServerConfig");
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void capabilityCommandsAndReset(GameTestHelper helper) {
        ServerPlayer player = makeNetworkedTestPlayer(helper);
        try {
            IFoodTolerance cap = requireCapability(helper, player);
            int setResult = helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    "ldiediet set_monotony @s minecraft:apple 12");
            helper.assertTrue(setResult > 0, "set_monotony command returned failure");
            assertNear(helper, cap.getMonotony(Items.APPLE), 12.0f, 0.001f,
                    "set_monotony did not update the capability");

            cap.setNutrientValue("test:nutrient", 9.0f);
            cap.addHistory(Items.APPLE);
            cap.addPermanentReward("ldiediet:test_reward");

            int resetResult = helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    "ldiediet reset @s");
            helper.assertTrue(resetResult > 0, "reset command returned failure");
            assertNear(helper, cap.getMonotony(Items.APPLE), 0.0f, 0.001f,
                    "reset did not clear monotony");
            assertNear(helper, cap.getNutrientValue("test:nutrient"), 0.0f, 0.001f,
                    "reset did not clear nutrients");
            helper.assertTrue(cap.getEatingHistory().isEmpty(), "reset did not clear eating history");
            helper.assertFalse(cap.hasPermanentReward("ldiediet:test_reward"),
                    "reset did not clear permanent rewards");
            pass(helper, "capabilityCommandsAndReset");
        } finally {
            removePlayer(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void freshAppleConsumptionFlow(GameTestHelper helper) {
        ServerPlayer player = makeNetworkedTestPlayer(helper);
        IFoodTolerance cap = requireCapability(helper, player);
        cap.clearMonotony();
        cap.clearNutrients();
        cap.clearHistory();
        var expectedNutrients = java.util.Map.copyOf(DietConfigManager.getNutrients(new ItemStack(Items.APPLE)));
        prepareToEat(player, new ItemStack(Items.APPLE));
        driveItemUse(helper, player);

        helper.assertTrue(player.getUseItemRemainingTicks() == 32,
                "fresh apple should keep the vanilla 32-tick eating duration");

        helper.runAfterDelay(40, () -> {
            try {
                LDieDiet.LOGGER.info("[LDieDiet GameTest] fresh state: using={}, remaining={}, hunger={}, saturation={}, monotony={}, history={}",
                        player.isUsingItem(), player.getUseItemRemainingTicks(), player.getFoodData().getFoodLevel(),
                        player.getFoodData().getSaturationLevel(), cap.getMonotony(Items.APPLE),
                        cap.getEatingHistory().size());
                helper.assertTrue(player.getFoodData().getFoodLevel() == 4,
                        "fresh apple should restore 4 hunger points, got " + player.getFoodData().getFoodLevel());
                assertNear(helper, player.getFoodData().getSaturationLevel(), 2.4f, 0.05f,
                        "fresh apple saturation is incorrect");
                assertNear(helper, cap.getMonotony(Items.APPLE), (float)(.36 * (.85 + .30 * 6.4 / 16.4)), 0.002f,
                        "apple monotony formula produced an unexpected result");
                helper.assertTrue(cap.getEatingHistory().size() == 1,
                        "finished eating did not append one history record");
                helper.assertTrue(cap.getLastEatenItem() == Items.APPLE,
                        "last-eaten tracking did not record the apple");
                expectedNutrients.forEach((id, share) -> assertNear(helper, cap.getNutrientValue(id),
                        (float) (6.4 * share), .05f, "Configured nutrient contribution is incorrect: " + id));
                pass(helper, "freshAppleConsumptionFlow");
            } finally {
                removePlayer(player);
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 180)
    public static void maximumMonotonyAppliesGameplayPenalties(GameTestHelper helper) {
        ServerPlayer player = makeNetworkedTestPlayer(helper);
        IFoodTolerance cap = requireCapability(helper, player);
        cap.clearNutrients();
        float stageFourThreshold = ToleranceStage.getBaseThresholds()[3];
        cap.setMonotony(Items.APPLE, stageFourThreshold);
        helper.assertTrue(ToleranceStage.getStage(player, Items.APPLE) == ToleranceStage.STAGE_4,
                "Configured fourth threshold should select stage 4");
        prepareToEat(player, new ItemStack(Items.APPLE));
        driveItemUse(helper, player);

        helper.assertTrue(player.getUseItemRemainingTicks() == 96,
                "stage 4 should triple the 32-tick eating duration");

        helper.runAfterDelay(105, () -> {
            try {
                LDieDiet.LOGGER.info("[LDieDiet GameTest] stage4 state: using={}, remaining={}, hunger={}, saturation={}, monotony={}",
                        player.isUsingItem(), player.getUseItemRemainingTicks(), player.getFoodData().getFoodLevel(),
                        player.getFoodData().getSaturationLevel(), cap.getMonotony(Items.APPLE));
                helper.assertTrue(player.getFoodData().getFoodLevel() == 1,
                        "stage 4 apple should restore only the configured minimum hunger, got "
                                + player.getFoodData().getFoodLevel());
                assertNear(helper, player.getFoodData().getSaturationLevel(), 0.6f, 0.001f,
                        "stage 4 should scale original saturation by 0.25");
                helper.assertTrue(cap.getMonotony(Items.APPLE) > stageFourThreshold,
                        "finished stage 4 eating did not increase monotony");
                helper.assertTrue(cap.getAllNutrients().isEmpty(), "stage 4 should block all nutrient contributions");
                pass(helper, "maximumMonotonyAppliesGameplayPenalties");
            } finally {
                removePlayer(player);
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void complexFoodActivelyRecoversOtherFood(GameTestHelper helper) {
        ServerPlayer player = makeNetworkedTestPlayer(helper);
        IFoodTolerance cap = requireCapability(helper, player);
        boolean automatic = com.lumbra.ldiediet.LDieDietServerConfig.AUTOMATIC_CCI.get();
        double breadCci;
        try {
            FixtureConfig.set(com.lumbra.ldiediet.LDieDietServerConfig.AUTOMATIC_CCI,true);
            breadCci = DietConfigManager.getOrCalculateCCI(new ItemStack(Items.BREAD), helper.getLevel().getServer());
        } finally { FixtureConfig.set(com.lumbra.ldiediet.LDieDietServerConfig.AUTOMATIC_CCI,automatic); }
        helper.assertTrue(breadCci > 1.0, "bread CCI must exceed the active-recovery offset");
        cap.setMonotony(Items.CARROT, 10.0f);
        prepareToEat(player, new ItemStack(Items.BREAD));
        driveItemUse(helper, player);

        helper.runAfterDelay(40, () -> {
            try {
                float recovered = cap.getMonotony(Items.CARROT);
                LDieDiet.LOGGER.info("[LDieDiet GameTest] recovery state: cci={}, using={}, remaining={}, carrot={}, bread={}, hunger={}",
                        breadCci, player.isUsingItem(), player.getUseItemRemainingTicks(), recovered,
                        cap.getMonotony(Items.BREAD), player.getFoodData().getFoodLevel());
                helper.assertTrue(recovered >= 0.0f && recovered < 10.0f,
                        "eating complex bread did not reduce carrot monotony");
                helper.assertTrue(cap.getMonotony(Items.BREAD) > 0.0f,
                        "eaten bread did not gain monotony");
                pass(helper, "complexFoodActivelyRecoversOtherFood");
            } finally {
                removePlayer(player);
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void capabilityNbtRoundTrip(GameTestHelper helper) {
        ServerPlayer player = makeNetworkedTestPlayer(helper);
        try {
            IFoodTolerance original = requireCapability(helper, player);
            original.setMonotony(Items.APPLE, 7.25f);
            original.setNutrientValue("test:nutrient", 14.5f);
            original.setDietSuite("test_suite");
            original.markImportedDietState();
            original.addHistory(Items.APPLE);
            original.addPermanentReward("ldiediet:round_trip");

            FoodToleranceCapability restored = new FoodToleranceCapability();
            restored.deserializeNBT(original.serializeNBT());

            assertNear(helper, restored.getMonotony(Items.APPLE), 7.25f, 0.001f,
                    "monotony did not survive NBT serialization");
            assertNear(helper, restored.getNutrientValue("test:nutrient"), 14.5f, 0.001f,
                    "nutrient state did not survive NBT serialization");
            helper.assertTrue(restored.getEatingHistory().size() == 1,
                    "history did not survive NBT serialization");
            helper.assertTrue(restored.hasPermanentReward("ldiediet:round_trip"),
                    "permanent reward did not survive NBT serialization");
            helper.assertTrue(restored.getDietSuite().equals("test_suite") && restored.hasImportedDietState(),
                    "Diet suite/migration marker did not survive NBT serialization");
            pass(helper, "capabilityNbtRoundTrip");
        } finally {
            removePlayer(player);
        }
    }

    private static IFoodTolerance requireCapability(GameTestHelper helper, ServerPlayer player) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        helper.assertTrue(cap != null, "L'DieDiet capability was not attached to the player");
        return cap;
    }

    static ServerPlayer makeNetworkedTestPlayer(GameTestHelper helper) {
        String name = "ldd-test-" + UUID.randomUUID().toString().substring(0, 8);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    private static void prepareToEat(ServerPlayer player, ItemStack stack) {
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0.0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.startUsingItem(InteractionHand.MAIN_HAND);
    }

     




    private static void driveItemUse(GameTestHelper helper, ServerPlayer player) {
        helper.onEachTick(() -> {
            if (player.isAlive() && player.isUsingItem()) {
                player.doTick();
            }
        });
    }

    private static void assertNear(GameTestHelper helper, float actual, float expected,
                                   float tolerance, String message) {
        helper.assertTrue(Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void pass(GameTestHelper helper, String name) {
        LDieDiet.LOGGER.info("[LDieDiet GameTest] PASS {}", name);
        helper.succeed();
    }

    static void removePlayer(ServerPlayer player) {
        player.stopUsingItem();
        player.getServer().getPlayerList().remove(player);
        player.discard();
    }
}
