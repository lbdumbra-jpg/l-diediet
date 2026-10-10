package com.lumbra.ldiediet.data;

import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.consumption.*;
import com.lumbra.ldiediet.event.FoodEatingHandler;
import com.lumbra.ldiediet.math.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Items;
import java.util.*;

 
public final class JournalFood {
    private JournalFood() {}
    private record ForecastKey(long revision,double hunger,double saturation,Double quality,double cci,double base,
                               float current,double floor,List<Integer> boundaries) {}
    private static final Map<ForecastKey,int[]> CACHE=new LinkedHashMap<>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ForecastKey,int[]> e){return size()>2048;}
    };
    public static ListTag capture(Player player,IFoodTolerance cap) {
        var result=new ListTag();
        for(var key:cap.getEatenCounts().keySet()) {
            var stack=cap.foodSample(key);if(stack.isEmpty())stack=DietConfigManager.representative(key);
             
            var captured=stack.is(Items.CAKE)?ConsumptionSnapshot.capture(player,stack,new FoodProperties.Builder().nutrition(2).saturationMod(.1f).build()):ConsumptionSnapshot.capture(player,stack);
            var external=cap.externalNutrition(key);
            var portion=external==null?captured:captured.withNutrition(external[0],external[1]);
            var e=new CompoundTag();key.write(e);
            float current=cap.getMonotony(key);var stage=portion.excluded()?ToleranceStage.STAGE_0:ToleranceStage.forStack(stack,current,false);
            e.putInt("stage",stage.getLevel());
            int[] thresholds=portion.rules().thresholds().stream().mapToInt(Integer::intValue).toArray();
            e.putIntArray("thresholds",thresholds);
            boolean fixed=portion.excluded()||ExclusionManager.getInstance().getStageOverrides().containsKey(key.item());
            e.putBoolean("fixed",fixed);
            if(!fixed&&portion.key().equals(key)) {
                var cacheKey=new ForecastKey(FormulaManager.revision()+31*FoodStageProfiles.SERVER.revision(),portion.hunger(),portion.saturation(),portion.qualityOverride(),portion.cci(),portion.baseMonotony(),current,
                        LDieDietServerConfig.MIN_MONOTONY_PER_MEAL.get(),Arrays.stream(thresholds).boxed().toList());
                int[] forecast=CACHE.computeIfAbsent(cacheKey,k->MealForecast.remaining(current,thresholds,FormulaManager.getMonotonyCap(),m->FoodEatingHandler.mealIncrease(portion,(float)m)));
                e.putIntArray("remaining",forecast);
            }
            var details=new ArrayList<Component>();
            details.add(Component.translatable("ldiediet.journal.stage_name."+stage.getLevel()));
            double factor=portion.penalty().multiplier();
            int hunger=FoodPenalty.apply(0,0,portion.hunger(),0,factor,portion.penalty().minimumHunger(),false).hunger();
            double saturation=portion.saturation()*(portion.excluded()?1:portion.rules().saturation().get(stage.getLevel()));
            details.add(Component.translatable("ldiediet.journal.portion_food",hunger,JournalSnapshot.number(saturation)));
            if(!stack.is(Items.CAKE)&&stack.getUseDuration()>0)details.add(Component.translatable("ldiediet.journal.portion_time",JournalSnapshot.number(stack.getUseDuration()*portion.chewingMultiplier()/20.0)));
            if(!portion.excluded()) {
                details.add(Component.translatable(portion.allowsBuffs()?"ldiediet.journal.buffs_yes":"ldiediet.journal.buffs_no"));
                if(!portion.rules().allowedEffects().isEmpty())details.add(Component.translatable("ldiediet.journal.effects_allowed",effectNames(portion.rules().allowedEffects())));
                if(!portion.rules().blockedEffects().isEmpty())details.add(Component.translatable("ldiediet.journal.effects_blocked",effectNames(portion.rules().blockedEffects())));
                for(var n:portion.nutrients().entrySet()) {
                    double multiplier=1;
                    for(var effect:cap.getActiveEffects())if(effect.isCurrentlyActive()&&effect.isNutrientMultiplier()&&("all".equals(effect.getEffectId())||"*".equals(effect.getEffectId())||n.getKey().equals(effect.getEffectId())))multiplier*=Math.max(0,1+effect.getValue());
                    double gain=portion.foodValue()<=0||portion.nutrientMultiplier()<=0?0:FormulaManager.evaluate("nutrient_add",Map.of("base_n",portion.foodValue()*n.getValue()*multiplier,"M",(double)current,"monotony",(double)current,"stage_multiplier",portion.nutrientMultiplier()));
                    if(!Double.isFinite(gain)||Math.abs(gain)>Float.MAX_VALUE)gain=0;
                    var scale=NutritionScaleManager.getInstance().get(n.getKey());
                    details.add(Component.translatable("ldiediet.journal.nutrient_gain",scale==null?Component.literal(n.getKey()):scale.displayName(),JournalSnapshot.number(gain)));
                }
            }
            e.put("details",JournalSnapshot.components(details));result.add(e);
        }
        return result;
    }
    private static Component effectNames(Set<String> ids){
        var result=Component.empty();boolean first=true;
        for(String id:new TreeSet<>(ids)){if(!first)result.append(", ");first=false;var effect=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(new net.minecraft.resources.ResourceLocation(id));result.append(effect==null?Component.literal(id):Component.translatable(effect.getDescriptionId()));}return result;
    }
    public static List<Component> stageHelp() {
        var result=new ArrayList<Component>();result.add(Component.translatable("ldiediet.journal.help.food"));
        for(var stage:ToleranceStage.values())result.add(Component.translatable("ldiediet.journal.help.stage",
                Component.translatable("ldiediet.journal.stage_name."+stage.getLevel()),
                JournalSnapshot.number(stage.getHungerMultiplier()*100),JournalSnapshot.number(stage.getChewingMultiplier()),
                JournalSnapshot.number(stage.getDietScaleMultiplier()*100),Component.translatable(stage.allowsBuffs()?"ldiediet.journal.allowed":"ldiediet.journal.blocked")));
        if(FoodStageProfiles.SERVER.hasProfiles())result.add(Component.translatable("ldiediet.journal.help.food_profiles"));
        result.addAll(recoveryHelp());return result;
    }

    public static List<Component> recoveryHelp() {
        var result = new ArrayList<Component>();
        result.add(FormulaManager.requiresHungerForRecovery()
                ? Component.translatable("ldiediet.journal.help.recovery_hunger", FormulaManager.getRecoveryMinHungerRestored())
                : Component.translatable("ldiediet.journal.help.recovery_full"));
        if (!FormulaManager.usesDefaultRecoveryFormula())
            result.add(Component.translatable("ldiediet.journal.help.recovery_custom"));
        else if (FormulaManager.getRecoveryAcceleration() > 0)
            result.add(Component.translatable("ldiediet.journal.help.recovery_acceleration",
                    FormulaManager.getRecoveryDelay(), JournalSnapshot.number(FormulaManager.getRecoveryAcceleration() * 100)));
        else result.add(Component.translatable("ldiediet.journal.help.recovery_acceleration_off"));
        result.add(Component.translatable(FormulaManager.resetsRecoveryOnEat()
                ? "ldiediet.journal.help.recovery_reset" : "ldiediet.journal.help.recovery_keep"));
        result.add(Component.translatable("ldiediet.journal.help.recovery_wait"));
        return result;
    }
}
