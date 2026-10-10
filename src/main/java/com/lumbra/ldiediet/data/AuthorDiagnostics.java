package com.lumbra.ldiediet.data;

import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.LDieDietServerConfig;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.rewards.RewardHandler;
import com.lumbra.ldiediet.util.GroupIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

 
public final class AuthorDiagnostics {
    private static final Map<String,List<String>> PARSE=new TreeMap<>();
    private AuthorDiagnostics() {}
    public static synchronized void parseIssue(String resource,String message) {
        PARSE.computeIfAbsent(resource,k->new ArrayList<>()).add(String.valueOf(message));
        LDieDiet.LOGGER.warn("{}: {}",resource,message);
    }
    public static synchronized void clearParseIssues(String prefix) { PARSE.keySet().removeIf(k->k.startsWith(prefix)); }
    public static void logValidation(MinecraftServer server) {
        var issues=validate(server);
        LDieDiet.LOGGER.info("Modpack validation: {} diagnostic(s); use /ldiediet validate",issues.size());
        for(String issue:issues)LDieDiet.LOGGER.warn("Pack validation: {}",issue);
    }
    public static List<String> validate(MinecraftServer server) {
        List<String> issues=new ArrayList<>();
        synchronized(AuthorDiagnostics.class) { PARSE.forEach((r,list)->list.forEach(s->issues.add(r+": "+s))); }
        issues.addAll(com.lumbra.ldiediet.math.FormulaDiagnostics.validate(
                com.lumbra.ldiediet.ToleranceStage.getBaseThresholds(), LDieDietServerConfig.MIN_MONOTONY_PER_MEAL.get()));
        issues.addAll(FoodStageProfiles.SERVER.validate());
        if(!DietConfigManager.canWriteConfiguration())issues.add("diet_config.json: last disk load failed; previous configuration retained");
        var manager=FoodGroupManager.getInstance();
        for(var group:manager.getAllGroups()) {
            String id="food_groups/"+group.getId();
            if(group.getThreshold()<=0)issues.add(id+": threshold must be positive to activate");
            if(group.getThreshold()>LDieDietServerConfig.REWARD_HISTORY_LENGTH.get())issues.add(id+": threshold exceeds reward_history_length");
            if(!Set.of("-c","-o").contains(group.getFlag().toLowerCase(Locale.ROOT)))issues.add(id+": unknown flag "+group.getFlag());
            long matches=ForgeRegistries.ITEMS.getValues().stream().filter(item->group.containsItem(ForgeRegistries.ITEMS.getKey(item).toString())).count();
            if(matches==0)issues.add(id+": no installed items match items/tags");
            if(group.isRequireUnique() && matches>0 && matches<group.getThreshold())issues.add(id+": fewer matching item IDs than threshold; requires distinct NBT variants or more items");
            checkAdvancement(server,issues,id,group.getAdvancement(),group.getId().getNamespace());
            if(!group.getIcon().isBlank())checkId(issues,id+".icon",group.getIcon(),false);
            int index=0;
            for(var effect:group.getEffects()) {
                String path=id+".effects["+index+++"]";
                for(String ref:effect.getRequiresActiveGroups())if(manager.getGroup(ResourceLocation.tryParse(GroupIds.canonical(ref,group.getId().getNamespace())))==null)issues.add(path+": missing prerequisite "+ref);
                for(String ref:effect.getCancelOnGroups())if(manager.getGroup(ResourceLocation.tryParse(GroupIds.canonical(ref,group.getId().getNamespace())))==null)issues.add(path+": missing cancel target "+ref);
                if(effect.isFixed() && effect.getDurationTicks()<=0)issues.add(path+": fixed duration_ticks must be positive");
                if(effect.isMobEffect())checkId(issues,path,effect.getEffectId(),true);
                if(effect.isAttribute())checkAttribute(issues,path,effect.getEffectId());
                var c=effect.getConditions();
                if(c!=null) {
                    for(String issue:c.diagnostics(server))issues.add(path+": "+issue);
                    if(!Set.of("AND","OR").contains(c.getLogic()))issues.add(path+": unknown condition logic "+c.getLogic());
                    if(c.getTemperatureMin()!=null && c.getTemperatureMax()!=null && c.getTemperatureMin()>c.getTemperatureMax())issues.add(path+": temperature min exceeds max");
                    if(c.getSeason()!=null && !Set.of("spring","summer","autumn","winter").contains(c.getSeason().toLowerCase(Locale.ROOT)))issues.add(path+": unknown season "+c.getSeason());
                    if(c.getColdSweatState()!=null && !Set.of("freezing","cold","normal","hot","overheating").contains(c.getColdSweatState().toLowerCase(Locale.ROOT)))issues.add(path+": unknown cold_sweat_state "+c.getColdSweatState());
                }
            }
            if(cycle(group.getId().toString(),group.getId().toString(),new HashSet<>()))issues.add(id+": cyclic group prerequisite path (review alternative effects/manual activation)");
        }
        for(var scale:NutritionScaleManager.getInstance().getAll()) {
            String path="nutrition_scales/"+scale.id();
            var thresholds=scale.thresholds();
            for(int i=0;i<thresholds.size();i++) {
                var a=thresholds.get(i);
                if(a.min()<0 || a.max()>scale.maxValue())issues.add(path+".thresholds["+i+"]: outside scale bounds");
                for(var spec:a.modifiers())checkAttribute(issues,path,spec.attribute().toString());
                for(int j=0;j<i;j++) {
                    var b=thresholds.get(j);float lo=Math.max(a.min(),b.min()),hi=Math.min(a.max(),b.max());
                    if(lo<hi || lo==hi && a.includes(lo)&&b.includes(lo))issues.add(path+": thresholds "+j+" and "+i+" overlap; both apply");
                }
            }
        }
        for(var rule:NutritionRuleManager.INSTANCE.getAll()) {
            String path="nutrition_rules/"+rule.id();
            if(rule.worldConditions()!=null)for(String issue:rule.worldConditions().diagnostics(server))issues.add(path+": "+issue);
            for(var c:rule.conditions())for(String nutrient:c.nutrients())if(NutritionScaleManager.getInstance().get(nutrient)==null)issues.add(path+": unknown nutrient "+nutrient);
            for(var a:rule.attributes())checkAttribute(issues,path,a.attribute().toString());
            for(var e:rule.effects())checkId(issues,path,e.effect().toString(),true);
            checkAdvancement(server,issues,path,rule.advancement(),rule.id().getNamespace());
        }
        return List.copyOf(issues);
    }
    private static boolean cycle(String start,String current,Set<String> visited) {
        if(!visited.add(current))return false;
        var group=FoodGroupManager.getInstance().getGroup(ResourceLocation.tryParse(current));if(group==null)return false;
        for(var e:group.getEffects())for(String ref:e.getRequiresActiveGroups()) {
            String next=GroupIds.canonical(ref,group.getId().getNamespace());
            if(next.equals(start)||cycle(start,next,visited))return true;
        }
        return false;
    }
    private static void checkAttribute(List<String> issues,String path,String value) {
        var id=ResourceLocation.tryParse(value);if(id==null||!ForgeRegistries.ATTRIBUTES.containsKey(id))issues.add(path+": unavailable attribute "+value);
    }
    private static void checkId(List<String> issues,String path,String value,boolean effect) {
        var id=ResourceLocation.tryParse(value);
        if(id==null || !(effect?ForgeRegistries.MOB_EFFECTS.containsKey(id):ForgeRegistries.ITEMS.containsKey(id)))issues.add(path+": unavailable "+(effect?"effect ":"item ")+value);
    }
    private static void checkAdvancement(MinecraftServer server,List<String> issues,String path,String value,String namespace) {
        if(value.isBlank())return;var id=ResourceLocation.tryParse(GroupIds.canonical(value,namespace));
        if(id==null || server.getAdvancements().getAdvancement(id)==null)issues.add(path+": unavailable advancement "+value);
    }
    public static List<String> explain(Player player,IFoodTolerance cap,FoodGroupDefinition group) {
        List<String> out=new ArrayList<>();String id=group.getId().toString();
        out.add(id+": "+RewardHandler.countEligibleHistory(cap.getEatingHistory(),group,cap.getRewardCursor(id))+"/"+group.getThreshold()
                +", active="+RewardHandler.activatedGroups(cap).contains(id)+", unique="+group.isRequireUnique()+", consecutive="+group.isConsecutive());
        int index=0;var active=RewardHandler.activatedGroups(cap);
        for(var e:group.getEffects()) {
            List<String> missing=e.getRequiresActiveGroups().stream().filter(ref->!active.contains(GroupIds.canonical(ref,group.getId().getNamespace()))).toList();
            out.add("effect["+index+++"] "+e.getEffectId()+": conditions="+(e.getConditions()==null||e.getConditions().evaluate(player))
                    +", missing prerequisites="+missing+", duration="+e.getDurationType());
        }
        for(var e:cap.getActiveEffects())if(e.getGroupId().equals(id))out.add("slot "+e.getDefinitionIndex()+": "+e.getState()+", remaining="+e.getRemainingTicks()+" ticks, applied="+e.isCurrentlyActive());
        return out;
    }
}
