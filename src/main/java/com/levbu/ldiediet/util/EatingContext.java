package com.levbu.ldiediet.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.Map;

/**
 * Контекст текущего поедания еды.
 * Используется для передачи данных между миксинами и обработчиками событий
 * в рамках одного акта еды.
 */
public final class EatingContext {

    private static final ThreadLocal<Item> currentItem = new ThreadLocal<>();
    private static final ThreadLocal<Player> currentPlayer = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Float>> dietValuesBefore = ThreadLocal.withInitial(java.util.HashMap::new);

    // Связь FoodData → Player (слабые ссылки, чтобы не утекала память)
    private static final Map<net.minecraft.world.food.FoodData, Player> foodDataPlayers =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private EatingContext() {}

    /** Связать FoodData с игроком (вызывается в MixinPlayer при инициализации). */
    public static void link(net.minecraft.world.food.FoodData data, Player player) {
        foodDataPlayers.put(data, player);
    }

    /** Получить игрока по FoodData (для MixinFoodData). */
    public static Player getPlayerFromData(net.minecraft.world.food.FoodData data) {
        return foodDataPlayers.get(data);
    }

    /** Установить контекст еды (вызывается в MixinPlayer перед едой). */
    public static void set(Player player, Item item) {
        currentPlayer.set(player);
        currentItem.set(item);
    }

    /** Сохранить Diet-значения ДО еды для последующей коррекции. */
    public static void setDietValues(Map<String, Float> values) {
        dietValuesBefore.get().clear();
        dietValuesBefore.get().putAll(values);
    }

    /** Очистить контекст (вызывается после обработки еды). */
    public static void clear() {
        currentPlayer.remove();
        currentItem.remove();
        dietValuesBefore.get().clear();
    }

    public static Player getPlayer() { return currentPlayer.get(); }
    public static Item getItem() { return currentItem.get(); }
    public static Map<String, Float> getDietValuesBefore() { return dietValuesBefore.get(); }
}
