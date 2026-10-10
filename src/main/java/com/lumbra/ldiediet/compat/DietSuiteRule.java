package com.lumbra.ldiediet.compat;

import com.google.gson.*;
import java.util.*;

 
public record DietSuiteRule(List<Condition> conditions, List<Attribute> attributes, List<Status> statuses) {
    public static DietSuiteRule parse(JsonObject json) {
        List<Condition> conditions = new ArrayList<>();
        for (JsonElement value : array(json, "conditions")) {
            JsonObject c = value.getAsJsonObject();
            Set<String> groups = new LinkedHashSet<>();
            array(c, "groups").forEach(group -> groups.add(group.getAsString()));
            String match = c.has("match") ? c.get("match").getAsString().toLowerCase(Locale.ROOT) : "any";
            if (!Set.of("every", "any", "average", "all", "none").contains(match)) match = "any";
            conditions.add(new Condition(Set.copyOf(groups), match, number(c, "above", 0), number(c, "below", 1)));
        }
        if (conditions.isEmpty()) throw new IllegalArgumentException("Diet effect requires conditions");
        List<Attribute> attributes = new ArrayList<>();
        for (JsonElement value : array(json, "attributes")) {
            JsonObject a = value.getAsJsonObject();
            double amount = number(a, "amount", 1);
            attributes.add(new Attribute(a.get("name").getAsString(),
                    a.has("operation") ? a.get("operation").getAsString() : "add",
                    amount, number(a, "increment", amount)));
        }
        List<Status> statuses = new ArrayList<>();
        for (JsonElement value : array(json, "status_effects")) {
            JsonObject s = value.getAsJsonObject();
            statuses.add(new Status(s.get("name").getAsString(), (int) number(s, "power", 0),
                    (int) number(s, "increment", 1)));
        }
        return new DietSuiteRule(List.copyOf(conditions), List.copyOf(attributes), List.copyOf(statuses));
    }

    public int multiplier(Map<String, Double> normalizedValues) {
        int cumulative = 0;
        for (Condition condition : conditions) {
            int matches = condition.matches(normalizedValues);
            if (matches == 0) return 0;
            if (condition.match.equals("every")) cumulative += matches;
        }
        return Math.max(1, cumulative);
    }

    static JsonArray array(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : new JsonArray();
    }

    static double number(JsonObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        double result = json.get(key).getAsDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Non-finite Diet value " + key);
        return result;
    }

    public record Condition(Set<String> groups, String match, double above, double below) {
        public int matches(Map<String, Double> values) {
            long hits = groups.stream().filter(g -> values.containsKey(g) && inRange(values.get(g))).count();
            return switch (match) {
                case "every" -> (int) hits;
                case "all" -> hits == groups.size() ? 1 : 0;
                case "none" -> hits == 0 ? 1 : 0;
                case "average" -> !groups.isEmpty() && inRange(groups.stream()
                        .mapToDouble(g -> values.getOrDefault(g, 0.0)).sum() / groups.size()) ? 1 : 0;
                default -> hits > 0 ? 1 : 0;
            };
        }
        private boolean inRange(double value) { return value >= above && value <= below; }
    }
    public record Attribute(String name, String operation, double amount, double increment) {}
    public record Status(String name, int power, int increment) {}
}
