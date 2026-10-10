package com.lumbra.ldiediet.util;

import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NbtVariantIdsTest {
    @Test void compoundFieldOrderDoesNotChangeTheLegacyVariantId() throws Exception {
        assertEquals(NbtVariantIds.fromMatch(TagParser.parseTag("{Potion:'example:soup',meta:{quality:2b,amount:1},list:[1,2]}")),
                NbtVariantIds.fromMatch(TagParser.parseTag("{list:[1,2],meta:{amount:1,quality:2b},Potion:'example:soup'}")));
    }
    @Test void TypesAndListOrderRemainPartOfTheIdentity() throws Exception {
        String original = NbtVariantIds.fromMatch(TagParser.parseTag("{quality:2b,list:[1,2]}"));
        assertNotEquals(original, NbtVariantIds.fromMatch(TagParser.parseTag("{quality:2,list:[1,2]}")));
        assertNotEquals(original, NbtVariantIds.fromMatch(TagParser.parseTag("{quality:2b,list:[2,1]}")));
    }
}
