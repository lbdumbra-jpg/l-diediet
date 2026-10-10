package com.lumbra.ldiediet.api;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
 
public final class GroupRewardEvent extends Event {
    private final Player player;
    private final ResourceLocation group;
    public GroupRewardEvent(Player player,ResourceLocation group) { this.player=player;this.group=group; }
    public Player getPlayer() { return player; }
    public ResourceLocation getGroup() { return group; }
}
