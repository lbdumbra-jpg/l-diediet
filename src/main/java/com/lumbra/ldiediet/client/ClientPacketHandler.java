package com.lumbra.ldiediet.client;

import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.capability.IFoodTolerance;
import com.lumbra.ldiediet.network.SyncFoodGroupsPacket;
import com.lumbra.ldiediet.network.SyncTolerancePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {
    public static void handleStageMessage(net.minecraft.network.chat.Component message) {
        Player player = Minecraft.getInstance().player;
        if (player != null && com.lumbra.ldiediet.LDieDietClientConfig.SHOW_STAGE_ACTIONBAR.get())
            player.displayClientMessage(message, true);
    }
    private static final com.lumbra.ldiediet.network.SnapshotTransport.Assembler[] TRANSFERS = {
            new com.lumbra.ldiediet.network.SnapshotTransport.Assembler(),
            new com.lumbra.ldiediet.network.SnapshotTransport.Assembler() };
    private static CompoundTag toleranceSnapshot;
    private static Player snapshotPlayer;
    public static net.minecraft.nbt.ListTag nutritionAttributes() {
        return toleranceSnapshot == null ? new net.minecraft.nbt.ListTag()
                : toleranceSnapshot.getList(com.lumbra.ldiediet.data.NutritionAttributeSummary.KEY,
                        net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
    }
    public static CompoundTag journal() { return toleranceSnapshot==null?new CompoundTag():toleranceSnapshot.getCompound(com.lumbra.ldiediet.data.JournalSnapshot.KEY); }
    private static int configurationBytes;
    public static int configurationBytes() { return configurationBytes; }
    private static final java.util.Map<net.minecraft.world.item.Item, Long> CCI_REQUESTS = new java.util.HashMap<>();
    private static long cciWindow;
    private static int cciRequestsInWindow;
    public static void requestCci(net.minecraft.world.item.ItemStack stack) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null || com.lumbra.ldiediet.data.DietConfigManager.hasClientCci(stack.getItem())) return;
        long tick = mc.level.getGameTime();
        if (tick < cciWindow || tick - cciWindow >= 20) { cciWindow = tick; cciRequestsInWindow = 0; }
        long previous = CCI_REQUESTS.getOrDefault(stack.getItem(), Long.MIN_VALUE);
        if (cciRequestsInWindow >= 8 || previous != Long.MIN_VALUE && tick >= previous && tick - previous < 100) return;
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || id.toString().length() > 256) return;
        cciRequestsInWindow++; CCI_REQUESTS.put(stack.getItem(), tick);
        com.lumbra.ldiediet.network.ModNetworkHandler.INSTANCE.sendToServer(new com.lumbra.ldiediet.network.RequestCciPacket(id));
    }

    public static void reset() {
        for (var transfer : TRANSFERS) transfer.clear();
        toleranceSnapshot = null;
        snapshotPlayer = null;
        configurationBytes = 0;
        com.lumbra.ldiediet.data.FoodStageProfiles.CLIENT.deserialize(new CompoundTag());
        com.lumbra.ldiediet.math.FormulaManager.clientNutrientSource("base_n * stage_multiplier");
        CCI_REQUESTS.clear(); cciWindow = 0; cciRequestsInWindow = 0;
        com.lumbra.ldiediet.data.DietConfigManager.clearClientSnapshot();
        com.lumbra.ldiediet.data.FoodGroupManager.getClientInstance().deserializeFromNBT(new CompoundTag());
        com.lumbra.ldiediet.data.ExclusionManager.getClientInstance().deserializeFromNBT(new CompoundTag());
        com.lumbra.ldiediet.data.NutritionScaleManager.getClientInstance().deserializeFromNBT(new CompoundTag());
    }
    public static void handleChunk(com.lumbra.ldiediet.network.SnapshotChunkPacket msg) {
        try {
            CompoundTag data = TRANSFERS[msg.stream()].accept(msg);
            if (data == null) return;
            if (msg.stream() == com.lumbra.ldiediet.network.SnapshotTransport.CONFIG) {
                configurationBytes = msg.totalBytes();
                handleSyncFoodGroups(new SyncFoodGroupsPacket(data));
            } else {
                if (!data.getBoolean("full") && toleranceSnapshot == null) return;
                CompoundTag next = (CompoundTag) com.lumbra.ldiediet.network.NbtDelta.apply(
                        data.getBoolean("full") ? null : toleranceSnapshot, data.getCompound("delta"));
                toleranceSnapshot = next;
                handleSyncTolerance(new SyncTolerancePacket(next));
            }
        } catch (RuntimeException e) {
            TRANSFERS[msg.stream()].clear();
            com.lumbra.ldiediet.LDieDiet.LOGGER.error("Rejected malformed synchronized snapshot", e);
        }
    }

    public static void handleSyncTolerance(SyncTolerancePacket msg) {
        if (msg.getData() == null) return;
        toleranceSnapshot = msg.getData().copy();
        snapshotPlayer = null;
        restorePlayerSnapshot();
    }

     
    public static void restorePlayerSnapshot() {
        Player player = Minecraft.getInstance().player;
        if (player == null) { snapshotPlayer = null; return; }
        if (toleranceSnapshot == null || snapshotPlayer == player) return;
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null) return;
        var use = cap.getUseSession();
        cap.deserializeNBT(toleranceSnapshot);
        if(use!=null && player.isUsingItem() && use.key().equals(com.lumbra.ldiediet.data.DietConfigManager.getFoodKey(player.getUseItem(),true)))cap.setUseSession(use);
        snapshotPlayer = player;
    }

    public static void handleSyncFoodGroups(SyncFoodGroupsPacket msg) {
        if (msg.getData() != null) {
            CompoundTag data = msg.getData();
            if(data.contains("foodStageProfiles"))com.lumbra.ldiediet.data.FoodStageProfiles.CLIENT.deserialize(data.getCompound("foodStageProfiles"));
            if(data.contains("nutrientFormula"))com.lumbra.ldiediet.math.FormulaManager.clientNutrientSource(data.getString("nutrientFormula"));
            
            if (data.contains("foodGroups")) {
                com.lumbra.ldiediet.data.FoodGroupManager.getClientInstance().deserializeFromNBT(data.getCompound("foodGroups"));
            }
            
            if (data.contains("exclusions")) {
                com.lumbra.ldiediet.data.ExclusionManager.getClientInstance().deserializeFromNBT(data.getCompound("exclusions"));
            }
            
            if (data.contains("dietConfig")) {
                com.lumbra.ldiediet.data.DietConfigManager.deserializeFromNBT(data.getCompound("dietConfig"));
            }

            if (data.contains("nutritionScales")) {
                com.lumbra.ldiediet.data.NutritionScaleManager.getClientInstance().deserializeFromNBT(data.getCompound("nutritionScales"));
            }
            if (data.contains("foodGroups") && Minecraft.getInstance().screen instanceof
                    com.lumbra.ldiediet.client.screen.MonotonyScreen screen) screen.refreshGroups();
            if (data.contains("nutritionScales") && Minecraft.getInstance().screen instanceof
                    com.lumbra.ldiediet.client.screen.NutritionScreen screen) screen.refreshScales();
        }
    }
}
