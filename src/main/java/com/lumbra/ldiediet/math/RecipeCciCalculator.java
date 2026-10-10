package com.lumbra.ldiediet.math;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

 
public final class RecipeCciCalculator<K> {
    public enum CyclePolicy { PRODUCTION_LAYERS, PATH_LOCAL }
    public record Recipe<K>(List<List<K>> ingredients,double processingWeight) {
        public Recipe(List<List<K>> ingredients) { this(ingredients,0); }
        public Recipe { ingredients = ingredients.stream().map(List::copyOf).toList();
            if(!Double.isFinite(processingWeight)||processingWeight<0)throw new IllegalArgumentException("Invalid processing weight"); }
    }
    @FunctionalInterface
    public interface Formula {
        double evaluate(double averageIngredients, int ingredientCount, double stagesWeight);
    }
    private record Result(double cci, int levels) {}
    private record Query<K>(K item, Set<K> ancestors) {}

    private final Map<K, List<Recipe<K>>> recipes;
    private final Map<K, Double> manual;
    private final Formula formula;
    private final double min;
    private final double max;
    private final Set<K> contextSensitive;
    private final Set<K> rawFallbackItems;
    private final Map<K, Result> acyclicCache = new HashMap<>();
    private final Map<K, Set<K>> components;
    private final Map<Query<K>, Result> cyclicCache = new HashMap<>();
    private final Map<Recipe<K>, List<List<K>>> uniqueSlots = new java.util.IdentityHashMap<>();
    private final Map<Set<K>, Set<K>> unavailableCache = new HashMap<>();

    public RecipeCciCalculator(Map<K, List<Recipe<K>>> recipes, Map<K, Double> manual,
                               double min, double max, Formula formula) {
        this(recipes, manual, min, max, formula, CyclePolicy.PRODUCTION_LAYERS);
    }

    public RecipeCciCalculator(Map<K, List<Recipe<K>>> recipes, Map<K, Double> manual,
                               double min, double max, Formula formula, CyclePolicy cyclePolicy) {
        this.recipes = new HashMap<>();
        recipes.forEach((item, list) -> this.recipes.put(item, List.copyOf(list)));
        this.recipes.values().forEach(list -> list.forEach(recipe ->
                uniqueSlots.put(recipe, recipe.ingredients().stream().distinct().toList())));
        this.manual = Map.copyOf(manual);
        this.min = min;
        this.max = max;
        this.formula = formula;
        rawFallbackItems = findUnproductiveCycles();
        if (cyclePolicy == CyclePolicy.PRODUCTION_LAYERS) removeBackwardCycleEdges();
        contextSensitive = findCycleDependentItems();
        components = findComponents();
    }

     
    private void removeBackwardCycleEdges() {
        Map<K, Set<K>> originalComponents = findComponents();
        Map<K, Integer> rank = new HashMap<>();
        manual.keySet().forEach(item -> rank.put(item, 0));
        rawFallbackItems.forEach(item -> rank.put(item, 0));
        recipes.forEach((item, list) -> {
            if (list.isEmpty()) rank.put(item, 0);
            for (Recipe<K> recipe : list) for (List<K> slot : recipe.ingredients()) {
                for (K child : slot) if (!recipes.containsKey(child)) rank.put(child, 0);
            }
        });
        boolean changed;
        do {
            changed = false;
            for (var entry : recipes.entrySet()) {
                if (manual.containsKey(entry.getKey()) || rawFallbackItems.contains(entry.getKey())) continue;
                int best = rank.getOrDefault(entry.getKey(), Integer.MAX_VALUE);
                for (Recipe<K> recipe : entry.getValue()) {
                    if (recipe.ingredients().isEmpty()) continue;
                    int longest = 0;
                    for (List<K> slot : uniqueSlots.get(recipe)) {
                        int shortest = slot.stream().mapToInt(child -> rank.getOrDefault(child, Integer.MAX_VALUE)).min().orElse(Integer.MAX_VALUE);
                        longest = Math.max(longest, shortest);
                    }
                    if (longest < Integer.MAX_VALUE) best = Math.min(best, longest + 1);
                }
                if (best < rank.getOrDefault(entry.getKey(), Integer.MAX_VALUE)) {
                    rank.put(entry.getKey(), best); changed = true;
                }
            }
        } while (changed);
        recipes.replaceAll((item, list) -> {
            Set<K> component = originalComponents.getOrDefault(item, Set.of());
            var retained = new java.util.ArrayList<Recipe<K>>();
            for (Recipe<K> recipe : list) {
                List<List<K>> slots = recipe.ingredients().stream().map(slot -> slot.stream().filter(child ->
                        !component.contains(child) || rank.getOrDefault(child, Integer.MAX_VALUE)
                                < rank.getOrDefault(item, Integer.MAX_VALUE)).toList()).toList();
                if (slots.stream().anyMatch(List::isEmpty)) continue;
                var filtered = new Recipe<>(slots,recipe.processingWeight());
                uniqueSlots.put(filtered, slots.stream().distinct().toList()); retained.add(filtered);
            }
            return List.copyOf(retained);
        });
    }

