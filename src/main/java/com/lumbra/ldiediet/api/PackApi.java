package com.lumbra.ldiediet.api;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.rewards.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
 
public final class PackApi {
    private PackApi() {}
    private static IFoodTolerance cap(ServerPlayer player) {
        if(!player.server.isSameThread())throw new IllegalStateException("PackApi must run on the server thread");
        var cap=CapabilityHandler.get(player);if(cap==null)throw new IllegalStateException("Player nutrition capability unavailable");return cap;
    }
    public static double getNutrient(ServerPlayer player,String nutrient) {
        var scale=scale(nutrient);return cap(player).getNutrientValue(scale.nutrientKey());
    }
    public static void setNutrient(ServerPlayer player,String nutrient,double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Value must be finite");
        var scale=scale(nutrient);var cap=cap(player);
        DietEvents.nutrient(player,cap,scale.nutrientKey(),value,"api");refresh(player);
    }
    private static NutritionScaleDefinition scale(String id) {
        var scale=NutritionScaleManager.getInstance().get(id);
        if(scale==null && id.startsWith("minecraft:"))scale=NutritionScaleManager.getInstance().get(id.substring(10));
        if(scale==null)throw new IllegalArgumentException("Unknown nutrient: "+id);return scale;
    }
    public static void refresh(ServerPlayer player) {
        var cap=cap(player);NutritionAttributeHandler.refresh(player,cap);NutritionRuleHandler.refresh(player,cap);
        com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(player,cap);CapabilityHandler.syncToClient(player);
    }
    public static void setSuite(ServerPlayer player,String suite) {
        if(!com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE.suiteNames().contains(suite))throw new IllegalArgumentException("Unknown Diet suite: "+suite);
        cap(player).setDietSuite(suite);refresh(player);
    }
    public static int groupProgress(ServerPlayer player,String id) {
        var group=group(id);var cap=cap(player);return RewardHandler.countEligibleHistory(cap.getEatingHistory(),group,cap.getRewardCursor(group.getId().toString()));
    }
    public static void grantGroup(ServerPlayer player,String id) { RewardHandler.activateGroupReward(player,cap(player),group(id)); }
    public static void addNutrient(ServerPlayer player,String nutrient,double amount) {
        if(!Double.isFinite(amount))throw new IllegalArgumentException("Amount must be finite");
        setNutrient(player,nutrient,getNutrient(player,nutrient)+amount);
    }
    private static FoodKey food(net.minecraft.world.item.ItemStack stack) {
        if(stack==null||stack.isEmpty())throw new IllegalArgumentException("Food stack is empty");
        return DietConfigManager.getFoodKey(stack,false);
    }
    public record FoodState(FoodKey key,float monotony,int stage,int recoveryMeals,long eatenCount,boolean countExact,double cci) {}
    public static FoodState foodState(ServerPlayer player,net.minecraft.world.item.ItemStack stack) {
        var cap=cap(player);var key=food(stack);
        return new FoodState(key,cap.getMonotony(key),com.lumbra.ldiediet.ToleranceStage.forStack(stack,cap.getMonotony(key),false).getLevel(),
                cap.getRecoveryMeals(key),cap.getEatenCounts().getOrDefault(key,0L),cap.isEatenCountExact(key),DietConfigManager.getOrCalculateCCI(stack,player.server));
    }
    public static float getMonotony(ServerPlayer player,net.minecraft.world.item.ItemStack stack){return cap(player).getMonotony(food(stack));}
    public static boolean setMonotony(ServerPlayer player,net.minecraft.world.item.ItemStack stack,double value){
        var cap=cap(player);var key=food(stack);var result=DietEvents.monotony(player,cap,key,stack,value,"api");refresh(player);return result.accepted();
    }
    public static boolean setMonotony(ServerPlayer player,FoodKey key,double value){
        if(key==null||!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(key.itemId())||key.item()==net.minecraft.world.item.Items.AIR)throw new IllegalArgumentException("Unknown food key");
        var cap=cap(player);var sample=cap.foodSample(key);if(sample.isEmpty())sample=DietConfigManager.representative(key);
        var result=DietEvents.monotony(player,cap,key,sample,value,"api");refresh(player);return result.accepted();
    }
    public static boolean addMonotony(ServerPlayer player,net.minecraft.world.item.ItemStack stack,double amount){return setMonotony(player,stack,getMonotony(player,stack)+amount);}
    public static int getStage(ServerPlayer player,net.minecraft.world.item.ItemStack stack){return foodState(player,stack).stage();}
    public static boolean setStage(ServerPlayer player,net.minecraft.world.item.ItemStack stack,int stage){
        cap(player);food(stack);if(stage<0||stage>4)throw new IllegalArgumentException("Stage must be 0..4");
        if(com.lumbra.ldiediet.util.ExclusionMatcher.isExcluded(stack,false)&&stage!=0)throw new IllegalArgumentException("Excluded food always has stage zero");
        var override=com.lumbra.ldiediet.data.ExclusionManager.getInstance().getStageOverrides().get(stack.getItem());
        if(override!=null&&override!=stage)throw new IllegalArgumentException("Item has a fixed stage override");
        if(override!=null)return true;
        double value=stage==0?0:FoodStageProfiles.SERVER.resolve(stack).thresholds().get(stage-1);
        if(value>com.lumbra.ldiediet.math.FormulaManager.getMonotonyCap())throw new IllegalArgumentException("Stage exceeds monotony_cap");
        if(com.lumbra.ldiediet.ToleranceStage.forStack(stack,(float)value,false).getLevel()!=stage)throw new IllegalArgumentException("Stage is unreachable with the configured thresholds");
        return setMonotony(player,stack,value);
    }
    public static int getRecoveryMeals(ServerPlayer player,net.minecraft.world.item.ItemStack stack){return cap(player).getRecoveryMeals(food(stack));}
    public static void setRecoveryMeals(ServerPlayer player,net.minecraft.world.item.ItemStack stack,int meals){
        if(meals<0)throw new IllegalArgumentException("Counter must be non-negative");var cap=cap(player);cap.setRecoveryMeals(food(stack),meals);refresh(player);
    }
    public static double getCCI(ServerPlayer player,net.minecraft.world.item.ItemStack stack){cap(player);food(stack);return DietConfigManager.getOrCalculateCCI(stack,player.server);}
    public static java.util.Set<String> activatedGroups(ServerPlayer player){return RewardHandler.activatedGroups(cap(player));}
    public static boolean isGroupKnown(ServerPlayer player,String id){return group(id).isKnown(cap(player));}
    public static java.util.Set<String> discoveredGroups(ServerPlayer player){
        var cap=cap(player);return FoodGroupManager.getInstance().getAllGroups().stream().filter(g->cap.hasDiscovered("group:"+g.getId())).map(g->g.getId().toString()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public static boolean discoverGroup(ServerPlayer player,String id){var g=group(id);boolean changed=DietEvents.discover(player,cap(player),"group:"+g.getId());refresh(player);return changed;}
    public static void cancelGroup(ServerPlayer player,String id){
        var g=group(id);var cap=cap(player);
        for(var effect:java.util.List.copyOf(cap.getActiveEffects()))if(effect.getGroupId().equals(g.getId().toString()))RewardHandler.removeEffect(player,effect);
        cap.removePermanentReward(g.getId().toString());cap.setRewardCursor(g.getId().toString(),cap.getMealSequence());refresh(player);
    }
    public static boolean cancelGroupEffect(ServerPlayer player,String id,int slot){
        var g=group(id);if(slot<0||slot>=g.getEffects().size())throw new IllegalArgumentException("Unknown reward slot");var cap=cap(player);boolean changed=false;
        for(var effect:java.util.List.copyOf(cap.getActiveEffects()))if(effect.getGroupId().equals(g.getId().toString())&&effect.getDefinitionIndex()==slot&&effect.isLive()){RewardHandler.removeEffect(player,effect);changed=true;}
        refresh(player);return changed;
    }
     
    public static boolean consume(ServerPlayer player,net.minecraft.world.item.ItemStack food,double hunger,double saturation,Runnable callback){
        cap(player);food(food);java.util.Objects.requireNonNull(callback);
        return com.lumbra.ldiediet.consumption.FoodConsumption.external(player,food,hunger,saturation,callback);
    }
    public static void reloadRecipeAdapters(ServerPlayer player){cap(player);DietConfigManager.reloadRecipes(player.server);}
    private static FoodGroupDefinition group(String id) {
        var group=FoodGroupManager.getInstance().getGroup(ResourceLocation.tryParse(com.lumbra.ldiediet.util.GroupIds.canonical(id)));
        if(group==null)throw new IllegalArgumentException("Unknown group: "+id);return group;
    }
}
