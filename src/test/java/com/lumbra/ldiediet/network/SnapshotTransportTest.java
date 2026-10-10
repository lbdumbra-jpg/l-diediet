package com.lumbra.ldiediet.network;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotTransportTest {
    @Test void longUnicodeJsonStringSurvivesActualBinaryNbt() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("long", "Ж🥕".repeat(400_000));
        var binary = SnapshotTransport.encode(com.lumbra.ldiediet.util.JsonNbt.encode(json));
        assertTrue(binary.length > 2 * 1024 * 1024);
        assertEquals(json, com.lumbra.ldiediet.util.JsonNbt.decode(SnapshotTransport.decode(binary)));
    }
    @Test void largeSnapshotIsAppliedOnlyAfterItsFinalChunk() {
        CompoundTag original = new CompoundTag();
        byte[] fixture = new byte[3 * 1024 * 1024]; new Random(71).nextBytes(fixture);
        original.putByteArray("large", fixture);
        byte[] bytes = SnapshotTransport.encode(original);
        var receiver = new SnapshotTransport.Assembler();
        int count = (bytes.length + SnapshotTransport.CHUNK_BYTES - 1) / SnapshotTransport.CHUNK_BYTES;
        for (int i = 0; i < count; i++) {
            var result = receiver.accept(new SnapshotChunkPacket(SnapshotTransport.CONFIG, 1, i, bytes.length,
                    Arrays.copyOfRange(bytes, i * SnapshotTransport.CHUNK_BYTES, Math.min(bytes.length, (i + 1) * SnapshotTransport.CHUNK_BYTES))));
            if (i < count - 1) assertNull(result); else assertEquals(original, result);
        }
    }
    @Test void truncatedOversizedAndOutOfOrderChunksAreRejected() {
        var receiver = new SnapshotTransport.Assembler();
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(new SnapshotChunkPacket((byte) 0, 1, 0, 100, new byte[99])));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(new SnapshotChunkPacket((byte) 0, 1, 0, SnapshotTransport.MAX_BYTES + 1, new byte[SnapshotTransport.CHUNK_BYTES])));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(new SnapshotChunkPacket((byte) 0, 1, 1, SnapshotTransport.CHUNK_BYTES + 1, new byte[1])));
    }
    @Test void oneChangedFoodEntryDoesNotSendTheOtherThousand() {
        CompoundTag before = new CompoundTag(); ListTag foods = new ListTag();
        for (int i = 0; i < 1000; i++) {
            CompoundTag entry = new CompoundTag(); entry.putString("item", "fixture:food_" + i); entry.putFloat("m", i);
            foods.add(entry);
        }
        before.put("foods", foods); CompoundTag after = before.copy();
        after.getList("foods", Tag.TAG_COMPOUND).getCompound(501).putFloat("m", 17);
        CompoundTag delta = NbtDelta.between(before, after);
        assertEquals(after, NbtDelta.apply(before, delta));
        assertTrue(SnapshotTransport.encode(delta).length < 250);
        assertEquals(501, before.getList("foods", Tag.TAG_COMPOUND).getCompound(501).getFloat("m"));
    }
    @Test void changesAppendTruncateRemoveAndReplaceTypes() {
        CompoundTag before = new CompoundTag();
        ListTag history = new ListTag(); history.add(StringTag.valueOf("a")); history.add(StringTag.valueOf("b"));
        before.put("history", history); before.putString("obsolete", "remove me"); before.putString("type", "string");
        CompoundTag appended = before.copy(); appended.getList("history", Tag.TAG_STRING).add(StringTag.valueOf("c"));
        appended.remove("obsolete"); appended.putInt("type", 4);
        assertEquals(appended, NbtDelta.apply(before, NbtDelta.between(before, appended)));
        CompoundTag truncated = appended.copy(); truncated.getList("history", Tag.TAG_STRING).remove(0);
        assertEquals(truncated, NbtDelta.apply(appended, NbtDelta.between(appended, truncated)));
        assertTrue(NbtDelta.between(truncated, truncated.copy()).isEmpty());
        assertEquals(truncated, NbtDelta.apply(null, NbtDelta.between(null, truncated)));
    }
    @Test void startingANewSnapshotDiscardsAnIncompleteTransfer() {
        var receiver = new SnapshotTransport.Assembler();
        receiver.accept(new SnapshotChunkPacket((byte) 0, 1, 0, SnapshotTransport.CHUNK_BYTES + 1, new byte[SnapshotTransport.CHUNK_BYTES]));
        CompoundTag replacement = new CompoundTag(); replacement.putString("source", "new login");
        byte[] bytes = SnapshotTransport.encode(replacement);
        assertEquals(replacement, receiver.accept(new SnapshotChunkPacket((byte) 0, 2, 0, bytes.length, bytes)));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(new SnapshotChunkPacket((byte) 0, 1, 1, SnapshotTransport.CHUNK_BYTES + 1, new byte[1])));
    }
}
