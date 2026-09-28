package com.example.book_of_sins.sins;

import com.example.ac_core.attr.AcCoreAttributes;
import com.example.ac_core.event.AcLootEnchantLevelEvent;
import com.example.book_of_sins.BookOfSins;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 七枚根源之印的被动结算。
 *
 * <p>属性类效果（移速/暴伤/生命/幸运/韧性/攻速/蓄力等）统一走「按秒对账」的
 * 瞬时属性修正：佩戴时每秒重写、摘下自动清零（沿用罪孽书残留清理的教训）。</p>
 *
 * <p>非属性类效果：</p>
 * <ul>
 *   <li>傲慢：伤害 ×1.1 且不低于 5 点（仅原始伤害 &gt; 0 时兜底，避免救回 0 伤）；</li>
 *   <li>嫉妒：疾跑中受伤钳制到 50% 最大生命值；</li>
 *   <li>暴怒：造成伤害时对目标施加 虚弱5（5 秒）；</li>
 *   <li>贪婪：幸运值 → 暴击率/时运/抢夺换算（时运抢夺走 ac_core 附魔等级钩子）；</li>
 *   <li>暴食：食用食物种类持久化计数（玩家 NBT），按阶梯换算多种加成；</li>
 *   <li>色欲：独特掉落物解锁（TODO：内容未开放，钩子预留）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BookOfSins.MOD_ID)
public final class RootSealEvents {

    // ---- 属性修正 UUID（每枚印每条修正独立） ----
    private static final UUID ENVY_SPEED = UUID.fromString("9a4c1d00-0001-4a1b-9c2d-100000000001");
    private static final UUID WRATH_CRITD = UUID.fromString("9a4c1d00-0002-4a1b-9c2d-100000000002");
    private static final UUID SLOTH_HP = UUID.fromString("9a4c1d00-0003-4a1b-9c2d-100000000003");
    private static final UUID SLOTH_MULT = UUID.fromString("9a4c1d00-0004-4a1b-9c2d-100000000004");
    private static final UUID GREED_LUCK = UUID.fromString("9a4c1d00-0005-4a1b-9c2d-100000000005");
    private static final UUID GREED_CRIT = UUID.fromString("9a4c1d00-0006-4a1b-9c2d-100000000006");
    private static final UUID GLUTTONY_TUFF = UUID.fromString("9a4c1d00-0007-4a1b-9c2d-100000000007");
    private static final UUID GLUTTONY_HP = UUID.fromString("9a4c1d00-0008-4a1b-9c2d-100000000008");
    private static final UUID GLUTTONY_ARMOR = UUID.fromString("9a4c1d00-0009-4a1b-9c2d-100000000009");
    private static final UUID GLUTTONY_AS = UUID.fromString("9a4c1d00-000a-4a1b-9c2d-10000000000a");
    private static final UUID GLUTTONY_DS = UUID.fromString("9a4c1d00-000b-4a1b-9c2d-10000000000b");
    private static final UUID GLUTTONY_LUCK = UUID.fromString("9a4c1d00-000c-4a1b-9c2d-10000000000c");
    private static final UUID LUST_AS = UUID.fromString("9a4c1d00-000d-4a1b-9c2d-10000000000d");
    private static final UUID LUST_DS = UUID.fromString("9a4c1d00-000e-4a1b-9c2d-10000000000e");

    /** 每玩家已知的暴食食物种类数（变化时才重写属性，减少噪音）。 */
    private static final Map<UUID, Integer> LAST_FOOD_COUNT = new ConcurrentHashMap<>();

