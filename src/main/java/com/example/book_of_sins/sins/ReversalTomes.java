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

/**
 * 逆转之书注册：目前 3 本（傲慢 / 嫉妒 / 暴怒），其余四本待后续开放。
 *
 * <p>逆转之书不占用 {@link SinTomes} 的注册与语义：佩戴逆转之书不会提供
 * 罪孽资源，也不会满足根源之印的装备门槛（逆转 = 与罪孽彻底了断）。</p>
 */
public final class ReversalTomes {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BookOfSins.MOD_ID);

    public static final Map<SinTome, RegistryObject<Item>> BOOKS = new EnumMap<>(SinTome.class);

    static {
        for (SinTome tome : EnumSet.of(SinTome.PRIDE, SinTome.ENVY, SinTome.WRATH,
                SinTome.SLOTH, SinTome.GREED, SinTome.GLUTTONY, SinTome.LUST)) {
            String id = tomeId(tome);
            BOOKS.put(tome, ITEMS.register(id, () -> new ReversalTomeItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    new ResourceLocation(BookOfSins.MOD_ID, id), tome)));
        }
    }

    /** 物品注册名后缀（reversal_tome_pride …）。 */
    public static String tomeId(SinTome tome) {
        return "reversal_tome_" + tome.id();
    }

    /** 该罪的逆转之书是否已开放。 */
    public static boolean isAvailable(SinTome tome) {
        return BOOKS.containsKey(tome);
    }

    /** 玩家是否佩戴着对应罪的逆转之书。 */
    public static boolean isEquipped(Player player, SinTome tome) {
        return CuriosApi.getCuriosInventory(player).map(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.getItem() instanceof ReversalTomeItem item && item.tome() == tome) {
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }

    /** 玩家当前佩戴的逆转之书总数（任意罪，供「每本 +3% 最终伤害」结算）。 */
    public static int equippedCount(Player player) {
        int[] count = {0};
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    if (stacks.getStackInSlot(i).getItem() instanceof ReversalTomeItem) {
                        count[0]++;
                    }
                }
            }
        });
        return count[0];
    }

    private ReversalTomes() {
    }
}
