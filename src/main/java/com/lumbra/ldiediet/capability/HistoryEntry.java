package com.lumbra.ldiediet.capability;

import net.minecraft.world.item.Item;
import com.lumbra.ldiediet.data.FoodKey;


public class HistoryEntry {
    private final FoodKey key;
    private int count;
    private long firstSequence;

    public HistoryEntry(Item item, int count) {
        this(item, count, 0);
    }
    public HistoryEntry(Item item, int count, long firstSequence) {
        this(FoodKey.of(item), count, firstSequence);
    }
    public HistoryEntry(FoodKey key, int count, long firstSequence) {
        this.key = key; this.count = Math.max(1, count); this.firstSequence = Math.max(0, firstSequence);
    }
    public FoodKey getKey() { return key; }
    public long getFirstSequence() { return firstSequence; }
    public long getLastSequence() { return firstSequence + count - 1; }
    public int countAfter(long cursor) { return (int) Math.max(0, Math.min(count, getLastSequence() - cursor)); }

    public Item getItem() {
        return key.item();
    }

    public int getCount() {
        return count;
    }

    public void increment() {
        count++;
    }

    public void decrement() {
        count--;
        firstSequence++;
    }
}
