package com.levbu.ldiediet;

import net.minecraftforge.common.ForgeConfigSpec;


public class LDieDietClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ==================== UI НАСТРОЙКИ ====================

    public static final ForgeConfigSpec.BooleanValue SHOW_DETAILED_TOOLTIP;

    public static final ForgeConfigSpec.BooleanValue SHOW_STAGE_ACTIONBAR;

    static {
        BUILDER.comment("Настройки интерфейса клиента");
        BUILDER.push("UI");

        SHOW_DETAILED_TOOLTIP = BUILDER
                .comment("Показывать детальный прогресс в тултипе еды",
                         "Включает отображение: съедено X/Y, время до восстановления, имя группы и эффекты")
                .define("show_detailed_tooltip", true);

        SHOW_STAGE_ACTIONBAR = BUILDER
                .comment("Показывать уведомление в action bar при смене стадии")
                .define("show_stage_actionbar", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
