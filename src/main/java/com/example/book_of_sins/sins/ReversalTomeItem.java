package com.example.book_of_sins.sins;

import com.example.ac_core.tooltip.TooltipParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 逆转之书物品：对应罪孽之书满足逆转条件后原地转化而来。
 *
 * <p>与罪孽之书相反：</p>
 * <ul>
 *   <li>装备后<b>可以随时取下</b>（不覆写 canUnequip）；</li>
 *   <li>逆转完成后，对应的<b>根源之印永久无法装配</b>——印的装备门槛
 *       （{@link RootSealItem#canEquip}）校验的是罪孽之书在身，
 *       罪书已被转化，校验天然失败；已佩戴的印也会被
 *       {@link RootSealEvents} 的周期校验自动弹回。</li>
 * </ul>
 *
 * <p>Tooltip 与罪孽之书同构：默认视图为七美德经文 + 逆转条件，
 * Shift 视图为「命运扭转」7 条效果。</p>
 */
public class ReversalTomeItem extends com.example.book_of_sins.accessory.BosCurioItem {

    private final SinTome tome;

    public ReversalTomeItem(Properties properties, ResourceLocation definitionId, SinTome tome) {
        super(properties, definitionId);
        this.tome = tome;
    }

    public SinTome tome() {
        return tome;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        Style base = Style.EMPTY.withColor(theme());

        // 书名渐变（主题色 → 暗色）
        String title = Component.translatable(
                "item.book_of_sins.reversal_tome.header", tome.display()).getString();
        tooltip.add(TooltipParser.parse(
                "<g:#" + hex(theme().getColor()) + ":#0A0A2A>" + title + "</g>",
                Style.EMPTY, false));

        if (!shift) {
            // 经文视图（默认）：七美德经文
            String lore = Component.translatable("item.book_of_sins." + definitionId().getPath() + ".lore").getString();
            for (String line : lore.split("\n")) {
                tooltip.add(TooltipParser.parse(line, base, true));
            }
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("item.book_of_sins.reversal_tome.shift_hint",
                    Component.literal("Shift").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            // 命运扭转视图：7 条效果（顶格排版——带行首空格的行在 JEI/换行场景
            // 会被重排丢失缩进，导致参差不齐）
            tooltip.add(Component.translatable("item.book_of_sins.reversal_tome.fate")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (int i = 1; i <= 7; i++) {
                String line = Component.translatable(
                        "item.book_of_sins." + definitionId().getPath() + "." + i).getString();
                tooltip.add(TooltipParser.parse(line, base, true));
            }
        }
    }

    /** 每本逆转之书的主题色（与贴图一致）。 */
    private ChatFormatting theme() {
        return switch (tome) {
            case PRIDE -> ChatFormatting.LIGHT_PURPLE;
            case ENVY -> ChatFormatting.GREEN;
            case WRATH -> ChatFormatting.RED;
            case SLOTH -> ChatFormatting.AQUA;
            case GREED -> ChatFormatting.YELLOW;
            case GLUTTONY -> ChatFormatting.GOLD;
            case LUST -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static String hex(Integer rgb) {
        return String.format("%06X", rgb == null ? 0xFFFFFF : rgb);
    }
}
