package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.compat.ClimateHelper;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ClimateIntegrationGameTests {
    private static final String TEMPLATE="bastion/blocks/air", BATCH="ldiediet_climate";
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void parsedClimateRewardsWaitRetryAndDoNotRenewFixedSlots(GameTestHelper h) throws Exception {
        if(!ModList.get().isLoaded("cold_sweat") || !ModList.get().isLoaded("sereneseasons")){h.succeed();return;}
        var group=FoodGroupManager.getInstance().getGroup(new ResourceLocation("test_climate:seasonal"));
        h.assertTrue(group!=null,"Missing isolated climate datapack fixture");
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        var storage=Class.forName("sereneseasons.season.SeasonHandler").getMethod("getSeasonSavedData",Level.class).invoke(null,p.level());
        var field=storage.getClass().getField("seasonCycleTicks");int previous=field.getInt(storage);
        var traits=Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
        var setter=Class.forName("com.momosoftworks.coldsweat.api.util.Temperature").getMethod("set",LivingEntity.class,traits,double.class);
        Object core=Arrays.stream(traits.getEnumConstants()).filter(v->((Enum<?>)v).name().equals("CORE")).findFirst().orElseThrow();
        try {
            p.setInvulnerable(true);
            var commands=p.getServer().getCommands();var source=p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            commands.performPrefixedCommand(source,"season set early_summer");setter.invoke(null,p,core,-100d);
            var cap=CapabilityHandler.get(p);double health=p.getMaxHealth();
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE).finishUsingItem(p.level(),p);
            var effects=cap.getActiveEffects().stream().filter(e->e.getGroupId().equals(group.getId().toString())).toList();
            h.assertTrue(effects.size()==3,"Meal did not register all parsed climate slots");
            h.assertTrue(effects.get(0).getState()==com.lumbra.ldiediet.capability.ActiveGroupEffect.State.WAITING,"Unmet permanent climate condition granted immediately");
            h.assertTrue(effects.get(1).getState()==com.lumbra.ldiediet.capability.ActiveGroupEffect.State.EXPIRED,"Unmet fixed climate condition can renew late");
            h.assertTrue(effects.get(2).getState()==com.lumbra.ldiediet.capability.ActiveGroupEffect.State.WAITING,"OR with both conditions false granted immediately");
            h.onEachTick(()->{commands.performPrefixedCommand(source,"season set early_winter");try{setter.invoke(null,p,core,-100d);}catch(Exception error){throw new RuntimeException(error);}p.doTick();});
            h.runAfterDelay(30,()->{
                try {
                    h.assertTrue(effects.get(0).isCurrentlyActive(),"Permanent parsed climate slot did not retry after the season changed");
                    h.assertTrue(effects.get(1).getState()==com.lumbra.ldiediet.capability.ActiveGroupEffect.State.EXPIRED,"Fixed slot was renewed after the season changed");
                    h.assertTrue(Math.abs(p.getMaxHealth()-health-2)<.001,"OR seasonal health reward did not apply");h.succeed();
                }finally{try{field.setInt(storage,previous);}catch(Exception error){throw new RuntimeException(error);}LDieDietGameTests.removePlayer(p);}
            });
        }catch(Throwable error){field.setInt(storage,previous);LDieDietGameTests.removePlayer(p);throw error;}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void biomeConditionsSupportAndOrAndMissingOptionalMods(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            double t=ClimateHelper.getBiomeTemperature(p);
            h.assertTrue(new EffectCondition("AND",t-.01,t+.01,null,null).evaluate(p),"Actual biome bounds did not match");
            h.assertFalse(new EffectCondition("AND",t+1,null,null,null).evaluate(p),"Impossible biome temperature matched");
            h.assertTrue(new EffectCondition("OR",t-.01,t+.01,"impossible",null).evaluate(p),"OR ignored a matching biome");
            h.assertFalse(new EffectCondition("AND",t-.01,t+.01,"impossible",null).evaluate(p),"AND ignored an unmatched temperature state");
            if(!ModList.get().isLoaded("cold_sweat")) h.assertFalse(new EffectCondition("AND",null,null,"normal",null).evaluate(p),"Absent Cold Sweat condition matched");
            if(!ModList.get().isLoaded("sereneseasons")) h.assertFalse(new EffectCondition("AND",null,null,null,"winter").evaluate(p),"Absent season condition matched");
            h.succeed();
        } finally {LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void installedColdSweatProvidesAllFiveStates(GameTestHelper h) throws Exception {
        if(!ModList.get().isLoaded("cold_sweat")){h.succeed();return;}
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var api=Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
            var traits=Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
            var setter=api.getMethod("set",LivingEntity.class,traits,double.class);
            Object core=Arrays.stream(traits.getEnumConstants()).filter(v->((Enum<?>)v).name().equals("CORE")).findFirst().orElseThrow();
            Object base=Arrays.stream(traits.getEnumConstants()).filter(v->((Enum<?>)v).name().equals("BASE")).findFirst().orElseThrow();
            setter.invoke(null,p,base,0d);
            double[] values={-100,-1,0,1,100}; String[] states={"freezing","cold","normal","hot","overheating"};
            for(int i=0;i<values.length;i++) {
                setter.invoke(null,p,core,values[i]);
                h.assertTrue(states[i].equals(ClimateHelper.getColdSweatState(p)),"Cold Sweat state mismatch at "+values[i]);
                h.assertTrue(new EffectCondition("AND",null,null,states[i],null).evaluate(p),"Condition rejected actual Cold Sweat state "+states[i]);
            }
            h.succeed();
        } finally {LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void installedSeasonsProvideFourStatesInEveryLocale(GameTestHelper h) throws Exception {
        if(!ModList.get().isLoaded("sereneseasons")){h.succeed();return;}
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        var storage=Class.forName("sereneseasons.season.SeasonHandler").getMethod("getSeasonSavedData",Level.class).invoke(null,p.level());
        var field=storage.getClass().getField("seasonCycleTicks"); int previous=field.getInt(storage);
        Locale original=Locale.getDefault();
        try {
            for(String season:List.of("spring","summer","autumn","winter")) {
                int code=p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(),"season set early_"+season);
                h.assertTrue(code>0,"Serene Seasons command failed for "+season);
                h.assertTrue(season.equals(ClimateHelper.getCurrentSeason(p.level())),"Incorrect season "+season);
                h.assertTrue(new EffectCondition("AND",null,null,null,season).evaluate(p),"Season condition failed for "+season);
            }
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            h.assertTrue("winter".equals(ClimateHelper.getCurrentSeason(p.level())),"System locale changed the winter identifier");
            h.succeed();
        } finally {Locale.setDefault(original);field.setInt(storage,previous);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void nutritionModifiersApplyToActualColdSweatAttributes(GameTestHelper h) throws Exception {
        if(!ModList.get().isLoaded("cold_sweat")){h.succeed();return;}
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        var scales=NutritionScaleManager.getInstance(); var snapshot=scales.serializeToNBT();
        try {
            var attribute=(net.minecraft.world.entity.ai.attributes.Attribute)Class.forName("com.momosoftworks.coldsweat.util.registries.ModAttributes").getField("COLD_RESISTANCE").get(null);
            var instance=p.getAttribute(attribute); h.assertTrue(instance!=null,"Cold Sweat player attribute is absent");
            ResourceLocation id=ForgeRegistries.ATTRIBUTES.getKey(attribute);
            var root=new net.minecraft.nbt.CompoundTag();var list=new net.minecraft.nbt.ListTag(); var entry=new net.minecraft.nbt.CompoundTag();
            entry.putString("id","test_climate:resistance");
            var json=com.google.gson.JsonParser.parseString("{\"name\":\"Resistance\",\"max_value\":100,\"thresholds\":[{\"min\":50,\"max\":100,\"modifiers\":[{\"attribute\":\""+id+"\",\"amount\":0.2,\"operation\":\"addition\"}]}]}");
            entry.put("definition",com.lumbra.ldiediet.util.JsonNbt.encode(json));list.add(entry);root.put("definitions",list);
            scales.deserializeFromNBT(root);
             
             
            instance.setBaseValue(.25);
            var cap=CapabilityHandler.get(p); double before=instance.getValue(); cap.setNutrientValue("test_climate:resistance",60);
            NutritionAttributeHandler.refresh(p,cap);
            h.assertTrue(Math.abs(instance.getValue()-before-.2)<.001,"Foreign attribute modifier was not applied");
            NutritionAttributeHandler.refresh(p,cap);
            h.assertTrue(Math.abs(instance.getValue()-before-.2)<.001,"Foreign attribute modifier duplicated");
            cap.setNutrientValue("test_climate:resistance",40);NutritionAttributeHandler.refresh(p,cap);
            h.assertTrue(Math.abs(instance.getValue()-before)<.001,"Foreign modifier remained below threshold");
            h.succeed();
        } finally {scales.deserializeFromNBT(snapshot);LDieDietGameTests.removePlayer(p);}
    }
}
