package com.example.book_of_sins.registry;

import com.example.book_of_sins.BookOfSins;
import com.example.book_of_sins.charm.BosCharms;
import com.example.book_of_sins.sins.RootSeals;
import com.example.book_of_sins.sins.SinTome;
import com.example.book_of_sins.sins.SinTomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 星界专属创造模式物品栏（所有星界物品集中在一个页签，不再淹没在战斗页里）。
 */
public final class BosCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BookOfSins.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.book_of_sins"))
                    .icon(() -> new ItemStack(SinTomes.BOOKS.get(SinTome.PRIDE).get()))
                    .displayItems((parameters, output) -> {
                        // 按种类排序：开局道具 → 罪孽之书 → 根源之印 → 独特材料
                        output.accept(BosCharms.FALLEN_SKULL.get());
                        output.accept(BosCharms.ABYSSAL_TALON.get());
                        output.accept(BosCharms.DREADFLAME.get());
                        output.accept(BosCharms.FINAL_CANTO.get());
                        output.accept(BosCharms.DAY_CLAMOR.get());
                        output.accept(BosCharms.NIGHT_HUSH.get());
                        output.accept(SinTomes.ROOT_OF_SIN.get());
                        for (var book : SinTomes.BOOKS.values()) {
                            output.accept(book.get());
                        }
                        for (var reversal : com.example.book_of_sins.sins.ReversalTomes.BOOKS.values()) {
                            output.accept(reversal.get());
                        }
                        for (var seal : RootSeals.SEALS.values()) {
                            output.accept(seal.get());
                        }
                        output.accept(RootSeals.UNDEAD_REMAINS.get());
                        output.accept(RootSeals.ABYSSAL_MOLT.get());
                        output.accept(RootSeals.CINDERCLOUD_FLAME.get());
                        output.accept(RootSeals.EMERALD_HEART_CRYSTAL.get());
                        output.accept(RootSeals.VOID_HEART.get());
                    })
                    .build());

    private BosCreativeTabs() {
    }
}
