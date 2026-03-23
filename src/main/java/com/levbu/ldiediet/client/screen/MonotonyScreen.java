package com.levbu.ldiediet.client.screen;

import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.data.FoodGroupDefinition;
import com.levbu.ldiediet.data.FoodGroupManager;
import com.levbu.ldiediet.rewards.RewardHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

public class MonotonyScreen extends Screen {
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("minecraft", "textures/gui/demo_background.png");
    private static final ResourceLocation DIET_ICONS = new ResourceLocation("diet", "textures/gui/icons.png");
    
    private final int imageWidth = 248;
    private final int imageHeight = 166;
    private int leftPos;
    private int topPos;
    
    private int currentPage = 0;
    private final int itemsPerPage = 3;
    private final int itemSpacing = 40;
    private List<FoodGroupDefinition> allGroups;
    
    private Button prevButton;
    private Button nextButton;
    private Button backButton;

    public MonotonyScreen() {
        super(Component.translatable("ldiediet.gui.monotony.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        
        Collection<FoodGroupDefinition> groups = FoodGroupManager.getInstance().getAllGroups();
        this.allGroups = new ArrayList<>(groups);
        
        this.backButton = addRenderableWidget(Button.builder(Component.literal("×"), b -> this.onClose())
                .bounds(this.leftPos + this.imageWidth - 22, this.topPos + 4, 18, 18)
                .build());

        this.prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (this.currentPage > 0) this.currentPage--;
            updateButtonVisibility();
        }).bounds(this.leftPos + 30, this.topPos + this.imageHeight - 22, 20, 16).build());

