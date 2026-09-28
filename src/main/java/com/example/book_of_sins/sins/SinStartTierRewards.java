package com.example.book_of_sins.sins;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * 开局档位奖励（内置方案）。
 *
 * <p>选择背负 N 本罪孽之书时，依次发放第 1~N 档的全部奖励
 * （高档包含之前所有档位的奖励）。第 0 档（不带书开局）无奖励。</p>
 *
 * <p>发放为静默入包（放不下掉落在脚边），无额外提示文本。</p>
 */
public final class SinStartTierRewards {

    /** 注册进开局资源钩子（在模组构造阶段调用一次）。 */
    public static void register() {
        SinStartBonuses.register(SinStartTierRewards::apply);
    }

    private static void apply(ServerPlayer player, int tomeCount) {
        for (int tier = 1; tier <= Math.min(tomeCount, 7); tier++) {
            giveTier(player, tier);
        }
    }

    private static void giveTier(ServerPlayer player, int tier) {
        switch (tier) {
            case 1 -> {
                give(player, Items.IRON_INGOT, 32);
                give(player, Items.OAK_LOG, 64);
                giveBook(player, Enchantments.MENDING, 1, 1);
            }
            case 2 -> {
                rollDesertPyramid(player, 5);
                give(player, Items.IRON_INGOT, 64);
                give(player, Items.GOLD_INGOT, 32);
                giveBook(player, Enchantments.MENDING, 1, 1);
            }
            case 3 -> {
                giveBook(player, Enchantments.ALL_DAMAGE_PROTECTION, 3, 4);
                givePotion(player);
                give(player, Items.GOLDEN_APPLE, 8);
            }
            case 4 -> {
                give(player, Items.ENCHANTED_GOLDEN_APPLE, 4);
                give(player, Items.DIAMOND, 8);
                giveBook(player, Enchantments.SHARPNESS, 3, 1);
                giveBook(player, Enchantments.POWER_ARROWS, 3, 1);
            }
            case 5 -> {
                give(player, Items.DIAMOND, 32);
                give(player, Items.IRON_INGOT, 64);
                give(player, Items.GOLD_INGOT, 64);
            }
            case 6 -> {
                giveBook(player, Enchantments.MENDING, 1, 2);
                give(player, Items.NETHERITE_INGOT, 1);
            }
            case 7 -> {
                give(player, Items.ELYTRA, 1);
                give(player, Items.FIREWORK_ROCKET, 64);
                give(player, Items.IRON_BLOCK, 32);
                give(player, Items.GOLD_BLOCK, 32);
                give(player, Items.DIAMOND_BLOCK, 32);
                give(player, Items.NETHERITE_INGOT, 32);
            }
            default -> {
            }
        }
    }

    /** 沙漠神殿战利品表抽取 rolls 次，产物全部入包。 */
    private static void rollDesertPyramid(ServerPlayer player, int rolls) {
        MinecraftServer server = player.server;
        LootTable table = server.getLootData().getLootTable(BuiltInLootTables.DESERT_PYRAMID);
        LootParams params = new LootParams.Builder((ServerLevel) player.level())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.CHEST);
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                giveStack(player, stack);
            }
        }
    }

    private static void give(ServerPlayer player, Item item, int count) {
        giveStack(player, new ItemStack(item, count));
    }

    private static void giveBook(ServerPlayer player, Enchantment enchantment, int level, int count) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK, count);
        EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(enchantment, level));
        giveStack(player, stack);
    }

    private static void givePotion(ServerPlayer player) {
        giveStack(player, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.REGENERATION));
    }

    private static void giveStack(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private SinStartTierRewards() {
    }
}
