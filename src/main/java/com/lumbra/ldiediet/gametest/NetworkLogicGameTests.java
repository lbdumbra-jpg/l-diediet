package com.lumbra.ldiediet.gametest;

import com.lumbra.ldiediet.data.DietConfigManager;
import com.lumbra.ldiediet.network.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class NetworkLogicGameTests {
    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_network", timeoutTicks = 100)
    public static void dietaryAttributeSummaryReportsOnlyOwnedModifiers(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            var attribute = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
            var instance = player.getAttribute(attribute);
            for (var modifier : java.util.List.copyOf(instance.getModifiers()))
                if (modifier.getName().startsWith("ldiediet_")) instance.removeModifier(modifier.getId());
            instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID.randomUUID(),
                    "ldiediet_nutrient_test", 2.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID.randomUUID(),
                    "ldiediet_diet:test", .2, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
            instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID.randomUUID(),
                    "foreign_equipment", 100, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            var snapshot = com.lumbra.ldiediet.data.NutritionAttributeSummary.capture(player);
            net.minecraft.nbt.CompoundTag row = null;
            for (int i = 0; i < snapshot.size(); i++) if (snapshot.getCompound(i).getString("attribute").equals("minecraft:generic.attack_damage")) row = snapshot.getCompound(i);
            h.assertTrue(row != null, "Server summary omitted dietary attack modifiers");
            h.assertTrue(row.getList("modifiers", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 2, "Summary included a foreign equipment bonus");
            h.assertTrue(row.getDouble("value") == instance.getValue(), "Summary lost authoritative final value");
            var envelope = new net.minecraft.nbt.CompoundTag(); envelope.put(com.lumbra.ldiediet.data.NutritionAttributeSummary.KEY, snapshot);
            h.assertTrue(com.lumbra.ldiediet.network.SnapshotTransport.decode(com.lumbra.ldiediet.network.SnapshotTransport.encode(envelope)).equals(envelope),
                    "Attribute summary did not survive actual snapshot codec");
            h.succeed();
        } finally { LDieDietGameTests.removePlayer(player); }
    }
    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_network", timeoutTicks = 100)
    public static void lazyRequestsRejectNonfoodsAndEnforceABudget(GameTestHelper h) {
        var player = LDieDietGameTests.makeNetworkedTestPlayer(h);
        try {
            RequestCciPacket.forget(player);
            h.assertTrue(!RequestCciPacket.accept(player, new ResourceLocation("minecraft:stone")), "Nonfood accepted");
            h.assertTrue(!RequestCciPacket.accept(player, new ResourceLocation("absent:item")), "Missing item accepted");
            for (int i = 0; i < 6; i++) h.assertTrue(RequestCciPacket.accept(player, new ResourceLocation("minecraft:bread")), "Valid request rejected inside budget");
            h.assertTrue(!RequestCciPacket.accept(player, new ResourceLocation("minecraft:bread")), "Ninth request bypassed budget");
            h.succeed();
        } finally { RequestCciPacket.forget(player); LDieDietGameTests.removePlayer(player); }
    }
    @GameTest(template = "bastion/blocks/air", batch = "ldiediet_network", timeoutTicks = 100)
    public static void clientAutomaticCciCanBeUpdatedWithoutReplacingProfiles(GameTestHelper h) {
        try {
            CompoundTag data = new CompoundTag();
            var json = com.google.gson.JsonParser.parseString("{\"overrides\":{\"minecraft:bread\":{\"cci\":1,\"cci_source\":\"auto\",\"nutrients\":{\"fixture\":0.7}}}}");
            data.put("config", com.lumbra.ldiediet.util.JsonNbt.encode(json)); data.putDouble("cci_min", 1); data.putDouble("cci_max", 4);
            DietConfigManager.deserializeFromNBT(data);
            CompoundTag delta = new CompoundTag(); delta.putDouble("minecraft:bread", 3); delta.putDouble("invalid ID", Double.NaN);
            DietConfigManager.applyClientCci(delta);
            var stack = new ItemStack(Items.BREAD);
            h.assertTrue(DietConfigManager.getClientCCI(stack) == 3, "Automatic profile froze old CCI");
            h.assertTrue(DietConfigManager.getClientNutrients(stack).get("fixture") == 0.7, "CCI delta replaced nutrient profile");
            h.succeed();
        } finally { DietConfigManager.clearClientSnapshot(); }
    }
}
