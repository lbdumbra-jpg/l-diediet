package com.lumbra.ldiediet.data;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

 
public final class JournalMarkers {
    private JournalMarkers() {}
    public static List<Component> tooltip(ListTag markers, String scale, double boundary, double maximum) {
        var selected = new ArrayList<CompoundTag>();
        for (var tag : markers) {
            var marker = (CompoundTag) tag;
            if (marker.getString("scale").equals(scale) && Double.compare(marker.getDouble("value"), boundary) == 0) selected.add(marker);
        }
        if (selected.isEmpty()) return List.of();
        var entering = new HashSet<String>();
        for (var marker : selected) if (!marker.getString("direction").equals("exit")) entering.add(marker.getString("family"));
        var result = new ArrayList<Component>();
        result.add(Component.translatable("ldiediet.journal.boundary_percent", JournalSnapshot.number(boundary / maximum * 100)));
        var keys = new HashSet<String>();
        for (var marker : selected) {
            boolean exit = marker.getString("direction").equals("exit");
            if (exit && entering.contains(marker.getString("family"))) continue;
            if (!keys.add(marker.getString("key") + ":" + marker.getString("direction"))) continue;
            result.add(Component.translatable(exit ? "ldiediet.journal.reward_ends" : "ldiediet.journal.reward_begins"));
            if (!marker.getBoolean("known")) result.add(Component.translatable("ldiediet.journal.secret"));
            else {
                result.addAll(lines(marker, "lines"));
                result.add(Component.translatable(marker.getBoolean("active") ? "ldiediet.journal.active" : "ldiediet.journal.inactive"));
            }
        }
        return result;
    }
    public static List<Component> lines(CompoundTag tag, String key) {
        var result = new ArrayList<Component>();
        for (var value : tag.getList(key, Tag.TAG_STRING)) {
            try { var c = Component.Serializer.fromJson(value.getAsString()); if (c != null) result.add(c); }
            catch (RuntimeException ignored) {}
        }
        return result;
    }
}
