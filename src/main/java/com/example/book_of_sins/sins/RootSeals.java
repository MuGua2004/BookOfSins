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
import java.util.Map;

/**
 * 七枚「根源之印」：与七罪一一对应的神印槽饰品。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>只在神印（god_seal）槽位装备（由 {@code curios:god_seal} 物品 tag 决定）；</li>
 *   <li>装备条件：佩戴者已装备对应的罪孽之书（{@link #canEquip} 侧校验）；</li>
 *   <li>可随时取下；</li>
 *   <li>作为合成催化剂使用时不被消耗（crafting remaining item = 自身）；</li>
 *   <li>被动效果实现在 {@link RootSealEvents}。</li>
 * </ul>
 */
public final class RootSeals {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BookOfSins.MOD_ID);

    public static final Map<SinTome, RegistryObject<Item>> SEALS = new EnumMap<>(SinTome.class);

    /** 亡灵之骸：色欲印独特掉落——击杀亡灵生物。 */
    public static final RegistryObject<Item> UNDEAD_REMAINS = ITEMS.register("undead_remains",
            () -> new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    /** 渊海遗蜕：击杀水生生物。 */
    public static final RegistryObject<Item> ABYSSAL_MOLT = ITEMS.register("abyssal_molt",
            () -> new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    /** 灼云之焰：击杀下界生物。 */
    public static final RegistryObject<Item> CINDERCLOUD_FLAME = ITEMS.register("cindercloud_flame",
            () -> new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    /** 翡翠心晶：击杀村民。 */
    public static final RegistryObject<Item> EMERALD_HEART_CRYSTAL = ITEMS.register("emerald_heart_crystal",
            () -> new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    /** 虚空之心：击杀末地生物。 */
    public static final RegistryObject<Item> VOID_HEART = ITEMS.register("void_heart",
            () -> new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    static {
        for (SinTome tome : SinTome.values()) {
            String id = sealId(tome);
            SEALS.put(tome, ITEMS.register(id, () -> new RootSealItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    new ResourceLocation(BookOfSins.MOD_ID, id), tome)));
        }
    }

    /** 物品注册名后缀（root_seal_pride …）。 */
    public static String sealId(SinTome tome) {
        return "root_seal_" + tome.id();
    }

    /** 查询玩家是否佩戴着对应罪的根源之印（实时扫描 Curios 槽位）。 */
    public static boolean isWearing(Player player, SinTome tome) {
        return CuriosApi.getCuriosInventory(player).map(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.getItem() instanceof RootSealItem seal && seal.tome() == tome) {
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }

    /**
     * 摘除玩家佩戴的对应根源之印并弹回背包（放不下则掉落）。
     * 用于「失去对应罪孽之书时强制弹出」。
     *
     * @return 是否确实摘除了一枚印
     */
    public static boolean popOut(Player player, SinTome tome) {
        boolean[] removed = {false};
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.getItem() instanceof RootSealItem seal && seal.tome() == tome) {
                        stacks.setStackInSlot(i, ItemStack.EMPTY);
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                        removed[0] = true;
                        return;
                    }
                }
            }
        });
        return removed[0];
    }

    private RootSeals() {
    }
}
