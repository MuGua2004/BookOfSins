package com.example.book_of_sins.recipe;

import com.example.book_of_sins.BookOfSins;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 星界合成表序列化器。 */
public final class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, BookOfSins.MOD_ID);

    public static final RegistryObject<RecipeSerializer<?>> DAY_NIGHT =
            SERIALIZERS.register("day_night", DayNightShapedRecipe.Serializer::new);

    private ModRecipeSerializers() {
    }
}