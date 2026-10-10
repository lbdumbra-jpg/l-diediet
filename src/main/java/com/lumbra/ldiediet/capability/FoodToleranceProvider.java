package com.lumbra.ldiediet.capability;

import com.lumbra.ldiediet.LDieDiet;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public class FoodToleranceProvider implements ICapabilitySerializable<CompoundTag> {

    public static final ResourceLocation IDENTIFIER = new ResourceLocation(LDieDiet.MOD_ID, "food_tolerance");

    private final FoodToleranceCapability capability = new FoodToleranceCapability();
    private LazyOptional<IFoodTolerance> lazyOptional = LazyOptional.of(() -> capability);
    public void invalidate() {
        var previous = lazyOptional;
        lazyOptional = null;
        if (previous != null) previous.invalidate();
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap != CapabilityHandler.FOOD_TOLERANCE) return LazyOptional.empty();
         
         
        if (lazyOptional == null) lazyOptional = LazyOptional.of(() -> capability);
        return lazyOptional.cast();
    }

    @Override
    public CompoundTag serializeNBT() {
        return capability.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        capability.deserializeNBT(nbt);
    }
}
