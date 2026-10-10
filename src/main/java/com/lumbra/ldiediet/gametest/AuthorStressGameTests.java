package com.lumbra.ldiediet.gametest;

import com.google.gson.*;
import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.api.PackApi;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

 
@GameTestHolder("minecraft") @PrefixGameTestTemplate(false)
public final class AuthorStressGameTests {
    private static final JsonArray RESULTS=new JsonArray();
    private static final String SPEED="minecraft:generic.movement_speed";
    interface Experiment { String run(ServerPlayer player,IFoodTolerance cap) throws Exception; }
    static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }
    static ResourceLocation id(String path) { return new ResourceLocation("stress",path); }
    @SuppressWarnings("unchecked") static Map<ResourceLocation,NutritionRuleManager.Rule> rules() throws Exception {
        var f=NutritionRuleManager.class.getDeclaredField("rules");f.setAccessible(true);
        return (Map<ResourceLocation,NutritionRuleManager.Rule>)f.get(NutritionRuleManager.INSTANCE);
    }
    static void rule(String id,String body) throws Exception { rules().put(id(id),NutritionRuleManager.parse(id(id),json(body))); }
    static void scale(String name,String body) {
        var manager=NutritionScaleManager.getInstance();var nbt=manager.serializeToNBT();
        var list=nbt.getList("definitions",Tag.TAG_COMPOUND);var entry=new CompoundTag();
        entry.putString("id","stress:"+name);entry.put("definition",com.lumbra.ldiediet.util.JsonNbt.encode(json(body)));list.add(entry);
        manager.deserializeFromNBT(nbt);
    }
    static FoodGroupDefinition group(String name,String effects) throws Exception {
        var parse=FoodGroupManager.class.getDeclaredMethod("parseGroup",ResourceLocation.class,JsonObject.class);parse.setAccessible(true);
        var g=(FoodGroupDefinition)parse.invoke(FoodGroupManager.getInstance(),id(name),json("{\"name\":\""+name+"\",\"items\":[\"minecraft:bread\"],\"threshold\":1,\"require_unique\":false,\"hide_activation_message\":true,\"effects\":"+effects+"}"));
        FoodGroupManager.getInstance().getGroupsMap().put(g.getId(),g);return g;
    }
    static String attr(double amount,String op) { return "{\"attribute\":\""+SPEED+"\",\"amount\":"+amount+",\"operation\":\""+op+"\"}"; }
    static String condition(String names,String match,double min,double max) { return "{\"nutrients\":["+names+"],\"match\":\""+match+"\",\"min\":"+min+",\"max\":"+max+"}"; }
    static String effect(double amount,String extra) { return "[{\"duration_type\":\"until_death\",\"effect_type\":\"attribute\",\"effect_id\":\""+SPEED+"\",\"value\":"+amount+extra+"}]"; }
    static void require(boolean condition,String observed) { if(!condition)throw new AssertionError(observed); }
    static void probe(GameTestHelper h,String name,String expected,Experiment experiment) throws Exception {
        var manager=NutritionScaleManager.getInstance();var scales=manager.serializeToNBT();
        var oldRules=new TreeMap<>(rules());var groups=FoodGroupManager.getInstance().getGroupsMap();var oldGroups=new HashMap<>(groups);
        var file=FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json");byte[] config=Files.readAllBytes(file);
        var mode=LDieDietServerConfig.DECAY_MODE.get();int history=LDieDietServerConfig.REWARD_HISTORY_LENGTH.get();
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var cap=CapabilityHandler.get(p);
        JsonObject result=new JsonObject();result.addProperty("scenario",name);result.addProperty("expected",expected);
        long start=System.nanoTime();
        try {
            rules().clear();groups.clear();cap.clearNutrients();cap.clearActiveEffects();cap.clearPermanentRewards();cap.clearHistory();p.removeAllEffects();
            result.addProperty("observed",experiment.run(p,cap));result.addProperty("outcome","SUPPORTED");
        } catch(AssertionError e) { result.addProperty("outcome","MISMATCH");result.addProperty("observed",e.getMessage()); }
        catch(Exception e) { result.addProperty("outcome","ERROR");result.addProperty("observed",e.toString()); }
        finally {
            result.addProperty("experiment_ms",(System.nanoTime()-start)/1e6);RESULTS.add(result);LDieDiet.LOGGER.info("[AUTHOR STRESS] {}",result);
            rules().clear();rules().putAll(oldRules);groups.clear();groups.putAll(oldGroups);manager.deserializeFromNBT(scales);
            Files.write(file,config);DietConfigManager.loadConfig(h.getLevel().getServer());
            FixtureConfig.set(LDieDietServerConfig.DECAY_MODE,mode);FixtureConfig.set(LDieDietServerConfig.REWARD_HISTORY_LENGTH,history);
            LDieDietGameTests.removePlayer(p);
        }
    }
    @GameTest(template="bastion/blocks/air",batch="zz_author_stress",timeoutTicks=400)
    public static void customizationMatrix(GameTestHelper h) throws Exception {
        if(!Boolean.getBoolean("ldiediet.authorStress")){h.succeed();return;}
        RESULTS.asList().clear();
        probe(h,"antidote_food","One food reduces toxin and raises proteins",(p,c)->{
            scale("toxin","{\"max_value\":100}");c.setNutrientValue("stress:toxin",50);c.setNutrientValue("proteins",50);
            var file=FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json");
            Files.writeString(file,"{\"overrides\":{\"minecraft:bread\":{\"food_value\":1,\"nutrients\":{\"stress:toxin\":-10,\"proteins\":5}}}}");DietConfigManager.loadConfig(p.server);
            var meal=com.lumbra.ldiediet.consumption.ConsumptionSnapshot.captureIfConsumable(p,new ItemStack(Items.BREAD));
            com.lumbra.ldiediet.event.FoodEatingHandler.complete(p,meal);
            require(c.getNutrientValue("stress:toxin")==40&&c.getNutrientValue("proteins")==55,"toxin="+c.getNutrientValue("stress:toxin")+", proteins="+c.getNutrientValue("proteins"));return "toxin 50 -> 40; proteins 50 -> 55";
        });
        probe(h,"mixed_scale_units","Average of 80% on a 10,000 scale and 40% on a 1 scale is 60%",(p,c)->{
            scale("blood","{\"max_value\":10000}");scale("sanity","{\"max_value\":1}");c.setNutrientValue("stress:blood",8000);c.setNutrientValue("stress:sanity",.4f);
            var rule=NutritionRuleManager.parse(id("mixed"),json("{\"conditions\":["+condition("\"stress:blood\",\"stress:sanity\"","average",59.99,60.01)+"]}"));
            require(rule.multiplier(c)==1,"Percentage normalization failed");return "Correct normalized average";
        });
        probe(h,"boolean_branches","(proteins>=60 AND vegetables>=60) OR (fruits>=60 AND sugars>=60)",(p,c)->{
            rule("branch","{\"logic\":\"OR\",\"conditions\":["+condition("\"proteins\",\"vegetables\"","all",60,100)+","+condition("\"fruits\",\"sugars\"","all",60,100)+"]}");
            c.setNutrientValue("proteins",80);c.setNutrientValue("fruits",80);require(rules().get(id("branch")).multiplier(c)==0,"Mixed halves activated OR branch");
            c.setNutrientValue("sugars",80);require(rules().get(id("branch")).multiplier(c)==1,"Second full branch failed");return "Both false and true branches correct";
        });
        probe(h,"duplicate_every","Repeated nutrient ID must not double a per-nutrient reward",(p,c)->{
            c.setNutrientValue("proteins",80);
            try { rule("duplicate","{\"conditions\":["+condition("\"proteins\",\"proteins\"","every",60,100)+"]}"); }
            catch(IllegalArgumentException e){return "Duplicate rejected";}
            int count=rules().get(id("duplicate")).multiplier(c);require(count==1,"Duplicate ID counted "+count+" times; JSON schema rejects it, runtime accepts it");return "Deduplicated";
        });
        probe(h,"percent_penalty_stack","+50% base then -20% total gives 120% base speed",(p,c)->{
            c.setNutrientValue("proteins",80);rule("stack","{\"conditions\":["+condition("\"proteins\"","all",60,100)+"],\"attributes\":["+attr(.5,"multiply_base")+","+attr(-.2,"multiply_total")+"]}");
            PackApi.refresh(p);double ratio=p.getAttributeValue(Attributes.MOVEMENT_SPEED)/p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();require(Math.abs(ratio-1.2)<.00001,"ratio="+ratio);return "ratio="+ratio;
        });
        probe(h,"quota_hot_rebalance","Changing an active quota's amount updates it immediately",(p,c)->{
            c.setNutrientValue("proteins",80);String head="{\"conditions\":["+condition("\"proteins\"","all",60,100)+"],\"attributes\":[";
            rule("live",head+attr(.1,"addition")+"]}");PackApi.refresh(p);rule("live",head+attr(.9,"addition")+"]}");PackApi.refresh(p);
            require(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().anyMatch(m->m.getName().startsWith("ldiediet_quota:")&&m.getAmount()==.9),"Old quota amount retained");return "0.1 -> 0.9 applied";
        });
        probe(h,"group_hot_rebalance","Changing the amount of an already active group updates it",(p,c)->{
            var g=group("live",effect(.1,""));RewardHandler.activateGroupReward(p,c,g);group("live",effect(.9,""));RewardHandler.tickActiveEffects(p);
            double value=c.getActiveEffects().get(0).getValue();require(value==.9,"Existing group still uses "+value+" after definition becomes 0.9; reward stores an activation snapshot");return "Updated";
        });
        probe(h,"waiting_prerequisite","A waiting, inactive group must not satisfy requires_active_groups",(p,c)->{
            var a=group("a",effect(.1,",\"conditions\":{\"temperature\":{\"min\":10000}}"));RewardHandler.activateGroupReward(p,c,a);
            require(c.getActiveEffects().stream().noneMatch(ActiveGroupEffect::isCurrentlyActive),"Fixture A should wait");
            var b=group("b",effect(.2,",\"requires_active_groups\":[\"stress:a\"]"));RewardHandler.activateGroupReward(p,c,b);
            boolean active=c.getActiveEffects().stream().anyMatch(e->e.getGroupId().equals("stress:b")&&e.isCurrentlyActive());require(!active,"B activates although A is WAITING, not active");return "Blocked correctly";
        });
        probe(h,"cyclic_unlock","A requires B, B requires A: neither should bootstrap without a seed",(p,c)->{
            var a=group("a",effect(.1,",\"requires_active_groups\":[\"stress:b\"]"));var b=group("b",effect(.2,",\"requires_active_groups\":[\"stress:a\"]"));
            c.addHistory(Items.BREAD);RewardHandler.checkAndApplyRewards(p,c);
            long active=c.getActiveEffects().stream().filter(ActiveGroupEffect::isCurrentlyActive).count();require(active==0,"Cycle bootstrapped: "+active+" active effect(s) from one bread meal, without administrative grants");return "Cycle remains locked";
        });
        probe(h,"overlapping_potions","Removing quota/group layers restores an external weaker potion",(p,c)->{
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,1000,0));c.setNutrientValue("proteins",80);
            rule("potion","{\"conditions\":["+condition("\"proteins\"","all",60,100)+"],\"effects\":[{\"effect\":\"minecraft:strength\",\"amplifier\":2}]}");
            var g=group("strong","[{\"duration_type\":\"until_death\",\"effect_type\":\"mob_effect\",\"effect_id\":\"minecraft:strength\",\"amplifier\":4}]");
            PackApi.refresh(p);RewardHandler.activateGroupReward(p,c,g);require(p.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier()==4,"Strongest layer failed");
            c.clearActiveEffects();RewardHandler.reconcileApplied(p,c);require(p.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier()==2,"Quota layer did not return");
            rules().clear();PackApi.refresh(p);require(p.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier()==0,"External potion lost");return "Amplifiers 4 -> 2 -> 0";
        });
        probe(h,"instant_healing_quota","Measure what an instant-health quota actually does",(p,c)->{
            c.setNutrientValue("proteins",80);p.setHealth(1);
            rule("heal","{\"conditions\":["+condition("\"proteins\"","all",60,100)+"],\"effects\":[{\"effect\":\"minecraft:instant_health\",\"amplifier\":0}]}");PackApi.refresh(p);
            for(int i=0;i<5;i++)p.getEffect(MobEffects.HEAL).tick(p,()->{});
            return "Health after five effect ticks="+p.getHealth()+"; this is continuous healing, not one-shot reward";
        });
        probe(h,"512_simultaneous_rules","512 independently owned modifiers coexist, update, then remove",(p,c)->{
            c.setNutrientValue("proteins",80);for(int i=0;i<512;i++)rule("r"+i,"{\"conditions\":["+condition("\"proteins\"","all",60,100)+"],\"attributes\":["+attr(.0001,"addition")+"]}");
            long t=System.nanoTime();for(int i=0;i<10;i++)NutritionRuleHandler.refresh(p,c);double ms=(System.nanoTime()-t)/1e7;
            long count=p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().filter(m->m.getName().startsWith("ldiediet_quota:")).count();require(count==512,"Only "+count+" modifiers");
            rules().clear();NutritionRuleHandler.refresh(p,c);require(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().noneMatch(m->m.getName().startsWith("ldiediet_quota:")),"Removed rules left modifiers");return "512 modifiers; mean refresh ms="+ms+" (one player, local machine, not a multiplayer benchmark)";
        });
        probe(h,"256_scales_snapshot","256 custom scales survive server/client snapshot and player NBT",(p,c)->{
            for(int i=0;i<256;i++){scale("n"+i,"{\"max_value\":10000,\"name\":\"Кровь 血 🩸\",\"color\":\"#AABBCC\"}");c.setNutrientValue("stress:n"+i,1234);}
            var bytes=com.lumbra.ldiediet.network.SnapshotTransport.encode(NutritionScaleManager.getInstance().serializeToNBT());
            var restored=new FoodToleranceCapability();restored.deserializeNBT(c.serializeNBT());require(restored.getAllNutrients().size()==256,"Player snapshot lost scales");
            var client=NutritionScaleManager.getClientInstance();var old=client.serializeToNBT();try {client.deserializeFromNBT(NutritionScaleManager.getInstance().serializeToNBT());require(client.get("stress:n255")!=null,"Client snapshot missing final scale");}finally{client.deserializeFromNBT(old);}return "256 scales retained; encoded definitions bytes="+bytes.length+"; UI rendering not tested";
        });
        probe(h,"100_scales_hunger_decay","100 populated scales still visibly decay after losing one hunger point",(p,c)->{
            float next=com.lumbra.ldiediet.math.NutritionDecay.hunger(50,100,1,100,15,1);
            float alternative=com.lumbra.ldiediet.math.NutritionDecay.hunger(50,100,1,100,0,1);
            require(alternative<50,"Even zero diversity penalty failed");
            require(next<50,"Result="+next+"; diversity penalty plus float precision stops decay. Explicit penalty=0 works: "+alternative);return "Remaining="+next;
        });
        probe(h,"100_unique_nbt_meals","100 NBT variants of the same item count as 100 unique meals",(p,c)->{
            FixtureConfig.set(LDieDietServerConfig.REWARD_HISTORY_LENGTH,100);var g=new FoodGroupDefinition(id("variety"),"Variety","","-o","",List.of("minecraft:bread"),List.of(),100,true,"","white",true);
            for(int i=0;i<100;i++)c.addHistory(new FoodKey(new ResourceLocation("minecraft:bread"),"stress:v"+i));
            int count=RewardHandler.countEligibleHistory(c.getEatingHistory(),g,0);require(count==100,"count="+count);return "100 distinct meal variants retained";
        });
        probe(h,"1000_meal_quest","Measure maximum progress for a lifetime 1000-meal quest",(p,c)->{
            FixtureConfig.set(LDieDietServerConfig.REWARD_HISTORY_LENGTH,100);var g=group("quest","[]");for(int i=0;i<1000;i++)c.addHistory(Items.BREAD);
            int count=RewardHandler.countEligibleHistory(c.getEatingHistory(),g,0);require(count==1000,"Only last "+count+" meals retained; use external quest counters for lifetime totals");return "1000 meals counted";
        });
        probe(h,"unknown_dimension_condition","Nether-only condition must not silently apply in Overworld",(p,c)->{
            try {rule("dimension","{\"conditions\":[{\"nutrients\":[\"proteins\"],\"min\":0,\"max\":100,\"dimension\":\"minecraft:the_nether\"}]}");}
            catch(IllegalArgumentException e){return "Unsupported field rejected";}
            require(rules().get(id("dimension")).multiplier(c)==0,"Unknown dimension field silently ignored; requires scripting, JSON schema catches unknown field");return "Dimension constraint applied";
        });
        probe(h,"extreme_attribute_magnitude","Huge positive and negative modifiers remain finite",(p,c)->{
            c.setNutrientValue("proteins",80);rule("huge","{\"conditions\":["+condition("\"proteins\"","all",60,100)+"],\"attributes\":["+attr(1e300,"multiply_total")+","+attr(1e300,"multiply_total")+","+attr(-1e300,"addition")+"]}");PackApi.refresh(p);
            double speed=p.getAttributeValue(Attributes.MOVEMENT_SPEED);require(Double.isFinite(speed),"Non-finite speed="+speed);return "Vanilla attribute range clamps final speed to "+speed+"; huge numbers do not mean unlimited speed";
        });
        probe(h,"invalid_maximum","Negative maximum should be rejected",(p,c)->{
            try {var scale=NutritionScaleDefinition.parse(id("negative"),json("{\"max_value\":-100}"));throw new AssertionError("Accepted and silently replaced with max_value="+scale.maxValue());}
            catch(IllegalArgumentException expected){return "Rejected";}
        });
        probe(h,"nan_and_unknown_references","NaN rejected; missing nutrient never grants NONE reward",(p,c)->{
            boolean rejected=false;try{NutritionRuleManager.parse(id("nan"),json("{\"conditions\":[{\"nutrients\":[\"proteins\"],\"min\":\"NaN\"}]}"));}catch(IllegalArgumentException e){rejected=true;}
            require(rejected,"NaN accepted");var rule=NutritionRuleManager.parse(id("missing"),json("{\"conditions\":["+condition("\"missing:ghost\"","none",0,100)+"]}"));require(rule.multiplier(c)==0,"Missing nutrient granted reward");return "Both fail safely";
        });
        probe(h,"100_mutually_dependent_groups","Validator detects a 100-node prerequisite cycle without hanging",(p,c)->{
            for(int i=0;i<100;i++)group("chain"+i,effect(.001,",\"requires_active_groups\":[\"stress:chain"+((i+1)%100)+"\"]"));
            long t=System.nanoTime();var diagnostics=AuthorDiagnostics.validate(p.server);long count=diagnostics.stream().filter(s->s.contains("cyclic")).count();require(count>=1,"Cycle missed");return "Cycle warnings="+count+", validation ms="+(System.nanoTime()-t)/1e6;
        });
        probe(h,"aliased_every","proteins and ldiediet:proteins are one physical scale",(p,c)->{
            c.setNutrientValue("proteins",80);rule("aliases","{\"conditions\":["+condition("\"proteins\",\"ldiediet:proteins\"","every",60,100)+"]}");
            int n=rules().get(id("aliases")).multiplier(c);require(n==1,"Same scale counted "+n+" times via aliases; uniqueItems JSON validation cannot catch this");return "Aliases deduplicated";
        });
        probe(h,"400_active_groups","400 active group rewards survive repeated tick reconciliation",(p,c)->{
            for(int i=0;i<400;i++){
                var g=group("active"+i,effect(.00001,""));var slot=ActiveGroupEffect.waiting(g.getId().toString(),0,g.getEffects().get(0));slot.start(Set.of());c.addActiveEffect(slot);
            }
            RewardHandler.tickActiveEffects(p);long t=System.nanoTime();for(int i=0;i<10;i++)RewardHandler.tickActiveEffects(p);double ms=(System.nanoTime()-t)/1e7;
            long n=p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().filter(m->m.getName().startsWith("ldiediet_reward_")).count();require(n==400,"Only "+n+" applied");
            return "400 modifiers; mean tick reconciliation ms="+ms+" for one player; 20 calls/sec in normal play";
        });
        probe(h,"amplifiers_127_128_255","Very high potion amplifiers survive owned-source NBT",(p,c)->{
            for(int amplifier:new int[]{127,128,255}) {
                c.getMobEffectSources().put(p,"stress:level",new MobEffectInstance(MobEffects.DAMAGE_BOOST,60,amplifier));c.getMobEffectSources().reconcile(p);
                int actual=p.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier();require(actual==amplifier,"Requested="+amplifier+", actual="+actual);
            }return "All 3 amplitudes retained";
        });
        probe(h,"integer_limit_random_timer","Random phase lasting up to Integer.MAX_VALUE ticks starts and persists",(p,c)->{
            var g=group("timer","[{\"duration_type\":\"until_death_intermittent\",\"effect_type\":\"attribute\",\"effect_id\":\""+SPEED+"\",\"value\":0.1,\"interval_active_ticks\":[1,2147483647],\"interval_pause_ticks\":[1,2147483647]}]");
            for(int i=0;i<100;i++){var slot=ActiveGroupEffect.waiting(g.getId().toString(),0,g.getEffects().get(0));slot.start(Set.of());var copy=ActiveGroupEffect.fromNBT(slot.serializeNBT());require(copy.isCurrentlyActive(),"Overflowed random timer");}return "100 rolls and NBT restores passed";
        });
        probe(h,"pulse_reward","One tick active, one tick paused remains intermittent after persistence",(p,c)->{
            var g=group("pulse","[{\"duration_type\":\"until_death_intermittent\",\"effect_type\":\"attribute\",\"effect_id\":\""+SPEED+"\",\"value\":0.1,\"interval_active_ticks\":[1,1],\"interval_pause_ticks\":[1,1]}]");
            var slot=ActiveGroupEffect.waiting(g.getId().toString(),0,g.getEffects().get(0));slot.start(Set.of());int active=0;
            for(int i=0;i<100;i++){slot.tick(i);if(slot.isCurrentlyActive())active++;if(i==49)slot=ActiveGroupEffect.fromNBT(slot.serializeNBT());}
            require(active==50,"Active ticks="+active);return "50/100 active ticks, including save/restore";
        });
        probe(h,"missing_climate_mods","Requiring an absent season/body-temperature integration fails closed",(p,c)->{
            var season=new EffectCondition("AND",null,null,null,"winter");
            var body=new EffectCondition("AND",null,null,"hot",null);
            require(!season.evaluate(p)&&!body.evaluate(p),"Absent optional dependency granted reward");return "Both conditions inactive without their optional mods";
        });
        probe(h,"removed_scale_cleanup","Deleting a custom scale removes dependent quota and attribute",(p,c)->{
            scale("temporary","{\"max_value\":100}");c.setNutrientValue("stress:temporary",80);
            rule("temporary","{\"conditions\":["+condition("\"stress:temporary\"","all",60,100)+"],\"attributes\":["+attr(.1,"addition")+"]}");PackApi.refresh(p);
            var manager=NutritionScaleManager.getInstance();var nbt=manager.serializeToNBT();var list=nbt.getList("definitions",Tag.TAG_COMPOUND);
            list.removeIf(e->((CompoundTag)e).getString("id").equals("stress:temporary"));manager.deserializeFromNBT(nbt);PackApi.refresh(p);
            require(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().noneMatch(m->m.getName().startsWith("ldiediet_quota:")),"Deleted scale left reward");return "Dependent modifier removed; stored value retained for possible re-add";
        });
        var report=new JsonObject();report.addProperty("version",net.minecraftforge.fml.ModList.get().getModContainerById("ldiediet").orElseThrow().getModInfo().getVersion().toString());report.addProperty("scope","Isolated Forge server; runtime production methods, synthetic configurations. No FreshCraft files or production datapack modified.");report.add("scenarios",RESULTS);
        Files.writeString(FMLPaths.GAMEDIR.get().resolve("author-stress-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        h.succeed();  
    }
}
