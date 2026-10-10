package com.lumbra.ldiediet.capability;

import com.lumbra.ldiediet.data.FoodGroupDefinition;
import com.lumbra.ldiediet.util.GroupIds;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

 
public final class ActiveGroupEffect {
    public enum State { WAITING, ACTIVE, PAUSED, EXPIRED, CANCELLED }
    private final String groupId, effectType, effectId, durationType;
    private final double value;
    private final int amplifier;
    private int definitionIndex = -1;
    private final int baseDuration, intervalActiveMin, intervalActiveMax, intervalPauseMin, intervalPauseMax;
    private final String[] cancelOnGroups;
    private final Map<String, Double> durationMultipliers;
    private State state;
    private int remainingTicks, phaseTicksRemaining;
    private double appliedDurationMultiplier = 1;
    private long lastAdvancedGameTime = Long.MIN_VALUE;
    private boolean activationClosed;
    private boolean conditionsMet=true;
    private net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation=net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
    public void setConditionsMet(boolean value) { conditionsMet=value; }
    public net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation getOperation() { return operation; }

     
    public ActiveGroupEffect(String group, String type, String id, double value, int amplifier,
            String durationType, int ticks, int activeMin, int activeMax, int pauseMin, int pauseMax,
            String[] cancelGroups, Map<String, Double> multipliers) {
        this(group, type, id, value, amplifier, durationType, ticks, activeMin, activeMax,
                pauseMin, pauseMax, cancelGroups, multipliers, State.ACTIVE);
        remainingTicks = baseDuration;
        phaseTicksRemaining = isIntermittent() ? activePhase() : 0;
        if (isFixed() && remainingTicks <= 0) state = State.EXPIRED;
    }

    private ActiveGroupEffect(String group, String type, String id, double value, int amplifier,
            String durationType, int ticks, int activeMin, int activeMax, int pauseMin, int pauseMax,
            String[] cancelGroups, Map<String, Double> multipliers, State state) {
        this.groupId = GroupIds.canonical(group);
        this.effectType = type; this.effectId = id; this.value = Double.isFinite(value) ? value : 0;
        this.amplifier = Math.max(0, amplifier); this.durationType = durationType;
        baseDuration = Math.max(0, ticks);
        intervalActiveMin = Math.max(0, Math.min(activeMin, activeMax));
        intervalActiveMax = Math.max(0, Math.max(activeMin, activeMax));
        intervalPauseMin = Math.max(0, Math.min(pauseMin, pauseMax));
        intervalPauseMax = Math.max(0, Math.max(pauseMin, pauseMax));
        cancelOnGroups = cancelGroups == null ? new String[0] : cancelGroups.clone();
        durationMultipliers = multipliers == null ? Map.of() : Map.copyOf(multipliers);
        this.state = state;
    }

    public static ActiveGroupEffect waiting(String group, int index, FoodGroupDefinition.GroupEffect definition) {
        int[] active = definition.getIntervalActiveTicks(), pause = definition.getIntervalPauseTicks();
        ActiveGroupEffect effect = new ActiveGroupEffect(group, definition.getEffectType(), definition.getEffectId(),
                definition.getValue(), definition.getAmplifier(), definition.getDurationType(), definition.getDurationTicks(),
                active[0], active[1], pause[0], pause[1], definition.getCancelOnGroups().toArray(String[]::new),
                definition.getDurationMultipliers(), State.WAITING);
        effect.definitionIndex = index;
        effect.operation = definition.getOperation();
        return effect;
    }

    public void start(Set<String> activatedGroups) {
        if (state != State.WAITING) return;
        double multiplier = 1;
        for (var entry : durationMultipliers.entrySet()) {
            if (activatedGroups.contains(GroupIds.canonical(entry.getKey(), GroupIds.namespace(groupId)))
                    && Double.isFinite(entry.getValue()) && entry.getValue() >= 0) multiplier *= entry.getValue();
        }
        appliedDurationMultiplier = Double.isFinite(multiplier) ? multiplier : Integer.MAX_VALUE;
        remainingTicks = scaled(baseDuration);
        phaseTicksRemaining = isIntermittent() ? activePhase() : 0;
        state = isFixed() && remainingTicks <= 0 ? State.EXPIRED : State.ACTIVE;
    }
    public void start(Set<String> groups, long gameTime) { start(groups); lastAdvancedGameTime = gameTime; }
    public boolean tick(long gameTime) {
        if (gameTime == lastAdvancedGameTime) return isTerminal();
        lastAdvancedGameTime = gameTime;
        return tick();
    }

