package com.lumbra.ldiediet.event;

import com.lumbra.ldiediet.ToleranceStage;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.consumption.ConsumptionSnapshot;
import com.lumbra.ldiediet.consumption.FoodConsumption;
import com.lumbra.ldiediet.data.FoodKey;
import com.lumbra.ldiediet.data.DietConfigManager;
import com.lumbra.ldiediet.math.FormulaManager;
import com.lumbra.ldiediet.rewards.RewardHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.HashMap;
import java.util.Map;

public class FoodEatingHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var cap = CapabilityHandler.get(player);
        if (cap == null) return;
        cap.setUseSession(null);
        ((com.lumbra.ldiediet.consumption.UseDurationOverride)(Object)event.getItem()).ldiediet$setDuration(0);
        if (event.isCanceled() || event.getDuration() <= 0) return;
        var portion = ConsumptionSnapshot.captureIfConsumable(player, event.getItem());
        if (portion == null) return;
        cap.setUseSession(portion);
        if (!portion.excluded()) event.setDuration((int) Math.min(Integer.MAX_VALUE,
                Math.max(1, Math.round((double) event.getDuration() * portion.chewingMultiplier()))));
        ((com.lumbra.ldiediet.consumption.UseDurationOverride)(Object)event.getItem()).ldiediet$setDuration(event.getDuration());
        com.lumbra.ldiediet.consumption.ChewingTiming.remember(player,event.getItem());
    }

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public void finalizeUseStart(LivingEntityUseItemEvent.Start event) {
        if(event.isCanceled()||event.getDuration()<=0) FoodConsumption.stop(event.getEntity());
        else {var cap=CapabilityHandler.get(event.getEntity());if(cap!=null&&cap.getUseSession()!=null)
            ((com.lumbra.ldiediet.consumption.UseDurationOverride)(Object)event.getItem()).ldiediet$setDuration(event.getDuration());}
    }

    @SubscribeEvent
    public void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        FoodConsumption.stop(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && !event.player.isUsingItem()) FoodConsumption.stop(event.player);
    }

    public static void complete(Player player, ConsumptionSnapshot portion) {
        complete(player, portion, 0);
    }

    public static void complete(Player player, ConsumptionSnapshot portion, int hungerRestored) {
        complete(player,portion,hungerRestored,false);
    }
    public static void complete(Player player, ConsumptionSnapshot portion, int hungerRestored,boolean external) {
        if (player.level().isClientSide || portion.excluded()) return;
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null) return;
        cap.limitMonotony(FormulaManager.getMonotonyCap());
        double increase = mealIncrease(portion,portion.monotonyBefore());
        com.lumbra.ldiediet.api.DietEvents.monotony(player,cap,portion.key(),portion.stack(),
                Math.min(FormulaManager.getMonotonyCap(),(double)cap.getMonotony(portion.key())+increase),"eat");
        cap.recordEaten(portion.key(), player.level().getGameTime(), FormulaManager.resetsRecoveryOnEat());
        cap.rememberFood(portion.key(),portion.stack());
        cap.rememberExternalNutrition(portion.key(),external?new double[]{portion.hunger(),portion.saturation()}:null);
        cap.addHistory(portion.key());
        if (FormulaManager.qualifiesForRecovery(portion.foodValue(), hungerRestored)
                && "active".equalsIgnoreCase(FormulaManager.getRecoveryMode())) {
            applyActiveRecovery(player, cap, portion.key(), portion.cci());
        }
        applyNutrients(player,cap, portion);
        com.lumbra.ldiediet.rewards.NutritionAttributeHandler.refresh(player, cap);
        com.lumbra.ldiediet.rewards.NutritionRuleHandler.refresh(player,cap);
        var stageAfter = ToleranceStage.forStack(portion.stack(), cap.getMonotony(portion.key()), false);
        if (stageAfter.getLevel() > portion.stage().getLevel()) {
            notifyStageChange(player, portion.key().item(), stageAfter);
            if (player instanceof ServerPlayer serverPlayer) grantStageAdvancements(serverPlayer, stageAfter);
        }
        RewardHandler.checkAndApplyRewards(player, cap);
        CapabilityHandler.syncToClient(player);
    }

    public static double mealIncrease(ConsumptionSnapshot portion,float current) {
        if (portion.foodValue() <= 0) return 0;
        var variables = new HashMap<String, Double>();
        variables.put("base_increase", FormulaManager.getBaseIncrease() * portion.baseMonotony() / 10.0);
         
        variables.put("hunger", portion.qualityOverride() == null ? portion.hunger() : portion.qualityOverride());
        variables.put("saturation", portion.qualityOverride() == null ? portion.saturation() : 0);
        variables.put("k_norm", FormulaManager.getKNorm());
        variables.put("cci", portion.cci());
        variables.put("M", (double) current);
        variables.put("monotony", (double) current);
        double increase = Math.max(0, FormulaManager.evaluate("monotony_increase", variables));
        if(increase>0 && portion.foodValue()>0) increase=Math.max(increase,com.lumbra.ldiediet.LDieDietServerConfig.MIN_MONOTONY_PER_MEAL.get());
        return increase;
    }

    private static void applyNutrients(Player player,IFoodTolerance cap, ConsumptionSnapshot portion) {
        if (portion.foodValue() <= 0 || portion.nutrientMultiplier() <= 0) return;
        for (var nutrient : portion.nutrients().entrySet()) {
            double multiplier = 1;
            for (var effect : cap.getActiveEffects()) {
                if (effect.isCurrentlyActive() && effect.isNutrientMultiplier()
                        && ("all".equals(effect.getEffectId()) || "*".equals(effect.getEffectId())
                        || nutrient.getKey().equals(effect.getEffectId()))) multiplier *= Math.max(0, 1 + effect.getValue());
            }
            var variables = Map.of("base_n", portion.foodValue() * nutrient.getValue() * multiplier,
                    "M", (double) portion.monotonyBefore(), "monotony", (double) portion.monotonyBefore(),
                    "stage_multiplier", portion.nutrientMultiplier());
            double amount = FormulaManager.evaluate("nutrient_add", variables);
            if (Double.isFinite(amount) && amount != 0 && Math.abs(amount) <= Float.MAX_VALUE) com.lumbra.ldiediet.api.DietEvents.nutrient(player,cap,nutrient.getKey(),cap.getNutrientValue(nutrient.getKey())+amount,"eat");
        }
    }

    private static void applyActiveRecovery(Player player, IFoodTolerance cap, FoodKey eaten, double cci) {
        for (var entry : cap.getAllFoodMonotony().entrySet()) {
            if (entry.getKey().equals(eaten) || entry.getValue() <= 0) continue;
            int nextMeal = (int) Math.min(Integer.MAX_VALUE, (long) cap.getRecoveryMeals(entry.getKey()) + 1);
            var recovered = FormulaManager.recover(entry.getValue(), cci,
                    DietConfigManager.getOrCalculateCCI(entry.getKey(), player.getServer()), nextMeal);
            if (recovered.isPresent()) {
                var pre=new com.lumbra.ldiediet.api.DietEvents.RecoveryPre(player,entry.getKey(),eaten,entry.getValue(),recovered.getAsDouble(),nextMeal);
                if(com.lumbra.ldiediet.api.DietEvents.post(pre))continue;
                var sample=cap.foodSample(entry.getKey());if(sample.isEmpty())sample=DietConfigManager.representative(entry.getKey());
                var result=com.lumbra.ldiediet.api.DietEvents.monotony(player,cap,entry.getKey(),sample,pre.getValue(),"recovery");
                if(result.accepted()) {
                    cap.setRecoveryMeals(entry.getKey(), nextMeal);
                    com.lumbra.ldiediet.api.DietEvents.post(new com.lumbra.ldiediet.api.DietEvents.RecoveryPost(player,entry.getKey(),eaten,result.before(),result.after(),cap.getRecoveryMeals(entry.getKey())));
                }
            }
        }
    }

    private static void notifyStageChange(Player player, Item item, ToleranceStage stage) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component message = switch (stage) {
            case STAGE_0 -> null;
            case STAGE_1 -> Component.translatable("ldiediet.actionbar.stage1", item.getDescription()).withStyle(ChatFormatting.YELLOW);
            case STAGE_2 -> Component.translatable("ldiediet.actionbar.stage2", item.getDescription()).withStyle(ChatFormatting.GOLD);
            case STAGE_3 -> Component.translatable("ldiediet.actionbar.stage3", item.getDescription()).withStyle(ChatFormatting.RED);
            case STAGE_4 -> Component.translatable("ldiediet.actionbar.stage4", item.getDescription()).withStyle(ChatFormatting.DARK_RED);
        };
        if (message != null) com.lumbra.ldiediet.network.ModNetworkHandler.sendToPlayer(
                new com.lumbra.ldiediet.network.StageMessagePacket(message), serverPlayer);
    }
    public static ToleranceStage getStage(Player player, Item item) { return ToleranceStage.getStage(player, item); }
    private static void grantStageAdvancements(ServerPlayer player, ToleranceStage stage) {
        String id = switch (stage) {
            case STAGE_1 -> "offseason:offseason_tab/tolerance_1";
            case STAGE_3 -> "offseason:offseason_tab/tolerance_3";
            case STAGE_4 -> "offseason:offseason_tab/tolerance_max";
            default -> null;
        };
        if (id == null || player.server == null) return;
        var advancement = player.server.getAdvancements().getAdvancement(new ResourceLocation(id));
        if (advancement == null) return;
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        if (!progress.isDone()) for (String criterion : progress.getRemainingCriteria()) player.getAdvancements().award(advancement, criterion);
    }
}
