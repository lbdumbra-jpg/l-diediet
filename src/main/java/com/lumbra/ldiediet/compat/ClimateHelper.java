package com.lumbra.ldiediet.compat;

import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

 



public final class ClimateHelper {

    private ClimateHelper() {
    }

     
    private static Boolean coldSweatLoaded = null;
    private static Boolean sereneSeasonsLoaded = null;

     
    private static Method coldSweatGetTempMethod = null;
    private static boolean coldSweatReflectionFailed = false;

    private static Method sereneSeasonsGetSeasonStateMethod = null;
    private static Method seasonStateGetSeasonMethod = null;
    private static boolean sereneSeasonsReflectionFailed = false;

     

     



    public static float getBiomeTemperature(Player player) {
        BlockPos pos = player.blockPosition();
        Biome biome = player.level().getBiome(pos).value();
        return biome.getBaseTemperature();
    }

     

    public static boolean isColdSweatLoaded() {
        if (coldSweatLoaded == null) {
            coldSweatLoaded = ModList.get().isLoaded("cold_sweat");
        }
        return coldSweatLoaded;
    }

     



    @Nullable
    public static String getColdSweatState(Player player) {
        if (!isColdSweatLoaded() || coldSweatReflectionFailed) {
            return null;
        }

        try {
             
             
            if (coldSweatGetTempMethod == null) {
                 
                Class<?> tempClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
                Class<?> traitEnum = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");

                coldSweatGetTempMethod = tempClass.getMethod("get",
                        net.minecraft.world.entity.LivingEntity.class, traitEnum);
            }

             
            Class<?> traitEnum = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
            Object bodyTrait = null;
            for (Object constant : traitEnum.getEnumConstants()) {
                if (constant instanceof Enum<?> enumValue && enumValue.name().equals("BODY")) {
                    bodyTrait = constant;
                    break;
                }
            }
            if (bodyTrait == null) {
                throw new IllegalStateException("Cold Sweat BODY temperature trait is unavailable");
            }

             
            Object result = coldSweatGetTempMethod.invoke(null, player, bodyTrait);
            double bodyTemp = ((Number) result).doubleValue();

             
             
            if (bodyTemp <= -50) return "freezing";
            if (bodyTemp < 0) return "cold";
            if (bodyTemp > 50) return "overheating";
            if (bodyTemp > 0) return "hot";
            return "normal";

        } catch (ClassNotFoundException e) {
            LDieDiet.LOGGER.warn("Cold Sweat API class not found. Disabling Cold Sweat integration.");
            coldSweatReflectionFailed = true;
            return null;
        } catch (Exception e) {
            LDieDiet.LOGGER.error("Error reading Cold Sweat temperature", e);
            coldSweatReflectionFailed = true;
            return null;
        }
    }

     

    public static boolean isSereneSeasonsLoaded() {
        if (sereneSeasonsLoaded == null) {
            sereneSeasonsLoaded = ModList.get().isLoaded("sereneseasons");
        }
        return sereneSeasonsLoaded;
    }

     



    @Nullable
    public static String getCurrentSeason(Level level) {
        if (!isSereneSeasonsLoaded() || sereneSeasonsReflectionFailed) {
            return null;
        }

        try {
            if (sereneSeasonsGetSeasonStateMethod == null) {
                 
                Class<?> helperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
                sereneSeasonsGetSeasonStateMethod = helperClass.getMethod("getSeasonState", Level.class);
            }

             
            Object seasonState = sereneSeasonsGetSeasonStateMethod.invoke(null, level);

            if (seasonStateGetSeasonMethod == null) {
                 
                seasonStateGetSeasonMethod = seasonState.getClass().getMethod("getSeason");
            }

             
            Object season = seasonStateGetSeasonMethod.invoke(seasonState);

             
            return season.toString().toLowerCase(java.util.Locale.ROOT);

        } catch (ClassNotFoundException e) {
            LDieDiet.LOGGER.warn("Serene Seasons API class not found. Disabling seasons integration.");
            sereneSeasonsReflectionFailed = true;
            return null;
        } catch (Exception e) {
            LDieDiet.LOGGER.error("Error reading Serene Seasons data", e);
            sereneSeasonsReflectionFailed = true;
            return null;
        }
    }
}
