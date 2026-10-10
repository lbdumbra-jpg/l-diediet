package com.lumbra.ldiediet.util;

import com.lumbra.ldiediet.consumption.ConsumptionSnapshot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import java.lang.ref.WeakReference;

 
public final class EatingContext {
    public record Frame(Player player, ConsumptionSnapshot portion) {}
    public interface Scope extends AutoCloseable { @Override void close(); }
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<WeakReference<Player>> LEGACY_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<Item> LEGACY_ITEM = new ThreadLocal<>();
    private EatingContext() {}
    public static Frame current() { return CURRENT.get(); }
    public static Scope open(Player player, ConsumptionSnapshot portion) {
        Frame previous = CURRENT.get(); CURRENT.set(new Frame(player, portion));
        return () -> restore(previous);
    }
    public static Scope suspend() {
        Frame previous = CURRENT.get(); CURRENT.remove();
        return () -> restore(previous);
    }
    private static void restore(Frame frame) {
        if (frame == null) CURRENT.remove(); else CURRENT.set(frame);
    }
     
    @Deprecated public static void link(net.minecraft.world.food.FoodData data, Player player) {}
    @Deprecated public static Player getPlayerFromData(net.minecraft.world.food.FoodData data) {
        Player player = getPlayer();
        return player != null && player.getFoodData() == data ? player : null;
    }
    @Deprecated public static void set(Player player, Item item) {
        LEGACY_PLAYER.set(new WeakReference<>(player)); LEGACY_ITEM.set(item);
    }
    @Deprecated public static void clear() {
        LEGACY_PLAYER.remove(); LEGACY_ITEM.remove();
    }
    public static Player getPlayer() {
        Frame frame = CURRENT.get();
        if (frame != null) return frame.player();
        WeakReference<Player> legacy = LEGACY_PLAYER.get();
        return legacy == null ? null : legacy.get();
    }
    public static Item getItem() {
        Frame frame = CURRENT.get();
        return frame == null ? LEGACY_ITEM.get() : frame.portion().key().item();
    }
}
