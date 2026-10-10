package com.lumbra.ldiediet.math;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.OptionalDouble;

 
public final class FormulaManager extends SimplePreparableReloadListener<JsonObject> {
    private static final ResourceLocation RESOURCE = new ResourceLocation(LDieDiet.MOD_ID, "formulas.json");
    private static final FormulaManager INSTANCE = new FormulaManager();
    private static final Map<String, String> DEFAULT_FORMULAS = new LinkedHashMap<>();
    private static final Map<String, ExpressionParser.Expression> FORMULAS = new HashMap<>();
    private static final Map<String, ExpressionParser.Expression> DIAGNOSTIC_EXPRESSIONS = new HashMap<>();
    private static final Map<String, ExpressionParser.Expression> DEFAULT_EXPRESSIONS = new HashMap<>();
    private static final Set<String> REPORTED_FAILURES = new HashSet<>();
    private static final Map<String, String> SOURCES = new LinkedHashMap<>();
    private static final java.util.List<String> CONFIGURATION_ISSUES = new java.util.ArrayList<>();
    private String readIssue;

    private static String nutrientSource="base_n * stage_multiplier";
    private static ExpressionParser.Expression clientNutrient=ExpressionParser.parse(nutrientSource);
    public static String nutrientSource() { return nutrientSource; }
    public static void clientNutrientSource(String source) { try {clientNutrient=ExpressionParser.parse(source);}catch(RuntimeException e){clientNutrient=ExpressionParser.parse("base_n * stage_multiplier");} }
    public static double clientNutrientGain(Map<String,Double> vars) {
        try {double value=clientNutrient.eval(vars);if(Double.isFinite(value))return value;}catch(RuntimeException ignored){}
        return vars.getOrDefault("base_n",0.0)*vars.getOrDefault("stage_multiplier",1.0);
    }
    private static long revision;
    public static long revision(){return revision;}
    private static String recoveryMode;
    private static double cciMin;
    private static double cciMax;
    private static double offsetCat;
    private static double dCci;
    private static double baseScale;
    private static double dMonotony;
    private static double baseRecovery;
    private static double kNorm;
    private static double baseIncrease;
    private static double monotonyCap;
    private static int recoveryDelay;
    private static double recoveryAcceleration;
    private static boolean recoveryRequiresHunger;
    private static boolean recoveryResetOnEat;
    private static int recoveryMinHungerRestored;

    static {
        DEFAULT_FORMULAS.put("cci_formula", "average_ingredients_cci + 0.4 * sqrt(max(0, ingredients_count - 1)) + stages_weight");
        DEFAULT_FORMULAS.put("monotony_increase", "base_increase * 5 * (0.85 + 0.30 * (hunger + saturation) / (hunger + saturation + 10)) / (125 / 9 - 3 * (min(4, max(1, cci)) - 1))");
        DEFAULT_FORMULAS.put("catalyst_formula", "1 + 0.25 * (min(4, max(1, cci)) - min(4, max(1, cci_b)))");
        DEFAULT_FORMULAS.put("debuff_factor", "(0.125 + 0.0056 * monotony_b^2) / ((1 - (min(4, max(1, cci)) - 1) / 3) / 0.94 + ((min(4, max(1, cci)) - 1) / 3) / 2.4)");
        DEFAULT_FORMULAS.put("recovery_formula", "max(0, monotony_b - base_recovery * catalyst_a * debuff_factor * (1 + recovery_acceleration * max(0, recovery_meals - recovery_delay)))");
        DEFAULT_FORMULAS.put("nutrient_add", "base_n * stage_multiplier");
        DEFAULT_FORMULAS.forEach((key, source) -> DEFAULT_EXPRESSIONS.put(key, ExpressionParser.parse(source)));
        applyConfiguration(defaultConfiguration());
    }

    private FormulaManager() {}

