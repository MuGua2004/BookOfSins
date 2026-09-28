package com.example.book_of_sins.sins;

import com.example.ac_core.tooltip.TooltipParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 根源之印物品：神印槽饰品。
 *
 * <ul>
 *   <li>装备门槛：佩戴者必须已装备对应罪孽之书（{@link #canEquip}）；</li>
 *   <li>可随时取下（不覆写 canUnequip）；</li>
 *   <li>合成催化剂：作为配方材料参与合成时自动返还（crafting remaining item = 自身）；</li>
 *   <li>被动效果在 {@link RootSealEvents}（属性修正按秒对账，摘下自动清理）。</li>
 * </ul>
 */
public class RootSealItem extends com.example.book_of_sins.accessory.BosCurioItem {

    private final SinTome tome;

    public RootSealItem(Properties properties, ResourceLocation definitionId, SinTome tome) {
        super(properties, definitionId);
        this.tome = tome;
    }

    public SinTome tome() {
        return tome;
    }

    /**
     * 装备门槛：必须已佩戴对应罪孽之书——但不在 canEquip 里校验！
     *
     * <p>Curios 载入存档时会用 canEquip 复检槽位（isItemValid），而复检时机早于
     * 罪书槽恢复，扫描必然为空 → 印被误弹回背包。真正的校验由
     * {@link RootSealEvents} 的按秒对账负责：失去对应罪书后 1 秒内强制取下。</p>
     */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    // ---- 合成催化剂：参与合成后自动返还 ----

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return new ItemStack(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        Style base = Style.EMPTY.withColor(theme());
        // 基础属性加成
        tooltip.add(TooltipParser.parse(
                Component.translatable("item.book_of_sins." + definitionId().getPath() + ".1").getString(),
                base, true));
        // 技能效果：隔一行，前缀菱形标记
        tooltip.add(Component.empty());
        tooltip.add(TooltipParser.parse(
                "◆ " + Component.translatable("item.book_of_sins." + definitionId().getPath() + ".2").getString(),
                base, true));
        // 经文：隔一行，斜体（lang 里用 <i> 标签），不做数字高亮
        tooltip.add(Component.empty());
        tooltip.add(TooltipParser.parse(
                Component.translatable("item.book_of_sins." + definitionId().getPath() + ".3").getString(),
                base, false));
    }

    /** 与罪孽之书同源的主题色。 */
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
}
