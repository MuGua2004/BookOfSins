package com.example.book_of_sins.sins;

import com.example.ac_core.attr.AcCoreAttributes;
import com.example.ac_core.event.AcBrushingEvent;
import com.example.ac_core.event.AcItemDurabilityEvent;
import com.example.ac_core.event.AcOmnivampEvent;
import com.example.book_of_sins.BookOfSins;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 逆转之书的全部结算与逆转触发。
 *
 * <p>逆转条件（临时占位，后续会改）：傲慢 = 累积击杀 10000 只生物（原版统计）；
 * 嫉妒 = 击杀循声守卫；暴怒 = 击杀凋灵。条件满足时，随身（饰品槽优先，其次背包）
 * 的对应罪孽之书原地转化为逆转之书——根源之印因装备门槛（罪书在身）随之永久失效。</p>
 *
 * <p>属性类效果（嫉妒 #3/#6/#7、暴怒 #3）沿用罪书的「按秒对账」瞬时修正：
 * 每秒重写、摘下自动清零；蓄力速度一律经 {@link AcCoreAttributes#resolve} 写入。</p>
 */
@Mod.EventBusSubscriber(modid = BookOfSins.MOD_ID)
public final class ReversalEvents {

    // ---- 属性修正 UUID ----
    private static final UUID ENVY_EMC = UUID.fromString("7c4d2e00-0001-4a1b-9c2d-200000000001");
    private static final UUID ENVY6_MOVE = UUID.fromString("7c4d2e00-0002-4a1b-9c2d-200000000002");
    private static final UUID ENVY7_ATTACK = UUID.fromString("7c4d2e00-0003-4a1b-9c2d-200000000003");
    private static final UUID ENVY7_DRAW = UUID.fromString("7c4d2e00-0004-4a1b-9c2d-200000000004");
    private static final UUID WRATH_STACK = UUID.fromString("7c4d2e00-0005-4a1b-9c2d-200000000005");
    /** 已驯服生物最大生命翻倍（写入实体持久属性，佩戴时驯服即获得）。 */
    private static final UUID PET_HP = UUID.fromString("7c4d2e00-0006-4a1b-9c2d-200000000006");
    // ---- 懒惰 ----
    private static final UUID SLOTH_RESPAWN = UUID.fromString("7c4d2e00-0007-4a1b-9c2d-200000000007");
    private static final UUID SLOTH_PHANTOM_MOVE = UUID.fromString("7c4d2e00-0008-4a1b-9c2d-200000000008");
    // ---- 贪婪 ----
    private static final UUID GREED_REACH_BLOCK = UUID.fromString("7c4d2e00-0009-4a1b-9c2d-200000000009");
    private static final UUID GREED_REACH_ENTITY = UUID.fromString("7c4d2e00-000a-4a1b-9c2d-20000000000a");
    // ---- 暴食 ----
    private static final UUID GLUTTONY_MOVE = UUID.fromString("7c4d2e00-000b-4a1b-9c2d-20000000000b");

    /** 懒惰#4：重生 buff 覆盖的属性集合（全属性 +10% 的落地范围）。 */
    private static final net.minecraft.world.entity.ai.attributes.Attribute[] RESPAWN_ATTRS = {
            Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED,
            Attributes.MOVEMENT_SPEED, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS};

    /** 随机正面 buff 池（懒惰#3 / 色欲#6；刻意排除村庄英雄——它是懒惰的逆转触发器）。 */
    private static final net.minecraft.world.effect.MobEffect[] POSITIVE_BUFFS = {
            net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, net.minecraft.world.effect.MobEffects.DIG_SPEED,
            net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, net.minecraft.world.effect.MobEffects.REGENERATION,
            net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
            net.minecraft.world.effect.MobEffects.WATER_BREATHING, net.minecraft.world.effect.MobEffects.NIGHT_VISION,
            net.minecraft.world.effect.MobEffects.JUMP, net.minecraft.world.effect.MobEffects.ABSORPTION,
            net.minecraft.world.effect.MobEffects.LUCK, net.minecraft.world.effect.MobEffects.SLOW_FALLING};

    /** 傲慢逆转条件：累积击杀生物数（临时占位值）。 */
    private static final int PRIDE_KILL_TARGET = 10000;

    // ---- 每玩家内存态（登出清除） ----
    private static final Map<UUID, RevState> STATES = new HashMap<>();

    private static final class RevState {
        long lastHurtGameTime = Long.MIN_VALUE;   // 傲慢#6
        boolean healPending;                      // 傲慢#6
        int wrathStacks;                          // 暴怒#3
        int wrathExpire;                          // 暴怒#3
        // ---- 懒惰 ----
        int slothRespawnExpire = Integer.MIN_VALUE;  // #4 重生 buff 截止
        int slothTpCdUntil;                          // #7 传送无敌冷却
        LivingEntity slothExtraTarget;               // #1 待结算的额外攻击目标
        float slothExtraAmount;                      // #1 待结算的额外攻击伤害
        // ---- 贪婪 ----
        int greedPickupHealCd;                       // #6 拾取回血冷却
        // ---- 暴食 ----
        int gluttonyAteTick = -100;                  // #5 刚进食标记（效果翻倍窗口）
        boolean gluttonyDoubling;                    // #5 重入护栏
        // ---- 色欲 ----
        int lustRegenCd;                             // #2 受击回血冷却
        int lustStrikeCdUntil;                       // #5 首击加成冷却
        int lustStrikeUsedTick = -100;               // #5 首击使用时刻（击杀重置判定）
        net.minecraft.world.effect.MobEffect lustBuff; // #6 当前随机永久 buff
        int lustBuffRerollAt;                        // #6 重随时刻
        int lustXpGainAt;                            // #7 经验发放时刻
    }

    private static RevState state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new RevState());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    // ==================== 逆转触发 ====================

    /** 傲慢：累积击杀 10000 只生物（按原版统计，20 tick 查一次）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
            return;
        }
        Player player = event.player;
        RevState st = state(player);

        // 暴怒#3：叠层过期（每 tick 检查，量小）
        if (st.wrathStacks > 0 && player.tickCount > st.wrathExpire) {
            st.wrathStacks = 0;
            removeMod(player, Attributes.ATTACK_SPEED, WRATH_STACK);
            removeMod(player, AcCoreAttributes.resolve(AcCoreAttributes.DRAW_SPEED.get()), WRATH_STACK);
        }

        // 傲慢#6：受击 1 秒后回复 10% 最大生命（每 tick 精确触发）
        if (st.healPending && player.level().getGameTime() >= st.lastHurtGameTime + 20) {
            st.healPending = false;
            player.heal(player.getMaxHealth() * 0.1F);
        }

        // 懒惰#1：上一 tick 排队的额外攻击
        if (st.slothExtraTarget != null) {
            LivingEntity target = st.slothExtraTarget;
            float extra = st.slothExtraAmount;
            st.slothExtraTarget = null;
            if (target.isAlive() && extra > 0) {
                target.hurt(player.damageSources().playerAttack(player), extra);
            }
        }

        if (player.tickCount % 20 != 0) {
            return;
        }

        // 逆转触发：傲慢（原版生物击杀统计）
        if (ReversalTomes.isAvailable(SinTome.PRIDE) && player instanceof net.minecraft.server.level.ServerPlayer sp
                && sp.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)) >= PRIDE_KILL_TARGET) {
            attemptReverse(player, SinTome.PRIDE);
        }

        // ---- 嫉妒 #3/#6/#7：按秒对账 ----
        int enderKinds = countEnderKinds(player);
        applyAdd(player, Attributes.ATTACK_DAMAGE, ENVY_EMC, 5.0D * enderKinds);
        double attackSpeed = value(player, Attributes.ATTACK_SPEED);
        double moveSpeed = value(player, Attributes.MOVEMENT_SPEED);
        applyMod(player, Attributes.MOVEMENT_SPEED, ENVY6_MOVE, attackSpeed > 3.0D ? 0.10D : 0.0D);
        applyMod(player, Attributes.ATTACK_SPEED, ENVY7_ATTACK, moveSpeed > 3.0D ? 0.10D : 0.0D);
        applyMod(player, AcCoreAttributes.resolve(AcCoreAttributes.DRAW_SPEED.get()),
                ENVY7_DRAW, moveSpeed > 3.0D ? 0.10D : 0.0D);

        // 嫉妒#5：盔甲耐久低于 50% 时每 10 秒回复 5 点
        if (player.tickCount % 200 == 0 && ReversalTomes.isEquipped(player, SinTome.ENVY)) {
            for (ItemStack armor : player.getInventory().armor) {
                if (armor.isDamageableItem() && armor.getDamageValue() > 0
                        && armor.getDamageValue() > armor.getMaxDamage() / 2) {
                    armor.setDamageValue(Math.max(0, armor.getDamageValue() - 5));
                }
            }
        }

        // ---- 懒惰 #4/#5：按秒对账 ----
        boolean slothRespawn = player.tickCount < st.slothRespawnExpire;
        for (var attr : RESPAWN_ATTRS) {
            applyMod(player, attr, SLOTH_RESPAWN, slothRespawn ? 0.10D : 0.0D);
        }
        boolean phantomDue = player instanceof net.minecraft.server.level.ServerPlayer sp2
                && sp2.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) >= 72000;
        applyMod(player, Attributes.MOVEMENT_SPEED, SLOTH_PHANTOM_MOVE, phantomDue ? 0.10D : 0.0D);

        // ---- 贪婪 #4/#7：按秒对账 ----
        boolean greedOn = ReversalTomes.isEquipped(player, SinTome.GREED);
        if (greedOn) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE, 60, 4, true, false));
        }
        applyAdd(player, net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), GREED_REACH_BLOCK, greedOn ? 3.0D : 0.0D);
        applyAdd(player, net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), GREED_REACH_ENTITY, greedOn ? 3.0D : 0.0D);

        // ---- 暴食#4：饱食度为满时 +10% 移速 ----
        applyMod(player, Attributes.MOVEMENT_SPEED, GLUTTONY_MOVE,
                ReversalTomes.isEquipped(player, SinTome.GLUTTONY)
                        && player.getFoodData().getFoodLevel() >= 20 ? 0.10D : 0.0D);

        // ---- 色欲 #6/#7：按秒对账 ----
        if (ReversalTomes.isEquipped(player, SinTome.LUST)) {
            if (st.lustXpGainAt == 0) {
                st.lustXpGainAt = player.tickCount + 12000;
            } else if (player.tickCount >= st.lustXpGainAt) {
                st.lustXpGainAt = player.tickCount + 12000;
                player.giveExperiencePoints(1000);
            }
            if (player.tickCount >= st.lustBuffRerollAt) {
                st.lustBuff = POSITIVE_BUFFS[player.getRandom().nextInt(POSITIVE_BUFFS.length)];
                st.lustBuffRerollAt = player.tickCount + 72000;  // 每 1 小时重随
            }
            if (st.lustBuff != null) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        st.lustBuff, 60, 0, true, false));
            }
        }

        // 逆转触发：色欲（跳入虚空）
        if (ReversalTomes.isAvailable(SinTome.LUST)
                && player.getY() < player.level().getMinBuildHeight() - 8) {
            attemptReverse(player, SinTome.LUST);
        }
    }

    /** 嫉妒/暴怒/贪婪/暴食：逆转触发；色欲#5：首击击杀目标则重置冷却。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        var type = event.getEntity().getType();
        if (type == net.minecraft.world.entity.EntityType.WARDEN) {
            attemptReverse(player, SinTome.ENVY);
        } else if (type == net.minecraft.world.entity.EntityType.WITHER) {
            attemptReverse(player, SinTome.WRATH);
        } else if (type == net.minecraft.world.entity.EntityType.ENDER_DRAGON) {
            attemptReverse(player, SinTome.GREED);
        } else if (type == net.minecraft.world.entity.EntityType.ELDER_GUARDIAN) {
            attemptReverse(player, SinTome.GLUTTONY);
        }
        // 色欲#5：本次攻击带首击加成且击杀了目标 → 冷却立即重置
        if (ReversalTomes.isEquipped(player, SinTome.LUST)) {
            RevState st = state(player);
            if (player.tickCount - st.lustStrikeUsedTick <= 2) {
                st.lustStrikeCdUntil = player.tickCount;
            }
        }
    }

    /**
     * 逆转：把随身对应罪孽之书原地转化为逆转之书（Curios 典籍槽优先，其次背包）。
     * 已佩戴的根源之印由 {@link RootSealEvents} 的周期校验自动弹回。
     */
    private static void attemptReverse(Player player, SinTome tome) {
        if (!ReversalTomes.isAvailable(tome)) {
            return;
        }
        ItemStack result = new ItemStack(ReversalTomes.BOOKS.get(tome).get());
        boolean[] done = {false};

        // 1) Curios 槽位（罪书绑定在此，最常见）
        curios:
        for (var stacksHandler : CuriosApi.getCuriosInventory(player)
                .map(h -> h.getCurios().values()).orElse(java.util.List.of())) {
            var stacks = stacksHandler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.getItem() instanceof SinTomeItem item && item.tome() == tome) {
                    stacks.setStackInSlot(i, result);
                    done[0] = true;
                    break curios;
                }
            }
        }

        // 2) 背包（含创造模式取得未装备的情况）
        if (!done[0]) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() instanceof SinTomeItem item && item.tome() == tome) {
                    inv.setItem(i, result);
                    done[0] = true;
                    break;
                }
            }
        }
        if (!done[0]) {
            return;
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.translatable(
                "message.book_of_sins.reversal.done", tome.display()));
    }

    // ==================== 伤害方向（傲慢 / 嫉妒 / 暴怒） ====================

    /** 护甲折算前（LivingHurt）：傲慢#1、嫉妒#1/#2、暴怒#4/#6、傲慢#6 计时。 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = event.getSource().getEntity() instanceof Player p && p != victim ? p : null;

        // 傲慢#1：免疫摔落伤害
        if (victim != null && ReversalTomes.isEquipped(victim, SinTome.PRIDE)
                && event.getSource().is(DamageTypes.FALL)) {
            event.setAmount(0.0F);
            return;
        }
        // 傲慢#6：记录受击时间（1 秒后回复 10% 最大生命）
        if (victim != null && ReversalTomes.isEquipped(victim, SinTome.PRIDE)) {
            RevState st = state(victim);
            st.lastHurtGameTime = victim.level().getGameTime();
            st.healPending = true;
        }
        float amount = event.getAmount();
        // 嫉妒#1：持有物品的生物攻击，受伤 -10%
        if (victim != null && ReversalTomes.isEquipped(victim, SinTome.ENVY)
                && event.getSource().getEntity() instanceof LivingEntity holder
                && !holder.getMainHandItem().isEmpty()) {
            amount *= 0.9F;
        }
        // 嫉妒#2：目标生命高于自己，伤害 +10%
        if (attacker != null && ReversalTomes.isEquipped(attacker, SinTome.ENVY)
                && event.getEntity().getHealth() > attacker.getHealth()) {
            amount *= 1.1F;
        }
        // 暴怒#4：每距一格伤害 +0.1%
        if (attacker != null && ReversalTomes.isEquipped(attacker, SinTome.WRATH)) {
            amount *= 1.0F + 0.001F * (float) attacker.distanceTo(event.getEntity());
        }
        // 暴怒#6：受伤 -5%
        if (victim != null && ReversalTomes.isEquipped(victim, SinTome.WRATH)) {
            amount *= 0.95F;
        }
        // 暴食#2：饱和度为满时，受到的伤害 -5%
        if (victim != null && ReversalTomes.isEquipped(victim, SinTome.GLUTTONY)
                && victim.getFoodData().getSaturationLevel() >= 20.0F) {
            amount *= 0.95F;
        }
        // 色欲#2：受到攻击获得生命恢复 1（5 秒，冷却 60 秒）
        if (victim != null && ReversalTomes.isEquipped(victim, SinTome.LUST)
                && amount > 0) {
            RevState st = state(victim);
            if (victim.tickCount >= st.lustRegenCd) {
                st.lustRegenCd = victim.tickCount + 1200;
                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION, 100, 0));
            }
        }
        // 色欲#3：中立生物造成的伤害减半
        if (victim != null && ReversalTomes.isEquipped(victim, SinTome.LUST)
                && event.getSource().getEntity() instanceof net.minecraft.world.entity.NeutralMob) {
            amount *= 0.5F;
        }
        event.setAmount(amount);
    }

    /** 最终伤害（LivingDamage）：傲慢#3、暴怒#1/#2/#3/#7。 */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = event.getSource().getEntity() instanceof Player p && p != victim ? p : null;
        float amount = event.getAmount();

        if (attacker != null) {
            // 傲慢#3：每佩戴一本逆转之书，最终伤害 +3%
            int books = ReversalTomes.equippedCount(attacker);
            if (books > 0) {
                amount *= 1.0F + 0.03F * books;
            }
            // 暴食#1：饱食度为满时，最终伤害 +10%
            if (ReversalTomes.isEquipped(attacker, SinTome.GLUTTONY)
                    && attacker.getFoodData().getFoodLevel() >= 20) {
                amount *= 1.1F;
            }
            // 色欲#5：进入战斗的第一次攻击 +100%（冷却 180 秒，击杀重置）
            if (ReversalTomes.isEquipped(attacker, SinTome.LUST)) {
                RevState st = state(attacker);
                if (attacker.tickCount >= st.lustStrikeCdUntil) {
                    amount *= 2.0F;
                    st.lustStrikeCdUntil = attacker.tickCount + 3600;
                    st.lustStrikeUsedTick = attacker.tickCount;
                }
            }
            // 懒惰#1：3% 概率附加一次额外攻击（排队到下一 tick 结算）
            if (ReversalTomes.isEquipped(attacker, SinTome.SLOTH)
                    && attacker.getRandom().nextFloat() < 0.03F
                    && state(attacker).slothExtraTarget == null) {
                RevState st = state(attacker);
                st.slothExtraTarget = event.getEntity();
                st.slothExtraAmount = amount;
            }
            if (ReversalTomes.isEquipped(attacker, SinTome.WRATH)) {
                // 暴怒#1：伤害 +20%
                amount *= 1.2F;
                // 暴怒#3：命中叠层（攻速/蓄力 +5%，3 秒，至多 5 层）
                RevState st = state(attacker);
                st.wrathStacks = Math.min(5, st.wrathStacks + 1);
                st.wrathExpire = attacker.tickCount + 60;
                double stackBonus = 0.05D * st.wrathStacks;
                applyMod(attacker, Attributes.ATTACK_SPEED, WRATH_STACK, stackBonus);
                applyMod(attacker, AcCoreAttributes.resolve(AcCoreAttributes.DRAW_SPEED.get()),
                        WRATH_STACK, stackBonus);
            }
        }
        // 暴怒#7：受到的伤害 5% 转化为对自身的治疗（在最终伤害上结算）
        event.setAmount(amount);
        if (victim != null && amount > 0
                && ReversalTomes.isEquipped(victim, SinTome.WRATH)) {
            victim.heal(amount * 0.05F);
        }

        // 暴怒#2：造成的伤害高于自身生命值时，回复其 5%（按最终伤害）
        if (attacker != null && ReversalTomes.isEquipped(attacker, SinTome.WRATH)
                && amount > attacker.getMaxHealth()) {
            attacker.heal((float) (amount * 0.05D));
        }
    }

    // ==================== 其他时点 ====================

    /** 傲慢#2：免疫冰冻效果（Forge 1.20.1 无 LivingFreezeEvent，tick 清零冻结值）。 */
    @SubscribeEvent
    public static void onPlayerTickFreeze(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
            return;
        }
        Player player = event.player;
        if (player.getTicksFrozen() > 0 && ReversalTomes.isEquipped(player, SinTome.PRIDE)) {
            player.setTicksFrozen(0);
        }
    }

    /** 傲慢#7：射出的箭速度 +10%（箭生成入世界时放大初速）。 */
    @SubscribeEvent
    public static void onArrowLaunch(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof Player player)
                || !ReversalTomes.isEquipped(player, SinTome.PRIDE)) {
            return;
        }
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.1D));
    }

    /** 傲慢#4：被驯服的生物最大生命值翻倍（永久修正，写入实体 NBT）。 */
    @SubscribeEvent
    public static void onPetTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof TamableAnimal pet) || pet.level().isClientSide) {
            return;
        }
        if (!pet.isTame() || pet.getOwnerUUID() == null) {
            return;
        }
        AttributeInstance inst = pet.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null
                || inst.getModifiers().stream().anyMatch(m -> PET_HP.equals(m.getId()))) {
            return;
        }
        Player owner = pet.level() instanceof net.minecraft.server.level.ServerLevel sl
                ? sl.getServer().getPlayerList().getPlayer(pet.getOwnerUUID()) : null;
        if (owner != null && ReversalTomes.isEquipped(owner, SinTome.PRIDE)) {
            inst.addPermanentModifier(new AttributeModifier(PET_HP, "book_of_sins:reversal_pet",
                    1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ==================== 懒惰 ====================

    /** 懒惰#2/#3：睡觉时回满生命；起床时获得随机 1 种正面 buff（60 秒）。 */
    @SubscribeEvent
    public static void onWakeUp(net.minecraftforge.event.entity.player.PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !ReversalTomes.isEquipped(player, SinTome.SLOTH)) {
            return;
        }
        player.setHealth(player.getMaxHealth());
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                POSITIVE_BUFFS[player.getRandom().nextInt(POSITIVE_BUFFS.length)], 1200, 0));
    }

    /** 懒惰#4：重生标记（60 秒内全属性 +10%，由按秒对账落地）。 */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !ReversalTomes.isEquipped(player, SinTome.SLOTH)) {
            return;
        }
        state(player).slothRespawnExpire = player.tickCount + 1200;
    }

    /** 懒惰#7：传送后 3 秒无敌（1 分钟冷却）；同时覆盖跨维度传送。 */
    @SubscribeEvent
    public static void onTeleport(net.minecraftforge.event.entity.EntityTeleportEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        triggerTpShield(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        triggerTpShield(event.getEntity());
    }

    private static void triggerTpShield(Player player) {
        if (!ReversalTomes.isEquipped(player, SinTome.SLOTH)) {
            return;
        }
        RevState st = state(player);
        if (player.tickCount >= st.slothTpCdUntil) {
            st.slothTpCdUntil = player.tickCount + 1200;
            player.invulnerableTime = 60;
        }
    }

    // ==================== 贪婪 ====================

    /** 贪婪#2：挖掘方块 5% 概率获得随机普通矿物（数量 = 1 + 时运等级）；懒惰#6：挖掘 20% 概率急迫 3（10 秒）。 */
    @SubscribeEvent
    public static void onBreakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (event.getLevel().isClientSide() || player == null) {
            return;
        }
        if (ReversalTomes.isEquipped(player, SinTome.SLOTH)
                && player.getRandom().nextFloat() < 0.20F) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DIG_SPEED, 200, 2));
        }
        if (ReversalTomes.isEquipped(player, SinTome.GREED)
                && player.getRandom().nextFloat() < 0.05F) {
            int fortune = EnchantmentHelper.getItemEnchantmentLevel(
                    Enchantments.BLOCK_FORTUNE, player.getMainHandItem());
            ItemStack ore = new ItemStack(switch (player.getRandom().nextInt(3)) {
                case 0 -> net.minecraft.world.item.Items.RAW_IRON;
                case 1 -> net.minecraft.world.item.Items.RAW_GOLD;
                default -> net.minecraft.world.item.Items.RAW_COPPER;
            }, 1 + fortune);
            if (!player.getInventory().add(ore)) {
                player.drop(ore, false);
            }
        }
    }

    /** 贪婪#6：每拾取一个物品回复 5 点血量（冷却 10 秒）。 */
    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !ReversalTomes.isEquipped(player, SinTome.GREED)) {
            return;
        }
        RevState st = state(player);
        if (player.tickCount >= st.greedPickupHealCd) {
            st.greedPickupHealCd = player.tickCount + 200;
            player.heal(5.0F);
        }
    }

    // ==================== 暴食 / 色欲 ====================

    /** 暴食#3/#5/#6：进食回血（= 饱食度）、金质食物生命恢复 3、效果翻倍窗口标记。 */
    @SubscribeEvent
    public static void onUseItemFinish(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        Player player = event.getEntity() instanceof Player p ? p : null;
        if (player == null || player.level().isClientSide
                || !ReversalTomes.isEquipped(player, SinTome.GLUTTONY)) {
            return;
        }
        var food = event.getItem().getFoodProperties(player);
        if (food == null) {
            return;
        }
        state(player).gluttonyAteTick = player.tickCount;
        if (food.getNutrition() > 0) {
            player.heal(food.getNutrition());
        }
        var itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(event.getItem().getItem());
        if (itemId != null && itemId.getPath().contains("golden")) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION, 200, 2));
        }
    }

    /** 懒惰逆转触发（村庄英雄）；暴食#5：刚进食的增益效果翻倍（时长×2、等级+1）。 */
    @SubscribeEvent
    public static void onMobEffectAdded(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        var inst = event.getEffectInstance();
        // 懒惰逆转：获得村庄英雄（原版仅在灾厄袭击胜利时授予，amplifier 0；
        // 贪婪#4 自刷的英雄为 amplifier 4，不会误触发）
        if (inst.getEffect() == net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE
                && inst.getAmplifier() == 0
                && event.getEntity() instanceof Player player
                && ReversalTomes.isAvailable(SinTome.SLOTH)) {
            attemptReverse(player, SinTome.SLOTH);
        }
        // 暴食#5
        if (event.getEntity() instanceof Player player) {
            RevState st = state(player);
            if (!st.gluttonyDoubling
                    && player.tickCount - st.gluttonyAteTick <= 3
                    && ReversalTomes.isEquipped(player, SinTome.GLUTTONY)
                    && inst.getEffect().isBeneficial()) {
                st.gluttonyDoubling = true;
                event.setCanceled(true);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        inst.getEffect(), inst.getDuration() * 2, inst.getAmplifier() + 1,
                        inst.isAmbient(), inst.isVisible()));
                st.gluttonyDoubling = false;
            }
        }
    }

    /** 色欲#1：击杀动物的掉落物翻倍。 */
    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getSource().getEntity() instanceof Player player)
                || !ReversalTomes.isEquipped(player, SinTome.LUST)
                || !(event.getEntity() instanceof net.minecraft.world.entity.animal.Animal)) {
            return;
        }
        java.util.List<net.minecraft.world.entity.item.ItemEntity> copies = new java.util.ArrayList<>();
        for (var original : event.getDrops()) {
            copies.add(new net.minecraft.world.entity.item.ItemEntity(
                    original.level(), original.getX(), original.getY(), original.getZ(),
                    original.getItem().copy()));
        }
        event.getDrops().addAll(copies);
    }

    /** 贪婪#1/#5：时运与抢夺等级 +3（ac_core 掉落附魔等级钩子同时覆盖两者）。 */
    @SubscribeEvent
    public static void onLootEnchant(com.example.ac_core.event.AcLootEnchantLevelEvent event) {
        if (event.getPlayer() != null && ReversalTomes.isEquipped(event.getPlayer(), SinTome.GREED)) {
            event.setLevel(event.getLevel() + 3);
        }
    }

    // ==================== ac_core 挂点 ====================

    /** 嫉妒#4：工具耐久消耗减半。 */
    @SubscribeEvent
    public static void onDurability(AcItemDurabilityEvent event) {
        if (ReversalTomes.isEquipped(event.getPlayer(), SinTome.ENVY)) {
            event.setAmount(event.getAmount() / 2);
        }
    }

    /** 暴怒#5：生命偷取提高 10%（ac_core 全能吸血结算钩子）。 */
    @SubscribeEvent
    public static void onOmnivamp(AcOmnivampEvent event) {
        if (ReversalTomes.isEquipped(event.getAttacker(), SinTome.WRATH)) {
            event.setMultiplier(event.getMultiplier() * 1.10D);
        }
    }

    /** 傲慢#5：考古 50% 双倍掉落（取消原版结算后按 loot 表手动跑两次）。 */
    @SubscribeEvent
    public static void onBrushing(AcBrushingEvent event) {
        if (event.getLootTable() == null
                || !ReversalTomes.isEquipped(event.getPlayer(), SinTome.PRIDE)
                || event.getPlayer().getRandom().nextFloat() >= 0.5F) {
            return;
        }
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        LootTable table = level.getServer().getLootData().getLootTable(event.getLootTable());
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.THIS_ENTITY, event.getPlayer())
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withLuck(event.getPlayer().getLuck());
        for (int i = 0; i < 2; i++) {
            for (ItemStack stack : table.getRandomItems(params.create(LootContextParamSets.ARCHAEOLOGY))) {
                Block.popResource(level, pos, stack);
            }
        }
        convertBrushable(level, pos);
        event.setCanceled(true);
    }

    /** 刷拭完成后把可疑方块转化为普通方块（与原版完成流程一致，防止重复触发）。 */
    static void convertBrushable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof BrushableBlock brushable) {
            level.setBlock(pos, brushable.getTurnsInto().defaultBlockState(), 3);
        } else {
            level.removeBlock(pos, false);
        }
        level.levelEvent(3008, pos, 0);
    }

    // ==================== 工具 ====================

    /** 末影箱里不同物品种类数（嫉妒#3）。 */
    private static int countEnderKinds(Player player) {
        Set<ResourceLocation> kinds = new HashSet<>();
        var ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            ItemStack stack = ender.getItem(i);
            if (!stack.isEmpty()) {
                kinds.add(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
            }
        }
        return kinds.size();
    }

    private static void applyMod(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                 UUID id, double amount) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        if (amount != 0.0D) {
            inst.addTransientModifier(new AttributeModifier(id, "book_of_sins:reversal",
                    amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void applyAdd(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                 UUID id, double amount) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        if (amount != 0.0D) {
            inst.addTransientModifier(new AttributeModifier(id, "book_of_sins:reversal",
                    amount, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void removeMod(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                  UUID id) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }

    private static double value(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        AttributeInstance inst = player.getAttribute(attribute);
        return inst == null ? 0.0D : inst.getValue();
    }

    private ReversalEvents() {
    }
}
