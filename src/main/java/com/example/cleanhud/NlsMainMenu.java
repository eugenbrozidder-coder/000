package com.example.cleanhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;

/** Animated NLS glass main menu. Vanilla destinations and actions remain intact. */
public final class NlsMainMenu extends Screen {
    private final long born = System.currentTimeMillis();
    private int panelX, panelY, panelW;
    private static final int CYAN = 0xFF54DDF5;
    private static final int WHITE = 0xFFF1F7FA;
    private static final int MUTED = 0xFF9AAAB7;
    private static final int GLASS = 0xA914202B;
    private static final int GLASS_HOVER = 0xD0223C4B;

    public NlsMainMenu() { super(Text.literal("NLS Visuals")); }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        float t = (System.currentTimeMillis() - born) / 1000f;
        drawBackdrop(c, t);
        panelW = Math.min(390, width - 36);
        panelX = (width - panelW) / 2;
        panelY = Math.max(92, height / 2 - 66);

        // NLS wordmark and soft cyan orbital accents.
        c.fill(28, 28, 31, 104, CYAN);
        c.drawTextWithShadow(textRenderer, "NLS", 43, 34, WHITE);
        c.drawText(textRenderer, "C L E A N   H U D   C L I E N T", 44, 51, CYAN, false);
        c.drawText(textRenderer, "VISUALS  /  1.21.4", 44, 65, MUTED, false);
        int orbit = (int)(Math.sin(t * 1.2f) * 7);
        c.fill(40, 78 + orbit, 118, 79 + orbit, 0xAA54DDF5);
        c.fill(112, 28 + orbit, 172, 29 + orbit, 0x6654DDF5);

        c.fill(panelX - 12, panelY - 18, panelX + panelW + 12, panelY + 216, 0x7E081018);
        c.fill(panelX - 12, panelY - 18, panelX + panelW + 12, panelY - 16, 0xAA54DDF5);
        c.drawCenteredTextWithShadow(textRenderer, "WELCOME BACK", width / 2, panelY - 5, MUTED);

        button(c, mouseX, mouseY, 0, "Singleplayer", "Одиночный мир", t);
        button(c, mouseX, mouseY, 1, "Multiplayer", "Сетевая игра", t);
        button(c, mouseX, mouseY, 2, "Settings", "Настройки клиента", t);
        button(c, mouseX, mouseY, 3, "Quit game", "Выход из игры", t);

        c.fill(18, height - 35, 188, height - 12, 0x94111D27);
        c.drawText(textRenderer, "Minecraft 1.21.4", 28, height - 29, CYAN, false);
        c.drawText(textRenderer, "NLS Visuals  •  Fabric", 28, height - 18, MUTED, false);
        c.drawText(textRenderer, "RIGHT SHIFT  /  CLICKGUI", width - 160, height - 21, MUTED, false);
        super.render(c, mouseX, mouseY, delta);
    }

    private void drawBackdrop(DrawContext c, float t) {
        c.fill(0, 0, width, height, 0xFF070C13);
        // Night sky and distant blocky landscape.
        c.fill(0, 0, width, height / 2, 0xFF0A1420);
        for (int i = 0; i < 38; i++) {
            int x = Math.floorMod(i * 173 + (int)(t * (i % 3 + 1) * 5), Math.max(1, width));
            int y = 12 + Math.floorMod(i * 67, Math.max(1, height / 2));
            c.fill(x, y, x + 2, y + 2, 0x665DCAE0);
        }
        int moonX = width * 3 / 4, moonY = height / 4;
        c.fill(moonX - 23, moonY - 23, moonX + 23, moonY + 23, 0x332D9BB0);
        c.fill(moonX - 15, moonY - 15, moonX + 15, moonY + 15, 0xAA9BC6D0);
        c.fill(moonX - 11, moonY - 11, moonX + 11, moonY + 11, 0xFFD5E5E8);
        int horizon = height * 2 / 3;
        c.fill(0, horizon, width, height, 0xFF08131B);
        // Blocky cliffs framing a reflective dark lake.
        for (int i = 0; i < 10; i++) {
            int h = 24 + (i * 19 % Math.max(25, height / 3));
            int w = Math.max(18, width / 15);
            c.fill(i * w - 10, horizon - h, i * w + w, height, 0xFF0C1921);
            c.fill(width - i * w - w, horizon - h / 2, width - i * w, height, 0xFF0A171F);
        }
        for (int i = 0; i < 16; i++) {
            int y = horizon + 8 + i * 9;
            int shift = (int)(Math.sin(t * .8f + i) * 10);
            c.fill(width / 2 - 65 + shift, y, width / 2 + 65 + shift, y + 1, 0x223CB4C9);
        }
        c.fill(0, 0, width, height, 0x44101822);
    }

    private void button(DrawContext c, int mx, int my, int index, String label, String sub, float t) {
        int y = panelY + index * 49 + 18;
        boolean hover = mx >= panelX && mx <= panelX + panelW && my >= y && my <= y + 40;
        int x = panelX;
        c.fill(x, y, x + panelW, y + 40, hover ? GLASS_HOVER : GLASS);
        c.fill(x, y, x + 2, y + 40, hover ? CYAN : 0x553B7182);
        if (hover) {
            int pulse = (int)(Math.sin(t * 4) * 18 + 38);
            c.fill(x + 2, y, x + panelW, y + 1, (pulse << 24) | 0x54DDF5);
        }
        c.drawTextWithShadow(textRenderer, label, x + 16, y + 7, hover ? WHITE : 0xFFDCE6EC);
        c.drawText(textRenderer, sub, x + 16, y + 22, MUTED, false);
        c.drawText(textRenderer, "›", x + panelW - 20, y + 10, hover ? CYAN : MUTED, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < 4; i++) {
                int y = panelY + i * 49 + 18;
                if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= y && mouseY <= y + 40) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    switch (i) {
                        case 0 -> mc.setScreen(new SelectWorldScreen(this));
                        case 1 -> mc.setScreen(new MultiplayerScreen(this));
                        case 2 -> mc.setScreen(new OptionsScreen(this, mc.options));
                        case 3 -> mc.scheduleStop();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void close() { if (client != null) client.setScreen(new TitleScreen()); }
    @Override public boolean shouldPause() { return false; }
}
