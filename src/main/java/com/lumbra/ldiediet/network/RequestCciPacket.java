package com.lumbra.ldiediet.network;

import com.lumbra.ldiediet.data.DietConfigManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Supplier;

 
public record RequestCciPacket(ResourceLocation item) {
    private static final Map<ServerPlayer, Window> WINDOWS = new WeakHashMap<>();
    private static final class Window { int start, count; Window(int start) { this.start = start; } }
    public static void forget(ServerPlayer player) { WINDOWS.remove(player); }
    public static void encode(RequestCciPacket msg, FriendlyByteBuf buf) { buf.writeUtf(msg.item.toString(), 256); }
    public static RequestCciPacket decode(FriendlyByteBuf buf) {
        ResourceLocation id = ResourceLocation.tryParse(buf.readUtf(256));
        if (id == null) throw new IllegalArgumentException("Invalid CCI item ID");
        return new RequestCciPacket(id);
    }
    public static boolean accept(ServerPlayer player, ResourceLocation id) {
        int tick = player.server.getTickCount();
        Window window = WINDOWS.computeIfAbsent(player, ignored -> new Window(tick));
        if (tick - window.start >= 20 || tick < window.start) { window.start = tick; window.count = 0; }
        if (++window.count > 8 || !ForgeRegistries.ITEMS.containsKey(id)) return false;
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(id));
        boolean consumable = stack.is(Items.CAKE) || com.lumbra.ldiediet.consumption.ConsumptionSnapshot.isConsumable(stack, player)
                || DietConfigManager.hasConfiguredProfile(stack.getItem());
        if (!consumable) consumable = player.getInventory().items.stream().anyMatch(held -> held.getItem() == stack.getItem()
                && com.lumbra.ldiediet.consumption.ConsumptionSnapshot.isConsumable(held, player))
                || player.getOffhandItem().getItem() == stack.getItem()
                && com.lumbra.ldiediet.consumption.ConsumptionSnapshot.isConsumable(player.getOffhandItem(), player);
        if (!consumable) return false;
        DietConfigManager.getOrCalculateCCI(stack, player.server);
        net.minecraft.nbt.CompoundTag values = new net.minecraft.nbt.CompoundTag();
        values.putDouble(id.toString(), DietConfigManager.getCCI(stack.getItem()));
        ModNetworkHandler.sendToPlayer(new SyncCciPacket(values), player);
        return true;
    }
    public static void handle(RequestCciPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> { ServerPlayer player = ctx.getSender(); if (player != null) accept(player, msg.item); });
        ctx.setPacketHandled(true);
    }
}
