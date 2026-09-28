package com.example.book_of_sins.mixin;

import com.example.book_of_sins.sins.SinTome;
import com.example.book_of_sins.sins.SinTomes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 暴食#5：食用效果减半——在原版进食结算的写入源头（LivingEntity#eat）
 * 直接按一半营养/饱和度调用，饱食度条不再有先满后落的闪变；
 * 食物附带的药水效果（金苹果等）在 finishUsingItem 中另行结算，不受影响。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEatMixin {

    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private ItemStack sl$halfFood(Level level, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (level.isClientSide || !(self instanceof Player player)) {
            return stack;
        }
        if (!SinTomes.equipped(player).contains(SinTome.GLUTTONY)) {
            return stack;
        }
        FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null) {
            return stack;
        }
        // 营养减半（至少 1），饱和度系数同减半——FoodData 内部按系数×营养×2 计入，
        // 结果恰好是原值的一半
        cir.setReturnValue(stack);
        player.getFoodData().eat(Math.max(1, food.getNutrition() / 2),
                food.getSaturationModifier() / 2.0F);
        return stack;
    }
}
