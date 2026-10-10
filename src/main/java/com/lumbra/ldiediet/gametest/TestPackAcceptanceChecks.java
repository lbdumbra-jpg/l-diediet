package com.lumbra.ldiediet.gametest;

import com.google.gson.*;
import com.lumbra.ldiediet.LDieDiet;
import com.lumbra.ldiediet.api.PackApi;
import com.lumbra.ldiediet.capability.CapabilityHandler;
import com.lumbra.ldiediet.consumption.FoodConsumption;
import com.lumbra.ldiediet.data.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

 
public final class TestPackAcceptanceChecks {
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    public static void verify(MinecraftServer server) throws Exception {
        var manager = FoodGroupManager.getInstance();
        var groups = manager.getAllGroups();
        require(groups.size() == 11, "Expected 11 laboratory groups, got " + groups.size());
        require(groups.stream().allMatch(g -> g.getId().getNamespace().equals("ldd_lab")), "Old groups leaked through pack filter");
        var primary = Set.of("fruit", "vegetables", "bakery", "raw_meat", "cooked_meat", "fish", "soups", "risky", "honey");
        var report = new JsonObject();
        var coverage = new JsonObject();
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("minecraft") || (!item.isEdible() && item != Items.CAKE)) continue;
            var assigned = manager.getGroupsForItem(id.toString()).stream()
                .filter(g -> primary.contains(g.getId().getPath())).toList();
            require(assigned.size() == 1, "Primary category count for " + id + ": " + assigned.size());
            coverage.addProperty(id.toString(), assigned.get(0).getId().toString());
        }
        require(coverage.size() == 41, "Vanilla food count changed: " + coverage.size());
        report.add("vanilla_foods", coverage);
        var completed = new JsonArray();
        for (var group : groups) {
            var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "ldd-lab-test"));
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player);
            try {
                require(!group.isSecret() || !group.isKnown(CapabilityHandler.get(player)), "Secret revealed before eating");
                var foods = BuiltInRegistries.ITEM.stream().filter(i -> group.containsItem(BuiltInRegistries.ITEM.getKey(i).toString())).toList();
                for (int n = 0; n < group.getThreshold(); n++) {
                    var item = foods.get(group.isRequireUnique() ? n : 0);
                    player.getFoodData().setFoodLevel(0); player.getFoodData().setSaturation(0);
                    var stack = new ItemStack(item);
                    if (item == Items.CAKE) FoodConsumption.cake(player, player.getFoodData(), 2, .1f);
                    else stack.finishUsingItem(player.level(), player);
                }
                require(PackApi.activatedGroups(player).contains(group.getId().toString()), "Eating did not activate " + group.getId());
                var advancement = server.getAdvancements().getAdvancement(new ResourceLocation(group.getAdvancement()));
                require(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone(), "Missing achievement " + group.getAdvancement());
                require(group.isKnown(CapabilityHandler.get(player)), "Group not discovered after reward");
                if (group.isSecret()) {
                    PackApi.cancelGroup(player, group.getId().toString());
                    require(group.isKnown(CapabilityHandler.get(player)), "Secret forgotten after reward ended");
                }
                completed.add(group.getId().toString());
            } finally { LDieDietGameTests.removePlayer(player); }
        }
         
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "ldd-cake-test"));
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        try {
            FoodConsumption.cake(player, player.getFoodData(), 2, .1f);
            require(PackApi.groupProgress(player, "ldd_lab:bakery") == 1, "Cake slice did not count for bakery");
        } finally { LDieDietGameTests.removePlayer(player); }
        verifyNutritionTiers(server, report);
        var issues = AuthorDiagnostics.validate(server);
        require(issues.isEmpty(), "Pack validation issues: " + issues);
        report.add("activated_and_awarded", completed);
        report.addProperty("old_groups_absent", true);
        report.addProperty("cake_slice_counted", true);
        report.addProperty("secret_discovery_persistent", true);
        report.addProperty("success", true);
        Files.writeString(FMLPaths.GAMEDIR.get().resolve("lab-pack-acceptance.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        LDieDiet.LOGGER.info("LAB PACK PASS: {} vanilla foods; {} groups; all achievements and hidden-group discovery", coverage.size(), groups.size());
    }
    private static void verifyNutritionTiers(MinecraftServer server,JsonObject report) {
        var player=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"ldd-tier-test"));
        var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);server.getPlayerList().placeNewPlayer(connection,player);
        try {
            var cap=CapabilityHandler.get(player);
            require(com.lumbra.ldiediet.compat.DietCompatibilityData.INSTANCE.rules("builtin").isEmpty(),"Old Diet bonuses still overlap laboratory tiers");
            var result=new JsonObject();
            var attributes=Map.of("health",net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                "damage",net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,"speed",net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                "attack_speed",net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,"armor",net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                "toughness",net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,"knockback",net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
                "luck",net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
            for(String name:List.of("fruits","grains","proteins","sugars","vegetables")) {
                String key=name.equals("grains")?"diet:grains":name;
                var rows=new JsonArray();
                for(float value:new float[]{79.9f,80,99.9f,100,99.9f,79.9f}) {
                    cap.clearNutrients();cap.setNutrientValue(key,value);
                    com.lumbra.ldiediet.rewards.NutritionAttributeHandler.refresh(player,cap);
                    com.lumbra.ldiediet.compat.DietSuiteEffects.refresh(player,cap);
                    boolean active=value>=80,full=value==100;var additions=new HashMap<String,Double>();var multipliers=new HashMap<String,Double>();
                    if(active)switch(name) {
                        case "fruits"->{multipliers.put("speed",full?.10:.05);if(full)additions.put("luck",1.0);}
                        case "grains"->{additions.put("knockback",full?.10:.05);if(full)additions.put("toughness",1.0);}
                        case "proteins"->{additions.put("health",full?4.0:2.0);if(full)additions.put("damage",1.0);}
                        case "sugars"->{multipliers.put("attack_speed",full?.10:.05);if(full)multipliers.put("speed",.05);}
                        case "vegetables"->{additions.put("armor",full?2.0:1.0);if(full)additions.put("toughness",.5);}
                    }
                    var row=new JsonObject();row.addProperty("percent",value);
                    for(var attribute:attributes.entrySet()) {
                        double base=player.getAttribute(attribute.getValue()).getBaseValue();
                        double expected=(base+additions.getOrDefault(attribute.getKey(),0.0))*(1+multipliers.getOrDefault(attribute.getKey(),0.0));
                        double actual=player.getAttributeValue(attribute.getValue());
                        require(Math.abs(actual-expected)<.00001,"Incorrect "+name+" tier "+value+" attribute "+attribute.getKey()+": "+actual+" expected "+expected);
                        row.addProperty(attribute.getKey(),actual);
                    }
                    rows.add(row);
                }
                cap.setNutrientValue(key,100);com.lumbra.ldiediet.rewards.NutritionAttributeHandler.refresh(player,cap);
                var markers=JournalSnapshot.capture(player,cap).getList("markers",net.minecraft.nbt.Tag.TAG_COMPOUND);var boundaries=new TreeSet<Double>();
                for(var tag:markers){var m=(net.minecraft.nbt.CompoundTag)tag;if(m.getString("scale").equals(key))boundaries.add(m.getDouble("value"));}
                require(boundaries.equals(Set.of(80.0,100.0)),"Wrong tier markers for "+name+": "+boundaries);
                result.add(name,rows);
            }
            report.add("nutrition_tier_transitions",result);
        } finally {LDieDietGameTests.removePlayer(player);}
    }
}
