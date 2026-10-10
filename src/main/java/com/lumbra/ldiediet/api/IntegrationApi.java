package com.lumbra.ldiediet.api;

import com.google.gson.JsonObject;
import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import java.util.*;

 
public final class IntegrationApi {
    private IntegrationApi() {}
    @FunctionalInterface public interface PlayerCondition { boolean test(ServerPlayer player, JsonObject arguments); }
    @FunctionalInterface public interface RecipeAdapter { Collection<CciRecipe> describe(Recipe<?> recipe, RegistryAccess registries); }
    @FunctionalInterface public interface RecipeContributor { Collection<CciRecipe> describe(MinecraftServer server); }
    public interface Registration extends AutoCloseable { @Override void close(); }
    public record CciRecipe(ItemStack output,List<List<ItemStack>> ingredients,double processingWeight) {
        public CciRecipe {
            if(output==null||output.isEmpty())throw new IllegalArgumentException("Recipe output is empty");
            if(!Double.isFinite(processingWeight)||processingWeight<0)throw new IllegalArgumentException("Processing weight must be finite and non-negative");
            output=output.copy();output.setCount(1);
            ingredients=ingredients.stream().map(slot->{if(slot.isEmpty()||slot.stream().anyMatch(ItemStack::isEmpty))throw new IllegalArgumentException("Recipe ingredient slot is empty");return slot.stream().map(ItemStack::copy).toList();}).toList();
            if(ingredients.isEmpty())throw new IllegalArgumentException("Recipe ingredients are empty");
        }
        @Override public ItemStack output(){return output.copy();}
        @Override public List<List<ItemStack>> ingredients(){return ingredients.stream().map(slot->slot.stream().map(ItemStack::copy).toList()).toList();}
    }
    private record Slot<T>(long generation,T callback) {}
    private static long generation;
    private static final Map<ResourceLocation,Slot<PlayerCondition>> CONDITIONS=new TreeMap<>();
    private static final Map<ResourceLocation,Slot<RecipeAdapter>> ADAPTERS=new TreeMap<>();
    private static final Map<ResourceLocation,Slot<RecipeContributor>> CONTRIBUTORS=new TreeMap<>();
    private static long recipeRevision;
    public static synchronized Registration registerCondition(String id,PlayerCondition condition){
        var key=new ResourceLocation(id);Objects.requireNonNull(condition);var slot=new Slot<>(++generation,condition);CONDITIONS.put(key,slot);
        return ()->{synchronized(IntegrationApi.class){CONDITIONS.remove(key,slot);}};
    }
    public static synchronized boolean hasCondition(String id){return CONDITIONS.containsKey(new ResourceLocation(id));}
    public static boolean testCondition(ServerPlayer player,String id,JsonObject arguments){
        PlayerCondition condition;synchronized(IntegrationApi.class){var slot=CONDITIONS.get(new ResourceLocation(id));condition=slot==null?null:slot.callback();}
        if(condition==null)return false;
        try{return condition.test(player,arguments.deepCopy());}
        catch(RuntimeException e){LDieDiet.LOGGER.error("Custom condition {} failed",id,e);return false;}
    }
    public static synchronized Registration registerRecipeAdapter(String recipeType,RecipeAdapter adapter){
        var key=new ResourceLocation(recipeType);Objects.requireNonNull(adapter);var slot=new Slot<>(++generation,adapter);ADAPTERS.put(key,slot);recipeRevision++;
        return ()->{synchronized(IntegrationApi.class){if(ADAPTERS.remove(key,slot))recipeRevision++;}};
    }
    public static synchronized Registration registerRecipeContributor(String id,RecipeContributor contributor){
        var key=new ResourceLocation(id);Objects.requireNonNull(contributor);var slot=new Slot<>(++generation,contributor);CONTRIBUTORS.put(key,slot);recipeRevision++;
        return ()->{synchronized(IntegrationApi.class){if(CONTRIBUTORS.remove(key,slot))recipeRevision++;}};
    }
    public static synchronized long recipeRevision(){return recipeRevision;}
     
    public static Collection<CciRecipe> adapt(Recipe<?> recipe,RegistryAccess registries){
        RecipeAdapter adapter;synchronized(IntegrationApi.class){var slot=ADAPTERS.get(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));adapter=slot==null?null:slot.callback();}
        return adapter==null?null:List.copyOf(Objects.requireNonNull(adapter.describe(recipe,registries),"Adapter returned null"));
    }
    public static Collection<CciRecipe> contributed(MinecraftServer server){
        Map<ResourceLocation,Slot<RecipeContributor>> contributors;synchronized(IntegrationApi.class){contributors=new TreeMap<>(CONTRIBUTORS);}
        var result=new ArrayList<CciRecipe>();
        contributors.forEach((id,provider)->{try{result.addAll(List.copyOf(provider.callback().describe(server)));}catch(RuntimeException e){LDieDiet.LOGGER.error("CCI recipe contributor {} failed",id,e);}});
        return List.copyOf(result);
    }
}