    public static FormulaManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected JsonObject prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        readIssue = null;
        try {
            Resource resource = resourceManager.getResource(RESOURCE).orElse(null);
            if (resource == null) {
                LDieDiet.LOGGER.debug("No formula datapack installed; using built-in defaults");
                return defaultConfiguration();
            }
            try (BufferedReader reader = resource.openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) throw new IllegalArgumentException("Root must be a JSON object");
                return parsed.getAsJsonObject();
            }
        } catch (Exception e) {
            readIssue = "formulas.json: cannot read configuration; built-in defaults used: " + e.getMessage();
            LDieDiet.LOGGER.error("Failed to read {}; using safe defaults", RESOURCE, e);
            return defaultConfiguration();
        }
    }

    @Override
    protected void apply(JsonObject json, ResourceManager resourceManager, ProfilerFiller profiler) {
        applyConfiguration(json);
        if (readIssue != null) CONFIGURATION_ISSUES.add(readIssue);
        LDieDiet.LOGGER.info("Loaded {} datapack formulas from {}", FORMULAS.size(), RESOURCE);
    }

    static void applyConfiguration(JsonObject json) {
        revision++;
        CONFIGURATION_ISSUES.clear();
        SOURCES.clear();
        validateSettings(json);
        JsonObject defaults = defaultConfiguration();
        recoveryMode = stringValue(json, "recovery_mode", defaults.get("recovery_mode").getAsString());
        if (!"active".equalsIgnoreCase(recoveryMode)) {
            LDieDiet.LOGGER.warn("Unsupported recovery_mode '{}'; falling back to active", recoveryMode);
            CONFIGURATION_ISSUES.add("formulas.json.recovery_mode: only active is supported; using active");
            recoveryMode = "active";
        }
        cciMin = positiveValue(json, "cci_min", 1.0);
        cciMax = positiveValue(json, "cci_max", 4.0);
        if (cciMax < cciMin) {
            double swap = cciMin;
            cciMin = cciMax;
            cciMax = swap;
        }
        offsetCat = finiteValue(json, "offset_cat", 1.0);
        dCci = finiteValue(json, "d_cci", 1.5);
        baseScale = positiveValue(json, "base_scale", 10.0);
        dMonotony = finiteValue(json, "d_monotony", 0.02);
        baseRecovery = finiteValue(json, "base_recovery", 1.0);
        kNorm = positiveValue(json, "k_norm", 10.0);
        baseIncrease = finiteValue(json, "base_increase", 1.0);
        monotonyCap = Math.min(Float.MAX_VALUE, positiveValue(json, "monotony_cap", 24.0));
        recoveryDelay = (int) Math.min(Integer.MAX_VALUE, Math.max(0, finiteValue(json, "recovery_delay", 20)));
        recoveryAcceleration = Math.max(0, finiteValue(json, "recovery_acceleration", 0.08));
        recoveryRequiresHunger = booleanValue(json, "recovery_requires_hunger", true);
        recoveryResetOnEat = booleanValue(json, "recovery_reset_on_eat", true);
        recoveryMinHungerRestored = (int) Math.min(Integer.MAX_VALUE,
                Math.max(1, finiteValue(json, "recovery_min_hunger_restored", 1)));

        FORMULAS.clear();
        DIAGNOSTIC_EXPRESSIONS.clear();
        REPORTED_FAILURES.clear();
        DEFAULT_FORMULAS.forEach((key, fallback) -> {
            String source = fallback;
            if (json.has(key)) {
                var value = json.get(key);
                if (value.isJsonPrimitive() && (value.getAsJsonPrimitive().isString() || value.getAsJsonPrimitive().isNumber()))
                    source = value.getAsString();
                else CONFIGURATION_ISSUES.add("formulas.json." + key + ": expected an expression string or number; built-in fallback used");
            }
            try {
                FORMULAS.put(key, ExpressionParser.parse(source));
                DIAGNOSTIC_EXPRESSIONS.put(key, ExpressionParser.parseForDiagnostics(source));
                SOURCES.put(key, source);
                if(key.equals("nutrient_add"))nutrientSource=source;
            } catch (RuntimeException e) {
                LDieDiet.LOGGER.error("Invalid formula '{}': {}; using default", key, source, e);
                CONFIGURATION_ISSUES.add("formulas.json." + key + ": " + e.getMessage() + "; built-in fallback used");
                FORMULAS.put(key, ExpressionParser.parse(fallback));
                DIAGNOSTIC_EXPRESSIONS.put(key, ExpressionParser.parseForDiagnostics(fallback));
                SOURCES.put(key, fallback);
                if(key.equals("nutrient_add"))nutrientSource=fallback;
            }
        });
    }

    private static JsonObject defaultConfiguration() {
        JsonObject json = new JsonObject();
        json.addProperty("recovery_mode", "active");
        json.addProperty("cci_min", 1.0);
        json.addProperty("cci_max", 4.0);
        json.addProperty("offset_cat", 1.0);
        json.addProperty("d_cci", 1.5);
        json.addProperty("base_scale", 10.0);
        json.addProperty("d_monotony", 0.02);
        json.addProperty("base_recovery", 1.0);
        json.addProperty("k_norm", 10.0);
        json.addProperty("base_increase", 1.0);
        json.addProperty("monotony_cap", 24.0);
        json.addProperty("recovery_delay", 20);
        json.addProperty("recovery_acceleration", 0.08);
        json.addProperty("recovery_requires_hunger", true);
        json.addProperty("recovery_reset_on_eat", true);
        json.addProperty("recovery_min_hunger_restored", 1);
        DEFAULT_FORMULAS.forEach(json::addProperty);
        return json;
    }

    private static boolean booleanValue(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        var value = json.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
        CONFIGURATION_ISSUES.add("formulas.json." + key + ": expected true/false; default used");
        return fallback;
    }

    private static void validateSettings(JsonObject json) {
        var allowed = defaultConfiguration().keySet();
        for (String key : new java.util.TreeSet<>(json.keySet()))
            if (!allowed.contains(key)) CONFIGURATION_ISSUES.add("formulas.json." + key + ": unknown setting; ignored");
        for (String key : Set.of("cci_min", "cci_max", "offset_cat", "d_cci", "base_scale",
                "d_monotony", "base_recovery", "k_norm", "base_increase", "monotony_cap",
                "recovery_delay", "recovery_acceleration", "recovery_min_hunger_restored")) {
            if (!json.has(key)) continue;
            try {
                var value = json.get(key);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("expected a finite number");
                double n = value.getAsDouble();
                if (!Double.isFinite(n)) throw new IllegalArgumentException("expected a finite number");
                if (Set.of("cci_min", "cci_max", "base_scale", "k_norm", "monotony_cap").contains(key) && n <= 0)
                    throw new IllegalArgumentException("must be positive; default used");
                if (key.equals("monotony_cap") && n > Float.MAX_VALUE)
                    throw new IllegalArgumentException("exceeds saved float range; clamped");
                if (Set.of("recovery_delay", "recovery_acceleration").contains(key) && n < 0)
                    throw new IllegalArgumentException("must be non-negative; clamped");
                if (key.equals("recovery_min_hunger_restored") && n < 1)
                    throw new IllegalArgumentException("must be at least 1; clamped");
                if (Set.of("recovery_delay", "recovery_min_hunger_restored").contains(key)
                        && (n != Math.rint(n) || n > Integer.MAX_VALUE))
                    throw new IllegalArgumentException("must be a whole number within the integer range; clamped/truncated");
            } catch (RuntimeException e) {
                CONFIGURATION_ISSUES.add("formulas.json." + key + ": " + e.getMessage());
            }
        }
        if (finiteValue(json, "cci_min", 1) > finiteValue(json, "cci_max", 4))
            CONFIGURATION_ISSUES.add("formulas.json: cci_min exceeds cci_max; bounds swapped");
    }

    static Map<String, String> sources() { return Map.copyOf(SOURCES); }
    static java.util.List<String> configurationIssues() { return java.util.List.copyOf(CONFIGURATION_ISSUES); }
    public static boolean usesDefaultRecoveryFormula() {
        return SOURCES.get("recovery_formula").replaceAll("\\s+", "")
                .equals(DEFAULT_FORMULAS.get("recovery_formula").replaceAll("\\s+", ""));
    }
    public static boolean requiresHungerForRecovery() { return recoveryRequiresHunger; }
    public static boolean resetsRecoveryOnEat() { return recoveryResetOnEat; }
    public static int getRecoveryMinHungerRestored() { return recoveryMinHungerRestored; }
    public static boolean qualifiesForRecovery(double quality, int hungerRestored) {
        return Double.isFinite(quality) && quality > 0
                && (!recoveryRequiresHunger || hungerRestored >= recoveryMinHungerRestored);
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double finiteValue(JsonObject json, String key, double fallback) {
        try {
            double value = json.has(key) ? json.get(key).getAsDouble() : fallback;
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double positiveValue(JsonObject json, String key, double fallback) {
        double value = finiteValue(json, key, fallback);
        return value > 0 ? value : fallback;
    }

    public static double evaluate(String key, Map<String, Double> variables) {
        OptionalDouble configured = evaluateChecked(key, variables);
        if (configured.isPresent()) return configured.getAsDouble();
         
        if ("recovery_formula".equals(key)) return variables.getOrDefault("monotony_b", 0.0);
        ExpressionParser.Expression fallback = DEFAULT_EXPRESSIONS.get(key);
        if (fallback != null) {
            try {
                double result = fallback.eval(variables);
                if (Double.isFinite(result)) return result;
            } catch (RuntimeException ignored) { }
        }
        return 0.0;
    }

     
    public static OptionalDouble evaluateChecked(String key, Map<String, Double> variables) {
        ExpressionParser.Expression expression = FORMULAS.get(key);
        if (expression == null) {
            if (REPORTED_FAILURES.add(key)) LDieDiet.LOGGER.error("Unknown formula key '{}'", key);
            return OptionalDouble.empty();
        }
        try {
            double result = expression.eval(variables);
            if (!Double.isFinite(result)) throw new ArithmeticException("non-finite result: " + result);
            return OptionalDouble.of(result);
        } catch (RuntimeException e) {
            if (REPORTED_FAILURES.add(key)) LDieDiet.LOGGER.error("Failed to evaluate formula '{}'", key, e);
            return OptionalDouble.empty();
        }
    }

    public static String getRecoveryMode() { return recoveryMode; }
    public static double getCCIMin() { return cciMin; }
    public static double getCCIMax() { return cciMax; }
    public static double getOffsetCat() { return offsetCat; }
    public static double getDCci() { return dCci; }
    public static double getBaseScale() { return baseScale; }
    public static double getDMonotony() { return dMonotony; }
    public static double getBaseRecovery() { return baseRecovery; }
    public static double getKNorm() { return kNorm; }
    public static double getBaseIncrease() { return baseIncrease; }
    public static float getMonotonyCap() { return (float) monotonyCap; }
    public static int getRecoveryDelay() { return recoveryDelay; }
    public static double getRecoveryAcceleration() { return recoveryAcceleration; }

     
    public static OptionalDouble recover(double current, double eatenCci, double targetCci, int nextMeal) {
        current = Math.min(monotonyCap, Math.max(0, current));
        var variables = recoveryVariables(current, eatenCci, targetCci, nextMeal);
        var catalyst = evaluateChecked("catalyst_formula", variables);
        if (catalyst.isEmpty()) return OptionalDouble.empty();
        if (catalyst.getAsDouble() <= 0) return OptionalDouble.of(current);
        variables.put("catalyst_a", catalyst.getAsDouble());
        variables.put("cci", targetCci);
        var debuff = evaluateChecked("debuff_factor", variables);
        if (debuff.isEmpty()) return OptionalDouble.empty();
        variables.put("debuff_factor", debuff.getAsDouble());
        var result = evaluateChecked("recovery_formula", variables);
        return result.isEmpty() ? result : OptionalDouble.of(Math.max(0, Math.min(current, result.getAsDouble())));
    }

    static Map<String, Double> recoveryVariables(double current, double eatenCci, double targetCci, int nextMeal) {
        var variables = new HashMap<String, Double>();
        variables.put("monotony_b", current);
        variables.put("cci", eatenCci);
        variables.put("cci_a", eatenCci);
        variables.put("cci_b", targetCci);
        variables.put("offset_cat", offsetCat);
        variables.put("d_cci", dCci);
        variables.put("base_scale", baseScale);
        variables.put("d_monotony", dMonotony);
        variables.put("base_recovery", baseRecovery);
        variables.put("recovery_meals", (double) Math.max(1, nextMeal));
        variables.put("recovery_delay", (double) recoveryDelay);
        variables.put("recovery_acceleration", recoveryAcceleration);
        return variables;
    }

     
    static double recoveryRaw(double current, double eatenCci, double targetCci, int nextMeal) {
        var vars = recoveryVariables(current, eatenCci, targetCci, nextMeal);
        double catalyst = strictEvaluate("catalyst_formula", vars);
        if (catalyst <= 0) return current;
        vars.put("catalyst_a", catalyst);
        vars.put("cci", targetCci);
        vars.put("debuff_factor", strictEvaluate("debuff_factor", vars));
        return strictEvaluate("recovery_formula", vars);
    }

    static double strictEvaluate(String key, Map<String, Double> vars) {
        double result;
        try { result = DIAGNOSTIC_EXPRESSIONS.get(key).eval(vars); }
        catch (RuntimeException e) { throw new IllegalArgumentException(key + ": " + e.getMessage(), e); }
        if (!Double.isFinite(result)) throw new IllegalArgumentException(key + ": non-finite result (division by zero/domain error)");
        return result;
    }
}
