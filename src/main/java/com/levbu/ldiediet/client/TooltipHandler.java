package com.levbu.ldiediet.client;

import com.levbu.ldiediet.LDieDietClientConfig;
import com.levbu.ldiediet.LDieDietServerConfig;
import com.levbu.ldiediet.ToleranceStage;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.FoodToleranceEntry;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.data.FoodGroupDefinition;
import com.levbu.ldiediet.data.FoodGroupManager;
import com.levbu.ldiediet.util.ExclusionMatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;


@OnlyIn(Dist.CLIENT)
public class TooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();

        if (item.getFoodProperties() == null)
            return;

        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;

        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null)
            return;

        if (ExclusionMatcher.isExcluded(item))
            return;

        FoodToleranceEntry entry = cap.getEntryOrNull(item);
        int eaten = entry != null ? entry.getConsecutiveEaten() : 0;

        if (eaten == 0 && !LDieDietClientConfig.SHOW_DETAILED_TOOLTIP.get())
            return;

      
        int baseRecoveryTicks = LDieDietServerConfig.RECOVERY_TIME_TICKS.get();
        double varietyMultiplier = cap.getVarietyRecoveryMultiplier();
        double dietMultiplier = entry != null ? entry.getRecoveryTimeMultiplier() : 1.0;
        int fullRecoveryTicks = (int) (baseRecoveryTicks * dietMultiplier / varietyMultiplier);
        long currentTime = player.level() != null ? player.level().getGameTime() : 0;

        
        ToleranceStage stage = entry != null
                ? ToleranceStage.fromEntry(entry, item, player)
                : ToleranceStage.STAGE_0;

        List<Component> tooltip = event.getToolTip();

        
        tooltip.add(getStageText(stage));

      
        addGroupNames(tooltip, item, cap);

        
        if (LDieDietClientConfig.SHOW_DETAILED_TOOLTIP.get() && entry != null) {
            
            int[] thresholds = entry.getAdjustedThresholds();
            int nextThreshold = entry.getNextThreshold(item, thresholds);

            tooltip.add(Component.translatable("ldiediet.tooltip.progress", eaten, nextThreshold)
                    .withStyle(ChatFormatting.GRAY));

            
            if (eaten > 0 && player.level() != null && stage.getLevel() > 0) {
                String recoveryText = calculateRecoveryText(player, entry, fullRecoveryTicks);
                tooltip.add(Component.literal(recoveryText).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    
    private void addGroupNames(List<Component> tooltip, Item item, IFoodTolerance cap) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String itemIdStr = itemId.toString();

        List<FoodGroupDefinition> groups = FoodGroupManager.getInstance().getGroupsForItem(itemIdStr);
        if (groups.isEmpty()) return;

        for (FoodGroupDefinition group : groups) {
            String icon = group.getTooltipIcon() != null ? group.getTooltipIcon() : "● ";
            String colorStr = group.getTooltipIconColor() != null ? group.getTooltipIconColor() : "aqua";
            
            net.minecraft.network.chat.Style iconStyle = net.minecraft.network.chat.Style.EMPTY;
            if (colorStr.startsWith("#")) {
                net.minecraft.network.chat.TextColor textColor = net.minecraft.network.chat.TextColor.parseColor(colorStr);
                if (textColor != null) {
                    iconStyle = iconStyle.withColor(textColor);
                }
            } else {
                ChatFormatting format = ChatFormatting.getByName(colorStr.toUpperCase(java.util.Locale.ROOT));
                if (format != null) {
                    iconStyle = iconStyle.withColor(format);
                } else {
                    iconStyle = iconStyle.withColor(ChatFormatting.AQUA);
                }
            }

            MutableComponent groupLine = Component.literal(icon)
                    .withStyle(iconStyle)
                    .append(Component.literal(group.getName())
                            .withStyle(ChatFormatting.WHITE));

           
            boolean isGroupEffectActive = isGroupRewardActive(cap, group);
            if (isGroupEffectActive && !group.getActiveEffectText().isEmpty()) {
                groupLine.append(Component.literal(" " + group.getActiveEffectText())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            tooltip.add(groupLine);
        }
    }

    
    private boolean isGroupRewardActive(IFoodTolerance cap, FoodGroupDefinition group) {
        String groupId = group.getId().getPath();
        return cap.hasActiveEffectsForGroup(groupId) || cap.hasPermanentReward(groupId);
    }

    private String calculateRecoveryText(Player player, FoodToleranceEntry entry, int fullRecoveryTicks) {
        long currentTime = player.level().getGameTime();

        long ticksRemaining = entry.getTicksUntilNextStageRecovery(currentTime, fullRecoveryTicks);
        int minutesRemaining = (int) (ticksRemaining / 1200);

        if (minutesRemaining > 60) {
            return String.format(
                    Component.translatable("ldiediet.tooltip.recovery_hours").getString(),
                    minutesRemaining / 60, minutesRemaining % 60);
        } else if (minutesRemaining > 0) {
            return String.format(
                    Component.translatable("ldiediet.tooltip.recovery_mins").getString(),
                    minutesRemaining);
        } else {
            return Component.translatable("ldiediet.tooltip.recovery_soon").getString();
        }
    }

    private Component getStageText(ToleranceStage stage) {
        return switch (stage) {
            case STAGE_0 -> Component.translatable("ldiediet.tooltip.stage0").withStyle(ChatFormatting.GREEN);
            case STAGE_1 -> Component.translatable("ldiediet.tooltip.stage1").withStyle(ChatFormatting.YELLOW);
            case STAGE_2 -> Component.translatable("ldiediet.tooltip.stage2").withStyle(ChatFormatting.GOLD);
            case STAGE_3 -> Component.translatable("ldiediet.tooltip.stage3").withStyle(ChatFormatting.RED);
            case STAGE_4 -> Component.translatable("ldiediet.tooltip.stage4").withStyle(ChatFormatting.DARK_RED);
        };
    }
}
