package com.lumbra.ldiediet.gametest;

import com.google.gson.*;
import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.api.*;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.util.JsonNbt;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

 
@GameTestHolder("minecraft") @PrefixGameTestTemplate(false)
public final class AuthorBoundaryGameTests {
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void chewingTimersUseEntityIdentityEvenWhenNumericIdsMatch(GameTestHelper h){
        var first=new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),0,0,0);
        var second=new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),0,0,0);second.setId(first.getId());
        h.assertTrue(first!=second&&first.equals(second),"Fixture must reproduce Minecraft entity ID equality");
        var a=new ItemStack(Items.APPLE);var b=new ItemStack(Items.APPLE);
        var oa=(com.lumbra.ldiediet.consumption.UseDurationOverride)(Object)a;var ob=(com.lumbra.ldiediet.consumption.UseDurationOverride)(Object)b;
        oa.ldiediet$setDuration(96);ob.ldiediet$setDuration(48);
        try{
            com.lumbra.ldiediet.consumption.ChewingTiming.remember(first,a);
            com.lumbra.ldiediet.consumption.ChewingTiming.clear(second);
            h.assertTrue(a.getUseDuration()==96,"Unrelated entity with shared ID cleared timer");
            com.lumbra.ldiediet.consumption.ChewingTiming.remember(second,b);
            com.lumbra.ldiediet.consumption.ChewingTiming.clear(first);
            h.assertTrue(a.getUseDuration()==32&&b.getUseDuration()==48,"One side reset the other side's timer");
            com.lumbra.ldiediet.consumption.ChewingTiming.clear(second);h.assertTrue(b.getUseDuration()==32,"Own timer not cleared");h.succeed();
        }finally{com.lumbra.ldiediet.consumption.ChewingTiming.clear(first);com.lumbra.ldiediet.consumption.ChewingTiming.clear(second);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void quotaAliasesCountOnePhysicalScaleAcrossEveryAllAndAverage(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try{
            var cap=CapabilityHandler.get(p);cap.setNutrientValue("proteins",80);cap.setNutrientValue("vegetables",20);
            var id=new ResourceLocation("test:unique_scales");
            var every=NutritionRuleManager.parse(id,json("{\"conditions\":[{\"nutrients\":[\"proteins\",\"proteins\",\"ldiediet:proteins\"],\"match\":\"every\",\"min\":60}]}"));
            h.assertTrue(every.multiplier(cap)==1,"One physical nutrient rewarded more than once");
            var average=NutritionRuleManager.parse(id,json("{\"conditions\":[{\"nutrients\":[\"proteins\",\"ldiediet:proteins\",\"vegetables\"],\"match\":\"average\",\"min\":49.9,\"max\":50.1}]}"));
            h.assertTrue(average.multiplier(cap)==1,"Alias biased normalized average");
            var all=NutritionRuleManager.parse(id,json("{\"conditions\":[{\"nutrients\":[\"proteins\",\"ldiediet:proteins\",\"vegetables\"],\"match\":\"all\"}]}"));
            h.assertTrue(all.multiplier(cap)==1,"ALL denominator still counts aliases");h.succeed();
        }finally{LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void quotaTyposAreRejectedAndReportedWithoutDroppingValidSiblings(GameTestHelper h)throws Exception {
        var rules=AuthorStressGameTests.rules();var original=new TreeMap<>(rules);var input=new TreeMap<ResourceLocation,JsonElement>();
        var valid=json("{\"conditions\":[{\"nutrients\":[\"proteins\"]}],\"world_conditions\":{\"dimensions\":[\"minecraft:the_nether\"]}}");
        input.put(new ResourceLocation("test:valid_quota"),valid);
        for(String path:List.of("condition","root","attribute","effect")){
            var body=valid.deepCopy();
            switch(path){
                case "condition"->body.getAsJsonArray("conditions").get(0).getAsJsonObject().addProperty("dimension","minecraft:the_nether");
                case "root"->body.addProperty("world_condition",true);
                case "attribute"->body.add("attributes",JsonParser.parseString("[{\"attribute\":\"minecraft:generic.max_health\",\"ammount\":2}]"));
                case "effect"->body.add("effects",JsonParser.parseString("[{\"effect\":\"minecraft:strength\",\"amplifer\":1}]"));
            }
            input.put(new ResourceLocation("test:bad_quota_"+path),body);
        }
        var apply=NutritionRuleManager.class.getDeclaredMethod("apply",Map.class,net.minecraft.server.packs.resources.ResourceManager.class,net.minecraft.util.profiling.ProfilerFiller.class);apply.setAccessible(true);
        try{
            apply.invoke(NutritionRuleManager.INSTANCE,input,h.getLevel().getServer().getResourceManager(),net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            h.assertTrue(rules.size()==1&&rules.containsKey(new ResourceLocation("test:valid_quota")),"Typo accepted or valid quota lost");
            var issues=AuthorDiagnostics.validate(h.getLevel().getServer());
            for(String path:List.of("condition","root","attribute","effect"))h.assertTrue(issues.stream().anyMatch(s->s.startsWith("nutrition_rules/test:bad_quota_"+path+":")),"Missing diagnostic for "+path);
            h.succeed();
        }finally{rules.clear();rules.putAll(original);AuthorDiagnostics.clearParseIssues("nutrition_rules/test:bad_quota_");}
    }
    private static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    private static void reject(GameTestHelper h,Runnable action){
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected,"Invalid author input accepted");
    }
    private static void profile(CompoundTag tag,String id,String body){tag.put(id,JsonNbt.encode(json(body)));}

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void profilesHaveStablePriorityAndInheritOnlyGlobalSettings(GameTestHelper h){
        var original=FoodStageProfiles.SERVER.serialize();var global=LDieDietServerConfig.ALLOWED_FOOD_EFFECTS.get();
        try{
            FixtureConfig.set(LDieDietServerConfig.ALLOWED_FOOD_EFFECTS,List.of("strength"));
            var data=new CompoundTag();
            profile(data,"test:z","{\"priority\":10,\"items\":[\"minecraft:apple\"],\"stages\":{\"thresholds\":[3,6,9,12]}}");
            profile(data,"test:a","{\"priority\":10,\"items\":[\"minecraft:apple\"],\"stages\":{\"thresholds\":[2,4,6,8],\"allowed_food_effects\":[]}}");
            profile(data,"test:lower","{\"priority\":0,\"items\":[\"minecraft:apple\"],\"stages\":{\"chewing_multipliers\":[9,9,9,9,9]}}");
            FoodStageProfiles.SERVER.deserialize(data);var policy=FoodStageProfiles.SERVER.resolve(new ItemStack(Items.APPLE));
            h.assertTrue(policy.thresholds().equals(List.of(2,4,6,8)),"Equal priority did not use ID order");
            h.assertTrue(policy.chewing().equals(FoodStageProfiles.defaults().chewing()),"Lower profile leaked into selected profile");
            h.assertTrue(policy.allowedEffects().isEmpty(),"Explicit empty array did not clear global allowlist");
            h.assertTrue(FoodStageProfiles.SERVER.resolve(new ItemStack(Items.BREAD)).allowedEffects().contains("minecraft:strength"),"Unmatched item did not inherit global policy");
            profile(data,"test:z","{\"priority\":11,\"items\":[\"minecraft:apple\"],\"stages\":{\"thresholds\":[3,6,9,12]}}");
            FoodStageProfiles.SERVER.deserialize(data);h.assertTrue(FoodStageProfiles.SERVER.resolve(new ItemStack(Items.APPLE)).thresholds().get(0)==3,"Higher priority did not win");h.succeed();
        }finally{FoodStageProfiles.SERVER.deserialize(original);FixtureConfig.set(LDieDietServerConfig.ALLOWED_FOOD_EFFECTS,global);}
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void malformedProfilesAreDiagnosedWithoutLosingValidSiblings(GameTestHelper h)throws Exception {
        var original=FoodStageProfiles.SERVER.serialize();var input=new TreeMap<ResourceLocation,JsonElement>();
        input.put(new ResourceLocation("test:valid"),json("{\"items\":[\"minecraft:apple\"],\"stages\":{\"thresholds\":[2,4,6,8]}}"));
        var bad=List.of("\"thresholds\":[1,2,2,4]","\"thresholds\":[1,2,3.5,4]","\"chewing_multipliers\":[1,1]","\"hunger_multipliers\":[1,1,1,1,2]","\"allow_food_buffs\":[true,true,true,true,1]","\"minimum_hunger_restored\":-1","\"unknown_setting\":true");
        for(int i=0;i<bad.size();i++)input.put(new ResourceLocation("test:bad"+i),json("{\"items\":[\"minecraft:bread\"],\"stages\":{"+bad.get(i)+"}}"));
        var apply=FoodStageProfiles.class.getDeclaredMethod("apply",Map.class,net.minecraft.server.packs.resources.ResourceManager.class,net.minecraft.util.profiling.ProfilerFiller.class);apply.setAccessible(true);
        try{
            apply.invoke(FoodStageProfiles.SERVER,input,h.getLevel().getServer().getResourceManager(),net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            h.assertTrue(FoodStageProfiles.SERVER.serialize().getAllKeys().equals(Set.of("test:valid")),"Invalid profile accepted or valid sibling lost");
            var issues=AuthorDiagnostics.validate(h.getLevel().getServer());
            for(int i=0;i<bad.size();i++){String id="food_stage_profiles/test:bad"+i;h.assertTrue(issues.stream().anyMatch(s->s.startsWith(id+":")),"Missing parse diagnostic for "+id);}
            h.succeed();
        }finally{FoodStageProfiles.SERVER.deserialize(original);AuthorDiagnostics.clearParseIssues("food_stage_profiles/test:");}
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void invalidPublicApiInputsLeaveStateUnchanged(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try{
            var cap=CapabilityHandler.get(p);var before=cap.serializeNBT();var apple=new ItemStack(Items.APPLE);
            reject(h,()->PackApi.setNutrient(p,"proteins",Double.NaN));reject(h,()->PackApi.addNutrient(p,"proteins",Double.POSITIVE_INFINITY));
            reject(h,()->PackApi.setNutrient(p,"missing:scale",1));reject(h,()->PackApi.setMonotony(p,apple,Double.NaN));
            reject(h,()->PackApi.setStage(p,apple,-1));reject(h,()->PackApi.setStage(p,apple,5));
            reject(h,()->PackApi.setRecoveryMeals(p,apple,-1));reject(h,()->PackApi.getCCI(p,ItemStack.EMPTY));
            reject(h,()->PackApi.grantGroup(p,"missing:group"));reject(h,()->PackApi.setSuite(p,"missing_suite"));
            int[] callbacks={0};reject(h,()->PackApi.consume(p,apple,-1,0,()->callbacks[0]++));reject(h,()->PackApi.consume(p,apple,1,Double.NaN,()->callbacks[0]++));
            h.assertTrue(callbacks[0]==0&&cap.serializeNBT().equals(before),"Rejected API input changed player or consumed resource");h.succeed();
        }finally{LDieDietGameTests.removePlayer(p);}
    }

    public static class RecursiveHooks {
        final ServerPlayer player;boolean rejected;int attempts;
        RecursiveHooks(ServerPlayer p){player=p;}
        @SubscribeEvent public void nutrient(DietEvents.NutrientPre e){
            if(e.getPlayer()!=player||!e.getNutrient().equals("proteins"))return;
            attempts++;try{PackApi.addNutrient(player,"ldiediet:proteins",1);}catch(IllegalStateException expected){rejected=true;}
            PackApi.addNutrient(player,"vegetables",3);
        }
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void recursiveApiCannotLoopThroughAliasesButCanChangeOtherScales(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var hook=new RecursiveHooks(p);MinecraftForge.EVENT_BUS.register(hook);
        try{
            PackApi.setNutrient(p,"proteins",10);
            h.assertTrue(hook.rejected&&hook.attempts==1,"Same-scale alias bypassed recursion guard");
            h.assertTrue(PackApi.getNutrient(p,"proteins")==10&&PackApi.getNutrient(p,"vegetables")==3,"Unrelated mutation blocked or outer write corrupted");
            PackApi.setNutrient(p,"proteins",20);h.assertTrue(hook.attempts==2&&PackApi.getNutrient(p,"vegetables")==6,"Guard leaked after event finished");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(hook);LDieDietGameTests.removePlayer(p);}
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void worldConditionsRespectOrTagsAndInclusiveMidnightBounds(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);long previous=p.level().getDayTime();
        try{
            var range=EffectCondition.parse(json("{\"time\":{\"min\":23000,\"max\":1000}}"));
            for(int t:new int[]{23000,23999,0,1000}){h.getLevel().setDayTime(t);h.assertTrue(range.evaluate(p),"Inclusive midnight boundary failed at "+t);}
            for(int t:new int[]{1001,22999}){h.getLevel().setDayTime(t);h.assertTrue(!range.evaluate(p),"Out-of-range time matched at "+t);}
            var either=EffectCondition.parse(json("{\"logic\":\"OR\",\"dimensions\":[\"minecraft:the_nether\",\"minecraft:overworld\"],\"custom\":[{\"id\":\"test:missing\"}]}"));
            h.assertTrue(either.evaluate(p),"OR required the unavailable callback");
            var both=EffectCondition.parse(json("{\"logic\":\"AND\",\"dimensions\":[\"minecraft:overworld\"],\"custom\":[{\"id\":\"test:missing\"}]}"));h.assertTrue(!both.evaluate(p),"AND ignored missing callback");
            var biome=p.level().getBiome(p.blockPosition());var tag=biome.tags().findFirst().orElseThrow();
            h.assertTrue(EffectCondition.parse(json("{\"biomes\":[\"#"+tag.location()+"\"]}")).evaluate(p),"Biome tag not recognized");
            for(String invalid:List.of("{\"dimension\":\"minecraft:overworld\"}","{\"time\":{\"min\":24000}}","{\"time\":{\"max\":1.5}}","{\"weather\":\"snow\"}","{\"dimensions\":[\"#minecraft:is_overworld\"]}"))reject(h,()->EffectCondition.parse(json(invalid)));
            h.succeed();
        }finally{h.getLevel().setDayTime(previous);LDieDietGameTests.removePlayer(p);}
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void brokenRecipeContributorCannotPoisonOtherProviders(GameTestHelper h){
        var server=h.getLevel().getServer();long revision=IntegrationApi.recipeRevision();
        try(var broken=IntegrationApi.registerRecipeContributor("test:broken",s->{throw new IllegalStateException("Intentional fixture failure");});
            var good=IntegrationApi.registerRecipeContributor("test:good",s->List.of(new IntegrationApi.CciRecipe(new ItemStack(Items.APPLE),List.of(List.of(new ItemStack(Items.BREAD))),.25)))){
            h.assertTrue(IntegrationApi.contributed(server).stream().anyMatch(r->r.output().is(Items.APPLE)&&r.processingWeight()==.25),"Broken provider discarded valid sibling");
            for(double invalid:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})reject(h,()->new IntegrationApi.CciRecipe(new ItemStack(Items.APPLE),List.of(List.of(new ItemStack(Items.BREAD))),invalid));
            reject(h,()->new IntegrationApi.CciRecipe(ItemStack.EMPTY,List.of(List.of(new ItemStack(Items.BREAD))),0));
            reject(h,()->new IntegrationApi.CciRecipe(new ItemStack(Items.APPLE),List.of(List.of()),0));
        }
        h.assertTrue(IntegrationApi.recipeRevision()>=revision+4,"Registration/removal failed to invalidate recipe index");h.succeed();
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void installedKubeJsCanUseJavaFacadeAndForgeHooks(GameTestHelper h){
        if(!net.minecraftforge.fml.ModList.get().isLoaded("kubejs")){h.succeed();return;}
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try{
            p.addTag("ldd_author_script");
            h.assertTrue(IntegrationApi.hasCondition("test:kubejs_author"),"KubeJS startup script failed to register Java condition");
            h.assertTrue(IntegrationApi.testCondition(p,"test:kubejs_author",json("{\"enabled\":true}")),"Rhino could not invoke Java functional interface");
            h.assertTrue(PackApi.getNutrient(p,"vegetables")==7,"Java facade mutation from JS callback failed");
            PackApi.setNutrient(p,"proteins",10);h.assertTrue(PackApi.getNutrient(p,"proteins")==17,"ForgeEvents subscription could not edit Pre event");
            h.assertTrue(!IntegrationApi.testCondition(p,"test:kubejs_author",json("{\"enabled\":false}")),"JS condition ignored arguments");
            LDieDiet.LOGGER.info("AUTHOR KUBEJS PASS Java.loadClass / SAM callback / server facade / ForgeEvents Pre");h.succeed();
        }finally{LDieDietGameTests.removePlayer(p);}
    }

    public static class RewardHooks {
        final ServerPlayer player;boolean cancel;int posts;
        RewardHooks(ServerPlayer p){player=p;}
        @SubscribeEvent public void quota(DietEvents.QuotaPre e){if(e.getPlayer()==player&&e.getQuota().contains("stress:hook_scale")){e.setCanceled(cancel);e.setMultiplier(3);}}
        @SubscribeEvent public void post(DietEvents.QuotaPost e){if(e.getPlayer()==player&&e.getQuota().contains("stress:hook_scale"))posts++;}
        @SubscribeEvent public void group(DietEvents.GroupAttempt e){if(e.getPlayer()==player&&e.getGroup().toString().equals("stress:hook_slots"))e.cancelEffect(0);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void scaleQuotaMultiplierEditsApplyAndCancellationPreservesForeignModifiers(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var original=NutritionScaleManager.getInstance().serializeToNBT();var hook=new RewardHooks(p);MinecraftForge.EVENT_BUS.register(hook);
        try{
            AuthorStressGameTests.scale("hook_scale","{\"max_value\":100,\"thresholds\":[{\"min\":50,\"max\":100,\"modifiers\":[{\"attribute\":\"minecraft:generic.movement_speed\",\"amount\":0.2}]}]}");
            var speed=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);var foreign=java.util.UUID.randomUUID();
            speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(foreign,"external_fixture",.01,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            PackApi.setNutrient(p,"stress:hook_scale",75);
            h.assertTrue(speed.getModifiers().stream().anyMatch(m->m.getName().startsWith("ldiediet_nutrient_")&&Math.abs(m.getAmount()-.6)<.00001),"Edited multiplier did not scale real attribute");
            h.assertTrue(hook.posts==1,"Scale quota transition missing/duplicated");
            hook.cancel=true;PackApi.refresh(p);
            h.assertTrue(speed.getModifiers().stream().noneMatch(m->m.getName().startsWith("ldiediet_nutrient_")&&Math.abs(m.getAmount()-.6)<.00001)&&speed.getModifier(foreign)!=null,"Cancellation left own modifier or removed external one");
            h.assertTrue(hook.posts==2,"Removing quota did not publish transition");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(hook);NutritionScaleManager.getInstance().deserializeFromNBT(original);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author_boundary",timeoutTicks=100)
    public static void groupAttemptCanCancelOneSlotWithoutBlockingOtherRewards(GameTestHelper h)throws Exception {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var groups=FoodGroupManager.getInstance().getGroupsMap();var original=new HashMap<>(groups);var hook=new RewardHooks(p);MinecraftForge.EVENT_BUS.register(hook);
        try{
            var g=AuthorStressGameTests.group("hook_slots","[{\"duration_type\":\"until_death\",\"effect_type\":\"attribute\",\"effect_id\":\"minecraft:generic.movement_speed\",\"value\":0.2},{\"duration_type\":\"until_death\",\"effect_type\":\"health\",\"effect_id\":\"health\",\"value\":2}]");
            float before=p.getMaxHealth();PackApi.grantGroup(p,g.getId().toString());var effects=CapabilityHandler.get(p).getActiveEffects();
            h.assertTrue(effects.stream().noneMatch(e->e.getGroupId().equals(g.getId().toString())&&e.getDefinitionIndex()==0&&e.isLive()),"Vetoed slot was applied");
            h.assertTrue(effects.stream().anyMatch(e->e.getGroupId().equals(g.getId().toString())&&e.getDefinitionIndex()==1&&e.isCurrentlyActive())&&p.getMaxHealth()==before+2,"Other slot was blocked");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(hook);groups.clear();groups.putAll(original);LDieDietGameTests.removePlayer(p);}
    }
}
