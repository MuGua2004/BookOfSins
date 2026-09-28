package com.example.book_of_sins.sins;

import com.example.ac_core.resource.AcCapabilities;
import com.example.ac_core.resource.IPlayerResources;
import com.example.ac_core.resource.ResourceType;
import com.example.book_of_sins.BookOfSins;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 七宗罪诅咒结算（49 条全部实装）。
 *
 * <p>近似实现说明：</p>
 * <ul>
 *   <li>傲慢#2 仇恨范围：对附近怪物加 FOLLOW_RANGE 临时修正模拟；</li>
 *   <li>懒惰#2 赖床：改为「有概率被赶下床」（右键床 30% 概率取消入睡）；</li>
 *   <li>暴食#2 饱和度不满：按 saturation = 0 判定；</li>
 *   <li>色欲#2 流血：ac_core 自定义流血效果（ac_core:bleed）；</li>
 *   <li>嫉妒#7 蓄力速度：AA 在场用 attributeslib:draw_speed，缺席用 book_of_sins:draw_speed + 本模组 Mixin；</li>
 *   <li>暴怒#5 生命偷取：ac_core 全能吸血结算钩子（AcOmnivampEvent，零 AA 依赖）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BookOfSins.MOD_ID)
public final class SinCurseEvents {

    private static final UUID ENVY6_ATTACK = UUID.fromString("8f3a1b2c-0001-4a1b-9c2d-000000000001");
    private static final UUID ENVY7_ATTACK = UUID.fromString("8f3a1b2c-0002-4a1b-9c2d-000000000002");
    private static final UUID ENVY6_MOVE = UUID.fromString("8f3a1b2c-0003-4a1b-9c2d-000000000003");
    private static final UUID ENVY7_MOVE = UUID.fromString("8f3a1b2c-0004-4a1b-9c2d-000000000004");
    private static final UUID WRATH_STACK = UUID.fromString("8f3a1b2c-0005-4a1b-9c2d-000000000005");
    private static final UUID SLOTH5_MOVE = UUID.fromString("8f3a1b2c-0006-4a1b-9c2d-000000000006");
    private static final UUID SLOTH5_ATTACK = UUID.fromString("8f3a1b2c-0007-4a1b-9c2d-000000000007");
    private static final UUID PRIDE2_MOB_FOLLOW = UUID.fromString("8f3a1b2c-0008-4a1b-9c2d-000000000008");
    private static final UUID SLOTH4_BASE = UUID.fromString("8f3a1b2c-0009-4a1b-9c2d-000000000009");
    private static final UUID ENVY7_DRAW = UUID.fromString("8f3a1b2c-000a-4a1b-9c2d-00000000000a");

    /** 色欲4：色欲佩戴者丢弃的金锭实体 UUID → 记账过期时间（防猪灵捡拾）。 */
    private static final java.util.Map<UUID, Long> TOSSED_GOLD = new java.util.concurrent.ConcurrentHashMap<>();

