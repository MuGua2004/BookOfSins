package com.example.book_of_sins.charm;

import com.example.book_of_sins.BookOfSins;
import com.example.book_of_sins.effect.BosMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.UUID;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 护符被动结算（堕亡颅骨 / 深渊利爪 / 神惧劫炎 / 神诗终幕 / 白日喧嚣 / 黑夜寂寥）。
 */
@Mod.EventBusSubscriber(modid = BookOfSins.MOD_ID)
public final class CharmPassives {

    private static final String KEY_GW_UNTIL = "sl_gw_until";
    private static final String KEY_SHRED_UNTIL = "sl_talon_shred_until";
    private static final String KEY_DF_STACKS = "sl_df_stacks";
    private static final String KEY_DF_UNTIL = "sl_df_until";
    private static final String KEY_DF_NEXT = "sl_df_next";
    private static final String KEY_DC_UNTIL = "sl_dc_until";
    private static final String KEY_DC_PREV = "sl_dc_prev";
    private static final String KEY_DC_CD = "sl_dc_cd";
    private static final String KEY_NH_CD = "sl_nh_cd";
    private static final String KEY_LAST_COMBAT = "sl_last_combat";

    private static final UUID SHRED_UUID = UUID.fromString("5b3f1e20-1111-4c1a-9b7d-0d0101010009");
    private static final String SHRED_NAME = "book_of_sins:abyssal_talon_armor_shred";

