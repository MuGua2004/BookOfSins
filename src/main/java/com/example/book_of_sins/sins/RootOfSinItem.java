package com.example.book_of_sins.sins;

import com.example.book_of_sins.BookOfSins;
import com.example.book_of_sins.network.BosNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nonnull;

/**
 * 根源之罪：开局选择道具。
 *
 * <p>玩家首次进入世界时获得一本；右键打开开局选择界面（0~7 本罪孽之书，
 * 按顺序装配进典籍槽，已有饰品会被丢出）。不同开局配套的起始资源由
 * {@link SinStartBonuses} 钩子预留（方案待定）。</p>
 *
 * <p>道具在获得后 <b>10 现实分钟</b> 自动消散（现实时间戳，不随游戏暂停走）。</p>
 */
public final class RootOfSinItem extends Item {

    /** 有效期：10 现实分钟。 */
    public static final long DURATION_MS = 10L * 60L * 1000L;

    private static final String TAG_CREATED_AT = "sl_created_at";
    private static final String TAG_GIVEN = "sl_root_of_sin_given";

    public RootOfSinItem(Properties properties) {
        super(properties);
    }

    /** 生成一本带获得时间戳的根源之罪（首次发放用）。 */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(SinTomes.ROOT_OF_SIN.get());
        stack.getOrCreateTag().putLong(TAG_CREATED_AT, System.currentTimeMillis());
        return stack;
    }

    public static long createdAt(@Nonnull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(TAG_CREATED_AT);
    }

    public static boolean isExpired(@Nonnull ItemStack stack) {
        long created = createdAt(stack);
        return created > 0L && System.currentTimeMillis() - created > DURATION_MS;
    }

    /** 距离消散的剩余毫秒（未有时间戳时返回满额）。 */
    public static long remainingMs(@Nonnull ItemStack stack) {
        long created = createdAt(stack);
        if (created <= 0L) {
            return DURATION_MS;
        }
        return Math.max(0L, DURATION_MS - (System.currentTimeMillis() - created));
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player,
                                                  @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            long expireAt = createdAt(stack) + DURATION_MS;
            // 仅客户端加载选择界面类（服务端永不执行该 lambda 体）
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> RootOfSinScreen.open(expireAt));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull Entity entity,
                              int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        // 懒惰补时间戳（创造栏取出等途径获得的道具）
        if (createdAt(stack) == 0L) {
            stack.getOrCreateTag().putLong(TAG_CREATED_AT, System.currentTimeMillis());
            return;
        }
        if (isExpired(stack)) {
            stack.setCount(0);
        }
    }

    /**
     * 玩家首次进入世界时发放（每玩家一次；标记随死亡/跨维度保留）。
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(TAG_GIVEN)) {
            return;
        }
        persisted.putBoolean(TAG_GIVEN, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);

        ItemStack rootOfSin = create();
        if (!player.getInventory().add(rootOfSin)) {
            player.drop(rootOfSin, false);
        }
        BookOfSins.LOGGER.debug("[Star Legends] Root of Sin given to {}", player.getName().getString());
    }

    /**
     * 服务端：执行开局选择（由 {@code C2SSinStart} 包触发）。
     *
     * @param count 0~7，按 {@link SinTome} 枚举顺序装配前 N 本进典籍槽
     */
    public static void applyChoice(ServerPlayer player, int count) {
        if (count < 0 || count > SinTome.values().length) {
            return;
        }
        // 校验：必须真实持有未过期的根源之罪
        ItemStack rootOfSin = null;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() instanceof RootOfSinItem && !isExpired(stack)) {
                rootOfSin = stack;
                break;
            }
        }
        if (rootOfSin == null) {
            return;
        }

        // 装配：按顺序写入典籍槽，被占用的槽位把原饰品丢出去
        com.example.book_of_sins.curios.BosCurios.tomeStacks(player).ifPresent(stacks -> {
            SinTome[] order = SinTome.values();
            int n = Math.min(count, Math.min(stacks.getSlots(), order.length));
            for (int i = 0; i < n; i++) {
                ItemStack displaced = stacks.getStackInSlot(i);
                if (!displaced.isEmpty()) {
                    player.drop(displaced, false); // 典籍槽已有饰品 → 丢出去
                }
                stacks.setStackInSlot(i, new ItemStack(SinTomes.BOOKS.get(order[i]).get()));
            }
            // 对应神印：前 N 本书逐个发放对应根源之印（进背包，放不下掉落）
            for (int i = 0; i < n; i++) {
                ItemStack seal = new ItemStack(RootSeals.SEALS.get(order[i]).get());
                if (!player.getInventory().add(seal)) {
                    player.drop(seal, false);
                }
            }
        });

        // 不同开局的起始资源（方案预留，见 SinStartBonuses）
        SinStartBonuses.apply(player, count);

        rootOfSin.shrink(1);
    }
}
