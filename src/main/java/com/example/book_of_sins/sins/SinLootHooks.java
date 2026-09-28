package com.example.book_of_sins.sins;

import com.example.ac_core.event.AcBrushingEvent;
import com.example.ac_core.event.AcEnchantCostEvent;
import com.example.ac_core.event.AcItemDurabilityEvent;
import com.example.ac_core.event.AcLootEnchantLevelEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 七宗罪 × ac_core mixin 挂点：
 * <ul>
 *   <li>嫉妒#4 耐久消耗翻倍（AcItemDurabilityEvent）；</li>
 *   <li>嫉妒#3 末影箱每个空格附魔等级-1（AcEnchantCostEvent）；</li>
 *   <li>傲慢#5 考古 50% 出怪 / 25% 损坏可疑方块 / 25% 正常（AcBrushingEvent）；</li>
 *   <li>贪婪#1/#5 时运、抢夺 -5，钳到 0（AcLootEnchantLevelEvent）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = com.example.book_of_sins.BookOfSins.MOD_ID)
public final class SinLootHooks {

    @SubscribeEvent
    public static void onDurability(AcItemDurabilityEvent event) {
        if (SinTomes.equipped(event.getPlayer()).contains(SinTome.ENVY)) {
            event.setAmount(event.getAmount() * 2);
        }
    }

    @SubscribeEvent
    public static void onEnchantCost(AcEnchantCostEvent event) {
        Player player = event.getPlayer();
        if (!SinTomes.equipped(player).contains(SinTome.ENVY)) {
            return;
        }
        var ender = player.getEnderChestInventory();
        int empty = 0;
        for (int i = 0; i < ender.getContainerSize(); i++) {
            if (ender.getItem(i).isEmpty()) {
                empty++;
            }
        }
        event.setCost(event.getCost() - empty);
    }

    @SubscribeEvent
    public static void onBrushing(AcBrushingEvent event) {
        Player player = event.getPlayer();
        if (!SinTomes.equipped(player).contains(SinTome.PRIDE)) {
            return;
        }
        float roll = player.getRandom().nextFloat();
        if (roll < 0.50F) {
            // 50%：产出怪物（随机常见敌对生物）
            event.setCanceled(true);
            List<EntityType<? extends Monster>> pool = List.of(
                    EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER);
            var type = pool.get(player.getRandom().nextInt(pool.size()));
            var mob = type.create(event.getLevel());
            if (mob != null) {
                mob.moveTo(event.getPos().getX() + 0.5D, event.getPos().getY(),
                        event.getPos().getZ() + 0.5D, 0.0F, 0.0F);
                event.getLevel().addFreshEntity(mob);
            }
            // 可疑方块照常转化（否则 brushCount 已满，下 tick 会再次触发本事件刷怪）
            ReversalEvents.convertBrushable(event.getLevel(), event.getPos());
        } else if (roll < 0.75F) {
            // 25%：损坏可疑方块（无掉落移除）
            event.setCanceled(true);
            event.getLevel().removeBlock(event.getPos(), false);
        }
        // 其余 25%：原版正常掉落
    }

    @SubscribeEvent
    public static void onLootEnchant(AcLootEnchantLevelEvent event) {
        Player player = event.getPlayer();
        if (player != null && SinTomes.equipped(player).contains(SinTome.GREED)) {
            event.setLevel(Math.max(0, event.getLevel() - 5));
        }
    }

    private SinLootHooks() {
    }
}
