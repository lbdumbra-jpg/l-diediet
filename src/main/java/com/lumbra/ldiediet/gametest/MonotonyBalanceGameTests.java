package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("minecraft") @PrefixGameTestTemplate(false)
public final class MonotonyBalanceGameTests {
    private static final String CONFIG="{\"minecraft:bread\":{\"cci\":4},\"minecraft:apple\":{\"cci\":1},\"minecraft:golden_apple\":{\"cci\":4}}";
    private static void near(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<.0001,message+": "+actual+" != "+expected);
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_balance",timeoutTicks=100)
    public static void realWeakFoodRecoversComplexFoodInFortyMeals(GameTestHelper h)throws Exception {
        ConsumptionLogicGameTests.withConfig(h,CONFIG,()->{
            var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var cap=CapabilityHandler.get(p);var bread=FoodKey.of(Items.BREAD);cap.setMonotony(bread,6);
                for(int n=1;n<=40;n++) {
                    p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);
                    new ItemStack(Items.APPLE).finishUsingItem(p.level(),p);
                    if(n<40)h.assertTrue(cap.getMonotony(bread)>0,"Recovered before predicted meal "+n);
                }
                near(h,cap.getMonotony(bread),0,"Did not recover on meal 40");
                h.assertTrue(cap.getRecoveryMeals(bread)==0,"Complete recovery retained stale acceleration");
                h.assertTrue(cap.getEatenCounts().get(FoodKey.of(Items.APPLE))==40,"Food completion was counted twice");
                h.succeed();
            }finally{LDieDietGameTests.removePlayer(p);}
        });
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_balance",timeoutTicks=100)
    public static void fullHungerAndZeroQualityDoNotAdvanceRecovery(GameTestHelper h)throws Exception {
        ConsumptionLogicGameTests.withConfig(h,CONFIG,()->{
            var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var cap=CapabilityHandler.get(p);var bread=FoodKey.of(Items.BREAD);
                cap.setMonotony(bread,6);cap.setRecoveryMeals(bread,20);
                p.getFoodData().setFoodLevel(20);
                new ItemStack(Items.GOLDEN_APPLE).finishUsingItem(p.level(),p);
                near(h,cap.getMonotony(bread),6,"Always-edible food at full hunger recovered another food");
                h.assertTrue(cap.getRecoveryMeals(bread)==20,"Full-hunger portion advanced acceleration");
                new ItemStack(Items.POTION).finishUsingItem(p.level(),p);
                near(h,cap.getMonotony(bread),6,"Zero-quality drink recovered another food");
                h.assertTrue(cap.getRecoveryMeals(bread)==20,"Zero-quality drink advanced acceleration");
                cap.tick(1000000);h.assertTrue(cap.getRecoveryMeals(bread)==20,"Waiting advanced acceleration");
                h.succeed();
            }finally{LDieDietGameTests.removePlayer(p);}
        });
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_balance",timeoutTicks=100)
    public static void twentyFirstMealPersistsAndEatingTargetResetsCounter(GameTestHelper h)throws Exception {
        ConsumptionLogicGameTests.withConfig(h,CONFIG,()->{
            var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var cap=CapabilityHandler.get(p);var bread=FoodKey.of(Items.BREAD);
                cap.setMonotony(bread,6);cap.setRecoveryMeals(bread,20);
                var restored=new FoodToleranceCapability();restored.deserializeNBT(cap.serializeNBT());cap.copyFrom(restored);
                p.getFoodData().setFoodLevel(0);
                new ItemStack(Items.APPLE).finishUsingItem(p.level(),p);
                near(h,cap.getMonotony(bread),5.7883632,"21st meal did not apply 108% recovery");
                h.assertTrue(cap.getRecoveryMeals(bread)==21,"Counter lost on copy/restore");
                p.getFoodData().setFoodLevel(0);
                new ItemStack(Items.BREAD).finishUsingItem(p.level(),p);
                h.assertTrue(cap.getRecoveryMeals(bread)==0,"Eating target did not reset the streak");
                h.succeed();
            }finally{LDieDietGameTests.removePlayer(p);}
        });
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_balance",timeoutTicks=100)
    public static void equalCciOneVariantsRecoverAndKeepSeparateCounters(GameTestHelper h)throws Exception {
        ConsumptionLogicGameTests.withConfig(h,"{\"minecraft:bread\":{\"cci\":1,\"nbt_filters\":[{\"variant_id\":\"red\",\"match\":\"{Flavor:'red'}\"},{\"variant_id\":\"blue\",\"match\":\"{Flavor:'blue'}\"}]}}",()->{
            var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
            try {
                var red=new ItemStack(Items.BREAD);red.getOrCreateTag().putString("Flavor","red");
                var blue=new ItemStack(Items.BREAD);blue.getOrCreateTag().putString("Flavor","blue");
                var r=DietConfigManager.getFoodKey(red,false);var b=DietConfigManager.getFoodKey(blue,false);
                var cap=CapabilityHandler.get(p);cap.setMonotony(r,6);cap.setMonotony(b,6);
                cap.setRecoveryMeals(r,20);cap.setRecoveryMeals(b,7);
                p.getFoodData().setFoodLevel(0);red.finishUsingItem(p.level(),p);
                near(h,cap.getMonotony(b),5.692996,"CCI1 did not recover another CCI1 variant");
                h.assertTrue(cap.getRecoveryMeals(r)==0&&cap.getRecoveryMeals(b)==8,"Variant counters were merged");
                var copy=new FoodToleranceCapability();copy.deserializeNBT(cap.serializeNBT());
                h.assertTrue(copy.getRecoveryMeals(r)==0&&copy.getRecoveryMeals(b)==8,"Variant counters lost in NBT");
                h.succeed();
            }finally{LDieDietGameTests.removePlayer(p);}
        });
    }

    @GameTest(template="bastion/blocks/air",batch="ldiediet_balance",timeoutTicks=100)
    public static void legacyLargeValuesAreCappedWithoutErasingHistory(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var cap=CapabilityHandler.get(p);var key=FoodKey.of(Items.BREAD);
            cap.setMonotony(key,5000);cap.recordEaten(key,8);cap.addHistory(key);
            var old=cap.serializeNBT();old.putInt("schema_version",5);
            for(var tag:old.getList("monotonyEntries",net.minecraft.nbt.Tag.TAG_COMPOUND))
                ((net.minecraft.nbt.CompoundTag)tag).getCompound("data").remove("recoveryMeals");
            cap.deserializeNBT(old);CapabilityHandler.syncToClient(p);
            near(h,cap.getMonotony(key),24,"Old unbounded value survived the server cap");
            h.assertTrue(cap.getRecoveryMeals(key)==0,"Legacy save got a free acceleration streak");
            h.assertTrue(cap.getEatenCounts().get(key)==1&&cap.getEatingHistory().size()==1,"Migration erased history");
            h.succeed();
        }finally{LDieDietGameTests.removePlayer(p);}
    }
}
