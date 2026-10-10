package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.data.*;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

 
@Mod.EventBusSubscriber(modid = LDieDiet.MOD_ID)
public final class ServerAcceptanceChecks {
    private static boolean started, reloaded, finished;
    private static int ticks;
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("ldiediet.serverAcceptance")) return;
        started = true;
        event.getServer().execute(() -> {
            if(Boolean.getBoolean("ldiediet.testPackAcceptance")) {
                var repository=event.getServer().getPackRepository();
                String baseline=com.lumbra.ldiediet.compat.DietResourceCache.PACK_ID;
                if(repository.getAvailableIds().contains(baseline)) {
                     
                    var stale=new java.util.ArrayList<>(repository.getSelectedIds());stale.remove(baseline);stale.add(baseline);
                    repository.setSelected(stale);
                    if(!repository.getSelectedPacks().iterator().next().getId().equals(baseline))throw new IllegalStateException("Saved order promoted the Diet cache above the author's pack");
                    LDieDiet.LOGGER.info("LAB PACK PRIORITY PASS: stale saved order normalized before /reload");
                }
            }
            event.getServer().getCommands().performPrefixedCommand(event.getServer().createCommandSourceStack(), "reload");
        });
    }
    @SubscribeEvent public static void synced(OnDatapackSyncEvent event) {
        if (started && event.getPlayer() == null) reloaded = true;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!started || finished || event.phase != TickEvent.Phase.END) return;
        if (!reloaded && ++ticks < 400) return;
        finished = true;
        try {
            if (!reloaded || FoodGroupManager.getInstance().getAllGroups().isEmpty()) throw new IllegalStateException("Real datapack reload failed");
            if (Boolean.getBoolean("ldiediet.testPackAcceptance")) TestPackAcceptanceChecks.verify(event.getServer());
            if(Boolean.getBoolean("ldiediet.authorAcceptance")) {
                if(!FoodStageProfiles.SERVER.resolve(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SWEET_BERRIES)).thresholds().equals(java.util.List.of(6,12,18,24)))throw new IllegalStateException("Documented stage profile missing after /reload");
                var example=FoodGroupManager.getInstance().getGroup(new net.minecraft.resources.ResourceLocation("mypack:midnight_berries"));
                if(example==null||!example.isSecret())throw new IllegalStateException("Documented secret group missing after /reload");
                LDieDiet.LOGGER.info("AUTHOR DEDICATED PASS documented profiles/groups loaded through actual /reload");
            }
            int added = DietConfigManager.updateConfig(event.getServer()); DietConfigManager.flushPendingSave();
            var file = FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json");
            if (!com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject().has("overrides"))
                throw new IllegalStateException("Saved configuration is invalid");
            LDieDiet.LOGGER.info("[LDieDiet SERVER ACCEPTANCE] PASS start/reload/CCI/save; {} groups; {} entries added",
                    FoodGroupManager.getInstance().getAllGroups().size(), added);
        } catch (Exception error) { LDieDiet.LOGGER.error("[LDieDiet SERVER ACCEPTANCE] FAILED", error); }
        finally { event.getServer().halt(false); }
    }
}
