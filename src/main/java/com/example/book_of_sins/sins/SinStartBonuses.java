package com.example.book_of_sins.sins;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 开局资源钩子（预留接口）。
 *
 * <p>不同开局（0~7 本罪孽之书）配套的起始资源方案由作者后续提供；
 * 到时实现 {@link Bonus} 并调用 {@link #register} 即可，
 * {@link RootOfSinItem#applyChoice} 会自动在装配完成后逐个执行。</p>
 */
public final class SinStartBonuses {

    /** 开局资源发放钩子：tomeCount = 玩家选择背负的罪孽之书数量。 */
    public interface Bonus {
        void apply(ServerPlayer player, int tomeCount);
    }

    private static final List<Bonus> BONUSES = new ArrayList<>();

    /** 注册一个开局资源方案（可注册多个，按注册顺序执行）。 */
    public static void register(Bonus bonus) {
        BONUSES.add(bonus);
    }

    /** 装配完成后调用（当前无任何已注册方案——等待作者的资源方案）。 */
    public static void apply(ServerPlayer player, int tomeCount) {
        for (Bonus bonus : BONUSES) {
            bonus.apply(player, tomeCount);
        }
    }

    /** 前缀预览：前 N 本罪孽之书的名字（「傲慢、嫉妒、…」），供界面与消息使用。 */
    public static String previewNames(int count) {
        SinTome[] order = SinTome.values();
        int n = Math.min(count, order.length);
        if (n == 0) {
            return "∅";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(order[i].display());
        }
        return sb.toString();
    }

    private SinStartBonuses() {
    }
}
