package com.example.cleanhud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * NLS Visuals glass-style ClickGUI.
 * Drawn manually to keep the dark translucent look consistent across vanilla themes.
 */
public final class CleanHudClickGui extends Screen {
    private enum Tab { HUD, VISUAL, PERFORMANCE, FONTS }
    private Tab tab = Tab.HUD;
    private int left, top, panelW, panelH;
    private static final int BG = 0xE910141D;
    private static final int PANEL = 0xB91B2230;
    private static final int PANEL_HOVER = 0xD1263345;
    private static final int MUTED = 0xFF8793A5;
    private static final int WHITE = 0xFFF0F4FA;
    private static final int CYAN = 0xFF54D6F5;

    public CleanHudClickGui() { super(Text.literal("NLS Visuals")); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x99060A10);
        panelW = Math.min(520, width - 28);
        panelH = Math.min(330, height - 28);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        ctx.fill(left + 3, top + 4, left + panelW + 3, top + panelH + 4, 0x55000000);
        ctx.fill(left, top, left + panelW, top + panelH, BG);
        ctx.fill(left, top, left + panelW, top + 2, CYAN);
        ctx.fill(left, top + 2, left + 112, top + panelH, 0xD9151B27);
        ctx.drawText(textRenderer, "NLS", left + 18, top + 16, WHITE, true);
        ctx.drawText(textRenderer, "VISUALS  /  CLIENT", left + 18, top + 29, MUTED, false);
        ctx.fill(left + 14, top + 47, left + 98, top + 48, 0x553C9DB5);

