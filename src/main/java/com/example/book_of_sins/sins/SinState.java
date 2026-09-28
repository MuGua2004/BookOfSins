package com.example.book_of_sins.sins;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家的罪孽状态（内存态，登出清除；各项诅咒的计时器 / 计数器 / 临时修正）。
 */
public final class SinState {

    private static final Map<java.util.UUID, SinState> STATES = new HashMap<>();

    public static SinState of(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new SinState());
    }

    public static void remove(java.util.UUID uuid) {
        STATES.remove(uuid);
    }

    /** 带时限的属性修正（懒惰#4 重生 debuff 等使用）。 */
    public record TimedMod(java.util.UUID id, Attribute attribute, int expireTick) {
    }

    // ---- 傲慢 ----
    public long lastHurtGameTime = Long.MIN_VALUE;   // #6 受击后 1 秒禁疗

    // ---- 暴怒 ----
    public int wrathStacks;                          // #3 叠层
    public int wrathExpire;                          // #3 叠层过期 tick
    public double pendingSelfHarm;                   // #2 下一 tick 结算的自伤

    // ---- 懒惰 ----
    public long lastSleepDay = Long.MIN_VALUE;       // #5 最近一次睡觉的 day
    public int appliedNoSleepPenalty = -1;           // #5 已应用的惩罚天数（缓存）
    public final List<TimedMod> timedMods = new ArrayList<>(); // #4 等时限修正

    // ---- 色欲 ----
    public int combatUntil;                          // #5 战斗标记
    public int bleedUntil;                           // #2 流血截止 tick（近似：凋零）
    public int nextBuffReroll;                       // #6 永久随机 buff 重掷
    public int nextXpLoss;                           // #7 经验扣除计时
    public boolean frozenLatch;                      // #3 冰冻永续：一旦被冰冻即锁存
    public boolean frozenThawed;                     // #3 火烤解冻中：完全解冻前不再重新锁存

    // ---- 贪婪 / 暴食 ----
    public int nextFoodDecay;                        // 暴食#4 食物消失计时

    // ---- 罪孽资源同步 ----
    public final Set<SinTome> grantedSins = EnumSet.noneOf(SinTome.class);
}
