package com.example.book_of_sins.curios;

import com.example.ac_core.chain.AccessoryDefinition;
import com.example.ac_core.chain.LoadoutGraph;
import com.example.book_of_sins.BookOfSins;
import com.example.book_of_sins.accessory.IAccessory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Curios 集成（星界侧）：把 Curios 饰品栏接入 AC Core 的佩戴组合图。
 *
 * <p>Curios 在 mods.toml 中为强制前置，运行期必然存在；isLoaded 守卫
 * 只用于开发环境的优雅降级。</p>
 *
 * <p>锚点（anchor）：Curios 槽位组 id（ring / charm / …），映射为
 * {@code curios:<identifier>}——后续位置系统（左环/右环等）在此基础上扩展。</p>
 */
public final class BosCurios {

    public static boolean isLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("curios");
    }

    /** 注册佩戴提供方（仅 curios 在场时调用）。 */
    public static void registerProvider() {
        LoadoutGraph.register(BosCurios::collect);
        BookOfSins.LOGGER.info("[Star Legends] Curios accessory provider registered");
    }

    /** 取玩家「典籍」槽位的可写物品处理器（开局装配 / 后续系统用）。 */
    public static java.util.Optional<net.minecraftforge.items.IItemHandlerModifiable> tomeStacks(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.getCurios().get("tome"))
                .map(top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler::getStacks);
    }

    /** 遍历玩家全部槽位组（供其他系统扫描装备）。 */
    public static java.lang.Iterable<ICurioStacksHandler> slotHandlers(Player player) {
        var out = new java.util.ArrayList<ICurioStacksHandler>();
        CuriosApi.getCuriosInventory(player).ifPresent(h -> out.addAll(h.getCurios().values()));
        return out;
    }

    private static void collect(Player player, Consumer<LoadoutGraph.Equipped> out) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            Map<String, ICurioStacksHandler> curios = handler.getCurios();
            for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
                IItemHandlerModifiable stacks = entry.getValue().getStacks();
                ResourceLocation anchor = new ResourceLocation("curios", entry.getKey());
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    AccessoryDefinition def = null;
                    if (stack.getItem() instanceof IAccessory accessory) {
                        def = accessory.definition();
                    } else {
                        def = com.example.book_of_sins.registry.BosItemBindings.definitionOf(stack);
                    }
                    if (def == null || !def.isAvailable()) {
                        continue; // 定义缺失或依赖未满足（§12.3）：饰品失效但不崩溃
                    }
                    out.accept(new LoadoutGraph.Equipped(def, anchor));
                }
            }
        });
    }

    private BosCurios() {
    }
}
