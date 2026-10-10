package com.lumbra.ldiediet.consumption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class ChewingTiming {
     
     
    private static final Map<LivingEntity,ItemStack> STACKS=new com.google.common.collect.MapMaker().weakKeys().makeMap();
    public static void remember(LivingEntity entity,ItemStack stack) { STACKS.put(entity,stack); }
    public static void clear(LivingEntity entity) { var stack=STACKS.remove(entity);if(stack!=null)((UseDurationOverride)(Object)stack).ldiediet$setDuration(0); }
    public static boolean isSlowed(LivingEntity entity,ItemStack stack) {
        var cap=com.lumbra.ldiediet.capability.CapabilityHandler.get(entity);
        return cap!=null&&cap.getUseSession()!=null&&!cap.getUseSession().excluded()&&cap.getUseSession().chewingMultiplier()>1;
    }
    public static boolean shouldPulse(LivingEntity entity,ItemStack stack) {
        var cap=com.lumbra.ldiediet.capability.CapabilityHandler.get(entity);
        int elapsed=stack.getUseDuration()-entity.getUseItemRemainingTicks();
        int interval=Math.max(4,(int)Math.round(4*cap.getUseSession().chewingMultiplier()));
        return elapsed>=7&&(elapsed-7)%interval==0;
    }
}