    /** 扫描 Curios 全槽位判断是否佩戴指定物品。 */
    public static boolean isWearing(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    if (stacks.getStackInSlot(i).is(item)) {
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }

    /** 按注册 id 判断（成对互斥装备用）。 */
    public static boolean isWearingId(LivingEntity entity, ResourceLocation itemId) {
        return CuriosApi.getCuriosInventory(entity).map(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(itemId)) {
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        long gameTime = victim.level().getGameTime();

        // 神惧劫炎：免疫火焰伤害
        if (isWearing(victim, BosCharms.DREADFLAME.get())
                && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            return;
        }
        // 神诗终幕：受到的伤害 -33%
        if (isWearing(victim, BosCharms.FINAL_CANTO.get())) {
            event.setAmount(event.getAmount() * 0.67F);
        }
        if (victim instanceof Player) {
            victim.getPersistentData().putLong(KEY_LAST_COMBAT, gameTime);
        }

        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        float amount = event.getAmount();

        // 堕亡颅骨：对亡灵 +50%；对非亡灵施加 50% 重伤 10 秒
        if (isWearing(attacker, BosCharms.FALLEN_SKULL.get())) {
            if (victim.getMobType() == net.minecraft.world.entity.MobType.UNDEAD) {
                amount *= 1.5F;
            } else {
                victim.getPersistentData().putLong(KEY_GW_UNTIL, gameTime + 200);
            }
        }

        // 深渊利爪：命中削减 50% 护甲 5 秒；目标无护甲时伤害 +10%
        if (isWearing(attacker, BosCharms.ABYSSAL_TALON.get())) {
            AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifier(SHRED_UUID);
                armor.addTransientModifier(new AttributeModifier(SHRED_UUID, SHRED_NAME, -0.5D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
                victim.getPersistentData().putLong(KEY_SHRED_UNTIL, gameTime + 100);
            }
            if (victim.getArmorValue() <= 0.0F) {
                amount *= 1.1F;
            }
        }

        // 神惧劫炎：伤害 +50%；攻击赋予可叠加灼烧（每秒 5 点真实伤害，3 秒）
        if (isWearing(attacker, BosCharms.DREADFLAME.get())) {
            amount *= 1.5F;
            CompoundTag data = victim.getPersistentData();
            data.putInt(KEY_DF_STACKS, data.getInt(KEY_DF_STACKS) + 1);
            data.putLong(KEY_DF_UNTIL, gameTime + 60);
            if (gameTime >= data.getLong(KEY_DF_NEXT)) {
                data.putLong(KEY_DF_NEXT, gameTime + 20);
            }
        }
        // 神诗终幕：最终伤害 +3%；脱战 10 秒后的首次攻击获得三祝福 30 秒
        if (isWearing(attacker, BosCharms.FINAL_CANTO.get())) {
            amount *= 1.03F;
            if (attacker instanceof Player player) {
                long last = player.getPersistentData().getLong(KEY_LAST_COMBAT);
                if (last == 0 || gameTime - last >= 200) {
                    player.addEffect(new MobEffectInstance(BosMobEffects.STAR_BLESSING.get(), 600, 0));
                    player.addEffect(new MobEffectInstance(BosMobEffects.STAR_SAGE.get(), 600, 0));
                    player.addEffect(new MobEffectInstance(BosMobEffects.STAR_REDEMPTION.get(), 600, 0));
                }
                player.getPersistentData().putLong(KEY_LAST_COMBAT, gameTime);
            }
        }

        // 白日喧嚣：最终伤害 +10%；攻击关闭目标 AI 3 秒（冷却 30 秒）
        if (isWearing(attacker, BosCharms.DAY_CLAMOR.get())) {
            amount *= 1.10F;
            CompoundTag data = attacker.getPersistentData();
            if (gameTime >= data.getLong(KEY_DC_CD) && victim instanceof Mob mob && !mob.isNoAi()) {
                data.putLong(KEY_DC_CD, gameTime + 600);
                victim.getPersistentData().putBoolean(KEY_DC_PREV, mob.isNoAi());
                mob.setNoAi(true);
                victim.getPersistentData().putLong(KEY_DC_UNTIL, gameTime + 60);
            }
        }

        // 黑夜寂寥：最终伤害 +10%；攻击附加目标最大生命 0.1% 的虚空伤害（内置冷却 0.5 秒）
        if (isWearing(attacker, BosCharms.NIGHT_HUSH.get())) {
            amount *= 1.10F;
            CompoundTag data = attacker.getPersistentData();
            if (gameTime >= data.getLong(KEY_NH_CD)) {
                data.putLong(KEY_NH_CD, gameTime + 10);
                victim.hurt(victim.damageSources().fellOutOfWorld(),
                        victim.getMaxHealth() * 0.001F);
            }
        }

        if (attacker instanceof Player) {
            attacker.getPersistentData().putLong(KEY_LAST_COMBAT, gameTime);
        }
        event.setAmount(amount);
    }

    /** 堕亡颅骨的重伤：生命回复 -50%。 */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity.getPersistentData().getLong(KEY_GW_UNTIL) > entity.level().getGameTime()) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }

    /** 定时清理：削甲到期 / 灼烧结算与过期 / AI 恢复。 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        long gameTime = entity.level().getGameTime();
        CompoundTag data = entity.getPersistentData();

        if (data.getLong(KEY_SHRED_UNTIL) > 0 && gameTime > data.getLong(KEY_SHRED_UNTIL)) {
            AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifier(SHRED_UUID);
            }
            data.putLong(KEY_SHRED_UNTIL, 0);
        }

        int stacks = data.getInt(KEY_DF_STACKS);
        if (stacks > 0) {
            if (gameTime >= data.getLong(KEY_DF_NEXT)) {
                entity.hurt(entity.damageSources().fellOutOfWorld(), 5.0F * stacks);
                data.putLong(KEY_DF_NEXT, gameTime + 20);
            }
            if (gameTime > data.getLong(KEY_DF_UNTIL)) {
                data.putInt(KEY_DF_STACKS, 0);
                data.putLong(KEY_DF_UNTIL, 0);
            }
        }

        if (data.getLong(KEY_DC_UNTIL) > 0 && gameTime > data.getLong(KEY_DC_UNTIL) && entity instanceof Mob mob) {
            mob.setNoAi(data.getBoolean(KEY_DC_PREV));
            data.putLong(KEY_DC_UNTIL, 0);
        }
    }

    private CharmPassives() {
    }
}