        String[] names = {"HUD", "VISUAL", "PERFORMANCE", "FONTS"};
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int y = top + 61 + i * 31;
            boolean active = tab == tabs[i];
            ctx.fill(left + 10, y - 5, left + 102, y + 19,
                    active ? 0xCC26384A : (inside(mouseX, mouseY, left + 10, y - 5, 92, 24) ? PANEL_HOVER : 0x00000000));
            if (active) ctx.fill(left + 10, y - 5, left + 12, y + 19, CYAN);
            ctx.drawText(textRenderer, names[i], left + 20, y + 2, active ? WHITE : MUTED, active);
        }

        int contentX = left + 132;
        ctx.drawText(textRenderer, titleForTab(), contentX, top + 17, WHITE, true);
        ctx.drawText(textRenderer, subtitleForTab(), contentX, top + 31, MUTED, false);
        ctx.fill(contentX, top + 48, left + panelW - 18, top + 49, 0x443C9DB5);
        switch (tab) {
            case HUD -> drawHud(ctx, mouseX, mouseY, contentX);
            case VISUAL -> drawVisual(ctx, mouseX, mouseY, contentX);
            case PERFORMANCE -> drawPerformance(ctx, mouseX, mouseY, contentX);
            case FONTS -> drawFonts(ctx, mouseX, mouseY, contentX);
        }
        ctx.drawText(textRenderer, "RIGHT SHIFT  •  CLOSE", left + panelW - 126, top + panelH - 17, MUTED, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private String titleForTab() {
        return switch (tab) {
            case HUD -> "HUD MODULES";
            case VISUAL -> "VISUAL SETTINGS";
            case PERFORMANCE -> "PERFORMANCE";
            case FONTS -> "TYPOGRAPHY";
        };
    }
    private String subtitleForTab() {
        return switch (tab) {
            case HUD -> "Включайте нужные элементы интерфейса";
            case VISUAL -> "Настройте внешний вид NLS";
            case PERFORMANCE -> "Управление производительностью";
            case FONTS -> "Пользовательские шрифты";
        };
    }

    private void drawHud(DrawContext c, int mx, int my, int x) {
        row(c, mx, my, x, 69, "HUD overlay", "Главный переключатель", CleanHudClient.isHudEnabled());
        row(c, mx, my, x, 108, "Brand / Name", "Логотип и никнейм", CleanHudClient.isElementEnabled("brand"));
        row(c, mx, my, x, 147, "FPS / Ping", "Статистика клиента и задержка", CleanHudClient.isElementEnabled("stats"));
        row(c, mx, my, x, 186, "Coordinates", "Координаты и направление", CleanHudClient.isElementEnabled("coords"));
        row(c, mx, my, x, 225, "Clock", "Текущее локальное время", CleanHudClient.isElementEnabled("time"));\n        row(c, mx, my, x, 264, "Animated hotbar", "NLS glass вместо обычной панели", CleanHudClient.isAnimatedHotbarEnabled());
    }
    private void drawVisual(DrawContext c, int mx, int my, int x) {
        action(c, mx, my, x, 69, "Accent color", "Сменить неоновый акцент", "CHANGE");
        info(c, x, 111, "Dark glass interface", "Полупрозрачные панели и тонкие линии");
        info(c, x, 151, "HUD layout", "Откройте чат и перетаскивайте HUD-панели");
        info(c, x, 191, "Scale", "Колесо мыши над панелью меняет масштаб");
    }
    private void drawPerformance(DrawContext c, int mx, int my, int x) {
        row(c, mx, my, x, 69, "FPS Boost", "Минимум частиц, тени выкл., дальность 8", CleanHudClient.cfg.fpsBoost);
        info(c, x, 111, "Safe restore", "При выключении возвращаются сохранённые настройки");
        info(c, x, 151, "Quick command", ".fps on  /  .fps off");
    }
    private void drawFonts(DrawContext c, int mx, int my, int x) {
        info(c, x, 69, "TTF / OTF support", "Поместите файл шрифта в config/cleanhud/fonts/");
        info(c, x, 111, "Resource pack", "NLS-Font.zip создаётся автоматически");
        info(c, x, 151, "Font status", CleanHudClient.isFontPackActive() ? "Активен" : "Не активен / нужен перезапуск");
        action(c, mx, my, x, 191, "Rebuild font pack", "Пересканировать папку шрифтов", "REBUILD");
    }

    private void row(DrawContext c, int mx, int my, int x, int y, String name, String desc, boolean enabled) {
        card(c, mx, my, x, y, 344, 32);
        c.drawText(textRenderer, name, x + 10, y + 5, WHITE, true);
        c.drawText(textRenderer, desc, x + 10, y + 18, MUTED, false);
        int sx = x + 292;
        c.fill(sx, y + 8, sx + 42, y + 24, enabled ? 0xFF286A77 : 0xFF343C49);
        c.fill(sx + (enabled ? 25 : 2), y + 10, sx + (enabled ? 39 : 16), y + 22, enabled ? CYAN : 0xFF929BA8);
        c.drawText(textRenderer, enabled ? "ON" : "OFF", sx - 27, y + 11, enabled ? CYAN : MUTED, false);
    }
    private void action(DrawContext c, int mx, int my, int x, int y, String name, String desc, String label) {
        card(c, mx, my, x, y, 344, 32);
        c.drawText(textRenderer, name, x + 10, y + 5, WHITE, true);
        c.drawText(textRenderer, desc, x + 10, y + 18, MUTED, false);
        c.fill(x + 276, y + 7, x + 334, y + 25, 0xFF245466);
        c.drawCenteredTextWithShadow(textRenderer, label, x + 305, y + 12, WHITE);
    }
    private void info(DrawContext c, int x, int y, String name, String desc) {
        c.fill(x, y, x + 344, y + 32, PANEL);
        c.drawText(textRenderer, name, x + 10, y + 5, WHITE, true);
        c.drawText(textRenderer, desc, x + 10, y + 18, MUTED, false);
    }
    private void card(DrawContext c, int mx, int my, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, inside(mx, my, x, y, w, h) ? PANEL_HOVER : PANEL);
        c.fill(x, y, x + 2, y + h, 0x553C9DB5);
    }
    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = left + 132;
        for (int i = 0; i < Tab.values().length; i++) {
            int y = top + 56 + i * 31;
            if (inside(mouseX, mouseY, left + 10, y, 92, 24)) { tab = Tab.values()[i]; return true; }
        }
        switch (tab) {
            case HUD -> {
                if (at(mouseX, mouseY, x, 69)) CleanHudClient.setHudEnabled(!CleanHudClient.isHudEnabled());
                else if (at(mouseX, mouseY, x, 108)) flip("brand");
                else if (at(mouseX, mouseY, x, 147)) flip("stats");
                else if (at(mouseX, mouseY, x, 186)) flip("coords");
                else if (at(mouseX, mouseY, x, 225)) flip("time");\n                else if (at(mouseX, mouseY, x, 264)) CleanHudClient.setAnimatedHotbarEnabled(!CleanHudClient.isAnimatedHotbarEnabled());
            }
            case VISUAL -> { if (at(mouseX, mouseY, x, 69)) CleanHudClient.cycleAccent(); }
            case PERFORMANCE -> { if (at(mouseX, mouseY, x, 69)) CleanHudClient.toggleFpsBoost(); }
            case FONTS -> { if (at(mouseX, mouseY, x, 191)) CleanHudClient.rebuildFontPack(); }
        }
        return true;
    }
    private boolean at(double mx, double my, int x, int y) { return inside(mx, my, x, y, 344, 32); }
    private void flip(String id) { CleanHudClient.setElementEnabled(id, !CleanHudClient.isElementEnabled(id)); }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(null); }
}