    public double calculate(K item) {
        Result result = resolve(item, new HashSet<>(), cyclicCache);
        return result == null ? min : result.cci();
    }

    private Result resolve(K item, Set<K> ancestors, Map<Query<K>, Result> pathCache) {
        if (manual.containsKey(item)) return new Result(clamp(manual.get(item)), 0);
        if (ancestors.contains(item)) return null;
         
        if (rawFallbackItems.contains(item)) return new Result(min, 0);
        if (acyclicCache.containsKey(item)) return acyclicCache.get(item);
         
         
        Set<K> forbidden = new HashSet<>(ancestors);
        forbidden.retainAll(components.getOrDefault(item, Set.of()));
        if (!forbidden.isEmpty()) {
            forbidden = unavailableItems(item, forbidden);
            if (forbidden.contains(item)) return null;
        }
        Query<K> originalQuery = new Query<>(item, Set.copyOf(forbidden));
        if (pathCache.containsKey(originalQuery)) return pathCache.get(originalQuery);
        if (forbidden.size() > 1) forbidden = relevantAncestors(item, forbidden);
        Query<K> query = new Query<>(item, Set.copyOf(forbidden));
        if (pathCache.containsKey(query)) {
            Result cached = pathCache.get(query); pathCache.put(originalQuery, cached); return cached;
        }
        ancestors.add(item);
        Result result;
        try {
            Set<K> unavailable = unavailableItems(item, ancestors);
            List<Recipe<K>> available = recipes.getOrDefault(item, List.of());
            if (available.isEmpty()) {
                result = new Result(min, 0);
            } else {
                double total = 0;
                int valid = 0;
                int longest = 0;
                for (Recipe<K> recipe : available) {
                    Result value = resolveRecipe(recipe, ancestors, unavailable, pathCache);
                    if (value == null) continue;
                    total += value.cci();
                    longest = Math.max(longest, value.levels());
                    valid++;
                }
                 
                result = valid == 0 ? null : new Result(clamp(total / valid), longest);
            }
        } finally { ancestors.remove(item); }
        if (contextSensitive.contains(item)) { pathCache.put(query, result); pathCache.put(originalQuery, result); }
        else acyclicCache.put(item, result);
        return result;
    }

    private Result resolveRecipe(Recipe<K> recipe, Set<K> ancestors, Set<K> unavailable, Map<Query<K>, Result> pathCache) {
        if (recipe.ingredients().isEmpty()) return null;
         
         
         
        for (List<K> alternatives : recipe.ingredients()) {
            if (alternatives.stream().noneMatch(item -> manual.containsKey(item)
                    || !ancestors.contains(item) && !unavailable.contains(item))) return null;
        }
        double total = 0;
        int longest = 0;
        Map<List<K>, Result> slots = new HashMap<>();
        for (List<K> alternatives : recipe.ingredients()) {
            if (slots.containsKey(alternatives)) {
                Result cached = slots.get(alternatives);
                total += cached.cci(); longest = Math.max(longest, cached.levels()); continue;
            }
            double sum = 0;
            int valid = 0;
            for (K alternative : alternatives) {
                if (unavailable.contains(alternative) && !manual.containsKey(alternative)) continue;
                Result value = resolve(alternative, ancestors, pathCache);
                if (value == null) continue;
                sum += value.cci();
                longest = Math.max(longest, value.levels());
                valid++;
            }
            if (valid == 0) return null;
            double average = sum / valid;
            slots.put(alternatives, new Result(average, longest));
            total += average;
        }
        return new Result(clamp(formula.evaluate(total / recipe.ingredients().size(),
                recipe.ingredients().size(), longest * 0.1 + recipe.processingWeight())), longest + 1);
    }

