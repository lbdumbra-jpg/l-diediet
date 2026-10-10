package com.lumbra.ldiediet.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NutritionScaleDefinitionTest {
    @Test void rejectsNonPositiveAndUnrepresentableMaximum() {
        var id=new ResourceLocation("test:maximum");
        for(String value:new String[]{"-100","0","1e-500","1e100","\"NaN\""}) {
            var json=JsonParser.parseString("{\"max_value\":"+value+"}").getAsJsonObject();
            assertThrows(IllegalArgumentException.class,()->NutritionScaleDefinition.parse(id,json));
        }
        assertEquals(.0001f,NutritionScaleDefinition.parse(id,JsonParser.parseString("{\"max_value\":0.0001}").getAsJsonObject()).maxValue());
    }
    @Test void malformedThresholdAndModifierDoNotDropValidSiblings() {
        var json = JsonParser.parseString("""
                {"thresholds":[{"min":"invalid"},{"min":10,"max":20,"modifiers":[
                  {"attribute":"minecraft:generic.max_health","amount":"NaN"},
                  {"attribute":"minecraft:generic.max_health","amount":3}]}]}
                """).getAsJsonObject();
        var definition = NutritionScaleDefinition.parse(new ResourceLocation("ldiediet:test"), json);
        assertEquals(1, definition.thresholds().size());
        assertEquals(1, definition.thresholds().get(0).modifiers().size());
        assertEquals(3, definition.thresholds().get(0).modifiers().get(0).amount());
    }
    @Test
    void parsesGenericAttributeThresholds() {
        String json = """
                {"name":"Protein","max_value":80,"thresholds":[
                  {"min":60,"max":80,"modifiers":[
                    {"attribute":"minecraft:generic.max_health","amount":2,"operation":"addition"},
                    {"attribute":"irons_spells:max_mana","amount":0.1,"operation":"multiply_total"}
                  ]}
                ]}
                """;
        NutritionScaleDefinition definition = NutritionScaleDefinition.parse(
                new ResourceLocation("ldiediet", "proteins"), JsonParser.parseString(json).getAsJsonObject());
        assertEquals(80.0f, definition.maxValue());
        assertTrue(definition.thresholds().get(0).includes(65.0f));
        assertEquals(AttributeModifier.Operation.MULTIPLY_TOTAL,
                definition.thresholds().get(0).modifiers().get(1).operation());
    }
}
