package com.lumbra.ldiediet.rewards;
import com.lumbra.ldiediet.LDieDietServerConfig;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.compat.DietCompatibilityData;
import com.lumbra.ldiediet.data.NutritionScaleManager;
import com.lumbra.ldiediet.math.NutritionDecay;
import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.WeakHashMap;
public final class NutritionDecayHandler {
    private static final Map<Player,Integer> PREVIOUS_FOOD = new WeakHashMap<>();
    private NutritionDecayHandler() {}
    private static boolean eligible(Player player, IFoodTolerance cap) {
        return cap != null && !player.level().isClientSide && player.isAlive() && !player.isSpectator()
                && LDieDietServerConfig.NUTRITION_DECAY.get()
                && (!player.isCreative() || LDieDietServerConfig.DECAY_IN_CREATIVE.get());
    }
     
    public static boolean tick(Player player, IFoodTolerance cap) {
        if (player.level().isClientSide) return false;
        int food = player.getFoodData().getFoodLevel();
        Integer before = PREVIOUS_FOOD.put(player, food);
        if (!eligible(player, cap) || before == null || before <= food
                || LDieDietServerConfig.DECAY_MODE.get() != LDieDietServerConfig.DecayMode.HUNGER) return false;
        var manager = NutritionScaleManager.getInstance();
        var compat = DietCompatibilityData.INSTANCE;
        var scales = manager.getAll().stream().filter(s -> compat.decayApplies(s.nutrientKey(),cap.getDietSuite())).toList();
        int count = (int)scales.stream().filter(s -> cap.getNutrientValue(s.nutrientKey()) > 0).count();
        int penalty = LDieDietServerConfig.DECAY_PENALTY_PER_GROUP.get();
        if (penalty < 0) penalty = compat.decayPenaltyPerGroup();
        boolean changed = false;
        for (var scale : scales) {
            String key = scale.nutrientKey(); float value = cap.getNutrientValue(key);
            if (value <= 0) continue;
            float after = NutritionDecay.hunger(value,scale.maxValue(),before-food,count,penalty,manager.decayMultiplier(scale));
            if (after != value) { changed |= com.lumbra.ldiediet.api.DietEvents.nutrient(player,cap,key,after,"decay"); }
        }
        return changed;
    }
    public static void tickSecond(Player player, IFoodTolerance cap) {
        if (!eligible(player, cap) || LDieDietServerConfig.DECAY_MODE.get() != LDieDietServerConfig.DecayMode.TIMED) return;
        for (var scale : NutritionScaleManager.getInstance().getAll()) {
            String key = scale.nutrientKey();
            double rate = scale.decayPercentPerMinute() == null ? LDieDietServerConfig.DECAY_PERCENT_PER_MINUTE.get() : scale.decayPercentPerMinute();
            float before = cap.getNutrientValue(key);
            if (before > 0) com.lumbra.ldiediet.api.DietEvents.nutrient(player,cap,key,NutritionDecay.apply(before,scale.maxValue(),rate,20),"decay");
        }
    }
}
