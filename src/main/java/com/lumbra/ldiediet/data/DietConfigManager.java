package com.lumbra.ldiediet.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.LDieDietServerConfig;
import com.lumbra.ldiediet.math.FormulaManager;
import com.lumbra.ldiediet.math.RecipeCciCalculator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

 
public final class DietConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Item, Double> ITEM_CCI = new HashMap<>();
    private static final Map<Item, FoodProfile> PROFILES = new HashMap<>();
    private static final Set<Item> MANUAL_CCI = new HashSet<>();
    private static Map<Item, JsonObject> importedFoods = Map.of();
    private static Map<Item, JsonObject> datapackFoods = Map.of();
    private static volatile Map<Item, FoodProfile> clientProfiles = Map.of();
    private static volatile Map<Item, Double> clientCci = Map.of();
    private static volatile double clientCciMin = 1;
    private static volatile double clientCciMax = 4;
    private static boolean writesAllowed = true;
    private static byte[] loadedFileBytes;
    private static boolean externalChangeReported;
    public static boolean canWriteConfiguration() { return writesAllowed; }
    public static synchronized boolean reloadConfig(MinecraftServer server) {
        flushPendingSave();
        loadConfig(server);
        return writesAllowed;
    }
    private static JsonObject currentRoot = emptyRoot();
    private static Map<Item, List<RecipeCciCalculator.Recipe<Item>>> cciRecipes = Map.of();
    private static RecipeCciCalculator<Item> cciCalculator;
    private static boolean pendingSave;
    private static final Set<Item> PENDING_CCI = new HashSet<>();
    private static RecipeManager indexedRecipeManager;
    private static long indexedAdapterRevision=-1;

    private DietConfigManager() {}

    public static synchronized void loadConfig(MinecraftServer server) {
        AuthorDiagnostics.clearParseIssues("diet_config/");
        File file = configFile();
        if (!file.exists()) writeDefaultConfig(file);
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject parsed = GSON.fromJson(reader, JsonObject.class);
            if (parsed == null || !parsed.has("overrides") || !parsed.get("overrides").isJsonObject()) {
                throw new IllegalArgumentException("Expected diet config object with overrides");
            }
            loadFromJson(parsed);
            writesAllowed = true;
            loadedFileBytes = Files.readAllBytes(file.toPath());
            externalChangeReported = false;
            pendingSave = false;
        } catch (Exception e) {
            writesAllowed = false;
            LDieDiet.LOGGER.error("Failed to load {}; retaining the previous configuration", file, e);
        }
        prepareRecipes(server);
    }

    public static synchronized void prepareRecipes(MinecraftServer server) {
        if (!LDieDietServerConfig.AUTOMATIC_CCI.get()) return;
        prepareRecipeIndex(server);
    }

    private static void prepareRecipeIndex(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        if (recipeManager == indexedRecipeManager && indexedAdapterRevision==com.lumbra.ldiediet.api.IntegrationApi.recipeRevision()) return;
        cciCalculator = null;
        Map<Item, List<RecipeCciCalculator.Recipe<Item>>> index = new HashMap<>();
        List<Recipe<?>> recipes = new ArrayList<>(recipeManager.getRecipes());
        recipes.sort(java.util.Comparator.comparing(recipe -> recipe.getId().toString()));
        for (Recipe<?> recipe : recipes) {
            try {
                var adapted=com.lumbra.ldiediet.api.IntegrationApi.adapt(recipe,server.registryAccess());
                if(adapted!=null){for(var value:adapted)addAdapterRecipe(index,value);continue;}
                ItemStack result = recipe.getResultItem(server.registryAccess());
                if (result.isEmpty()) continue;
                List<List<Item>> ingredients = recipe.getIngredients().stream()
                        .filter(ingredient -> !ingredient.isEmpty())
                        .map(ingredient -> java.util.Arrays.stream(ingredient.getItems())
                                .filter(stack -> !stack.isEmpty()).map(ItemStack::getItem).distinct()
                                .sorted(java.util.Comparator.comparing(item -> String.valueOf(ForgeRegistries.ITEMS.getKey(item))))
                                .toList()).toList();
                index.computeIfAbsent(result.getItem(), ignored -> new ArrayList<>())
                        .add(new RecipeCciCalculator.Recipe<>(ingredients));
            } catch (RuntimeException e) {
                LDieDiet.LOGGER.warn("Ignoring malformed CCI recipe {}", recipe.getId(), e);
            }
        }
        for(var value:com.lumbra.ldiediet.api.IntegrationApi.contributed(server))addAdapterRecipe(index,value);
        cciRecipes = index;
        indexedAdapterRevision=com.lumbra.ldiediet.api.IntegrationApi.recipeRevision();
        indexedRecipeManager = recipeManager;
        LDieDiet.LOGGER.info("Indexed {} recipe result items for lazy CCI calculation", index.size());
    }

    private static void addAdapterRecipe(Map<Item,List<RecipeCciCalculator.Recipe<Item>>> index,com.lumbra.ldiediet.api.IntegrationApi.CciRecipe recipe) {
        var slots=recipe.ingredients().stream().map(slot->slot.stream().map(ItemStack::getItem).distinct().toList()).toList();
        index.computeIfAbsent(recipe.output().getItem(),item->new ArrayList<>()).add(new RecipeCciCalculator.Recipe<>(slots,recipe.processingWeight()));
    }
     
    public static synchronized void reloadRecipes(MinecraftServer server) {
        indexedRecipeManager = null;
        cciRecipes = Map.of();
        cciCalculator = null;
        prepareRecipes(server);
    }

    private static boolean isAutomatic(JsonObject json) {
        try { return json.has("cci_source") && "auto".equalsIgnoreCase(json.get("cci_source").getAsString()); }
        catch (RuntimeException ignored) { return false; }
    }

    private static void loadFromJson(JsonObject root) {
        PENDING_CCI.clear();
        ITEM_CCI.clear();
        PROFILES.clear();
        MANUAL_CCI.clear();
        cciCalculator = null;
        currentRoot = root.deepCopy();
        JsonObject overrides = overrides(currentRoot);
        for (Map.Entry<String, com.google.gson.JsonElement> entry : overrides.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null || !entry.getValue().isJsonObject()) {
                LDieDiet.LOGGER.warn("Ignoring invalid diet_config entry '{}'", entry.getKey());
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || !ForgeRegistries.ITEMS.containsKey(id)) continue;
            JsonObject itemJson = entry.getValue().getAsJsonObject();
            try {
                PROFILES.put(item, FoodProfile.parse(effectiveProfile(item, itemJson), entry.getKey()));
            } catch (RuntimeException e) {
                AuthorDiagnostics.parseIssue("diet_config/"+entry.getKey(),e.getMessage());
                LDieDiet.LOGGER.warn("Ignoring malformed food entry {}", entry.getKey(), e);
                continue;
            }
            if (itemJson.has("cci")) {
                try {
                    double cci = itemJson.get("cci").getAsDouble();
                    if (!Double.isFinite(cci)) throw new IllegalArgumentException("Non-finite CCI");
                    ITEM_CCI.put(item, clampCci(cci));
                    if (!isAutomatic(itemJson)) MANUAL_CCI.add(item);
                } catch (RuntimeException e) {
                    LDieDiet.LOGGER.warn("Ignoring invalid CCI for {}", id);
                }
            }
        }
        loadImportedProfiles();
        PROFILES.forEach((item, profile) -> {
            if (profile.baseCci() != null) {
                ITEM_CCI.put(item, clampCci(profile.baseCci()));
                MANUAL_CCI.add(item);
            }
        });
    }

    private static void loadImportedProfiles() {
        Map<Item, JsonObject> foods = new HashMap<>(importedFoods);
        datapackFoods.forEach((item, json) -> {
            JsonObject merged = foods.containsKey(item) ? foods.get(item).deepCopy() : new JsonObject();
            json.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
            foods.put(item, merged);
        });
        foods.forEach((item, json) -> {
            if (PROFILES.containsKey(item)) return;
            try { PROFILES.put(item, FoodProfile.parse(json, String.valueOf(ForgeRegistries.ITEMS.getKey(item)))); }
            catch (RuntimeException e) { LDieDiet.LOGGER.warn("Ignoring malformed imported food {}", item, e); }
        });
    }

    private static File configFile() {
        File directory = FMLPaths.CONFIGDIR.get().resolve(LDieDiet.MOD_ID).toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            LDieDiet.LOGGER.error("Could not create config directory {}", directory);
        }
        return new File(directory, "diet_config.json");
    }

    public static synchronized void setImportedFoods(Map<Item, JsonObject> imported) {
        Map<Item, JsonObject> snapshot = new HashMap<>();
        imported.forEach((item, json) -> snapshot.put(item, json.deepCopy()));
        importedFoods = Map.copyOf(snapshot);
        loadFromJson(currentRoot);
    }

    public static synchronized void setDatapackFoods(Map<Item, JsonObject> foods) {
        Map<Item, JsonObject> snapshot = new HashMap<>();
        foods.forEach((item, json) -> snapshot.put(item, json.deepCopy()));
        datapackFoods = Map.copyOf(snapshot);
        loadFromJson(currentRoot);
    }

    private static JsonObject effectiveProfile(Item item, JsonObject nativeJson) {
        JsonObject result = importedFoods.containsKey(item) ? importedFoods.get(item).deepCopy() : new JsonObject();
        if (datapackFoods.containsKey(item))
            datapackFoods.get(item).entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue().deepCopy()));
        nativeJson.entrySet().forEach(entry -> {
            if (!entry.getKey().equals("cci") || !isAutomatic(nativeJson))
                result.add(entry.getKey(), entry.getValue().deepCopy());
        });
        return result;
    }

    private static JsonObject emptyRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 2);
        root.add("overrides", new JsonObject());
        return root;
    }

    private static void writeDefaultConfig(File file) {
        try (var stream = DietConfigManager.class.getResourceAsStream("/defaults/diet_config.json")) {
            if (stream == null) throw new IOException("Missing default food configuration");
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            writeJson(file, root, "default diet configuration");
        } catch (IOException e) { LDieDiet.LOGGER.error("Cannot read default food configuration", e); }
    }

    private static JsonObject overrides(JsonObject root) {
        if (!root.has("overrides") || !root.get("overrides").isJsonObject()) {
            root.add("overrides", new JsonObject());
        }
        return root.getAsJsonObject("overrides");
    }

    public static synchronized double getOrCalculateCCI(ItemStack stack, MinecraftServer server) {
        ensureItemCci(stack.getItem(), server);
        return clampCci(resolved(stack).cci());
    }

    private static void ensureItemCci(Item item, MinecraftServer server) {
        if (ITEM_CCI.containsKey(item) || !LDieDietServerConfig.AUTOMATIC_CCI.get()) return;
        prepareRecipeIndex(server);
        double calculated = calculateItemCCI(item);
        ITEM_CCI.put(item, calculated);
        putAutomaticCci(item, calculated);
    }

     
    public static synchronized int updateConfig(MinecraftServer server) {
        prepareRecipes(server);
        JsonObject configured = overrides(currentRoot);
        int added = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!isCciFood(item)) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || configured.has(id.toString())) continue;
            JsonObject itemJson = new JsonObject();
            if (LDieDietServerConfig.AUTOMATIC_CCI.get()) {
                double cci = calculateItemCCI(item);
                ITEM_CCI.put(item, cci);
                itemJson.addProperty("cci", cci);
                itemJson.addProperty("cci_source", "auto");
                PENDING_CCI.add(item);
            }
            configured.add(id.toString(), itemJson);
            PROFILES.put(item, FoodProfile.parse(effectiveProfile(item, itemJson), id.toString()));
            added++;
        }
        if (added > 0) saveConfig();
        return added;
    }

     
    public static synchronized int recalculateAllCCI(MinecraftServer server) {
        prepareRecipeIndex(server);
        cciCalculator = null;
        int count = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!isCciFood(item) || MANUAL_CCI.contains(item)) continue;
            double cci = calculateItemCCI(item);
            ITEM_CCI.put(item, cci);
            putAutomaticCci(item, cci);
            count++;
        }
        saveConfig();
        return count;
    }

     
    public static void autoCalculateCCI(MinecraftServer server) {
        updateConfig(server);
    }

    private static void putAutomaticCci(Item item, double cci) {
        PENDING_CCI.add(item);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return;
        JsonObject itemJson = overrides(currentRoot).has(id.toString())
                && overrides(currentRoot).get(id.toString()).isJsonObject()
                ? overrides(currentRoot).getAsJsonObject(id.toString()) : new JsonObject();
        itemJson.addProperty("cci", cci);
        itemJson.addProperty("cci_source", "auto");
        pendingSave = true;
        overrides(currentRoot).add(id.toString(), itemJson);
        PROFILES.put(item, FoodProfile.parse(effectiveProfile(item, itemJson), id.toString()));
    }

    private static double calculateItemCCI(Item item) {
        if (cciCalculator == null) {
            Map<Item, Double> manual = new HashMap<>();
            for (Item locked : MANUAL_CCI) manual.put(locked, clampCci(ITEM_CCI.get(locked)));
            cciCalculator = new RecipeCciCalculator<>(cciRecipes, manual,
                    FormulaManager.getCCIMin(), FormulaManager.getCCIMax(), (average, count, stages) ->
                    FormulaManager.evaluate("cci_formula", Map.of("average_ingredients_cci", average,
                            "ingredients_count", (double) count, "stages_weight", stages)));
        }
        return cciCalculator.calculate(item);
    }

    private static boolean isCciFood(Item item) {
        if (item.isEdible() || item == net.minecraft.world.item.Items.CAKE || PROFILES.containsKey(item)) return true;
        var animation = new ItemStack(item).getUseAnimation();
        return animation == net.minecraft.world.item.UseAnim.EAT || animation == net.minecraft.world.item.UseAnim.DRINK;
    }

    private static double clampCci(double value) {
        if (!Double.isFinite(value)) return FormulaManager.getCCIMin();
        return Math.max(FormulaManager.getCCIMin(), Math.min(FormulaManager.getCCIMax(), value));
    }

    public static synchronized int cleanConfig() {
        JsonObject configured = overrides(currentRoot);
        List<String> removed = new ArrayList<>();
        for (String key : configured.keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) removed.add(key);
        }
        removed.forEach(configured::remove);
        if (!removed.isEmpty()) { loadFromJson(currentRoot); saveConfig(); }
        return removed.size();
    }

    public static synchronized void saveConfig() {
        if (!writesAllowed) {
            LDieDiet.LOGGER.warn("Keeping malformed diet_config.json untouched; reload a repaired configuration before saving");
            return;
        }
        try {
            if (loadedFileBytes != null && (!Files.exists(configFile().toPath())
                    || !java.util.Arrays.equals(loadedFileBytes,Files.readAllBytes(configFile().toPath())))) {
                if (!externalChangeReported) LDieDiet.LOGGER.warn("diet_config.json was edited externally; saving paused. Run /ldiediet reload or /reload to import edits.");
                externalChangeReported = true;
                return;
            }
        } catch (IOException e) { LDieDiet.LOGGER.error("Cannot check configuration before saving",e); return; }
        JsonObject sortedRoot = currentRoot.deepCopy();
        JsonObject sorted = new JsonObject();
        Map<String, com.google.gson.JsonElement> entries = new TreeMap<>();
        overrides(currentRoot).entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue().deepCopy()));
        entries.forEach(sorted::add);
        sortedRoot.add("overrides", sorted);
        if (writeJson(configFile(), sortedRoot, "diet configuration")) {
            currentRoot = sortedRoot;
            try { loadedFileBytes = Files.readAllBytes(configFile().toPath()); }
            catch (IOException e) { writesAllowed = false; LDieDiet.LOGGER.error("Cannot verify saved configuration",e); }
            pendingSave = false;
        }
    }

    public static synchronized void flushPendingSave() {
        if (pendingSave && writesAllowed) saveConfig();
    }
    public static synchronized void flushCciUpdates(MinecraftServer server) {
        if (PENDING_CCI.isEmpty()) return;
        CompoundTag values = new CompoundTag();
        for (Item item : List.copyOf(PENDING_CCI)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null && ITEM_CCI.containsKey(item)) values.putDouble(id.toString(), getCCI(item));
            if (values.size() == 64) { broadcastCci(server, values); values = new CompoundTag(); }
        }
        if (!values.isEmpty()) broadcastCci(server, values);
        PENDING_CCI.clear();
    }
    private static void broadcastCci(MinecraftServer server, CompoundTag values) {
        var packet = new com.lumbra.ldiediet.network.SyncCciPacket(values);
        for (var player : server.getPlayerList().getPlayers()) com.lumbra.ldiediet.network.ModNetworkHandler.sendToPlayer(packet, player);
    }
    public static void clearClientSnapshot() {
        clientProfiles = Map.of(); clientCci = Map.of(); clientCciMin = 1; clientCciMax = 4;
    }
    public static boolean hasClientCci(Item item) { return clientCci.containsKey(item); }
    public static void applyClientCci(CompoundTag values) {
        Map<Item, Double> next = new HashMap<>(clientCci);
        for (String key : values.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            double value = values.getDouble(key);
            if (id != null && values.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                    && ForgeRegistries.ITEMS.containsKey(id) && Double.isFinite(value))
                next.put(ForgeRegistries.ITEMS.getValue(id), Math.max(clientCciMin, Math.min(clientCciMax, value)));
        }
        clientCci = Map.copyOf(next);
    }

    private static boolean writeJson(File file, JsonObject json, String description) {
        try {
            com.lumbra.ldiediet.util.AtomicJsonFile.write(file.toPath(), GSON, json);
            return true;
        } catch (IOException e) {
            LDieDiet.LOGGER.error("Failed to write {} to {}", description, file, e);
            return false;
        }
    }

    private static FoodProfile.Resolved resolved(ItemStack stack) {
        FoodProfile profile = PROFILES.getOrDefault(stack.getItem(), FoodProfile.DEFAULT);
        return profile.resolve(stack, ITEM_CCI.getOrDefault(stack.getItem(), FormulaManager.getCCIMin()));
    }

    public static double getCCI(Item item) {
        return ITEM_CCI.getOrDefault(item, FormulaManager.getCCIMin());
    }
    public static java.util.Set<String> usedNutrients() {
        var keys=new java.util.HashSet<String>();for(var p:PROFILES.values())keys.addAll(p.nutrientKeys());return keys;
    }
    public static ItemStack representative(FoodKey key){return PROFILES.getOrDefault(key.item(),FoodProfile.DEFAULT).representative(key);}
    public static boolean hasConfiguredProfile(Item item) { return PROFILES.containsKey(item); }

    public static double getCCI(ItemStack stack) {
        return clampCci(resolved(stack).cci());
    }

    public static double getCCI(FoodKey key) {
        Item item = key.item();
        return clampCci(PROFILES.getOrDefault(item, FoodProfile.DEFAULT)
                .cciForVariant(key.variantId(), ITEM_CCI.getOrDefault(item, FormulaManager.getCCIMin())));
    }

    public static synchronized double getOrCalculateCCI(FoodKey key, MinecraftServer server) {
        if (!ForgeRegistries.ITEMS.containsKey(key.itemId())) return getCCI(key);
        ensureItemCci(key.item(), server);
        return getCCI(key);
    }

    public static double getBaseMonotony(Item item) {
        return resolved(new ItemStack(item)).baseMonotony();
    }

    public static double getBaseMonotony(ItemStack stack) {
        return resolved(stack).baseMonotony();
    }

    public static boolean isExcluded(ItemStack stack) {
        return resolved(stack).exclusion();
    }

    public static Map<String, Double> getNutrients(ItemStack stack) {
        return resolved(stack).nutrients();
    }

    public static Double getFoodValueOverride(ItemStack stack) { return resolved(stack).foodValue(); }
    public record FoodSettings(FoodKey key, double cci, double baseMonotony, Double foodValue,
                               boolean exclusion, Map<String, Double> nutrients) {}
    public static FoodSettings describeFood(ItemStack stack, boolean client) {
        var profile = client ? clientResolved(stack) : resolved(stack);
        double min = client ? clientCciMin : FormulaManager.getCCIMin();
        double max = client ? clientCciMax : FormulaManager.getCCIMax();
        double cci = Double.isFinite(profile.cci()) ? Math.max(min, Math.min(max, profile.cci())) : min;
        return new FoodSettings(new FoodKey(ForgeRegistries.ITEMS.getKey(stack.getItem()), profile.variantId()),
                cci, profile.baseMonotony(), profile.foodValue(), profile.exclusion(), profile.nutrients());
    }
    public static FoodKey getFoodKey(ItemStack stack, boolean client) {
        return new FoodKey(ForgeRegistries.ITEMS.getKey(stack.getItem()),
                (client ? clientResolved(stack) : resolved(stack)).variantId());
    }
    private static FoodProfile.Resolved clientResolved(ItemStack stack) {
        return clientProfiles.getOrDefault(stack.getItem(), FoodProfile.DEFAULT)
                .resolve(stack, clientCci.getOrDefault(stack.getItem(), clientCciMin));
    }
    public static double getClientCCI(ItemStack stack) {
        double value = clientResolved(stack).cci();
        return Double.isFinite(value) ? Math.max(clientCciMin, Math.min(clientCciMax, value)) : clientCciMin;
    }
    public static Map<String, Double> getClientNutrients(ItemStack stack) { return clientResolved(stack).nutrients(); }
    public static boolean isClientExcluded(ItemStack stack) { return clientResolved(stack).exclusion(); }

    public static synchronized CompoundTag serializeToNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag values = new CompoundTag();
        ITEM_CCI.forEach((item, value) -> values.putDouble(ForgeRegistries.ITEMS.getKey(item).toString(), value));
        tag.put("calculated_cci", values);
        tag.putDouble("cci_min", FormulaManager.getCCIMin());
        tag.putDouble("cci_max", FormulaManager.getCCIMax());
        JsonObject effective = currentRoot.deepCopy();
        importedFoods.forEach((item, json) -> {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) return;
            JsonObject nativeJson = overrides(currentRoot).has(id.toString()) && overrides(currentRoot).get(id.toString()).isJsonObject()
                    ? overrides(currentRoot).getAsJsonObject(id.toString()) : new JsonObject();
            overrides(effective).add(id.toString(), effectiveProfile(item, nativeJson));
        });
        tag.put("config", com.lumbra.ldiediet.util.JsonNbt.encode(effective));
        return tag;
    }

    public static synchronized void deserializeFromNBT(CompoundTag tag) {
        if (!tag.contains("config") && !tag.contains("configJson")) return;
        try {
            JsonObject root = tag.contains("config") ? com.lumbra.ldiediet.util.JsonNbt.decode(tag.getCompound("config")).getAsJsonObject()
                    : JsonParser.parseString(tag.getString("configJson")).getAsJsonObject();
            Map<Item, FoodProfile> profiles = new HashMap<>();
            Map<Item, Double> values = new HashMap<>();
            for (var entry : overrides(root).entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null || !ForgeRegistries.ITEMS.containsKey(id) || !entry.getValue().isJsonObject()) continue;
                Item item = ForgeRegistries.ITEMS.getValue(id);
                try {
                    JsonObject json = entry.getValue().getAsJsonObject();
                    JsonObject profile = json.deepCopy();
                    if (isAutomatic(profile)) profile.remove("cci");
                    profiles.put(item, FoodProfile.parse(profile, entry.getKey()));
                    if (json.has("cci") && Double.isFinite(json.get("cci").getAsDouble())) values.put(item, json.get("cci").getAsDouble());
                } catch (RuntimeException e) { LDieDiet.LOGGER.warn("Ignoring malformed synchronized food {}", id, e); }
            }
            clientCci = Map.copyOf(values);
            clientProfiles = Map.copyOf(profiles);
            double min = tag.contains("cci_min") ? tag.getDouble("cci_min") : 1;
            double max = tag.contains("cci_max") ? tag.getDouble("cci_max") : 4;
            if (Double.isFinite(min) && min > 0 && Double.isFinite(max) && max >= min) {
                clientCciMin = min; clientCciMax = max;
            }
            applyClientCci(tag.getCompound("calculated_cci"));
        } catch (RuntimeException e) {
            LDieDiet.LOGGER.error("Failed to deserialize synchronized diet configuration", e);
        }
    }
}
