package com.example.book_of_sins.effect;

import com.example.ac_core.mixin.MobEffectInstanceAccessor;
import com.example.book_of_sins.BookOfSins;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 星界三祝福（星赐 / 星哲 / 星赎）。
 *
 * <p>全部不走属性修改器（避免药水 tooltip 拉出一长串属性行），
 * 伤害与治疗增益在事件层结算，周期回复在效果自身的 tick 里结算。</p>
 *
 * <ul>
 *   <li><b>星赐</b>：近战伤害 +30%；每秒回复 2 点生命值；</li>
 *   <li><b>星哲</b>：非近战伤害 +30%；每 10 秒回复 25% 最大生命值；</li>
 *   <li><b>星赎</b>：受到的治疗效果 +30%；每 10 秒令身上所有效果时长 +1 秒
 *       （只延长有限时长，无限效果不动——增速远低于自然衰减，不会无限）。</li>
 * </ul>
 */
public final class BosMobEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, BookOfSins.MOD_ID);

    public static final RegistryObject<MobEffect> STAR_BLESSING =
            EFFECTS.register("star_blessing", StarBlessingEffect::new);
    public static final RegistryObject<MobEffect> STAR_SAGE =
            EFFECTS.register("star_sage", StarSageEffect::new);
    public static final RegistryObject<MobEffect> STAR_REDEMPTION =
            EFFECTS.register("star_redemption", StarRedemptionEffect::new);

    // ==================== 星赐 ====================

    public static class StarBlessingEffect extends MobEffect {

        public StarBlessingEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x5BC8F5);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide && entity.tickCount % 20 == 0) {
                entity.heal(2.0F);
            }
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }
    }

    // ==================== 星哲 ====================

    public static class StarSageEffect extends MobEffect {

        public StarSageEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x8E7CC3);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide && entity.tickCount % 200 == 0) {
                entity.heal(entity.getMaxHealth() * 0.25F);
            }
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }
    }

    // ==================== 星赎 ====================

    public static class StarRedemptionEffect extends MobEffect {

        public StarRedemptionEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xF2C14E);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.level().isClientSide || entity.tickCount % 200 != 0) {
                return;
            }
            // 所有已激活效果时长 +1 秒（20 tick）；无限时长效果不动
            for (MobEffectInstance instance : entity.getActiveEffects()) {
                if (instance.isInfiniteDuration() || instance.getDuration() <= 0) {
                    continue;
                }
                ((MobEffectInstanceAccessor) (Object) instance)
                        .ac_core$setDuration(instance.getDuration() + 20);
            }
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }
    }

    // ==================== 事件结算（伤害/治疗增益，无属性修改器） ====================

    @Mod.EventBusSubscriber(modid = BookOfSins.MOD_ID)
    public static class StarEvents {

        /** 星赐：近战（直接伤害源=攻击者本体）伤害 +30%；星哲：非近战伤害 +30%。 */
        @SubscribeEvent
        public static void onHurt(LivingHurtEvent event) {
            if (event.getEntity().level().isClientSide) {
                return;
            }
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
                return;
            }
            boolean melee = event.getSource().getDirectEntity() == attacker;
            if (melee && attacker.hasEffect(STAR_BLESSING.get())) {
                event.setAmount(event.getAmount() * 1.3F);
            } else if (!melee && attacker.hasEffect(STAR_SAGE.get())) {
                event.setAmount(event.getAmount() * 1.3F);
            }
        }

        /** 星赎：受到的治疗效果 +30%。 */
        @SubscribeEvent
        public static void onHeal(LivingHealEvent event) {
            LivingEntity entity = event.getEntity();
            if (!entity.level().isClientSide && entity.hasEffect(STAR_REDEMPTION.get())) {
                event.setAmount(event.getAmount() * 1.3F);
            }
        }
    }

    private BosMobEffects() {
    }
}