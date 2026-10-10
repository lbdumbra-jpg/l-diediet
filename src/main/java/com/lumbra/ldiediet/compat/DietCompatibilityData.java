package com.lumbra.ldiediet.compat;

import com.google.gson.*;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.data.DietConfigManager;
import com.lumbra.ldiediet.data.NutritionScaleManager;
import com.lumbra.ldiediet.util.AtomicJsonFile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

 
public final class DietCompatibilityData extends SimplePreparableReloadListener<JsonObject> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final DietCompatibilityData INSTANCE = new DietCompatibilityData();
    private static final TagKey<Item> IGNORED_INGREDIENTS = TagKey.create(Registries.ITEM, new ResourceLocation("diet", "ingredients"));
    private volatile JsonObject resources = new JsonObject();
    private volatile Map<String, JsonObject> groups = Map.of();
    private volatile Map<String, JsonObject> suites = Map.of();
    private volatile Map<String, String> nutrientIds = Map.of();
    private volatile Map<String, List<DietSuiteRule>> rules = Map.of();

    private DietCompatibilityData() {}

    @Override protected JsonObject prepare(ResourceManager manager, ProfilerFiller profiler) {
        JsonObject result = new JsonObject();
        for (String directory : List.of("diet/groups", "diet/suites")) {
            manager.listResources(directory, id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
                try (var reader = resource.openAsReader()) {
                    JsonElement json = JsonParser.parseReader(reader);
                    if (!json.isJsonObject()) throw new IllegalArgumentException("Root is not an object");
                    result.add(id.toString(), json);
                } catch (Exception e) { LDieDiet.LOGGER.error("Cannot import Diet resource {}", id, e); }
            });
        }
        return result;
    }

    @Override protected void apply(JsonObject prepared, ResourceManager manager, ProfilerFiller profiler) {
        resources = prepared;
    }

     
    public synchronized void refresh(MinecraftServer server) {
        Map<String, JsonObject> nextGroups = mergeResources("diet/groups/", false);
        Map<String, JsonObject> nextSuites = mergeResources("diet/suites/", true);
        Map<String, String> ids = new LinkedHashMap<>();
        nextGroups.forEach((name, json) -> {
            var nativeScale = NutritionScaleManager.getInstance().get(name);
             
            ids.put(name, nativeScale != null ? name : "diet:" + name);
        });
        groups = Map.copyOf(nextGroups);
        suites = Map.copyOf(nextSuites);
        nutrientIds = Map.copyOf(ids);
        Map<String, List<DietSuiteRule>> nextRules = new LinkedHashMap<>();
        nextSuites.forEach((name, suite) -> {
            List<DietSuiteRule> list = new ArrayList<>();
            for (JsonElement effect : DietSuiteRule.array(suite, "effects")) {
                try { list.add(DietSuiteRule.parse(effect.getAsJsonObject())); }
                catch (RuntimeException e) { LDieDiet.LOGGER.error("Invalid imported Diet suite effect in {}", name, e); }
            }
            nextRules.put(name, List.copyOf(list));
        });
        rules = Map.copyOf(nextRules);
        JsonObject config = readConfig(server);
        decayPenalty = config.has("decayPenaltyPerGroup") ? Math.max(0, Math.min(100, config.get("decayPenaltyPerGroup").getAsInt())) : 15;
        Map<Item, Set<String>> memberships = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            Set<String> found = new LinkedHashSet<>();
            ItemStack stack = item.getDefaultInstance();
            for (String group : nextGroups.keySet()) {
                if (stack.is(TagKey.create(Registries.ITEM, new ResourceLocation("diet", group)))) found.add(group);
            }
            if (!found.isEmpty()) memberships.put(item, found);
        }
        if (!config.has("generateGroupsForEmptyItems") || config.get("generateGroupsForEmptyItems").getAsBoolean()) {
            inferRecipeGroups(server, memberships);
        }
        Map<Item, Double> overrides = parseFoodOverrides(DietSuiteRule.array(config, "foodOverrides"));
        Map<Item, JsonObject> nextFoods = new HashMap<>();
        memberships.forEach((item, assigned) -> {
            JsonObject nutrients = new JsonObject();
            for (String name : assigned) {
                double gain;
                try { gain = Math.max(0, DietSuiteRule.number(nextGroups.get(name), "gain_multiplier", 1)); }
                catch (RuntimeException e) { gain = 1; }
                nutrients.addProperty(ids.get(name), gain / assigned.size());
            }
            JsonObject profile = new JsonObject();
            profile.add("nutrients", nutrients);
            if (overrides.containsKey(item)) profile.addProperty("food_value", overrides.get(item));
            nextFoods.put(item, profile);
        });
        overrides.forEach((item, value) -> nextFoods.computeIfAbsent(item, ignored -> new JsonObject()).addProperty("food_value", value));
        DietConfigManager.setImportedFoods(nextFoods);
        NutritionScaleManager.getInstance().setImportedScales(nextGroups, ids);
        LDieDiet.LOGGER.info("Imported {} Diet groups, {} suites, {} food assignments", nextGroups.size(), nextSuites.size(), nextFoods.size());
    }

    private Map<String, JsonObject> mergeResources(String prefix, boolean suite) {
        Map<String, JsonObject> merged = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(resources.keySet());
        keys.sort(Comparator.<String, Boolean>comparing(key -> !key.startsWith("diet:"))
                .thenComparing(Comparator.naturalOrder()));
        for (String key : keys) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null || !id.getPath().startsWith(prefix)) continue;
            String name = id.getPath().substring(prefix.length(), id.getPath().length() - 5);
            if (name.startsWith("_")) continue;
            JsonObject value = resources.getAsJsonObject(key);
            try {
                JsonObject target = merged.containsKey(name) ? merged.get(name).deepCopy() : new JsonObject();
                if (suite && value.has("replace") && value.get("replace").getAsBoolean()) target = new JsonObject();
                for (var entry : value.entrySet()) {
                    if (suite && (entry.getKey().equals("groups") || entry.getKey().equals("effects"))) {
                        JsonArray array = target.has(entry.getKey()) ? target.getAsJsonArray(entry.getKey()) : new JsonArray();
                        for (JsonElement element : entry.getValue().getAsJsonArray()) array.add(element.deepCopy());
                        target.add(entry.getKey(), array);
                    } else { target.add(entry.getKey(), entry.getValue().deepCopy()); }
                }
                merged.put(name, target);
            } catch (RuntimeException e) { LDieDiet.LOGGER.error("Ignoring malformed Diet resource {}", key, e); }
        }
        return merged;
    }

    private static void inferRecipeGroups(MinecraftServer server, Map<Item, Set<String>> memberships) {
        Set<Item> explicit = Set.copyOf(memberships.keySet());
        Map<Item, Set<Item>> dependents = new HashMap<>();
        for (var recipe : server.getRecipeManager().getRecipes()) {
            try {
                Item result = recipe.getResultItem(server.registryAccess()).getItem();
                if (result == Items.AIR || explicit.contains(result)) continue;
                for (var ingredient : recipe.getIngredients()) for (ItemStack stack : ingredient.getItems()) {
                    if (!stack.is(IGNORED_INGREDIENTS)) {
                        dependents.computeIfAbsent(stack.getItem(), ignored -> new HashSet<>()).add(result);
                    }
                }
            } catch (RuntimeException e) { LDieDiet.LOGGER.debug("Cannot import recipe {}", recipe.getId(), e); }
        }
         
        ArrayDeque<Item> pending = new ArrayDeque<>(explicit);
        Set<Item> queued = new HashSet<>(explicit);
        while (!pending.isEmpty()) {
            Item source = pending.removeFirst(); queued.remove(source);
            for (Item result : dependents.getOrDefault(source, Set.of())) {
                if (memberships.computeIfAbsent(result, ignored -> new LinkedHashSet<>()).addAll(memberships.get(source))
                        && queued.add(result)) pending.addLast(result);
            }
        }
    }

    static Map<Item, Double> parseFoodOverrides(JsonArray array) {
        Map<Item, Double> result = new HashMap<>();
        for (JsonElement element : array) {
            try {
                String[] parts = element.getAsString().split(";", -1);
                if (parts.length != 2) throw new IllegalArgumentException("Expected item;value");
                ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
                double value = Double.parseDouble(parts[1].trim());
                if (id != null && ForgeRegistries.ITEMS.containsKey(id) && Double.isFinite(value) && value >= 0) {
                    result.put(ForgeRegistries.ITEMS.getValue(id), value);
                }
            } catch (RuntimeException e) { LDieDiet.LOGGER.warn("Ignoring invalid Diet foodOverride {}", element); }
        }
        return result;
    }

    private static JsonObject readConfig(MinecraftServer server) {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String key = UUID.nameUUIDFromBytes(world.toString().getBytes(StandardCharsets.UTF_8)).toString();
        Path cache = FMLPaths.CONFIGDIR.get().resolve("ldiediet/compat/diet/worlds/" + key + ".json");
        JsonObject sources = new JsonObject();
        if (Files.isRegularFile(cache)) {
            try (var reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
                JsonObject cached = JsonParser.parseReader(reader).getAsJsonObject();
                if (cached.has("sources")) sources = cached.getAsJsonObject("sources");
                else sources.add("world", cached);
            }
            catch (Exception e) { LDieDiet.LOGGER.error("Cannot read Diet config cache {}", cache, e); }
        }
         
        var paths = List.of(FMLPaths.GAMEDIR.get().resolve("defaultconfigs/diet-server.toml"),
                FMLPaths.CONFIGDIR.get().resolve("diet-server.toml"), world.resolve("serverconfig/diet-server.toml"));
        var names = List.of("default", "global", "world");
        for (int i = 0; i < paths.size(); i++) {
            Path path = paths.get(i);
            if (!Files.isRegularFile(path)) continue;
            try (FileConfig config = FileConfig.of(path)) {
                config.load();
                JsonObject source = new JsonObject();
                Object override = config.get("foodOverrides");
                if (override instanceof List<?> list) {
                    JsonArray values = new JsonArray();
                    list.forEach(value -> values.add(String.valueOf(value)));
                    source.add("foodOverrides", values);
                }
                Object decay = config.get("decayPenaltyPerGroup");
                if (decay instanceof Number value) source.addProperty("decayPenaltyPerGroup", value.intValue());
                Object generate = config.get("generateGroupsForEmptyItems");
                if (generate instanceof Boolean value) source.addProperty("generateGroupsForEmptyItems", value);
                sources.add(names.get(i), source);
            } catch (Exception e) { LDieDiet.LOGGER.error("Cannot read Diet config {}; retained prior import", path, e); }
        }
        JsonObject result = new JsonObject();
        for (String name : names) {
            if (sources.has(name) && sources.get(name).isJsonObject()) {
                sources.getAsJsonObject(name).entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue().deepCopy()));
            }
        }
        if (ModList.get().isLoaded("diet") || !result.entrySet().isEmpty()) {
            JsonObject snapshot = new JsonObject(); snapshot.add("sources", sources);
            try { AtomicJsonFile.write(cache, GSON, snapshot); }
            catch (Exception e) { LDieDiet.LOGGER.error("Cannot preserve imported Diet config {}", cache, e); }
        }
        return result;
    }

    private int decayPenalty = 15;
    public int decayPenaltyPerGroup() { return decayPenalty; }
    public double decayMultiplier(String nutrient) {
        for (var entry : groups.entrySet()) if (nutrientId(entry.getKey()).equals(nutrient)) {
            try { return Math.max(0, DietSuiteRule.number(entry.getValue(), "decay_multiplier", 1)); }
            catch (RuntimeException e) { return 1; }
        }
        return 1;
    }
    public boolean decayApplies(String nutrient, String suite) {
         
        if (!nutrient.startsWith("diet:")) return true;
        return suiteGroups(suite).stream().anyMatch(name -> nutrientId(name).equals(nutrient));
    }
    public Set<String> suiteNames() { return suites.keySet(); }
    public Map<String, JsonObject> groups() { return groups; }
    public List<DietSuiteRule> rules(String suite) { return rules.getOrDefault(suite, List.of()); }
    public Set<String> suiteGroups(String suite) {
        Set<String> result = new LinkedHashSet<>();
        JsonObject json = suites.get(suite);
        if (json != null) DietSuiteRule.array(json, "groups").forEach(group -> result.add(group.getAsString()));
        return result;
    }
    public String nutrientId(String name) { return nutrientIds.getOrDefault(name, "diet:" + name); }
}
