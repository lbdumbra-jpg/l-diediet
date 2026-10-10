package com.lumbra.ldiediet.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record SnapshotChunkPacket(byte stream, int id, int index, int totalBytes, byte[] bytes) {
    public SnapshotChunkPacket { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    public void validate() {
        if (stream < 0 || stream > 1 || totalBytes <= 0 || totalBytes > SnapshotTransport.MAX_BYTES
                || index < 0 || index > (totalBytes - 1) / SnapshotTransport.CHUNK_BYTES
                || bytes.length != Math.min(SnapshotTransport.CHUNK_BYTES, totalBytes - index * SnapshotTransport.CHUNK_BYTES))
            throw new IllegalArgumentException("Invalid snapshot chunk");
    }
    public static void encode(SnapshotChunkPacket msg, FriendlyByteBuf buf) {
        msg.validate(); buf.writeByte(msg.stream); buf.writeInt(msg.id);
        buf.writeVarInt(msg.index); buf.writeVarInt(msg.totalBytes); buf.writeByteArray(msg.bytes);
    }
    public static SnapshotChunkPacket decode(FriendlyByteBuf buf) {
        SnapshotChunkPacket result = new SnapshotChunkPacket(buf.readByte(), buf.readInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readByteArray(SnapshotTransport.CHUNK_BYTES));
        result.validate(); return result;
    }
    public static void handle(SnapshotChunkPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lumbra.ldiediet.client.ClientPacketHandler.handleChunk(msg)));
        ctx.setPacketHandled(true);
    }
}