    public boolean tick() {
        if (isTerminal()) return true;
        if (state == State.WAITING) return false;
        if (isFixed() && --remainingTicks <= 0) { state = State.EXPIRED; return true; }
        if (isIntermittent() && phaseTicksRemaining >= 0 && --phaseTicksRemaining <= 0) {
            if (state == State.ACTIVE) {
                state = State.PAUSED; phaseTicksRemaining = roll(intervalPauseMin, intervalPauseMax);
            } else { state = State.ACTIVE; phaseTicksRemaining = activePhase(); }
        }
        return false;
    }

    public void expire() { state = State.EXPIRED; }
    public boolean isActivationClosed() { return activationClosed; }
    public void markActivationClosed() { activationClosed = true; }
    public void cancel() { state = State.CANCELLED; }
    public boolean isTerminal() { return state == State.EXPIRED || state == State.CANCELLED; }
    public boolean isLive() { return !isTerminal(); }
    public boolean isPersistent() { return isUntilDeath() || "until_death_intermittent".equals(durationType); }
    public State getState() { return state; }
    public String getGroupId() { return groupId; }
    public String getEffectType() { return effectType; }
    public String getEffectId() { return effectId; }
    public double getValue() { return value; }
    public int getAmplifier() { return amplifier; }
    public String getDurationType() { return durationType; }
    public int getRemainingTicks() { return remainingTicks; }
    public int getPhaseTicksRemaining() { return phaseTicksRemaining; }
    public boolean isCurrentlyActive() { return state == State.ACTIVE && conditionsMet; }
    public String[] getCancelOnGroups() { return cancelOnGroups.clone(); }
    public int getDefinitionIndex() { return definitionIndex; }
    public void migrateDefinitionIndex(int index) { if (definitionIndex < 0) definitionIndex = index; }
    public String sourceId() { return "reward:" + groupId + "#" + definitionIndex; }
    public boolean isMobEffect() { return "mob_effect".equals(effectType); }
    public boolean isHealth() { return "health".equals(effectType); }
    public boolean isNutrientMultiplier() { return "nutrient_multiplier".equals(effectType); }
    public boolean isOverlay() { return "overlay".equals(effectType); }
    public boolean isFixed() { return "fixed".equals(durationType); }
    public boolean isUntilDeath() { return "until_death".equals(durationType); }
    public boolean isIntermittent() {
        return "until_death_intermittent".equals(durationType) || "history_intermittent".equals(durationType);
    }
    public int mobDuration() { return isFixed() ? remainingTicks : isIntermittent() ? phaseTicksRemaining : -1; }
     
