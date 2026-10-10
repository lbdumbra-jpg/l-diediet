package com.lumbra.ldiediet.capability;

import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.network.ModNetworkHandler;
import com.lumbra.ldiediet.network.SyncTolerancePacket;
import com.lumbra.ldiediet.network.SyncFoodGroupsPacket;
import com.lumbra.ldiediet.rewards.RewardHandler;
import com.lumbra.ldiediet.data.DietConfigManager;
import com.lumbra.ldiediet.data.NutritionScaleManager;
import com.lumbra.ldiediet.LDieDietServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
            if (event.getObject() instanceof LivingEntity) {
                FoodToleranceProvider provider = new FoodToleranceProvider();
                event.addCapability(FoodToleranceProvider.IDENTIFIER, provider);
                event.addListener(provider::invalidate);
            }
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
             
            var saved = event.getOriginal().saveWithoutId(new net.minecraft.nbt.CompoundTag())
                    .getCompound("ForgeCaps").getCompound(FoodToleranceProvider.IDENTIFIER.toString());
            IFoodTolerance next = get(event.getEntity());
            if (next == null) return;
            next.deserializeNBT(saved);
            if (event.isWasDeath()) {
                double retained = 1 - LDieDietServerConfig.NUTRITION_DEATH_LOSS_PERCENT.get() / 100.0;
                if (retained < 1) for (var nutrient : java.util.Map.copyOf(next.getAllNutrients()).entrySet()) {
                    com.lumbra.ldiediet.api.DietEvents.nutrient(event.getEntity(), next, nutrient.getKey(), nutrient.getValue() * retained, "death");
                }
                if (LDieDietServerConfig.RESET_HISTORY_ON_DEATH.get()) next.clearHistory();
                RewardHandler.onDeath(next, LDieDietServerConfig.RESET_GROUPS_ON_DEATH.get());
                next.getMobEffectSources().deserializeNBT(new net.minecraft.nbt.CompoundTag());
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) ModNetworkHandler.resetPlayer(player);
            IFoodTolerance imported = get(event.getEntity());
            if (imported != null) {
                com.lumbra.ldiediet.compat.DietLegacyState.migrate(event.getEntity(), imported);
                com.lumbra.ldiediet.rewards.NutritionAttributeHandler.refresh(event.getEntity(), imported);
                com.lumbra.ldiediet.rewards.NutritionRuleHandler.refresh(event.getEntity(), imported);
                com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(event.getEntity(), imported);
            }
            syncToClient(event.getEntity());
            syncResourcesToClient(event.getEntity());
        }

      
        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) ModNetworkHandler.resetPlayer(player);
            IFoodTolerance tolerance = get(event.getEntity());
            if (tolerance != null) {
                com.lumbra.ldiediet.rewards.NutritionAttributeHandler.refresh(event.getEntity(), tolerance);
                com.lumbra.ldiediet.rewards.NutritionRuleHandler.refresh(event.getEntity(), tolerance);
                com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(event.getEntity(), tolerance);
            }
            syncToClient(event.getEntity());
            syncResourcesToClient(event.getEntity());
        }

       
        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) ModNetworkHandler.resetPlayer(player);
            syncToClient(event.getEntity());
            syncResourcesToClient(event.getEntity());
        }

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
                return;

            long gameTime = event.player.level().getGameTime();
            event.player.getCapability(FOOD_TOLERANCE).ifPresent(cap -> cap.tick(gameTime));
            IFoodTolerance current = get(event.player);
            if (current != null) current.getMobEffectSources().advance();
            
            boolean nutritionChanged = com.lumbra.ldiediet.rewards.NutritionDecayHandler.tick(event.player,current);
            RewardHandler.tickActiveEffects(event.player);
            if (nutritionChanged || event.player.tickCount % 20 == 0) {
                IFoodTolerance tolerance = get(event.player);
                if (tolerance != null) {
                    if (event.player.tickCount % 20 == 0) com.lumbra.ldiediet.rewards.NutritionDecayHandler.tickSecond(event.player, tolerance);
                    com.lumbra.ldiediet.rewards.NutritionAttributeHandler.refresh(event.player, tolerance);
                    com.lumbra.ldiediet.rewards.NutritionRuleHandler.refresh(event.player, tolerance);
                    com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(event.player, tolerance);
                }
            }
            if (current != null) current.getMobEffectSources().reconcile(event.player);
            if (nutritionChanged || event.player.tickCount % 20 == 0) syncToClient(event.player);
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            com.lumbra.ldiediet.api.DietEvents.forgetPlayer(event.getEntity());
            if (event.getEntity() instanceof ServerPlayer player) ModNetworkHandler.resetPlayer(player);
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onEffectRemoved(net.minecraftforge.event.entity.living.MobEffectEvent.Remove event) {
            if (event.getEntity() instanceof Player player) {
                com.lumbra.ldiediet.rewards.MobEffectSources.removedExternal(player, event.getEffect());
            }
        }
    }

  
    public static void syncToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(FOOD_TOLERANCE).ifPresent(cap -> {
                 
                cap.limitMonotony(com.lumbra.ldiediet.math.FormulaManager.getMonotonyCap());
                var journal = com.lumbra.ldiediet.data.JournalSnapshot.capture(serverPlayer, cap);
                var snapshot = cap.serializeNBT();
                snapshot.put(com.lumbra.ldiediet.data.JournalSnapshot.KEY,journal);
                snapshot.put(com.lumbra.ldiediet.data.NutritionAttributeSummary.KEY,
                        com.lumbra.ldiediet.data.NutritionAttributeSummary.capture(serverPlayer));
                ModNetworkHandler.sendToPlayer(
                        new SyncTolerancePacket(snapshot),
                        serverPlayer);
            });
        }
    }

  
    public static void syncResourcesToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
            data.put("foodGroups", com.lumbra.ldiediet.data.FoodGroupManager.getInstance().serializeToNBT());
            data.put("exclusions", com.lumbra.ldiediet.data.ExclusionManager.getInstance().serializeToNBT());
            data.put("dietConfig", DietConfigManager.serializeToNBT());
            data.putString("nutrientFormula",com.lumbra.ldiediet.math.FormulaManager.nutrientSource());
            data.put("nutritionScales", NutritionScaleManager.getInstance().serializeToNBT());
            data.put("foodStageProfiles", com.lumbra.ldiediet.data.FoodStageProfiles.SERVER.serialize());
            ModNetworkHandler.sendToPlayer(new SyncFoodGroupsPacket(data), serverPlayer);
        }
    }

    
    public static IFoodTolerance get(Player player) {
        return player.getCapability(FOOD_TOLERANCE).orElse(null);
    }

    public static IFoodTolerance get(LivingEntity entity) {
        return entity.getCapability(FOOD_TOLERANCE).orElse(null);
    }
}
