package com.lumbra.ldiediet.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record SyncCciPacket(CompoundTag values) {
    public SyncCciPacket { values = values.copy(); }
    public static void encode(SyncCciPacket msg, FriendlyByteBuf buf) { buf.writeNbt(msg.values); }
    public static SyncCciPacket decode(FriendlyByteBuf buf) {
        CompoundTag values = buf.readNbt();
        if (values == null || values.size() > 64) throw new IllegalArgumentException("Invalid CCI delta");
        return new SyncCciPacket(values);
    }
    public static void handle(SyncCciPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lumbra.ldiediet.data.DietConfigManager.applyClientCci(msg.values)));
        ctx.setPacketHandled(true);
    }
}
