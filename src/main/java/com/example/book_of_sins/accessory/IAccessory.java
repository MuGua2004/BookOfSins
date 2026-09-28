package com.example.book_of_sins.accessory;

import com.example.ac_core.chain.AccessoryDefinition;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 饰品物品标记接口：物品 → 饰品定义（ac_core 数据包）的映射。
 *
 * <p>定义是数据驱动的（{@code data/<ns>/accessories/*.json}），
 * 物品只持有定义 id，查询时解析——这样改数据包不需要重启游戏，
 * 物品类也不必感知数据加载时序。</p>
 */
public interface IAccessory {

    /** 该饰品绑定的定义 id。 */
    ResourceLocation definitionId();

    /** 解析当前定义；数据包未加载/被删时返回 null（饰品失效但不崩溃）。 */
    @Nullable
    default AccessoryDefinition definition() {
        return AccessoryDefinition.REGISTRY.get(definitionId());
    }
}