    // ==================== 伤害方向 ====================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        // 暴怒：造成伤害时施加虚弱5（等级 5 / 持续 6 秒）
        if (event.getSource().getEntity() instanceof Player attacker
                && attacker != event.getEntity()
                && RootSeals.isWearing(attacker, SinTome.WRATH)) {
            event.getEntity().addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, 120, 4, false, true));
        }
        // 嫉妒：疾跑中受到的伤害不超过 50% 最大生命值
        if (event.getEntity() instanceof Player victim
                && RootSeals.isWearing(victim, SinTome.ENVY) && victim.isSprinting()) {
            float cap = victim.getMaxHealth() * 0.5F;
            if (event.getAmount() > cap) {
                event.setAmount(cap);
            }
        }
    }

    /**
     * 最终伤害独立乘区（LivingDamage 阶段：护甲/魔抗等常规折算之后、落血之前）。
     *
     * <p>对所有伤害类型生效；真理伤害不走实际伤害管线（ac_core 直削最大生命），
     * 天然不被本乘区影响。</p>
     */
    @SubscribeEvent
    public static void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker)
                || attacker == event.getEntity()) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        boolean pride = RootSeals.isWearing(attacker, SinTome.PRIDE);
        float mult = 1.0F;
        // 傲慢：最终伤害 +10%
        if (pride) {
            mult += 0.10F;
        }
        // 暴食：每 50 种食物 +1% 最终伤害
        if (RootSeals.isWearing(attacker, SinTome.GLUTTONY)) {
            mult += 0.01F * (foodVariety(attacker) / 50);
        }
        if (mult != 1.0F) {
            amount *= mult;
        }
        // 傲慢：伤害不低于 5 点（仅对原伤害 > 0 生效，避免救回丢失的 0 伤）
        if (pride && amount < 5.0F) {
            amount = 5.0F;
        }
        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
    }

    // ==================== 贪婪：幸运 → 时运/抢夺 ====================

    @SubscribeEvent
    public static void onLootEnchant(AcLootEnchantLevelEvent event) {
        Player player = event.getPlayer();
        if (player == null || !RootSeals.isWearing(player, SinTome.GREED)) {
            return;
        }
        var luck = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        int bonus = luck == null ? 0 : (int) Math.floor(luck.getValue());
        if (bonus > 0) {
            event.setLevel(event.getLevel() + bonus);
        }
    }

    // ==================== 暴食：食用食物种类计数 ====================

    @SubscribeEvent
    public static void onEatFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide
                || !RootSeals.isWearing(player, SinTome.GLUTTONY)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack.getItem().getFoodProperties(stack, player) == null) {
            return;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return;
        }
        CompoundTag data = player.getPersistentData().getCompound("book_of_sins");
        ListTag list = data.getList("foods_eaten", Tag.TAG_STRING);
        StringTag entry = StringTag.valueOf(itemId.toString());
        if (!list.contains(entry)) {
            list.add(entry);
            data.put("foods_eaten", list);
            player.getPersistentData().put("book_of_sins", data);
        }
    }

    // ==================== 按秒对账：属性修正 ====================

    /** 诊断：dump 玩家 Curios 全部槽位内容。 */
    private static String dumpCurios(Player player) {
        StringBuilder sb = new StringBuilder("[");
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            boolean first = true;
            for (var entry : handler.getCurios().entrySet()) {
                var stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack s = stacks.getStackInSlot(i);
                    if (!s.isEmpty()) {
                        if (!first) {
                            sb.append(", ");
                        }
                        first = false;
                        sb.append(entry.getKey()).append('#').append(i).append('=')
                                .append(ForgeRegistries.ITEMS.getKey(s.getItem()));
                    }
                }
            }
        });
        return sb.append(']').toString();
    }

    /**
     * 登录时把贪婪箱 GLM 回填进 Forge 管理器——Forge 在重进存档
     * （datapack 重载）后会丢失我们的全局修改器，此处绕过。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("net.minecraftforge.common.ForgeInternalHandler");
            var method = clazz.getDeclaredMethod("getLootModifierManager");
            method.setAccessible(true);
            var manager = method.invoke(null);
            var mapField = manager.getClass().getDeclaredField("registeredLootModifiers");
            mapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<net.minecraft.resources.ResourceLocation,
                    net.minecraftforge.common.loot.IGlobalLootModifier>) mapField.get(manager);
            map.put(new net.minecraft.resources.ResourceLocation(BookOfSins.MOD_ID, "greed_chest"),
                    com.example.book_of_sins.loot.GreedChestLootModifier.INSTANCE);
            map.put(new net.minecraft.resources.ResourceLocation(BookOfSins.MOD_ID, "greed_chest_plus"),
                    com.example.book_of_sins.loot.ReversalChestBarterLootModifiers.GreedChestPlus.INSTANCE);
            map.put(new net.minecraft.resources.ResourceLocation(BookOfSins.MOD_ID, "lust_barter"),
                    com.example.book_of_sins.loot.ReversalChestBarterLootModifiers.LustBarter.INSTANCE);
        } catch (Throwable t) {
            BookOfSins.LOGGER.warn("[Star Legends] GLM 回填失败：{}", t.toString());
        }
    }

    /**
     * 色欲·独特掉落：佩戴根源之印-色欲时，按类别击杀生物有概率掉落独特材料。
     *
     * <p>概率（各掉落一致）= 1% × 1.1^抢夺 + 1% × 幸运（抢夺乘区、幸运加算）。
     * 优先级：亡灵 &gt; 村民 &gt; 水生 &gt; 下界 &gt; 末地（重叠类别取前者）。</p>
     *
     * <ul>
     *   <li>亡灵之骸：亡灵生物（原版 MobType.UNDEAD 判定）；</li>
     *   <li>翡翠心晶：村民（含流浪商人）；</li>
     *   <li>渊海遗蜕：水生生物（WATER_CREATURE / WATER_AMPHIBIAN 类别）；</li>
     *   <li>灼云之焰：下界维度生物；</li>
     *   <li>虚空之心：末地维度生物。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getSource().getEntity() instanceof Player player)
                || !RootSeals.isWearing(player, SinTome.LUST)) {
            return;
        }
        var victim = event.getEntity();
        ItemStack dropStack = uniqueDropFor(victim);
        if (dropStack == null) {
            return;
        }
        int looting = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING,
                        player.getMainHandItem());
        double chance = 0.01D * Math.pow(1.1D, looting) + 0.01D * player.getLuck();
        if (player.getRandom().nextFloat() < chance) {
            var drop = new net.minecraft.world.entity.item.ItemEntity(victim.level(),
                    victim.getX(), victim.getY() + 0.5D, victim.getZ(), dropStack);
            victim.level().addFreshEntity(drop);
            BookOfSins.LOGGER.info("[Star Legends] 色欲·独特掉落：{} {}（概率 {}%）",
                    player.getName().getString(), dropStack.getHoverName().getString(),
                    Math.round(chance * 10000) / 100.0);
        }
    }

    /** 按击杀类别取独特掉落物；无匹配返回 null（优先级：亡灵 &gt; 村民 &gt; 水生 &gt; 下界 &gt; 末地）。 */
    private static ItemStack uniqueDropFor(net.minecraft.world.entity.LivingEntity victim) {
        if (victim.getMobType() == net.minecraft.world.entity.MobType.UNDEAD) {
            return new ItemStack(RootSeals.UNDEAD_REMAINS.get());
        }
        if (victim instanceof net.minecraft.world.entity.npc.AbstractVillager) {
            return new ItemStack(RootSeals.EMERALD_HEART_CRYSTAL.get());
        }
        var category = victim.getType().getCategory();
        if (category == net.minecraft.world.entity.MobCategory.WATER_CREATURE
                || category == net.minecraft.world.entity.MobCategory.WATER_AMBIENT
                || category == net.minecraft.world.entity.MobCategory.UNDERGROUND_WATER_CREATURE
                || category == net.minecraft.world.entity.MobCategory.AXOLOTLS) {
            return new ItemStack(RootSeals.ABYSSAL_MOLT.get());
        }
        if (victim.level().dimension() == net.minecraft.world.level.Level.NETHER) {
            return new ItemStack(RootSeals.CINDERCLOUD_FLAME.get());
        }
        if (victim.level().dimension() == net.minecraft.world.level.Level.END) {
            return new ItemStack(RootSeals.VOID_HEART.get());
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (player.tickCount % 20 != 0) {
            return;
        }

        // 没书弹出：佩戴中的印失去对应罪孽之书 → 强制弹回背包/掉落
        for (SinTome tome : SinTome.values()) {
            if (RootSeals.isWearing(player, tome) && !SinTomes.equipped(player).contains(tome)) {
                RootSeals.popOut(player, tome);
            }
        }

        // 嫉妒：+50% 移动速度（乘区）
        applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                ENVY_SPEED, RootSeals.isWearing(player, SinTome.ENVY) ? 0.5D : 0.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        // 暴怒：暴击伤害 +20%（加值）
        applyMod(player, AcCoreAttributes.CRIT_DAMAGE.get(),
                WRATH_CRITD, RootSeals.isWearing(player, SinTome.WRATH) ? 0.2D : 0.0D,
                AttributeModifier.Operation.ADDITION);

        // 懒惰：翻倍是独立乘区（作用于印自身的 +10 之前的生命值）——
        // 乘区内 +5 经过 ×2 恰好等于乘区外 +10，实现「先翻倍再加 10」
        boolean sloth = RootSeals.isWearing(player, SinTome.SLOTH);
        applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                SLOTH_HP, sloth ? 5.0D : 0.0D, AttributeModifier.Operation.ADDITION);
        applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                SLOTH_MULT, sloth ? 1.0D : 0.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);

        // 贪婪：+1 幸运（加值）；每 1 幸运 → 1% 暴击率（加值）
        boolean greed = RootSeals.isWearing(player, SinTome.GREED);
        applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.LUCK,
                GREED_LUCK, greed ? 1.0D : 0.0D, AttributeModifier.Operation.ADDITION);
        double luck = value(player, net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        applyMod(player, AcCoreAttributes.CRIT_CHANCE.get(),
                GREED_CRIT, greed && luck > 0.0D ? luck * 0.01D : 0.0D,
                AttributeModifier.Operation.ADDITION);

        // 暴食：护甲韧性 +5（加值）；食物种类阶梯加成
        // （暴食#5 食用效果减半已迁移到 LivingEntityEatMixin，见 sins 包）
        boolean gluttony = RootSeals.isWearing(player, SinTome.GLUTTONY);
        int foods = gluttony ? foodVariety(player) : 0;
        Integer last = LAST_FOOD_COUNT.get(player.getUUID());
        if (gluttony || (last != null && last != 0)) {
            applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
                    GLUTTONY_TUFF, gluttony ? 5.0D : 0.0D, AttributeModifier.Operation.ADDITION);
            applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                    GLUTTONY_HP, foods, AttributeModifier.Operation.ADDITION);
            applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                    GLUTTONY_ARMOR, foods / 5.0D, AttributeModifier.Operation.ADDITION);
            Attribute drawSpeed = AcCoreAttributes.resolve(AcCoreAttributes.DRAW_SPEED.get());
            applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                    GLUTTONY_AS, 0.01D * (foods / 10), AttributeModifier.Operation.MULTIPLY_TOTAL);
            applyMod(player, drawSpeed,
                    GLUTTONY_DS, 0.01D * (foods / 10), AttributeModifier.Operation.MULTIPLY_TOTAL);
            applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.LUCK,
                    GLUTTONY_LUCK, foods / 100.0D, AttributeModifier.Operation.ADDITION);
            LAST_FOOD_COUNT.put(player.getUUID(), foods);
        }

        // 色欲：+10% 攻击速度 / 蓄力速度（乘区）
        boolean lust = RootSeals.isWearing(player, SinTome.LUST);
        applyMod(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                LUST_AS, lust ? 0.1D : 0.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
        applyMod(player, AcCoreAttributes.resolve(AcCoreAttributes.DRAW_SPEED.get()),
                LUST_DS, lust ? 0.1D : 0.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    // ==================== 工具 ====================

    /** 暴食：已食用过的食物种类数（持久化在玩家 NBT）。 */
    private static int foodVariety(Player player) {
        CompoundTag data = player.getPersistentData().getCompound("book_of_sins");
        return data.getList("foods_eaten", Tag.TAG_STRING).size();
    }

    /** 幂等的瞬时属性修正：先移除再按需写回（amount = 0 即纯移除）。 */
    private static void applyMod(Player player, Attribute attribute, UUID id, double amount,
                                 AttributeModifier.Operation operation) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        if (amount != 0.0D) {
            inst.addTransientModifier(new AttributeModifier(id, "book_of_sins:root_seal",
                    amount, operation));
        }
    }

    private static double value(Player player, Attribute attribute) {
        AttributeInstance inst = player.getAttribute(attribute);
        return inst == null ? 0.0D : inst.getValue();
    }

    private RootSealEvents() {
    }
}
