package com.lumbra.ldiediet.rewards;

import com.lumbra.ldiediet.capability.CapabilityHandler;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

 
public final class MobEffectSources {
    private static final ThreadLocal<Integer> APPLYING = ThreadLocal.withInitial(() -> 0);
    private final Map<String, Owned> owned = new LinkedHashMap<>();
    private final Map<ResourceLocation, CompoundTag> external = new HashMap<>();
    private final Set<ResourceLocation> managed = new HashSet<>();
    private boolean legacyMigrationPending;
    private final Map<ResourceLocation,CompoundTag> origins=new HashMap<>();
    public net.minecraft.network.chat.Component externalOrigin(ResourceLocation id) {
        var tag=origins.get(id);if(tag!=null)try{return net.minecraft.network.chat.Component.Serializer.fromJson(tag.getString("label"));}catch(RuntimeException ignored){}
        return net.minecraft.network.chat.Component.translatable("ldiediet.journal.external");
    }
    private record Owned(ResourceLocation effect, CompoundTag data) {}

     
    public void migrateLegacy(Player player, List<com.lumbra.ldiediet.capability.ActiveGroupEffect> records) {
        if (!legacyMigrationPending) return;
        legacyMigrationPending = false;
        Set<MobEffect> legacy = new HashSet<>();
        for (var record : records) {
            if (record.getDefinitionIndex() >= 0 || !"mob_effect".equals(record.getEffectType())) continue;
            ResourceLocation id = ResourceLocation.tryParse(record.getEffectId());
            MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect != null && !managed.contains(id)) legacy.add(effect);
        }
        APPLYING.set(APPLYING.get() + 1);
        try {
            for (MobEffect effect : legacy) {
                MobEffectInstance current = player.getEffect(effect);
                if (current == null) continue;
                CompoundTag original = save(current), cleaned = stripLegacy(original);
                if (Objects.equals(original, cleaned)) continue;
                player.removeEffect(effect);
                MobEffectInstance remaining = load(cleaned);
                if (remaining != null) player.addEffect(remaining);
            }
        } finally {
            int depth = APPLYING.get() - 1;
            if (depth == 0) APPLYING.remove(); else APPLYING.set(depth);
        }
    }
    private static CompoundTag stripLegacy(CompoundTag source) {
        CompoundTag result = source.copy();
        CompoundTag hidden = source.contains("HiddenEffect") ? stripLegacy(source.getCompound("HiddenEffect")) : null;
        if (!source.getBoolean("Ambient") && !source.getBoolean("ShowParticles") && source.getBoolean("ShowIcon")) return hidden;
        if (hidden == null) result.remove("HiddenEffect"); else result.put("HiddenEffect", hidden);
        return result;
    }

    public void put(Player player, String source, MobEffectInstance effect) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());
        if (id == null || effect.getDuration() == 0) { remove(source); return; }
        if (managed.add(id)) {
            MobEffectInstance vanilla = player.getEffect(effect.getEffect());
            if (vanilla != null) external.put(id, save(vanilla));
        }
        owned.put(source, new Owned(id, save(effect)));
    }
    public void remove(String source) {
        owned.remove(source);
    }
    public void removePrefixExcept(String prefix, Set<String> keep) {
        for (String source : List.copyOf(owned.keySet())) if (source.startsWith(prefix) && !keep.contains(source)) remove(source);
    }
    public void advance() {
        origins.replaceAll((id,tag)->{var next=decrease(tag.getCompound("effect"));if(next==null)return null;tag.put("effect",next);return tag;});
        origins.values().removeIf(java.util.Objects::isNull);
        external.replaceAll((id, tag) -> decrease(tag));
        external.entrySet().removeIf(e -> e.getValue() == null);
        for (String source : List.copyOf(owned.keySet())) {
            Owned entry = owned.get(source);
            CompoundTag data = decrease(entry.data);
            if (data == null) remove(source); else owned.put(source, new Owned(entry.effect, data));
        }
    }
    public void reconcile(Player player) {
        for (ResourceLocation id : Set.copyOf(managed)) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect == null) { managed.remove(id); external.remove(id); continue; }
            MobEffectInstance desired = load(external.get(id));
            List<Owned> sources = owned.values().stream().filter(e -> e.effect.equals(id))
                    .sorted(Comparator.comparingInt(e -> e.data.getInt("FullAmplifier"))).toList();
            for (Owned source : sources) {
                MobEffectInstance value = load(source.data);
                if (value == null) continue;
                if (desired == null) desired = value; else desired.update(value);
            }
            MobEffectInstance current = player.getEffect(effect);
            if (!same(current, desired)) {
                APPLYING.set(APPLYING.get() + 1);
                try {
                    if (current != null && (desired == null || desired.getAmplifier() < current.getAmplifier()
                            || desired.getDuration() != -1 && (current.getDuration() == -1 || desired.getDuration() < current.getDuration()))) player.removeEffect(effect);
                    if (desired != null) player.addEffect(desired);
                } finally {
                    int depth = APPLYING.get() - 1;
                    if (depth == 0) APPLYING.remove(); else APPLYING.set(depth);
                }
            }
            if (sources.isEmpty()) { managed.remove(id); external.remove(id); }
        }
    }
    public static void captureExternal(LivingEntity entity, MobEffectInstance incoming) {
        if (entity.level().isClientSide || applying() || !(entity instanceof Player player)) return;
        var cap = CapabilityHandler.get(player);
        if (cap == null) return;
        MobEffectSources ledger = cap.getMobEffectSources();
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(incoming.getEffect());
        if(id==null)return;
        MobEffectInstance baseline=ledger.managed.contains(id)?load(ledger.external.get(id)):player.getEffect(incoming.getEffect());
        if(baseline==null||incoming.getAmplifier()>baseline.getAmplifier()||incoming.getAmplifier()==baseline.getAmplifier()&&(incoming.getDuration()==-1||baseline.getDuration()!=-1&&incoming.getDuration()>=baseline.getDuration())) {
            var context=com.lumbra.ldiediet.util.EatingContext.current();
            var label=context!=null&&context.player()==player?net.minecraft.network.chat.Component.translatable("ldiediet.journal.source_food",context.portion().stack().getHoverName()):net.minecraft.network.chat.Component.translatable("ldiediet.journal.external");
            var origin=new CompoundTag();origin.put("effect",save(incoming));origin.putString("label",net.minecraft.network.chat.Component.Serializer.toJson(label));ledger.origins.put(id,origin);
        }
        if(!ledger.managed.contains(id))return;
        MobEffectInstance previous = load(ledger.external.get(id));
        MobEffectInstance copy = load(save(incoming));
        if (previous == null) previous = copy; else previous.update(copy);
        ledger.external.put(id, save(previous));
    }
    public static void removedExternal(Player player, MobEffect effect) {
        if (applying() || player.level().isClientSide) return;
        var cap = CapabilityHandler.get(player);
        if (cap != null) {var id=ForgeRegistries.MOB_EFFECTS.getKey(effect);cap.getMobEffectSources().external.remove(id);cap.getMobEffectSources().origins.remove(id);}
    }
    private static boolean applying() { return APPLYING.get() > 0; }
    public static boolean isApplyingOwnedEffect() { return applying(); }
    private static boolean same(MobEffectInstance a, MobEffectInstance b) {
        if (a == null || b == null) return a == b;
        return a.getAmplifier() == b.getAmplifier() && a.getDuration() == b.getDuration()
                && a.isAmbient() == b.isAmbient() && a.isVisible() == b.isVisible() && a.showIcon() == b.showIcon();
    }
    private static CompoundTag save(MobEffectInstance value) {
        CompoundTag tag = value.save(new CompoundTag());
        tag.putInt("FullAmplifier", value.getAmplifier());
        return tag;
    }
    private static MobEffectInstance load(CompoundTag tag) {
        if (tag == null) return null;
        MobEffectInstance vanilla = MobEffectInstance.load(tag);
        if (vanilla == null) return null;
        int amplifier = tag.contains("FullAmplifier") ? tag.getInt("FullAmplifier") : vanilla.getAmplifier();
        if (amplifier == vanilla.getAmplifier()) return vanilla;
        MobEffectInstance result = new MobEffectInstance(vanilla.getEffect(), vanilla.getDuration(), amplifier,
                vanilla.isAmbient(), vanilla.isVisible(), vanilla.showIcon(),
                tag.contains("HiddenEffect") ? load(tag.getCompound("HiddenEffect")) : null, vanilla.getFactorData());
        result.setCurativeItems(vanilla.getCurativeItems());
        return result;
    }
    private static CompoundTag decrease(CompoundTag source) {
        CompoundTag tag = source.copy();
        if (tag.contains("HiddenEffect")) {
            CompoundTag hidden = decrease(tag.getCompound("HiddenEffect"));
            if (hidden == null) tag.remove("HiddenEffect"); else tag.put("HiddenEffect", hidden);
        }
        int ticks = tag.getInt("Duration");
        if (ticks == -1) return tag;
        if (ticks > 1) { tag.putInt("Duration", ticks - 1); return tag; }
        return tag.contains("HiddenEffect") ? tag.getCompound("HiddenEffect") : null;
    }
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag(); ListTag sources = new ListTag();
        root.putBoolean("legacy_migrated", !legacyMigrationPending);
        owned.forEach((id, value) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("source", id);
            entry.putString("effect", value.effect.toString()); entry.put("data", value.data.copy()); sources.add(entry);
        });
        root.put("owned", sources);
        CompoundTag baseline = new CompoundTag(); external.forEach((id, data) -> baseline.put(id.toString(), data.copy()));
        root.put("external", baseline);
        var originTags=new CompoundTag();origins.forEach((id,tag)->originTags.put(id.toString(),tag.copy()));root.put("origins",originTags);
        return root;
    }
    public void deserializeNBT(CompoundTag root) {
        owned.clear(); external.clear(); managed.clear();origins.clear();
        var savedOrigins=root.getCompound("origins");for(String key:savedOrigins.getAllKeys()){var id=ResourceLocation.tryParse(key);if(id!=null)origins.put(id,savedOrigins.getCompound(key).copy());}
        legacyMigrationPending = root.contains("legacy_migrated") ? !root.getBoolean("legacy_migrated")
                : !root.contains("owned") && !root.contains("external");
        ListTag sources = root.getList("owned", Tag.TAG_COMPOUND);
        for (int i = 0; i < sources.size(); i++) {
            CompoundTag entry = sources.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("effect"));
            if (id != null && load(entry.getCompound("data")) != null) {
                owned.put(entry.getString("source"), new Owned(id, entry.getCompound("data").copy())); managed.add(id);
            }
        }
        CompoundTag baseline = root.getCompound("external");
        for (String key : baseline.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && load(baseline.getCompound(key)) != null) { external.put(id, baseline.getCompound(key).copy()); managed.add(id); }
        }
    }
}
