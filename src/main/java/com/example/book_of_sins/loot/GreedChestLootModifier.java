package com.example.book_of_sins.loot;

import com.example.book_of_sins.sins.SinTome;
import com.example.book_of_sins.sins.SinTomes;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.loot.IGlobalLootModifier;

/**
 * 贪婪#3：开启战利品箱时战利品数量 -50%。
 */
public class GreedChestLootModifier implements IGlobalLootModifier {

    public static final GreedChestLootModifier INSTANCE = new GreedChestLootModifier();
    public static final Codec<GreedChestLootModifier> CODEC = Codec.unit(INSTANCE);

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Player player)) {
            return loot;
        }
        // 只作用于箱子类战利品表（玩家上下文的开箱/礼物等也走 Player，需按表 id 过滤）
        ResourceLocation tableId = context.getQueriedLootTableId();
        if (tableId == null || !tableId.getPath().startsWith("chests/")) {
            return loot;
        }
        if (!SinTomes.equipped(player).contains(SinTome.GREED)) {
            return loot;
        }
        ObjectArrayList<ItemStack> out = new ObjectArrayList<>(loot.size());
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            copy.setCount(copy.getCount() / 2);
            if (!copy.isEmpty()) {
                out.add(copy);
            }
        }
        return out;
    }
}
