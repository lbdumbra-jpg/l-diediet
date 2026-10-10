package com.lumbra.ldiediet.data;

import com.google.gson.*;
import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.util.NbtMatcher;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

 
public final class FoodStageProfiles extends SimpleJsonResourceReloadListener {
    public static final FoodStageProfiles SERVER = new FoodStageProfiles(), CLIENT = new FoodStageProfiles();
    private Map<ResourceLocation, JsonObject> definitions = Map.of();
    private List<Profile> profiles = List.of();
    private long revision;
    private FoodStageProfiles() { super(new Gson(), "food_stage_profiles"); }
    public long revision() { return revision; }
    public boolean hasProfiles(){return !profiles.isEmpty();}
    public record Policy(List<Integer> thresholds, List<Double> hunger, List<Double> saturation,
                         List<Double> chewing, List<Double> nutrients, List<Boolean> buffs,
                         Set<String> allowedEffects, Set<String> blockedEffects,double minimumHunger) {
        public Policy {
            thresholds=List.copyOf(thresholds);hunger=List.copyOf(hunger);saturation=List.copyOf(saturation);
            chewing=List.copyOf(chewing);nutrients=List.copyOf(nutrients);buffs=List.copyOf(buffs);
            allowedEffects=Set.copyOf(allowedEffects);blockedEffects=Set.copyOf(blockedEffects);
        }
        public int stage(float value) { for(int i=3;i>=0;i--)if(value>=thresholds.get(i))return i+1;return 0; }
        public boolean blocks(MobEffect effect,int stage) {
            String id=String.valueOf(ForgeRegistries.MOB_EFFECTS.getKey(effect));
            if(blockedEffects.contains(id))return true;
            if(allowedEffects.contains(id))return false;
            return !buffs.get(stage)&&effect.getCategory()==net.minecraft.world.effect.MobEffectCategory.BENEFICIAL;
        }
    }
    private record Profile(ResourceLocation id,int priority,List<String> items,CompoundTag nbt,JsonObject values) {
        boolean matches(ItemStack stack) {
            boolean item=items.stream().anyMatch(s->s.startsWith("#")
                    ? stack.is(TagKey.create(Registries.ITEM,new ResourceLocation(s.substring(1))))
                    : String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())).equals(s));
            return item&&(nbt==null||NbtMatcher.matches(stack.getTag(),nbt));
        }
    }
    public static Policy defaults() {
        var hunger=new ArrayList<Double>();for(int i=0;i<5;i++)hunger.add(LDieDietServerConfig.getMultiplier(i));
        var saturation=LDieDietServerConfig.ENABLE_SATURATION_PENALTY.get()?hunger:List.of(1d,1d,1d,1d,1d);
        return new Policy(com.lumbra.ldiediet.math.StageThresholds.validated(LDieDietServerConfig.STAGE_THRESHOLDS.get()),
                hunger,saturation,new ArrayList<>(LDieDietServerConfig.CHEWING_MULTIPLIERS.get()),
                new ArrayList<>(LDieDietServerConfig.NUTRIENT_MULTIPLIERS.get()),new ArrayList<>(LDieDietServerConfig.ALLOW_FOOD_BUFFS.get()),LDieDietServerConfig.ALLOWED_FOOD_EFFECTS.get().stream().map(s->new ResourceLocation(s).toString()).collect(java.util.stream.Collectors.toSet()),LDieDietServerConfig.BLOCKED_FOOD_EFFECTS.get().stream().map(s->new ResourceLocation(s).toString()).collect(java.util.stream.Collectors.toSet()),LDieDietServerConfig.MIN_HUNGER_RESTORED.get());
    }
    public Policy resolve(ItemStack stack) {
        Policy base=defaults();
        for(var p:profiles)if(p.matches(stack))return parsePolicy(p.values,base);
        return base;
    }
    public static Policy forStack(ItemStack stack,boolean client) { return (client?CLIENT:SERVER).resolve(stack); }
    @Override protected void apply(Map<ResourceLocation,JsonElement> input,ResourceManager resources,ProfilerFiller profiler) {
        AuthorDiagnostics.clearParseIssues("food_stage_profiles/");load(input,false);
    }
    private void load(Map<ResourceLocation,? extends JsonElement> input,boolean client) {
        var parsed=new ArrayList<Profile>();var raw=new TreeMap<ResourceLocation,JsonObject>();
        input.forEach((id,element)->{
            try {
                var j=element.getAsJsonObject();
                for(String field:j.keySet())if(!Set.of("$schema","priority","items","nbt","stages").contains(field))throw new IllegalArgumentException("Unknown profile setting: "+field);
                if(j.has("priority")){double priority=j.get("priority").getAsDouble();if(!Double.isFinite(priority)||priority!=Math.rint(priority)||priority<Integer.MIN_VALUE||priority>Integer.MAX_VALUE)throw new IllegalArgumentException("priority must be an integer");}
                var items=strings(j,"items");if(items.isEmpty())throw new IllegalArgumentException("items must not be empty");
                for(String item:items)new ResourceLocation(item.startsWith("#")?item.substring(1):item);
                CompoundTag nbt=j.has("nbt")?TagParser.parseTag(j.get("nbt").getAsString()):null;
                var values=j.has("stages")?j.getAsJsonObject("stages"):new JsonObject();
                parsePolicy(values,new Policy(List.of(5,10,15,20),List.of(1d,.75,.5,.25,.25),List.of(1d,.75,.5,.25,.25),List.of(1d,1.5,2d,2.5,3d),List.of(1d,1d,1d,.5,0d),List.of(true,true,false,false,false),Set.of(),Set.of(),.5));
                parsed.add(new Profile(id,j.has("priority")?j.get("priority").getAsInt():0,List.copyOf(items),nbt,values.deepCopy()));raw.put(id,j.deepCopy());
            }catch(Exception e){if(!client)AuthorDiagnostics.parseIssue("food_stage_profiles/"+id,e.getMessage());}
        });
        parsed.sort(Comparator.comparingInt(Profile::priority).reversed().thenComparing(Profile::id));
        profiles=List.copyOf(parsed);definitions=Map.copyOf(raw);revision++;
    }
    private static Policy parsePolicy(JsonObject j,Policy base) {
        var thresholds=base.thresholds;
        if(j.has("thresholds")) {
            var result=new ArrayList<Integer>();for(var e:j.getAsJsonArray("thresholds")) {
                double n=e.getAsDouble();if(!Double.isFinite(n)||n!=Math.rint(n)||n<=0||n>Integer.MAX_VALUE)throw new IllegalArgumentException("thresholds must be positive integers");result.add((int)n);
            }
            if(result.size()!=4)throw new IllegalArgumentException("thresholds must contain four values");
            for(int i=1;i<4;i++)if(result.get(i)<=result.get(i-1))throw new IllegalArgumentException("thresholds must strictly increase");thresholds=List.copyOf(result);
        }
        var buffs=base.buffs;if(j.has("allow_food_buffs")){var b=new ArrayList<Boolean>();for(var e:j.getAsJsonArray("allow_food_buffs")){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("allow_food_buffs must be boolean");b.add(e.getAsBoolean());}if(b.size()!=5)throw new IllegalArgumentException("allow_food_buffs must contain five values");buffs=List.copyOf(b);}
        double minimum=j.has("minimum_hunger_restored")?j.get("minimum_hunger_restored").getAsDouble():base.minimumHunger;
        if(!Double.isFinite(minimum)||minimum<0||minimum>20)throw new IllegalArgumentException("minimum_hunger_restored must be 0..20");
        for(String field:j.keySet())if(!Set.of("$schema","thresholds","hunger_multipliers","saturation_multipliers","chewing_multipliers","nutrient_multipliers","allow_food_buffs","allowed_food_effects","blocked_food_effects","minimum_hunger_restored").contains(field))throw new IllegalArgumentException("Unknown stage setting: "+field);
        return new Policy(thresholds,numbers(j,"hunger_multipliers",base.hunger,0,1),numbers(j,"saturation_multipliers",base.saturation,0,1),
                numbers(j,"chewing_multipliers",base.chewing,1,100),numbers(j,"nutrient_multipliers",base.nutrients,0,100),buffs,
                j.has("allowed_food_effects")?effectIds(j,"allowed_food_effects"):base.allowedEffects,j.has("blocked_food_effects")?effectIds(j,"blocked_food_effects"):base.blockedEffects,minimum);
    }
    private static List<Double> numbers(JsonObject j,String key,List<Double> fallback,double min,double max) {
        if(!j.has(key))return fallback;var out=new ArrayList<Double>();
        for(var e:j.getAsJsonArray(key)){double n=e.getAsDouble();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException(key+" outside "+min+".."+max);out.add(n);}
        if(out.size()!=5)throw new IllegalArgumentException(key+" must contain five values");return List.copyOf(out);
    }
    private static List<String> strings(JsonObject j,String key){var result=new ArrayList<String>();if(j.has(key))for(var e:j.getAsJsonArray(key))result.add(e.getAsString());return result;}
    private static Set<String> effectIds(JsonObject j,String key){var result=new TreeSet<String>();for(var s:strings(j,key))result.add(new ResourceLocation(s).toString());return result;}
    public CompoundTag serialize() {var tag=new CompoundTag();definitions.forEach((id,j)->tag.put(id.toString(),com.lumbra.ldiediet.util.JsonNbt.encode(j)));return tag;}
    public void deserialize(CompoundTag tag){var data=new TreeMap<ResourceLocation,JsonElement>();for(var id:tag.getAllKeys())data.put(new ResourceLocation(id),com.lumbra.ldiediet.util.JsonNbt.decode(tag.getCompound(id)));load(data,true);}
    public List<String> validate() {
        var issues=new ArrayList<String>();
        for(String id:union(defaults().allowedEffects,defaults().blockedEffects))if(!ForgeRegistries.MOB_EFFECTS.containsKey(new ResourceLocation(id)))issues.add("serverconfig.FoodEffects: unavailable effect "+id);
        for(var p:profiles){var policy=parsePolicy(p.values,defaults());
            if(policy.thresholds.get(3)>com.lumbra.ldiediet.math.FormulaManager.getMonotonyCap())issues.add("food_stage_profiles/"+p.id+": threshold exceeds monotony_cap");
            for(String id:union(policy.allowedEffects,policy.blockedEffects))if(!ForgeRegistries.MOB_EFFECTS.containsKey(new ResourceLocation(id)))issues.add("food_stage_profiles/"+p.id+": unavailable effect "+id);
            if(ForgeRegistries.ITEMS.getValues().stream().noneMatch(item->p.matches(new ItemStack(item)))) {
                 
                var selector=new Profile(p.id,p.priority,p.items,null,p.values);
                if(ForgeRegistries.ITEMS.getValues().stream().noneMatch(item->selector.matches(new ItemStack(item))))issues.add("food_stage_profiles/"+p.id+": no installed item matches selectors");
            }
        }return issues;
    }
    private static Set<String> union(Set<String> a,Set<String> b){var out=new HashSet<>(a);out.addAll(b);return out;}
}
