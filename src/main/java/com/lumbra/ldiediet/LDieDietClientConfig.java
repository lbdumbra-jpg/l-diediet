package com.lumbra.ldiediet;

import net.minecraftforge.common.ForgeConfigSpec;


public class LDieDietClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

     

    public static final ForgeConfigSpec.BooleanValue SHOW_DETAILED_TOOLTIP;

    public static final ForgeConfigSpec.BooleanValue SHOW_STAGE_ACTIONBAR;

    static {
        BUILDER.comment("Client interface settings");
        BUILDER.push("UI");

        SHOW_DETAILED_TOOLTIP = BUILDER
                .comment("Show food stage and nutritional details in item tooltips.",
                         "Recovery comes from dietary variety, not from waiting.")
                .translation("ldiediet.config.show_detailed_tooltip").define("show_detailed_tooltip", true);

        SHOW_STAGE_ACTIONBAR = BUILDER
                .comment("Display an action bar notification when the appetite stage changes.")
                .translation("ldiediet.config.show_stage_actionbar").define("show_stage_actionbar", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
