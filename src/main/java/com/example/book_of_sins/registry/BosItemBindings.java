package com.example.book_of_sins.registry;

import com.example.ac_core.chain.AccessoryDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 外部物品 → 饰品定义绑定：把别的模组的物品映射为星界饰品定义。
 *
 * <p>这些物品不实现 {@code IAccessory}，由佩戴提供方在收集阶段查此表。</p>
 *
 * <p>当前绑定表为空（外部物品饰品已随 v0.3 方向调整移除）；
 * 框架保留，供大型模组后续内容使用。</p>
 */
public final class BosItemBindings {

    private static final Map<ResourceLocation, ResourceLocation> BINDINGS = new HashMap<>();

    /** 查询物品绑定的定义；无绑定返回 null。 */
    @Nullable
    public static AccessoryDefinition definitionOf(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return null;
        }
        ResourceLocation defId = BINDINGS.get(itemId);
        return defId == null ? null : AccessoryDefinition.REGISTRY.get(defId);
    }

    private BosItemBindings() {
    }
}
