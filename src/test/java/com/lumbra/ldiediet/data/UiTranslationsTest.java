package com.lumbra.ldiediet.data;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class UiTranslationsTest {
    private JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/ldiediet/lang/"+locale+".json"))).getAsJsonObject();
    }
    @Test void allInterfaceKeysExistInBothLanguagesWithMatchingArguments() throws Exception {
        var english=language("en_us");var russian=language("ru_ru");
        var keyPattern=Pattern.compile("\"(ldiediet\\.(?:journal|gui|tooltip|condition|overlay)\\.[A-Za-z0-9_.]+)\"");
        var referenced=new TreeSet<String>();
        try(var files=Files.walk(Path.of("src/main/java/com/lumbra/ldiediet"))) {
            for(var path:files.filter(p->p.toString().endsWith(".java")&&!p.toString().contains("gametest")).toList()) {
                var matcher=keyPattern.matcher(Files.readString(path));while(matcher.find())if(!matcher.group(1).endsWith("."))referenced.add(matcher.group(1));
            }
        }
        for(String key:referenced) {
            assertTrue(english.has(key),"Missing English UI key: "+key);assertTrue(russian.has(key),"Missing Russian UI key: "+key);
            var arguments=Pattern.compile("%(?:[0-9]+\\$)?s");
            assertEquals(arguments.matcher(english.get(key).getAsString()).results().count(),arguments.matcher(russian.get(key).getAsString()).results().count(),"Different translation arguments: "+key);
        }
    }
    @Test void builtinGroupNamesAndMessagesHaveTranslationsWithoutChangingDatapackRules() throws Exception {
        var english=language("en_us");var russian=language("ru_ru");
        try(var files=Files.list(Path.of("src/testFixtures/original/data/ldiediet/food_groups"))) {
            for(var file:files.filter(p->p.toString().endsWith(".json")).toList()) {
                String id=file.getFileName().toString().replace(".json","");
                for(String suffix:List.of("name","active")) {
                    String key="ldiediet.group.ldiediet."+id+"."+suffix;
                    assertTrue(english.has(key)&&russian.has(key),"Untranslated built-in group: "+key);
                }
            }
        }
    }
}
