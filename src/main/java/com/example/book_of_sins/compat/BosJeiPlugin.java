package com.example.book_of_sins;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 集成：逆转之书的信息页。
 *
 * <p>在 JEI 中查看逆转之书时，额外显示一页「逆转条件」——
 * 逆转条件刻意不写在物品 tooltip 上（由 JEI 信息页承载）。</p>
 */
@JeiPlugin
public class BosJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(BookOfSins.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (var entry : com.example.book_of_sins.sins.ReversalTomes.BOOKS.entrySet()) {
            var tome = entry.getKey();
            ItemStack stack = new ItemStack(entry.getValue().get());

            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("item.book_of_sins.reversal_tome.cond",
                    Component.translatable("item.book_of_sins.reversal_tome_" + tome.id() + ".cond_text")));
            registration.addIngredientInfo(stack,
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, lines.toArray(new Component[0]));
        }
    }
}
