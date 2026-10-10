package com.lumbra.ldiediet.network;

import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;


public class ModNetworkHandler {
     
    private static final String PROTOCOL_VERSION = "3";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LDieDiet.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;
    private static final java.util.Map<ServerPlayer, net.minecraft.nbt.CompoundTag> LAST_TOLERANCE = new java.util.WeakHashMap<>();

    public static void register() {
        INSTANCE.registerMessage(packetId++, StageMessagePacket.class, StageMessagePacket::encode,
                StageMessagePacket::decode, StageMessagePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(packetId++, RequestCciPacket.class, RequestCciPacket::encode,
                RequestCciPacket::decode, RequestCciPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(packetId++, SyncCciPacket.class, SyncCciPacket::encode,
                SyncCciPacket::decode, SyncCciPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(packetId++, SnapshotChunkPacket.class, SnapshotChunkPacket::encode,
                SnapshotChunkPacket::decode, SnapshotChunkPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(
                packetId++,
                SyncTolerancePacket.class,
                SyncTolerancePacket::encode,
                SyncTolerancePacket::decode,
                SyncTolerancePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(
                packetId++,
                SyncFoodGroupsPacket.class,
                SyncFoodGroupsPacket::encode,
                SyncFoodGroupsPacket::decode,
                SyncFoodGroupsPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        if (msg instanceof SyncFoodGroupsPacket packet) {
            SnapshotTransport.send(player, SnapshotTransport.CONFIG, packet.getData()); return;
        }
        if (msg instanceof SyncTolerancePacket packet) {
            var current = packet.getData();
            var previous = LAST_TOLERANCE.get(player);
            var delta = NbtDelta.between(previous, current);
            if (delta.isEmpty()) return;
            net.minecraft.nbt.CompoundTag envelope = new net.minecraft.nbt.CompoundTag();
            envelope.putBoolean("full", previous == null); envelope.put("delta", delta);
            SnapshotTransport.send(player, SnapshotTransport.TOLERANCE, envelope);
            LAST_TOLERANCE.put(player, current.copy()); return;
        }
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
    public static void resetPlayer(ServerPlayer player) {
        LAST_TOLERANCE.remove(player);
        RequestCciPacket.forget(player);
    }
}
