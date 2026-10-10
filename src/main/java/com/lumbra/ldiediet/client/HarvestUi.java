package com.lumbra.ldiediet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import java.util.function.BooleanSupplier;

 
public final class HarvestUi {
    public static final ResourceLocation FONT = Style.DEFAULT_FONT;
    public static final int INK = 0x603B2E, MUTED = 0x89503B, GOOD = 0x42684B, BAD = 0x934B48;
    private HarvestUi() {}
    public static ResourceLocation texture(String name) {
        return new ResourceLocation("ldiediet", "textures/gui/harvest/" + name + ".png");
    }
    public static MutableComponent text(Component value) { return value.copy().withStyle(s -> s.withFont(FONT)); }
    public static MutableComponent text(String value) { return text(Component.literal(value)); }
    public static int width(Font font, String value) { return font.width(text(value)); }
    public static void label(GuiGraphics g, Font font, Component value, int x, int y, int color, int maxWidth) {
        var lines = font.split(text(value), Math.max(1, maxWidth));
        if (!lines.isEmpty()) g.drawString(font, lines.get(0), x, y, color, false);
    }
    public static void label(GuiGraphics g, Font f, String value, int x, int y, int color, int maxWidth) {
        label(g, f, Component.literal(value), x, y, color, maxWidth);
    }
    public static void centered(GuiGraphics g, Font f, String value, int center, int y, int color) {
        centered(g,f,Component.literal(value),center,y,color);
    }
    public static void centered(GuiGraphics g, Font f, Component value, int center, int y, int color) {
        var t = text(value); g.drawString(f, t, center - f.width(t) / 2, y, color, false);
    }
    public static void icon(GuiGraphics g, String name, int x, int y) {
        g.blit(texture(name), x, y, 0, 0, 16, 16, 16, 16);
    }
    public static void emptyState(GuiGraphics g, Font font, String key, int x, int y, int width) {
        icon(g,"attribute_check",x+width/2-8,y+65);
        int lineY=y+92;
        for (String suffix : List.of(".title", ".hint")) {
            for (var line : font.split(text(Component.translatable(key+suffix)), Math.max(1,width-44))) {
                g.drawString(font,line,x+(width-font.width(line))/2,lineY,suffix.equals(".title") ? INK : MUTED,false);
                lineY+=10;
            }
            lineY+=4;
        }
    }
     
    public static void attributeIcon(GuiGraphics g, ResourceLocation id, int x, int y) {
        var attribute = id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
        MobEffect effect = null;
        Item item = Items.PAPER;
        if (attribute == Attributes.MAX_HEALTH) effect = MobEffects.HEALTH_BOOST;
        else if (attribute == Attributes.MOVEMENT_SPEED) effect = MobEffects.MOVEMENT_SPEED;
        else if (attribute == Attributes.ATTACK_DAMAGE) effect = MobEffects.DAMAGE_BOOST;
        else if (attribute == Attributes.ATTACK_SPEED) effect = MobEffects.DIG_SPEED;
        else if (attribute == Attributes.LUCK) effect = MobEffects.LUCK;
        else if (attribute == Attributes.ARMOR) item = Items.IRON_CHESTPLATE;
        else if (attribute == Attributes.ARMOR_TOUGHNESS) item = Items.DIAMOND_CHESTPLATE;
        else if (attribute == Attributes.KNOCKBACK_RESISTANCE) item = Items.SHIELD;
        else if (attribute == Attributes.FLYING_SPEED) item = Items.ELYTRA;
        if (effect != null) g.blit(x-1, y, 0, 18, 18, Minecraft.getInstance().getMobEffectTextures().get(effect));
        else g.renderFakeItem(new ItemStack(item), x, y);
    }
     
