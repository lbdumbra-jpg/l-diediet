package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.compat.*;
import com.lumbra.ldiediet.data.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("minecraft") @PrefixGameTestTemplate(false)
public final class NutritionJournalGameTests {
    @GameTest(template="bastion/blocks/air",batch="ldiediet_nutrition_journal",timeoutTicks=100)
    public static void replacementTierHasOneLocalizedBoundaryAndOnlyTheNewReward(GameTestHelper h) {
        var manager=NutritionScaleManager.getInstance();var original=manager.serializeToNBT();
        var player=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            AuthorStressGameTests.scale("tier_popup","{\"max_value\":200,\"thresholds\":[{\"min\":160,\"max\":200,\"max_inclusive\":false,\"modifiers\":[{\"attribute\":\"minecraft:generic.max_health\",\"amount\":2}]},{\"min\":200,\"max\":200,\"modifiers\":[{\"attribute\":\"minecraft:generic.max_health\",\"amount\":4}]}]}");
            var cap=CapabilityHandler.get(player);cap.setNutrientValue("stress:tier_popup",160);
            var markers=JournalSnapshot.capture(player,cap).getList("markers",Tag.TAG_COMPOUND);
            var tip=JournalMarkers.tooltip(markers,"stress:tier_popup",160,200);
            h.assertTrue(tip.size()==4,"80% popup repeats headings or rewards");
            String heading=net.minecraft.network.chat.Component.Serializer.toJson(tip.get(0));
            h.assertTrue(heading.contains("ldiediet.journal.boundary_percent")&&heading.contains("80"),"Threshold is not a localized percentage");
            String serialized=JournalSnapshot.components(JournalMarkers.tooltip(markers,"stress:tier_popup",200,200)).toString();
            h.assertTrue(serialized.contains("ldiediet.journal.secret")&&!serialized.contains("+2"),"Unknown next tier leaks a previous bonus");
            cap.setNutrientValue("stress:tier_popup",200);markers=JournalSnapshot.capture(player,cap).getList("markers",Tag.TAG_COMPOUND);
            tip=JournalMarkers.tooltip(markers,"stress:tier_popup",200,200);serialized=JournalSnapshot.components(tip).toString();
            h.assertTrue(tip.size()==4&&serialized.contains("+4")&&!serialized.contains("+2")&&!serialized.contains("ldiediet.journal.reward_ends"),"100% includes the replaced bonus");
            AuthorStressGameTests.scale("ending_popup","{\"max_value\":100,\"thresholds\":[{\"min\":50,\"max\":80,\"max_inclusive\":false,\"modifiers\":[{\"attribute\":\"minecraft:generic.max_health\",\"amount\":3}]}]}");
            cap.setNutrientValue("stress:ending_popup",60);markers=JournalSnapshot.capture(player,cap).getList("markers",Tag.TAG_COMPOUND);
            serialized=JournalSnapshot.components(JournalMarkers.tooltip(markers,"stress:ending_popup",80,100)).toString();
            h.assertTrue(serialized.contains("ldiediet.journal.reward_ends")&&serialized.contains("+3"),"A real reward ending was hidden");h.succeed();
        } finally {manager.deserializeFromNBT(original);LDieDietGameTests.removePlayer(player);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_nutrition_journal",timeoutTicks=100)
    public static void deathLossIsRelativeConfigurableAndOnlyAppliedToDeathClones(GameTestHelper h) {
        var setting=com.lumbra.ldiediet.LDieDietServerConfig.NUTRITION_DEATH_LOSS_PERCENT;double original=setting.get();
        try {
            for(double loss:List.of(0.0,12.5,50.0,100.0))for(boolean death:List.of(false,true)) {
                FixtureConfig.set(setting,loss);
                var before=LDieDietGameTests.makeNetworkedTestPlayer(h);var after=LDieDietGameTests.makeNetworkedTestPlayer(h);
                try {
                    var cap=CapabilityHandler.get(before);cap.setNutrientValue("proteins",80);cap.setNutrientValue("test:custom_nutrition",160);
                    cap.setMonotony(Items.APPLE,6);cap.discover("group:test:secret");
                    before.invalidateCaps();
                    CapabilityHandler.ForgeBusEvents.onPlayerClone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(after,before,death));
                    var next=CapabilityHandler.get(after);double factor=death?1-loss/100:1;
                    h.assertTrue(Math.abs(next.getNutrientValue("proteins")-80*factor)<.0001,"Incorrect loss for native scale at "+loss);
                    h.assertTrue(Math.abs(next.getNutrientValue("test:custom_nutrition")-160*factor)<.0001,"Incorrect loss for custom scale at "+loss);
                    h.assertTrue(next.getMonotony(Items.APPLE)==6&&next.hasDiscovered("group:test:secret"),"Nutrition loss erased unrelated saved state");
                    h.assertTrue(!before.getCapability(CapabilityHandler.FOOD_TOLERANCE).isPresent(),"Death clone revived old capabilities");
                } finally {LDieDietGameTests.removePlayer(before);LDieDietGameTests.removePlayer(after);}
            }
            h.succeed();
        } finally {FixtureConfig.set(setting,original);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_nutrition_journal",timeoutTicks=100)
    public static void markerOnlyShowsReachableRewardTransitions(GameTestHelper h) {
        var manager=NutritionScaleManager.getInstance();var original=manager.serializeToNBT();
        var player=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            AuthorStressGameTests.scale("markers","{\"max_value\":100,\"thresholds\":[{\"min\":80,\"max\":100,\"modifiers\":[{\"attribute\":\"minecraft:generic.max_health\",\"amount\":2}]}]}");
            var cap=CapabilityHandler.get(player);cap.setNutrientValue("stress:markers",80);
            var markers=JournalSnapshot.capture(player,cap).getList("markers",Tag.TAG_COMPOUND);
            int count=0;for(var tag:markers) {var m=(CompoundTag)tag;if(m.getString("scale").equals("stress:markers")) {
                h.assertTrue(m.getDouble("value")==80,"Inclusive 100 created a fake transition");count++;
            }}
            h.assertTrue(count==1,"Reward transition missing or duplicated");
            AuthorStressGameTests.scale("exclusive","{\"max_value\":100,\"thresholds\":[{\"min\":0,\"max\":100,\"min_inclusive\":false,\"max_inclusive\":false,\"modifiers\":[{\"attribute\":\"minecraft:generic.max_health\",\"amount\":1}]}]}");
            markers=JournalSnapshot.capture(player,cap).getList("markers",Tag.TAG_COMPOUND);
            var values=new TreeSet<Double>();for(var tag:markers) {var m=(CompoundTag)tag;if(m.getString("scale").equals("stress:exclusive"))values.add(m.getDouble("value"));}
            h.assertTrue(values.equals(Set.of(0.0,100.0)),"Exclusive endpoint transitions were hidden");h.succeed();
        } finally { manager.deserializeFromNBT(original);LDieDietGameTests.removePlayer(player); }
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_nutrition_journal",timeoutTicks=100)
    public static void actualDietEveryBonusesStackOnceAndStopBelow80(GameTestHelper h) {
        var diet=DietCompatibilityData.INSTANCE;
        if(!diet.suiteNames().contains("builtin")){h.succeed();return;}
        var player=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap=CapabilityHandler.get(player);cap.setDietSuite("builtin");
            var names=List.of("fruits","grains","proteins","vegetables");
            for(String name:diet.suiteGroups("builtin"))cap.setNutrientValue(diet.nutrientId(name),0);
            for(int count=1;count<=4;count++) {
                cap.setNutrientValue(diet.nutrientId(names.get(count-1)),80);
                DietSuiteEffects.refresh(player,cap);DietSuiteEffects.refresh(player,cap);
                h.assertTrue(Math.abs(player.getAttributeValue(Attributes.MAX_HEALTH)-(20+2*count+(count==4?2:0)))<.001,"EVERY health amount at "+count);
                h.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)-.1*count)<.001,"EVERY knockback amount at "+count);
                h.assertTrue(Math.abs(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)-count)<.001,"EVERY toughness amount at "+count);
                cap.setNutrientValue(diet.nutrientId(names.get(count-1)),100);DietSuiteEffects.refresh(player,cap);
                h.assertTrue(Math.abs(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)-count)<.001,"100 should retain, not multiply, the bonus");
            }
            var snapshot=JournalSnapshot.capture(player,cap);String serialized=snapshot.getList("markers",Tag.TAG_COMPOUND).toString();
            h.assertTrue(serialized.contains("ldiediet.journal.match.every")&&serialized.contains("ldiediet.nutrition.fruits"),"Server flattened translation components into English");
            var source=net.minecraft.network.chat.Component.Serializer.toJson(JournalSnapshot.source("ldiediet_diet:builtin:1:2",cap));
            h.assertTrue(source.contains("ldiediet.nutrition.fruits")&&!source.contains("ldiediet_diet:"),"Internal modifier identifier leaked as a source name");
            for(String name:names)cap.setNutrientValue(diet.nutrientId(name),79.9f);
            DietSuiteEffects.refresh(player,cap);
            h.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH)==20&&player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)==0,"Bonuses survived crossing below 80");h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_nutrition_journal",timeoutTicks=100)
    public static void fullHungerPreventsRecoveryButActualFoodRestoresIt(GameTestHelper h) {
        var player=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap=CapabilityHandler.get(player);cap.setMonotony(Items.APPLE,6);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getFoodData().setFoodLevel(20);
            new ItemStack(Items.CARROT).finishUsingItem(player.level(),player);
            h.assertTrue(cap.getMonotony(Items.APPLE)==6,"Full-hunger creative meal bypassed recovery guard");
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.getFoodData().setFoodLevel(0);player.getFoodData().setSaturation(0);
            new ItemStack(Items.CARROT).finishUsingItem(player.level(),player);
            h.assertTrue(cap.getMonotony(Items.APPLE)<6&&cap.getMonotony(Items.APPLE)>0,"Different CCI-1 food did not recover in survival");
            h.assertTrue(cap.getRecoveryMeals(FoodKey.of(Items.APPLE))==1,"Recovery meal counter is incorrect");h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }
}
