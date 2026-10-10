package com.lumbra.ldiediet.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NBTUtils {

    public static ListTag writeStringList(List<String> list, String keyName) {
        ListTag listTag = new ListTag();
        for (String s : list) {
            CompoundTag tag = new CompoundTag();
            tag.putString(keyName, s);
            listTag.add(tag);
        }
        return listTag;
    }

    public static List<String> readStringList(ListTag listTag, String keyName) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < listTag.size(); i++) {
            list.add(listTag.getCompound(i).getString(keyName));
        }
        return list;
    }

    public static CompoundTag writeStringDoubleMap(Map<String, Double> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            tag.putDouble(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    public static void readStringDoubleMap(CompoundTag tag, Map<String, Double> map) {
        map.clear();
        for (String key : tag.getAllKeys()) {
            double value = tag.getDouble(key);
            if (Double.isFinite(value)) map.put(key, value);
        }
    }

    public static CompoundTag writeStringIntMap(Map<String, Integer> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            tag.putInt(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    public static void readStringIntMap(CompoundTag tag, Map<String, Integer> map) {
        map.clear();
        for (String key : tag.getAllKeys()) {
            map.put(key, tag.getInt(key));
        }
    }
}
