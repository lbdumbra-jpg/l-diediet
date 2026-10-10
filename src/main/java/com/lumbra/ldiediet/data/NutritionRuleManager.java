package com.lumbra.ldiediet.data;

import com.google.gson.*;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

 
public final class NutritionRuleManager extends SimpleJsonResourceReloadListener {
    public static final NutritionRuleManager INSTANCE = new NutritionRuleManager();
    private final Map<ResourceLocation,Rule> rules = new TreeMap<>();
    private NutritionRuleManager() { super(new Gson(),"nutrition_rules"); }
    public Collection<Rule> getAll() { return List.copyOf(rules.values()); }
    public Rule get(ResourceLocation id) { return rules.get(id); }
    @Override protected void apply(Map<ResourceLocation,JsonElement> input,ResourceManager resources,ProfilerFiller profiler) {
        rules.clear(); AuthorDiagnostics.clearParseIssues("nutrition_rules/");
        input.forEach((id,json) -> {
            try { rules.put(id,parse(id,json.getAsJsonObject())); }
            catch(RuntimeException e) { AuthorDiagnostics.parseIssue("nutrition_rules/"+id,e.getMessage()); }
        });
    }
    public static Rule parse(ResourceLocation id,JsonObject json) {
        knownFields(json,Set.of("$schema","name","name_key","logic","conditions","attributes","effects","advancement","world_conditions"),"rule");
        List<Condition> conditions = new ArrayList<>();
        for(var element : array(json,"conditions")) {
            var c=element.getAsJsonObject(); List<String> nutrients=new ArrayList<>();
            knownFields(c,Set.of("$schema","nutrients","match","min","max","percent","min_inclusive","max_inclusive"),"conditions[]");
            array(c,"nutrients").forEach(n -> nutrients.add(n.getAsString()));
            if(nutrients.isEmpty()) throw new IllegalArgumentException("conditions[].nutrients must not be empty");
            String match=string(c,"match","all").toLowerCase(Locale.ROOT);
            if(!Set.of("all","any","none","average","every").contains(match)) throw new IllegalArgumentException("Unknown match: "+match);
            double min=number(c,"min",0),max=number(c,"max",100);
            if(min>max) throw new IllegalArgumentException("conditions[].min exceeds max");
            conditions.add(new Condition(List.copyOf(nutrients),match,min,max,bool(c,"percent",true),bool(c,"min_inclusive",true),bool(c,"max_inclusive",true)));
        }
        if(conditions.isEmpty()) throw new IllegalArgumentException("At least one condition is required");
        String logic=string(json,"logic","AND").toUpperCase(Locale.ROOT);
        if(!Set.of("AND","OR").contains(logic)) throw new IllegalArgumentException("logic must be AND or OR");
        List<NutritionScaleDefinition.ModifierSpec> attributes=new ArrayList<>();
        for(var element:array(json,"attributes")) {
            var a=element.getAsJsonObject();
            knownFields(a,Set.of("$schema","attribute","amount","operation"),"attributes[]");
            attributes.add(new NutritionScaleDefinition.ModifierSpec(new ResourceLocation(a.get("attribute").getAsString()),
                    number(a,"amount",0),NutritionScaleDefinition.parseOperation(string(a,"operation","addition"))));
        }
        List<Status> effects=new ArrayList<>();
        for(var element:array(json,"effects")) {
            var e=element.getAsJsonObject(); int amplifier=e.has("amplifier")?e.get("amplifier").getAsInt():0;
            knownFields(e,Set.of("$schema","effect","amplifier"),"effects[]");
            if(amplifier<0 || amplifier>255) throw new IllegalArgumentException("amplifier must be 0..255");
            effects.add(new Status(new ResourceLocation(e.get("effect").getAsString()),amplifier));
        }
        return new Rule(id,string(json,"name",id.getPath()),string(json,"name_key",""),List.copyOf(conditions),logic,
                List.copyOf(attributes),List.copyOf(effects),string(json,"advancement",""),json.has("world_conditions")?EffectCondition.parse(json.getAsJsonObject("world_conditions")):null);
    }
    private static void knownFields(JsonObject json,Set<String> allowed,String location) {
        for(String key:json.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown "+location+" setting: "+key);
    }
    private static JsonArray array(JsonObject j,String key) { return j.has(key)?j.getAsJsonArray(key):new JsonArray(); }
    private static String string(JsonObject j,String key,String fallback) { return j.has(key)?j.get(key).getAsString():fallback; }
    private static boolean bool(JsonObject j,String key,boolean fallback) { return j.has(key)?j.get(key).getAsBoolean():fallback; }
    private static double number(JsonObject j,String key,double fallback) {
        double value=j.has(key)?j.get(key).getAsDouble():fallback;
        if(!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite "+key);return value;
    }
    public record Status(ResourceLocation effect,int amplifier) {}
    public record Condition(List<String> nutrients,String match,double min,double max,boolean percent,boolean minInclusive,boolean maxInclusive) {
        public int matches(IFoodTolerance cap) {
            int hits=0;double total=0;
            Set<ResourceLocation> visited=new HashSet<>();
            for(String key:nutrients) {
                var scale=NutritionScaleManager.getInstance().get(key);
                 
                if(scale==null) return 0;
                 
                if(!visited.add(scale.id()))continue;
                double value=cap.getNutrientValue(scale.nutrientKey());
                if(percent) value=value/scale.maxValue()*100;
                total+=value;if(inRange(value))hits++;
            }
            return switch(match) {
                case "all" -> hits==visited.size()?1:0;
                case "any" -> hits>0?1:0;
                case "none" -> hits==0?1:0;
                case "average" -> !visited.isEmpty()&&inRange(total/visited.size())?1:0;
                case "every" -> hits;
                default -> 0;
            };
        }
        private boolean inRange(double value) { return (minInclusive?value>=min:value>min)&&(maxInclusive?value<=max:value<max); }
    }
    public record Rule(ResourceLocation id,String name,String nameKey,List<Condition> conditions,String logic,
                       List<NutritionScaleDefinition.ModifierSpec> attributes,List<Status> effects,String advancement,EffectCondition worldConditions) {
        public Rule(ResourceLocation id,String name,String nameKey,List<Condition> conditions,String logic,List<NutritionScaleDefinition.ModifierSpec> attributes,List<Status> effects,String advancement){this(id,name,nameKey,conditions,logic,attributes,effects,advancement,null);}
        public int multiplier(IFoodTolerance cap) {
            int result=0;
            for(var c:conditions) { int hits=c.matches(cap);if(logic.equals("AND") && hits==0)return 0;
                if(hits>0)result+=c.match.equals("every")?hits:1; }
             
            if(logic.equals("AND")) return Math.max(1,conditions.stream().filter(c->c.match.equals("every")).mapToInt(c->c.matches(cap)).sum());
            return result==0?0:Math.max(1,conditions.stream().filter(c->c.match.equals("every")).mapToInt(c->c.matches(cap)).sum());
        }
        public net.minecraft.network.chat.Component displayName() { return com.lumbra.ldiediet.util.PackText.of(nameKey,name); }
    }
}
