package com.example.book_of_sins;

import com.example.book_of_sins.curios.BosCurios;
import com.example.book_of_sins.curios.BosCuriosSlots;
import com.example.book_of_sins.registry.BosCreativeTabs;
import com.example.book_of_sins.sins.SinTomes;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 罪孽之书（Book of Sins）—— 独立的诅咒饰品 mod（1.20.1）。
 *
 * <p>内容为七宗罪套装：根源之罪开局道具、七本罪孽之书（49 条诅咒）、
 * 七本逆转之书、七个根源之印。基于 AC Core（属性 / tooltip / 事件挂点）。</p>
 *
 * <p>本 mod 由星界传说（star_legends）的 sins 内容独立而来，
 * 槽位沿用 Curios 的 tome（典籍 7 格）与 god_seal（神印 7 格）。</p>
 */
@Mod(BookOfSins.MOD_ID)
public class BookOfSins {

    /** 模组 ID，同时是本 mod 全部内容的命名空间前缀（book_of_sins:...）。 */
    public static final String MOD_ID = "book_of_sins";

    /** 模组日志器。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    public BookOfSins(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        com.example.book_of_sins.effect.BosMobEffects.EFFECTS.register(modEventBus);
        com.example.book_of_sins.charm.BosCharms.ITEMS.register(modEventBus);
        com.example.book_of_sins.recipe.ModRecipeSerializers.SERIALIZERS.register(modEventBus);
        SinTomes.ITEMS.register(modEventBus);
        com.example.book_of_sins.sins.RootSeals.ITEMS.register(modEventBus);
        com.example.book_of_sins.sins.ReversalTomes.ITEMS.register(modEventBus);
        BosCreativeTabs.TABS.register(modEventBus);

        // 佩戴提供方：把饰品物品映射为 ac_core 的饰品定义（Curios 饰品栏）
        if (BosCurios.isLoaded()) {
            BosCurios.registerProvider();
        }

        // 槽位注册：典籍 7 格 + 神印 7 格（Curios IMC）
        modEventBus.addListener(BosCuriosSlots::onEnqueueIMC);

        // 全局战利品修改器（贪婪：战利品箱 ±）+ 进食营养减半钩子（暴食#5）
        com.example.book_of_sins.loot.BosLootModifiers.SERIALIZERS.register(modEventBus);

        // 开局档位奖励（1~7 档，高档含低档）
        com.example.book_of_sins.sins.SinStartTierRewards.register();
        com.example.ac_core.event.AcHookContext.NUTRITION_HALF =
                p -> SinTomes.equipped(p).contains(com.example.book_of_sins.sins.SinTome.GLUTTONY);
        // 逆转之书-暴食#7：饱食度满时依旧可以进食
        com.example.ac_core.event.AcHookContext.FORCE_EAT =
                p -> com.example.book_of_sins.sins.ReversalTomes.isEquipped(p,
                        com.example.book_of_sins.sins.SinTome.GLUTTONY);

        LOGGER.info("[Book of Sins] Initialized (requires AC Core)");

        // 网络（根源之罪的开局选择包等）
        com.example.book_of_sins.network.BosNetwork.register();

        // 根源之罪：首次进世界发放
        MinecraftForge.EVENT_BUS.addListener(
                com.example.book_of_sins.sins.RootOfSinItem::onPlayerLoggedIn);
    }
}
