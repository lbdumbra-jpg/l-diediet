package com.lumbra.ldiediet.util;

import com.google.gson.*;
import net.minecraft.nbt.*;

 
public final class JsonNbt {
    private JsonNbt() {}

    public static CompoundTag encode(JsonElement json) {
        CompoundTag result = new CompoundTag();
        if (json == null || json.isJsonNull()) { result.putByte("type", (byte) 0); }
        else if (json.isJsonObject()) {
            result.putByte("type", (byte) 1);
            CompoundTag values = new CompoundTag();
            json.getAsJsonObject().entrySet().forEach(e -> values.put(e.getKey(), encode(e.getValue())));
            result.put("value", values);
        } else if (json.isJsonArray()) {
            result.putByte("type", (byte) 2);
            ListTag values = new ListTag();
            json.getAsJsonArray().forEach(e -> values.add(encode(e)));
            result.put("value", values);
        } else {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                result.putByte("type", (byte) 3); result.putBoolean("value", primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                result.putByte("type", (byte) 4); result.putDouble("value", primitive.getAsDouble());
            } else {
                String value = primitive.getAsString();
                if (value.length() <= 8000) {
                    result.putByte("type", (byte) 5); result.putString("value", value);
                } else {
                    result.putByte("type", (byte) 6);
                    ListTag chunks = new ListTag();
                    for (int i = 0; i < value.length(); i += 8000)
                        chunks.add(StringTag.valueOf(value.substring(i, Math.min(value.length(), i + 8000))));
                    result.put("chunks", chunks);
                }
            }
        }
        return result;
    }

    public static JsonElement decode(CompoundTag node) { return decode(node, 0); }

    private static JsonElement decode(CompoundTag node, int depth) {
        if (depth > 64) throw new IllegalArgumentException("JSON NBT nesting exceeds 64");
        return switch (node.getByte("type")) {
            case 0 -> JsonNull.INSTANCE;
            case 1 -> {
                JsonObject object = new JsonObject();
                CompoundTag values = node.getCompound("value");
                for (String key : values.getAllKeys()) object.add(key, decode(values.getCompound(key), depth + 1));
                yield object;
            }
            case 2 -> {
                JsonArray array = new JsonArray();
                ListTag values = node.getList("value", Tag.TAG_COMPOUND);
                for (int i = 0; i < values.size(); i++) array.add(decode(values.getCompound(i), depth + 1));
                yield array;
            }
            case 3 -> new JsonPrimitive(node.getBoolean("value"));
            case 4 -> new JsonPrimitive(node.getDouble("value"));
            case 5 -> new JsonPrimitive(node.getString("value"));
            case 6 -> {
                StringBuilder value = new StringBuilder();
                ListTag chunks = node.getList("chunks", Tag.TAG_STRING);
                for (int i = 0; i < chunks.size(); i++) value.append(chunks.getString(i));
                yield new JsonPrimitive(value.toString());
            }
            default -> throw new IllegalArgumentException("Unknown JSON NBT type");
        };
    }
}
