package com.levbu.ldiediet;

import com.levbu.ldiediet.data.ExclusionManager;
import com.levbu.ldiediet.data.FoodGroupManager;
import com.levbu.ldiediet.network.ModNetworkHandler;
import com.levbu.ldiediet.rewards.RewardHandler;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.IFoodTolerance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;



@Mod(LDieDiet.MOD_ID)
public class LDieDiet {
    public static final String MOD_ID = "ldiediet";


    public LDieDiet() {
       
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LDieDietClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LDieDietServerConfig.SPEC);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new com.levbu.ldiediet.event.FoodEatingHandler());

        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        MinecraftForge.EVENT_BUS.addListener(this::onDatapackSync);

        MinecraftForge.EVENT_BUS.addListener(this::onPlayerTick);

        MinecraftForge.EVENT_BUS.addListener(this::onPlayerDeath);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.levbu.ldiediet.client.ClientEvents::register);

        MinecraftForge.EVENT_BUS.register(com.levbu.ldiediet.command.LDieDietCommand.class);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetworkHandler::register);
    }

   
    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(ExclusionManager.getInstance());
        event.addListener(FoodGroupManager.getInstance());
    }

    
    private void onDatapackSync(net.minecraftforge.event.OnDatapackSyncEvent event) {
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.put("foodGroups", FoodGroupManager.getInstance().serializeToNBT());
        data.put("exclusions", ExclusionManager.getInstance().serializeToNBT());
        
        com.levbu.ldiediet.network.SyncFoodGroupsPacket packet = new com.levbu.ldiediet.network.SyncFoodGroupsPacket(data);
        
        if (event.getPlayer() != null) {
            ModNetworkHandler.sendToPlayer(packet, event.getPlayer());
        } else {
            for (net.minecraft.server.level.ServerPlayer player : event.getPlayerList().getPlayers()) {
                ModNetworkHandler.sendToPlayer(packet, player);
            }
        }

    }

   
    private void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        RewardHandler.tickActiveEffects(event.player);
    }

   
    private void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) return;

        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap != null) {
           
            cap.clearDeathEffects();
            
            cap.clearPermanentRewards();
           
            cap.clearHistory();

        }
    }

    public static boolean isDietLoaded() {
        return ModList.get().isLoaded("diet");
    }
}
