package com.lumbra.ldiediet.gametest;

import com.google.gson.*;
import com.lumbra.ldiediet.data.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import java.util.Map;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class DataRobustnessGameTests {
    private static final class Groups extends FoodGroupManager {
        void load(Map<ResourceLocation, JsonElement> data) { apply(data, null, InactiveProfiler.INSTANCE); }
    }
    private static final class Exclusions extends ExclusionManager {
        void load(Map<ResourceLocation, JsonElement> data) { apply(data, null, InactiveProfiler.INSTANCE); }
    }
    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_data", timeoutTicks = 100)
    public static void overlappingTagsAndItemsAreIndexedOnceAndSynchronizedAsResolvedItems(GameTestHelper h) {
        var manager = new Groups();
        var id = new ResourceLocation("fixture:tag_group");
        manager.load(Map.of(id, JsonParser.parseString("{\"items\":[\"minecraft:sweet_berries\",\"minecraft:sweet_berries\",\"#minecraft:fox_food\"]}")));
        manager.refreshMemberships();
        h.assertTrue(manager.getGroupsForItem("minecraft:sweet_berries").size() == 1, "Overlapping item/tag duplicated a group");
        var client = new FoodGroupManager();
        client.deserializeFromNBT(manager.serializeToNBT());
        h.assertTrue(client.getGroupsForItem("minecraft:sweet_berries").size() == 1, "Resolved membership was lost or duplicated on sync");
        h.assertTrue(client.getGroup(id).getItems().stream().noneMatch(item -> item.startsWith("#")), "Client still depends on tag binding order");
        h.succeed();
    }

    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_data", timeoutTicks = 100)
    public static void datapackProfilesHaveConfigPriorityStableCciAndCleanRemoval(GameTestHelper h) throws Exception {
        var rootField = DietConfigManager.class.getDeclaredField("currentRoot"); rootField.setAccessible(true);
        var packField = DietConfigManager.class.getDeclaredField("datapackFoods"); packField.setAccessible(true);
        var importField = DietConfigManager.class.getDeclaredField("importedFoods"); importField.setAccessible(true);
        var loader = DietConfigManager.class.getDeclaredMethod("loadFromJson", JsonObject.class); loader.setAccessible(true);
        var root = ((JsonObject) rootField.get(null)).deepCopy();
        var packs = (Map<net.minecraft.world.item.Item, JsonObject>) packField.get(null);
        var imported = (Map<net.minecraft.world.item.Item, JsonObject>) importField.get(null);
        try {
            loader.invoke(null, JsonParser.parseString("{\"schema_version\":2,\"overrides\":{}}").getAsJsonObject());
            DietConfigManager.setImportedFoods(Map.of(Items.APPLE, JsonParser.parseString("{\"nutrients\":{\"proteins\":1}}").getAsJsonObject()));
            DietConfigManager.setDatapackFoods(Map.of(Items.APPLE, JsonParser.parseString("{\"cci\":3,\"nutrients\":{\"fruits\":1},\"is_exclusion\":true}").getAsJsonObject()));
            var apple = Items.APPLE.getDefaultInstance();
            h.assertTrue(DietConfigManager.getNutrients(apple).equals(Map.of("fruits", 1.0)), "Datapack did not replace imported nutrients");
            h.assertTrue(DietConfigManager.getCCI(Items.APPLE) == 3, "Datapack CCI is absent from the authoritative cache");
            loader.invoke(null, JsonParser.parseString("{\"overrides\":{\"minecraft:apple\":{\"cci\":1,\"cci_source\":\"auto\"}}}").getAsJsonObject());
            h.assertTrue(DietConfigManager.getCCI(Items.APPLE) == 3 && DietConfigManager.getCCI(apple) == 3,
                    "Old automatic cache overrode explicit datapack CCI");
            loader.invoke(null, JsonParser.parseString("{\"overrides\":{\"minecraft:apple\":{\"cci\":2,\"nutrients\":{}}}}").getAsJsonObject());
            h.assertTrue(DietConfigManager.getNutrients(apple).isEmpty() && DietConfigManager.getCCI(Items.APPLE) == 2, "Config did not override datapack, including explicit empty nutrients");
            DietConfigManager.setDatapackFoods(Map.of());
            loader.invoke(null, JsonParser.parseString("{\"overrides\":{}}").getAsJsonObject());
            h.assertTrue(DietConfigManager.getNutrients(apple).equals(Map.of("proteins", 1.0)) && DietConfigManager.getCCI(Items.APPLE) == 1, "Removed datapack left stale nutrients or CCI");
            h.succeed();
        } finally {
            DietConfigManager.setImportedFoods(imported); DietConfigManager.setDatapackFoods(packs); loader.invoke(null, root);
        }
    }
    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_data", timeoutTicks = 100)
    public static void damagedGroupItemsAndEffectsDoNotDiscardValidSiblings(GameTestHelper h) {
        var manager = new Groups(); var id = new ResourceLocation("fixture:good");
        manager.load(Map.of(id, JsonParser.parseString("""
                {"items":[{},"bad item","minecraft:bread"],"effects":[
                  {"amplifier":{}},{"effect_type":"health","value":4,"duration_type":"until_death"}]}
                """)));
        var group = manager.getGroup(id);
        h.assertTrue(group != null && group.getEffects().size() == 1 && group.getEffects().get(0).getValue() == 4,
                "Invalid effect discarded valid siblings");
        h.assertTrue(manager.getGroupsForItem("minecraft:bread").size() == 1, "Invalid item discarded valid items");
        CompoundTag snapshot = manager.serializeToNBT(); CompoundTag bad = new CompoundTag(); bad.putString("id", "invalid ID");
        snapshot.getList("groups", Tag.TAG_COMPOUND).add(0, bad);
        var client = new FoodGroupManager(); client.deserializeFromNBT(snapshot);
        h.assertTrue(client.getGroup(id) != null, "Invalid synchronized ID discarded following group");
        h.succeed();
    }
    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_data", timeoutTicks = 100)
    public static void damagedExclusionsDoNotDiscardFollowingOverrides(GameTestHelper h) {
        var manager = new Exclusions();
        manager.load(Map.of(new ResourceLocation("fixture:test"), JsonParser.parseString(
                "{\"overrides\":{\"minecraft:stone\":{},\"invalid ID\":1,\"minecraft:apple\":9,\"minecraft:bread\":2}}")));
        h.assertTrue(manager.getStageOverrides().size() == 1 && manager.getStageOverrides().get(Items.BREAD) == 2,
                "Invalid exclusion discarded valid sibling or accepted invalid stage");
        CompoundTag snapshot = manager.serializeToNBT(); snapshot.putInt("invalid ID", 1); snapshot.putInt("minecraft:apple", 9);
        manager.deserializeFromNBT(snapshot);
        h.assertTrue(manager.getStageOverrides().size() == 1 && manager.getStageOverrides().get(Items.BREAD) == 2,
                "Invalid synchronized exclusions accepted");
        h.succeed();
    }
}
