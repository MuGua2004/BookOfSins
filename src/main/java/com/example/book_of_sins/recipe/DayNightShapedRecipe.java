package com.example.book_of_sins.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.Ingredient;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 昼夜合成表：配方形态与原版有序合成一致，结果随合成时的世界昼夜切换。
 *
 * <p>JSON：在原版 shaped 字段基础上，用 "day_result"（白天产物）与
 * "night_result"（夜晚产物）替代 "result"。</p>
 *
 * <p>昼夜以主世界时间为准（下界/末地无昼夜，统一按主世界判定）。</p>
 */
public class DayNightShapedRecipe extends ShapedRecipe {

    private final ItemStack dayResult;
    private final ItemStack nightResult;

    public DayNightShapedRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                                 int width, int height, NonNullList<Ingredient> ingredients,
                                 ItemStack dayResult, ItemStack nightResult) {
        super(id, group, category, width, height, ingredients, dayResult);
        this.dayResult = dayResult;
        this.nightResult = nightResult;
    }

    /** 白天合成 → day_result（黑夜寂寥）；夜晚合成 → night_result（白日喧嚣）。 */
    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        return isDay() ? dayResult.copy() : nightResult.copy();
    }

    /** 展示用产物（配方书/JEI 显示白日喧嚣）。 */
    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return nightResult.copy();
    }

    private static boolean isDay() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.overworld().isDay();
    }

    public static class Serializer extends ShapedRecipe.Serializer {

        @Override
        public ShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            ShapedRecipe base = super.fromJson(id, json);
            ItemStack day = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "day_result"));
            ItemStack night = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "night_result"));
            return new DayNightShapedRecipe(id, base.getGroup(), CraftingBookCategory.MISC,
                    base.getWidth(), base.getHeight(), base.getIngredients(), day, night);
        }

        @Override
        public ShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ShapedRecipe base = super.fromNetwork(id, buf);
            ItemStack day = buf.readItem();
            ItemStack night = buf.readItem();
            return new DayNightShapedRecipe(id, base.getGroup(), CraftingBookCategory.MISC,
                    base.getWidth(), base.getHeight(), base.getIngredients(), day, night);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ShapedRecipe recipe) {
            super.toNetwork(buf, recipe);
            if (recipe instanceof DayNightShapedRecipe dayNight) {
                buf.writeItem(dayNight.dayResult);
                buf.writeItem(dayNight.nightResult);
            }
        }
    }
}