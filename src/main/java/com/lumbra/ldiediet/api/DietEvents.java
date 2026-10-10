package com.lumbra.ldiediet.api;

import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.consumption.ConsumptionSnapshot;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.math.FormulaManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.*;
import java.util.*;

 
public final class DietEvents {
    private DietEvents() {}
    public abstract static class PlayerHook extends Event {
        private final Player player;
        protected PlayerHook(Player player){this.player=player;}
        public Player getPlayer(){return player;}
    }
    @Cancelable public static class MealPre extends PlayerHook {
        private ConsumptionSnapshot portion;
        private final String source;
        public MealPre(Player p,ConsumptionSnapshot portion,String source){super(p);this.portion=portion;this.source=source;}
        public ConsumptionSnapshot getPortion(){return portion;}
        public ItemStack getFood(){return portion.stack();}
        public String getSource(){return source;}
        public void setNutrition(double hunger,double saturation){portion=portion.withNutrition(hunger,saturation);}
    }
    public static class MealPost extends PlayerHook {
        private final ConsumptionSnapshot portion;private final String source;private final int hungerRestored;
        public MealPost(Player p,ConsumptionSnapshot portion,String source,int hungerRestored){super(p);this.portion=portion;this.source=source;this.hungerRestored=hungerRestored;}
        public ConsumptionSnapshot getPortion(){return portion;}public String getSource(){return source;}public int getHungerRestored(){return hungerRestored;}
    }
    @Cancelable public static class MonotonyPre extends PlayerHook {
        private final FoodKey food;private final float before;private final String reason;private double value;
        public MonotonyPre(Player p,FoodKey food,float before,double value,String reason){super(p);this.food=food;this.before=before;this.reason=reason;setValue(value);}
        public FoodKey getFood(){return food;}public float getBefore(){return before;}public double getValue(){return value;}public String getReason(){return reason;}
        public void setValue(double value){finite(value);this.value=value;}
    }
    public abstract static class StageChange extends PlayerHook {
        private final FoodKey food;private final int before,after;private final String reason;
        protected StageChange(Player p,FoodKey food,int before,int after,String reason){super(p);this.food=food;this.before=before;this.after=after;this.reason=reason;}
        public FoodKey getFood(){return food;}public int getBefore(){return before;}public int getAfter(){return after;}public String getReason(){return reason;}
    }
    @Cancelable public static class StagePre extends StageChange {
        public StagePre(Player p,FoodKey food,int before,int after,String reason){super(p,food,before,after,reason);}
    }
    public static class StagePost extends StageChange {
        public StagePost(Player p,FoodKey food,int before,int after,String reason){super(p,food,before,after,reason);}
    }
    public abstract static class RecoveryChange extends PlayerHook {
        private final FoodKey food,other;private final float before;private final int meal;private double value;
        protected RecoveryChange(Player p,FoodKey food,FoodKey other,float before,double value,int meal){super(p);this.food=food;this.other=other;this.before=before;this.meal=meal;setValue(value);}
        public FoodKey getFood(){return food;}public FoodKey getOtherFood(){return other;}public float getBefore(){return before;}public double getValue(){return value;}public int getMeal(){return meal;}
        public void setValue(double value){finite(value);this.value=value;}
    }
    @Cancelable public static class RecoveryPre extends RecoveryChange {
        public RecoveryPre(Player p,FoodKey food,FoodKey other,float before,double value,int meal){super(p,food,other,before,value,meal);}
    }
    public static class RecoveryPost extends RecoveryChange {
        public RecoveryPost(Player p,FoodKey food,FoodKey other,float before,double value,int meal){super(p,food,other,before,value,meal);}
    }
    public abstract static class NutrientChange extends PlayerHook {
        private final String nutrient,reason;private final float before;private double value;
        protected NutrientChange(Player p,String nutrient,float before,double value,String reason){super(p);this.nutrient=nutrient;this.before=before;this.reason=reason;setValue(value);}
        public String getNutrient(){return nutrient;}public String getReason(){return reason;}public float getBefore(){return before;}public double getValue(){return value;}
        public void setValue(double value){finite(value);this.value=value;}
    }
    @Cancelable public static class NutrientPre extends NutrientChange {
        public NutrientPre(Player p,String nutrient,float before,double value,String reason){super(p,nutrient,before,value,reason);}
    }
    public static class NutrientPost extends NutrientChange {
        public NutrientPost(Player p,String nutrient,float before,double value,String reason){super(p,nutrient,before,value,reason);}
    }
    @Cancelable public static class QuotaPre extends PlayerHook {
        private final String quota;private int multiplier;
        public QuotaPre(Player p,String quota,int multiplier){super(p);this.quota=quota;setMultiplier(multiplier);}
        public String getQuota(){return quota;}public int getMultiplier(){return multiplier;}
        public void setMultiplier(int multiplier){if(multiplier<0||multiplier>1000)throw new IllegalArgumentException("multiplier must be 0..1000");this.multiplier=multiplier;}
    }
    public static class QuotaPost extends PlayerHook {
        private final String quota;private final int before,after;
        public QuotaPost(Player p,String quota,int before,int after){super(p);this.quota=quota;this.before=before;this.after=after;}
        public String getQuota(){return quota;}public int getBefore(){return before;}public int getAfter(){return after;}
    }
    public abstract static class DiscoveryChange extends PlayerHook {
        private final String discovery;
        protected DiscoveryChange(Player p,String discovery){super(p);this.discovery=discovery;}public String getDiscovery(){return discovery;}
    }
    @Cancelable public static class DiscoveryPre extends DiscoveryChange {
        public DiscoveryPre(Player p,String discovery){super(p,discovery);}
    }
    public static class DiscoveryPost extends DiscoveryChange {
        public DiscoveryPost(Player p,String discovery){super(p,discovery);}
    }
    @Cancelable public static class GroupAttempt extends PlayerHook {
        private final ResourceLocation group;private final List<FoodGroupDefinition.GroupEffect> effects;private final Set<Integer> cancelled=new HashSet<>();
        public GroupAttempt(Player p,FoodGroupDefinition definition){super(p);group=definition.getId();effects=new ArrayList<>(definition.getEffects());}
        public ResourceLocation getGroup(){return group;}public List<FoodGroupDefinition.GroupEffect> getEffects(){return List.copyOf(effects);}
        public void cancelEffect(int index){effects.get(index);cancelled.add(index);}
        public boolean isEffectCancelled(int index){return cancelled.contains(index);}
        public void replaceEffect(int index,FoodGroupDefinition.GroupEffect effect){
            var old=effects.get(index);Objects.requireNonNull(effect);
            if(!old.getEffectId().equals(effect.getEffectId())||!old.getEffectType().equals(effect.getEffectType())||!old.getDurationType().equals(effect.getDurationType()))
                throw new IllegalArgumentException("Reward slot identity must stay unchanged; edit amounts/amplifier/duration instead");
            effects.set(index,effect);
        }
    }
    private static void finite(double n){if(!Double.isFinite(n))throw new IllegalArgumentException("Value must be finite");}
    private static final ThreadLocal<Set<String>> POSTING=ThreadLocal.withInitial(HashSet::new);
    public static boolean post(Event event){
        if(!(event instanceof GroupAttempt)&&!(event instanceof MealPre))return MinecraftForge.EVENT_BUS.post(event);
        String token=event.getClass().getName()+":"+((PlayerHook)event).getPlayer().getUUID();
        if(!POSTING.get().add(token)){event.setCanceled(true);return true;}
        try{return MinecraftForge.EVENT_BUS.post(event);}finally{POSTING.get().remove(token);if(POSTING.get().isEmpty())POSTING.remove();}
    }
    public record Change(boolean accepted,float before,float after) {}
    private static final ThreadLocal<Set<String>> MUTATING=ThreadLocal.withInitial(HashSet::new);
    public static Change monotony(Player player,IFoodTolerance cap,FoodKey food,ItemStack sample,double value,String reason){
        finite(value);float before=cap.getMonotony(food);String token=player.getUUID()+":food:"+food;
        if(!MUTATING.get().add(token))throw new IllegalStateException("Recursive change to the same food");
        try {
            var pre=new MonotonyPre(player,food,before,value,reason);if(post(pre))return new Change(false,before,before);
            float after=(float)Math.max(0,Math.min(FormulaManager.getMonotonyCap(),pre.getValue()));
            if(reason.equals("recovery"))after=Math.min(before,after);
            int oldStage=ToleranceStage.forStack(sample,before,false).getLevel(),newStage=ToleranceStage.forStack(sample,after,false).getLevel();
            if(oldStage!=newStage&&post(new StagePre(player,food,oldStage,newStage,reason)))return new Change(false,before,before);
            cap.setMonotony(food,after);
            if(oldStage!=newStage)post(new StagePost(player,food,oldStage,newStage,reason));
            return new Change(true,before,after);
        }finally{MUTATING.get().remove(token);if(MUTATING.get().isEmpty())MUTATING.remove();}
    }
    public static boolean nutrient(Player player,IFoodTolerance cap,String nutrient,double value,String reason){
        finite(value);float before=cap.getNutrientValue(nutrient);String token=player.getUUID()+":nutrient:"+nutrient;
        if(!MUTATING.get().add(token))throw new IllegalStateException("Recursive change to the same nutrient");
        try {
            var pre=new NutrientPre(player,nutrient,before,value,reason);if(post(pre))return false;
            var scale=NutritionScaleManager.getInstance().get(nutrient);double max=scale==null?Float.MAX_VALUE:scale.maxValue();
            cap.setNutrientValue(nutrient,(float)Math.max(0,Math.min(max,pre.getValue())));float after=cap.getNutrientValue(nutrient);
            if(before!=after)post(new NutrientPost(player,nutrient,before,after,reason));return before!=after;
        }finally{MUTATING.get().remove(token);if(MUTATING.get().isEmpty())MUTATING.remove();}
    }
    public static boolean discover(Player player,IFoodTolerance cap,String id){
        if(cap.hasDiscovered(id))return false;String token=player.getUUID()+":discovery:"+id;
        if(!MUTATING.get().add(token))return false;
        try{if(post(new DiscoveryPre(player,id)))return false;cap.discover(id);post(new DiscoveryPost(player,id));return true;}
        finally{MUTATING.get().remove(token);if(MUTATING.get().isEmpty())MUTATING.remove();}
    }
    private static final Map<Player,Map<String,Integer>> QUOTAS=new WeakHashMap<>();
    private static final Map<Player,Map<String,Integer>> RAW_QUOTAS=new WeakHashMap<>();
    private record QuotaTransition(String quota,int before,int after) {}
    private static final Map<Player,List<QuotaTransition>> PENDING_QUOTAS=new WeakHashMap<>();
    public static int quota(Player player,String id,int raw){
        String token=player.getUUID()+":quota:"+id;
        if(!MUTATING.get().add(token))return quotaState(player,id,raw);
        try {
        var pre=new QuotaPre(player,id,Math.min(1000,Math.max(0,raw)));int after=post(pre)?0:pre.getMultiplier();
        RAW_QUOTAS.computeIfAbsent(player,p->new HashMap<>()).put(id,raw);
        var states=QUOTAS.computeIfAbsent(player,p->new HashMap<>());int before=states.getOrDefault(id,0);states.put(id,after);
        if(before!=after)PENDING_QUOTAS.computeIfAbsent(player,p->new ArrayList<>()).add(new QuotaTransition(id,before,after));return after;
        }finally{MUTATING.get().remove(token);if(MUTATING.get().isEmpty())MUTATING.remove();}
    }
    public static int quotaState(Player player,String id,int fallback){return java.util.Objects.equals(RAW_QUOTAS.getOrDefault(player,Map.of()).get(id),fallback)?QUOTAS.getOrDefault(player,Map.of()).getOrDefault(id,fallback):fallback;}
    public static void clearQuotaPrefix(Player player,String prefix,Set<String> present){
        var states=QUOTAS.get(player);if(states==null)return;
        for(String id:List.copyOf(states.keySet()))if(id.startsWith(prefix)&&!present.contains(id)){
            int before=states.remove(id);RAW_QUOTAS.getOrDefault(player,new HashMap<>()).remove(id);if(before!=0)PENDING_QUOTAS.computeIfAbsent(player,p->new ArrayList<>()).add(new QuotaTransition(id,before,0));
        }
    }
    public static void commitQuotas(Player player,String prefix){
        var pending=PENDING_QUOTAS.get(player);if(pending==null)return;
        var committed=pending.stream().filter(e->e.quota().startsWith(prefix)).toList();pending.removeAll(committed);
        if(pending.isEmpty())PENDING_QUOTAS.remove(player);committed.forEach(e->post(new QuotaPost(player,e.quota(),e.before(),e.after())));
    }
    public static void forgetPlayer(Player player){QUOTAS.remove(player);RAW_QUOTAS.remove(player);PENDING_QUOTAS.remove(player);}
}