    /** 记账色欲佩戴者丢出的金锭（60 秒过期）。 */
    @SubscribeEvent
    public static void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide
                || !SinTomes.equipped(player).contains(SinTome.LUST)
                || !event.getEntity().getItem().is(net.minecraft.world.item.Items.GOLD_INGOT)) {
            return;
        }
        TOSSED_GOLD.put(event.getEntity().getUUID(), System.currentTimeMillis() + 60_000L);
    }

    /** 供 Mixin 查询：该物品实体是否是色欲佩戴者丢弃、尚未过记账期的金锭。 */
    public static boolean isLustTossedGold(java.util.UUID itemEntityId) {
        Long expire = TOSSED_GOLD.get(itemEntityId);
        if (expire == null) {
            return false;
        }
        if (System.currentTimeMillis() > expire) {
            TOSSED_GOLD.remove(itemEntityId);
            return false;
        }
        return true;
    }

    // ==================== 事件：伤害（进出双向路由） ====================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Player player = asPlayer(event.getEntity());
        Player attacker = asPlayer(event.getSource().getEntity());

        // ---- 出手方向（玩家带书打人） ----
        if (attacker != null) {
            Set<SinTome> sins = SinTomes.equipped(attacker);
            SinState state = SinState.of(attacker);
            float amount = event.getAmount();

            if (sins.contains(SinTome.WRATH)) {
                // 暴怒1：伤害 -50%；暴怒4：每格距离 -1%
                amount *= 0.5F;
                amount *= (float) Math.max(0.0D, 1.0D - 0.01D * attacker.distanceTo(event.getEntity()));
                // 暴怒2：本次伤害高于自身最大生命值 → 下一 tick 承受其 5%
                if (amount > attacker.getMaxHealth()) {
                    state.pendingSelfHarm += amount * 0.05D;
                }
                // 暴怒3：命中叠加 -5% 攻速（3 秒）
                applyWrathStack(attacker, state);
            }
            if (sins.contains(SinTome.ENVY) && event.getEntity().getHealth() > attacker.getHealth()) {
                // 嫉妒2：目标生命高于自己 → 伤害 -30%
                amount *= 0.7F;
            }
            if (sins.contains(SinTome.SLOTH) && attacker.getRandom().nextFloat() < 0.05F) {
                // 懒惰1：5% 概率该次伤害丢失
                amount = 0.0F;
            }
            if (sins.contains(SinTome.GLUTTONY) && attacker.getFoodData().getFoodLevel() < 20) {
                // 暴食1：饱食度不满 → 无法造成伤害
                amount = 0.0F;
            }
            if (sins.contains(SinTome.LUST)) {
                // 色欲5：进入战斗的第一次攻击 -99%
                if (attacker.tickCount > state.combatUntil) {
                    amount *= 0.01F;
                }
                state.combatUntil = attacker.tickCount + 200;
            }
            event.setAmount(amount);
        }

        // ---- 受击方向（玩家带书挨打） ----
        if (player != null) {
            Set<SinTome> sins = SinTomes.equipped(player);
            SinState state = SinState.of(player);
            float amount = event.getAmount();

            if (sins.contains(SinTome.PRIDE) && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
                // 傲慢1：摔落伤害 5%×2 / 1%×4 / 0.1%×8
                float roll = player.getRandom().nextFloat();
                if (roll < 0.001F) {
                    amount *= 8.0F;
                } else if (roll < 0.01F) {
                    amount *= 4.0F;
                } else if (roll < 0.05F) {
                    amount *= 2.0F;
                }
            }
            if (sins.contains(SinTome.ENVY)) {
                // 嫉妒1：持物生物打你 +10%
                if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity holder
                        && !holder.getMainHandItem().isEmpty()) {
                    amount *= 1.1F;
                }
                // 嫉妒5：盔甲耐久 <50% → 5% 概率耐久清零
                for (ItemStack armor : player.getInventory().armor) {
                    if (!armor.isEmpty() && isBelowHalfDurability(armor)
                            && player.getRandom().nextFloat() < 0.05F) {
                        armor.setDamageValue(armor.getMaxDamage());
                        player.level().playSound(null, player.blockPosition(),
                                SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                }
            }
            if (sins.contains(SinTome.WRATH)) {
                // 暴怒6：受伤 +10%；暴怒7：每次受伤不低于 20% 最大生命值
                amount *= 1.1F;
                amount = Math.max(amount, player.getMaxHealth() * 0.2F);
            }
            if (sins.contains(SinTome.GLUTTONY)
                    && player.getFoodData().getSaturationLevel() <= 0.0F) {
                // 暴食2：饱和度不满 → 受伤 +20%
                amount *= 1.2F;
            }
            if (sins.contains(SinTome.LUST)) {
                // 色欲2：受击流血（ac_core:bleed，每 2 秒掉血）
                // 注意：受击不进入「战斗窗口」——那是色欲5 首击判定的独立状态
                player.addEffect(new MobEffectInstance(
                        com.example.ac_core.effect.AcCoreEffects.BLEED.get(), 100, 0));
            }
            // 傲慢6：记录受击时间（1 秒禁疗窗口）
            state.lastHurtGameTime = player.level().getGameTime();
            event.setAmount(amount);
        }
    }

    // ==================== 事件：其他单点诅咒 ====================

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        Player player = asPlayer(event.getEntity());
        if (player == null) {
            return;
        }
        Set<SinTome> sins = SinTomes.equipped(player);
        SinState state = SinState.of(player);
        // 傲慢6：受击后 1 秒内无法恢复生命值
        if (sins.contains(SinTome.PRIDE)
                && player.level().getGameTime() - state.lastHurtGameTime < 20L) {
            event.setCanceled(true);
        }
        // 暴食1：饱食度不满 → 无法恢复生命值
        if (sins.contains(SinTome.GLUTTONY) && player.getFoodData().getFoodLevel() < 20) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAnimalTame(AnimalTameEvent event) {
        // 傲慢4：无法驯服生物
        if (event.getTamer() != null && SinTomes.equipped(event.getTamer()).contains(SinTome.PRIDE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOmnivamp(com.example.ac_core.event.AcOmnivampEvent event) {
        // 暴怒5：生命偷取效率 -50%（作用于 ac_core 全能吸血体系的每次结算）
        if (SinTomes.equipped(event.getAttacker()).contains(SinTome.WRATH)) {
            event.setMultiplier(event.getMultiplier() * 0.5D);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        // 傲慢7：射出的箭 5% 概率回击自己（近似：命中瞬间箭伤反弹给射手）
        if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                && arrow.getOwner() instanceof Player player
                && SinTomes.equipped(player).contains(SinTome.PRIDE)
                && player.getRandom().nextFloat() < 0.05F) {
            player.hurt(player.damageSources().arrow(arrow, player), 5.0F);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        // 贪婪6：每拾取一个物品受到 1 点伤害
        Player player = event.getEntity();
        if (SinTomes.equipped(player).contains(SinTome.GREED)) {
            player.hurt(player.damageSources().generic(), 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        // 懒惰2：赖床——30% 概率被赶下床（当晚无法入睡；只判定主手，避免双手双掷）
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && event.getLevel().getBlockState(event.getPos()).getBlock()
                        instanceof net.minecraft.world.level.block.BedBlock
                && SinTomes.equipped(player).contains(SinTome.SLOTH)
                && player.getRandom().nextFloat() < 0.30F) {
            event.setCanceled(true);
            return;
        }
        // 贪婪7：右键功能方块（可打开 UI 的）受到 5 点伤害
        if (!SinTomes.equipped(player).contains(SinTome.GREED)) {
            return;
        }
        BlockEntity be = player.level().getBlockEntity(event.getPos());
        if (be instanceof MenuProvider) {
            player.hurt(player.damageSources().generic(), 5.0F);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        Set<SinTome> sins = SinTomes.equipped(player);
        // 色欲4：猪灵拒绝与你交易
        if (event.getTarget() instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
                && sins.contains(SinTome.LUST)) {
            // TODO 调试日志（定位拦截问题，确认后移除）
            BookOfSins.LOGGER.info("[DEBUG lust_piglin] 拦截猪灵交易 hand={}", event.getHand());
            event.setCanceled(true);
            return;
        }
        // 贪婪4：村民交易价格翻倍且不再补货（每个村民只处理一次）
        // 「不再补货」由 VillagerRestockMixin 拦截原版补货方法实现
        if (event.getTarget() instanceof net.minecraft.world.entity.npc.Villager villager
                && sins.contains(SinTome.GREED)
                && !villager.getPersistentData().getBoolean("book_of_sins_greed")) {
            villager.getPersistentData().putBoolean("book_of_sins_greed", true);
            var offers = villager.getOffers();
            for (int i = 0; i < offers.size(); i++) {
                var offer = offers.get(i);
                ItemStack costA = offer.getCostA().copy();
                if (!costA.isEmpty()) {
                    costA.setCount(Math.min(costA.getMaxStackSize(), costA.getCount() * 2));
                }
                offers.set(i, new net.minecraft.world.item.trading.MerchantOffer(costA,
                        offer.getCostB().copy(), offer.getResult().copy(),
                        offer.getUses(), offer.getMaxUses(), offer.getXp(),
                        offer.getPriceMultiplier()));
            }
        }
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide) {
            return;
        }
        Set<SinTome> sins = SinTomes.equipped(player);
        // 贪婪2：10% 概率挖掘失败
        if (sins.contains(SinTome.GREED) && player.getRandom().nextFloat() < 0.10F) {
            event.setCanceled(true);
            return;
        }
        // 懒惰6：10% 概率获得 10 秒挖掘疲劳3
        if (sins.contains(SinTome.SLOTH) && player.getRandom().nextFloat() < 0.10F) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 2));
        }
    }

    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        Player player = asPlayer(event.getEntity());
        if (player == null) {
            return;
        }
        SinState state = SinState.of(player);
        Set<SinTome> sins = SinTomes.equipped(player);
        if (!sins.contains(SinTome.GLUTTONY)) {
            return;
        }
        ItemStack stack = event.getItem();
        FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null) {
            return;
        }
        // 暴食3：食用食物受到相当于其饱食度的伤害
        if (food.getNutrition() > 0) {
            player.hurt(player.damageSources().generic(), food.getNutrition());
        }
        // 暴食6：金质食物获得反胃
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null && itemId.getPath().contains("golden")) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        }
        // 暴食5（食用效果减半）已迁移到 LivingEntityEatMixin：在原版进食写入源头直接减半
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        // 懒惰7：传送后 3 秒内无法行动
        if (event.getEntity() instanceof Player player
                && !player.level().isClientSide
                && SinTomes.equipped(player).contains(SinTome.SLOTH)) {
            lockMotion(player, 60);
        }
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !SinTomes.equipped(player).contains(SinTome.SLOTH)) {
            return;
        }
        SinState state = SinState.of(player);
        state.lastSleepDay = player.level().getDayTime() / 24000L;
        // 懒惰3：起床获得随机的 5 种负面 buff
        List<MobEffectInstance> negatives = List.of(
                new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0),
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 0),
                new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 1200, 0),
                new MobEffectInstance(MobEffects.HUNGER, 1200, 0),
                new MobEffectInstance(MobEffects.CONFUSION, 600, 0),
                new MobEffectInstance(MobEffects.BLINDNESS, 400, 0),
                new MobEffectInstance(MobEffects.POISON, 600, 0),
                new MobEffectInstance(MobEffects.UNLUCK, 2400, 0));
        var shuffled = new ArrayList<>(negatives);
        for (int i = shuffled.size() - 1; i > 0; i--) { // Fisher-Yates（RandomSource 不是 java.util.Random）
            int j = player.getRandom().nextInt(i + 1);
            var tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        for (int i = 0; i < 5 && i < shuffled.size(); i++) {
            player.addEffect(shuffled.get(i));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // 懒惰4：重生后 120 秒全属性 -50%
        Player player = event.getEntity();
        if (player.level().isClientSide || !SinTomes.equipped(player).contains(SinTome.SLOTH)) {
            return;
        }
        SinState state = SinState.of(player);
        int expire = player.tickCount + 2400;
        AttributeModifier mod = new AttributeModifier(SLOTH4_BASE, "book_of_sins:sloth_respawn",
                -0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL);
        for (Attribute attr : List.of(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)) {
            applyMod(player, attr, SLOTH4_BASE, -0.5D);
            state.timedMods.add(new SinState.TimedMod(SLOTH4_BASE, attr, expire));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SinState.remove(event.getEntity().getUUID());
    }

    // ==================== 每 tick 汇总处理 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        Set<SinTome> sins = SinTomes.equipped(player);
        SinState state = SinState.of(player);
        boolean everySecond = player.tickCount % 20 == 0;

        // ---- 暴怒3：叠层过期；暴怒2：自伤结算 ----
        if (state.wrathStacks > 0 && player.tickCount > state.wrathExpire) {
            state.wrathStacks = 0;
            removeMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, WRATH_STACK);
            removeMod(player, com.example.ac_core.attr.AcCoreAttributes.resolve(
                    com.example.ac_core.attr.AcCoreAttributes.DRAW_SPEED.get()), WRATH_STACK);
        }
        if (state.pendingSelfHarm > 0) {
            player.hurt(player.damageSources().generic(), (float) state.pendingSelfHarm);
            state.pendingSelfHarm = 0;
        }

        // ---- 时限属性修正过期（懒惰4 等） ----
        if (!state.timedMods.isEmpty()) {
            List<SinState.TimedMod> expired = new ArrayList<>();
            for (SinState.TimedMod mod : state.timedMods) {
                if (player.tickCount > mod.expireTick()) {
                    AttributeInstance inst = player.getAttribute(mod.attribute());
                    if (inst != null) {
                        inst.removeModifier(mod.id());
                    }
                    expired.add(mod);
                }
            }
            state.timedMods.removeAll(expired);
        }

        if (!everySecond) {
            return;
        }

        // ---- 罪孽资源同步：装备 = +10 罪孽，卸下（死亡掉落）= 回收 ----
        IPlayerResources res = player.getCapability(AcCapabilities.PLAYER_RESOURCES).orElse(null);
        if (res != null) {
            for (SinTome tome : sins) {
                if (!state.grantedSins.contains(tome)) {
                    res.add(ResourceType.SIN, SinTome.SIN_PER_TOME);
                    state.grantedSins.add(tome);
                }
            }
            for (SinTome tome : List.copyOf(state.grantedSins)) {
                if (!sins.contains(tome)) {
                    res.tryPay(ResourceType.SIN, SinTome.SIN_PER_TOME); // 回收罪孽
                    state.grantedSins.remove(tome);
                }
            }
        }

        // ---- 摘下书后的残留清理：阈值类属性修正只在佩戴时对账，摘下必须显式移除 ----
        if (!sins.contains(SinTome.ENVY)) {
            removeMod(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, ENVY6_MOVE);
            removeMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, ENVY7_ATTACK);
            removeMod(player, com.example.ac_core.attr.AcCoreAttributes.resolve(
                    com.example.ac_core.attr.AcCoreAttributes.DRAW_SPEED.get()), ENVY7_DRAW);
        }
        if (!sins.contains(SinTome.SLOTH)) {
            removeMod(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, SLOTH5_MOVE);
            removeMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, SLOTH5_ATTACK);
            state.appliedNoSleepPenalty = -1;
            state.lastSleepDay = Long.MIN_VALUE;
        }

        // ---- 嫉妒6/7：攻速/移速阈值互锁 ----
        if (sins.contains(SinTome.ENVY)) {
            double attackSpeed = value(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            double moveSpeed = value(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            // 嫉妒6：攻速 > 3 → 移速 -50%
            applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                    ENVY6_MOVE, attackSpeed > 3.0D ? -0.5D : 0.0D);
            // 嫉妒7：移速 > 3 → 攻速 -50% + 蓄力速度 -50%（ac_core:draw_speed，自带 Mixin 结算）
            applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                    ENVY7_ATTACK, moveSpeed > 3.0D ? -0.5D : 0.0D);
            applyMod(player, com.example.ac_core.attr.AcCoreAttributes.resolve(
                            com.example.ac_core.attr.AcCoreAttributes.DRAW_SPEED.get()),
                    ENVY7_DRAW, moveSpeed > 3.0D ? -0.5D : 0.0D);
        }

        // ---- 懒惰5：连续不睡觉惩罚（每天 -5% 移速/伤害） ----
        if (sins.contains(SinTome.SLOTH) && state.lastSleepDay != Long.MIN_VALUE) {
            long day = player.level().getDayTime() / 24000L;
            int penaltyDays = (int) Math.min(10, Math.max(0, day - state.lastSleepDay - 1));
            if (penaltyDays != state.appliedNoSleepPenalty) {
                state.appliedNoSleepPenalty = penaltyDays;
                double amount = -0.05D * penaltyDays;
                applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                        SLOTH5_MOVE, amount);
                applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                        SLOTH5_ATTACK, amount);
            }
        }

        // ---- 色欲3：冰冻永续——一旦受到冰冻（细雪）效果，永久保持冰冻状态；着火可解除 ----
        // 着火期间进入「解冻中」状态：不再重新锁存，等火把冰冻值烧到 0 才恢复敏感觉知
        //（修复：着火时残留的部分冰冻值会让锁存下一 tick 立即重新生效，看起来无法解除）
        if (sins.contains(SinTome.LUST)) {
            if (player.isOnFire()) {
                state.frozenLatch = false;
                state.frozenThawed = true;
            }
            if (player.getTicksFrozen() <= 0) {
                state.frozenThawed = false; // 已完全解冻，之后再次被冰冻会重新锁存
            } else if (!state.frozenThawed) {
                state.frozenLatch = true;
            }
            if (state.frozenLatch && !player.isOnFire()) {
                player.setTicksFrozen(140); // 满冰冻（原版封顶），细雪冰伤每 2 秒结算
            }
            // TODO 调试日志（定位解冻问题，确认后移除；仅在状态翻转时输出）
            if (state.frozenLatch != state.frozenThawed) {
                BookOfSins.LOGGER.info("[DEBUG lust_freeze] latch={} thawed={} frozen={} onFire={}",
                        state.frozenLatch, state.frozenThawed, player.getTicksFrozen(), player.isOnFire());
            }
        }

        // ---- 色欲4 补充：佩戴者丢出的金锭在附近时持续保持拾取延迟——猪灵无法捡起地上的金锭以物易物 ----
        if (sins.contains(SinTome.LUST)) {
            for (net.minecraft.world.entity.item.ItemEntity item : player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    player.getBoundingBox().inflate(32.0D))) {
                if (item.getItem().is(net.minecraft.world.item.Items.GOLD_INGOT)
                        && TOSSED_GOLD.containsKey(item.getUUID())) {
                    item.setPickUpDelay(40); // 每秒续一次延迟，玩家在附近时猪灵永远捡不起来
                }
            }
        }

        // ---- 色欲1：周围动物疯狂繁殖（每 5 秒 30% 概率触发一轮，范围内全部成年动物进入求爱） ----
        if (sins.contains(SinTome.LUST) && player.tickCount % 100 == 0
                && player.getRandom().nextFloat() < 0.30F) {
            for (var mob : player.level().getEntitiesOfClass(Animal.class,
                    player.getBoundingBox().inflate(8.0D))) {
                if (mob.getAge() >= 0 && !mob.isInLove()) {
                    mob.setInLove(player);
                }
            }
        }

        // ---- 傲慢2：仇恨范围扩大 5 格（附近怪物 FOLLOW_RANGE 临时修正） ----
        if (sins.contains(SinTome.PRIDE) && player.tickCount % 40 == 0) {
            for (Monster mob : player.level().getEntitiesOfClass(Monster.class,
                    player.getBoundingBox().inflate(32.0D))) {
                AttributeInstance follow = mob.getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
                if (follow != null && follow.getModifier(PRIDE2_MOB_FOLLOW) == null) {
                    follow.addTransientModifier(new AttributeModifier(PRIDE2_MOB_FOLLOW,
                            "book_of_sins:pride_aggro", 5.0D, AttributeModifier.Operation.ADDITION));
                }
            }
        }

        // ---- 色欲2：流血由受击时施加的 ac_core:bleed 效果自行结算，无需额外代码 ----

        // ---- 色欲6：永久随机 buff，每小时重掷 ----
        if (sins.contains(SinTome.LUST)) {
            if (player.tickCount >= state.nextBuffReroll) {
                state.nextBuffReroll = player.tickCount + 72000;
                player.addEffect(randomOf(player, List.of(
                        new MobEffectInstance(MobEffects.REGENERATION, 72000, 0),
                        new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 72000, 0),
                        new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 72000, 0),
                        new MobEffectInstance(MobEffects.WATER_BREATHING, 72000, 0),
                        new MobEffectInstance(MobEffects.NIGHT_VISION, 72000, 0),
                        new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 72000, 0),
                        new MobEffectInstance(MobEffects.DAMAGE_BOOST, 72000, 0),
                        new MobEffectInstance(MobEffects.JUMP, 72000, 0),
                        new MobEffectInstance(MobEffects.ABSORPTION, 72000, 1),
                        new MobEffectInstance(MobEffects.LUCK, 72000, 0))));
            }
            // ---- 色欲7：每 10 分钟损失 100 经验 ----
            if (player.tickCount >= state.nextXpLoss) {
                state.nextXpLoss = player.tickCount + 12000;
                player.giveExperiencePoints(-100);
            }
        }

        // ---- 暴食4：背包食物每分钟 1% 概率消失 10% ----
        if (sins.contains(SinTome.GLUTTONY) && player.tickCount >= state.nextFoodDecay) {
            state.nextFoodDecay = player.tickCount + 1200;
            if (player.getRandom().nextFloat() < 0.01F) {
                List<ItemStack> slots = new ArrayList<>(player.getInventory().items);
                slots.addAll(player.getInventory().offhand);
                for (ItemStack stack : slots) {
                    if (stack.isEdible() && stack.getCount() > 1) {
                        stack.shrink(Math.max(1, stack.getCount() / 10));
                        break; // 每次判定只影响一组
                    }
                }
            }
        }

        // ---- 暴食7：永久饥饿 buff ----
        if (sins.contains(SinTome.GLUTTONY)) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0, true, false));
        }
    }

    // ==================== 工具 ====================

    private static void applyWrathStack(Player player, SinState state) {
        state.wrathStacks++;
        state.wrathExpire = player.tickCount + 60; // 3 秒
        double amount = -0.05D * state.wrathStacks;
        applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                WRATH_STACK, amount);
        // 蓄力速度同步叠层（AA 在场时写在 AA 的 draw_speed 上，保证结算生效）
        applyMod(player, com.example.ac_core.attr.AcCoreAttributes.resolve(
                        com.example.ac_core.attr.AcCoreAttributes.DRAW_SPEED.get()),
                WRATH_STACK, amount);
    }

    private static void applyMod(Player player, Attribute attribute, UUID id, double amount) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        if (amount != 0.0D) {
            inst.addTransientModifier(new AttributeModifier(id, "book_of_sins:sin_curse",
                    amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeMod(Player player, Attribute attribute, UUID id) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }

    private static double value(Player player, Attribute attribute) {
        AttributeInstance inst = player.getAttribute(attribute);
        return inst == null ? 0.0D : inst.getValue();
    }

    private static boolean isBelowHalfDurability(ItemStack stack) {
        return stack.isDamageableItem()
                && stack.getDamageValue() > stack.getMaxDamage() / 2.0D;
    }

    private static void lockMotion(Player player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 3));
    }

    private static MobEffectInstance randomOf(Player player, List<MobEffectInstance> pool) {
        return pool.get(player.getRandom().nextInt(pool.size()));
    }

    private static Player asPlayer(net.minecraft.world.entity.Entity entity) {
        return entity instanceof Player player && !player.level().isClientSide ? player : null;
    }

    private SinCurseEvents() {
    }
}
