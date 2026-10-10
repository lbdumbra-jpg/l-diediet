package com.lumbra.ldiediet.data;

import com.lumbra.ldiediet.compat.ClimateHelper;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

 





public class EffectCondition {
    private com.google.gson.JsonObject extra = new com.google.gson.JsonObject();
    public EffectCondition extended(com.google.gson.JsonObject json) {
        extra=json.deepCopy();
        for(String field:extra.keySet())if(!java.util.Set.of("$schema","logic","temperature","cold_sweat_state","season","biomes","dimensions","advancements","weather","time","custom").contains(field))throw new IllegalArgumentException("Unknown condition: "+field);
        if(extra.has("temperature"))for(String field:extra.getAsJsonObject("temperature").keySet())if(!java.util.Set.of("$schema","min","max").contains(field))throw new IllegalArgumentException("Unknown temperature bound: "+field);
        if(extra.has("time"))for(String field:extra.getAsJsonObject("time").keySet())if(!java.util.Set.of("min","max").contains(field))throw new IllegalArgumentException("Unknown time bound: "+field);
        for(String field:List.of("biomes","dimensions","advancements"))if(extra.has(field)) {
            for(var e:extra.getAsJsonArray(field)){String id=e.getAsString();new net.minecraft.resources.ResourceLocation(id.startsWith("#")?id.substring(1):id);if(!field.equals("biomes")&&id.startsWith("#"))throw new IllegalArgumentException(field+" does not accept tags");}
        }
        if(extra.has("weather")&&!List.of("clear","rain","thunder").contains(extra.get("weather").getAsString()))throw new IllegalArgumentException("weather must be clear/rain/thunder");
        if(extra.has("time"))for(String field:List.of("min","max"))if(extra.getAsJsonObject("time").has(field)){
            double n=extra.getAsJsonObject("time").get(field).getAsDouble();if(!Double.isFinite(n)||n!=Math.rint(n)||n<0||n>23999)throw new IllegalArgumentException("time must be integer 0..23999");
        }
        if(extra.has("custom"))for(var e:extra.getAsJsonArray("custom")){var c=e.getAsJsonObject();new net.minecraft.resources.ResourceLocation(c.get("id").getAsString());if(c.has("args")&&!c.get("args").isJsonObject())throw new IllegalArgumentException("custom args must be an object");}
        return this;
    }
    public com.google.gson.JsonObject additionalJson(){return extra.deepCopy();}
    public static EffectCondition parse(com.google.gson.JsonObject json){
        var temperature=json.has("temperature")?json.getAsJsonObject("temperature"):new com.google.gson.JsonObject();
        return new EffectCondition(json.has("logic")?json.get("logic").getAsString():"AND",
                temperature.has("min")?temperature.get("min").getAsDouble():null,temperature.has("max")?temperature.get("max").getAsDouble():null,
                json.has("cold_sweat_state")?json.get("cold_sweat_state").getAsString():null,json.has("season")?json.get("season").getAsString():null).extended(json);
    }

    private final String logic;            
    private final Double temperatureMin;   
    private final Double temperatureMax;   
    private final String coldSweatState;   
    private final String season;           

    public EffectCondition(String logic,
                           @Nullable Double temperatureMin,
                           @Nullable Double temperatureMax,
                           @Nullable String coldSweatState,
                           @Nullable String season) {
        if (temperatureMin != null && !Double.isFinite(temperatureMin)
                || temperatureMax != null && !Double.isFinite(temperatureMax))
            throw new IllegalArgumentException("Non-finite effect temperature");
        this.logic = (logic != null && !logic.isEmpty()) ? logic.toUpperCase(java.util.Locale.ROOT) : "AND";
        this.temperatureMin = temperatureMin;
        this.temperatureMax = temperatureMax;
        this.coldSweatState = coldSweatState;
        this.season = season;
    }

    public String getLogic() { return logic; }
    @Nullable public Double getTemperatureMin() { return temperatureMin; }
    @Nullable public Double getTemperatureMax() { return temperatureMax; }
    @Nullable public String getColdSweatState() { return coldSweatState; }
    @Nullable public String getSeason() { return season; }

     