    private static void patch(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                              int u, int v, int sw, int sh, int tw, int th) {
        if (w > 0 && h > 0 && sw > 0 && sh > 0) g.blit(tex, x, y, w, h, u, v, sw, sh, tw, th);
    }
    public static void nine(GuiGraphics g, String name, int x, int y, int w, int h,
                            int tw, int th, int edgeX, int top, int bottom) {
        int[] dx = {0, edgeX, w-edgeX, w}, dy = {0, top, h-bottom, h};
        int[] sx = {0, edgeX, tw-edgeX, tw}, sy = {0, top, th-bottom, th};
        var tex = texture(name);
        for (int row=0; row<3; row++) for (int col=0; col<3; col++)
            patch(g,tex,x+dx[col],y+dy[row],dx[col+1]-dx[col],dy[row+1]-dy[row],
                    sx[col],sy[row],sx[col+1]-sx[col],sy[row+1]-sy[row],tw,th);
    }
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        nine(g,"panel",x,y,w,h,320,262,10,48,28);
    }
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        nine(g,"group_card",x,y,w,h,294,56,6,6,6);
    }
    public static void bar(GuiGraphics g, int x, int y, int w, float ratio) {
        bar(g,x,y,w,ratio,0);
    }
    public static void bar(GuiGraphics g, int x, int y, int w, float ratio, int nutrientIcon) {
        if (w < 5) return;
        nine(g,"scale_empty",x,y,w,8,261,8,2,2,2);
        int fill = Math.round((w-4)*Math.max(0,Math.min(1,ratio)));
        if (fill == 0) return;
        String fillTexture = nutrientIcon >= 1 && nutrientIcon <= 7 ? "scale_fill_"+nutrientIcon : "scale_fill_green";
        patch(g,texture(fillTexture),x+2,y+2,fill,4,0,0,257,4,257,4);
    }
    public static Button button(int x,int y,int w,int h,Component label,Button.OnPress press,BooleanSupplier selected) {
        return new Button(x,y,w,h,text(label),press,supplier -> supplier.get()) {
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial) {
                boolean on = selected.getAsBoolean();
                String state = on ? "selected" : isHoveredOrFocused() && active ? "hover" : "normal";
                nine(g,"tab_"+state,getX(),getY(),getWidth(),getHeight(),96,19,6,5,5);
                var font = Minecraft.getInstance().font;
                var title = getMessage();
                if (font.width(title) <= getWidth()-8) g.drawString(font,title,
                        getX()+(getWidth()-font.width(title))/2,getY()+(getHeight()-8)/2-1,INK,false);
                else label(g,font,title,getX()+4,getY()+(getHeight()-8)/2-1,INK,getWidth()-8);
            }
        };
    }
    public static Button button(int x,int y,int w,int h,Component label,Button.OnPress press) {
        return button(x,y,w,h,label,press,()->false);
    }
    public static Button navigationButton(int x,int y,String name,Component label,Button.OnPress press) {
        return navigationButton(x,y,name,label,press,()->false);
    }
    public static Button navigationButton(int x,int y,String name,Component label,Button.OnPress press,BooleanSupplier selected) {
        return new Button(x,y,20,17,text(label),press,supplier -> supplier.get()) {
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial) {
                String state = selected.getAsBoolean()?"selected":active && isHoveredOrFocused() ? "hover" : "normal";
                g.blit(texture("nav_"+name+"_"+state),getX(),getY(),0,0,20,17,20,17);
            }
        };
    }
    public static void marker(GuiGraphics g,int x,int y) {
        g.fill(x-1,y,x+2,y+11,0xFF603B2E);g.fill(x,y+1,x+1,y+10,0xFFFFE8B0);
    }
    public static void scrollThumb(GuiGraphics g,int x,int y,int height) {
        nine(g,"scroll_thumb",x,y,8,height,8,12,2,3,3);
        g.blit(texture("scroll_grip"),x+2,y+(height-5)/2,0,0,4,5,4,5);
    }
     
    public static void popup(GuiGraphics g,Font font,List<Component> text,int mx,int my,int width,int height,int offset) {
        int wrap=Math.max(40,Math.min(310,width-30));
        var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(var line:text)lines.addAll(font.split(HarvestUi.text(line),wrap));
        if(lines.isEmpty())return;
        int maxRows=Math.max(1,(height-32)/10),visible=Math.min(maxRows,lines.size());
        boolean scrollable=visible<lines.size();if(scrollable)visible=Math.max(1,visible-1);
        int start=Math.min(Math.max(0,offset),Math.max(0,lines.size()-visible));
        int inner=lines.stream().mapToInt(font::width).max().orElse(40);
        if(scrollable)inner=Math.max(inner,font.width(Component.translatable("ldiediet.journal.help.scroll")));
        int w=Math.min(width-8,inner+14),h=(visible+(scrollable?1:0))*10+14;
        int x=mx+12;if(x+w>width-4)x=mx-w-10;x=Math.max(4,Math.min(width-w-4,x));
        int y=Math.max(4,Math.min(height-h-4,my-8));
        g.pose().pushPose();g.pose().translate(0,0,500);
        nine(g,"hover_panel",x,y,w,h,24,24,5,5,5);
        for(int i=0;i<visible;i++)g.drawString(font,lines.get(start+i),x+7,y+7+i*10,INK,false);
        if(scrollable)g.drawString(font,Component.translatable("ldiediet.journal.help.scroll"),x+7,y+7+visible*10,MUTED,false);
        g.pose().popPose();
    }
    public static List<Component> tooltip(List<Component> lines) {
         
        return lines.stream().map(line -> (Component)text(line)).toList();
    }
}
