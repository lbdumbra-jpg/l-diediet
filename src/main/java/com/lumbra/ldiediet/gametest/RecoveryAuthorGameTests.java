package com.lumbra.ldiediet.gametest;

import com.google.gson.*;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.math.FormulaManager;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("minecraft") @PrefixGameTestTemplate(false)
public final class RecoveryAuthorGameTests {
    private static final String CONFIG = "{\"minecraft:bread\":{\"cci\":4},\"minecraft:apple\":{\"cci\":1},\"minecraft:golden_apple\":{\"cci\":4}}";

     
    private static void withFormulas(GameTestHelper h, String overrides, Runnable action) throws Exception {
        JsonObject original;
        var resource = h.getLevel().getServer().getResourceManager().getResource(new ResourceLocation("ldiediet", "formulas.json")).orElseThrow();
        try (var reader = resource.openAsReader()) { original = JsonParser.parseReader(reader).getAsJsonObject(); }
        var next = original.deepCopy();
        JsonParser.parseString(overrides).getAsJsonObject().entrySet().forEach(e -> next.add(e.getKey(), e.getValue()));
        var apply = FormulaManager.class.getDeclaredMethod("applyConfiguration", JsonObject.class); apply.setAccessible(true);
        try { apply.invoke(null, next); action.run(); } finally { apply.invoke(null, original); }
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_recovery",timeoutTicks=100)
    public static void fullHungerPolicyAndPreservedCounterAffectRealConsumption(GameTestHelper h) throws Exception {
        ConsumptionLogicGameTests.withConfig(h, CONFIG, () -> {
            try { withFormulas(h, "{\"recovery_requires_hunger\":false,\"recovery_reset_on_eat\":false}", () -> {
                var p = LDieDietGameTests.makeNetworkedTestPlayer(h);
                try {
                    var cap = CapabilityHandler.get(p); var bread = FoodKey.of(Items.BREAD);
                    cap.setMonotony(bread, 6); cap.setRecoveryMeals(bread, 20); p.getFoodData().setFoodLevel(20);
                    new ItemStack(Items.GOLDEN_APPLE).finishUsingItem(p.level(), p);
                    h.assertTrue(cap.getMonotony(bread) < 6 && cap.getRecoveryMeals(bread) == 21, "Full-hunger policy did not restore food");
                    p.getFoodData().setFoodLevel(0); new ItemStack(Items.BREAD).finishUsingItem(p.level(), p);
                    h.assertTrue(cap.getRecoveryMeals(bread) == 21, "Eating target ignored disabled reset policy");
                    h.assertTrue(cap.getEatenCounts().get(bread) == 1, "Policy changed lifetime counting");
                    new ItemStack(Items.POTION).finishUsingItem(p.level(), p);
                    h.assertTrue(cap.getRecoveryMeals(bread) == 21, "Zero-quality drink counted under full-hunger policy");
                    var restored = new FoodToleranceCapability(); restored.deserializeNBT(cap.serializeNBT());
                    h.assertTrue(restored.getRecoveryMeals(bread) == 21, "Preserved counter was not saved");
                    h.succeed();
                } finally { LDieDietGameTests.removePlayer(p); }
            }); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_recovery",timeoutTicks=100)
    public static void minimumActualHungerGainFiltersPartiallyCappedPortions(GameTestHelper h) throws Exception {
        ConsumptionLogicGameTests.withConfig(h, CONFIG, () -> {
            try { withFormulas(h, "{\"recovery_min_hunger_restored\":3}", () -> {
                var p = LDieDietGameTests.makeNetworkedTestPlayer(h);
                try {
                    var cap = CapabilityHandler.get(p); var bread = FoodKey.of(Items.BREAD); cap.setMonotony(bread, 6);
                    p.getFoodData().setFoodLevel(19); new ItemStack(Items.APPLE).finishUsingItem(p.level(), p);
                    h.assertTrue(cap.getMonotony(bread) == 6 && cap.getRecoveryMeals(bread) == 0, "One restored point met minimum three");
                    p.getFoodData().setFoodLevel(0); new ItemStack(Items.APPLE).finishUsingItem(p.level(), p);
                    h.assertTrue(cap.getMonotony(bread) < 6 && cap.getRecoveryMeals(bread) == 1, "Qualifying actual portion did not recover");
                    h.succeed();
                } finally { LDieDietGameTests.removePlayer(p); }
            }); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_recovery",timeoutTicks=100)
    public static void synchronizedHelpFollowsSettingsAndCustomFormula(GameTestHelper h) throws Exception {
        withFormulas(h, "{\"recovery_delay\":30,\"recovery_acceleration\":0.125,\"recovery_requires_hunger\":false,\"recovery_reset_on_eat\":false}", () -> {
            var p = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var snapshot = JournalSnapshot.capture(p, CapabilityHandler.get(p));
                String help = snapshot.getList("foodHelp", net.minecraft.nbt.Tag.TAG_STRING).toString();
                h.assertTrue(help.contains("recovery_full") && help.contains("recovery_keep"), "Help ignored recovery policies");
                h.assertTrue(help.contains("recovery_acceleration") && help.contains("30") && help.contains("12.5"), "Help hardcodes acceleration settings");
                var lines = JournalFood.recoveryHelp();
                h.assertTrue(Component.Serializer.toJson(lines.get(lines.size() - 1)).contains("recovery_wait"), "Waiting help is not the last line");
            } finally { LDieDietGameTests.removePlayer(p); }
        });
        withFormulas(h, "{\"recovery_acceleration\":0}", () -> {
            String help = JournalFood.recoveryHelp().stream().map(Component.Serializer::toJson).reduce("", String::concat);
            h.assertTrue(help.contains("recovery_acceleration_off"), "Disabled acceleration still promised");
        });
        withFormulas(h, "{\"recovery_formula\":\"max(0, monotony_b - 1)\"}", () -> {
            String help = JournalFood.recoveryHelp().stream().map(Component.Serializer::toJson).reduce("", String::concat);
            h.assertTrue(help.contains("recovery_custom") && !help.contains("recovery_acceleration"), "Custom formula gets misleading default acceleration help");
            h.succeed();
        });
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_recovery",timeoutTicks=100)
    public static void validateCommandReportsFormulaErrorsWithoutEditingPlayer(GameTestHelper h) throws Exception {
        withFormulas(h, "{\"monotony_cap\":4,\"recovery_formula\":\"monotony_b - typo_recovery\"}", () -> {
            var p = LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var cap = CapabilityHandler.get(p); cap.setMonotony(Items.BREAD, 3); cap.recordEaten(Items.BREAD, 1);
                var saved = cap.serializeNBT();
                var issues = AuthorDiagnostics.validate(p.getServer());
                h.assertTrue(issues.stream().anyMatch(s -> s.contains("typo_recovery")), "Author validator omitted formula variables");
                h.assertTrue(issues.stream().anyMatch(s -> s.contains("exceed cap 4.0")), "Author validator omitted impossible stages");
                int result = p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2), "ldiediet validate");
                h.assertTrue(result == 0, "Validate command reported success despite formula issues");
                h.assertTrue(saved.equals(cap.serializeNBT()), "Read-only validation changed player state"); h.succeed();
            } finally { LDieDietGameTests.removePlayer(p); }
        });
    }
}
