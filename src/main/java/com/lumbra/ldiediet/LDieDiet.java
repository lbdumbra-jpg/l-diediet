package com.lumbra.ldiediet;

import com.lumbra.ldiediet.data.ExclusionManager;
import com.lumbra.ldiediet.data.FoodGroupManager;
import com.lumbra.ldiediet.network.ModNetworkHandler;
import com.lumbra.ldiediet.rewards.RewardHandler;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.data.DietConfigManager;
import com.lumbra.ldiediet.data.NutritionScaleManager;
import com.lumbra.ldiediet.math.FormulaManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

@Mod(LDieDiet.MOD_ID)
public class LDieDiet {
    public static final String MOD_ID = "ldiediet";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);


    public LDieDiet() {
        com.lumbra.ldiediet.compat.DietResourceCache.initialize();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(com.lumbra.ldiediet.compat.DietResourceCache::addPack);
       
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LDieDietClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LDieDietServerConfig.SPEC);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new com.lumbra.ldiediet.event.FoodEatingHandler());

        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        MinecraftForge.EVENT_BUS.addListener(this::onDatapackSync);
        
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.lumbra.ldiediet.client.ClientEvents::register);

        MinecraftForge.EVENT_BUS.register(com.lumbra.ldiediet.command.LDieDietCommand.class);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetworkHandler::register);
    }

   
    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(ExclusionManager.getInstance());
        event.addListener(FoodGroupManager.getInstance());
        event.addListener(com.lumbra.ldiediet.data.FoodProfileManager.INSTANCE);
        event.addListener(com.lumbra.ldiediet.data.FoodStageProfiles.SERVER);
        event.addListener(FormulaManager.getInstance());
        event.addListener(NutritionScaleManager.getInstance());
        event.addListener(com.lumbra.ldiediet.data.NutritionRuleManager.INSTANCE);
        event.addListener(com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE);
    }

    
    private void onDatapackSync(OnDatapackSyncEvent event) {
        net.minecraft.server.MinecraftServer server = event.getPlayerList().getServer();
        if (server != null) {
            FoodGroupManager.getInstance().refreshMemberships();
            if (event.getPlayer() == null) {
                DietConfigManager.reloadConfig(server);
                com.lumbra.ldiediet.data.FoodProfileManager.INSTANCE.refresh();
                com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE.refresh(server);
                com.lumbra.ldiediet.data.AuthorDiagnostics.logValidation(server);
            }
            if (event.getPlayer() == null) DietConfigManager.reloadRecipes(server);
            else DietConfigManager.prepareRecipes(server);
            if (event.getPlayer() == null) {
                for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                    IFoodTolerance tolerance = CapabilityHandler.get(player);
                    if (tolerance != null) com.lumbra.ldiediet.api.PackApi.refresh(player);
                }
            }
        }

        CompoundTag data = new CompoundTag();
        data.put("foodGroups", FoodGroupManager.getInstance().serializeToNBT());
        data.put("exclusions", ExclusionManager.getInstance().serializeToNBT());
        data.put("dietConfig", DietConfigManager.serializeToNBT());
        data.put("nutritionScales", NutritionScaleManager.getInstance().serializeToNBT());
        data.put("foodStageProfiles", com.lumbra.ldiediet.data.FoodStageProfiles.SERVER.serialize());
        
        com.lumbra.ldiediet.network.SyncFoodGroupsPacket packet = new com.lumbra.ldiediet.network.SyncFoodGroupsPacket(data);
        
        if (event.getPlayer() != null) {
            ModNetworkHandler.sendToPlayer(packet, event.getPlayer());
        } else {
            for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                ModNetworkHandler.sendToPlayer(packet, player);
            }
        }

    }


    private void onServerStarted(ServerStartedEvent event) {
        FoodGroupManager.getInstance().refreshMemberships();
        DietConfigManager.loadConfig(event.getServer());
        com.lumbra.ldiediet.data.FoodProfileManager.INSTANCE.refresh();
        com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE.refresh(event.getServer());
        com.lumbra.ldiediet.data.AuthorDiagnostics.logValidation(event.getServer());
    }

    private void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) DietConfigManager.flushCciUpdates(event.getServer());
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.getServer().getTickCount() % 100 == 0) {
            DietConfigManager.flushPendingSave();
        }
    }

    private void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        DietConfigManager.flushPendingSave();
    }

}
