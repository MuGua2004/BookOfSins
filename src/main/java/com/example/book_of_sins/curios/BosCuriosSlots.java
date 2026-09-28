package com.example.book_of_sins.curios;

import com.example.book_of_sins.BookOfSins;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import top.theillusivec4.curios.api.SlotTypeMessage;

import java.util.List;

/**
 * 罪孽之书槽位注册（通过 Curios 的 IMC 机制）。
 *
 * <p>共 2 类 14 槽：7 典籍 / 7 神印。槽位 id 沿用星界的 tome / god_seal
 * （本 mod 与星界不会同时安装，不会冲突）。</p>
 *
 * <p>槽位显示名走 lang 键 {@code curios.identifier.<id>}；
 * 每类槽位对应的物品 tag：{@code data/curios/tags/items/<id>.json}。</p>
 */
public final class BosCuriosSlots {

    /** 槽位规格：id → 数量。 */
    private static final List<SlotSpec> SLOTS = List.of(
            new SlotSpec("tome", 7),            // 典籍
            new SlotSpec("god_seal", 7)         // 神印
    );

    /** Mod 总线事件：FML 收集 IMC 阶段把槽位类型发给 Curios。 */
    public static void onEnqueueIMC(InterModEnqueueEvent event) {
        for (SlotSpec spec : SLOTS) {
            ResourceLocation icon = new ResourceLocation(BookOfSins.MOD_ID,
                    "slot/empty_" + spec.id() + "_slot");
            InterModComms.sendTo("curios", SlotTypeMessage.REGISTER_TYPE,
                    () -> new SlotTypeMessage.Builder(spec.id())
                            .size(spec.size())
                            .icon(icon)
                            .build());
        }
        BookOfSins.LOGGER.info("[Book of Sins] Registered {} Curios slot types ({} slots total)",
                SLOTS.size(), SLOTS.stream().mapToInt(SlotSpec::size).sum());
    }

    /** 槽位规格记录。 */
    private record SlotSpec(String id, int size) {
    }

    private BosCuriosSlots() {
    }
}