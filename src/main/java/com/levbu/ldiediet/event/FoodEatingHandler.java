package com.levbu.ldiediet.event;

import com.levbu.ldiediet.LDieDiet;
import com.levbu.ldiediet.ToleranceStage;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.FoodToleranceEntry;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.compat.DietIntegration;
import com.levbu.ldiediet.rewards.RewardHandler;
import com.levbu.ldiediet.LDieDietServerConfig;
import com.levbu.ldiediet.util.EatingContext;
import com.levbu.ldiediet.util.ExclusionMatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;

/**
 * Обработчик событий поедания еды.
 * Применяет штрафы за надоедание и синхронизирует данные с клиентом.
 */
public class FoodEatingHandler {

    /**
     * Вызывается когда игрок заканчивает есть.
     * Низкий приоритет — выполняется после других модов (включая Diet).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        ItemStack stack = event.getItem();
        Item item = stack.getItem();

        FoodProperties foodProps = item.getFoodProperties();
        boolean isDrinkOrFood = (foodProps != null)
                || stack.getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK
                || stack.getUseAnimation() == net.minecraft.world.item.UseAnim.EAT;

        if (!isDrinkOrFood) return;

        IFoodTolerance toleranceCap = CapabilityHandler.get(player);
        if (toleranceCap == null) return;

        if (ExclusionMatcher.isExcluded(item)) return;

        // --- Стадия ДО еды ---
        FoodToleranceEntry entryBefore = toleranceCap.getEntryOrNull(item);
        ToleranceStage stageBefore = ToleranceStage.fromEntry(entryBefore, item, player);

        // Сохраняем количество diet-групп при первом поедании
        if (entryBefore == null || entryBefore.getConsecutiveEaten() == 0) {
            int dietGroups = DietIntegration.countDietGroups(player, stack);
            toleranceCap.getEntry(item).setDietGroupCount(dietGroups);
        }

        // --- Записываем факт еды ---
        long gameTime = player.level().getGameTime();
        toleranceCap.recordEaten(item, gameTime);
        toleranceCap.addHistory(item);

        // --- Стадия ПОСЛЕ еды ---
        FoodToleranceEntry entryAfter = toleranceCap.getEntry(item);
        ToleranceStage stageAfter = ToleranceStage.fromEntry(entryAfter, item, player);

        // --- Уведомление о смене стадии ---
        if (stageAfter.getLevel() > stageBefore.getLevel()) {
            notifyStageChange(player, item, stageAfter);
            if (player instanceof ServerPlayer sp) {
                grantStageAdvancements(sp, stageAfter);
            }
        }

        // --- Удаление баффов на высоких стадиях ---
        if (foodProps != null && !stageAfter.allowsBuffs()) {
            removeAppliedBuffs(player, foodProps);
        }

        // --- Проверка наград ---
        RewardHandler.checkAndApplyRewards(player, toleranceCap);

        // --- Синхронизация с клиентом ---
        CapabilityHandler.syncToClient(player);

        // --- Коррекция Diet-значений при надоедании ---
        if (LDieDiet.isDietLoaded() && LDieDietServerConfig.ENABLE_DIET_PENALTY.get()) {
            Map<String, Float> valuesBefore = EatingContext.getDietValuesBefore();
            DietIntegration.applyDietCorrection(player, stack, item, valuesBefore);
        }

        EatingContext.clear();
    }

    /**
     * Удалить положительные эффекты, применённые едой, на высоких стадиях надоедания.
     */
    private void removeAppliedBuffs(Player player, FoodProperties foodProps) {
        List<com.mojang.datafixers.util.Pair<MobEffectInstance, Float>> effects = foodProps.getEffects();
        if (effects.isEmpty()) return;

        for (var pair : effects) {
            MobEffectInstance effectInstance = pair.getFirst();
            MobEffect effect = effectInstance.getEffect();

            if (effect.getCategory() == MobEffectCategory.BENEFICIAL) {
                MobEffectInstance activeEffect = player.getEffect(effect);
                if (activeEffect != null) {
                    int foodDuration = effectInstance.getDuration();
                    int activeDuration = activeEffect.getDuration();
                    if (Math.abs(activeDuration - foodDuration) < 20) {
                        player.removeEffect(effect);
                    }
                }
            }
        }
    }

    /**
     * Уведомить игрока о смене стадии надоедания.
     */
    private void notifyStageChange(Player player, Item item, ToleranceStage newStage) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Component itemName = item.getDescription();
        Component message = switch (newStage) {
            case STAGE_0 -> null;
            case STAGE_1 -> Component.translatable("ldiediet.actionbar.stage1", itemName)
                    .withStyle(ChatFormatting.YELLOW);
            case STAGE_2 -> Component.translatable("ldiediet.actionbar.stage2", itemName)
                    .withStyle(ChatFormatting.GOLD);
            case STAGE_3 -> Component.translatable("ldiediet.actionbar.stage3", itemName)
                    .withStyle(ChatFormatting.RED);
            case STAGE_4 -> Component.translatable("ldiediet.actionbar.stage4", itemName)
                    .withStyle(ChatFormatting.DARK_RED);
        };

        if (message != null) {
            serverPlayer.displayClientMessage(message, true);
        }
    }

    /**
     * Получить стадию надоедания текущего игрока для предмета.
     */
    public static ToleranceStage getStage(Player player, Item item) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null) return ToleranceStage.STAGE_0;
        return ToleranceStage.fromEntry(cap.getEntryOrNull(item), item, player);
    }

    private void grantStageAdvancements(ServerPlayer player, ToleranceStage stage) {
        switch (stage) {
            case STAGE_1 -> grantAdvancement(player, "offseason:offseason_tab/tolerance_1");
            case STAGE_3 -> grantAdvancement(player, "offseason:offseason_tab/tolerance_3");
            case STAGE_4 -> grantAdvancement(player, "offseason:offseason_tab/tolerance_max");
            default -> {}
        }
    }

    private void grantAdvancement(ServerPlayer player, String advancementId) {
        if (player.server == null) return;
        net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation(advancementId);
        net.minecraft.advancements.Advancement adv = player.server.getAdvancements().getAdvancement(id);
        if (adv != null) {
            net.minecraft.advancements.AdvancementProgress prog = player.getAdvancements().getOrStartProgress(adv);
            if (!prog.isDone()) {
                for (String criterion : prog.getRemainingCriteria()) {
                    player.getAdvancements().award(adv, criterion);
                }
            }
        }
    }
}
