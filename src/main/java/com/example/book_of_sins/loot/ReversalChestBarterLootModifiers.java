package com.example.book_of_sins.loot;

import com.example.ac_core.attr.AcCoreAttributes;
import com.example.book_of_sins.sins.ReversalTomes;
import com.example.book_of_sins.sins.SinTome;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.loot.IGlobalLootModifier;

import javax.annotation.Nullable;

/**
 * 逆转之书-贪婪 #3：开启战利品箱时战利品数量 +25%（对每组的数量按比例加成）。
 * 逆转之书-色欲 #4：猪灵以物易物时，产物翻倍（以最近的佩戴者为准，16 格内）。
 */
public final class ReversalChestBarterLootModifiers {

    private ReversalChestBarterLootModifiers() {
    }

    // ==================== 贪婪：箱子 +25% ====================

    public static class GreedChestPlus implements IGlobalLootModifier {

        public static final GreedChestPlus INSTANCE = new GreedChestPlus();
        public static final Codec<GreedChestPlus> CODEC = Codec.unit(INSTANCE);

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }

        @Override
        public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot, LootContext context) {
            if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Player player)
                    || !ReversalTomes.isEquipped(player, SinTome.GREED)) {
                return loot;
            }
            ResourceLocation tableId = context.getQueriedLootTableId();
            if (tableId == null || !tableId.getPath().startsWith("chests/")) {
                return loot;
            }
            for (ItemStack stack : loot) {
                int extra = stack.getCount() / 4;                       // 整数部分 +25%
                float rem = (stack.getCount() % 4) / 4.0F;              // 余数按概率进位
                if (extra > 0 || rem > 0) {
                    if (player.getRandom().nextFloat() < rem) {
                        extra++;
                    }
                    if (extra > 0) {
                        stack.setCount(stack.getCount() + extra);
                    }
                }
            }
            return loot;
        }
    }

    // ==================== 色欲：猪灵以物易物翻倍 ====================

    public static class LustBarter implements IGlobalLootModifier {

        public static final LustBarter INSTANCE = new LustBarter();
        public static final Codec<LustBarter> CODEC = Codec.unit(INSTANCE);

        private static final double SEARCH_RANGE = 16.0D;

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }

        @Override
        public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot, LootContext context) {
            // 以物易物表：THIS_ENTITY = 正在交易的猪灵；找 16 格内最近的佩戴者
            if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof LivingEntity trader)) {
                return loot;
            }
            ResourceLocation tableId = context.getQueriedLootTableId();
            if (tableId == null || !tableId.getPath().equals("gameplay/piglin_bartering")) {
                return loot;
            }
            if (nearestWearer(trader) == null) {
                return loot;
            }
            for (ItemStack stack : loot) {
                stack.setCount(stack.getCount() * 2);
            }
            return loot;
        }

        @Nullable
        private static Player nearestWearer(LivingEntity trader) {
            Player best = null;
            double bestDist = SEARCH_RANGE * SEARCH_RANGE;
            for (Player player : trader.level().players()) {
                if (!player.isSpectator() && ReversalTomes.isEquipped(player, SinTome.LUST)) {
                    double d = player.distanceToSqr(trader);
                    if (d < bestDist) {
                        bestDist = d;
                        best = player;
                    }
                }
            }
            return best;
        }
    }
}
