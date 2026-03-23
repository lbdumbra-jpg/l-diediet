package com.levbu.ldiediet.client;

import net.minecraftforge.common.MinecraftForge;

public class ClientEvents {
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new TooltipHandler());
        MinecraftForge.EVENT_BUS.register(new OverlayRenderer());
    }
}
