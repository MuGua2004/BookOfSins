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
 * 罪孽之书物品：装备后【无法取下】（Curios canUnequip = false，绑定诅咒语义）。
 *
 * <p>Tooltip 使用 ac_core 的 TooltipParser 渲染：书名渐变、经文主题色 +
 * 数字金色高亮、行内微型图标（字体位图 U+E000 起）。</p>
 */
public class SinTomeItem extends com.example.book_of_sins.accessory.BosCurioItem {

    private final SinTome tome;

    public SinTomeItem(Properties properties, ResourceLocation definitionId, SinTome tome) {
        super(properties, definitionId);
        this.tome = tome;
    }

    public SinTome tome() {
        return tome;
    }

    /** 绑定诅咒语义：一旦装备无法取下（掉落/死亡除外）；创造模式例外，可自由取下。 */
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return slotContext.getWearer() instanceof net.minecraft.world.entity.player.Player player
                && player.isCreative();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        Style base = Style.EMPTY.withColor(theme().getColor());

        // 书名渐变（双色取自主题色 → 暗色）
        tooltip.add(TooltipParser.parse(
                "<g:#" + hex(theme().getColor()) + ":#3A0A0A>" + tome.display() + "</g>",
                Style.EMPTY, false));

        if (!shift) {
            // 经文视图（默认）
            String lore = Component.translatable("item.book_of_sins." + tome.itemId() + ".lore").getString();
            for (String line : lore.split("\n")) {
                tooltip.add(TooltipParser.parse(line, base, true));
            }
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("item.book_of_sins.sin_tome.binding").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.book_of_sins.sin_tome.unremovable").withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("item.book_of_sins.sin_tome.shift_hint",
                    Component.literal("Shift").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            // 诅咒视图（按住 Shift 切换）：只展示 7 条诅咒详情，
            // 元信息与「按住 Shift」提示都只在默认视图出现
            for (int i = 1; i <= 7; i++) {
                String line = "  " + Component.translatable("item.book_of_sins." + tome.itemId() + "." + i).getString();
                tooltip.add(TooltipParser.parse(line, base, true));
            }
        }
    }

    /** 每本罪孽之书的主题色（色相与贴图一致）。 */
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
