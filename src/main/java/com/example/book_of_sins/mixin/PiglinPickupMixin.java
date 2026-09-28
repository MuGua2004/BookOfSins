package com.example.book_of_sins.mixin;

import com.example.book_of_sins.sins.SinCurseEvents;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 色欲4 补充：猪灵拒绝捡起色欲佩戴者丢出的金锭（捡起金锭会触发以物易物，
 * 绕过右键拦截）。命中记账（{@link SinCurseEvents} 的 TOSSED_GOLD）即取消捡拾。
 */
@Mixin(Piglin.class)
public abstract class PiglinPickupMixin {

    @Inject(method = "pickUpItem(Lnet/minecraft/world/entity/item/ItemEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private void sl$blockGoldPickup(ItemEntity itemEntity, CallbackInfo ci) {
        if (itemEntity.level().isClientSide) {
            return;
        }
        if (itemEntity.getItem().is(Items.GOLD_INGOT)
                && SinCurseEvents.isLustTossedGold(itemEntity.getUUID())) {
            ci.cancel();
        }
    }
}