        this.nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if ((this.currentPage + 1) * this.itemsPerPage < this.allGroups.size()) this.currentPage++;
            updateButtonVisibility();
        }).bounds(this.leftPos + this.imageWidth - 50, this.topPos + this.imageHeight - 22, 20, 16).build());
        
        updateButtonVisibility();
    }
    
    private void updateButtonVisibility() {
        this.prevButton.visible = this.currentPage > 0;
        this.nextButton.visible = (this.currentPage + 1) * this.itemsPerPage < this.allGroups.size();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        
        // Отрисовка основного фона окна
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        guiGraphics.blit(BACKGROUND_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        
        // Заголовок окна (Центрируем по ширине окна)
        guiGraphics.drawCenteredString(this.font, this.title, this.leftPos + this.imageWidth / 2, this.topPos + 10, 0xFFFFFF);
        
        if (allGroups.size() > itemsPerPage) {
            String pageInfo = (currentPage + 1) + " / " + ((allGroups.size() + itemsPerPage - 1) / itemsPerPage);
            guiGraphics.drawCenteredString(this.font, pageInfo, this.leftPos + this.imageWidth / 2, this.topPos + this.imageHeight - 18, 0xFFFFFF);
        }

        renderGroupList(guiGraphics, mouseX, mouseY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderGroupList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIdx = this.currentPage * this.itemsPerPage;
        int endIdx = Math.min(startIdx + this.itemsPerPage, this.allGroups.size());
        
        IFoodTolerance cap = CapabilityHandler.get(Minecraft.getInstance().player);
        if (cap == null) return;

        int startY = this.topPos + 32; 
        for (int i = startIdx; i < endIdx; i++) {
            FoodGroupDefinition group = this.allGroups.get(i);
            renderGroupEntry(guiGraphics, group, cap, this.leftPos + 9, startY + (i - startIdx) * itemSpacing, mouseX, mouseY);
        }
    }

    private void renderGroupEntry(GuiGraphics guiGraphics, FoodGroupDefinition group, IFoodTolerance cap, int x, int y, int mouseX, int mouseY) {
        int offset = cap.getConsumedHistoryTotal(group.getId().getPath());
        int matchCount = RewardHandler.countMatchedInHistory(cap.getEatingHistory(), group, offset);
        int threshold = group.getThreshold();
        float progress = Math.min(1.0f, (float) matchCount / threshold);
        boolean isCompleted = matchCount >= threshold;
        
        int groupColor = getColorFromHash(group.getTooltipIconColor());
        int color = isCompleted ? groupColor : 0xFFFFFF;
        
        ItemStack iconStack = getGroupIcon(group);
        guiGraphics.renderFakeItem(iconStack, x, y + 2);
        
        List<FormattedCharSequence> nameLines = this.font.split(Component.literal(group.getName()), 66);
        int nameY = y + (nameLines.size() > 1 ? -1 : 3);
        for (int i = 0; i < nameLines.size(); i++) {
            guiGraphics.drawString(this.font, nameLines.get(i), x + 24, nameY + i * 9, color, true);
        }
        
        // Маленькие подписи снизу (Тип: Подряд/Любой | Разная/Любая)
        String flagText = group.isConsecutive() 
                ? Component.translatable("ldiediet.gui.monotony.consecutive").getString() 
                : Component.translatable("ldiediet.gui.monotony.any").getString();
        String uniqueText = group.isRequireUnique() 
                ? Component.translatable("ldiediet.gui.monotony.unique").getString() 
                : Component.translatable("ldiediet.gui.monotony.any_type").getString();
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.6f, 0.6f, 1.0f);
        String label = flagText + " | " + uniqueText;
        int labelY = (int)((y + (nameLines.size() > 1 ? 19 : 18)) / 0.6f);
        guiGraphics.drawString(this.font, label, (int)((x + 22) / 0.6f), labelY, 0xAAAAAA, false);
        guiGraphics.pose().popPose();

        drawDietProgressBar(guiGraphics, x + 96, y + 5, progress, groupColor);
        
        int percentage = (int)(progress * 100);
        String percentText = percentage + "%";
        guiGraphics.drawString(this.font, percentText, x + 202, y + 5, color, true);
    }

    private void drawDietProgressBar(GuiGraphics guiGraphics, int x, int y, float progress, int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, DIET_ICONS);
        
        float r = (color >> 16 & 255) / 255.0f;
        float g = (color >> 8 & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        
        RenderSystem.setShaderColor(r, g, b, 1.0f);
        guiGraphics.blit(DIET_ICONS, x, y, 20, 0, 102, 5, 256, 256);
        
        int fillWidth = (int) (101 * progress);
        if (fillWidth > 0) {
            guiGraphics.blit(DIET_ICONS, x, y, 20, 5, fillWidth, 5, 256, 256);
        }
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }



    private ItemStack getGroupIcon(FoodGroupDefinition group) {
        if (!group.getAdvancement().isEmpty()) {
            try {
                ResourceLocation advId = new ResourceLocation(group.getAdvancement());
                net.minecraft.advancements.Advancement adv = Minecraft.getInstance().getConnection().getAdvancements().getAdvancements().get(advId);
                if (adv != null && adv.getDisplay() != null) {
                    return adv.getDisplay().getIcon();
                }
            } catch (Exception ignored) {}
        }
        
        if (!group.getItems().isEmpty()) {
            String firstItem = group.getItems().get(0);
            if (firstItem.startsWith("#")) {
                try {
                ResourceLocation tagRL = new ResourceLocation(firstItem.substring(1));
                var tagKey = net.minecraft.tags.TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
                var holderSet = BuiltInRegistries.ITEM.getTag(tagKey);
                if (holderSet.isPresent() && holderSet.get().size() > 0) {
                    return new ItemStack(holderSet.get().get(0).value());
                }
                } catch (Exception ignored) {}
            } else {
                try {
                    ResourceLocation rl = new ResourceLocation(firstItem);
                    Item item = BuiltInRegistries.ITEM.get(rl);
                    if (item != Items.AIR) return new ItemStack(item);
                } catch (Exception ignored) {}
            }
        }
        return new ItemStack(Items.BARRIER);
    }

    private int getColorFromHash(String colorName) {
        if (colorName.startsWith("#")) {
            try {
                return Integer.parseInt(colorName.substring(1), 16);
            } catch (NumberFormatException e) {
                return 0x55FFFF;
            }
        }
        return switch (colorName.toLowerCase()) {
            case "red" -> 0xFF5555;
            case "dark_red" -> 0xAA0000;
            case "green" -> 0x55FF55;
            case "dark_green" -> 0x00AA00;
            case "blue" -> 0x5555FF;
            case "dark_blue" -> 0x0000AA;
            case "yellow" -> 0xFFFF55;
            case "gold" -> 0xFFAA00;
            case "aqua" -> 0x55FFFF;
            case "dark_aqua" -> 0x00AAAA;
            case "purple" -> 0xFF55FF;
            case "dark_purple" -> 0xAA00AA;
            case "white" -> 0xFFFFFF;
            case "gray" -> 0xAAAAAA;
            case "dark_gray" -> 0x555555;
            case "black" -> 0x000000;
            default -> 0x55FFFF;
        };
    }
}
