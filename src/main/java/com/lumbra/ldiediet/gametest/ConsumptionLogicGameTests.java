package com.lumbra.ldiediet.gametest;

import com.google.gson.JsonParser;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.consumption.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.util.EatingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.Direction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ConsumptionLogicGameTests {
    private static final String TEMPLATE = "bastion/blocks/air";
    private static final String BATCH = "ldiediet_consumption";
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .001, message + ": expected " + expected + ", got " + actual);
    }
    private static void hungry(ServerPlayer player) { player.getFoodData().setFoodLevel(0); player.getFoodData().setSaturation(0); }
    private static ItemStack dynamic() {
        Item item = ForgeRegistries.ITEMS.getValue(ConsumptionFixtureItems.DYNAMIC_FOOD);
        if (item == null || item == Items.AIR) throw new IllegalStateException("Consumption fixture property is missing");
        return new ItemStack(item);
    }
     
    static void withConfig(GameTestHelper h, String overrides, Runnable action) throws Exception {
        var file = FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json");
        byte[] original = Files.readAllBytes(file);
        var root = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonParser.parseString(overrides).getAsJsonObject().entrySet().forEach(entry -> root.getAsJsonObject("overrides").add(entry.getKey(), entry.getValue()));
        try {
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
            DietConfigManager.loadConfig(h.getLevel().getServer()); action.run();
        } finally { Files.write(file, original); DietConfigManager.loadConfig(h.getLevel().getServer()); }
    }
    public static final class CancelStart {
        private final ServerPlayer player;
        CancelStart(ServerPlayer player) { this.player = player; }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void cancel(LivingEntityUseItemEvent.Start event) {
            if (event.getEntity() == player) event.setCanceled(true);
        }
    }
    public static final class FinishBuff {
        private final ServerPlayer player;
        private final boolean fail;
        FinishBuff(ServerPlayer player, boolean fail) { this.player = player; this.fail = fail; }
        @SubscribeEvent public void finish(LivingEntityUseItemEvent.Finish event) {
            if (event.getEntity() != player) return;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 3));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 80));
            if (fail) throw new IllegalStateException("fixture Finish failure");
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void repeatedStartAndCancellationLeaveNoPendingPortion(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h); var listener = new CancelStart(player);
        try {
            var cap = CapabilityHandler.get(player); cap.setMonotony(Items.APPLE, com.lumbra.ldiediet.ToleranceStage.getBaseThresholds()[3]);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            player.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertTrue(player.getUseItemRemainingTicks() == 96, "First start has wrong duration");
            player.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertTrue(player.getUseItemRemainingTicks() == 96, "Repeated start multiplied the timer again");
            player.stopUsingItem();
            h.assertTrue(cap.getUseSession() == null && EatingContext.current() == null, "Stop retained the portion context");
            MinecraftForge.EVENT_BUS.register(listener);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertFalse(player.isUsingItem(), "Cancelled start began item use");
            h.assertTrue(cap.getUseSession() == null && cap.getEatingHistory().isEmpty(), "Cancelled start retained state or counted a meal");
            h.succeed();
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void dynamicPropertiesAndStageStayFrozenForTheWholePortion(GameTestHelper h) throws Exception {
        withConfig(h, "{\"ldiediet:test_dynamic_food\":{\"cci\":1,\"nutrients\":{\"test:portion\":1}}}", () -> {
            var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var stack = dynamic(); stack.getOrCreateTag().putInt("FixtureNutrition", 8);
                stack.getOrCreateTag().putFloat("FixtureSaturation", .5f); stack.getOrCreateTag().putInt("FixtureAmplifier", 1);
                var cap = CapabilityHandler.get(player); cap.setMonotony(stack.getItem(), 9.9f); hungry(player);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack); player.startUsingItem(InteractionHand.MAIN_HAND);
                h.assertTrue(player.getUseItemRemainingTicks() == 48, "Stage-one duration is wrong");
                stack.getOrCreateTag().putInt("FixtureNutrition", 2); stack.getOrCreateTag().putFloat("FixtureSaturation", .1f);
                stack.getOrCreateTag().putInt("FixtureAmplifier", 3); cap.setMonotony(FoodKey.of(stack.getItem()), 20);
                stack.finishUsingItem(player.level(), player);
                near(h, player.getFoodData().getFoodLevel(), 6, "Hunger used finish-time properties or stage");
                near(h, player.getFoodData().getSaturationLevel(), 6, "Saturation used finish-time properties or stage");
                near(h, cap.getNutrientValue("test:portion"), 16, "Nutrients used finish-time properties or stage");
                h.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED) != null && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1,
                        "Buff used finish-time properties or was blocked by the new stage");
                h.assertTrue(cap.getHistoryTotalCount() == 1 && cap.getUseSession() == null && EatingContext.current() == null,
                        "The same portion was counted twice or leaked its context");
                h.succeed();
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void blockedFoodBuffKeepsExistingPotionAndAllowsHarmfulEffects(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var stack = dynamic(); stack.getOrCreateTag().putInt("FixtureAmplifier", 3); stack.getOrCreateTag().putBoolean("FixturePoison", true);
            var cap = CapabilityHandler.get(player); cap.setMonotony(stack.getItem(), 10); hungry(player);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
            stack.finishUsingItem(player.level(), player);
            var speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
            h.assertTrue(speed != null && speed.getAmplifier() == 1 && speed.getDuration() == 100, "Food changed/deleted the existing potion");
            h.assertTrue(player.hasEffect(MobEffects.POISON), "Harmful food effect was blocked");
            h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void suspiciousStewEffectsAreFilteredBeforeApplication(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var stack = new ItemStack(Items.SUSPICIOUS_STEW);
            SuspiciousStewItem.saveMobEffect(stack, MobEffects.REGENERATION, 160);
            SuspiciousStewItem.saveMobEffect(stack, MobEffects.POISON, 80);
            CapabilityHandler.get(player).setMonotony(Items.SUSPICIOUS_STEW, 10); hungry(player);
            stack.finishUsingItem(player.level(), player);
            h.assertFalse(player.hasEffect(MobEffects.REGENERATION), "Stew bypassed the beneficial-effect filter");
            h.assertTrue(player.hasEffect(MobEffects.POISON), "Stew harmful effect was blocked");
            h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void instantaneousPotionEffectsRespectTheVariantStage(GameTestHelper h) throws Exception {
        withConfig(h, "{\"minecraft:potion\":{\"cci\":1,\"is_exclusion\":false,\"nbt_filters\":[{\"variant_id\":\"test:heal\",\"match\":\"{Potion:'minecraft:healing'}\"},{\"variant_id\":\"test:harm\",\"match\":\"{Potion:'minecraft:harming'}\"}]}}", () -> {
            var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var heal = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
                var harm = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HARMING);
                var cap = CapabilityHandler.get(player); cap.setMonotony(DietConfigManager.getFoodKey(heal, false), 10);
                cap.setMonotony(DietConfigManager.getFoodKey(harm, false), 10);
                hungry(player);
                 
                for (int i = 0; i < 65; i++) player.tick();
                player.setHealth(8);
                heal.finishUsingItem(player.level(), player); near(h, player.getHealth(), 8, "Instant healing bypassed the filter");
                 
                boolean previousPvp = player.getServer().isPvpAllowed();
                try {
                    player.getServer().setPvpAllowed(true);
                    harm.finishUsingItem(player.level(), player); h.assertTrue(player.getHealth() < 8, "Instant harm was blocked");
                } finally { player.getServer().setPvpAllowed(previousPvp); }
                h.succeed();
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void exceptionRestoresNestedContextAndClearsThePendingPortion(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var stack = dynamic(); var cap = CapabilityHandler.get(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack); player.startUsingItem(InteractionHand.MAIN_HAND);
            var outer = ConsumptionSnapshot.capture(player, new ItemStack(Items.APPLE));
            try (var scope = EatingContext.open(player, outer)) {
                try { FoodConsumption.finish(player, stack, () -> { throw new IllegalStateException("fixture failure"); }); }
                catch (IllegalStateException expected) { h.assertTrue(expected.getMessage().equals("fixture failure"), "Unexpected failure"); }
                h.assertTrue(EatingContext.current().portion() == outer, "Nested exception did not restore the previous scope");
                h.assertTrue(cap.getUseSession() == null && cap.getHistoryTotalCount() == 0, "Failed portion retained state or counted a meal");
            }
            h.assertTrue(EatingContext.current() == null, "Exception leaked a scoped player reference"); h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void zeroNutritionDoesNotRestoreHungerMonotonyOrNutrients(GameTestHelper h) throws Exception {
        withConfig(h, "{\"ldiediet:test_dynamic_food\":{\"cci\":3,\"nutrients\":{\"test:portion\":1}}}", () -> {
            var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var stack = dynamic(); stack.getOrCreateTag().putInt("FixtureNutrition", 0); stack.getOrCreateTag().putFloat("FixtureSaturation", 1);
                Item item = stack.getItem();
                var cap = CapabilityHandler.get(player); cap.setMonotony(item, 20); cap.setMonotony(Items.CARROT, 10); hungry(player);
                stack.finishUsingItem(player.level(), player);
                near(h, player.getFoodData().getFoodLevel(), 0, "Zero food granted minimum hunger");
                near(h, cap.getMonotony(Items.CARROT), 10, "Zero food actively recovered another food");
                near(h, cap.getMonotony(item), 20, "Zero food grew monotony");
                near(h, cap.getNutrientValue("test:portion"), 0, "Zero food added nutrients"); h.succeed();
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void cakeSlicesUseTheSamePortionPipeline(GameTestHelper h) throws Exception {
        withConfig(h, "{\"minecraft:cake\":{\"cci\":1,\"nutrients\":{\"test:cake\":1}}}", () -> {
            var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
            BlockPos position = h.absolutePos(new BlockPos(0, 1, 0));
            try {
                var level = h.getLevel(); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(position, Blocks.CAKE.defaultBlockState(), 3); hungry(player);
                var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
                var state = level.getBlockState(position);
                state.use(level, player, InteractionHand.MAIN_HAND, hit);
                var cap = CapabilityHandler.get(player);
                near(h, player.getFoodData().getFoodLevel(), 2, "Cake hunger is incorrect");
                near(h, player.getFoodData().getSaturationLevel(), .4, "Cake saturation is incorrect");
                near(h, cap.getMonotony(Items.CAKE), Math.max(.36 * (.85 + .30 * 2.4 / 12.4), com.lumbra.ldiediet.LDieDietServerConfig.MIN_MONOTONY_PER_MEAL.get()), "Cake did not grow monotony from the slice value");
                near(h, cap.getNutrientValue("test:cake"), 2.4, "Cake did not add configured nutrients");
                h.assertTrue(cap.getHistoryTotalCount() == 1 && level.getBlockState(position).getValue(CakeBlock.BITES) == 1,
                        "Cake slice was not recorded exactly once");
                player.getFoodData().setFoodLevel(20);
                level.getBlockState(position).use(level, player, InteractionHand.MAIN_HAND, hit);
                h.assertTrue(cap.getHistoryTotalCount() == 1, "A refused cake slice was recorded"); h.succeed();
            } finally { LDieDietGameTests.removePlayer(player); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void actualFoodDataScalesTheOriginalPortionBeforeVanillaCaps(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var stack = dynamic(); stack.getOrCreateTag().putInt("FixtureNutrition", 8); stack.getOrCreateTag().putFloat("FixtureSaturation", .75f);
            CapabilityHandler.get(player).setMonotony(stack.getItem(), 10);
            player.getFoodData().setFoodLevel(16); player.getFoodData().setSaturation(0);
            stack.finishUsingItem(player.level(), player);
            near(h, player.getFoodData().getFoodLevel(), 20, "Penalty scaled the capped hunger gain");
            near(h, player.getFoodData().getSaturationLevel(), 6, "Penalty scaled saturation twice"); h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void forgeFinishBuffsUseTheStartingStageAndKeepExistingEffects(GameTestHelper h) {
        var blocked = LDieDietGameTests.makeNetworkedTestPlayer(h); var allowed = LDieDietGameTests.makeNetworkedTestPlayer(h);
        var blockedListener = new FinishBuff(blocked, false); var allowedListener = new FinishBuff(allowed, false);
        try {
            MinecraftForge.EVENT_BUS.register(blockedListener); MinecraftForge.EVENT_BUS.register(allowedListener);
            CapabilityHandler.get(blocked).setMonotony(Items.APPLE, 10);
            CapabilityHandler.get(allowed).setMonotony(Items.APPLE, 9.9f);
            for (var player : List.of(blocked, allowed)) {
                hungry(player); player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE)); player.startUsingItem(InteractionHand.MAIN_HAND);
                for (int i = 0; i < 70 && player.isUsingItem(); i++) player.doTick();
            }
            h.assertTrue(blocked.getEffect(MobEffects.MOVEMENT_SPEED) != null && blocked.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1,
                    "Forge Finish bypassed the food buff filter or removed the old potion");
            h.assertTrue(blocked.hasEffect(MobEffects.POISON), "Harmful Finish buff was blocked");
            h.assertTrue(allowed.getEffect(MobEffects.MOVEMENT_SPEED) != null && allowed.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 3,
                    "Forge Finish used the newly increased stage instead of the initial stage");
            h.assertTrue(EatingContext.current() == null, "Finish event retained the context"); h.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(blockedListener); MinecraftForge.EVENT_BUS.unregister(allowedListener);
            LDieDietGameTests.removePlayer(blocked); LDieDietGameTests.removePlayer(allowed);
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void forgeFinishExceptionDoesNotLeakThePortionContext(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h); var listener = new FinishBuff(player, true);
        try {
            MinecraftForge.EVENT_BUS.register(listener); hungry(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE)); player.startUsingItem(InteractionHand.MAIN_HAND);
            boolean caught = false;
            try { for (int i = 0; i < 40 && player.isUsingItem(); i++) player.doTick(); }
            catch (RuntimeException e) {
                for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                    if (cause instanceof IllegalStateException && "fixture Finish failure".equals(cause.getMessage())) caught = true;
                }
            }
            h.assertTrue(caught, "The failing Finish handler did not run");
            var cap = CapabilityHandler.get(player);
            h.assertTrue(EatingContext.current() == null && cap.getUseSession() == null, "Finish exception leaked state");
            h.assertTrue(cap.getHistoryTotalCount() == 1, "The already-consumed portion was lost or counted twice"); h.succeed();
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); LDieDietGameTests.removePlayer(player); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void configuredMinimumAppliesToPositiveFoodAndStaysFrozenDuringUse(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        double previous = com.lumbra.ldiediet.LDieDietServerConfig.MIN_HUNGER_RESTORED.get();
        try {
            FixtureConfig.set(com.lumbra.ldiediet.LDieDietServerConfig.MIN_HUNGER_RESTORED,5.0);
            var stack = dynamic(); stack.getOrCreateTag().putInt("FixtureNutrition", 2); stack.getOrCreateTag().putFloat("FixtureSaturation", .1f);
            hungry(player); player.setItemInHand(InteractionHand.MAIN_HAND, stack); player.startUsingItem(InteractionHand.MAIN_HAND);
            FixtureConfig.set(com.lumbra.ldiediet.LDieDietServerConfig.MIN_HUNGER_RESTORED,0.0);
            stack.finishUsingItem(player.level(), player);
            near(h, player.getFoodData().getFoodLevel(), 5, "The positive-food minimum was skipped or changed mid-portion");
            near(h, player.getFoodData().getSaturationLevel(), .4, "The hunger floor inflated saturation");
            h.succeed();
        } finally {
            FixtureConfig.set(com.lumbra.ldiediet.LDieDietServerConfig.MIN_HUNGER_RESTORED,previous);
            LDieDietGameTests.removePlayer(player);
        }
    }
}
