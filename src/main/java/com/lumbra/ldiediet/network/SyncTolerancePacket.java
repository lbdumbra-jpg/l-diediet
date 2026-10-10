package com.lumbra.ldiediet.network;

import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncTolerancePacket {
    private final CompoundTag data;

    public SyncTolerancePacket(CompoundTag data) {
        this.data = data;
    }

    public CompoundTag getData() {
        return data;
    }

    public static void encode(SyncTolerancePacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static SyncTolerancePacket decode(FriendlyByteBuf buf) {
        return new SyncTolerancePacket(buf.readNbt());
    }

    public static void handle(SyncTolerancePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lumbra.ldiediet.client.ClientPacketHandler.handleSyncTolerance(msg)));
        ctx.get().setPacketHandled(true);
    }
}
