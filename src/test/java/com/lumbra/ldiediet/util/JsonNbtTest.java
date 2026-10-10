package com.lumbra.ldiediet.util;

import com.google.gson.*;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonNbtTest {
    @Test void largeConfigurationSurvivesActualNbtWireEncoding() throws Exception {
        JsonObject config = new JsonObject();
        for (int i = 0; i < 2000; i++) {
            JsonObject food = new JsonObject();
            food.addProperty("cci", 2.5);
            JsonObject nutrients = new JsonObject(); nutrients.addProperty("mod:configured_nutrient", .25);
            food.add("nutrients", nutrients);
            config.add("mod:food_" + i, food);
        }
        assertTrue(config.toString().length() > 65535);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        NbtIo.write(JsonNbt.encode(config), new DataOutputStream(wire));
        var read = NbtIo.read(new DataInputStream(new ByteArrayInputStream(wire.toByteArray())));
        assertEquals(config, JsonNbt.decode(read));
    }
    @Test void mixedArraysUnicodeAndNullArePreserved() {
        JsonElement value = JsonParser.parseString("""
                {"имя":"Тест","rules":[null,true,4.5,"text",{"nested":[1,false]}]}
                """);
        assertEquals(value, JsonNbt.decode(JsonNbt.encode(value)));
    }
}
