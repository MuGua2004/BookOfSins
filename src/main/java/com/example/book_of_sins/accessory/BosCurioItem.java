package com.example.book_of_sins.accessory;

import com.example.ac_core.chain.AccessoryDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 星界饰品物品基类：Curios 可装备（ICurioItem 接口）+ 定义映射（IAccessory）。
 *
 * <p>子类只补外观与 tooltip；效果全部来自数据包定义，不写在代码里。</p>
 */
public class BosCurioItem extends Item implements ICurioItem, IAccessory {

    private final ResourceLocation definitionId;

    public BosCurioItem(Properties properties, ResourceLocation definitionId) {
        super(properties);
        this.definitionId = definitionId;
    }

    @Override
    public ResourceLocation definitionId() {
        return definitionId;
    }

    @Nullable
    @Override
    public AccessoryDefinition definition() {
        return IAccessory.super.definition();
    }

    /** 开发期诊断 tooltip：显示绑定的定义 id 与数据包加载状态。 */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.book_of_sins.bound_definition",
                definitionId.toString()).withStyle(ChatFormatting.DARK_GRAY));
        if (definition() == null) {
            tooltip.add(Component.translatable("item.book_of_sins.definition_missing")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
