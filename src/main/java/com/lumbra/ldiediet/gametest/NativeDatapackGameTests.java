package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.consumption.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.RewardHandler;
import com.lumbra.ldiediet.util.EatingContext;
import com.lumbra.ldiediet.util.ExclusionMatcher;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class NativeDatapackGameTests {
    @GameTestGenerator public static Collection<TestFunction> nativeGroups() {
        if(!Boolean.getBoolean("ldiediet.extendedIntegration"))return List.of();
        var tests=new ArrayList<TestFunction>();
        var groups=FoodGroupManager.getInstance().getAllGroups().stream().filter(g->g.getId().getNamespace().equals("ldiediet")).toList();
        if(groups.size()!=17)throw new IllegalStateException("Expected the 17 original groups");
        for(var group:groups)tests.add(new TestFunction("ldiediet_native_groups","native_group_"+group.getId().getPath(),"minecraft:bastion/blocks/air",100,0,true,h->checkGroup(h,group)));
        tests.add(new TestFunction("ldiediet_native_foods","every_registered_native_food_uses_the_portion_pipeline","minecraft:bastion/blocks/air",100,0,true,NativeDatapackGameTests::checkEveryFood));
        return tests;
    }
    private static void checkGroup(GameTestHelper h,FoodGroupDefinition group) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            p.setInvulnerable(true);
            var cap=CapabilityHandler.get(p);
            List<Item> foods=ForgeRegistries.ITEMS.getValues().stream()
                .filter(item->group.containsItem(ForgeRegistries.ITEMS.getKey(item).toString()))
                .filter(item->ConsumptionSnapshot.isConsumable(new ItemStack(item),p))
                .filter(item->!ExclusionMatcher.isExcluded(new ItemStack(item),false))
                .sorted(Comparator.comparing(item->ForgeRegistries.ITEMS.getKey(item).toString())).toList();
            h.assertTrue(!foods.isEmpty(),"No actual consumable food installed for "+group.getId());
            h.assertTrue(!group.isRequireUnique() || foods.size()>=group.getThreshold(),"Only "+foods.size()+" unique foods installed for "+group.getId()+"; need "+group.getThreshold());
            Set<String> required=new LinkedHashSet<>();
            group.getEffects().forEach(e->required.addAll(e.getRequiresActiveGroups()));
            for(String id:required) {
                ResourceLocation key=new ResourceLocation(com.lumbra.ldiediet.util.GroupIds.canonical(id,group.getId().getNamespace()));
                var definition=FoodGroupManager.getInstance().getGroup(key);
                h.assertTrue(definition!=null,"Missing native prerequisite "+id);
                RewardHandler.activateGroupReward(p,cap,definition);
            }
            for(int i=0;i<group.getThreshold();i++) {
                p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);
                new ItemStack(foods.get(group.isRequireUnique()?i:0)).finishUsingItem(p.level(),p);
            }
            h.assertTrue(cap.getActiveEffects().stream().anyMatch(e->e.getGroupId().equals(group.getId().toString())),"Native meals did not activate "+group.getId());
            h.assertTrue(cap.getHistoryTotalCount()>=group.getThreshold(),"Native meals did not record history for "+group.getId());
            com.lumbra.ldiediet.LDieDiet.LOGGER.info("NATIVE GROUP PASS {} using {}",group.getId(),foods.stream().limit(group.isRequireUnique()?group.getThreshold():1).map(ForgeRegistries.ITEMS::getKey).toList());
            h.succeed();
        }finally{LDieDietGameTests.removePlayer(p);}
    }
    private static void checkEveryFood(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        var results=new com.google.gson.JsonArray();
        try {
            p.setInvulnerable(true);
            var foods=ForgeRegistries.ITEMS.getValues().stream()
                .filter(item->ConsumptionSnapshot.isConsumable(new ItemStack(item),p)).toList();
            for(Item item:foods) {
                var cap=CapabilityHandler.get(p);
                p.removeAllEffects();cap.deserializeNBT(new net.minecraft.nbt.CompoundTag());RewardHandler.reconcileApplied(p,cap);
                p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);
                var stack=new ItemStack(item); var portion=ConsumptionSnapshot.capture(p,stack);
                long before=cap.getHistoryTotalCount();
                stack.finishUsingItem(p.level(),p);
                String id=ForgeRegistries.ITEMS.getKey(item).toString();
                h.assertTrue(cap.getHistoryTotalCount()==before+(portion.excluded()?0:1),"Consumption history incorrect for "+id);
                h.assertTrue(EatingContext.current()==null && cap.getUseSession()==null,"Portion context leaked for "+id);
                h.assertTrue(Double.isFinite(portion.cci()),"Captured CCI is not finite for "+id);
                var row=new com.google.gson.JsonObject();row.addProperty("item",id);row.addProperty("cci",portion.cci());
                row.addProperty("history",cap.getHistoryTotalCount());row.addProperty("hunger",p.getFoodData().getFoodLevel());
                boolean foodBuff = p.getActiveEffects().stream().anyMatch(e->e.getEffect().isBeneficial())
                        && cap.getActiveEffects().isEmpty() && !portion.excluded();
                if (foodBuff) {
                    p.removeAllEffects(); cap.deserializeNBT(new net.minecraft.nbt.CompoundTag()); RewardHandler.reconcileApplied(p,cap);
                    var repeated=new ItemStack(item);
                    cap.setMonotony(DietConfigManager.getFoodKey(repeated,false),com.lumbra.ldiediet.ToleranceStage.getBaseThresholds()[1]);
                    p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);
                    if (!com.lumbra.ldiediet.ToleranceStage.getStage(p,repeated).allowsBuffs()) {
                        repeated.finishUsingItem(p.level(),p);
                        h.assertTrue(p.getActiveEffects().stream().noneMatch(e->e.getEffect().isBeneficial()),"Food buff bypassed monotony penalty for "+id);
                        row.addProperty("beneficial_blocked_stage2",true);
                    }
                }
                results.add(row);
            }
            java.nio.file.Files.writeString(java.nio.file.Path.of("native-food-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
            com.lumbra.ldiediet.LDieDiet.LOGGER.info("NATIVE FOOD PIPELINE PASS {} registered foods",foods.size());
            h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}finally{LDieDietGameTests.removePlayer(p);}
    }
}
