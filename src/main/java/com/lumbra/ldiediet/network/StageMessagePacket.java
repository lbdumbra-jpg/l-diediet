package com.lumbra.ldiediet.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record StageMessagePacket(Component message) {
    public static void encode(StageMessagePacket msg, FriendlyByteBuf buf) { buf.writeComponent(msg.message); }
    public static StageMessagePacket decode(FriendlyByteBuf buf) { return new StageMessagePacket(buf.readComponent()); }
    public static void handle(StageMessagePacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lumbra.ldiediet.client.ClientPacketHandler.handleStageMessage(msg.message)));
        ctx.setPacketHandled(true);
    }
}