    public boolean evaluate(Player player) {
        List<Boolean> results = new ArrayList<>();
        if(extra.has("biomes")) {
            var biome=player.level().getBiome(player.blockPosition());boolean match=false;
            for(var e:extra.getAsJsonArray("biomes")){String id=e.getAsString();match|=id.startsWith("#")?biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,new net.minecraft.resources.ResourceLocation(id.substring(1)))):biome.unwrapKey().map(k->k.location().toString().equals(id)).orElse(false);}results.add(match);
        }
        if(extra.has("dimensions")){boolean match=false;for(var e:extra.getAsJsonArray("dimensions"))match|=player.level().dimension().location().toString().equals(e.getAsString());results.add(match);}
        if(extra.has("time")){var time=extra.getAsJsonObject("time");int min=time.has("min")?time.get("min").getAsInt():0,max=time.has("max")?time.get("max").getAsInt():23999;long now=Math.floorMod(player.level().getDayTime(),24000);results.add(min<=max?now>=min&&now<=max:now>=min||now<=max);}
        if(extra.has("weather")){String weather=extra.get("weather").getAsString();results.add(switch(weather){case "rain"->player.level().isRaining()&&!player.level().isThundering();case "thunder"->player.level().isThundering();default->!player.level().isRaining();});}
        if(extra.has("advancements")){
            boolean all=player instanceof net.minecraft.server.level.ServerPlayer;
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)for(var e:extra.getAsJsonArray("advancements")){
                var advancement=serverPlayer.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(e.getAsString()));all&=advancement!=null&&serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone();
            }results.add(all);
        }
        if(extra.has("custom"))for(var e:extra.getAsJsonArray("custom")){
            var c=e.getAsJsonObject();results.add(player instanceof net.minecraft.server.level.ServerPlayer p&&com.lumbra.ldiediet.api.IntegrationApi.testCondition(p,c.get("id").getAsString(),c.has("args")?c.getAsJsonObject("args"):new com.google.gson.JsonObject()));
        }

         
        if (temperatureMin != null || temperatureMax != null) {
            float biomeTemp = ClimateHelper.getBiomeTemperature(player);
            boolean tempMatch = true;
            if (temperatureMin != null && biomeTemp < temperatureMin) {
                tempMatch = false;
            }
            if (temperatureMax != null && biomeTemp > temperatureMax) {
                tempMatch = false;
            }
            results.add(tempMatch);
        }

         
        if (coldSweatState != null && !coldSweatState.isEmpty()) {
            if (!ClimateHelper.isColdSweatLoaded()) {
                 
                results.add(false);
            } else {
                String currentState = ClimateHelper.getColdSweatState(player);
                results.add(coldSweatState.equalsIgnoreCase(currentState));
            }
        }

         
        if (season != null && !season.isEmpty()) {
            if (!ClimateHelper.isSereneSeasonsLoaded()) {
                 
                results.add(false);
            } else {
                String currentSeason = ClimateHelper.getCurrentSeason(player.level());
                results.add(season.equalsIgnoreCase(currentSeason));
            }
        }

         
        if (results.isEmpty()) {
            return true;
        }

         
        if ("OR".equals(logic)) {
            return results.stream().anyMatch(Boolean::booleanValue);
        } else {
             
            return results.stream().allMatch(Boolean::booleanValue);
        }
    }

     


    public boolean hasAnyCondition() {
        return List.of("biomes","dimensions","time","weather","advancements","custom").stream().anyMatch(extra::has) || temperatureMin != null || temperatureMax != null
                || (coldSweatState != null && !coldSweatState.isEmpty())
                || (season != null && !season.isEmpty());
    }
    public List<String> diagnostics(net.minecraft.server.MinecraftServer server) {
        var out=new ArrayList<String>();
        if(!java.util.Set.of("AND","OR").contains(logic))out.add("unknown condition logic "+logic);
        if(extra.has("dimensions"))for(var e:extra.getAsJsonArray("dimensions")){var id=new net.minecraft.resources.ResourceLocation(e.getAsString());if(server.levelKeys().stream().noneMatch(k->k.location().equals(id)))out.add("unavailable dimension "+id);}
        if(extra.has("biomes")){var registry=server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);for(var e:extra.getAsJsonArray("biomes")){String id=e.getAsString();if(id.startsWith("#")){if(registry.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,new net.minecraft.resources.ResourceLocation(id.substring(1)))).isEmpty())out.add("unavailable biome tag "+id);}else if(!registry.containsKey(new net.minecraft.resources.ResourceLocation(id)))out.add("unavailable biome "+id);}}
        if(extra.has("advancements"))for(var e:extra.getAsJsonArray("advancements"))if(server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(e.getAsString()))==null)out.add("unavailable advancement "+e.getAsString());
        if(extra.has("custom"))for(var e:extra.getAsJsonArray("custom"))if(!com.lumbra.ldiediet.api.IntegrationApi.hasCondition(e.getAsJsonObject().get("id").getAsString()))out.add("unregistered custom condition "+e.getAsJsonObject().get("id").getAsString());
        return List.copyOf(out);
    }
    public List<net.minecraft.network.chat.Component> descriptions() {
        var out=new ArrayList<net.minecraft.network.chat.Component>();
        for(String field:List.of("biomes","dimensions","advancements"))if(extra.has(field))out.add(net.minecraft.network.chat.Component.translatable("ldiediet.condition."+field,String.join(", ",extra.getAsJsonArray(field).asList().stream().map(com.google.gson.JsonElement::getAsString).toList())));
        if(extra.has("weather"))out.add(net.minecraft.network.chat.Component.translatable("ldiediet.condition.weather",net.minecraft.network.chat.Component.translatable("ldiediet.condition.weather."+extra.get("weather").getAsString())));
        if(extra.has("time")){var time=extra.getAsJsonObject("time");out.add(net.minecraft.network.chat.Component.translatable("ldiediet.condition.time_range",time.has("min")?time.get("min").getAsInt():0,time.has("max")?time.get("max").getAsInt():23999));}
        if(temperatureMin!=null)out.add(net.minecraft.network.chat.Component.translatable("ldiediet.gui.temperature_min",JournalSnapshot.number(temperatureMin)));
        if(temperatureMax!=null)out.add(net.minecraft.network.chat.Component.translatable("ldiediet.gui.temperature_max",JournalSnapshot.number(temperatureMax)));
        if(coldSweatState!=null&&!coldSweatState.isBlank())out.add(net.minecraft.network.chat.Component.translatable("ldiediet.gui.body_state",net.minecraft.network.chat.Component.translatable("ldiediet.condition.body."+coldSweatState.toLowerCase(java.util.Locale.ROOT))));
        if(season!=null&&!season.isBlank())out.add(net.minecraft.network.chat.Component.translatable("ldiediet.gui.season",net.minecraft.network.chat.Component.translatable("ldiediet.condition.season."+season.toLowerCase(java.util.Locale.ROOT))));
        if(extra.has("custom"))for(var e:extra.getAsJsonArray("custom")){var c=e.getAsJsonObject();out.add(net.minecraft.network.chat.Component.translatable(c.has("description_key")?c.get("description_key").getAsString():"ldiediet.condition.custom"));}return out;
    }
}
