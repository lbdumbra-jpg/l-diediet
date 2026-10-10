package com.lumbra.ldiediet.data;

import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.compat.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

 
public final class JournalSnapshot {
    public static final String KEY="journal";
    private JournalSnapshot() {}
    public static CompoundTag capture(Player player, IFoodTolerance cap) {
        var root=new CompoundTag(); var markers=new ListTag();
        for(var scale:NutritionScaleManager.getInstance().getAll()) for(var threshold:scale.thresholds()) {
            if(threshold.modifiers().isEmpty())continue;
            var lines=new ArrayList<Component>();
            for(var a:threshold.modifiers()) lines.add(attribute(a.attribute(),a.amount(),a.operation().toValue()));
            boolean active=com.lumbra.ldiediet.api.DietEvents.quotaState(player,"scale:"+scale.id()+":"+scale.thresholds().indexOf(threshold),threshold.includes(cap.getNutrientValue(scale.nutrientKey()))?1:0)>0;
            marker(markers,player,cap,"scale:"+scale.id()+":"+fingerprint(threshold),scale,threshold.min(),threshold.max(),threshold.minInclusive(),threshold.maxInclusive(),active,lines);
        }
        for(var rule:NutritionRuleManager.INSTANCE.getAll()) {
            int raw=rule.multiplier(cap);if(rule.worldConditions()!=null&&!rule.worldConditions().evaluate(player))raw=0;int multiplier=com.lumbra.ldiediet.api.DietEvents.quotaState(player,"rule:"+rule.id(),raw);var lines=new ArrayList<Component>();
            lines.add(rule.displayName());
            if(rule.worldConditions()!=null)lines.addAll(rule.worldConditions().descriptions());
            for(var a:rule.attributes())lines.add(attribute(a.attribute(),a.amount()*Math.max(1,multiplier),a.operation().toValue()));
            for(var e:rule.effects())lines.add(status(e.effect(),e.amplifier()));
            if(!rule.advancement().isBlank())lines.add(Component.translatable("ldiediet.journal.advancement",rule.advancement()));
            lines.add(Component.translatable("ldiediet.journal.compound",logic(rule.logic())));
            for(var c:rule.conditions()) {
                lines.add(condition(c.match(),nutritionNames(c.nutrients(),false),c.min(),c.max(),c.percent()));
            }
            for(var c:rule.conditions())for(String n:c.nutrients()) {
                var scale=NutritionScaleManager.getInstance().get(n);if(scale==null)continue;
                double factor=c.percent()?scale.maxValue()/100.0:1;
                marker(markers,player,cap,"quota:"+rule.id()+":"+fingerprint(rule),scale,c.min()*factor,c.max()*factor,c.minInclusive(),c.maxInclusive(),multiplier>0,lines);
            }
        }
        var diet=DietCompatibilityData.INSTANCE;String suite=cap.getDietSuite();var values=new HashMap<String,Double>();
        for(String g:diet.suiteGroups(suite)) {var s=NutritionScaleManager.getInstance().get(diet.nutrientId(g));values.put(g,cap.getNutrientValue(diet.nutrientId(g))/(double)(s==null?100:s.maxValue()));}
        int dietRuleIndex=0;
        for(var rule:diet.rules(suite)) {
            int mult=com.lumbra.ldiediet.api.DietEvents.quotaState(player,"diet:"+suite+":"+dietRuleIndex++,rule.multiplier(values));var lines=new ArrayList<Component>();
            for(var a:rule.attributes()) {var id=ResourceLocation.tryParse(a.name());if(id!=null)lines.add(attribute(id,a.amount()+Math.max(0,mult-1)*a.increment(),switch(a.operation()){case "multiply_base"->1;case "multiply_total"->2;default->0;}));}
            for(var e:rule.statuses()) {var id=ResourceLocation.tryParse(e.name());if(id!=null)lines.add(status(id,e.power()+Math.max(0,mult-1)*e.increment()));}
            if(lines.isEmpty())continue;
            lines.add(Component.translatable("ldiediet.journal.compound",logic("AND")));
            for(var c:rule.conditions())lines.add(condition(c.match(),nutritionNames(new TreeSet<>(c.groups()),true),c.above()*100,c.below()*100,true));
            for(var c:rule.conditions())for(String g:c.groups()) {
                var scale=NutritionScaleManager.getInstance().get(diet.nutrientId(g));if(scale==null)continue;
                marker(markers,player,cap,"diet:"+suite+":"+fingerprint(rule.conditions().stream().map(condition->new TreeSet<>(condition.groups())+":"+condition.match()+":"+condition.above()+":"+condition.below()).toList()+"|"+rule.attributes()+"|"+rule.statuses()),scale,c.above()*scale.maxValue(),c.below()*scale.maxValue(),true,true,mult>0,lines);
            }
        }
        root.put("markers",markers);
        var effects=new ListTag();
        for(var row:NutritionAttributeSummary.capture(player)) {
            var entry=(CompoundTag)row;var mods=entry.getList("modifiers",Tag.TAG_COMPOUND);
            var e=new CompoundTag();e.putString("type","attribute");e.putString("id",entry.getString("attribute"));
            double addition=0,base=0,total=1;var sources=new ArrayList<Component>();
            for(int i=0;i<mods.size();i++) {
                var m=mods.getCompound(i);double amount=m.getDouble("amount");int op=m.getInt("operation");
                if(op==0)addition+=amount;else if(op==1)base+=amount;else total*=1+amount;
                sources.add(Component.translatable("ldiediet.journal.source_value",source(m.getString("source"),cap),amountText(amount,op)));
            }
            e.putDouble("addition",addition);e.putDouble("base",base);e.putDouble("total",total);
            e.put("sources",components(sources));effects.add(e);
        }
        var ledger=cap.getMobEffectSources().serializeNBT();var owned=ledger.getList("owned",Tag.TAG_COMPOUND);
        player.getActiveEffects().stream().sorted(Comparator.comparing(e->String.valueOf(ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect())))).forEach(effect->{
            var id=ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());if(id==null)return;
            var e=new CompoundTag();e.putString("type","status");e.putString("id",id.toString());e.putInt("amplifier",effect.getAmplifier());e.putInt("duration",effect.getDuration());
            var sources=new ArrayList<Component>();
            for(int i=0;i<owned.size();i++){var s=owned.getCompound(i);if(s.getString("effect").equals(id.toString()))sources.add(Component.translatable("ldiediet.journal.source_value",source(s.getString("source"),cap),s.getCompound("data").getInt("FullAmplifier")+1));}
            if(sources.isEmpty()||ledger.getCompound("external").contains(id.toString()))sources.add(cap.getMobEffectSources().externalOrigin(id));
            e.put("sources",components(sources));effects.add(e);
        });
        for(var effect:cap.getActiveEffects())if(effect.isCurrentlyActive()&&(effect.isOverlay()||effect.isNutrientMultiplier())) {
            var e=new CompoundTag();e.putString("type",effect.getEffectType());e.putString("id",effect.getEffectId());e.putDouble("amount",effect.getValue());e.put("sources",components(List.of(source(effect.sourceId(),cap))));effects.add(e);
        }
        root.put("effects",combineExtras(effects));
        root.put("foods",JournalFood.capture(player,cap));
        root.put("foodHelp",components(JournalFood.stageHelp()));
        var help=new ArrayList<Component>();help.add(Component.translatable("ldiediet.journal.help.nutrition"));
        var config=com.lumbra.ldiediet.LDieDietServerConfig.DECAY_MODE.get();
        help.add(Component.translatable(!com.lumbra.ldiediet.LDieDietServerConfig.NUTRITION_DECAY.get()?"ldiediet.journal.help.decay_off":config==com.lumbra.ldiediet.LDieDietServerConfig.DecayMode.HUNGER?"ldiediet.journal.help.decay_hunger":"ldiediet.journal.help.decay_time"));
        help.add(Component.translatable("ldiediet.journal.help.markers"));
        help.add(Component.translatable("ldiediet.journal.help.death_loss",number(com.lumbra.ldiediet.LDieDietServerConfig.NUTRITION_DEATH_LOSS_PERCENT.get())));
        root.put("nutritionHelp",components(help));
        var used=DietConfigManager.usedNutrients();used.addAll(cap.getAllNutrients().keySet());
        for(var scale:NutritionScaleManager.getInstance().getAll())if(scale.thresholds().stream().anyMatch(t->!t.modifiers().isEmpty()))used.add(scale.nutrientKey());
        for(var rule:NutritionRuleManager.INSTANCE.getAll())for(var condition:rule.conditions())used.addAll(condition.nutrients());
        for(var rule:diet.rules(suite))for(var condition:rule.conditions())for(var name:condition.groups())used.add(diet.nutrientId(name));
        for(var group:FoodGroupManager.getInstance().getAllGroups())for(var effect:group.getEffects())if(effect.getEffectType().equals("nutrient_multiplier")&&!effect.getEffectId().equals("all")&&!effect.getEffectId().equals("*"))used.add(effect.getEffectId());
        var visible=new ListTag();for(var scale:NutritionScaleManager.getInstance().getAll())if(used.contains(scale.nutrientKey())||used.contains(scale.id().toString()))visible.add(StringTag.valueOf(scale.nutrientKey()));root.put("visibleScales",visible);
        return root;
    }
    public static String amountText(double amount,int operation) {return operation==2?"×"+number(1+amount):(amount>=0?"+":"")+number(amount*(operation==1?100:1))+(operation==1?"%":"");}
    public static String attributeTotal(CompoundTag e) {
        var parts=new ArrayList<String>();
        if(e.getDouble("addition")!=0)parts.add(amountText(e.getDouble("addition"),0));
        if(e.getDouble("base")!=0)parts.add(amountText(e.getDouble("base"),1));
        if(e.getDouble("total")!=1)parts.add(amountText(e.getDouble("total")-1,2));
        return parts.isEmpty()?"0":String.join(" · ",parts);
    }
    private static ListTag combineExtras(ListTag effects) {
        var result=new ListTag();var grouped=new LinkedHashMap<String,CompoundTag>();
        for(var tag:effects) {
            var e=(CompoundTag)tag;String type=e.getString("type");
            if(type.equals("attribute")||type.equals("status")){result.add(e);continue;}
            String key=type+":"+e.getString("id");var current=grouped.get(key);
            var sources=e.getList("sources",Tag.TAG_STRING);var detailed=new ListTag();
            for(var source:sources){var c=Component.Serializer.fromJson(source.getAsString());if(c!=null)detailed.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("ldiediet.journal.source_value",c,type.equals("nutrient_multiplier")?amountText(e.getDouble("amount"),2):number(e.getDouble("amount"))))));}
            if(current==null){current=e.copy();current.put("sources",detailed);grouped.put(key,current);result.add(current);}
            else {current.putDouble("amount",type.equals("nutrient_multiplier")?(1+current.getDouble("amount"))*Math.max(0,1+e.getDouble("amount"))-1:Math.max(current.getDouble("amount"),e.getDouble("amount")));current.getList("sources",Tag.TAG_STRING).addAll(detailed);}
        }
        return result;
    }
    private static String fingerprint(Object definition) { return UUID.nameUUIDFromBytes(definition.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(); }
    private static void marker(ListTag target,Player player,IFoodTolerance cap,String key,NutritionScaleDefinition scale,double min,double max,boolean minInclusive,boolean maxInclusive,boolean active,List<Component> lines) {
        if(active)com.lumbra.ldiediet.api.DietEvents.discover(player,cap,key);
        for(double boundary:new double[]{min,max}) {
            if(!Double.isFinite(boundary)||boundary<0||boundary>scale.maxValue())continue;
             
            if(boundary==min&&min==0&&minInclusive&&max!=0)continue;
            if(boundary==max&&max==scale.maxValue()&&maxInclusive&&min!=max)continue;
            var m=new CompoundTag();m.putString("scale",scale.nutrientKey());m.putDouble("value",boundary);m.putString("key",key);
            m.putString("direction",boundary==min?"enter":"exit");
            m.putString("family",key.startsWith("scale:")?"scale:"+scale.id():key);
            m.putBoolean("known",cap.hasDiscovered(key));m.putBoolean("active",active);
            if(cap.hasDiscovered(key))m.put("lines",components(lines));target.add(m);
            if(min==max)break;
        }
    }
    public static Component attribute(ResourceLocation id,double amount,int operation) {
        var a=ForgeRegistries.ATTRIBUTES.getValue(id);
        String value=amountText(amount,operation);
        return Component.translatable("ldiediet.journal.name_value",a==null?Component.literal(id.toString()):Component.translatable(a.getDescriptionId()),value);
    }
    private static Component status(ResourceLocation id,int power) {var e=ForgeRegistries.MOB_EFFECTS.getValue(id);return Component.translatable("ldiediet.journal.name_value",e==null?Component.literal(id.toString()):Component.translatable(e.getDescriptionId()),power+1);}
    public static String number(double value) {if(value!=0&&Math.abs(value)<.01)return String.format(Locale.ROOT,"%.2g",value);return String.format(Locale.ROOT,"%.2f",value).replaceAll("0+$","").replaceAll("\\.$","");}
    public static ListTag components(List<Component> lines) {var list=new ListTag();for(var c:lines)list.add(StringTag.valueOf(Component.Serializer.toJson(c)));return list;}
    private static Component logic(String value) { return Component.translatable("ldiediet.journal.logic."+value.toLowerCase(Locale.ROOT)); }
    private static Component condition(String match,Component names,double min,double max,boolean percent) {
        return Component.translatable("ldiediet.journal.condition",Component.translatable("ldiediet.journal.match."+match),names,number(min),number(max),percent?"%":"");
    }
    private static Component nutritionNames(Collection<String> names,boolean legacy) {
        var result=Component.empty();var visited=new HashSet<String>();
        for(String name:names) {
            String key=legacy?DietCompatibilityData.INSTANCE.nutrientId(name):name;
            var scale=NutritionScaleManager.getInstance().get(key);
            if(!visited.add(scale==null?key:scale.nutrientKey()))continue;
            if(!result.getSiblings().isEmpty())result.append(", ");
            result.append(Component.translatable("ldiediet.journal.quoted",scale==null?Component.literal(name):scale.displayName()));
        }
        return result;
    }
    public static Component source(String key) { return source(key,null); }
    public static Component source(String key,IFoodTolerance cap) {
        if(key.startsWith("ldiediet_reward_"))key=key.substring("ldiediet_reward_".length());
        if(key.startsWith("reward:")) {String group=key.substring(7).split("#")[0];var id=ResourceLocation.tryParse(group);var g=id==null?null:FoodGroupManager.getInstance().getGroup(id);return Component.translatable("ldiediet.journal.source_group",g==null?Component.literal(group):g.displayName());}
        if(key.startsWith("ldiediet_nutrient_")){var s=NutritionScaleManager.getInstance().get(key.substring(18));return Component.translatable("ldiediet.journal.source_scale",s==null?Component.literal(key):s.displayName());}
        if(key.startsWith("ldiediet_quota:")){String id=key.substring(15).replaceFirst(":(?:effect:)?[0-9]+$","");var r=NutritionRuleManager.INSTANCE.get(ResourceLocation.tryParse(id));return Component.translatable("ldiediet.journal.source_quota",r==null?Component.literal(id):r.displayName());}
        for(String prefix:List.of("ldiediet_diet:","diet_suite:"))if(key.startsWith(prefix)) {
            String value=key.substring(prefix.length());int last=value.lastIndexOf(':');
            if(last<0)break;value=value.substring(0,last);last=value.lastIndexOf(':');
            if(last<0)break;
            try {
                String suite=value.substring(0,last);int index=Integer.parseInt(value.substring(last+1));
                var rules=DietCompatibilityData.INSTANCE.rules(suite);if(index<0||index>=rules.size())break;
                var names=new TreeSet<String>();
                for(var c:rules.get(index).conditions())for(String name:c.groups()) {
                    var scale=NutritionScaleManager.getInstance().get(DietCompatibilityData.INSTANCE.nutrientId(name));
                    double level=cap==null||scale==null?Double.NaN:cap.getNutrientValue(scale.nutrientKey())/(double)scale.maxValue();
                    if(cap==null||!c.match().equals("every")||level>=c.above()&&level<=c.below())names.add(name);
                }
                return Component.translatable("ldiediet.journal.source_diet",nutritionNames(names,true));
            }catch(NumberFormatException ignored){break;}
        }
        return Component.translatable("ldiediet.journal.source_nutrition");
    }
}

