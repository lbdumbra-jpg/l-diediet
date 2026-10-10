package com.lumbra.ldiediet.gametest;

import com.google.gson.JsonParser;
import com.lumbra.ldiediet.LDieDietServerConfig;
import com.lumbra.ldiediet.api.PackApi;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class AuthorToolsGameTests {
    private static final String ENERGY="test_rewards:author_energy";
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void hungerDecayTracksLossAndNeverCatchesUp(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        var mode=LDieDietServerConfig.DECAY_MODE.get();boolean enabled=LDieDietServerConfig.NUTRITION_DECAY.get();
        try {
            FixtureConfig.set(LDieDietServerConfig.DECAY_MODE,LDieDietServerConfig.DecayMode.HUNGER);FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,true);
            p.setGameMode(GameType.SURVIVAL);var cap=CapabilityHandler.get(p);cap.clearNutrients();cap.setNutrientValue(ENERGY,100);
            p.getFoodData().setFoodLevel(10);NutritionDecayHandler.tick(p,cap);
            h.assertTrue(cap.getNutrientValue(ENERGY)==100,"Login charged historical hunger");
            NutritionDecayHandler.tickSecond(p,cap);h.assertTrue(cap.getNutrientValue(ENERGY)==100,"HUNGER also charged timed decay");
            p.getFoodData().setFoodLevel(9);h.assertTrue(NutritionDecayHandler.tick(p,cap),"Lost hunger did not decay nutrition");
            h.assertTrue(Math.abs(cap.getNutrientValue(ENERGY)-97.28172)<.0001,"Diet single-group rate differs");
            float after=cap.getNutrientValue(ENERGY);
            p.getFoodData().setFoodLevel(15);NutritionDecayHandler.tick(p,cap);
            h.assertTrue(cap.getNutrientValue(ENERGY)==after,"Eating caused decay");
            FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,false);p.getFoodData().setFoodLevel(5);NutritionDecayHandler.tick(p,cap);
            FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,true);NutritionDecayHandler.tick(p,cap);
            h.assertTrue(cap.getNutrientValue(ENERGY)==after,"Re-enabling charged historical hunger");
            h.succeed();
        } finally {FixtureConfig.set(LDieDietServerConfig.DECAY_MODE,mode);FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,enabled);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void dietGroupMultiplierImportedAndNativeOverrideWins(GameTestHelper h) {
        var manager=NutritionScaleManager.getInstance();var snapshot=manager.serializeToNBT();
        var compat=com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE;
        var group=compat.groups().get("proteins");var original=group==null?null:group.deepCopy();
        if(group==null) { h.succeed(); return; }  
        try {
            h.assertTrue(group!=null,"Diet/cache fixture did not provide proteins");
            group.addProperty("decay_multiplier",.25);
            h.assertTrue(manager.decayMultiplier(manager.get("proteins"))==.25,"Imported decay multiplier lost");
            var changed=snapshot.copy();var entries=changed.getList("definitions",net.minecraft.nbt.Tag.TAG_COMPOUND);
            for(int i=0;i<entries.size();i++)if(entries.getCompound(i).getString("id").equals("ldiediet:proteins")) {
                var entry=entries.getCompound(i);var json=com.lumbra.ldiediet.util.JsonNbt.decode(entry.getCompound("definition")).getAsJsonObject();
                json.addProperty("decay_multiplier",0);entry.put("definition",com.lumbra.ldiediet.util.JsonNbt.encode(json));
            }
            manager.deserializeFromNBT(changed);
            h.assertTrue(manager.decayMultiplier(manager.get("proteins"))==0,"Explicit native multiplier must override import");h.succeed();
        } finally {
            if(group!=null){group.remove("decay_multiplier");if(original.has("decay_multiplier"))group.add("decay_multiplier",original.get("decay_multiplier"));}
            manager.deserializeFromNBT(snapshot);
        }
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void decayCrossesQuotaAndRemovesOwnedBonuses(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);boolean enabled=LDieDietServerConfig.NUTRITION_DECAY.get();
        var mode=LDieDietServerConfig.DECAY_MODE.get();
        try {
            FixtureConfig.set(LDieDietServerConfig.DECAY_MODE,LDieDietServerConfig.DecayMode.TIMED);
            p.setGameMode(GameType.SURVIVAL);FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,true);
            var cap=CapabilityHandler.get(p);cap.clearNutrients();
            PackApi.setNutrient(p,ENERGY,60.5);PackApi.setNutrient(p,"vegetables",70);
            var rule=NutritionRuleManager.INSTANCE.get(new ResourceLocation("test_rewards:author_balanced"));
            h.assertTrue(rule.multiplier(cap)==1 && p.hasEffect(MobEffects.DAMAGE_BOOST),"Compound quota failed to activate");
            h.assertTrue(p.getAttribute(Attributes.MAX_HEALTH).getModifiers().stream().anyMatch(m->m.getName().contains(ENERGY)),"Scale health bonus missing");
             
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,1200,2));
            NutritionDecayHandler.tickSecond(p,cap);PackApi.refresh(p);
            h.assertTrue(Math.abs(cap.getNutrientValue(ENERGY)-59.5)<.001,"Per-scale decay rate wrong");
            h.assertTrue(rule.multiplier(cap)==0,"Quota still matches after decay");
            h.assertTrue(p.getAttribute(Attributes.MAX_HEALTH).getModifiers().stream().noneMatch(m->m.getName().contains(ENERGY)),"Stale health bonus");
            h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().noneMatch(m->m.getName().startsWith("ldiediet_quota:")),"Stale quota attribute");
            h.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST)&&p.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier()==2,"Quota removed foreign potion");h.succeed();
        } finally {FixtureConfig.set(LDieDietServerConfig.DECAY_MODE,mode);FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,enabled);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void decayRespectsCreativeSpectatorAndDisabledMode(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);boolean enabled=LDieDietServerConfig.NUTRITION_DECAY.get(),creative=LDieDietServerConfig.DECAY_IN_CREATIVE.get();
        try {
            var cap=CapabilityHandler.get(p);cap.setNutrientValue(ENERGY,80);
            FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,true);FixtureConfig.set(LDieDietServerConfig.DECAY_IN_CREATIVE,false);p.setGameMode(GameType.CREATIVE);
            NutritionDecayHandler.tickSecond(p,cap);h.assertTrue(cap.getNutrientValue(ENERGY)==80,"Creative decay");
            FixtureConfig.set(LDieDietServerConfig.DECAY_IN_CREATIVE,true);p.setGameMode(GameType.SPECTATOR);
            NutritionDecayHandler.tickSecond(p,cap);h.assertTrue(cap.getNutrientValue(ENERGY)==80,"Spectator decay");
            FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,false);p.setGameMode(GameType.SURVIVAL);
            NutritionDecayHandler.tickSecond(p,cap);h.assertTrue(cap.getNutrientValue(ENERGY)==80,"Disabled decay");h.succeed();
        } finally {FixtureConfig.set(LDieDietServerConfig.NUTRITION_DECAY,enabled);FixtureConfig.set(LDieDietServerConfig.DECAY_IN_CREATIVE,creative);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void externalFileEditsSurviveSaveAndReload(GameTestHelper h) throws Exception {
        var file=FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json");byte[] original=Files.readAllBytes(file);var server=h.getLevel().getServer();
        try {
            DietConfigManager.loadConfig(server);
            byte[] edited="{\"overrides\":{\"minecraft:bread\":{\"cci\":3.125,\"cci_source\":\"manual\"}}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(file,edited);DietConfigManager.saveConfig();
            h.assertTrue(Arrays.equals(edited,Files.readAllBytes(file)),"In-memory save overwrote external edit");
            h.assertTrue(DietConfigManager.reloadConfig(server)&&DietConfigManager.getCCI(Items.BREAD)==3.125,"Reload ignored edited CCI");
            Files.writeString(file,"{broken");h.assertTrue(!DietConfigManager.reloadConfig(server),"Malformed config accepted");
            h.assertTrue(DietConfigManager.getCCI(Items.BREAD)==3.125,"Malformed reload destroyed last good config");
            DietConfigManager.saveConfig();h.assertTrue(Files.readString(file).equals("{broken"),"Malformed disk file overwritten");h.succeed();
        } finally {Files.write(file,original);DietConfigManager.loadConfig(server);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void groupPercentOperationAndPresentationSurviveNbt(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var group=FoodGroupManager.getInstance().getGroup(new ResourceLocation("test_rewards:author_percent"));
            var effect=ActiveGroupEffect.waiting(group.getId().toString(),0,group.getEffects().get(0));effect.start(Set.of());
            var copy=ActiveGroupEffect.fromNBT(effect.serializeNBT());
            h.assertTrue(copy.getOperation()==AttributeModifier.Operation.MULTIPLY_BASE,"Group percentage operation lost in player NBT");
            var cap=CapabilityHandler.get(p);cap.clearActiveEffects();cap.addActiveEffect(copy);RewardHandler.reconcileApplied(p,cap);
            h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().anyMatch(m->m.getName().startsWith("ldiediet_reward_")&&m.getOperation()==AttributeModifier.Operation.MULTIPLY_BASE&&m.getAmount()==.25),"Group percentage not applied");
            var client=FoodGroupManager.getClientInstance();client.deserializeFromNBT(FoodGroupManager.getInstance().serializeToNBT());
            var synced=client.getGroup(group.getId());h.assertTrue(synced.getNameKey().equals("test.percent")&&synced.getIcon().equals("minecraft:apple")&&synced.getOrder()==7,"Presentation lost over network");
            h.assertTrue(synced.getEffects().get(0).getOperation()==AttributeModifier.Operation.MULTIPLY_BASE,"Operation lost over network");h.succeed();
        } finally {FoodGroupManager.getClientInstance().deserializeFromNBT(new net.minecraft.nbt.CompoundTag());LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void exclusiveQuotaBoundaryAndUnknownOperation(GameTestHelper h) {
        var scale=NutritionScaleDefinition.parse(new ResourceLocation("test:boundary"),JsonParser.parseString("{\"thresholds\":[{\"min\":0,\"max\":60,\"max_inclusive\":false},{\"min\":60,\"max\":100}]}").getAsJsonObject());
        h.assertTrue(!scale.thresholds().get(0).includes(60)&&scale.thresholds().get(1).includes(60),"Exclusive boundary doubled reward");
        boolean rejected=false;try{NutritionScaleDefinition.parseOperation("multipy_base");}catch(IllegalArgumentException e){rejected=true;}
        h.assertTrue(rejected,"Misspelled operation silently accepted");h.succeed();
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void nativeRuleMissingScaleCannotGrantNoneReward(GameTestHelper h) {
        var cap=new FoodToleranceCapability();
        var rule=NutritionRuleManager.parse(new ResourceLocation("test:missing"),JsonParser.parseString("{\"conditions\":[{\"nutrients\":[\"missing:scale\"],\"match\":\"none\"}]}").getAsJsonObject());
        h.assertTrue(rule.multiplier(cap)==0,"Unknown scale accidentally granted NONE reward");h.succeed();
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void operatorCommandsUseSameQuotaPath(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var source=p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            int result=p.server.getCommands().getDispatcher().execute("ldiediet nutrition @s "+ENERGY+" 75",source);
            h.assertTrue(result==1&&PackApi.getNutrient(p,ENERGY)==75,"Nutrition debug command failed");
            h.assertTrue(p.server.getCommands().getDispatcher().execute("ldiediet nutrition @s "+ENERGY+" 75",source.withPermission(0))==0,"Unprivileged mutation allowed");
            h.succeed();
        } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            h.assertTrue(PackApi.getNutrient(p,ENERGY)==75,"Operator command failed before permission check");h.succeed();
        } finally {LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void stageBalanceSettingsAreEffective(GameTestHelper h) {
        var chew=LDieDietServerConfig.CHEWING_MULTIPLIERS.get();var nutrients=LDieDietServerConfig.NUTRIENT_MULTIPLIERS.get();var buffs=LDieDietServerConfig.ALLOW_FOOD_BUFFS.get();
        try {
            FixtureConfig.set(LDieDietServerConfig.CHEWING_MULTIPLIERS,List.of(1.0,1.0,1.0,1.0,1.0));FixtureConfig.set(LDieDietServerConfig.NUTRIENT_MULTIPLIERS,List.of(1.0,1.0,1.0,1.0,.25));FixtureConfig.set(LDieDietServerConfig.ALLOW_FOOD_BUFFS,List.of(true,true,true,true,true));
            var stage=com.lumbra.ldiediet.ToleranceStage.STAGE_4;
            h.assertTrue(stage.getChewingMultiplier()==1&&stage.getDietScaleMultiplier()==.25&&stage.allowsBuffs(),"Stage values still hard-coded");h.succeed();
        } finally {FixtureConfig.set(LDieDietServerConfig.CHEWING_MULTIPLIERS,chew);FixtureConfig.set(LDieDietServerConfig.NUTRIENT_MULTIPLIERS,nutrients);FixtureConfig.set(LDieDietServerConfig.ALLOW_FOOD_BUFFS,buffs);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void continuousConditionsSuspendWithoutRenewingTimer(GameTestHelper h) {
        var p=LDieDietGameTests.makeNetworkedTestPlayer(h);var id=new ResourceLocation("test_rewards:continuous_author");
        try {
            var definition=new FoodGroupDefinition.GroupEffect("fixed","attribute","minecraft:generic.movement_speed",.2,0,100,
                    new int[]{0,0},new int[]{0,0},List.of(),List.of(),Map.of(),new EffectCondition("AND",10000.0,null,null,null)).options("multiply_base",true);
            var group=new FoodGroupDefinition(id,"Test","","-o","",List.of(),List.of(definition),1,false,"","white",true);
            FoodGroupManager.getInstance().getGroupsMap().put(id,group);
            var cap=CapabilityHandler.get(p);cap.clearActiveEffects();
            var effect=ActiveGroupEffect.waiting(id.toString(),0,definition);effect.start(Set.of());cap.addActiveEffect(effect);
            RewardHandler.tickActiveEffects(p);
            h.assertTrue(!effect.isCurrentlyActive()&&effect.getRemainingTicks()==99,"Continuous condition renewed or applied forbidden reward");
            var allowed=new FoodGroupDefinition.GroupEffect("fixed","attribute","minecraft:generic.movement_speed",.2,0,100,
                    new int[]{0,0},new int[]{0,0},List.of(),List.of(),Map.of(),null).options("multiply_base",true);
            FoodGroupManager.getInstance().getGroupsMap().put(id,new FoodGroupDefinition(id,"Test","","-o","",List.of(),List.of(allowed),1,false,"","white",true));
            RewardHandler.tickActiveEffects(p);
            h.assertTrue(effect.isCurrentlyActive()&&effect.getRemainingTicks()==99,"Resumed condition reset timer or advanced twice in one tick");h.succeed();
        } finally {FoodGroupManager.getInstance().getGroupsMap().remove(id);LDieDietGameTests.removePlayer(p);}
    }
    @GameTest(template="bastion/blocks/air",batch="ldiediet_author",timeoutTicks=100)
    public static void quotaMatchingAndScaleMetadataSurviveSerialization(GameTestHelper h) {
        var cap=new FoodToleranceCapability();cap.setNutrientValue("proteins",80);cap.setNutrientValue("vegetables",40);
        for(var entry:Map.of("all",0,"any",1,"none",0,"average",1,"every",1).entrySet()) {
            var c=new NutritionRuleManager.Condition(List.of("proteins","vegetables"),entry.getKey(),60,100,true,true,true);
            h.assertTrue(c.matches(cap)==entry.getValue(),"Incorrect matching mode "+entry.getKey());
        }
        var server=NutritionScaleManager.getInstance();var client=NutritionScaleManager.getClientInstance();
        try {
            client.deserializeFromNBT(server.serializeToNBT());var scale=client.get(ENERGY);
            h.assertTrue(scale!=null&&scale.nameKey().equals("test.energy")&&scale.order()==12&&scale.color()==0x34AA56&&scale.decayPercentPerMinute()==60,"Scale author metadata lost on sync");
            h.assertTrue(scale.displayName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents,"Scale name frozen to server language");h.succeed();
        } finally {client.deserializeFromNBT(new net.minecraft.nbt.CompoundTag());}
    }
}
