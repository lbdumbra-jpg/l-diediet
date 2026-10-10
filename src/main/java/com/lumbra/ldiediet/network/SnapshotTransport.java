package com.lumbra.ldiediet.network;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

 
public final class SnapshotTransport {
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    public static final byte CONFIG = 0, TOLERANCE = 1;
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private SnapshotTransport() {}
    public static byte[] encode(CompoundTag data) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            OutputStream bounded = new FilterOutputStream(bytes) {
                private int count;
                @Override public void write(int value) throws IOException {
                    if (++count > MAX_BYTES) throw new IOException("Snapshot exceeds 64 MiB");
                    out.write(value);
                }
                @Override public void write(byte[] value, int offset, int length) throws IOException {
                    if (length > MAX_BYTES - count) throw new IOException("Snapshot exceeds 64 MiB");
                    count += length; out.write(value, offset, length);
                }
            };
            NbtIo.write(data, new DataOutputStream(bounded));
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("Cannot encode snapshot", e); }
    }
    public static CompoundTag decode(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Oversized snapshot");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            CompoundTag result = NbtIo.read(input, new NbtAccounter(8L * MAX_BYTES));
            if (input.available() != 0) throw new IOException("Trailing snapshot data");
            return result;
        } catch (IOException e) { throw new IllegalArgumentException("Cannot decode snapshot", e); }
    }
    public static void send(ServerPlayer player, byte stream, CompoundTag data) {
        byte[] bytes = encode(data);
        int id = NEXT_ID.incrementAndGet(), count = (bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        for (int i = 0; i < count; i++) ModNetworkHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new SnapshotChunkPacket(stream, id, i, bytes.length,
                        Arrays.copyOfRange(bytes, i * CHUNK_BYTES, Math.min(bytes.length, (i + 1) * CHUNK_BYTES))));
    }
     
    public static final class Assembler {
        private int id, next, total;
        private ByteArrayOutputStream bytes;
        public void clear() { bytes = null; next = 0; }
        public CompoundTag accept(SnapshotChunkPacket packet) {
            packet.validate();
            if (packet.index() == 0) {
                id = packet.id(); total = packet.totalBytes(); next = 0;
                bytes = new ByteArrayOutputStream(Math.min(total, CHUNK_BYTES));
            }
            if (bytes == null || packet.id() != id || packet.index() != next || packet.totalBytes() != total) {
                clear(); throw new IllegalArgumentException("Out-of-order snapshot chunk");
            }
            bytes.writeBytes(packet.bytes()); next++;
            if (bytes.size() != total) return null;
            byte[] complete = bytes.toByteArray(); clear(); return decode(complete);
        }
    }
}
