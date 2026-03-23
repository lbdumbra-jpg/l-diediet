package com.levbu.ldiediet.mixin;

import com.levbu.ldiediet.client.screen.MonotonyScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(com.illusivesoulworks.diet.client.screen.DietScreen.class)
public abstract class MixinDietScreen extends Screen {
    private static final ResourceLocation MONOTONY_ICON = new ResourceLocation("ldiediet", "textures/gui/sup.png");
    private static final ResourceLocation MONOTONY_ICON_HOVER = new ResourceLocation("ldiediet", "textures/gui/sup_wight.png");

    protected MixinDietScreen(Component pTitle) {
        super(pTitle);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ldiediet$onInit(CallbackInfo ci) {
        int leftPos = (this.width - 248) / 2;
        int topPos = (this.height - 166) / 2;

        int btnSize = 20;
        int btnX = leftPos + 248 - btnSize - 6;
        int btnY = topPos + 166 - btnSize - 6;

        this.addRenderableWidget(new ImageButton(btnX, btnY, btnSize, btnSize, 0, 0, 0, MONOTONY_ICON, btnSize, btnSize, b -> {
            Minecraft.getInstance().setScreen(new MonotonyScreen());
        }) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                boolean hovered = this.isHoveredOrFocused();

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                int bx = this.getX();
                int by = this.getY();

                if (hovered) {
                    // Текстура с окантовкой 18x18, центрируем в области 20x20
                    int drawX = bx + (btnSize - 18) / 2;
                    int drawY = by + (btnSize - 18) / 2;
                    guiGraphics.blit(MONOTONY_ICON_HOVER, drawX, drawY, 0, 0, 18, 18, 18, 18);
                } else {
                    // Обычная текстура 16x16, центрируем в области 20x20
                    int drawX = bx + (btnSize - 16) / 2;
                    int drawY = by + (btnSize - 16) / 2;
                    guiGraphics.blit(MONOTONY_ICON, drawX, drawY, 0, 0, 16, 16, 16, 16);
                }

                RenderSystem.disableBlend();
            }
        });
    }
}
