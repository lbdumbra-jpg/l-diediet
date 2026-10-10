package com.lumbra.ldiediet.gametest;

import com.google.gson.*;
import com.lumbra.ldiediet.*;
import com.lumbra.ldiediet.capability.*;
import com.lumbra.ldiediet.client.*;
import com.lumbra.ldiediet.client.screen.MonotonyScreen;
import com.lumbra.ldiediet.client.screen.NutritionScreen;
import com.lumbra.ldiediet.client.screen.JournalScreen;
import com.lumbra.ldiediet.data.*;
import com.lumbra.ldiediet.mixin.GameRendererAccessor;
import com.lumbra.ldiediet.network.StageMessagePacket;
import com.lumbra.ldiediet.network.ModNetworkHandler;
import com.lumbra.ldiediet.rewards.RewardHandler;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

 
@Mod.EventBusSubscriber(modid = LDieDiet.MOD_ID, value = Dist.CLIENT)
public final class ClientAcceptanceChecks {
    private static int phase, ticks, wait;
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;
    private static PostChain foreign;
    private static byte[] originalConfig;
    private static net.minecraft.nbt.CompoundTag harvestScales, harvestGroups;
    private static final ResourceLocation GROUP = new ResourceLocation("acceptance:overlay");
    private static final ResourceLocation SHADER = new ResourceLocation(UiAssets.DESATURATE_SHADER);
    private static final List<String> passed = new ArrayList<>();
    private static boolean finished;
    private static int expectedUseDuration;
    private static volatile float expectedNutritionAfterDeath;
    private static int chewingSounds;
    @SubscribeEvent public static void hearChewing(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        if(phase==28&&event.getSound()!=null&&event.getSound().getLocation().toString().equals("minecraft:entity.generic.eat"))chewingSounds++;
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ldiediet.clientAcceptance") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance(); ticks++;
        try {
            if (ticks > 2400) throw new IllegalStateException("Client acceptance timed out at phase " + phase);
            if (serverFailure != null) throw new IllegalStateException("Server fixture failed", serverFailure);
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            var cap = CapabilityHandler.get(mc.player);
            if (cap == null) return;
            var accessor = (GameRendererAccessor) mc.gameRenderer;
            switch (phase) {
                case 0 -> {
                    if (ticks < 100 || FoodGroupManager.getClientInstance().getAllGroups().isEmpty()) return;
                    if (Boolean.getBoolean("ldiediet.checkRejoin")) {
                        require(cap.getMonotony(Items.BREAD) == 12, "Rejoin lost the saved monotony before any fixture mutation");
                        passed.add("real rejoin restores saved monotony before fixture mutation");
                    }
                    passed.add("real login/resource snapshot"); phase = 1;
                    onServer(mc, () -> {
                        Path file = FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json");
                        originalConfig = Files.readAllBytes(file);
                        JsonObject root = JsonParser.parseString("""
                                {"overrides":{"minecraft:potion":{"nbt_filters":[
                                  {"match":"{Potion:\\"minecraft:healing\\"}","variant_id":"acceptance:heal","cci":3}]}}}
                                """).getAsJsonObject();
                        root.addProperty("acceptance_metadata", "Ж".repeat(1_100_000));
                        com.lumbra.ldiediet.util.AtomicJsonFile.write(file, new Gson(), root);
                        var server = mc.getSingleplayerServer(); DietConfigManager.loadConfig(server);
                        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                        var tolerance = CapabilityHandler.get(player); tolerance.setMonotony(Items.BREAD, 12);
                        for(var food:List.of(Items.APPLE,Items.BREAD,Items.CARROT,Items.BEETROOT,Items.COOKIE,Items.BAKED_POTATO,Items.MELON_SLICE,Items.PUMPKIN_PIE,Items.COOKED_BEEF,Items.COOKED_CHICKEN)) for(int i=0;i<18;i++) tolerance.recordEaten(food,100);
                        tolerance.setNutrientValue("proteins", 65.5f);
                        com.lumbra.ldiediet.rewards.NutritionAttributeHandler.refresh(player, tolerance);
                        var definition = new FoodGroupDefinition(GROUP, "Acceptance", "", "-o", "", List.of(),
                                List.of(new FoodGroupDefinition.GroupEffect("until_death", "overlay", "grayscale", 0, 0, 0,
                                        null, null, List.of(), List.of(), Map.of(), null)), 1, true, "", "aqua", true);
                        FoodGroupManager.getInstance().getGroupsMap().put(GROUP, definition);
                        var tooltipId=new ResourceLocation("acceptance:tooltip");
                        FoodGroupManager.getInstance().getGroupsMap().put(tooltipId,new FoodGroupDefinition(tooltipId,"Tooltip sentinel","","-o","",List.of("minecraft:bread"),List.of(),1,true,"●","aqua",true));
                        var hiddenId=new ResourceLocation("acceptance:hidden");
                        var hidden=new FoodGroupDefinition(hiddenId,"Secret sentinel","Must stay concealed","","",List.of("minecraft:apple"),definition.getEffects(),3,true,"?","aqua",true).secret(true).presentation("","","minecraft:apple",-999);
                        FoodGroupManager.getInstance().getGroupsMap().put(hiddenId,hidden);
                        RewardHandler.activateGroupReward(player, tolerance, definition);
                        require(com.lumbra.ldiediet.network.SnapshotTransport.encode(DietConfigManager.serializeToNBT()).length > 2 * 1024 * 1024,
                                "Large string was lost before transmission");
                        CapabilityHandler.syncResourcesToClient(player); CapabilityHandler.syncToClient(player);
                        ready = true;
                    });
                }
                case 1 -> {
                    if (!ready || cap.getMonotony(Items.BREAD) != 12 || accessor.ldiediet$getPostEffect() == null) return;
                    if(Boolean.getBoolean("ldiediet.authorAcceptance")) {
                        require(FoodStageProfiles.CLIENT.resolve(new ItemStack(Items.SWEET_BERRIES)).thresholds().equals(List.of(6,12,18,24)), "Author stage profile did not cross actual resource snapshot");
                        var example=FoodGroupManager.getClientInstance().getGroup(new ResourceLocation("mypack:midnight_berries"));
                        require(example!=null&&example.isSecret(),"Documented secret group not synchronized");
                        String name=Component.translatable("mypack.group.midnight_berries").getString();
                        require(!name.equals("mypack.group.midnight_berries")&&!name.isBlank(),"Author resource-pack translation not loaded");
                        passed.add("Author example datapack: stage profiles and secret group cross actual network; resource-pack translation loaded: "+name);
                    }
                    ItemStack potion = new ItemStack(Items.POTION); potion.getOrCreateTag().putString("Potion", "minecraft:healing");
                    if (!DietConfigManager.getFoodKey(potion, true).variantId().equals("acceptance:heal")) return;
                    require(ClientPacketHandler.configurationBytes() > 2 * 1024 * 1024, "Large snapshot did not cross network");
                    require(DietConfigManager.getClientCCI(potion) == 3, "Variant CCI differs after large transfer");
                    passed.add("large config over 2 MiB over actual network; variant and monotony snapshots; owned shader");
                    var originalPlayer = mc.player;
                    var originalConnection = mc.getConnection();
                    var savedTolerance = cap.serializeNBT();
                    savedTolerance.put(com.lumbra.ldiediet.data.NutritionAttributeSummary.KEY, ClientPacketHandler.nutritionAttributes());
                    savedTolerance.put(JournalSnapshot.KEY,ClientPacketHandler.journal().copy());
                    try {
                         
                         
                        mc.player = null;
                        ClientPacketHandler.handleSyncTolerance(new com.lumbra.ldiediet.network.SyncTolerancePacket(savedTolerance));
                        var replacement = new net.minecraft.client.player.LocalPlayer(mc, mc.level, originalConnection,
                                originalPlayer.getStats(), originalPlayer.getRecipeBook(), false, false);
                        mc.player = replacement;
                        ClientPacketHandler.restorePlayerSnapshot();
                        require(CapabilityHandler.get(replacement).getMonotony(Items.BREAD) == 12,
                                "Snapshot arriving before a replacement LocalPlayer was lost");
                    } finally {
                        mc.player = originalPlayer;
                        ClientPacketHandler.restorePlayerSnapshot();
                    }
                    passed.add("cached server snapshot survives absent/replaced LocalPlayer");
                    List<Component> tooltip = new ItemStack(Items.BREAD).getTooltipLines(mc.player, TooltipFlag.NORMAL);
                    require(tooltip.stream().map(Component::getString).anyMatch(s -> s.equals(Component.translatable("ldiediet.tooltip.stage_label",2).getString())), "Compact tooltip stage missing");
                    require(tooltip.stream().map(Component::getString).noneMatch(s -> s.contains("CCI")||s.contains("×")),"Verbose diagnostic tooltip survived");
                    require(tooltip.stream().map(Component::getString).anyMatch(s->s.contains("Tooltip sentinel")),"Known food group name missing in tooltip");
                    require(new ItemStack(Items.APPLE).getTooltipLines(mc.player,TooltipFlag.NORMAL).stream().noneMatch(c->c.getString().contains("Secret sentinel")),"Undiscovered group name leaked in item tooltip");
                    require(new ItemStack(Items.CAKE).getTooltipLines(mc.player, TooltipFlag.NORMAL).stream()
                            .map(Component::getString).anyMatch(s -> s.equals(Component.translatable("ldiediet.tooltip.stage_label",ToleranceStage.getStage(mc.player,Items.CAKE).getLevel()).getString())), "Cake tooltip missing");
                    passed.add("real food/cake tooltip events");
                    phase = 7; wait = 0; ready = false;
                    mc.reloadResourcePacks().whenComplete((value, error) -> { serverFailure = error; ready = true; });
                }
                case 7 -> {
                    if (!ready || mc.getOverlay() != null || mc.isPaused() || ++wait < 10) return;
                    require(accessor.ldiediet$getPostEffect() != null, "Active overlay disappeared after resource reload");
                    phase = 8; wait = 0; ready = false;
                    onServer(mc, () -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var tolerance = CapabilityHandler.get(player);
                        tolerance.getActiveEffects().stream().filter(e -> e.getGroupId().equals(GROUP.toString())).forEach(ActiveGroupEffect::cancel);
                        RewardHandler.tickActiveEffects(player); CapabilityHandler.syncToClient(player); ready = true;
                    });
                }
                case 8 -> {
                    if (!ready || cap.hasActiveEffectsForGroup(GROUP.toString()) || ++wait < 10) return;
                    require(accessor.ldiediet$getPostEffect() == null, "Reloaded owned shader remained after its reward ended");
                    passed.add("actual resource-pack reload preserves overlay ownership and cancellation");
                    phase = 9; wait = 0; ready = false;
                    onServer(mc, () -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var tolerance = CapabilityHandler.get(player);
                        RewardHandler.activateGroupReward(player, tolerance, FoodGroupManager.getInstance().getGroup(GROUP));
                        CapabilityHandler.syncToClient(player); ready = true;
                    });
                }
                case 9 -> {
                    if (!ready || accessor.ldiediet$getPostEffect() == null) return;
                    mc.setScreen(null);
                    KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_N));
                    phase = 10; wait = 0;
                }
                case 10 -> {
                    if (++wait < 5) return;
                    require(mc.screen instanceof NutritionScreen, "N did not open the nutrition dashboard");
                    require(mc.font.width(HarvestUi.text("Ж")) == mc.font.width("Ж") && mc.font.width(HarvestUi.text("i")) == mc.font.width("i") && HarvestUi.FONT.equals(net.minecraft.network.chat.Style.DEFAULT_FONT),
                            "Functional UI text must use the vanilla font and glyph metrics");
                    require(cap.getNutrientValue("proteins") == 65.5f, "Nutrition dashboard did not receive authoritative nutrient values");
                    var nutrition = mc.screen;
                    require(NutritionScaleManager.getClientInstance().getAll().size()>=6,"Native nutrition scales omitted");
                    require(((JournalScreen)nutrition).selectedTab()==JournalScreen.Tab.NUTRITION,"Wrong opening tab");
                    Screenshot.grab(mc.gameDirectory, "ldiediet-nutrition.png", mc.getMainRenderTarget(), msg -> LDieDiet.LOGGER.info("Nutrition screenshot: {}", msg.getString()));
                    boolean bonus = false;
                    var attributes = ClientPacketHandler.nutritionAttributes();
                    for (int i = 0; i < attributes.size(); i++) if (attributes.getCompound(i).getString("attribute").equals("minecraft:generic.max_health")) {
                        var modifiers = attributes.getCompound(i).getList("modifiers", net.minecraft.nbt.Tag.TAG_COMPOUND);
                        for (int j = 0; j < modifiers.size(); j++) if (modifiers.getCompound(j).getDouble("amount") == 2) bonus = true;
                    }
                    require(bonus, "Nutrition dashboard lost authoritative attribute bonuses");
                    phase = 12; wait = 0; ready = false;
                    onServer(mc, () -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        CapabilityHandler.get(player).setNutrientValue("proteins", 82.25f);
                        CapabilityHandler.syncToClient(player); ready = true;
                    });
                }
                case 12 -> {
                    if (!ready || cap.getNutrientValue("proteins") != 82.25f || ++wait < 5) return;
                    require(mc.screen instanceof NutritionScreen, "Live nutrition update closed the dashboard");
                    Screenshot.grab(mc.gameDirectory, "ldiediet-nutrition-live.png", mc.getMainRenderTarget(), msg -> LDieDiet.LOGGER.info("Live nutrition screenshot: {}", msg.getString()));
                    var nutrition = mc.screen;
                    for (var child : nutrition.children()) if (child instanceof Button button && button.getMessage().getString().equals(
                            Component.translatable("ldiediet.journal.effects").getString())) button.onPress();
                    passed.add("N nutrition dashboard, live nutrient levels, reload pagination and attribute snapshot");
                    phase = 11; wait = 0;
                }
                case 11 -> {
                    if (++wait < 5) return;
                    require(mc.screen instanceof NutritionScreen, "Attribute tab navigated away from the dashboard");
                    Screenshot.grab(mc.gameDirectory, "ldiediet-attributes.png", mc.getMainRenderTarget(), msg -> LDieDiet.LOGGER.info("Attribute screenshot: {}", msg.getString()));
                    for (var child : mc.screen.children()) if (child instanceof Button button && button.getMessage().getString().equals(
                            Component.translatable("ldiediet.gui.nutrition.groups").getString())) button.onPress();
                    require(((JournalScreen)mc.screen).selectedTab()==JournalScreen.Tab.GROUPS, "Nutrition dashboard did not open food groups");
                    passed.add("rendered dietary attribute tab and group navigation");
                    phase = 2; wait = 0;
                }
                case 2 -> {
                    if (++wait < 3) return;
                    require(((JournalScreen)mc.screen).selectedTab()==JournalScreen.Tab.GROUPS, "Group screen did not open");
                    var journal=(JournalScreen)mc.screen;journal.mouseScrolled(mc.screen.width/2,mc.screen.height/2,-3);
                    require(journal.scrollPosition()>0,"Group wheel did not scroll");
                    for (var child : mc.screen.children()) if (child instanceof Button button && button.getMessage().getString().equals(">") && button.visible) button.onPress();
                    Screenshot.grab(mc.gameDirectory, "ldiediet-acceptance.png", mc.getMainRenderTarget(), msg -> LDieDiet.LOGGER.info("Acceptance screenshot: {}", msg.getString()));
                    var screen = mc.screen;
                    var originalGroups = FoodGroupManager.getClientInstance().serializeToNBT();
                    try {
                        var changed = new net.minecraft.nbt.CompoundTag();
                        var one = new net.minecraft.nbt.ListTag();
                        var group = originalGroups.getList("groups", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).copy();
                        group.putString("name", "reload_sentinel"); one.add(group); changed.put("groups", one);
                        var update = new net.minecraft.nbt.CompoundTag(); update.put("foodGroups", changed);
                        ClientPacketHandler.handleSyncFoodGroups(new com.lumbra.ldiediet.network.SyncFoodGroupsPacket(update));
                        require(FoodGroupManager.getClientInstance().getAllGroups().size()==1,"Reload did not update groups");
                    } finally {
                        var restore = new net.minecraft.nbt.CompoundTag(); restore.put("foodGroups", originalGroups);
                        ClientPacketHandler.handleSyncFoodGroups(new com.lumbra.ldiediet.network.SyncFoodGroupsPacket(restore));
                    }
                    passed.add("open group screen refreshes and clamps its page after resource sync");
                    mc.setScreen(null); passed.add("rendered group screen and next-page button");
                    mc.gameRenderer.loadEffect(SHADER); foreign = accessor.ldiediet$getPostEffect();
                    require(foreign != null, "External shader fixture did not load");
                    ready = false; phase = 3; wait = 0;
                    onServer(mc, () -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var tolerance = CapabilityHandler.get(player);
                        tolerance.getActiveEffects().stream().filter(e -> e.getGroupId().equals(GROUP.toString())).forEach(ActiveGroupEffect::cancel);
                        RewardHandler.tickActiveEffects(player); CapabilityHandler.syncToClient(player); ready = true;
                    });
                }
                case 3 -> {
                    if (!ready || cap.hasActiveEffectsForGroup(GROUP.toString()) || ++wait < 5) return;
                    require(accessor.ldiediet$getPostEffect() == foreign, "Cancelling owned overlay removed a foreign shader with the same path");
                    passed.add("foreign shader ownership");
                    mc.gameRenderer.shutdownEffect(); foreign = null;
                    mc.gui.setOverlayMessage(Component.literal("acceptance-baseline"), false);
                    LDieDietClientConfig.SHOW_STAGE_ACTIONBAR.set(false);
                    ready = false; phase = 4; wait = 0;
                    onServer(mc, () -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        ModNetworkHandler.sendToPlayer(new StageMessagePacket(Component.literal("acceptance-stage")), player); ready = true;
                    });
                }
                case 4 -> {
                    if (!ready || ++wait < 5) return;
                    var field = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString"); field.setAccessible(true);
                    require(((Component) field.get(mc.gui)).getString().equals("acceptance-baseline"), "Disabled actionbar preference ignored");
                    LDieDietClientConfig.SHOW_STAGE_ACTIONBAR.set(true);
                    ClientPacketHandler.handleStageMessage(Component.literal("acceptance-enabled"));
                    require(((Component) field.get(mc.gui)).getString().equals("acceptance-enabled"), "Enabled actionbar missing");
                    passed.add("actionbar off over actual network / on");
                    phase = 5; ready = false;
                    onServer(mc, () -> {
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        expectedNutritionAfterDeath=(float)(CapabilityHandler.get(p).getNutrientValue("proteins")*(1-LDieDietServerConfig.NUTRITION_DEATH_LOSS_PERCENT.get()/100));
                        p.kill();
                    });
                }
                case 5 -> {
                    if (!mc.player.isDeadOrDying()) return;
                    mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    mc.setScreen(null); phase = 6; wait = 0;
                }
                case 6 -> {
                    if (mc.player.isDeadOrDying() || ++wait < 30) return;
                    require(CapabilityHandler.get(mc.player).getMonotony(Items.BREAD) == 12, "Respawn lost monotony/full client baseline");
                    require(Math.abs(CapabilityHandler.get(mc.player).getNutrientValue("proteins")-expectedNutritionAfterDeath)<.001,"Percentage nutrition loss did not cross actual respawn snapshot");
                    passed.add("Configured percentage nutrition loss crosses real death/respawn network snapshot");
                    passed.add("real death/respawn and full client baseline");
                    mc.setScreen(new MonotonyScreen()); mc.screen.resize(mc,320,200);
                    phase=20; wait=0;
                }
                case 20 -> {
                    if (++wait < 5) return;
                    for (var child:mc.screen.children()) if(child instanceof Button b && b.visible)
                        require(b.getX()>=0 && b.getY()>=0 && b.getX()+b.getWidth()<=320 && b.getY()+b.getHeight()<=200,
                                "Responsive group control escaped the viewport");
                    Screenshot.grab(mc.gameDirectory,"harvest-small-groups.png",mc.getMainRenderTarget(),msg -> {});
                    for (var child:mc.screen.children()) if(child instanceof Button b && b.getMessage().getString().equals(
                            Component.translatable("ldiediet.journal.effects").getString())) b.onPress();
                    require(((JournalScreen)mc.screen).selectedTab()==JournalScreen.Tab.EFFECTS,"Small effects button opened wrong tab");
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
                    require(mc.screen==null,"Escape did not close Harvest menu");
                    passed.add("Vanilla font, small viewport controls, groups-to-attributes and Escape");
                    harvestScales=NutritionScaleManager.getClientInstance().serializeToNBT();
                    var update=new net.minecraft.nbt.CompoundTag();
                    var empty=new net.minecraft.nbt.CompoundTag(); empty.put("definitions",new net.minecraft.nbt.ListTag());
                    update.put("nutritionScales",empty); ClientPacketHandler.handleSyncFoodGroups(new com.lumbra.ldiediet.network.SyncFoodGroupsPacket(update));
                    mc.setScreen(new NutritionScreen()); phase=21; wait=0;
                }
                case 21 -> {
                    if (++wait < 5) return;
                    Screenshot.grab(mc.gameDirectory,"harvest-empty-nutrition.png",mc.getMainRenderTarget(),msg -> {});
                    var update=new net.minecraft.nbt.CompoundTag(); update.put("nutritionScales",harvestScales);
                    ClientPacketHandler.handleSyncFoodGroups(new com.lumbra.ldiediet.network.SyncFoodGroupsPacket(update));
                    harvestGroups=FoodGroupManager.getClientInstance().serializeToNBT();
                    var empty=new net.minecraft.nbt.CompoundTag(); empty.put("groups",new net.minecraft.nbt.ListTag());
                    update=new net.minecraft.nbt.CompoundTag(); update.put("foodGroups",empty);
                    ClientPacketHandler.handleSyncFoodGroups(new com.lumbra.ldiediet.network.SyncFoodGroupsPacket(update));
                    mc.setScreen(new MonotonyScreen()); phase=22; wait=0;
                }
                case 22 -> {
                    if (++wait < 5) return;
                    Screenshot.grab(mc.gameDirectory,"harvest-empty-groups.png",mc.getMainRenderTarget(),msg -> {});
                    var update=new net.minecraft.nbt.CompoundTag(); update.put("foodGroups",harvestGroups);
                    ClientPacketHandler.handleSyncFoodGroups(new com.lumbra.ldiediet.network.SyncFoodGroupsPacket(update));
                    passed.add("Harvest nutrition/group empty states render and recover after resource sync");
                    mc.setScreen(new MonotonyScreen() {
                        @Override public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float partial) {
                            super.render(g, width/2, (height-Math.min(262,height-16))/2+65, partial);
                        }
                    });
                    phase=23; wait=0;
                }
                case 23 -> {
                    if (++wait < 5) return;
                    Screenshot.grab(mc.gameDirectory,"harvest-group-tooltip.png",mc.getMainRenderTarget(),msg -> {});
                    passed.add("Vanilla group tooltip rendered for visual inspection");
                    mc.setScreen(new JournalScreen(JournalScreen.Tab.FOOD));phase=24;wait=0;
                }
                case 24 -> {
                    if(++wait<5)return;
                    require(cap.getEatenCounts().size()>=10,"Saved food list lost on death/respawn");
                    Screenshot.grab(mc.gameDirectory,"journal-foods.png",mc.getMainRenderTarget(),msg->{});
                    var journal=(JournalScreen)mc.screen;journal.mouseScrolled(journal.width/2,journal.height/2,-4);
                    require(journal.scrollPosition()>0,"Food wheel did not scroll");phase=25;wait=0;
                }
                case 25 -> {
                    if(++wait<5)return;
                    Screenshot.grab(mc.gameDirectory,"journal-foods-scrolled.png",mc.getMainRenderTarget(),msg->{});
                    for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().equals(Component.translatable("ldiediet.journal.next").getString()))b.onPress();
                    require(((JournalScreen)mc.screen).selectedTab()==JournalScreen.Tab.GROUPS,"Arrow paginated instead of switching tabs");
                    passed.add("Lifetime food journal after death, vertical scrolling and arrow tab navigation");
                    mc.setScreen(new JournalScreen(JournalScreen.Tab.GROUPS){
                        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){super.render(g,width/2,(height-Math.min(262,height-16))/2+65,partial);}
                    });phase=26;wait=0;
                }
                case 26 -> {
                    if(++wait<5)return;
                    var hover=JournalScreen.class.getDeclaredField("hover");hover.setAccessible(true);
                    var lines=(List<Component>)hover.get(mc.screen);
                    require(lines!=null&&lines.stream().noneMatch(c->c.getString().contains("Secret sentinel")),"Secret group name leaked in hovered card");
                    Screenshot.grab(mc.gameDirectory,"journal-secret-group.png",mc.getMainRenderTarget(),msg->{});
                    passed.add("Secret group renders a masked card and concealed hover");
                    mc.setScreen(null);ready=false;wait=0;phase=27;
                    onServer(mc,()->{
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var c=CapabilityHandler.get(p);c.setMonotony(Items.APPLE,ToleranceStage.getBaseThresholds()[3]);
                        p.getInventory().selected=0;p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.APPLE,4));
                        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);CapabilityHandler.syncToClient(p);ready=true;
                    });
                }
                case 27 -> {
                     
                     
                    if(!ready||++wait<15||!mc.player.getMainHandItem().is(Items.APPLE)||mc.player.getFoodData().getFoodLevel()!=0||mc.player.getAbilities().instabuild)return;
                    require(ToleranceStage.getStage(mc.player,Items.APPLE).getLevel()==4,"Client did not receive eating test stage");
                    expectedUseDuration=Math.round(32*ToleranceStage.STAGE_4.getChewingMultiplier());
                    chewingSounds=0;mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND);phase=28;wait=0;
                }
                case 28 -> {
                    mc.options.keyUse.setDown(true);if(++wait<25)return;
                    require(mc.player.isUsingItem()&&mc.player.getUseItem().getUseDuration()==expectedUseDuration,"Client lost the slowed item duration: using="+mc.player.isUsingItem()+", actual="+mc.player.getUseItem().getUseDuration()+", expected="+expectedUseDuration+", remaining="+mc.player.getUseItemRemainingTicks()+", session="+(cap.getUseSession()==null?"none":cap.getUseSession().chewingMultiplier()));
                    require(cap.getUseSession()!=null,"Network snapshot erased ongoing eating timing");
                    require(chewingSounds>0,"Slowed food remained silent during the first 25 ticks");
                    passed.add("Eating sound starts during the early part of slowed chewing");
                    if(net.minecraftforge.fml.ModList.get().isLoaded("gea")) {
                        var handler=Class.forName("net.grapes.gea.EatingAnimationHandler");var field=handler.getDeclaredField("activeAnimations");field.setAccessible(true);
                        var animation=((Map<?,?>)field.get(null)).get(mc.player);
                        require(animation!=null,"GEA has no live local animation");
                        require((Integer)animation.getClass().getMethod("getTotalDurationTicks").invoke(animation)==expectedUseDuration,"GEA client animation did not stretch with chewing");
                        var model=mc.getItemRenderer().getModel(mc.player.getUseItem(),mc.level,mc.player,0);
                        require(!model.getParticleIcon().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"GEA rendered a missing-texture cube while chewing");
                        var config=Class.forName("net.grapes.gea.EatingAnimationConfig");
                        var configured=(Map<?,?>)config.getMethod("getAllAnimations").invoke(null);
                        for(var frames:configured.values())for(var frame:(List<?>)frames) {
                            var id=new ResourceLocation(frame.toString());String path=id.getPath().startsWith("item/")?id.getPath():"item/"+id.getPath();
                            var baked=mc.getModelManager().getModel(new ResourceLocation(id.getNamespace(),path));
                            require(!baked.getParticleIcon().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Missing animation frame texture: "+frame);
                        }
                        passed.add("Every configured GEA food frame has a baked model and real texture");
                        Screenshot.grab(mc.gameDirectory,"chewing-gea-model.png",mc.getMainRenderTarget(),msg->{});
                        passed.add("Eating model has a real texture during slowed GEA chewing");
                        passed.add("Real GEA client animation duration matches slowed food after a state snapshot");
                    }
                    mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);finish(mc,null);
                }
            }
        } catch (Throwable error) { finish(mc, error); }
    }
    private interface Work { void run() throws Exception; }
    private static void onServer(Minecraft mc, Work work) {
        mc.getSingleplayerServer().execute(() -> { try { work.run(); } catch (Throwable error) { serverFailure = error; } });
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void finish(Minecraft mc, Throwable error) {
        finished = true;
        mc.options.keyUse.setDown(false);
        try {
            LDieDietClientConfig.SHOW_STAGE_ACTIONBAR.set(true);
            if (mc.getSingleplayerServer() != null && originalConfig != null) onServer(mc, () ->
                    Files.write(FMLPaths.CONFIGDIR.get().resolve("ldiediet/diet_config.json"), originalConfig));
            JsonObject result = new JsonObject(); result.addProperty("success", error == null); result.addProperty("phase", phase);
            result.add("passed", new Gson().toJsonTree(passed)); if (error != null) result.addProperty("error", error.toString());
            Files.writeString(mc.gameDirectory.toPath().resolve("client-acceptance-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            if (error == null) LDieDiet.LOGGER.info("[LDieDiet CLIENT ACCEPTANCE] PASS {}", passed);
            else LDieDiet.LOGGER.error("[LDieDiet CLIENT ACCEPTANCE] FAILED phase {}", phase, error);
        } catch (Exception reportError) { LDieDiet.LOGGER.error("Cannot save client acceptance report", reportError); }
        mc.stop();
    }
}
