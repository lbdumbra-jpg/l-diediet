package com.levbu.ldiediet.capability;

import net.minecraft.world.item.Item;


public class HistoryEntry {
    private final Item item;
    private int count;

    public HistoryEntry(Item item, int count) {
        this.item = item;
        this.count = count;
    }

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public void increment() {
        count++;
    }

    public void decrement() {
        count--;
    }
}
