package com.lumbra.ldiediet.compat;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

 
public final class DietMixinPlugin implements IMixinConfigPlugin {
    public static final String SUPPORTED_VERSION = "2.1.1+1.20.1";
    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        var file = LoadingModList.get().getModFileById("diet");
        present = file != null;
        if (!present) return;
        String version = file.getMods().stream().filter(mod -> mod.getModId().equals("diet"))
                .findFirst().orElseThrow().getVersion().toString();
        validateVersion(version);
    }

    public static void validateVersion(String version) {
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new IllegalStateException("L'DieDiet cannot safely disable Diet " + version
                    + ". Supported Diet version: " + SUPPORTED_VERSION
                    + ". Use the supported version or remove Diet to avoid conflicting gameplay.");
        }
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return present; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
