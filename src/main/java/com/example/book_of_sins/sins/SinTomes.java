package com.example.book_of_sins.sins;

import com.example.book_of_sins.BookOfSins;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 七宗罪典籍注册：7 本罪孽之书（典籍槽）。
 */
public final class SinTomes {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BookOfSins.MOD_ID);

    public static final Map<SinTome, RegistryObject<Item>> BOOKS = new EnumMap<>(SinTome.class);

    /** 根源之罪：开局选择道具（首次进世界发放，10 现实分钟后消散）。 */
    public static final RegistryObject<Item> ROOT_OF_SIN = ITEMS.register("root_of_sin",
            () -> new RootOfSinItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    static {
        for (SinTome tome : SinTome.values()) {
            String id = tome.itemId();
            BOOKS.put(tome, ITEMS.register(id, () ->
                    new SinTomeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                            definitionId(tome), tome)));
        }
    }

    /** 绑定的饰品定义（book_of_sins 数据包，效果为空，诅咒在代码侧）。 */
    public static ResourceLocation definitionId(SinTome tome) {
        return new ResourceLocation(BookOfSins.MOD_ID, tome.itemId());
    }

    /** 查询玩家当前装备了哪些罪孽之书（实时扫描 Curios 槽位）。 */
    public static Set<SinTome> equipped(Player player) {
        Set<SinTome> out = EnumSet.noneOf(SinTome.class);
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.getItem() instanceof SinTomeItem item) {
                        out.add(item.tome());
                    }
                }
            }
        });
        return out;
    }

    private SinTomes() {
    }
}
