package com.lumbra.ldiediet.mixin;

import com.lumbra.ldiediet.consumption.*;
import com.lumbra.ldiediet.api.DietEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CakeBlock.class)
public abstract class MixinCakeBlock {
    @Unique private static final ThreadLocal<ConsumptionSnapshot> ldiediet$cake=new ThreadLocal<>();
    @Inject(method="eat",at=@At("HEAD"),cancellable=true)
    private static void ldiediet$beforeCake(LevelAccessor level,BlockPos pos,BlockState state,Player player,CallbackInfoReturnable<InteractionResult> ci) {
        ldiediet$cake.remove();if(player.level().isClientSide||!player.canEat(false))return;
        var portion=ConsumptionSnapshot.capture(player,new ItemStack(Items.CAKE),new FoodProperties.Builder().nutrition(2).saturationMod(.1f).build());
        var pre=new DietEvents.MealPre(player,portion,"cake");
        if(DietEvents.post(pre)){ci.setReturnValue(InteractionResult.PASS);return;}ldiediet$cake.set(pre.getPortion());
    }
    @Redirect(method="eat",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;eat(IF)V"))
    private static void ldiediet$consumeSlice(FoodData data,int hunger,float saturationModifier,LevelAccessor level,BlockPos position,BlockState state,Player player) {
        try{FoodConsumption.cake(player,data,hunger,saturationModifier,ldiediet$cake.get());}finally{ldiediet$cake.remove();}
    }
    @Inject(method="eat",at=@At("RETURN"))
    private static void ldiediet$clearCake(LevelAccessor level,BlockPos pos,BlockState state,Player player,CallbackInfoReturnable<InteractionResult> ci){ldiediet$cake.remove();}
}
