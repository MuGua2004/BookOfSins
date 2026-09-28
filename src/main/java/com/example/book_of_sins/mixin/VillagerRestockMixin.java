package com.example.book_of_sins.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 贪婪#4：被贪婪印/书标记（价格翻倍）的村民永远不再补货——
 * 拦截原版补货方法 {@code Villager#updateTrades}。
 */
@Mixin(Villager.class)
public abstract class VillagerRestockMixin {

    @Inject(method = "updateTrades()V", at = @At("HEAD"), cancellable = true)
    private void sl$blockRestock(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (!self.level().isClientSide
                && self.getPersistentData().getBoolean("book_of_sins_greed")) {
            ci.cancel();
        }
    }
}
