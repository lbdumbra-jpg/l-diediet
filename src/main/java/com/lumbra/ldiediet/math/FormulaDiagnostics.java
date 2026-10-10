package com.lumbra.ldiediet.math;

import java.util.*;

 
public final class FormulaDiagnostics {
    private static final Set<String> RECOVERY_VARIABLES = Set.of("monotony_b", "cci", "cci_a", "cci_b",
            "offset_cat", "d_cci", "base_scale", "d_monotony", "base_recovery", "recovery_meals",
            "recovery_delay", "recovery_acceleration");
    private static final Map<String, Set<String>> VARIABLES = Map.of(
            "cci_formula", Set.of("average_ingredients_cci", "ingredients_count", "stages_weight"),
            "monotony_increase", Set.of("base_increase", "hunger", "saturation", "k_norm", "cci", "M", "monotony"),
            "nutrient_add", Set.of("base_n", "stage_multiplier", "M", "monotony"),
            "catalyst_formula", RECOVERY_VARIABLES,
            "debuff_factor", union(RECOVERY_VARIABLES, "catalyst_a"),
            "recovery_formula", union(RECOVERY_VARIABLES, "catalyst_a", "debuff_factor"));
    private record CacheKey(long revision, List<Integer> thresholds, double minimum) {}
    private static CacheKey cacheKey;
    private static List<String> cached = List.of();
    private FormulaDiagnostics() {}

    private static Set<String> union(Set<String> base, String... extra) {
        var result = new HashSet<>(base); result.addAll(List.of(extra)); return Set.copyOf(result);
    }

