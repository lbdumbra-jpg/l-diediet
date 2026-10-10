package com.lumbra.ldiediet.compat;

import com.google.gson.*;
import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.util.AtomicJsonFile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

 
public final class DietResourceCache {
    public static final String PACK_ID = "ldiediet:diet_baseline";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FMLPaths.CONFIGDIR.get().resolve("ldiediet/compat/diet");
    private static final Path PACK = ROOT.resolve("baseline-pack");
    private DietResourceCache() {}

    public static void initialize() {
        var modFile = ModList.get().getModFileById("diet");
        if (modFile == null) return;
        try {
            Path data = modFile.getFile().findResource("data");
            JsonObject baseline = new JsonObject();
            try (var paths = Files.walk(data)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                    String relative = "data/" + data.relativize(path).toString().replace('\\', '/');
                    baseline.addProperty(relative, Files.readString(path, StandardCharsets.UTF_8));
                }
            }
             
            AtomicJsonFile.write(ROOT.resolve("baseline.json"), GSON, baseline);
            LDieDiet.LOGGER.info("Diet gameplay disabled; cached {} original data resources", baseline.size());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot preserve Diet resources before disabling its gameplay", e);
        }
    }

    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !Files.isRegularFile(ROOT.resolve("baseline.json"))) return;
        try (var reader = Files.newBufferedReader(ROOT.resolve("baseline.json"), StandardCharsets.UTF_8)) {
            JsonObject baseline = JsonParser.parseReader(reader).getAsJsonObject();
            Path absolutePack = PACK.toAbsolutePath().normalize();
            Files.createDirectories(absolutePack);
             
            Path index = ROOT.resolve("materialized.json");
            if (Files.isRegularFile(index)) {
                try (var oldReader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
                    for (String key : JsonParser.parseReader(oldReader).getAsJsonObject().keySet()) {
                        if (!baseline.has(key)) Files.deleteIfExists(checkedPath(absolutePack, key));
                    }
                }
            }
            for (var entry : baseline.entrySet()) {
                Path destination = checkedPath(absolutePack, entry.getKey());
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, entry.getValue().getAsString(), StandardCharsets.UTF_8);
            }
            Files.writeString(absolutePack.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":15,\"description\":\"L'DieDiet cached Diet resources\"}}", StandardCharsets.UTF_8);
            AtomicJsonFile.write(index, GSON, baseline);
            event.addRepositorySource(consumer -> {
                Pack.ResourcesSupplier supplier = id -> new PathPackResources(id, absolutePack, false);
                var info = Pack.readPackInfo(PACK_ID, supplier);
                if (info == null) throw new IllegalStateException("Cannot load cached Diet resource pack");
                Pack pack = Pack.create(PACK_ID, Component.translatable("ldiediet.compat.diet_pack"), true,
                        supplier, info, PackType.SERVER_DATA, Pack.Position.BOTTOM, true, PackSource.BUILT_IN);
                consumer.accept(pack);
            });
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load preserved Diet data", e);
        }
    }

    private static Path checkedPath(Path root, String relative) {
        Path path = root.resolve(relative).normalize();
        if (!relative.startsWith("data/") || !relative.endsWith(".json") || !path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid cached Diet resource path: " + relative);
        }
        return path;
    }
}
