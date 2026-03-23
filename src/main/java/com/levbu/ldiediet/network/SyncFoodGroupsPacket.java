package com.levbu.ldiediet.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class SyncFoodGroupsPacket {
    private final CompoundTag data;

    public SyncFoodGroupsPacket(CompoundTag data) {
        this.data = data;
    }

    public CompoundTag getData() {
        return data;
    }

    public static void encode(SyncFoodGroupsPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static SyncFoodGroupsPacket decode(FriendlyByteBuf buf) {
        return new SyncFoodGroupsPacket(buf.readNbt());
    }

    public static void handle(SyncFoodGroupsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.levbu.ldiediet.client.ClientPacketHandler.handleSyncFoodGroups(msg)));
        ctx.get().setPacketHandled(true);
    }
}
