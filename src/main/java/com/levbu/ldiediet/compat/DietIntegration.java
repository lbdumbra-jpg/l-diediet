package com.levbu.ldiediet.compat;

import com.illusivesoulworks.diet.api.DietApi;
import com.illusivesoulworks.diet.api.type.IDietGroup;
import com.illusivesoulworks.diet.api.type.IDietResult;
import com.illusivesoulworks.diet.api.type.IDietTracker;
import com.illusivesoulworks.diet.common.capability.DietCapability;
import com.levbu.ldiediet.LDieDiet;
import com.levbu.ldiediet.ToleranceStage;
import com.levbu.ldiediet.capability.CapabilityHandler;
import com.levbu.ldiediet.capability.FoodToleranceEntry;
import com.levbu.ldiediet.capability.IFoodTolerance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * Интеграция с модом Diet.
 * Использует только публичный API: DietApi, IDietGroup, IDietResult, IDietTracker.
 *
 * Diet API reference:
 * - DietApi.getInstance().getGroups(player, stack) → Set<IDietGroup>
 * - DietApi.getInstance().get(player, stack)       → IDietResult → Map<IDietGroup, Float> (gain per group)
 * - DietCapability.get(player)                     → LazyOptional<IDietTracker>
 * - IDietTracker.getValue(groupName)               → current value (0.0–1.0)
 * - IDietTracker.setValue(groupName, value)         → set value directly
 * - IDietTracker.sync()                            → sync to client
 */
public class DietIntegration {

    // ==================== ЧТЕНИЕ ДАННЫХ (ПУБЛИЧНЫЙ API) ====================

    /**
     * Получить группы, к которым принадлежит предмет.
     */
    public static Set<IDietGroup> getGroups(Player player, ItemStack stack) {
        try {
            return DietApi.getInstance().getGroups(player, stack);
        } catch (Exception e) {
            return Set.of();
        }
    }

    /**
     * Получить количество Diet-групп для данного предмета.
     * Используется для расчёта бонусных порогов надоедания.
     */
    public static int countDietGroups(Player player, ItemStack stack) {
        if (!LDieDiet.isDietLoaded()) return 1;
        Set<IDietGroup> groups = getGroups(player, stack);
        return groups.isEmpty() ? 1 : Math.min(5, groups.size());
    }

    /**
     * Проверить, входит ли предмет в указанную Diet-группу.
     */
    public static boolean isInDietGroup(Player player, ItemStack stack, String groupName) {
        if (!LDieDiet.isDietLoaded()) return false;
        try {
            for (IDietGroup group : getGroups(player, stack)) {
                if (group.getName().equalsIgnoreCase(groupName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Diet мод мог не загрузиться полностью
        }
        return false;
    }

    /**
     * Получить результат применения Diet для предмета (gain по группам).
     * Это официальный способ узнать, сколько Diet добавит каждой группе.
     */
    @Nullable
    public static IDietResult getDietResult(Player player, ItemStack stack) {
        if (!LDieDiet.isDietLoaded()) return null;
        try {
            return DietApi.getInstance().get(player, stack);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получить текущее значение Diet-группы у игрока (0.0–1.0).
     */
    public static float getDietValue(Player player, String groupName) {
        if (!LDieDiet.isDietLoaded()) return 0f;
        try {
            return DietCapability.get(player)
                    .map(tracker -> tracker.getValue(groupName))
                    .orElse(0f);
        } catch (Throwable e) {
            return 0f;
        }
    }

    /**
     * Установить значение Diet-группы у игрока с синхронизацией.
     * Используется для коррекции значений при надоедании.
     */
    public static void setDietValue(Player player, String groupName, float value) {
        if (!LDieDiet.isDietLoaded()) return;
        try {
            DietCapability.get(player).ifPresent(tracker -> {
                tracker.setValue(groupName, Math.max(0f, Math.min(1f, value)));
                tracker.sync();
            });
        } catch (Throwable e) {
            // Fail silently — Diet may not be ready
        }
    }

    // ==================== КОРРЕКЦИЯ DIET ПРИ НАДОЕДАНИИ ====================

    /**
     * Получить множители для Diet-групп на основе стадии надоедания.
     * @return [positiveMult, negativeMult]
     */
    public static float[] getDietMultipliers(Player player, Item item) {
        IFoodTolerance cap = CapabilityHandler.get(player);
        if (cap == null) return new float[]{1.0f, 1.0f};

        FoodToleranceEntry entry = cap.getEntryOrNull(item);
        ToleranceStage stage = ToleranceStage.fromEntry(entry, item, player);

        if (stage.getLevel() < 3) return new float[]{1.0f, 1.0f};

        return new float[]{
                stage.getPositiveDietMultiplier(),
                stage.getNegativeDietMultiplier()
        };
    }

    /**
     * Применить коррекцию Diet-значений после еды.
     * Вызывается ПОСЛЕ того, как Diet уже обработал еду.
     *
     * Логика:
     * - Берём official gain из IDietResult
     * - Сравниваем фактическое изменение значения (before → after)
     * - Корректируем разницу с учётом множителя надоедания
     *
     * @param valuesBefore значения Diet-групп ДО еды
     */
    public static void applyDietCorrection(Player player, ItemStack stack, Item item,
                                           Map<String, Float> valuesBefore) {
        if (valuesBefore.isEmpty()) return;

        float[] multipliers = getDietMultipliers(player, item);
        float positiveMult = multipliers[0];

        // Если множитель 1.0 — коррекция не нужна
        if (positiveMult >= 1.0f) return;

        IDietResult dietResult = getDietResult(player, stack);
        if (dietResult == null) return;

        Map<IDietGroup, Float> resultMap = dietResult.get();
        if (resultMap.isEmpty()) return;

        for (Map.Entry<IDietGroup, Float> entry : resultMap.entrySet()) {
            IDietGroup group = entry.getKey();
            float officialGain = entry.getValue();
            if (officialGain <= 0) continue;

            String groupName = group.getName().toLowerCase();
            float before = valuesBefore.getOrDefault(groupName, -1f);
            if (before < 0) continue;

            float after = getDietValue(player, groupName);
            float actualDelta = after - before;

            if (actualDelta <= 0) continue;

            // Корректируем: оставляем только positiveMult * actualDelta
            float correctedDelta = actualDelta * positiveMult;
            float targetValue = before + correctedDelta;
            setDietValue(player, groupName, targetValue);
        }
    }

    /**
     * Сохранить текущие Diet-значения для групп, к которым принадлежит предмет.
     * Вызвать ДО еды, чтобы потом сравнить с "после".
     */
    public static Map<String, Float> captureDietValues(Player player, ItemStack stack) {
        Map<String, Float> values = new java.util.HashMap<>();
        if (!LDieDiet.isDietLoaded()) return values;

        try {
            Set<IDietGroup> itemGroups = getGroups(player, stack);
            for (IDietGroup group : itemGroups) {
                String groupName = group.getName().toLowerCase();
                values.put(groupName, getDietValue(player, groupName));
            }
        } catch (Exception e) {
            // Diet не готов
        }
        return values;
    }
}
