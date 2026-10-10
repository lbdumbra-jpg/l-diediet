package com.lumbra.ldiediet.compat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DietSuiteRuleTest {
    @Test void inclusiveBoundsAndAll() {
        var condition = new DietSuiteRule.Condition(Set.of("a", "b"), "all", .8, 1);
        assertEquals(1, condition.matches(Map.of("a", .8, "b", 1.0)));
        assertEquals(0, condition.matches(Map.of("a", .8)));
        assertEquals(0, condition.matches(Map.of("a", .79, "b", 1.0)));
    }
    @Test void everyIsCumulative() {
        var rule = DietSuiteRule.parse(JsonParser.parseString("""
                {"conditions":[{"groups":["a","b","c"],"match":"every","above":0.8}]}
                """).getAsJsonObject());
        assertEquals(2, rule.multiplier(Map.of("a", .8, "b", .9, "c", .1)));
        assertEquals(0, rule.multiplier(Map.of("a", .1)));
    }
    @Test void allConditionsMustPass() {
        var rule = DietSuiteRule.parse(JsonParser.parseString("""
                {"conditions":[{"groups":["a"],"match":"all","above":0.8},
                  {"groups":["b"],"match":"none","above":0.8}]}
                """).getAsJsonObject());
        assertEquals(1, rule.multiplier(Map.of("a", .9, "b", .1)));
        assertEquals(0, rule.multiplier(Map.of("a", .9, "b", .9)));
    }
    @Test void averageIncludesMissingValuesAsZero() {
        var condition = new DietSuiteRule.Condition(Set.of("a", "b"), "average", .4, .6);
        assertEquals(1, condition.matches(Map.of("a", 1.0)));
        assertEquals(0, new DietSuiteRule.Condition(Set.of(), "average", 0, 1).matches(Map.of()));
    }
    @Test void incrementsAndDefaultsAreParsed() {
        var rule = DietSuiteRule.parse(JsonParser.parseString("""
                {"conditions":[{"groups":["a"]}],
                 "attributes":[{"name":"mod:attribute","amount":2,"increment":0.25}],
                 "status_effects":[{"name":"mod:effect","power":3}]}
                """).getAsJsonObject());
        assertEquals(2, rule.attributes().get(0).amount());
        assertEquals(.25, rule.attributes().get(0).increment());
        assertEquals(1, rule.statuses().get(0).increment());
    }
    @Test void unsupportedDietVersionFailsClearly() {
        assertDoesNotThrow(() -> DietMixinPlugin.validateVersion("2.1.1+1.20.1"));
        var error = assertThrows(IllegalStateException.class, () -> DietMixinPlugin.validateVersion("unknown"));
        assertTrue(error.getMessage().contains("avoid conflicting gameplay"));
    }
}
