package com.lumbra.ldiediet.math;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeCciCalculatorTest {
    @Test void machineProcessingWeightPropagatesThroughRecipeTree() {
        var graph=Map.of("meal",List.of(new RecipeCciCalculator.Recipe<>(List.of(List.of("raw")),.75)),
                "second",List.of(new RecipeCciCalculator.Recipe<>(List.of(List.of("meal")),.5)));
        var calculator=new RecipeCciCalculator<>(graph,Map.of("raw",1d),1,4,(average,count,weight)->average+weight);
        assertEquals(1.75,calculator.calculate("meal"),1e-12);
        assertEquals(2.35,calculator.calculate("second"),1e-12);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->new RecipeCciCalculator.Recipe<>(List.of(List.of("raw")),Double.NaN));
    }
    @Test
    void cycleBoundaryPruningMatchesIndependentPathEnumeration() {
        var random = new java.util.Random(827104);
        for (int sample = 0; sample < 100; sample++) {
            var graph = new HashMap<String, List<RecipeCciCalculator.Recipe<String>>>();
            for (int i = 0; i < 6; i++) {
                var recipes = new java.util.ArrayList<RecipeCciCalculator.Recipe<String>>();
                recipes.add(recipe("raw"));
                for (int j = 0; j < 2; j++) recipes.add(new RecipeCciCalculator.Recipe<>(List.of(
                        List.of("n" + random.nextInt(6), "n" + random.nextInt(6)),
                        List.of("n" + random.nextInt(6)))));
                graph.put("n" + i, recipes);
            }
            var calculator = calculator(graph);
            for (int i = 0; i < 6; i++) assertEquals(
                    enumerate("n" + i, graph, new java.util.HashSet<>())[0],
                    calculator.calculate("n" + i), .000001, "sample " + sample + ", root " + i);
        }
    }

    private static double[] enumerate(String item, Map<String, List<RecipeCciCalculator.Recipe<String>>> graph,
                                      java.util.Set<String> ancestors) {
        if (ancestors.contains(item)) return null;
        if (!graph.containsKey(item)) return new double[]{1, 0};
        ancestors.add(item);
        try {
            double sum = 0, levels = 0; int validRecipes = 0;
            for (var recipe : graph.get(item)) {
                double total = 0, longest = 0; boolean valid = true;
                for (var alternatives : recipe.ingredients()) {
                    double slot = 0; int count = 0;
                    for (String alternative : alternatives) {
                        var result = enumerate(alternative, graph, ancestors);
                        if (result != null) { slot += result[0]; longest = Math.max(longest, result[1]); count++; }
                    }
                    if (count == 0) { valid = false; break; }
                    total += slot / count;
                }
                if (valid) { sum += Math.min(100, total / recipe.ingredients().size() + longest * .1);
                    levels = Math.max(levels, longest + 1); validRecipes++; }
            }
            return validRecipes == 0 ? null : new double[]{sum / validRecipes, levels};
        } finally { ancestors.remove(item); }
    }

    @Test
    void upstreamDiamondPathsShareCycleCalculationsWithoutChangingTheAnswer() {
        var graph = new HashMap<String, List<RecipeCciCalculator.Recipe<String>>>();
        graph.put("a0", List.of(recipe("b0"), recipe("raw")));
        graph.put("b0", List.of(recipe("a0"), recipe("raw")));
        for (int i = 1; i <= 24; i++) {
            var ingredients = List.of(new RecipeCciCalculator.Recipe<>(List.of(List.of("a" + (i - 1), "b" + (i - 1)))));
            graph.put("a" + i, ingredients); graph.put("b" + i, ingredients);
        }
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
            var calculator = calculator(graph);
            assertEquals(33.45, calculator.calculate("a24"), .000001);
            assertEquals(33.45, calculator.calculate("b24"), .000001);
            assertEquals(1.05, calculator.calculate("b0"), .000001);
        });
    }
    private static RecipeCciCalculator.Recipe<String> recipe(String... ingredients) {
        return new RecipeCciCalculator.Recipe<>(java.util.Arrays.stream(ingredients).map(List::of).toList());
    }
    private static RecipeCciCalculator<String> calculator(Map<String, List<RecipeCciCalculator.Recipe<String>>> recipes) {
        return new RecipeCciCalculator<>(recipes, Map.of(), 1, 100, (average, count, stages) -> average + stages,
                RecipeCciCalculator.CyclePolicy.PATH_LOCAL);
    }

    @Test
    void productionLayersBreakDenseRecyclingCyclesWithoutDependingOnRootOrder() {
        var graph = new HashMap<String, List<RecipeCciCalculator.Recipe<String>>>();
        for (int i = 0; i < 170; i++) {
            var variants = new java.util.ArrayList<String>();
            for (int j = 0; j < 170; j++) variants.add("glass" + j);
            graph.put("glass" + i, List.of(recipe("raw"), new RecipeCciCalculator.Recipe<>(List.of(variants))));
        }
        graph.put("bottle", List.of(recipe("glass0", "glass0", "glass0")));
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
            var c = new RecipeCciCalculator<>(graph, Map.of(), 1, 100, (a, n, s) -> a + s);
            assertEquals(1.1, c.calculate("bottle"), .000001);
            for (int i = 169; i >= 0; i--) assertEquals(1, c.calculate("glass" + i), .000001);
        });
    }

    @Test
    void productionLayersRetainAllRecipesAndAlternativesOutsideCycles() {
        var graph = Map.of("one", List.of(recipe("raw")), "two", List.of(recipe("one")),
                "food", List.of(recipe("raw"), recipe("two"),
                        new RecipeCciCalculator.Recipe<>(List.of(List.of("one", "two")))));
        var c = new RecipeCciCalculator<>(graph, Map.of(), 1, 100, (a, n, s) -> a + s);
        assertEquals(calculator(graph).calculate("food"), c.calculate("food"), .000001);
    }

    @Test
    void countsIntermediateLevelsWithoutTraversalDepthOrEightLevelLimit() {
        var graph = new HashMap<String, List<RecipeCciCalculator.Recipe<String>>>();
        for (int i = 1; i <= 11; i++) graph.put("level" + i, List.of(recipe("level" + (i - 1))));
        var calculator = calculator(graph);
        assertEquals(1, calculator.calculate("level1"), .000001);
        assertEquals(1.1, calculator.calculate("level2"), .000001);
        assertEquals(6.5, calculator.calculate("level11"), .000001);
        assertEquals(1, calculator.calculate("level1"), .000001);
    }

    @Test
    void rootAndCacheOrderDoNotChangeCyclicGraphWithValidRecipes() {
        var graph = Map.of("a", List.of(recipe("b"), recipe("raw")),
                "b", List.of(recipe("a"), recipe("raw")));
        var first = calculator(graph);
        var second = calculator(graph);
        assertEquals(1.05, first.calculate("a"), .000001);
        assertEquals(1.05, first.calculate("b"), .000001);
        assertEquals(first.calculate("b"), second.calculate("b"), .000001);
        assertEquals(first.calculate("a"), second.calculate("a"), .000001);
    }

    @Test
    void ignoresCyclicRecipesInsteadOfDilutingAverageWithMinimum() {
        var graph = Map.of("result", List.of(recipe("result"), recipe("crafted")),
                "crafted", List.of(recipe("raw")));
        assertEquals(1.1, calculator(graph).calculate("result"), .000001);
        assertEquals(1, calculator(Map.of("loop", List.of(recipe("loop")))).calculate("loop"), .000001);
    }

    @Test
    void packingLoopsRemainRawIngredientsWithoutAddingArtificialStages() {
        var graph = Map.of("wheat", List.of(recipe("hay")),
                "hay", List.of(recipe("wheat")),
                "bread", List.of(recipe("wheat", "wheat", "wheat")));
        var calculator = new RecipeCciCalculator<>(graph, Map.of(), 1, 4,
                (average, count, stages) -> average + .4 * Math.sqrt(count - 1) + stages);
        assertEquals(1, calculator.calculate("wheat"), .000001);
        assertEquals(1 + .4 * Math.sqrt(2), calculator.calculate("bread"), .000001);
    }

    @Test
    void longestIngredientChainDeterminesBonusAndValidAlternativesAreAveraged() {
        var graph = Map.of("first", List.of(recipe("raw")),
                "second", List.of(recipe("first")),
                "final", List.of(recipe("raw", "second")),
                "alternatives", List.of(new RecipeCciCalculator.Recipe<>(List.of(List.of("alternatives", "second")))));
        var calculator = calculator(graph);
        assertEquals(1.25, calculator.calculate("final"), .000001);
        assertEquals(1.3, calculator.calculate("alternatives"), .000001);
    }

    @Test
    void manualLocksBreakCyclesAndMissingIngredientRejectsRecipe() {
        var graph = Map.of("manual", List.of(recipe("result")),
                "result", List.of(recipe("manual"), new RecipeCciCalculator.Recipe<String>(List.of(List.of()))));
        var calculator = new RecipeCciCalculator<>(graph, Map.of("manual", 2.5), 1, 4,
                (average, count, stages) -> average + stages);
        assertEquals(2.5, calculator.calculate("manual"), .000001);
        assertEquals(2.5, calculator.calculate("result"), .000001);
    }
}
