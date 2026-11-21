package com.example.cleanhud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Opaque NLS Visuals ClickGUI. Uses solid layers to avoid vanilla world blur bleed-through. */
public final class CleanHudClickGui extends Screen {
    private enum Tab { HUD, ASSIST, VISUAL, PERFORMANCE, FONTS }

    private Tab tab = Tab.HUD;
    private int left, top, panelW, panelH;

    private static final int BG = 0xFF101722;
    private static final int PANEL = 0xFF1A2533;
    private static final int PANEL_HOVER = 0xFF26384A;
    private static final int MUTED = 0xFF9AAABD;
    private static final int WHITE = 0xFFF0F4FA;
    private static final int CYAN = 0xFF54D6F5;

    public CleanHudClickGui() {
        super(Text.literal("NLS Visuals"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Fully opaque screen background: no blurred world or translucent half-layer.
        ctx.fill(0, 0, width, height, 0xFF080D14);

        panelW = Math.min(560, width - 28);
        panelH = Math.min(350, height - 28);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        ctx.fill(left + 4, top + 5, left + panelW + 4, top + panelH + 5, 0xFF03060A);
        ctx.fill(left, top, left + panelW, top + panelH, BG);
        ctx.fill(left, top, left + panelW, top + 2, CYAN);
        ctx.fill(left, top + 2, left + 116, top + panelH, 0xFF131D29);

        ctx.drawText(textRenderer, "NLS", left + 18, top + 16, WHITE, true);
        ctx.drawText(textRenderer, "VISUALS  /  CLIENT", left + 18, top + 29, MUTED, false);
        ctx.fill(left + 14, top + 47, left + 102, top + 48, 0xFF31566A);

        String[] names = {"HUD", "GAME ASSIST", "VISUAL", "PERFORMANCE", "FONTS"};
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int y = top + 61 + i * 31;
            boolean active = tab == tabs[i];
            ctx.fill(left + 10, y - 5, left + 106, y + 19,
                    active ? 0xFF26384A : (inside(mouseX, mouseY, left + 10, y - 5, 96, 24) ? PANEL_HOVER : 0xFF131D29));
            if (active) ctx.fill(left + 10, y - 5, left + 12, y + 19, CYAN);
            ctx.drawText(textRenderer, names[i], left + 20, y + 2, active ? WHITE : MUTED, active);
        }

        int contentX = left + 136;
        ctx.drawText(textRenderer, titleForTab(), contentX, top + 17, WHITE, true);
        ctx.drawText(textRenderer, subtitleForTab(), contentX, top + 31, MUTED, false);
        ctx.fill(contentX, top + 48, left + panelW - 18, top + 49, 0xFF31566A);

        // Content rows use panel-local Y coordinates; translate them into the centered panel.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, top, 0);
        switch (tab) {
            case HUD -> drawHud(ctx, mouseX, mouseY - top, contentX);
            case ASSIST -> drawAssist(ctx, mouseX, mouseY - top, contentX);
            case VISUAL -> drawVisual(ctx, mouseX, mouseY - top, contentX);
            case PERFORMANCE -> drawPerformance(ctx, mouseX, mouseY - top, contentX);
            case FONTS -> drawFonts(ctx, mouseX, mouseY - top, contentX);
        }
        ctx.getMatrices().pop();

        ctx.drawText(textRenderer, "RIGHT SHIFT  •  CLOSE", left + panelW - 132, top + panelH - 17, MUTED, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private String titleForTab() {
        return switch (tab) {
            case HUD -> "HUD MODULES";
            case ASSIST -> "GAME ASSIST";
            case VISUAL -> "VISUAL SETTINGS";
            case PERFORMANCE -> "PERFORMANCE";
            case FONTS -> "TYPOGRAPHY";
        };
    }

    private String subtitleForTab() {
        return switch (tab) {
            case HUD -> "Включайте нужные элементы интерфейса";
            case ASSIST -> "Игровые помощники и информеры";
            case VISUAL -> "Настройте внешний вид NLS";
            case PERFORMANCE -> "Управление производительностью";
            case FONTS -> "Пользовательские шрифты";
        };
    }

    private void drawHud(DrawContext c, int mx, int my, int x) {
        plainRow(c, mx, my, x, 69, "HUD overlay", "Главный переключатель", CleanHudClient.isHudEnabled());
        row(c, mx, my, x, 108, "Brand / Name", "Логотип и никнейм", "brand", CleanHudClient.isElementEnabled("brand"));
        row(c, mx, my, x, 147, "FPS / Ping", "Статистика клиента и задержка", "stats", CleanHudClient.isElementEnabled("stats"));
        row(c, mx, my, x, 186, "Coordinates", "Координаты и направление", "coords", CleanHudClient.isElementEnabled("coords"));
        row(c, mx, my, x, 225, "Clock", "Текущее локальное время", "time", CleanHudClient.isElementEnabled("time"));
        plainRow(c, mx, my, x, 264, "Animated hotbar", "NLS glass вместо обычной панели", CleanHudClient.isAnimatedHotbarEnabled());
        c.drawText(textRenderer, "HOTBAR STYLE  ·  " + styleName(CleanHudClient.getHotbarStyle()), x + 8, 301, CYAN, true);
    }

    private void drawAssist(DrawContext c, int mx, int my, int x) {
        assistRow(c, mx, my, x, 69, "Target HUD", "Здоровье и имя сущности под прицелом", "target");
        assistRow(c, mx, my, x, 108, "Armor HUD", "Броня и прочность экипировки", "armor");
        assistRow(c, mx, my, x, 147, "Potion HUD", "Активные эффекты и оставшееся время", "potions");
        assistRow(c, mx, my, x, 186, "Low HP Warning", "Пульсирующее предупреждение при 6 HP", "lowhp");
        info(c, x, 232, "Client-side helpers", "Только отображение данных, без отправки игровых действий");
    }

    private void assistRow(DrawContext c, int mx, int my, int x, int y, String name, String desc, String id) {
        card(c, mx, my, x, y, 370, 32);
        c.drawText(textRenderer, name, x + 10, y + 5, WHITE, true);
        c.drawText(textRenderer, desc, x + 10, y + 18, MUTED, false);
        boolean on = CleanHudClient.isAssistEnabled(id);
        c.fill(x + 316, y + 8, x + 358, y + 24, on ? 0xFF286A77 : 0xFF343C49);
        c.drawText(textRenderer, on ? "ON" : "OFF", x + 325, y + 12, on ? CYAN : MUTED, true);
    }

    private void drawVisual(DrawContext c, int mx, int my, int x) {
        action(c, mx, my, x, 69, "Accent color", "Сменить неоновый акцент", "CHANGE");
        info(c, x, 111, "Dark glass interface", "Полностью непрозрачные панели");
        info(c, x, 151, "HUD layout", "Откройте чат и перетаскивайте HUD-панели");
        info(c, x, 191, "Scale", "Колесо мыши над панелью меняет масштаб");
        plainRow(c, mx, my, x, 232, "Dynamic Island", "Анимированный информер сверху экрана", CleanHudClient.isDynamicIslandEnabled());\n        plainRow(c, mx, my, x, 264, "World particles", "Декоративные частицы вокруг игрока", CleanHudClient.isWorldParticlesEnabled());\n        c.drawText(textRenderer, "PARTICLE STYLE  ·  " + particleStyleName(CleanHudClient.getParticleStyle()), x + 8, 301, CYAN, true);
    }

    private void drawPerformance(DrawContext c, int mx, int my, int x) {
        plainRow(c, mx, my, x, 69, "FPS Boost", "Минимум частиц, тени выкл., дальность 8", CleanHudClient.cfg.fpsBoost);
        info(c, x, 111, "Safe restore", "При выключении возвращаются сохранённые настройки");
        info(c, x, 151, "Quick command", ".fps on  /  .fps off");
    }

    private void drawFonts(DrawContext c, int mx, int my, int x) {
        info(c, x, 69, "TTF / OTF support", "Поместите файл шрифта в config/cleanhud/fonts/");
        info(c, x, 111, "Resource pack", "NLS-Font.zip создаётся автоматически");
        info(c, x, 151, "Font status", CleanHudClient.isFontPackActive() ? "Активен" : "Не активен / нужен перезапуск");
        action(c, mx, my, x, 191, "Rebuild font pack", "Пересканировать папку шрифтов", "REBUILD");
    }

    private void plainRow(DrawContext c, int mx, int my, int x, int y, String name, String desc, boolean enabled) {
        card(c, mx, my, x, y, 370, 32);
        c.drawText(textRenderer, name, x + 10, y + 5, WHITE, true);
        c.drawText(textRenderer, desc, x + 10, y + 18, MUTED, false);
        c.fill(x + 316, y + 8, x + 358, y + 24, enabled ? 0xFF286A77 : 0xFF343C49);
        c.drawText(textRenderer, enabled ? "ON" : "OFF", x + 325, y + 12, enabled ? CYAN : MUTED, true);
    }

    private void row(DrawContext c, int mx, int my, int x, int y, String name, String desc, String id, boolean enabled) {
        card(c, mx, my, x, y, 370, 32);
        c.drawText(textRenderer, name, x + 10, y + 5, WHITE, true);
        c.drawText(textRenderer, desc, x + 10, y + 18, MUTED, false);
        int style = CleanHudClient.getElementStyle(id);
        c.fill(x + 244, y + 7, x + 302, y + 25, 0xFF26384A);
        c.drawCenteredTextWithShadow(textRenderer, styleName(style), x + 273, y + 12, CYAN);
        int sx = x + 316;
        c.fill(sx, y + 8, sx + 42, y + 24, enabled ? 0xFF286A77 : 0xFF343C49);
        c.fill(sx + (enabled ? 25 : 2), y + 10, sx + (enabled ? 39 : 16), y + 22, enabled ? CYAN : 0xFF929BA8);
        c.drawText(textRenderer, enabled ? "ON" : "OFF", sx - 27, y + 11, enabled ? CYAN : MUTED, false);
    }

    private void action(DrawContext c, int mx, int my, int x, int y, String name, String desc, String label) {
        card(c, mx, my, x, y, 370, 32);
        c.drawText(textRenderer, name, x + 10, y + 5, WHITE, true);
        c.drawText(textRenderer, desc, x + 10, y + 18, MUTED, false);
        c.fill(x + 300, y + 7, x + 360, y + 25, 0xFF245466);
        c.drawCenteredTextWithShadow(textRenderer, label, x + 330, y + 12, WHITE);
    }

    private void info(DrawContext c, int x, int y, String name, String desc) {
        c.fill(x, y, x + 370, y + 32, PANEL);
        c.drawText(textRenderer, name, x + 10, y + 5, WHITE, true);
        c.drawText(textRenderer, desc, x + 10, y + 18, MUTED, false);
    }

    private void card(DrawContext c, int mx, int my, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, inside(mx, my, x, y, w, h) ? PANEL_HOVER : PANEL);
        c.fill(x, y, x + 2, y + h, 0xFF31566A);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = left + 136;
        double localY = mouseY - top;
        for (int i = 0; i < Tab.values().length; i++) {
            int y = top + 56 + i * 31;
            if (inside(mouseX, mouseY, left + 10, y, 96, 24)) {
                tab = Tab.values()[i];
                return true;
            }
        }

        switch (tab) {
            case ASSIST -> {
                if (at(mouseX, localY, x, 69)) CleanHudClient.toggleAssist("target");
                else if (at(mouseX, localY, x, 108)) CleanHudClient.toggleAssist("armor");
                else if (at(mouseX, localY, x, 147)) CleanHudClient.toggleAssist("potions");
                else if (at(mouseX, localY, x, 186)) CleanHudClient.toggleAssist("lowhp");
            }
            case HUD -> {
                if (at(mouseX, localY, x, 69)) CleanHudClient.setHudEnabled(!CleanHudClient.isHudEnabled());
                else if (moduleAt(mouseX, localY, x, 108, "brand")) {}
                else if (moduleAt(mouseX, localY, x, 147, "stats")) {}
                else if (moduleAt(mouseX, localY, x, 186, "coords")) {}
                else if (moduleAt(mouseX, localY, x, 225, "time")) {}
                else if (at(mouseX, localY, x, 264)) CleanHudClient.setAnimatedHotbarEnabled(!CleanHudClient.isAnimatedHotbarEnabled());
                else if (inside(mouseX, localY, x, 296, 370, 24)) CleanHudClient.cycleHotbarStyle();
            }
            case VISUAL -> {
                if (at(mouseX, localY, x, 69)) CleanHudClient.cycleAccent();\n                else if (at(mouseX, localY, x, 264)) CleanHudClient.toggleWorldParticles();\n                else if (inside(mouseX, localY, x, 296, 370, 24)) CleanHudClient.cycleParticleStyle();
            }
            case PERFORMANCE -> {
                if (at(mouseX, localY, x, 69)) CleanHudClient.toggleFpsBoost();
            }
            case FONTS -> {
                if (at(mouseX, localY, x, 191)) CleanHudClient.rebuildFontPack();
            }
        }
        return true;
    }

    private boolean moduleAt(double mx, double my, int x, int y, String id) {
        if (!at(mx, my, x, y)) return false;
        if (inside(mx, my, x + 238, y, 70, 32)) CleanHudClient.cycleElementStyle(id);
        else flip(id);
        return true;
    }

    private String particleStyleName(int style) {\n        return switch (style) { case 1 -> "SOUL"; case 2 -> "SPARK"; default -> "STAR"; };\n    }\n\n    private String styleName(int style) {
        return switch (style) { case 1 -> "OUTLINE"; case 2 -> "MINIMAL"; default -> "GLASS"; };
    }

    private boolean at(double mx, double my, int x, int y) {
        return inside(mx, my, x, y, 370, 32);
    }

    private void flip(String id) {
        CleanHudClient.setElementEnabled(id, !CleanHudClient.isElementEnabled(id));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(null);
    }
}
