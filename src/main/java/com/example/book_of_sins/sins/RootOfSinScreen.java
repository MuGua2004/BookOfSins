package com.example.book_of_sins.sins;

import com.example.book_of_sins.network.C2SSinStart;
import com.example.book_of_sins.network.BosNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 根源之罪·开局选择界面（仅客户端）。
 *
 * <p>布局：暗红面板 + 金色标题 + 一行 8 个开局按钮（0~7）+
 * 七个罪名实时联动高亮 + 消散倒计时。点击数字立即确认并向服务端发包。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RootOfSinScreen extends Screen {

    private static final int PANEL_W = 256;
    private static final int PANEL_H = 160;
    private static final long WARN_MS = 60_000L;
    /** 按钮双状态贴图（32x16：左凸起 / 右扁平，各 16x16）。 */
    private static final net.minecraft.resources.ResourceLocation BUTTON_TEXTURE =
            new net.minecraft.resources.ResourceLocation("book_of_sins", "textures/gui/root_of_sin_button.png");
    /** 面板背景贴图（作者绘制完成后启用，替换上方的渐变绘制）。 */
    private static final net.minecraft.resources.ResourceLocation BG_TEXTURE =
            new net.minecraft.resources.ResourceLocation("book_of_sins", "textures/gui/root_of_sin_bg.png");

    /** 七罪的展示色（与 SinTomeItem 主题一致）。 */
    private static final int[] SIN_COLORS = {
            0xFFD080FF, // 傲慢
            0xFF55FF55, // 嫉妒
            0xFFFF5555, // 暴怒
            0xFF55FFFF, // 懒惰
            0xFFFFFF55, // 贪婪
            0xFFFFAA00, // 暴食
            0xFFFF70C0, // 色欲
    };

    private final long expireAtMs;
    private int hoverCount = -1;
    private int left;
    private int top;

    private RootOfSinScreen(long expireAtMs) {
        super(Component.translatable("gui.book_of_sins.root_of_sin.title"));
        this.expireAtMs = expireAtMs;
    }

    /** 供 DistExecutor 从物品 use() 进入（仅客户端执行）。 */
    public static Runnable open(long expireAtMs) {
        return () -> Minecraft.getInstance().setScreen(new RootOfSinScreen(expireAtMs));
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_W) / 2;
        top = (this.height - PANEL_H) / 2;

        for (int n = 0; n <= 7; n++) {
            addRenderableWidget(new StartButton(left + 10 + n * 29, top + 46, n));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        // 面板：作者绘制的符文边框贴图（256x160，与面板同尺寸，1:1 blit）
        graphics.blit(BG_TEXTURE, left, top, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        drawCentered(graphics, Component.translatable("gui.book_of_sins.root_of_sin.title"),
                top + 12, 0xFFFFC84A, true);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 七罪预览行：悬停的数字 N 点亮前 N 个罪名（主题色），其余暗化
        for (int i = 0; i < 7; i++) {
            int color = i < hoverCount ? SIN_COLORS[i] : 0xFF574A4A;
            graphics.drawString(font, Component.literal(SinTome.values()[i].display()),
                    left + 10 + i * 33, top + 82, color, false);
        }

        // 消散倒计时
        long remaining = Math.max(0L, expireAtMs - System.currentTimeMillis());
        String time = String.format("%d:%02d", remaining / 60_000L, (remaining % 60_000L) / 1000L);
        int timeColor = remaining <= WARN_MS ? 0xFFFF4040 : 0xFF9AD1D1;
        drawCentered(graphics, Component.translatable("gui.book_of_sins.root_of_sin.remaining", time),
                top + PANEL_H - 16, timeColor, false);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int y, int color, boolean shadow) {
        graphics.drawCenteredString(font, text, width / 2, y, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 开局按钮：暗底 + 悬停提亮 + 金色描边（悬停时）。 */
    private final class StartButton extends Button {

        private final int count;

        private StartButton(int x, int y, int count) {
            super(x, y, 24, 24, Component.literal(String.valueOf(count)),
                    b -> { }, Button.DEFAULT_NARRATION);
            this.count = count;
        }

        @Override
        public void onPress() {
            BosNetwork.CHANNEL.sendToServer(new C2SSinStart(this.count));
            onClose();
        }

        private void confirm(int n) {
            BosNetwork.CHANNEL.sendToServer(new C2SSinStart(n));
            onClose();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            boolean pressed = hovered && Minecraft.getInstance().mouseHandler.isLeftPressed();
            // 32x16 双状态切片：左 16x16 = 凸起（悬停），右 16x16 = 扁平（未悬停/按下）
            int ox = getX() + (width - 16) / 2;
            int oy = getY() + (height - 16) / 2 + (pressed ? 1 : 0);
            int u = hovered && !pressed ? 0 : 16;
            graphics.blit(BUTTON_TEXTURE, ox, oy, u, 0, 16, 16, 32, 16);
            graphics.drawCenteredString(RootOfSinScreen.this.font, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2 + (pressed ? 1 : 0),
                    hovered ? 0xFFFFC84A : 0xFFC9B8B8);
            if (isHoveredOrFocused()) {
                hoverCount = count;
            }
        }
    }
}
