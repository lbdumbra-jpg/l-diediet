package com.lumbra.ldiediet;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;


public class LDieDietServerConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

     

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> STAGE_THRESHOLDS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HUNGER_MULTIPLIERS;

    public static final ForgeConfigSpec.DoubleValue MIN_HUNGER_RESTORED;

    public static final ForgeConfigSpec.IntValue REWARD_HISTORY_LENGTH;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SATURATION_PENALTY;
    public static final ForgeConfigSpec.BooleanValue RESET_HISTORY_ON_DEATH;
    public static final ForgeConfigSpec.BooleanValue RESET_GROUPS_ON_DEATH;
    public static final ForgeConfigSpec.BooleanValue AUTOMATIC_CCI;

    public enum DecayMode { HUNGER, TIMED }
    public static final ForgeConfigSpec.EnumValue<DecayMode> DECAY_MODE;
    public static final ForgeConfigSpec.IntValue DECAY_PENALTY_PER_GROUP;
    public static final ForgeConfigSpec.DoubleValue MIN_MONOTONY_PER_MEAL;
    public static final ForgeConfigSpec.BooleanValue NUTRITION_DECAY;
    public static final ForgeConfigSpec.BooleanValue DECAY_IN_CREATIVE;
    public static final ForgeConfigSpec.DoubleValue DECAY_PERCENT_PER_MINUTE;
    public static final ForgeConfigSpec.DoubleValue NUTRITION_DEATH_LOSS_PERCENT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> CHEWING_MULTIPLIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> NUTRIENT_MULTIPLIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Boolean>> ALLOW_FOOD_BUFFS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_FOOD_EFFECTS, BLOCKED_FOOD_EFFECTS;

    static {
         
        BUILDER.comment("Food familiarity and dietary variety settings");
        BUILDER.push("General");

        STAGE_THRESHOLDS = BUILDER
                .comment("Base familiarity thresholds: [stage 1, stage 2, stage 3, stage 4].",
                        "Defaults: 5, 10, 15, 20.")
                .translation("ldiediet.config.stage_thresholds").define("stage_thresholds", com.lumbra.ldiediet.math.StageThresholds.DEFAULT,
                        com.lumbra.ldiediet.math.StageThresholds::isValid);

        HUNGER_MULTIPLIERS = BUILDER
                .comment("Hunger restoration multipliers for stages 0 through 4.",
                        "Defaults: 100%, 75%, 50%, 25%, 25%.")
                .translation("ldiediet.config.hunger_multipliers").define("hunger_multipliers", List.of(1.0, 0.75, 0.5, 0.25, 0.25),
                        value -> validFive(value, 0, 1));

        MIN_HUNGER_RESTORED = BUILDER
                .comment("Minimum hunger restored by nutritious food after penalties.")
                .translation("ldiediet.config.min_hunger_restored").defineInRange("min_hunger_restored", 0.5, 0.0, 10.0);

        REWARD_HISTORY_LENGTH = BUILDER
                .comment("Number of recent foods retained for group reward calculations.",
                         "Affects memory usage. Recommended range: 10 to 30.")
                .translation("ldiediet.config.reward_history_length").defineInRange("reward_history_length", 30, 1, 100);

        RESET_HISTORY_ON_DEATH = BUILDER
                .comment("Reset the recent eating history used for rewards on death.")
                .translation("ldiediet.config.reset_history_on_death").define("reset_history_on_death", true);

        ENABLE_SATURATION_PENALTY = BUILDER
                .comment("Apply familiarity penalties to saturation as well as hunger.")
                .translation("ldiediet.config.enable_saturation_penalty").define("enable_saturation_penalty", true);

        RESET_GROUPS_ON_DEATH = BUILDER
                .comment("Reset food group progress on death.")
                .translation("ldiediet.config.reset_groups_on_death").define("reset_groups_on_death", true);

        BUILDER.pop();

        BUILDER.push("CCI");
        AUTOMATIC_CCI = BUILDER
                .comment("Automatically calculate and save CCI only for items without a saved value.",
                        "For modpack authors; saved values survive restart and resource reload.",
                        "Disabled: use configured CCI or the formula minimum; no recipe calculation.",
                        "The explicit /ldiediet calc_all_cci command works regardless of this setting.")
                .translation("ldiediet.config.automatic_cci").define("automatic_cci", false);
        BUILDER.pop();

        BUILDER.push("Monotony");
        MIN_MONOTONY_PER_MEAL=BUILDER.comment("Minimum positive monotony gain for a nutritious portion, after CCI. 0 preserves the raw formula.")
                .translation("ldiediet.config.minimum_per_meal").defineInRange("minimum_per_meal",0.25,0,100);
        BUILDER.pop();
        BUILDER.push("Nutrition");
        DECAY_MODE = BUILDER.comment("HUNGER uses Diet 2.1.1 decay on lost food points. TIMED uses percent per minute.")
                .translation("ldiediet.config.decay_mode").defineEnum("decay_mode", DecayMode.HUNGER);
        DECAY_PENALTY_PER_GROUP = BUILDER.comment("HUNGER: reduction per additional non-empty scale. -1 imports Diet config, or uses its default 15.")
                .translation("ldiediet.config.decay_penalty_per_group").defineInRange("decay_penalty_per_group", -1, -1, 100);
        NUTRITION_DECAY = BUILDER.comment("Naturally reduce nutrition while a player is alive and online; no offline catch-up.")
                .translation("ldiediet.config.decay_enabled").define("decay_enabled", true);
        DECAY_PERCENT_PER_MINUTE = BUILDER.comment("Percentage points of each scale's maximum lost per 1200 game ticks. 1 = 20% per Minecraft day.")
                .translation("ldiediet.config.decay_percent_per_minute").defineInRange("decay_percent_per_minute", 1.0, 0.0, 100.0);
        DECAY_IN_CREATIVE = BUILDER.comment("Also decay in creative mode. Spectators never decay.").translation("ldiediet.config.decay_in_creative").define("decay_in_creative", false);
        NUTRITION_DEATH_LOSS_PERCENT = BUILDER
                .comment("Percentage of each current nutrition value lost on death. 0 keeps all, 50 halves, 100 clears.",
                        "Does not reset food habituation, lifetime counters or discoveries.")
                .translation("ldiediet.config.death_loss_percent").defineInRange("death_loss_percent", 50.0, 0.0, 100.0);
        BUILDER.pop();
        BUILDER.push("Stages");
        CHEWING_MULTIPLIERS = BUILDER.comment("Five values, stages 0..4. Must be finite and at least 1.")
                .translation("ldiediet.config.chewing_multipliers").define("chewing_multipliers", List.of(1.0,1.5,2.0,2.5,3.0), value -> validFive(value,1,100));
        NUTRIENT_MULTIPLIERS = BUILDER.comment("Five nutrition gain multipliers, stages 0..4.")
                .translation("ldiediet.config.nutrient_multipliers").define("nutrient_multipliers", List.of(1.0,1.0,1.0,0.5,0.0), value -> validFive(value,0,100));
        ALLOW_FOOD_BUFFS = BUILDER.comment("Whether beneficial food effects are allowed, stages 0..4.")
                .translation("ldiediet.config.allow_food_buffs").define("allow_food_buffs", List.of(true,true,false,false,false), value -> value instanceof List<?> values
                        && values.size()==5 && values.stream().allMatch(v -> v instanceof Boolean));
        BUILDER.pop();
        BUILDER.push("FoodEffects");
        ALLOWED_FOOD_EFFECTS=BUILDER.comment("Food-applied effect IDs allowed even when the stage blocks beneficial effects.").translation("ldiediet.config.allowed_food_effects").defineList("allowed_food_effects",List.<String>of(),v->v instanceof String s&&net.minecraft.resources.ResourceLocation.tryParse(s)!=null);
        BLOCKED_FOOD_EFFECTS=BUILDER.comment("Food-applied effect IDs always blocked. Blocklist takes priority over allowlist.").translation("ldiediet.config.blocked_food_effects").defineList("blocked_food_effects",List.<String>of(),v->v instanceof String s&&net.minecraft.resources.ResourceLocation.tryParse(s)!=null);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

     

    private static boolean validFive(Object value, double min, double max) {
        return value instanceof List<?> values && values.size()==5 && values.stream().allMatch(v ->
                v instanceof Double n && Double.isFinite(n) && n>=min && n<=max);
    }
    public static int getThreshold(int stage) {
        var thresholds = com.lumbra.ldiediet.math.StageThresholds.validated(STAGE_THRESHOLDS.get());
        return (stage >= 1 && stage <= thresholds.size()) ? Math.max(0, thresholds.get(stage - 1)) : Integer.MAX_VALUE;
    }

    public static double getMultiplier(int stage) {
        var multipliers = HUNGER_MULTIPLIERS.get();
        double value = (stage >= 0 && stage < multipliers.size()) ? multipliers.get(stage) : 0.25;
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.25;
    }


}
