package com.lumbra.ldiediet.math;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MathAndPenaltyTest {
    @org.junit.jupiter.api.AfterEach
    void restoreDefaults() { FormulaManager.applyConfiguration(new com.google.gson.JsonObject()); }

    @Test
    public void testBasicMath() {
        ExpressionParser.Expression expr = ExpressionParser.parse("2 + 2");
        assertEquals(4.0, expr.eval(new HashMap<>()), 0.001);
    }

    @Test
    public void testVariables() {
        ExpressionParser.Expression expr = ExpressionParser.parse("base_m * (1.0 / (CCI * V))");
        Map<String, Double> vars = new HashMap<>();
        vars.put("base_m", 10.0);
        vars.put("CCI", 2.0);
        vars.put("V", 1.0);
        
         
        assertEquals(5.0, expr.eval(vars), 0.001);
    }

    @Test
    public void testFunctions() {
        ExpressionParser.Expression expr1 = ExpressionParser.parse("max(0, M - decay_rate)");
        Map<String, Double> vars = new HashMap<>();
        
        vars.put("M", 5.0);
        vars.put("decay_rate", 1.0);
        assertEquals(4.0, expr1.eval(vars), 0.001);
        
        vars.put("M", 0.5);
        assertEquals(0.0, expr1.eval(vars), 0.001);
        
        ExpressionParser.Expression expr2 = ExpressionParser.parse("ln(M)");
        vars.put("M", Math.E);
        assertEquals(1.0, expr2.eval(vars), 0.001);
    }

    @Test
    public void testPrecedencePowerAndScientificNotation() {
        assertEquals(14.0, ExpressionParser.parse("2 + 3 * 4").eval(Map.of()), 0.001);
        assertEquals(512.0, ExpressionParser.parse("2^3^2").eval(Map.of()), 0.001);
        assertEquals(1001.5, ExpressionParser.parse("1e3 + 1.5").eval(Map.of()), 0.001);
    }

    @Test
    public void testAdditionalFunctionsAndConstants() {
        assertEquals(5.0, ExpressionParser.parse("abs(-3) + min(4, 2)").eval(Map.of()), 0.001);
        assertEquals(8.0, ExpressionParser.parse("pow(2, 3)").eval(Map.of()), 0.001);
        assertEquals(Math.PI, ExpressionParser.parse("pi").eval(Map.of()), 0.001);
    }

    @Test
    public void testRejectsMalformedExpressions() {
        assertThrows(IllegalArgumentException.class, () -> ExpressionParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ExpressionParser.parse("(2 + 3"));
        assertThrows(IllegalArgumentException.class, () -> ExpressionParser.parse("unknown(2)"));
        assertThrows(IllegalArgumentException.class, () -> ExpressionParser.parse("missing + 1").eval(Map.of()));
    }

    @Test
    public void testFormulaConfigurationAndFallback() {
        com.google.gson.JsonObject config = new com.google.gson.JsonObject();
        config.addProperty("cci_min", 5.0);
        config.addProperty("cci_max", 2.0);
        config.addProperty("monotony_increase", "broken(");
        FormulaManager.applyConfiguration(config);
        assertEquals(2.0, FormulaManager.getCCIMin(), 0.001);
        assertEquals(5.0, FormulaManager.getCCIMax(), 0.001);
        double result = FormulaManager.evaluate("monotony_increase", Map.of(
                "base_increase", 1.0, "hunger", 5.0, "saturation", 0.0,
                "k_norm", 10.0, "cci", 2.0));
        assertEquals(5 * .95 / (125.0 / 9 - 3), result, 0.001);
    }

    @Test
    void rejectsIncorrectFunctionArity() {
        for (String source : java.util.List.of("sqrt(1, 2)", "abs(1, 2)", "ln(1, 2)",
                "log(1, 2)", "min(1)", "max(1)", "pow(2)", "sqrt()", "pow(1, 2, 3)")) {
            assertThrows(IllegalArgumentException.class, () -> ExpressionParser.parse(source), source);
        }
    }

    @Test
    void nonFiniteIncreaseUsesDefaultInsteadOfZero() {
        var config = new com.google.gson.JsonObject();
        config.addProperty("monotony_increase", "1 / 0");
        FormulaManager.applyConfiguration(config);
        assertEquals(5 * .9625 / (125.0 / 9 - 3), FormulaManager.evaluate("monotony_increase", Map.of(
                "base_increase", 1.0, "hunger", 4.0, "saturation", 2.0,
                "k_norm", 10.0, "cci", 2.0)), .00001);
    }

    @Test
    void failedRecoveryPreservesMonotonyAndSignalsFailureInIntermediateSteps() {
        var config = new com.google.gson.JsonObject();
        config.addProperty("recovery_formula", "sqrt(-1)");
        config.addProperty("debuff_factor", "undefined_variable");
        FormulaManager.applyConfiguration(config);
        assertEquals(17, FormulaManager.evaluate("recovery_formula", Map.of("monotony_b", 17.0)), .00001);
        org.junit.jupiter.api.Assertions.assertTrue(FormulaManager.evaluateChecked("debuff_factor", Map.of()).isEmpty());
    }
}
