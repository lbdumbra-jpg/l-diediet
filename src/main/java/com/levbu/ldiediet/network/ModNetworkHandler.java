package com.levbu.ldiediet.network;

import com.levbu.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;


public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LDieDiet.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;

    public static void register() {
        INSTANCE.registerMessage(
                packetId++,
                SyncTolerancePacket.class,
                SyncTolerancePacket::encode,
                SyncTolerancePacket::decode,
                SyncTolerancePacket::handle);
        
        INSTANCE.registerMessage(
                packetId++,
                SyncFoodGroupsPacket.class,
                SyncFoodGroupsPacket::encode,
                SyncFoodGroupsPacket::decode,
                SyncFoodGroupsPacket::handle);
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
