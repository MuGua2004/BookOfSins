package com.example.book_of_sins.loot;

import com.example.book_of_sins.BookOfSins;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 星界全局战利品修改器注册（Forge 1.20.1：注册表直接存 Codec）。
 */
public final class BosLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BookOfSins.MOD_ID);

    static {
        SERIALIZERS.register("greed_chest", () -> GreedChestLootModifier.CODEC);
        SERIALIZERS.register("greed_chest_plus", () -> ReversalChestBarterLootModifiers.GreedChestPlus.CODEC);
        SERIALIZERS.register("lust_barter", () -> ReversalChestBarterLootModifiers.LustBarter.CODEC);
    }

    private BosLootModifiers() {
    }
}
