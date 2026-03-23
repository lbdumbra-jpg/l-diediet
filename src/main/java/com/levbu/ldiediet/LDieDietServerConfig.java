package com.levbu.ldiediet;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;


public class LDieDietServerConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ==================== ОСНОВНЫЕ ПАРАМЕТРЫ ====================

    // Пороги стадий (базовые, для еды с 1 diet статом)
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> STAGE_THRESHOLDS;

    // Множители голода/насыщения по стадиям [стадия 0, 1, 2, 3, 4]
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HUNGER_MULTIPLIERS;

    // Время полного восстановления (тики) - делится на 4 стадии
    public static final ForgeConfigSpec.IntValue RECOVERY_TIME_TICKS;

    // Минимальное количество голода, которое еда восстанавливает независимо от штрафа
    public static final ForgeConfigSpec.DoubleValue MIN_HUNGER_RESTORED;

    // Длина истории съеденной еды для системы наград
    public static final ForgeConfigSpec.IntValue REWARD_HISTORY_LENGTH;

    // ==================== ВКЛЮЧЕНИЕ/ВЫКЛЮЧЕНИЕ ФУНКЦИЙ ====================

    // Применять штраф к насыщению
    public static final ForgeConfigSpec.BooleanValue ENABLE_SATURATION_PENALTY;

    // Включить интеграцию с Diet модом
    public static final ForgeConfigSpec.BooleanValue ENABLE_DIET_INTEGRATION;

    // Применять штраф к Diet статам
    public static final ForgeConfigSpec.BooleanValue ENABLE_DIET_PENALTY;

    // Включить бонусные пороги для еды с несколькими diet статами
    public static final ForgeConfigSpec.BooleanValue ENABLE_DIET_THRESHOLD_BONUS;

    // Включить штраф времени восстановления для еды с несколькими diet статами
    public static final ForgeConfigSpec.BooleanValue ENABLE_DIET_RECOVERY_PENALTY;

    // ==================== МНОЖИТЕЛИ DIET ПО СТАДИЯМ ====================

    // Стадия 3: множитель положительных diet статов
    public static final ForgeConfigSpec.DoubleValue STAGE3_POSITIVE_DIET_MULT;
    // Стадия 3: множитель отрицательных diet статов
    public static final ForgeConfigSpec.DoubleValue STAGE3_NEGATIVE_DIET_MULT;

    // Стадия 4: множитель положительных diet статов
    public static final ForgeConfigSpec.DoubleValue STAGE4_POSITIVE_DIET_MULT;
    // Стадия 4: множитель отрицательных diet статов
    public static final ForgeConfigSpec.DoubleValue STAGE4_NEGATIVE_DIET_MULT;

    // ==================== БОНУСНЫЕ ПОРОГИ ДЛЯ DIET СТАТОВ ====================

    // Пороги для еды с 2 diet статами
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> THRESHOLDS_2_STATS;
    // Пороги для еды с 3 diet статами
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> THRESHOLDS_3_STATS;
    // Пороги для еды с 4 diet статами
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> THRESHOLDS_4_STATS;
    // Пороги для еды с 5 diet статами
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> THRESHOLDS_5_STATS;

    // ==================== ШТРАФЫ ВРЕМЕНИ ДЛЯ DIET СТАТОВ ====================

    // Множитель времени восстановления для еды с 2 diet статами
    public static final ForgeConfigSpec.DoubleValue RECOVERY_MULT_2_STATS;
    // Множитель времени восстановления для еды с 3 diet статами
    public static final ForgeConfigSpec.DoubleValue RECOVERY_MULT_3_STATS;
    // Множитель времени восстановления для еды с 4 diet статами
    public static final ForgeConfigSpec.DoubleValue RECOVERY_MULT_4_STATS;
    // Множитель времени восстановления для еды с 5 diet статами
    public static final ForgeConfigSpec.DoubleValue RECOVERY_MULT_5_STATS;

    // ==================== БОНУС РАЗНООБРАЗИЯ ====================

    // Множитель скорости восстановления за каждый активный продукт на стадии 1+
    public static final ForgeConfigSpec.DoubleValue VARIETY_RECOVERY_BONUS_PER_FOOD;

    static {
        // ==================== GENERAL ====================
        BUILDER.comment("Основные настройки механики надоедания");
        BUILDER.push("General");

        STAGE_THRESHOLDS = BUILDER
                .comment("Базовые пороги стадий: [стадия1, стадия2, стадия3, стадия4]")
                .defineList("stage_thresholds", List.of(5, 10, 15, 25), obj -> obj instanceof Integer);

        HUNGER_MULTIPLIERS = BUILDER
                .comment("Множители восстановления голода по стадиям: [стадия0, стадия1, стадия2, стадия3, стадия4]")
                .defineList("hunger_multipliers", List.of(1.0, 0.75, 0.5, 0.25, 0.25),
                        obj -> obj instanceof Double);

        RECOVERY_TIME_TICKS = BUILDER
                .comment("Время полного восстановления в тиках",
                        "24000 = 1 игровой день, 1200 = 1 игровая минута")
                .defineInRange("recovery_time_ticks", 30000, 1000, 10000000);

        MIN_HUNGER_RESTORED = BUILDER
                .comment("Минимальное количество голода, которое еда всегда восстанавливает")
                .defineInRange("min_hunger_restored", 0.5, 0.0, 10.0);

        REWARD_HISTORY_LENGTH = BUILDER
                .comment("Количество последних съеденных продуктов, запоминаемых для наград",
                         "Влияет на потребление памяти, рекомендуемое значение 10-30")
                .defineInRange("reward_history_length", 30, 1, 100);

        BUILDER.pop();

        // ==================== FEATURES ====================
        BUILDER.comment("Включение/выключение функций");
        BUILDER.push("Features");

        ENABLE_SATURATION_PENALTY = BUILDER
                .comment("Применять штраф надоедания к насыщению")
                .define("enable_saturation_penalty", true);

        ENABLE_DIET_INTEGRATION = BUILDER
                .comment("Включить интеграцию с модом Diet")
                .define("enable_diet_integration", true);

        ENABLE_DIET_PENALTY = BUILDER
                .comment("Применять штраф надоедания к Diet статам на стадии 3+")
                .define("enable_diet_penalty", true);

        ENABLE_DIET_THRESHOLD_BONUS = BUILDER
                .comment("Включить бонусные пороги для еды с несколькими diet статами",
                        "Еда с большим количеством статов получает повышенные пороги стадий")
                .define("enable_diet_threshold_bonus", true);

        ENABLE_DIET_RECOVERY_PENALTY = BUILDER
                .comment("Включить штраф времени восстановления для еды с несколькими diet статами",
                        "Еда с большим количеством статов медленнее восстанавливается")
                .define("enable_diet_recovery_penalty", true);

        BUILDER.pop();

        // ==================== DIET MULTIPLIERS ====================
        BUILDER.comment("Множители Diet статов по стадиям надоедания");
        BUILDER.push("Diet_Multipliers");

        STAGE3_POSITIVE_DIET_MULT = BUILDER
                .comment("Стадия 3: множитель положительных diet статов",
                        "0.5 = еда даёт только 50% от нормальных diet статов")
                .defineInRange("stage3_positive_diet_mult", 0.5, 0.0, 2.0);

        STAGE3_NEGATIVE_DIET_MULT = BUILDER
                .comment("Стадия 3: множитель отрицательных diet статов",
                        "1.5 = еда отнимает на 50% больше diet статов")
                .defineInRange("stage3_negative_diet_mult", 1.5, 0.0, 5.0);

        STAGE4_POSITIVE_DIET_MULT = BUILDER
                .comment("Стадия 4: множитель положительных diet статов",
                        "0.0 = еда не даёт никаких diet статов")
                .defineInRange("stage4_positive_diet_mult", 0.0, 0.0, 2.0);

        STAGE4_NEGATIVE_DIET_MULT = BUILDER
                .comment("Стадия 4: множитель отрицательных diet статов",
                        "1.0 = нормальное количество")
                .defineInRange("stage4_negative_diet_mult", 1.0, 0.0, 5.0);

        BUILDER.pop();

        // ==================== DIET THRESHOLD BONUSES ====================
        BUILDER.comment("Бонусные пороги стадий для еды с несколькими diet статами");
        BUILDER.push("Diet_Thresholds");

        THRESHOLDS_2_STATS = BUILDER
                .comment("Пороги для еды с 2 diet статами: [стадия1, стадия2, стадия3, стадия4]")
                .defineList("thresholds_2_stats", List.of(6, 11, 16, 26),
                        obj -> obj instanceof Integer);

        THRESHOLDS_3_STATS = BUILDER
                .comment("Пороги для еды с 3 diet статами")
                .defineList("thresholds_3_stats", List.of(7, 12, 17, 27),
                        obj -> obj instanceof Integer);

        THRESHOLDS_4_STATS = BUILDER
                .comment("Пороги для еды с 4 diet статами")
                .defineList("thresholds_4_stats", List.of(7, 13, 18, 27),
                        obj -> obj instanceof Integer);

        THRESHOLDS_5_STATS = BUILDER
                .comment("Пороги для еды с 5 diet статами")
                .defineList("thresholds_5_stats", List.of(7, 14, 19, 27),
                        obj -> obj instanceof Integer);

        BUILDER.pop();

        // ==================== DIET RECOVERY PENALTIES ====================
        BUILDER.comment("Штрафы времени восстановления для еды с несколькими diet статами");
        BUILDER.push("Diet_Recovery");

        RECOVERY_MULT_2_STATS = BUILDER
                .comment("Множитель времени восстановления для еды с 2 diet статами",
                        "1.2 = +20% время восстановления")
                .defineInRange("recovery_mult_2_stats", 1.2, 1.0, 5.0);

        RECOVERY_MULT_3_STATS = BUILDER
                .comment("Множитель времени восстановления для еды с 3 diet статами",
                        "1.3 = +30% время восстановления")
                .defineInRange("recovery_mult_3_stats", 1.3, 1.0, 5.0);

        RECOVERY_MULT_4_STATS = BUILDER
                .comment("Множитель времени восстановления для еды с 4 diet статами",
                        "1.4 = +40% время восстановления")
                .defineInRange("recovery_mult_4_stats", 1.4, 1.0, 5.0);

        RECOVERY_MULT_5_STATS = BUILDER
                .comment("Множитель времени восстановления для еды с 5 diet статами",
                        "1.5 = +50% время восстановления")
                .defineInRange("recovery_mult_5_stats", 1.5, 1.0, 5.0);

        BUILDER.pop();

        // ==================== VARIETY BONUS ====================
        BUILDER.comment("Бонус разнообразия - ускорение восстановления за разную еду");
        BUILDER.push("Variety");

        VARIETY_RECOVERY_BONUS_PER_FOOD = BUILDER
                .comment("Бонус к скорости восстановления за каждый продукт на стадии 1+",
                        "0.5 = +50% скорость за каждый активный продукт",
                        "Формула: recoverySpeed = 1.0 + (этот_параметр * количество_продуктов)")
                .defineInRange("variety_recovery_bonus_per_food", 0.5, 0.0, 2.0);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    public static int getThreshold(int stage) {
        var thresholds = STAGE_THRESHOLDS.get();
        return (stage >= 1 && stage <= thresholds.size()) ? thresholds.get(stage - 1) : Integer.MAX_VALUE;
    }

    public static double getMultiplier(int stage) {
        var multipliers = HUNGER_MULTIPLIERS.get();
        return (stage >= 0 && stage < multipliers.size()) ? multipliers.get(stage) : 0.25;
    }

    public static int[] getThresholdsForDietStats(int dietStatCount) {
        if (!ENABLE_DIET_THRESHOLD_BONUS.get()) {
            var t = STAGE_THRESHOLDS.get();
            return new int[] { t.get(0), t.get(1), t.get(2), t.get(3) };
        }

        List<? extends Integer> t = switch (dietStatCount) {
            case 2 -> THRESHOLDS_2_STATS.get();
            case 3 -> THRESHOLDS_3_STATS.get();
            case 4 -> THRESHOLDS_4_STATS.get();
            case 5 -> THRESHOLDS_5_STATS.get();
            default -> STAGE_THRESHOLDS.get();
        };
        return new int[] { t.get(0), t.get(1), t.get(2), t.get(3) };
    }

    public static double getRecoveryMultiplierForDietStats(int dietStatCount) {
        if (!ENABLE_DIET_RECOVERY_PENALTY.get()) {
            return 1.0;
        }

        return switch (dietStatCount) {
            case 2 -> RECOVERY_MULT_2_STATS.get();
            case 3 -> RECOVERY_MULT_3_STATS.get();
            case 4 -> RECOVERY_MULT_4_STATS.get();
            case 5 -> RECOVERY_MULT_5_STATS.get();
            default -> 1.0;
        };
    }
}