    public double getAppliedMultiplier(IFoodTolerance ignored) { return appliedDurationMultiplier; }
    public boolean shouldCancelOnGroup(String activatedGroup) {
        String target = GroupIds.canonical(activatedGroup);
        for (String group : cancelOnGroups) {
            if (GroupIds.canonical(group, GroupIds.namespace(groupId)).equals(target)) return true;
        }
        return false;
    }
    private int activePhase() {
        if (intervalActiveMax == 0 && intervalPauseMax == 0) return -1;
        return Math.max(1, scaled(roll(intervalActiveMin, intervalActiveMax)));
    }
    private int scaled(int ticks) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.round(ticks * appliedDurationMultiplier)));
    }
    private static int roll(int min, int max) {
        return min >= max ? Math.max(1, min) : (int) ThreadLocalRandom.current().nextLong(Math.max(1, min), (long) max + 1);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("groupId", groupId); tag.putInt("definitionIndex", definitionIndex);
        tag.putString("operation",operation.name().toLowerCase(java.util.Locale.ROOT));tag.putBoolean("conditionsMet",conditionsMet);
        tag.putString("effectType", effectType); tag.putString("effectId", effectId);
        tag.putDouble("value", value); tag.putInt("amplifier", amplifier);
        tag.putString("durationType", durationType); tag.putString("state", state.name());
        tag.putInt("baseDuration", baseDuration); tag.putInt("remainingTicks", remainingTicks);
        tag.putDouble("appliedDurationMultiplier", appliedDurationMultiplier);
        tag.putLong("lastAdvancedGameTime", lastAdvancedGameTime);
        tag.putBoolean("activationClosed", activationClosed);
        tag.putBoolean("currentlyActive", isCurrentlyActive()); tag.putInt("phaseTicksRemaining", phaseTicksRemaining);
        tag.putInt("intervalActiveMin", intervalActiveMin); tag.putInt("intervalActiveMax", intervalActiveMax);
        tag.putInt("intervalPauseMin", intervalPauseMin); tag.putInt("intervalPauseMax", intervalPauseMax);
        tag.put("cancelGroups", com.lumbra.ldiediet.util.NBTUtils.writeStringList(List.of(cancelOnGroups), "id"));
        CompoundTag multipliers = new CompoundTag(); durationMultipliers.forEach(multipliers::putDouble);
        tag.put("durationMultipliers", multipliers);
        return tag;
    }

    public static ActiveGroupEffect fromNBT(CompoundTag tag) {
        String[] cancel = tag.contains("cancelGroups")
                ? com.lumbra.ldiediet.util.NBTUtils.readStringList(tag.getList("cancelGroups", 10), "id").toArray(String[]::new)
                : tag.getString("cancelOnGroups").isEmpty() ? new String[0] : tag.getString("cancelOnGroups").split(",");
        Map<String, Double> multipliers = new HashMap<>();
        CompoundTag saved = tag.getCompound("durationMultipliers");
        for (String key : saved.getAllKeys()) if (Double.isFinite(saved.getDouble(key))) multipliers.put(key, saved.getDouble(key));
        State state = tag.contains("state") ? State.valueOf(tag.getString("state"))
                : tag.getBoolean("currentlyActive") ? State.ACTIVE : State.PAUSED;
        ActiveGroupEffect effect = new ActiveGroupEffect(tag.getString("groupId"), tag.getString("effectType"),
                tag.getString("effectId"), tag.getDouble("value"), tag.getInt("amplifier"), tag.getString("durationType"),
                tag.contains("baseDuration") ? tag.getInt("baseDuration") : tag.getInt("remainingTicks"),
                tag.getInt("intervalActiveMin"), tag.getInt("intervalActiveMax"), tag.getInt("intervalPauseMin"),
                tag.getInt("intervalPauseMax"), cancel, multipliers, state);
        effect.operation = com.lumbra.ldiediet.data.NutritionScaleDefinition.parseOperation(tag.contains("operation")?tag.getString("operation"):"addition");
        effect.conditionsMet = !tag.contains("conditionsMet") || tag.getBoolean("conditionsMet");
        effect.definitionIndex = tag.contains("definitionIndex") ? tag.getInt("definitionIndex") : -1;
        effect.remainingTicks = Math.max(0, tag.getInt("remainingTicks"));
        effect.phaseTicksRemaining = tag.getInt("phaseTicksRemaining");
        double factor = tag.contains("appliedDurationMultiplier") ? tag.getDouble("appliedDurationMultiplier") : 1;
        effect.appliedDurationMultiplier = Double.isFinite(factor) && factor >= 0 ? factor : 1;
        effect.lastAdvancedGameTime = tag.contains("lastAdvancedGameTime") ? tag.getLong("lastAdvancedGameTime") : Long.MIN_VALUE;
        effect.activationClosed = tag.getBoolean("activationClosed");
        if (effect.isFixed() && effect.remainingTicks == 0 && effect.state != State.WAITING && !effect.isTerminal()) effect.expire();
        return effect;
    }
}