    public static List<String> validate(int[] thresholds, double minimumIncrease) {
        var key = new CacheKey(FormulaManager.revision(), Arrays.stream(thresholds).boxed().toList(), minimumIncrease);
        if (key.equals(cacheKey)) return cached;
        var issues = new ArrayList<>(FormulaManager.configurationIssues());
        if (FormulaManager.requiresHungerForRecovery() && FormulaManager.getRecoveryMinHungerRestored() > 20)
            issues.add("formulas.json.recovery_min_hunger_restored: exceeds the vanilla hunger capacity (20); no vanilla portion can qualify");
        var invalid = new HashSet<String>();
        FormulaManager.sources().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var unknown = new TreeSet<>(ExpressionParser.variables(entry.getValue()));
            unknown.removeAll(VARIABLES.get(entry.getKey()));
            if (!unknown.isEmpty()) {
                issues.add("formulas.json." + entry.getKey() + ": unknown variables " + unknown);
                invalid.add(entry.getKey());
            }
        });
        var aboveCap = Arrays.stream(thresholds).filter(t -> t > FormulaManager.getMonotonyCap()).boxed().toList();
        if (!aboveCap.isEmpty()) issues.add("formulas.json.monotony_cap: stage thresholds " + aboveCap
                + " exceed cap " + FormulaManager.getMonotonyCap() + "; these stages cannot be reached by eating");
        if (!invalid.contains("cci_formula")) probeCci(issues);
        if (!invalid.contains("nutrient_add")) probeNutrients(issues);
        if (!invalid.contains("monotony_increase")) probeAccumulation(issues, thresholds, minimumIncrease);
        if (Collections.disjoint(invalid, Set.of("catalyst_formula", "debuff_factor", "recovery_formula")))
            probeRecovery(issues, thresholds);
        cacheKey = key; cached = List.copyOf(issues); return cached;
    }

    private static double[] cciSamples() {
        double min = FormulaManager.getCCIMin(), max = FormulaManager.getCCIMax();
        double[] result = new double[7];
        for (int i = 0; i < result.length; i++) result[i] = min + (max - min) * (i / 6.0);
        return result;
    }

    private static void probeCci(List<String> issues) {
        try {
            for (double c : cciSamples()) for (double n : new double[]{1, 2, 10, 64})
                for (double weight : new double[]{0, 1, 4})
                    FormulaManager.strictEvaluate("cci_formula", Map.of("average_ingredients_cci", c,
                            "ingredients_count", n, "stages_weight", weight));
        } catch (RuntimeException e) { issues.add("formulas.json: " + e.getMessage() + " in sampled recipe inputs"); }
    }

    private static void probeNutrients(List<String> issues) {
        try {
            for (double base : new double[]{0, .1, 10, 1000}) for (double stage : new double[]{0, .5, 1})
                for (double m : new double[]{0, 6, FormulaManager.getMonotonyCap()})
                    FormulaManager.strictEvaluate("nutrient_add", Map.of("base_n", base, "stage_multiplier", stage,
                            "M", m, "monotony", m));
        } catch (RuntimeException e) { issues.add("formulas.json: " + e.getMessage() + " in sampled nutrition inputs"); }
    }

    private static double gain(double cci, double hunger, double saturation, double current) {
        return FormulaManager.strictEvaluate("monotony_increase", Map.of("base_increase", FormulaManager.getBaseIncrease(),
                "hunger", hunger, "saturation", saturation, "k_norm", FormulaManager.getKNorm(),
                "cci", cci, "M", current, "monotony", current));
    }

    private static void probeAccumulation(List<String> issues, int[] thresholds, double minimum) {
        String stalled = null;
        for (double c : cciSamples()) for (double q : new double[]{.01, 1, 2.4, 10, 100, 1000000}) {
            try {
                 
                for (double m : new double[]{0, 5, 6, 10, 15, 20, FormulaManager.getMonotonyCap()}) {
                    gain(c, q, 0, m); gain(c, q / 2, q / 2, m);
                }
                int[] counts = MealForecast.remaining(0, thresholds, FormulaManager.getMonotonyCap(), m -> {
                    double g = Math.max(0, gain(c, q, 0, m)); return g > 0 ? Math.max(minimum, g) : 0;
                });
                if (stalled == null) for (int i = 0; i < counts.length; i++) {
                    if (thresholds[i] <= FormulaManager.getMonotonyCap() && counts[i] < 0) {
                        stalled = "formulas.json.monotony_increase: stage " + (i + 1) + " (threshold=" + thresholds[i]
                                + ") " + (counts[i] == MealForecast.UNREACHABLE ? "stops progressing" : "is not reached within " + MealForecast.LIMIT + " meals")
                                + " for sampled Q=" + q + ", CCI=" + c + ", base_monotony=10";
                        break;
                    }
                }
            } catch (RuntimeException e) {
                issues.add("formulas.json: " + e.getMessage() + "; sampled Q=" + q + ", CCI=" + c); return;
            }
        }
        if (stalled != null) issues.add(stalled);
    }

    private static void probeRecovery(List<String> issues, int[] thresholds) {
        float cap = FormulaManager.getMonotonyCap();
        var starts = new LinkedHashSet<Float>(List.of(Math.min(cap, 6f), cap));
        for (int threshold : thresholds) if (threshold > 0 && threshold <= cap) starts.add((float) threshold);
        for (double target : cciSamples()) for (double source : cciSamples()) for (float start : starts) {
            float m = start;
            try {
                 
                for (int meal : new int[]{1, FormulaManager.getRecoveryDelay(),
                        (int) Math.min(Integer.MAX_VALUE, (long) FormulaManager.getRecoveryDelay() + 1)})
                    FormulaManager.recoveryRaw(m, source, target, Math.max(1, meal));
                for (int n = 1; n <= MealForecast.LIMIT && m > 0; n++) {
                    double next = FormulaManager.recoveryRaw(m, source, target, n);
                    if (next > m) {
                        issues.add("formulas.json.recovery_formula: tries to increase monotony; runtime clamps it"
                                + "; target CCI=" + target + ", other CCI=" + source + ", M=" + m + ", meal=" + n); return;
                    }
                    m = (float) Math.max(0, next);
                }
                if (m > 0) {
                    issues.add("formulas.json.recovery_formula: does not reach zero within " + MealForecast.LIMIT
                            + " qualifying other meals; target CCI=" + target + ", other CCI=" + source
                            + ", start M=" + start + ", remaining=" + m
                            + ". This is a bounded numerical check, not proof of infinite recovery."); return;
                }
            } catch (RuntimeException e) {
                issues.add("formulas.json: " + e.getMessage() + "; target CCI=" + target + ", other CCI=" + source + ", M=" + m); return;
            }
        }
    }
}
