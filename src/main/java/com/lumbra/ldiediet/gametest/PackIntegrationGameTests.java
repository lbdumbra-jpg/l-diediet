package com.lumbra.ldiediet.gametest;

import com.google.gson.*;
import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.api.*;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.consumption.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.*;
import com.lumbra.ldiediet.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("minecraft") @PrefixGameTestTemplate(false)
public final class PackIntegrationGameTests {
    private static JsonObject json(String source){return JsonParser.parseString(source).getAsJsonObject();}
    private static ItemStack fixture(Item item){var stack=new ItemStack(item);stack.getOrCreateTag().putBoolean("LddApiFixture",true);return stack;}
    private static void near(GameTestHelper h,double actual,double expected){h.assertTrue(Math.abs(actual-expected)<.0001,actual+" != "+expected);}
    public static class Hooks {
        final ServerPlayer player;
        boolean cancelMeal,cancelStage,cancelRecovery,cancelNutrient,cancelQuota,cancelGroup,cancelDiscovery;
        boolean alterMeal,alterMonotony,alterNutrient,alterReward;
        int mealPosts,stagePosts,recoveryPosts,nutrientPosts,quotaPosts,discoveryPosts;
        boolean quotaWasCommitted=true;
        Hooks(ServerPlayer player){this.player=player;}
        @SubscribeEvent public void meal(DietEvents.MealPre e){if(e.getPlayer()==player){e.setCanceled(cancelMeal);if(alterMeal)e.setNutrition(2,1);}}
        @SubscribeEvent public void mealPost(DietEvents.MealPost e){if(e.getPlayer()==player)mealPosts++;}
        @SubscribeEvent public void monotony(DietEvents.MonotonyPre e){if(e.getPlayer()==player&&alterMonotony)e.setValue(6);}
        @SubscribeEvent public void stage(DietEvents.StagePre e){if(e.getPlayer()==player)e.setCanceled(cancelStage);}
        @SubscribeEvent public void stagePost(DietEvents.StagePost e){if(e.getPlayer()==player)stagePosts++;}
        @SubscribeEvent public void recovery(DietEvents.RecoveryPre e){if(e.getPlayer()==player)e.setCanceled(cancelRecovery);}
        @SubscribeEvent public void recoveryPost(DietEvents.RecoveryPost e){if(e.getPlayer()==player)recoveryPosts++;}
        @SubscribeEvent public void nutrient(DietEvents.NutrientPre e){if(e.getPlayer()==player){e.setCanceled(cancelNutrient);if(alterNutrient)e.setValue(70);}}
        @SubscribeEvent public void nutrientPost(DietEvents.NutrientPost e){if(e.getPlayer()==player)nutrientPosts++;}
        @SubscribeEvent public void quota(DietEvents.QuotaPre e){if(e.getPlayer()==player&&e.getQuota().startsWith("rule:stress:api_"))e.setCanceled(cancelQuota);}
        @SubscribeEvent public void quotaPost(DietEvents.QuotaPost e){if(e.getPlayer()==player){quotaPosts++;
            if(e.getQuota().equals("rule:stress:api_quota"))quotaWasCommitted &= player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifiers().stream().anyMatch(m->m.getName().contains("stress:api_quota")) == (e.getAfter()>0);
        }}
        @SubscribeEvent public void discovery(DietEvents.DiscoveryPre e){if(e.getPlayer()==player)e.setCanceled(cancelDiscovery);}
        @SubscribeEvent public void discoveryPost(DietEvents.DiscoveryPost e){if(e.getPlayer()==player)discoveryPosts++;}
        @SubscribeEvent public void group(DietEvents.GroupAttempt e){if(e.getPlayer()==player){e.setCanceled(cancelGroup);if(alterReward)e.replaceEffect(0,e.getEffects().get(0).withValues(.5,0,100));}}
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void publicApiPreservesNbtIdentityAndRejectsWrongThread(GameTestHelper h)throws Exception {
        ConsumptionLogicGameTests.withConfig(h,"{\"minecraft:bread\":{\"nbt_filters\":[{\"variant_id\":\"api_red\",\"match\":\"{Flavor:'red'}\"},{\"variant_id\":\"api_blue\",\"match\":\"{Flavor:'blue'}\"}]}}",()->{
            var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
            try{
                var red=new ItemStack(Items.BREAD);red.getOrCreateTag().putString("Flavor","red");var blue=new ItemStack(Items.BREAD);blue.getOrCreateTag().putString("Flavor","blue");
                h.assertTrue(PackApi.setStage(p,red,2),"Stage API failed");PackApi.setRecoveryMeals(p,red,25);
                near(h,PackApi.getMonotony(p,red),10);near(h,PackApi.getMonotony(p,blue),0);
                h.assertTrue(PackApi.foodState(p,red).recoveryMeals()==25&&PackApi.getStage(p,red)==2,"Food state disagrees");
                PackApi.addMonotony(p,red,1000);near(h,PackApi.getMonotony(p,red),24);
                var copy=new FoodToleranceCapability();copy.deserializeNBT(CapabilityHandler.get(p).serializeNBT());h.assertTrue(copy.getRecoveryMeals(DietConfigManager.getFoodKey(red,false))==25,"API counter not persisted");
                boolean rejected=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{PackApi.setMonotony(p,red,0);return false;}catch(IllegalStateException expected){return true;}}).join();
                h.assertTrue(rejected,"Wrong-thread API mutation accepted");h.succeed();
            }finally{LDieDietGameTests.removePlayer(p);}
        });
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void preStageCanCancelOrModifyAndPostOnlyReportsCommittedTransitions(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var hooks=new Hooks(p);MinecraftForge.EVENT_BUS.register(hooks);
        try{
            var stack=new ItemStack(Items.BREAD);hooks.cancelStage=true;h.assertTrue(!PackApi.setStage(p,stack,1),"Cancelled transition accepted");near(h,PackApi.getMonotony(p,stack),0);h.assertTrue(hooks.stagePosts==0,"Cancelled transition posted");
            hooks.cancelStage=false;hooks.alterMonotony=true;PackApi.setMonotony(p,stack,2);near(h,PackApi.getMonotony(p,stack),6);h.assertTrue(hooks.stagePosts==1,"Stage listener got Post twice/never");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(hooks);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void mealCancellationLeavesItemHungerAndHistoryUntouched(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var hooks=new Hooks(p);MinecraftForge.EVENT_BUS.register(hooks);
        try{
            hooks.cancelMeal=true;p.getFoodData().setFoodLevel(0);var stack=new ItemStack(Items.APPLE,3);stack.finishUsingItem(p.level(),p);
            h.assertTrue(stack.getCount()==3&&p.getFoodData().getFoodLevel()==0&&CapabilityHandler.get(p).getHistoryTotalCount()==0&&hooks.mealPosts==0,"Cancelled meal changed state");
            hooks.cancelMeal=false;hooks.alterMeal=true;stack.finishUsingItem(p.level(),p);h.assertTrue(p.getFoodData().getFoodLevel()==2&&hooks.mealPosts==1,"Modified portion did not apply");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(hooks);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void cakeCancellationDoesNotConsumeABlockSlice(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var hooks=new Hooks(p);MinecraftForge.EVENT_BUS.register(hooks);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(0,1,0));var previous=h.getLevel().getBlockState(pos);
        try{
            h.getLevel().setBlock(pos,Blocks.CAKE.defaultBlockState(),3);p.getFoodData().setFoodLevel(0);hooks.cancelMeal=true;
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false);
            h.getLevel().getBlockState(pos).use(h.getLevel(),p,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(h.getLevel().getBlockState(pos).getValue(CakeBlock.BITES)==0&&p.getFoodData().getFoodLevel()==0,"Cancelled cake bite consumed block/hunger");
            hooks.cancelMeal=false;h.getLevel().getBlockState(pos).use(h.getLevel(),p,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(h.getLevel().getBlockState(pos).getValue(CakeBlock.BITES)==1&&hooks.mealPosts==1,"Cake preflight duplicated or blocked normal slice");h.succeed();
        }finally{h.getLevel().setBlock(pos,previous,3);MinecraftForge.EVENT_BUS.unregister(hooks);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void recoveryCancellationPreservesMonotonyAndAcceleration(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var hooks=new Hooks(p);MinecraftForge.EVENT_BUS.register(hooks);
        try{
            var cap=CapabilityHandler.get(p);var key=FoodKey.of(Items.BREAD);cap.setMonotony(key,6);cap.setRecoveryMeals(key,20);hooks.cancelRecovery=true;
            p.getFoodData().setFoodLevel(0);new ItemStack(Items.APPLE).finishUsingItem(p.level(),p);
            h.assertTrue(cap.getMonotony(key)==6&&cap.getRecoveryMeals(key)==20&&hooks.recoveryPosts==0,"Cancelled recovery changed state");
            hooks.cancelRecovery=false;p.getFoodData().setFoodLevel(0);new ItemStack(Items.APPLE).finishUsingItem(p.level(),p);
            h.assertTrue(cap.getMonotony(key)<6&&cap.getRecoveryMeals(key)==21&&hooks.recoveryPosts==1,"Recovery did not resume");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(hooks);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void nutrientAndQuotaHooksControlRealModifiers(GameTestHelper h)throws Exception {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var hooks=new Hooks(p);MinecraftForge.EVENT_BUS.register(hooks);var rules=AuthorStressGameTests.rules();var original=new TreeMap<>(rules);
        try{
            AuthorStressGameTests.rule("api_quota","{\"conditions\":[{\"nutrients\":[\"proteins\"],\"min\":60,\"max\":100}],\"attributes\":[{\"attribute\":\"minecraft:generic.movement_speed\",\"amount\":0.5}]}");
            hooks.cancelNutrient=true;PackApi.setNutrient(p,"proteins",80);near(h,PackApi.getNutrient(p,"proteins"),0);
            hooks.cancelNutrient=false;hooks.alterNutrient=true;hooks.cancelQuota=true;PackApi.addNutrient(p,"proteins",10);near(h,PackApi.getNutrient(p,"proteins"),70);
            var speed=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            h.assertTrue(speed.getModifiers().stream().noneMatch(m->m.getName().contains("stress:api_quota")),"Cancelled quota applied a modifier");
            int before=hooks.quotaPosts;hooks.cancelQuota=false;PackApi.refresh(p);h.assertTrue(speed.getModifiers().stream().anyMatch(m->m.getName().contains("stress:api_quota")),"Quota did not apply");
            h.assertTrue(hooks.quotaPosts>before&&hooks.quotaWasCommitted,"Quota Post ran before modifiers were committed");before=hooks.quotaPosts;PackApi.refresh(p);h.assertTrue(hooks.quotaPosts==before,"Unchanged quota emitted repeated Post");h.succeed();
        }finally{rules.clear();rules.putAll(original);MinecraftForge.EVENT_BUS.unregister(hooks);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void rewardAndDiscoveryHooksCanVetoAndEditWithoutChangingDefinitions(GameTestHelper h)throws Exception {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var hooks=new Hooks(p);MinecraftForge.EVENT_BUS.register(hooks);var groups=FoodGroupManager.getInstance().getGroupsMap();var original=new HashMap<>(groups);
        try{
            var group=AuthorStressGameTests.group("api_reward","[{\"duration_type\":\"until_death\",\"effect_type\":\"attribute\",\"effect_id\":\"minecraft:generic.movement_speed\",\"value\":0.1}]").secret(true);
            hooks.cancelGroup=true;PackApi.grantGroup(p,group.getId().toString());h.assertTrue(!PackApi.activatedGroups(p).contains(group.getId().toString()),"Cancelled reward activated");
            hooks.cancelGroup=false;hooks.alterReward=true;hooks.cancelDiscovery=true;PackApi.grantGroup(p,group.getId().toString());
            var effect=CapabilityHandler.get(p).getActiveEffects().stream().filter(e->e.getGroupId().equals(group.getId().toString())).findFirst().orElseThrow();near(h,effect.getValue(),.5);near(h,group.getEffects().get(0).getValue(),.1);
            h.assertTrue(!PackApi.isGroupKnown(p,group.getId().toString()),"Cancelled discovery revealed group");hooks.cancelDiscovery=false;PackApi.discoverGroup(p,group.getId().toString());
            h.assertTrue(PackApi.discoveredGroups(p).contains(group.getId().toString())&&hooks.discoveryPosts==1,"Discovery API not notified");
            h.assertTrue(PackApi.cancelGroupEffect(p,group.getId().toString(),0),"Reward slot not cancelled");RewardHandler.tickActiveEffects(p);h.assertTrue(!effect.isLive(),"Cancelled slot was recreated");
            PackApi.cancelGroup(p,group.getId().toString());h.assertTrue(PackApi.isGroupKnown(p,group.getId().toString()),"Cancelling reward erased discovery");h.succeed();
        }finally{groups.clear();groups.putAll(original);MinecraftForge.EVENT_BUS.unregister(hooks);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void profileLoadedAtStartupMatchesTagsNbtAndSynchronizes(GameTestHelper h){
        var apple=fixture(Items.APPLE);var berries=fixture(Items.SWEET_BERRIES);
        h.assertTrue(FoodStageProfiles.SERVER.resolve(apple).thresholds().equals(List.of(2,4,6,8)),"Startup profile was not loaded");
        h.assertTrue(FoodStageProfiles.SERVER.resolve(berries).thresholds().equals(List.of(2,4,6,8)),"Tag selector did not match");
        h.assertTrue(FoodStageProfiles.SERVER.resolve(new ItemStack(Items.APPLE)).thresholds().equals(List.of(5,10,15,20)),"NBT restriction ignored");
        var before=FoodStageProfiles.CLIENT.serialize();try{FoodStageProfiles.CLIENT.deserialize(FoodStageProfiles.SERVER.serialize());h.assertTrue(FoodStageProfiles.CLIENT.resolve(apple).thresholds().equals(List.of(2,4,6,8)),"Client profile not synchronized");}finally{FoodStageProfiles.CLIENT.deserialize(before);}h.succeed();
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void profileChangesPenaltiesForecastAndSelectiveEffectBlocking(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try{
            var stack=fixture(Items.APPLE);var cap=CapabilityHandler.get(p);cap.setMonotony(Items.APPLE,4);var portion=ConsumptionSnapshot.capture(p,stack);
            h.assertTrue(portion.stage().getLevel()==2,"Individual stage ignored");near(h,portion.chewingMultiplier(),1);near(h,portion.nutrientMultiplier(),.75);
            p.addEffect(new MobEffectInstance(MobEffects.POISON,300,0));
            try(var scope=EatingContext.open(p,portion)){
                h.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,300,0)),"Allowlisted food buff blocked");
                h.assertTrue(!p.addEffect(new MobEffectInstance(MobEffects.POISON,600,1)),"Blocked harmful effect applied");
                h.assertTrue(!p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,300,0)),"Unlisted beneficial effect bypassed stage");
            }
            h.assertTrue(p.getEffect(MobEffects.POISON).getAmplifier()==0,"Existing external potion was removed");
            p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);stack.finishUsingItem(p.level(),p);near(h,p.getFoodData().getFoodLevel(),1);
            var data=JournalFood.capture(p,cap).getCompound(0);h.assertTrue(Arrays.equals(data.getIntArray("thresholds"),new int[]{2,4,6,8}),"Journal used global marks");h.succeed();
        }finally{LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void worldConditionsCoverBiomeDimensionTimeWeatherAndAdvancement(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var level=h.getLevel();long time=level.getDayTime();boolean rain=level.isRaining(),thunder=level.isThundering();float rainLevel=level.getRainLevel(1),thunderLevel=level.getThunderLevel(1);
        try{
            String biome=p.level().getBiome(p.blockPosition()).unwrapKey().orElseThrow().location().toString();
            level.setDayTime(23500);level.setWeatherParameters(0,200,true,true);level.setRainLevel(1);level.setThunderLevel(1);
            var c=EffectCondition.parse(json("{\"biomes\":[\""+biome+"\"],\"dimensions\":[\"minecraft:overworld\"],\"time\":{\"min\":23000,\"max\":1000},\"weather\":\"thunder\"}"));h.assertTrue(c.evaluate(p),"World predicate did not match wrapped time range");
            level.setDayTime(5000);h.assertTrue(!c.evaluate(p),"Time mismatch ignored");
            var advancement=level.getServer().getAdvancements().getAdvancement(new ResourceLocation("minecraft:story/root"));var gate=EffectCondition.parse(json("{\"advancements\":[\"minecraft:story/root\"]}"));h.assertTrue(!gate.evaluate(p),"Unearned advancement matched");
            for(String criterion:p.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria())p.getAdvancements().award(advancement,criterion);h.assertTrue(gate.evaluate(p),"Earned advancement did not match");h.succeed();
        }finally{level.setDayTime(time);level.setWeatherParameters(0,200,rain,thunder);level.setRainLevel(rainLevel);level.setThunderLevel(thunderLevel);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void registeredPredicatesReplaceSafelyAndFailClosed(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try{
            var c=EffectCondition.parse(json("{\"custom\":[{\"id\":\"test:api_condition\",\"args\":{\"minimum\":4}}]}"));h.assertTrue(!c.evaluate(p),"Missing callback matched");
            var old=IntegrationApi.registerCondition("test:api_condition",(player,args)->false);
            try(var newer=IntegrationApi.registerCondition("test:api_condition",(player,args)->{boolean result=args.get("minimum").getAsInt()==4;args.addProperty("minimum",99);return result;})){
                old.close();h.assertTrue(c.evaluate(p)&&c.evaluate(p),"Replacement removed or mutable callback arguments leaked");
            }
            h.assertTrue(!c.evaluate(p),"Closed callback remained installed");h.succeed();
        }finally{LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void worldConditionsGateNativeQuotaRewards(GameTestHelper h)throws Exception {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var rules=AuthorStressGameTests.rules();var original=new TreeMap<>(rules);
        try{
            AuthorStressGameTests.rule("api_world","{\"conditions\":[{\"nutrients\":[\"proteins\"],\"min\":60,\"max\":100}],\"world_conditions\":{\"custom\":[{\"id\":\"test:api_world\"}]},\"attributes\":[{\"attribute\":\"minecraft:generic.movement_speed\",\"amount\":0.5}]}");
            PackApi.setNutrient(p,"proteins",80);var speed=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);h.assertTrue(speed.getModifiers().stream().noneMatch(m->m.getName().contains("stress:api_world")),"Unknown world condition granted quota");
            try(var registered=IntegrationApi.registerCondition("test:api_world",(player,args)->true)){PackApi.refresh(p);h.assertTrue(speed.getModifiers().stream().anyMatch(m->m.getName().contains("stress:api_world")),"World condition did not unlock quota");}
            PackApi.refresh(p);h.assertTrue(speed.getModifiers().stream().noneMatch(m->m.getName().contains("stress:api_world")),"Removed predicate left reward active");h.succeed();
        }finally{rules.clear();rules.putAll(original);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void externalConsumptionCountsOnceAndCleansUpAfterFailure(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var hooks=new Hooks(p);MinecraftForge.EVENT_BUS.register(hooks);
        try{
            p.getFoodData().setFoodLevel(0);var stack=new ItemStack(Items.APPLE,3);
            h.assertTrue(PackApi.consume(p,stack,4,2,()->stack.finishUsingItem(p.level(),p)),"External portion refused");
            h.assertTrue(stack.getCount()==2&&PackApi.foodState(p,stack).eatenCount()==1&&hooks.mealPosts==1,"Nested vanilla consumption counted twice");near(h,p.getFoodData().getFoodLevel(),4);
            hooks.cancelMeal=true;h.assertTrue(!PackApi.consume(p,stack,4,2,()->stack.shrink(1))&&stack.getCount()==2,"Cancelled external callback ran");hooks.cancelMeal=false;
            try{PackApi.consume(p,stack,4,2,()->{throw new IllegalStateException("api fixture");});throw new AssertionError("Callback failure swallowed");}catch(IllegalStateException expected){h.assertTrue(expected.getMessage().equals("api fixture"),"Unexpected error");}
            h.assertTrue(EatingContext.current()==null&&PackApi.foodState(p,stack).eatenCount()==1,"Failed external portion leaked or counted");
            PackApi.consume(p,stack,4,2,()->stack.shrink(1));h.assertTrue(PackApi.foodState(p,stack).eatenCount()==2,"Failed callback left transaction locked");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(hooks);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void customRecipeAdapterAffectsRealCciAndPreservesManualValues(GameTestHelper h)throws Exception {
        ConsumptionLogicGameTests.withConfig(h,"{\"minecraft:apple\":{\"cci\":1,\"cci_source\":\"auto\"},\"minecraft:bread\":{\"cci\":1},\"minecraft:golden_carrot\":{\"cci\":3}}",()->{
            var server=h.getLevel().getServer();
            try(var adapter=IntegrationApi.registerRecipeAdapter("minecraft:crafting",(recipe,registries)->List.of(new IntegrationApi.CciRecipe(new ItemStack(Items.APPLE),List.of(List.of(new ItemStack(Items.BREAD))),1)))){
                DietConfigManager.reloadRecipes(server);DietConfigManager.recalculateAllCCI(server);near(h,DietConfigManager.getCCI(Items.APPLE),2);near(h,DietConfigManager.getCCI(Items.GOLDEN_CARROT),3);
            }
            DietConfigManager.reloadRecipes(server);DietConfigManager.recalculateAllCCI(server);near(h,DietConfigManager.getCCI(Items.APPLE),1);h.succeed();
        });
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void contributorsAddNonVanillaRecipesAndReturnDefensiveCopies(GameTestHelper h)throws Exception {
        ConsumptionLogicGameTests.withConfig(h,"{\"minecraft:apple\":{\"cci\":1,\"cci_source\":\"auto\"},\"minecraft:bread\":{\"cci\":1}}",()->{
            var server=h.getLevel().getServer();var recipe=new IntegrationApi.CciRecipe(new ItemStack(Items.APPLE),List.of(List.of(new ItemStack(Items.BREAD))),.75);
            recipe.output().shrink(1);recipe.ingredients().get(0).get(0).shrink(1);h.assertTrue(!recipe.output().isEmpty()&&!recipe.ingredients().get(0).get(0).isEmpty(),"Mutable recipe data leaked");
            try(var contributor=IntegrationApi.registerRecipeContributor("test:api_machine",s->List.of(recipe))){DietConfigManager.reloadRecipes(server);DietConfigManager.recalculateAllCCI(server);near(h,DietConfigManager.getCCI(Items.APPLE),1.75);}
            DietConfigManager.reloadRecipes(server);h.succeed();
        });
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void externalNonEdibleItemKeepsNutritionAndForecastAcrossSave(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try{
            var stack=new ItemStack(Items.ARROW,2);p.getFoodData().setFoodLevel(0);
            PackApi.consume(p,stack,4,2,()->stack.shrink(1));var cap=CapabilityHandler.get(p);var key=FoodKey.of(Items.ARROW);
            var restored=new FoodToleranceCapability();restored.deserializeNBT(cap.serializeNBT());cap.copyFrom(restored);
            h.assertTrue(Arrays.equals(cap.externalNutrition(key),new double[]{4,2}),"External quality lost on save");
            var food=JournalFood.capture(p,cap).getCompound(0);h.assertTrue(food.getIntArray("remaining")[0]>0,"Non-edible representation received infinite forecast");
            h.assertTrue(food.getList("details",Tag.TAG_STRING).toString().contains("4"),"External restoration absent from journal");h.succeed();
        }finally{LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void useStartFreezesIndividualStagePolicyAcrossReload(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var original=FoodStageProfiles.SERVER.serialize();
        try{
            var stack=fixture(Items.APPLE);CapabilityHandler.get(p).setMonotony(Items.APPLE,4);p.getFoodData().setFoodLevel(0);p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.startUsingItem(InteractionHand.MAIN_HAND);
            var changed=original.copy();var id="ldiediet:api_fixture";var definition=JsonNbt.decode(changed.getCompound(id)).getAsJsonObject();
            definition.getAsJsonObject("stages").add("hunger_multipliers",JsonParser.parseString("[1,1,1,1,1]"));changed.put(id,JsonNbt.encode(definition));FoodStageProfiles.SERVER.deserialize(changed);
            stack.finishUsingItem(p.level(),p);near(h,p.getFoodData().getFoodLevel(),1);
            h.assertTrue(CapabilityHandler.get(p).externalNutrition(FoodKey.of(Items.APPLE))==null,"Native meal left external metadata");h.succeed();
        }finally{FoodStageProfiles.SERVER.deserialize(original);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void globalEffectIdsNormalizeAndBlocklistWins(GameTestHelper h){
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var allowed=LDieDietServerConfig.ALLOWED_FOOD_EFFECTS.get();var blocked=LDieDietServerConfig.BLOCKED_FOOD_EFFECTS.get();
        try{
            CapabilityHandler.get(p).setMonotony(Items.APPLE,10);FixtureConfig.set(LDieDietServerConfig.ALLOWED_FOOD_EFFECTS,List.of("strength"));
            try(var scope=EatingContext.open(p,ConsumptionSnapshot.capture(p,new ItemStack(Items.APPLE)))){h.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,200,0)),"Unqualified effect ID not normalized");}
            p.removeEffect(MobEffects.DAMAGE_BOOST);FixtureConfig.set(LDieDietServerConfig.BLOCKED_FOOD_EFFECTS,List.of("minecraft:strength"));
            try(var scope=EatingContext.open(p,ConsumptionSnapshot.capture(p,new ItemStack(Items.APPLE)))){h.assertTrue(!p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,200,0)),"Allowlist beat blocklist");}h.succeed();
        }finally{FixtureConfig.set(LDieDietServerConfig.ALLOWED_FOOD_EFFECTS,allowed);FixtureConfig.set(LDieDietServerConfig.BLOCKED_FOOD_EFFECTS,blocked);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void groupWorldConditionsSurviveNetworkSerialization(GameTestHelper h)throws Exception {
        var groups=FoodGroupManager.getInstance().getGroupsMap();var original=new HashMap<>(groups);var client=FoodGroupManager.getClientInstance().serializeToNBT();var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try{
            var g=AuthorStressGameTests.group("api_serial","[{\"duration_type\":\"until_death\",\"effect_type\":\"health\",\"effect_id\":\"health\",\"value\":2,\"conditions\":{\"dimensions\":[\"minecraft:the_nether\"],\"time\":{\"min\":1000,\"max\":2000}}}]");
            PackApi.grantGroup(p,g.getId().toString());h.assertTrue(CapabilityHandler.get(p).getActiveEffects().stream().noneMatch(ActiveGroupEffect::isCurrentlyActive),"Dimension condition was ignored");
            FoodGroupManager.getClientInstance().deserializeFromNBT(FoodGroupManager.getInstance().serializeToNBT());
            var copied=FoodGroupManager.getClientInstance().getGroup(g.getId()).getEffects().get(0).getConditions();
            h.assertTrue(copied.additionalJson().equals(g.getEffects().get(0).getConditions().additionalJson())&&!copied.evaluate(p),"World predicates disappeared in NBT");h.succeed();
        }finally{groups.clear();groups.putAll(original);FoodGroupManager.getClientInstance().deserializeFromNBT(client);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_pack_api",timeoutTicks=100)
    public static void repeatedRegistrationOfSameCallbackHasIndependentHandles(GameTestHelper h){
        IntegrationApi.PlayerCondition callback=(player,args)->true;
        var first=IntegrationApi.registerCondition("test:api_same",callback);
        try(var second=IntegrationApi.registerCondition("test:api_same",callback)){
            first.close();h.assertTrue(IntegrationApi.hasCondition("test:api_same"),"Old handle removed repeated callback registration");
        }
        h.assertTrue(!IntegrationApi.hasCondition("test:api_same"),"Latest handle did not unregister");h.succeed();
    }
}
