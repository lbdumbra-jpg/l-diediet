package com.lumbra.ldiediet.gametest;

import com.google.gson.*;
import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.client.*;
import com.lumbra.ldiediet.client.screen.JournalScreen;
import com.lumbra.ldiediet.compat.DietCompatibilityData;
import com.lumbra.ldiediet.data.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

 
@Mod.EventBusSubscriber(modid=LDieDiet.MOD_ID,value=Dist.CLIENT)
public final class JournalUiAcceptanceChecks {
    private static int phase,ticks,wait;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static boolean finished;
    private static final List<String> passed=new ArrayList<>();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("ldiediet.journalAcceptance")||finished||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks>1600)throw new IllegalStateException("Journal acceptance timed out at "+phase);
            if(failure!=null)throw new IllegalStateException("Journal server fixture failed",failure);
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            var cap=CapabilityHandler.get(mc.player);
            switch(phase) {
                case 0 -> {
                    if(ticks<100||FoodGroupManager.getClientInstance().getAllGroups().isEmpty())return;
                    require(DietCompatibilityData.INSTANCE.rules("builtin").isEmpty(),"Old Diet bonuses overlap laboratory tiers");
                    passed.add("Cached original Diet data stays below the real laboratory datapack");
                    language(mc,"ru_ru");phase=1;wait=0;
                }
                case 1 -> {
                    if(!ready||mc.getOverlay()!=null)return;
                    require(Component.translatable("ldiediet.journal.title").getString().equals("Рацион еды"),"Russian header not localized");
                    require(mc.font.width(Component.translatable("ldiediet.journal.title"))<=86,"Title overlaps the plaque decoration");
                    nutrient(mc,80);phase=2;wait=0;
                }
                case 2 -> {
                    if(!ready||cap.getNutrientValue("proteins")!=80)return;
                    verifyPopup(80,"+2");mc.setScreen(screen(80));phase=3;wait=0;
                }
                case 3 -> {
                    if(++wait<8)return;
                    Screenshot.grab(mc.gameDirectory,"journal-80-ru.png",mc.getMainRenderTarget(),msg->{});
                    nutrient(mc,100);phase=4;wait=0;
                }
                case 4 -> {
                    if(!ready||cap.getNutrientValue("proteins")!=100)return;
                    verifyPopup(100,"+4");mc.setScreen(screen(100));phase=5;wait=0;
                }
                case 5 -> {
                    if(++wait<8)return;
                    Screenshot.grab(mc.gameDirectory,"journal-100-ru.png",mc.getMainRenderTarget(),msg->{});
                    passed.add("Russian 80%/100% popups have one heading, native attribute names and no replaced bonus");
                    language(mc,"en_us");phase=6;wait=0;
                }
                case 6 -> {
                    if(!ready||mc.getOverlay()!=null||++wait<8)return;
                    require(Component.translatable("ldiediet.journal.title").getString().equals("Food diet"),"English header did not change after resource reload");
                    verifyPopup(100,"+4");mc.setScreen(screen(100));phase=7;wait=0;
                }
                case 7 -> {
                    if(++wait<8)return;
                    Screenshot.grab(mc.gameDirectory,"journal-100-en.png",mc.getMainRenderTarget(),msg->{});
                    passed.add("English language reload changes title and complete threshold text without baked lettering");
                    finish(mc,null);
                }
            }
        }catch(Throwable error){finish(mc,error);}
    }
    private static void language(Minecraft mc,String locale) {
        ready=false;mc.options.languageCode=locale;mc.getLanguageManager().setSelected(locale);
        mc.reloadResourcePacks().whenComplete((unused,error)->{failure=error;ready=true;});
    }
    private static void nutrient(Minecraft mc,float amount) {
        ready=false;mc.getSingleplayerServer().execute(()->{
            try {
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var cap=CapabilityHandler.get(player);cap.clearNutrients();cap.setNutrientValue("proteins",amount);
                com.lumbra.ldiediet.api.PackApi.refresh(player);CapabilityHandler.syncToClient(player);ready=true;
            }catch(Throwable error){failure=error;}
        });
    }
    private static JournalScreen screen(double boundary) {
        return new JournalScreen(JournalScreen.Tab.NUTRITION) {
            @Override public void render(GuiGraphics g,int mx,int my,float dt) {
                int pw=Math.min(320,Math.max(160,width-16)),ph=Math.min(262,Math.max(120,height-16));
                int index=0;var scales=visibleScales();while(index<scales.size()&&!scales.get(index).nutrientKey().equals("proteins"))index++;
                int x=(width-pw)/2+44+(int)Math.round((pw-73)*boundary/100);
                int y=(height-ph)/2+52+index*26+14;
                super.render(g,x,y,dt);
            }
        };
    }
    private static void verifyPopup(double boundary,String bonus) {
        var markers=ClientPacketHandler.journal().getList("markers",Tag.TAG_COMPOUND);
        var tip=JournalMarkers.tooltip(markers,"proteins",boundary,100);
        require(tip.stream().filter(c->Component.Serializer.toJson(c).contains("ldiediet.journal.boundary_percent")).count()==1,"Repeated threshold heading");
        String content=String.join("\n",tip.stream().map(Component::getString).toList());
        require(content.contains(bonus)&&!content.contains("???")&&!content.contains("ldiediet.")&&!content.contains("attribute.name."),"Missing bonus or unresolved localization: "+content);
        require(boundary!=100||!content.contains("+2"),"100% still lists the old +2 reward");
        require(tip.size()==(boundary==80?4:5),"Unexpected old Diet or duplicate reward lines: "+content);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void finish(Minecraft mc,Throwable error) {
        finished=true;
        try {
            var report=new JsonObject();report.addProperty("success",error==null);report.addProperty("phase",phase);report.add("passed",new Gson().toJsonTree(passed));
            if(error!=null)report.addProperty("error",error.toString());
            Files.writeString(mc.gameDirectory.toPath().resolve("journal-ui-acceptance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception writeError){LDieDiet.LOGGER.error("Cannot save journal acceptance report",writeError);}
        if(error!=null)LDieDiet.LOGGER.error("JOURNAL UI ACCEPTANCE FAILED",error);else LDieDiet.LOGGER.info("JOURNAL UI ACCEPTANCE PASS {}",passed);
        mc.stop();
    }
}