    private double clamp(double value) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

     
    private Set<K> unavailableItems(K root, Set<K> ancestors) {
        Set<K> component = components.getOrDefault(root, Set.of());
        if (component.size() < 2) return Set.of();
        Set<K> blocked = new HashSet<>(ancestors); blocked.retainAll(component);
        if (blocked.isEmpty()) return Set.of();
        Set<K> key = Set.copyOf(blocked);
        if (unavailableCache.containsKey(key)) return unavailableCache.get(key);
        Set<K> productive = new HashSet<>();
        for (K item : component) {
            if (!blocked.contains(item) && (manual.containsKey(item) || rawFallbackItems.contains(item)
                    || recipes.getOrDefault(item, List.of()).isEmpty())) productive.add(item);
        }
        boolean changed;
        do {
            changed = false;
            for (K item : component) {
                if (blocked.contains(item) || productive.contains(item)) continue;
                for (Recipe<K> recipe : recipes.getOrDefault(item, List.of())) {
                    if (!recipe.ingredients().isEmpty() && uniqueSlots.get(recipe).stream().allMatch(slot ->
                            slot.stream().anyMatch(child -> !component.contains(child) || productive.contains(child)))) {
                        changed |= productive.add(item); break;
                    }
                }
            }
        } while (changed);
        Set<K> unavailable = new HashSet<>(component); unavailable.removeAll(productive);
        Set<K> result = Set.copyOf(unavailable); unavailableCache.put(key, result); return result;
    }

     
    private Set<K> relevantAncestors(K root, Set<K> forbidden) {
        Set<K> boundary = new HashSet<>(), visited = new HashSet<>();
        Set<K> component = components.get(root);
        ArrayDeque<K> pending = new ArrayDeque<>(); pending.add(root); visited.add(root);
        while (!pending.isEmpty()) {
            K item = pending.remove();
            if (manual.containsKey(item) || rawFallbackItems.contains(item)) continue;
            for (Recipe<K> recipe : recipes.getOrDefault(item, List.of())) {
                boolean blocked = false;
                for (List<K> alternatives : uniqueSlots.get(recipe)) {
                    if (alternatives.stream().allMatch(forbidden::contains)) {
                        boundary.addAll(alternatives); blocked = true; break;
                    }
                }
                if (blocked) continue;
                for (List<K> alternatives : uniqueSlots.get(recipe)) for (K child : alternatives) {
                    if (forbidden.contains(child)) boundary.add(child);
                    else if (component.contains(child) && visited.add(child)) pending.add(child);
                }
            }
        }
        return boundary;
    }

