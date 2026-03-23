package com.levbu.ldiediet.capability;

import com.levbu.ldiediet.LDieDiet;
import com.levbu.ldiediet.network.ModNetworkHandler;
import com.levbu.ldiediet.network.SyncTolerancePacket;
import com.levbu.ldiediet.network.SyncFoodGroupsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


public class CapabilityHandler {

    public static final Capability<IFoodTolerance> FOOD_TOLERANCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    
    @Mod.EventBusSubscriber(modid = LDieDiet.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(IFoodTolerance.class);
        }
    }

   
    @Mod.EventBusSubscriber(modid = LDieDiet.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusEvents {

        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(FoodToleranceProvider.IDENTIFIER, new FoodToleranceProvider());
            }
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
            event.getOriginal().reviveCaps();

            event.getOriginal().getCapability(FOOD_TOLERANCE).ifPresent(oldCap -> {
                event.getEntity().getCapability(FOOD_TOLERANCE).ifPresent(newCap -> {

                    if (!event.isWasDeath()) {
                        newCap.copyFrom(oldCap);
                    } else {
                       
                    }
                });
            });
        }

       
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            syncToClient(event.getEntity());
            syncResourcesToClient(event.getEntity());
        }

      
        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            syncToClient(event.getEntity());
            syncResourcesToClient(event.getEntity());
        }

       
        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            syncToClient(event.getEntity());
            syncResourcesToClient(event.getEntity());
        }

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
                return;

            long gameTime = event.player.level().getGameTime();
            event.player.getCapability(FOOD_TOLERANCE).ifPresent(cap -> cap.tick(gameTime));
        }
    }

  
    public static void syncToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(FOOD_TOLERANCE).ifPresent(cap -> {
                ModNetworkHandler.sendToPlayer(
                        new SyncTolerancePacket(cap.serializeNBT()),
                        serverPlayer);
            });
        }
    }

  
    public static void syncResourcesToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
            data.put("foodGroups", com.levbu.ldiediet.data.FoodGroupManager.getInstance().serializeToNBT());
            data.put("exclusions", com.levbu.ldiediet.data.ExclusionManager.getInstance().serializeToNBT());
            ModNetworkHandler.sendToPlayer(new SyncFoodGroupsPacket(data), serverPlayer);
           
        }
    }

    
    public static IFoodTolerance get(Player player) {
        return player.getCapability(FOOD_TOLERANCE).orElse(null);
    }
}
