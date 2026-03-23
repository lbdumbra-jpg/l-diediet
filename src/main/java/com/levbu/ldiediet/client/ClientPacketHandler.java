package com.levbu.ldiediet.client;

import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.IFoodTolerance;
import com.levbu.ldiediet.network.SyncFoodGroupsPacket;
import com.levbu.ldiediet.network.SyncTolerancePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleSyncTolerance(SyncTolerancePacket msg) {
        Player player = Minecraft.getInstance().player;
        if (player != null && msg.getData() != null) {
            IFoodTolerance cap = CapabilityHandler.get(player);
            if (cap != null) {
                cap.deserializeNBT(msg.getData());
            }
        }
    }

    public static void handleSyncFoodGroups(SyncFoodGroupsPacket msg) {
        if (msg.getData() != null) {
            CompoundTag data = msg.getData();
            
            if (data.contains("foodGroups")) {
                com.levbu.ldiediet.data.FoodGroupManager.getInstance().deserializeFromNBT(data.getCompound("foodGroups"));
            }
            
            if (data.contains("exclusions")) {
                com.levbu.ldiediet.data.ExclusionManager.getInstance().deserializeFromNBT(data.getCompound("exclusions"));
            }
        }
    }
}