    private Map<K, Set<K>> findComponents() {
        Map<K, Set<K>> graph = new HashMap<>(), reverse = new HashMap<>();
        recipes.forEach((item, list) -> {
            Set<K> children = new HashSet<>();
            if (!manual.containsKey(item) && !rawFallbackItems.contains(item)) {
                for (Recipe<K> recipe : list) for (List<K> alternatives : recipe.ingredients()) children.addAll(alternatives);
            }
            graph.put(item, children);
            for (K child : children) reverse.computeIfAbsent(child, ignored -> new HashSet<>()).add(item);
        });
        Set<K> nodes = new HashSet<>(graph.keySet()); nodes.addAll(reverse.keySet());
        Set<K> visited = new HashSet<>();
        List<K> order = new java.util.ArrayList<>();
        record Visit<T>(T item, boolean finish) {}
        for (K root : nodes) {
            ArrayDeque<Visit<K>> stack = new ArrayDeque<>(); stack.push(new Visit<>(root, false));
            while (!stack.isEmpty()) {
                var visit = stack.pop();
                if (visit.finish()) { order.add(visit.item()); continue; }
                if (!visited.add(visit.item())) continue;
                stack.push(new Visit<>(visit.item(), true));
                for (K child : graph.getOrDefault(visit.item(), Set.of())) stack.push(new Visit<>(child, false));
            }
        }
        visited.clear();
        Map<K, Set<K>> result = new HashMap<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            K root = order.get(i);
            if (visited.contains(root)) continue;
            Set<K> members = new HashSet<>(); ArrayDeque<K> stack = new ArrayDeque<>(); stack.push(root);
            while (!stack.isEmpty()) {
                K item = stack.pop(); if (!visited.add(item)) continue;
                members.add(item); stack.addAll(reverse.getOrDefault(item, Set.of()));
            }
            Set<K> component = Set.copyOf(members);
            for (K item : members) result.put(item, component);
        }
        return result;
    }

    private Set<K> findUnproductiveCycles() {
        Set<K> productive = new HashSet<>(manual.keySet());
        recipes.forEach((item, list) -> {
            if (list.isEmpty()) productive.add(item);
            for (Recipe<K> recipe : list) for (List<K> alternatives : recipe.ingredients()) {
                for (K child : alternatives) if (!recipes.containsKey(child)) productive.add(child);
            }
        });
        boolean changed;
        do {
            changed = false;
            for (var entry : recipes.entrySet()) {
                if (productive.contains(entry.getKey())) continue;
                for (Recipe<K> recipe : entry.getValue()) {
                    if (!recipe.ingredients().isEmpty() && recipe.ingredients().stream()
                            .allMatch(alternatives -> alternatives.stream().anyMatch(productive::contains))) {
                        changed |= productive.add(entry.getKey());
                        break;
                    }
                }
            }
        } while (changed);
        Set<K> result = new HashSet<>(recipes.keySet());
        result.removeAll(productive);
         
        Map<K, Set<K>> children = new HashMap<>();
        Map<K, Integer> incoming = new HashMap<>();
        for (K item : result) incoming.put(item, 0);
        for (K item : result) {
            Set<K> dependencies = new HashSet<>();
            for (Recipe<K> recipe : recipes.get(item)) for (List<K> alternatives : recipe.ingredients()) {
                for (K child : alternatives) if (result.contains(child)) dependencies.add(child);
            }
            children.put(item, dependencies);
            for (K child : dependencies) incoming.computeIfPresent(child, (ignored, count) -> count + 1);
        }
        ArrayDeque<K> sources = new ArrayDeque<>();
        incoming.forEach((item, count) -> { if (count == 0) sources.add(item); });
        while (!sources.isEmpty()) {
            K source = sources.remove();
            result.remove(source);
            for (K child : children.getOrDefault(source, Set.of())) {
                int remaining = incoming.computeIfPresent(child, (ignored, count) -> count - 1);
                if (remaining == 0) sources.add(child);
            }
        }
        return result;
    }

     
    private Set<K> findCycleDependentItems() {
        Map<K, Set<K>> parents = new HashMap<>();
        Map<K, Integer> childCounts = new HashMap<>();
        recipes.forEach((item, list) -> {
            Set<K> children = new HashSet<>();
            if (!manual.containsKey(item) && !rawFallbackItems.contains(item)) {
                for (Recipe<K> recipe : list) for (List<K> alternatives : recipe.ingredients()) children.addAll(alternatives);
            }
            childCounts.put(item, children.size());
            for (K child : children) parents.computeIfAbsent(child, ignored -> new HashSet<>()).add(item);
        });
        for (K child : parents.keySet()) childCounts.putIfAbsent(child, 0);
        ArrayDeque<K> sinks = new ArrayDeque<>();
        childCounts.forEach((item, count) -> { if (count == 0) sinks.add(item); });
        while (!sinks.isEmpty()) {
            K sink = sinks.remove();
            for (K parent : parents.getOrDefault(sink, Set.of())) {
                int remaining = childCounts.computeIfPresent(parent, (ignored, count) -> count - 1);
                if (remaining == 0) sinks.add(parent);
            }
        }
        Set<K> result = new HashSet<>();
        childCounts.forEach((item, count) -> { if (count > 0) result.add(item); });
        return result;
    }
}
