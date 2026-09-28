package com.example.book_of_sins.charm;

import com.example.ac_core.attr.AcCoreAttributes;
import com.example.book_of_sins.BookOfSins;
import com.example.book_of_sins.accessory.BosCurioItem;
import com.example.book_of_sins.sins.RootSeals;
import com.example.book_of_sins.sins.SinTome;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.example.ac_core.tooltip.TooltipParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.UUID;

/**
 * 护符槽饰品（Curios 原生 charm 槽，默认 2 格）。
 */
public final class BosCharms {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BookOfSins.MOD_ID);

    public static final RegistryObject<Item> FALLEN_SKULL = ITEMS.register("fallen_skull",
            () -> new BosCharmItem(props(Rarity.UNCOMMON), "fallen_skull", null,
                    entry(mods(), Attributes.ATTACK_DAMAGE, UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010001"),
                            "fallen_skull", 10.0D, AttributeModifier.Operation.ADDITION)));

    public static final RegistryObject<Item> ABYSSAL_TALON = ITEMS.register("abyssal_talon",
            () -> new BosCharmItem(props(Rarity.UNCOMMON), "abyssal_talon", null,
                    entry(entry(mods(), Attributes.ATTACK_SPEED, UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010002"),
                            "abyssal_talon_attack_speed", 0.10D, AttributeModifier.Operation.MULTIPLY_BASE),
                            AcCoreAttributes.ARMOR_PIERCE.get(), UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010003"),
                            "abyssal_talon_armor_pierce", 5.0D, AttributeModifier.Operation.ADDITION)));

    public static final RegistryObject<Item> DREADFLAME = ITEMS.register("dreadflame",
            () -> new BosCharmItem(props(Rarity.RARE), "dreadflame", null, null));

    public static final RegistryObject<Item> FINAL_CANTO = ITEMS.register("final_canto",
            () -> new BosCharmItem(props(Rarity.EPIC), "final_canto", null,
                    entry(entry(entry(mods(), Attributes.MOVEMENT_SPEED, UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010004"),
                            "final_canto_move_speed", 0.33D, AttributeModifier.Operation.MULTIPLY_BASE),
                            Attributes.ARMOR, UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010005"),
                            "final_canto_armor", 3.0D, AttributeModifier.Operation.ADDITION),
                            Attributes.ARMOR_TOUGHNESS, UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010006"),
                            "final_canto_armor_toughness", 3.0D, AttributeModifier.Operation.ADDITION)));

    public static final RegistryObject<Item> DAY_CLAMOR = ITEMS.register("day_clamor",
            () -> new BosCharmItem(props(Rarity.EPIC), "day_clamor",
                    new ResourceLocation(BookOfSins.MOD_ID, "night_hush"),
                    entry(mods(), AcCoreAttributes.ARMOR_SHRED.get(), UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010007"),
                            "day_clamor_armor_shred", 0.50D, AttributeModifier.Operation.ADDITION)));

    public static final RegistryObject<Item> NIGHT_HUSH = ITEMS.register("night_hush",
            () -> new BosCharmItem(props(Rarity.EPIC), "night_hush",
                    new ResourceLocation(BookOfSins.MOD_ID, "day_clamor"),
                    entry(mods(), AcCoreAttributes.ARMOR_SHRED.get(), UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010008"),
                            "night_hush_armor_shred", 0.50D, AttributeModifier.Operation.ADDITION)));

    /** 护符物品：文案走 lang（.1/.2/... 序号键），支持成对互斥装备。 */
    public static class BosCharmItem extends BosCurioItem {

        private final ResourceLocation counterpartId;
        private final Multimap<Attribute, AttributeModifier> modifiers;
        /** 主题色与技能行起始序号（从该行起加菱形前缀）。 */
        private static final java.util.Map<String, ChatFormatting> THEMES = java.util.Map.of(
                "fallen_skull", ChatFormatting.GREEN, "abyssal_talon", ChatFormatting.DARK_AQUA,
                "dreadflame", ChatFormatting.RED, "final_canto", ChatFormatting.LIGHT_PURPLE,
                "day_clamor", ChatFormatting.YELLOW, "night_hush", ChatFormatting.BLUE);
        private static final java.util.Map<String, Integer> SKILL_FROM = java.util.Map.of(
                "fallen_skull", 2, "abyssal_talon", 3, "dreadflame", 2,
                "final_canto", 6, "day_clamor", 3, "night_hush", 3);

        public BosCharmItem(Properties properties, String definitionPath, ResourceLocation counterpartId,
                             Multimap<Attribute, AttributeModifier> modifiers) {
            super(properties, ResourceLocation.fromNamespaceAndPath(BookOfSins.MOD_ID, definitionPath));
            this.counterpartId = counterpartId;
            this.modifiers = modifiers;
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
            return modifiers == null ? HashMultimap.create() : modifiers;
        }

        /** 成对互斥：另一半已佩戴时不可装备。 */
        @Override
        public boolean canEquip(SlotContext slotContext, ItemStack stack) {
            if (counterpartId != null) {
                LivingEntity wearer = slotContext.getWearer();
                if (wearer != null && CharmPassives.isWearingId(wearer, counterpartId)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
                                    List<Component> tooltip, TooltipFlag flag) {
            String path = definitionId().getPath();
            Style base = Style.EMPTY.withColor(THEMES.getOrDefault(path, ChatFormatting.GRAY));
            int skillFrom = SKILL_FROM.getOrDefault(path, 99);
            boolean skillStarted = false;
            for (int i = 1; i <= 8; i++) {
                String key = "item.book_of_sins." + path + "." + i;
                String text = Component.translatable(key).getString();
                if (text.equals(key)) {
                    break;
                }
                if (i >= skillFrom) {
                    if (!skillStarted) {
                        tooltip.add(Component.empty());
                        skillStarted = true;
                    }
                    tooltip.add(TooltipParser.parse("◆ " + text, base, true));
                } else {
                    tooltip.add(TooltipParser.parse(text, base, true));
                }
            }
        }
    }

    private static Item.Properties props(Rarity rarity) {
        return new Item.Properties().stacksTo(1).rarity(rarity);
    }

    private static Multimap<Attribute, AttributeModifier> mods() {
        return HashMultimap.create();
    }

    private static Multimap<Attribute, AttributeModifier> entry(
            Multimap<Attribute, AttributeModifier> map, Attribute attribute,
            UUID uuid, String name, double amount, AttributeModifier.Operation operation) {
        map.put(attribute, new AttributeModifier(uuid, name, amount, operation));
        return map;
    }

    private BosCharms() {
    }
}