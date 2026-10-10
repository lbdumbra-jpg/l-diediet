package com.lumbra.ldiediet.network;

import net.minecraft.nbt.*;
import java.util.Objects;

 
public final class NbtDelta {
    private NbtDelta() {}
    public static CompoundTag between(Tag before, Tag after) {
        CompoundTag node = new CompoundTag();
        if (Objects.equals(before, after)) return node;
        if (before instanceof CompoundTag a && after instanceof CompoundTag b) {
            node.putString("kind", "compound");
            CompoundTag changed = new CompoundTag();
            for (String key : b.getAllKeys()) {
                CompoundTag child = between(a.get(key), b.get(key));
                if (!child.isEmpty()) changed.put(key, child);
            }
            ListTag removed = new ListTag();
            for (String key : a.getAllKeys()) if (!b.contains(key)) removed.add(StringTag.valueOf(key));
            node.put("changed", changed); node.put("removed", removed);
        } else if (before instanceof ListTag a && after instanceof ListTag b && a.getElementType() == b.getElementType()) {
            node.putString("kind", "list"); node.putInt("size", b.size());
            CompoundTag changed = new CompoundTag();
            for (int i = 0; i < b.size(); i++) {
                CompoundTag child = between(i < a.size() ? a.get(i) : null, b.get(i));
                if (!child.isEmpty()) changed.put(Integer.toString(i), child);
            }
            node.put("changed", changed);
        } else {
            node.putString("kind", "replace"); node.put("value", after.copy());
        }
        return node;
    }
    public static Tag apply(Tag before, CompoundTag node) {
        if (node.isEmpty()) return before.copy();
        return switch (node.getString("kind")) {
            case "replace" -> Objects.requireNonNull(node.get("value"), "Missing delta value").copy();
            case "compound" -> {
                if (!(before instanceof CompoundTag compound)) throw new IllegalArgumentException("Missing compound baseline");
                CompoundTag result = compound.copy(), changed = node.getCompound("changed");
                ListTag removed = node.getList("removed", Tag.TAG_STRING);
                for (int i = 0; i < removed.size(); i++) result.remove(removed.getString(i));
                for (String key : changed.getAllKeys()) result.put(key, apply(compound.get(key), changed.getCompound(key)));
                yield result;
            }
            case "list" -> {
                if (!(before instanceof ListTag list)) throw new IllegalArgumentException("Missing list baseline");
                int size = node.getInt("size");
                if (size < 0 || size > SnapshotTransport.MAX_BYTES) throw new IllegalArgumentException("Invalid delta list size");
                ListTag result = new ListTag(); CompoundTag changed = node.getCompound("changed");
                for (int i = 0; i < size; i++) {
                    Tag old = i < list.size() ? list.get(i) : null;
                    String key = Integer.toString(i);
                    Tag value = changed.contains(key) ? apply(old, changed.getCompound(key)) : Objects.requireNonNull(old).copy();
                    result.add(value);
                }
                yield result;
            }
            default -> throw new IllegalArgumentException("Unknown delta node");
        };
    }
}
