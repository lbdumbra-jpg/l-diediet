package com.lumbra.ldiediet.client.screen;

import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.client.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.RewardHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

 
public class JournalScreen extends Screen {
    public enum Tab { NUTRITION, FOOD, GROUPS, EFFECTS }
    protected Tab tab;
    private int left,top,pw,ph,bodyTop,bodyBottom,contentHeight;
    private double scroll,dragOffset;
    private boolean dragging;
    private List<Component> hover;
    private int tooltipScroll,tooltipHash;
    public JournalScreen(Tab tab) {super(Component.translatable("ldiediet.journal.title"));this.tab=tab;}
    @Override public boolean isPauseScreen(){return false;}
    public Tab selectedTab(){return tab;}
    public double scrollPosition(){return scroll;}
    public void refreshScales() {clamp();}
    public void refreshGroups() {clamp();}
    @Override protected void init(){
        pw=Math.min(320,Math.max(160,width-16));ph=Math.min(262,Math.max(120,height-16));
        left=(width-pw)/2;top=(height-ph)/2;bodyTop=top+52;bodyBottom=top+ph-32;
        int tw=(pw-32)/3;
        for(int i=0;i<3;i++) {final Tab t=Tab.values()[i];
            addRenderableWidget(HarvestUi.button(left+10+i*(tw+6),top+25,tw,19,title(t),b->select(t),()->tab==t));}
        addRenderableWidget(HarvestUi.navigationButton(left+pw-36,top+7,"close",Component.translatable("gui.close"),b->onClose()));
        var prev=addRenderableWidget(HarvestUi.navigationButton(left+13,top+ph-26,"previous",Component.translatable("ldiediet.journal.previous"),b->select(Tab.values()[(tab.ordinal()+3)%4])));
        var next=addRenderableWidget(HarvestUi.navigationButton(left+pw-34,top+ph-26,"next",Component.translatable("ldiediet.journal.next"),b->select(Tab.values()[(tab.ordinal()+1)%4])));

        var effects=addRenderableWidget(HarvestUi.navigationButton(left+pw/2-10,top+ph-26,"effects",title(Tab.EFFECTS),b->select(Tab.EFFECTS),()->tab==Tab.EFFECTS));
        clamp();
    }
    private static Component title(Tab t){return Component.translatable(switch(t){case NUTRITION->"ldiediet.gui.nutrition.tab";case FOOD->"ldiediet.journal.food";case GROUPS->"ldiediet.gui.nutrition.groups";case EFFECTS->"ldiediet.journal.effects";});}
    private void select(Tab next){tab=next;scroll=0;dragging=false;tooltipScroll=0;}
    private boolean helpHovered(int mx,int my){return mx>=left+pw-62&&mx<left+pw-42&&my>=top+7&&my<top+24;}
    private int viewport(){return Math.max(1,bodyBottom-bodyTop);}
    private int maxScroll(){return Math.max(0,contentHeight-viewport());}
    private void clamp(){scroll=Math.max(0,Math.min(scroll,maxScroll()));}
    private int thumbHeight(){return Math.min(viewport(),Math.max(20,viewport()*viewport()/Math.max(1,contentHeight)));}
    private int thumbY(){return bodyTop+(int)Math.round((viewport()-thumbHeight())*scroll/Math.max(1,maxScroll()));}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(hasShiftDown()&&hover!=null){tooltipScroll=Math.max(0,tooltipScroll-(int)Math.signum(delta)*3);return true;}if(x>=left+10&&x<left+pw-10&&y>=bodyTop&&y<bodyBottom){scroll-=delta*26;clamp();return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&maxScroll()>0&&x>=left+pw-21&&x<left+pw-11&&y>=bodyTop&&y<bodyBottom){
            dragging=true;dragOffset=y>=thumbY()&&y<thumbY()+thumbHeight()?y-thumbY():thumbHeight()/2.0;dragTo(y);return true;
        }return super.mouseClicked(x,y,button);
    }
    private void dragTo(double y){scroll=(y-bodyTop-dragOffset)*maxScroll()/Math.max(1,viewport()-thumbHeight());clamp();}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging){dragTo(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==264||key==265||key==266||key==267){scroll+=(key==264?26:key==265?-26:key==266?-viewport():viewport());clamp();return true;}
        if(key==268||key==269){scroll=key==268?0:maxScroll();return true;}return super.keyPressed(key,scan,mods);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        renderBackground(g);HarvestUi.nine(g,"journal_panel",left,top,pw,ph,320,262,10,48,28);hover=null;
        HarvestUi.centered(g,font,title,left+pw/2,top+9,HarvestUi.INK);
        g.blit(HarvestUi.texture(helpHovered(mx,my)?"nav_help_hover":"nav_help_normal"),left+pw-62,top+7,0,0,20,17,20,17);
        var cap=minecraft.player==null?null:CapabilityHandler.get(minecraft.player);
        if(cap!=null){
            var scales=visibleScales();
            var foods=cap.getEatenCounts().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing((FoodKey k)->k.itemId().toString()).thenComparing(FoodKey::variantId))).toList();
            var groups=FoodGroupManager.getClientInstance().getAllGroups().stream().sorted(Comparator.comparingInt(FoodGroupDefinition::getOrder).thenComparing(s->s.getId().toString())).toList();
            var effects=ClientPacketHandler.journal().getList("effects",Tag.TAG_COMPOUND);
            int count=switch(tab){case NUTRITION->scales.size();case FOOD->foods.size();case GROUPS->groups.size();case EFFECTS->effects.size();};
            int rowHeight=switch(tab){case NUTRITION->26;case FOOD->36;case GROUPS->60;case EFFECTS->30;};
            contentHeight=count*rowHeight;clamp();
            boolean inside=my>=bodyTop&&my<bodyBottom&&mx>=left+12&&mx<left+pw-23;
            g.enableScissor(left+11,bodyTop,left+pw-11,bodyBottom);
             
            for(int y=bodyTop;y<bodyBottom;y+=16)for(int x=left+12;x<left+pw-12;x+=16)g.blit(HarvestUi.texture("journal_paper"),x,y,0,0,Math.min(16,left+pw-12-x),Math.min(16,bodyBottom-y),16,16);
            for(int i=Math.max(0,(int)scroll/rowHeight);i<count;i++){
                int y=bodyTop+i*rowHeight-(int)scroll;if(y>=bodyBottom)break;
                switch(tab){
                    case NUTRITION->scale(g,scales.get(i),cap,y,inside?mx:-1,inside?my:-1);
                    case FOOD->food(g,foods.get(i),cap,y,inside?mx:-1,inside?my:-1);
                    case GROUPS->group(g,groups.get(i),cap,y,inside?mx:-1,inside?my:-1);
                    case EFFECTS->effect(g,effects.getCompound(i),y,inside?mx:-1,inside?my:-1);
                }
            }
            if(count==0)HarvestUi.label(g,font,Component.translatable("ldiediet.journal.empty"),left+24,bodyTop+20,HarvestUi.MUTED,pw-48);
            g.disableScissor();
            if(maxScroll()>0){HarvestUi.nine(g,"scroll_track",left+pw-20,bodyTop,8,viewport(),8,16,2,2,2);HarvestUi.scrollThumb(g,left+pw-20,thumbY(),thumbHeight());}
        }
        super.render(g,mx,my,dt);
        if(helpHovered(mx,my))hover=help();
        else if(my>=top+ph-26&&my<top+ph-9) {
            if(mx>=left+13&&mx<left+33)hover=List.of(Component.translatable("ldiediet.journal.previous"));
            else if(mx>=left+pw-34&&mx<left+pw-14)hover=List.of(Component.translatable("ldiediet.journal.next"));
            else if(mx>=left+pw/2-10&&mx<left+pw/2+10)hover=List.of(title(Tab.EFFECTS));
        }
        if(hover!=null){int hash=hover.hashCode();if(hash!=tooltipHash){tooltipScroll=0;tooltipHash=hash;}HarvestUi.popup(g,font,hover,mx,my,width,height,tooltipScroll);}
    }
    public static List<NutritionScaleDefinition> visibleScales(){
        var snapshot=ClientPacketHandler.journal();var visible=snapshot.getList("visibleScales",Tag.TAG_STRING);var keys=new HashSet<String>();for(var tag:visible)keys.add(tag.getAsString());
        return NutritionScaleManager.getClientInstance().getAll().stream().filter(s->!snapshot.contains("visibleScales")||keys.contains(s.nutrientKey())).sorted(Comparator.comparingInt(NutritionScaleDefinition::order).thenComparing(s->s.id().toString())).toList();
    }
    private List<Component> help(){
        return switch(tab){
            case NUTRITION->lines(ClientPacketHandler.journal(),"nutritionHelp");
            case FOOD->lines(ClientPacketHandler.journal(),"foodHelp");
            case GROUPS->List.of(Component.translatable("ldiediet.journal.help.groups"),Component.translatable("ldiediet.journal.help.groups_rules"),Component.translatable("ldiediet.journal.help.secrets"));
            case EFFECTS->List.of(Component.translatable("ldiediet.journal.help.effects"));
        };
    }
    private void scale(GuiGraphics g,NutritionScaleDefinition s,IFoodTolerance cap,int y,int mx,int my){
        float value=Math.max(0,Math.min(s.maxValue(),cap.getNutrientValue(s.nutrientKey())));int x=left+42,w=pw-69;
        int icon=switch(s.id().getPath()){case "proteins"->1;case "fats"->2;case "carbohydrates"->3;case "vegetables"->4;case "fruits"->5;case "sugars"->6;case "grains"->7;default->0;};
        var custom=s.icon().isBlank()?null:ResourceLocation.tryParse(s.icon());
        if(custom!=null)g.blit(custom,left+17,y+1,0,0,16,16,16,16);else HarvestUi.icon(g,icon==0?"attribute_check":"food_icon_"+icon,left+17,y+1);
        var number=Component.translatable("ldiediet.journal.percent",Math.round(value/s.maxValue()*100));int nw=font.width(number);
        HarvestUi.label(g,font,s.displayName(),x,y,HarvestUi.INK,w-nw-8);HarvestUi.label(g,font,number,x+w-nw,y,HarvestUi.MUTED,nw+1);
        HarvestUi.bar(g,x,y+12,w,value/s.maxValue(),icon);
        if(s.color()!=null&&value>0)g.fill(x+2,y+14,x+2+Math.round((w-4)*value/s.maxValue()),y+18,0xFF000000|s.color());
        var marks=ClientPacketHandler.journal().getList("markers",Tag.TAG_COMPOUND);Double boundary=null;int distance=4;
        for(int i=0;i<marks.size();i++){
            var m=marks.getCompound(i);if(!m.getString("scale").equals(s.nutrientKey()))continue;
            int pos=x+2+(int)Math.round((w-4)*m.getDouble("value")/s.maxValue());
            HarvestUi.marker(g,pos,y+10);
            if(Math.abs(mx-pos)<distance&&my>=y+9&&my<y+23){distance=Math.abs(mx-pos);boundary=m.getDouble("value");}
        }
        if(boundary!=null)hover=JournalMarkers.tooltip(marks,s.nutrientKey(),boundary,s.maxValue());
    }
    private void food(GuiGraphics g,Map.Entry<FoodKey,Long> row,IFoodTolerance cap,int y,int mx,int my){
        var key=row.getKey();var stack=cap.foodSample(key);if(stack.isEmpty())stack=new ItemStack(key.item());int x=left+42,w=pw-69;
        g.renderFakeItem(stack,left+17,y+2);
        var data=new CompoundTag();var all=ClientPacketHandler.journal().getList("foods",Tag.TAG_COMPOUND);
        for(int i=0;i<all.size();i++)if(key.equals(FoodKey.read(all.getCompound(i)))){data=all.getCompound(i);break;}
        float m=cap.getMonotony(key);int level=data.contains("stage")?data.getInt("stage"):ToleranceStage.forItem(key.item(),m,true).getLevel();
        var stageLabel=Component.translatable("ldiediet.journal.stage",level);int labelWidth=font.width(stageLabel);
        HarvestUi.label(g,font,stack.getHoverName(),x,y,HarvestUi.INK,w-labelWidth-8);HarvestUi.label(g,font,stageLabel,x+w-labelWidth,y,HarvestUi.MUTED,labelWidth+1);
        int[] thresholds=data.contains("thresholds")?data.getIntArray("thresholds"):ToleranceStage.getBaseThresholds(),remaining=data.getIntArray("remaining");int max=Math.max(1,thresholds[thresholds.length-1]);
        HarvestUi.bar(g,x,y+12,w,m/max);
        for(int i=0;i<thresholds.length;i++){
            int pos=x+2+Math.round((w-4)*(float)thresholds[i]/max);HarvestUi.marker(g,pos,y+10);
            Component n=data.getBoolean("fixed")?Component.translatable("ldiediet.journal.portions.fixed"):remaining.length<=i?Component.translatable("ldiediet.journal.portions.unknown"):remaining[i]==0?Component.translatable("ldiediet.journal.portions.reached"):remaining[i]<0?portions(remaining[i]):Component.translatable("ldiediet.journal.portions.remaining",remaining[i]);
            int textX=Math.max(x,Math.min(pos-font.width(n)/2,x+w-font.width(n)));
            HarvestUi.label(g,font,n,textX,y+24,HarvestUi.MUTED,Math.max(1,x+w-textX));
        }
        if(mx>=left+12&&mx<left+pw-23&&my>=y&&my<y+34){hover=lines(data,"details");}
    }
    private static Component portions(int value){return value==com.lumbra.ldiediet.math.MealForecast.UNREACHABLE?Component.translatable("ldiediet.journal.infinity"):value==com.lumbra.ldiediet.math.MealForecast.BEYOND_LIMIT?Component.translatable("ldiediet.journal.portions.beyond_limit",com.lumbra.ldiediet.math.MealForecast.LIMIT):Component.literal(String.valueOf(value));}
    private void group(GuiGraphics g,FoodGroupDefinition group,IFoodTolerance cap,int y,int mx,int my){
        int x=left+13,w=pw-40;HarvestUi.card(g,x,y,w,56);
        if(!group.isKnown(cap)){
            HarvestUi.icon(g,"secret_group",x+7,y+8);g.drawString(font,Component.translatable("ldiediet.journal.secret_name").withStyle(style->style.withFont(new ResourceLocation("minecraft:alt"))),x+30,y+7,HarvestUi.INK,false);
            HarvestUi.label(g,font,Component.translatable("ldiediet.journal.secret"),x+30,y+20,HarvestUi.MUTED,w-40);HarvestUi.bar(g,x+30,y+34,w-40,0);
            for(int px=x+34;px<x+w-12;px+=8)g.fill(px,y+36,px+3,y+40,0xFFCEAF76);
            if(mx>=x&&mx<x+w&&my>=y&&my<y+56)hover=List.of(Component.translatable("ldiediet.journal.secret_hint"));return;
        }
        g.renderFakeItem(groupIcon(group),x+7,y+8);HarvestUi.label(g,font,group.displayName(),x+30,y+6,HarvestUi.INK,w-40);
        Component flags=Component.translatable("ldiediet.journal.group_rules",Component.translatable(group.isConsecutive()?"ldiediet.gui.monotony.consecutive":"ldiediet.gui.monotony.any"),Component.translatable(group.isRequireUnique()?"ldiediet.gui.monotony.unique":"ldiediet.gui.monotony.any_type"));
        HarvestUi.label(g,font,flags,x+30,y+19,HarvestUi.MUTED,w-40);
        int count=RewardHandler.countEligibleHistory(cap.getEatingHistory(),group,cap.getRewardCursor(group.getId().toString()));int threshold=group.getThreshold();
        HarvestUi.bar(g,x+30,y+34,w-40,threshold<=0?0:(float)count/threshold);
        HarvestUi.label(g,font,Component.translatable("ldiediet.journal.progress",count,threshold),x+30,y+44,HarvestUi.MUTED,w-40);
        if(mx>=x&&mx<x+w&&my>=y&&my<y+56){hover=new ArrayList<>();hover.add(group.displayName());hover.add(flags);hover.add(Component.translatable("ldiediet.tooltip.progress",count,threshold));for(var effect:group.getEffects())if(effect.getConditions()!=null)hover.addAll(effect.getConditions().descriptions());}
    }
    private static ItemStack groupIcon(FoodGroupDefinition group){
        var id=ResourceLocation.tryParse(group.getIcon());if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))return new ItemStack(BuiltInRegistries.ITEM.get(id));
        if(!group.getAdvancement().isBlank()) {
            var connection=net.minecraft.client.Minecraft.getInstance().getConnection();id=ResourceLocation.tryParse(group.getAdvancement());
            var advancement=connection==null||id==null?null:connection.getAdvancements().getAdvancements().get(id);
            if(advancement!=null&&advancement.getDisplay()!=null)return advancement.getDisplay().getIcon();
        }
        for(String item:group.getItems()){
            if(item.startsWith("#")){id=ResourceLocation.tryParse(item.substring(1));if(id==null)continue;var tag=BuiltInRegistries.ITEM.getTag(net.minecraft.tags.TagKey.create(BuiltInRegistries.ITEM.key(),id));if(tag.isPresent()&&tag.get().size()>0)return new ItemStack(tag.get().get(0).value());}
            else {id=ResourceLocation.tryParse(item);if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))return new ItemStack(BuiltInRegistries.ITEM.get(id));}
        }return new ItemStack(Items.PAPER);
    }
    private void effect(GuiGraphics g,CompoundTag e,int y,int mx,int my){
        int x=left+42,w=pw-69;var id=ResourceLocation.tryParse(e.getString("id"));Component name,detail;
        if(e.getString("type").equals("attribute")){
            HarvestUi.attributeIcon(g,id,left+17,y+1);var a=id==null?null:ForgeRegistries.ATTRIBUTES.getValue(id);name=a==null?Component.literal(e.getString("id")):Component.translatable(a.getDescriptionId());
            detail=Component.literal(JournalSnapshot.attributeTotal(e));
        }else if(e.getString("type").equals("status")){
            var effect=id==null?null:ForgeRegistries.MOB_EFFECTS.getValue(id);name=effect==null?Component.literal(e.getString("id")):Component.translatable(effect.getDescriptionId());
            if(effect!=null)g.blit(left+16,y+1,0,18,18,minecraft.getMobEffectTextures().get(effect));
            int seconds=e.getInt("duration")/20;Component duration=e.getInt("duration")==-1?Component.translatable("ldiediet.journal.infinity"):Component.translatable("ldiediet.journal.duration",seconds/60,String.format(Locale.ROOT,"%02d",seconds%60));
            detail=Component.translatable("ldiediet.journal.status_detail",e.getInt("amplifier")+1,duration);
        }else{
            HarvestUi.icon(g,"attribute_check",left+17,y+1);name=Component.translatable("ldiediet.journal.kind."+e.getString("type"));
            boolean nutrient=e.getString("type").equals("nutrient_multiplier");var scale=NutritionScaleManager.getClientInstance().get(e.getString("id"));
            Component target=nutrient?(scale!=null?scale.displayName():Component.translatable(e.getString("id").equals("all")||e.getString("id").equals("*")?"ldiediet.journal.all_nutrients":"ldiediet.journal.custom_nutrient",e.getString("id"))):Component.translatableWithFallback("ldiediet.overlay."+e.getString("id"),e.getString("id"));
            detail=Component.translatable("ldiediet.journal.name_value",target,nutrient?JournalSnapshot.amountText(e.getDouble("amount"),2):JournalSnapshot.number(e.getDouble("amount")));
        }
        HarvestUi.label(g,font,name,x,y,HarvestUi.INK,w);HarvestUi.label(g,font,detail,x,y+12,HarvestUi.GOOD,w);
        g.fill(x,y+26,x+w,y+27,0xFFE8B571);
        if(mx>=left+12&&mx<left+pw-23&&my>=y&&my<y+28){hover=new ArrayList<>();hover.add(name);hover.add(detail);hover.addAll(lines(e,"sources"));}
    }
    private static List<Component> lines(CompoundTag tag,String key){return JournalMarkers.lines(tag,key);}
}
