package com.lumbra.ldiediet.math;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FormulaDiagnosticsTest {
    private static final int[] STAGES = {5, 10, 15, 20};
    @BeforeEach void defaults() { FormulaManager.applyConfiguration(new JsonObject()); }
    @AfterEach void restore() { defaults(); }
    private List<String> validate() { return FormulaDiagnostics.validate(STAGES, .25); }

    @Test void defaultBalanceHasNoFormulaWarningsAndValidationIsReadOnly() {
        long revision = FormulaManager.revision();
        double before = FormulaManager.recover(6, 2, 3, 21).orElseThrow();
        assertEquals(List.of(), validate());
        assertEquals(List.of(), validate());
        assertEquals(revision, FormulaManager.revision());
        assertEquals(before, FormulaManager.recover(6, 2, 3, 21).orElseThrow());
    }

    @Test void unknownVariablesAreCheckedInTheirRealFormulaContext() {
        var json = new JsonObject();
        json.addProperty("monotony_increase", "unknown_gain + hunger");
        json.addProperty("catalyst_formula", "catalyst_a + 1");  
        json.addProperty("recovery_formula", "monotony_b - unknown_recovery");
        FormulaManager.applyConfiguration(json);
        var issues = String.join("\n", validate());
        assertTrue(issues.contains("unknown_gain"));
        assertTrue(issues.contains("catalyst_formula: unknown variables [catalyst_a]"));
        assertTrue(issues.contains("unknown_recovery"));
        assertFalse(ExpressionParser.variables("max(1, sqrt(pi)) + e + hunger").contains("sqrt"));
        assertEquals(java.util.Set.of("hunger"), ExpressionParser.variables("max(1, sqrt(pi)) + e + hunger"));
    }

    @Test void syntaxErrorsSurviveSafeFallbackAndDisappearAfterCorrection() {
        var json = new JsonObject(); json.addProperty("recovery_formula", "max(0,");
        FormulaManager.applyConfiguration(json);
        assertTrue(validate().stream().anyMatch(s -> s.contains("fallback used")));
        assertTrue(FormulaManager.recover(6, 2, 2, 1).isPresent());
        json.add("recovery_formula", new com.google.gson.JsonArray());
        FormulaManager.applyConfiguration(json);
        assertTrue(validate().stream().anyMatch(s -> s.contains("expected an expression string or number")));
        defaults(); assertTrue(validate().isEmpty());
    }

    @Test void divisionByZeroIsDetectedEvenIfMinHidesInfinity() {
        var json = new JsonObject(); json.addProperty("monotony_increase", "min(1, 1 / (cci - 2))");
        FormulaManager.applyConfiguration(json);
        assertTrue(validate().stream().anyMatch(s -> s.contains("division by zero") && s.contains("CCI=2.0")));
        assertThrows(ArithmeticException.class, () -> ExpressionParser.parseForDiagnostics("min(0, 1/0)").eval(Map.of()));
        assertThrows(ArithmeticException.class, () -> ExpressionParser.parseForDiagnostics("sqrt(-1)").eval(Map.of()));
    }

    @Test void CapAndStalledGrowthIdentifyUnreachableStages() {
        var json = new JsonObject(); json.addProperty("monotony_cap", 4);
        FormulaManager.applyConfiguration(json);
        assertTrue(validate().stream().anyMatch(s -> s.contains("exceed cap 4.0")));
        json = new JsonObject(); json.addProperty("monotony_increase", "0");
        FormulaManager.applyConfiguration(json);
        assertTrue(validate().stream().anyMatch(s -> s.contains("stage 1") && s.contains("stops progressing")));
    }

    @Test void incompleteAndIncreasingRecoveryAreReportedWithoutChangingFormula() {
        var json = new JsonObject(); json.addProperty("recovery_formula", "max(1, monotony_b - 1)");
        FormulaManager.applyConfiguration(json);
        assertTrue(validate().stream().anyMatch(s -> s.contains("does not reach zero within 4096") && s.contains("remaining=1.0")));
        assertEquals(1, FormulaManager.recover(1, 2, 2, 1).orElseThrow());
        json.addProperty("recovery_formula", "monotony_b + 1"); FormulaManager.applyConfiguration(json);
        assertTrue(validate().stream().anyMatch(s -> s.contains("tries to increase monotony")));
        assertEquals(6, FormulaManager.recover(6, 2, 2, 1).orElseThrow());
    }

    @Test void authorPoliciesAreIndependentAndInvalidSettingsAreVisible() {
        assertFalse(FormulaManager.qualifiesForRecovery(10, 0));
        assertTrue(FormulaManager.qualifiesForRecovery(10, 1));
        var json = new JsonObject(); json.addProperty("recovery_requires_hunger", false);
        json.addProperty("recovery_reset_on_eat", false); json.addProperty("recovery_min_hunger_restored", 3);
        FormulaManager.applyConfiguration(json);
        assertTrue(FormulaManager.qualifiesForRecovery(10, 0));
        assertFalse(FormulaManager.qualifiesForRecovery(0, 0));
        assertFalse(FormulaManager.resetsRecoveryOnEat());
        json.addProperty("recovery_requires_hunger", true); FormulaManager.applyConfiguration(json);
        assertFalse(FormulaManager.qualifiesForRecovery(10, 2));
        assertTrue(FormulaManager.qualifiesForRecovery(10, 3));
        json.addProperty("recovery_requires_hunger", "false");
        json.addProperty("recovery_delay", -1); json.addProperty("recovery_min_hunger_restored", 1.5);
        FormulaManager.applyConfiguration(json);
        var issues = String.join("\n", validate());
        assertTrue(issues.contains("expected true/false"));
        assertTrue(issues.contains("must be non-negative"));
        assertTrue(issues.contains("whole number"));
        assertTrue(FormulaManager.requiresHungerForRecovery());
    }

    @Test void impossibleHungerRequirementAndMisspelledSettingsAreReported() {
        var json = new JsonObject(); json.addProperty("recovery_min_hunger_restored", 21);
        json.addProperty("recovery_delai", 30); FormulaManager.applyConfiguration(json);
        assertTrue(validate().stream().anyMatch(s -> s.contains("exceeds the vanilla hunger capacity")));
        assertTrue(validate().stream().anyMatch(s -> s.contains("recovery_delai: unknown setting")));
        json.addProperty("recovery_requires_hunger", false); FormulaManager.applyConfiguration(json);
        assertFalse(validate().stream().anyMatch(s -> s.contains("exceeds the vanilla hunger capacity")));
    }
}
