package com.lumbra.ldiediet.gametest;
import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.consumption.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.event.FoodEatingHandler;
import com.lumbra.ldiediet.rewards.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("minecraft") @PrefixGameTestTemplate(false)
public final class JournalGameTests {
 @GameTest(template="bastion/blocks/air",batch="ldiediet_journal",timeoutTicks=100)
 public static void journalPredictsRealPortionsAndKeepsNbtSample(GameTestHelper h)throws Exception {
  ConsumptionLogicGameTests.withConfig(h,"{\"minecraft:bread\":{\"cci\":3,\"food_value\":2.4,\"nbt_filters\":[{\"variant_id\":\"small\",\"match\":\"{Small:1b}\",\"food_value\":1}]}}",()->{
   var p=LDieDietGameTests.makeNetworkedTestPlayer(h);try {
    var cap=CapabilityHandler.get(p);var stack=new ItemStack(Items.BREAD);stack.getOrCreateTag().putBoolean("Small",true);
    var key=DietConfigManager.getFoodKey(stack,false);
    FoodEatingHandler.complete(p,ConsumptionSnapshot.capture(p,stack));cap.setMonotony(key,4.5f);
    var snapshot=JournalFood.capture(p,cap);var row=snapshot.getCompound(0);int next=row.getIntArray("remaining")[0];
    h.assertTrue(next==1,"Forecast did not use the new variant-specific gain");
    h.assertTrue(ToleranceStage.getStage(p,stack).getLevel()==0,"Stage advanced before eating");
    FoodEatingHandler.complete(p,ConsumptionSnapshot.capture(p,stack));h.assertTrue(ToleranceStage.getStage(p,stack).getLevel()==1,"Stage did not advance at forecast count");
    var restored=new FoodToleranceCapability();restored.deserializeNBT(cap.serializeNBT());
    h.assertTrue(restored.foodSample(key).getOrCreateTag().getBoolean("Small"),"NBT sample lost on save/load");
    restored.setMonotony(key,3.5f);h.assertTrue(JournalFood.capture(p,restored).getCompound(0).getIntArray("remaining")[0]==3,"Recovery did not change remaining portions");
    h.succeed();
   }finally{LDieDietGameTests.removePlayer(p);}
  });
 }
 @GameTest(template="bastion/blocks/air",batch="ldiediet_journal",timeoutTicks=100)
 public static void sameAttributeAggregatesAndRetainsEveryContribution(GameTestHelper h){
  var p=LDieDietGameTests.makeNetworkedTestPlayer(h);try {
   var cap=CapabilityHandler.get(p);var attribute=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
   for(var mod:List.copyOf(attribute.getModifiers()))attribute.removeModifier(mod.getId());
   var op=net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
   attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUID.randomUUID(),"ldiediet_nutrient_proteins",2,op));
   attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUID.randomUUID(),"ldiediet_nutrient_fruits",3,op));
   attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUID.randomUUID(),"ldiediet_nutrient_sugars",.2,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
   attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUID.randomUUID(),"ldiediet_nutrient_vegetables",.25,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
   var effects=JournalSnapshot.capture(p,cap).getList("effects",Tag.TAG_COMPOUND);int found=0;
   for(var tag:effects){var e=(CompoundTag)tag;if(e.getString("id").equals("minecraft:generic.max_health")){
    found++;h.assertTrue(e.getDouble("addition")==5&&Math.abs(e.getDouble("total")-1.5)<1e-9,"Mixed modifiers were summed incorrectly");
    h.assertTrue(e.getList("sources",Tag.TAG_STRING).size()==4,"A contribution disappeared from tooltip");
   }}
   h.assertTrue(found==1,"Repeated attribute rows survived aggregation");h.succeed();
  }finally{LDieDietGameTests.removePlayer(p);}
 }
 @GameTest(template="bastion/blocks/air",batch="ldiediet_journal",timeoutTicks=100)
 public static void fortySixSmallPortionsReachStageAndCountExactly(GameTestHelper h)throws Exception {
  ConsumptionLogicGameTests.withConfig(h,"{\"minecraft:bread\":{\"cci\":3,\"food_value\":2.4}}",()->{
   var p=LDieDietGameTests.makeNetworkedTestPlayer(h);try {
    var cap=CapabilityHandler.get(p);var key=FoodKey.of(Items.BREAD);
    for(int i=0;i<46;i++)FoodEatingHandler.complete(p,ConsumptionSnapshot.capture(p,new ItemStack(Items.BREAD)));
    h.assertTrue(cap.getEatenCounts().get(key)==46,"Portion counter lost or doubled a meal");
    h.assertTrue(Math.abs(cap.getMonotony(key)-24)<.001&&ToleranceStage.getStage(p,Items.BREAD).getLevel()==4,"Repeated portions did not reach the configured cap");
    cap.clearHistory();cap.setMonotony(key,0);var restored=new FoodToleranceCapability();restored.deserializeNBT(cap.serializeNBT());
    h.assertTrue(restored.getEatenCounts().get(key)==46&&restored.isEatenCountExact(key),"Clearing bounded reward history or monotony value erased lifetime counts");h.succeed();
   }finally{LDieDietGameTests.removePlayer(p);}
  });
 }
 @GameTest(template="bastion/blocks/air",batch="ldiediet_journal",timeoutTicks=100)
 public static void oldCountsAreExplicitLowerBounds(GameTestHelper h){
  var cap=new FoodToleranceCapability();var key=FoodKey.of(Items.APPLE);for(int i=0;i<7;i++){cap.recordEaten(key,10);cap.addHistory(key);}
  var old=cap.serializeNBT();old.putInt("schema_version",4);var entries=old.getList("monotonyEntries",Tag.TAG_COMPOUND);
  for(int i=0;i<entries.size();i++){entries.getCompound(i).getCompound("data").remove("eatenCount");entries.getCompound(i).getCompound("data").remove("countExact");}
  var restored=new FoodToleranceCapability();restored.deserializeNBT(old);
  h.assertTrue(restored.getEatenCounts().get(key)==7&&!restored.isEatenCountExact(key),"Legacy bounded history advertised an exact lifetime count");
  restored.recordEaten(key,20);var again=new FoodToleranceCapability();again.deserializeNBT(restored.serializeNBT());
  h.assertTrue(again.getEatenCounts().get(key)==8&&!again.isEatenCountExact(key),"Legacy lower bound was lost on a second save");h.succeed();
 }
 @GameTest(template="bastion/blocks/air",batch="ldiediet_journal",timeoutTicks=100)
 public static void quotaDiscoverySurvivesLosingRewardAndReload(GameTestHelper h){
  var p=LDieDietGameTests.makeNetworkedTestPlayer(h);try{
   var cap=CapabilityHandler.get(p);cap.clearNutrients();
   var before=JournalSnapshot.capture(p,cap).getList("markers",Tag.TAG_COMPOUND);String key="";
   for(int i=0;i<before.size();i++){var m=before.getCompound(i);if(m.getString("key").startsWith("quota:test_rewards:author_balanced:")){key=m.getString("key");h.assertTrue(!m.getBoolean("known")&&!m.contains("lines"),"Undiscovered reward text leaked");}}
   h.assertTrue(!key.isBlank(),"Compound quota has no markers");
   cap.setNutrientValue("test_rewards:author_energy",70);cap.setNutrientValue("vegetables",70);NutritionRuleHandler.refresh(p,cap);JournalSnapshot.capture(p,cap);
   h.assertTrue(cap.hasDiscovered(key),"Active quota was not discovered");cap.clearNutrients();
   var restored=new FoodToleranceCapability();restored.deserializeNBT(cap.serializeNBT());
   var after=JournalSnapshot.capture(p,restored).getList("markers",Tag.TAG_COMPOUND);
   for(int i=0;i<after.size();i++){var m=after.getCompound(i);if(m.getString("key").equals(key))h.assertTrue(m.getBoolean("known")&&!m.getBoolean("active")&&!m.getList("lines",Tag.TAG_STRING).isEmpty(),"Previously obtained quota became secret again");}h.succeed();
  }finally{LDieDietGameTests.removePlayer(p);}
 }
 @GameTest(template="bastion/blocks/air",batch="ldiediet_journal",timeoutTicks=100)
 public static void secretGroupWaitsForActualEffectAndStaysKnown(GameTestHelper h){
  var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var id=new ResourceLocation("journal:secret");
  var definition=new FoodGroupDefinition(id,"Hidden recipe","","","",List.of("minecraft:apple"),List.of(new FoodGroupDefinition.GroupEffect("until_death","attribute","minecraft:generic.max_health",2,0,0,null,null,List.of(),List.of("journal:gate"),Map.of(),null)),1,true,"","aqua",true).secret(true);
  FoodGroupManager.getInstance().getGroupsMap().put(id,definition);
  try{var cap=CapabilityHandler.get(p);RewardHandler.activateGroupReward(p,cap,definition);
   h.assertTrue(!definition.isKnown(cap),"Pending reward revealed secret group");cap.addPermanentReward("journal:gate");RewardHandler.activateGroupReward(p,cap,definition);
   h.assertTrue(definition.isKnown(cap),"Actual effect did not reveal group");RewardHandler.onDeath(cap,true);
   var restored=new FoodToleranceCapability();restored.deserializeNBT(cap.serializeNBT());h.assertTrue(definition.isKnown(restored),"Death/save lost group discovery");
   var data=FoodGroupManager.getInstance().serializeToNBT();var client=FoodGroupManager.getClientInstance();var saved=client.serializeToNBT();try{client.deserializeFromNBT(data);h.assertTrue(client.getGroup(id).isSecret(),"Secret flag lost in resource sync");}finally{client.deserializeFromNBT(saved);}h.succeed();
  }finally{FoodGroupManager.getInstance().getGroupsMap().remove(id);LDieDietGameTests.removePlayer(p);}
 }
 @GameTest(template="bastion/blocks/air",batch="ldiediet_journal",timeoutTicks=200)
 public static void chewingDurationMatchesAnimationAndClearsAfterUse(GameTestHelper h){
  var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var cap=CapabilityHandler.get(p);cap.setMonotony(Items.APPLE,20);var stack=new ItemStack(Items.APPLE,2);
  p.getFoodData().setFoodLevel(0);p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.startUsingItem(InteractionHand.MAIN_HAND);
  h.assertTrue(stack.getUseDuration()==96&&p.getUseItemRemainingTicks()==96&&p.getTicksUsingItem()==0,"Animation uses vanilla duration while real use is slowed");
  try {
   var handler=Class.forName("net.grapes.gea.ServerEventHandler");var field=handler.getDeclaredField("serverEatingStates");field.setAccessible(true);
   var state=((Map<?,?>)field.get(null)).get(p.getUUID());h.assertTrue(state!=null,"GEA did not start apple animation");
   var duration=state.getClass().getDeclaredField("duration");duration.setAccessible(true);h.assertTrue(duration.getInt(state)==96,"GEA captured the unmodified vanilla duration");
  }catch(ClassNotFoundException optionalModAbsent){}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  h.onEachTick(()->{if(p.isAlive()&&p.isUsingItem())p.doTick();});
  h.runAfterDelay(8,()->{
   try{h.assertTrue(p.isUsingItem()&&p.getTicksUsingItem()>0&&p.getTicksUsingItem()<16,"Use animation jumps or has negative elapsed time");
    p.stopUsingItem();h.assertTrue(stack.getUseDuration()==32&&cap.getUseSession()==null,"Stopped stack retained duration override");h.succeed();
   }finally{LDieDietGameTests.removePlayer(p);}
  });
 }
}

