package com.example.cleanhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.function.Supplier;

/**
 * Косметический HUD. Откройте чат (T): панели перетаскиваются мышью, колесо над панелью меняет размер.
 * Рамка «Рука» двигает и масштабирует предмет в руке. Всё хранится в config/cleanhud.json.
 */
public class CleanHudClient implements ClientModInitializer {
    public static class Pos { public int x, y; public float s = 1f; public Pos() {} Pos(int x, int y) { this.x = x; this.y = y; } }
    public static class Config {
        public Map<String, Pos> pos = new LinkedHashMap<>();
        public float hx = 0f, hy = 0f, hs = 1f;
        public int accent = 0;
        public String discordAppId = "";
        public boolean fpsBoost = false;
        public boolean showBrand = true, showStats = true, showCoords = true, showTime = true;
        public boolean animatedHotbar = false;
        public boolean dynamicIsland = true;
        public int hotbarStyle = 0;
        public Map<String, Integer> styles = new LinkedHashMap<>();
        public boolean assistTargetHud = false, assistArmorHud = false, assistPotionHud = false, assistLowHealth = false;
        public int[] prev = null;
        public boolean gpsEnabled = false;
        public double gpsX = 0, gpsZ = 0;
    }
    record El(String id, Supplier<List<String>> lines) {}

    public static final Config cfg = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cleanhud.json");
    private static final int[] ACCENTS = {0xFF4FC3F7, 0xFFBA68C8, 0xFF81C784, 0xFFFFB74D};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, int[]> sizes = new HashMap<>();
    private static final List<El> ELEMENTS = new ArrayList<>();
    private static final Identifier FONT_ID = Identifier.of("cleanhud", "custom");
    private static final String PACK_NAME = "NLS-Font.zip", PACK_ID = "file/" + PACK_NAME;
    private static final Set<String> COMMANDS = Set.of("config", "fps", "rpc", "help", "gps");
    private static boolean enabled = true, fontActive = false, fontPackWritten = false, fontChecked = false;
    private static int ticks = 0;
    private static String dragging = null;
    private static double grabX, grabY;

    @Override
    public void onInitializeClient() {
        ELEMENTS.add(new El("brand", () -> List.of("NLS VISUALS", MinecraftClient.getInstance().getSession().getUsername())));
        ELEMENTS.add(new El("stats", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            List<String> l = new ArrayList<>();
            l.add("FPS " + mc.getCurrentFps());
            if (mc.getNetworkHandler() != null && mc.player != null) {
                var e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) l.add("Ping " + e.getLatency() + " ms");
            }
            return l;
        }));
        ELEMENTS.add(new El("coords", () -> {
            var p = MinecraftClient.getInstance().player;
            return p == null ? List.of() : List.of(String.format("XYZ %.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()),
                    "Facing " + p.getHorizontalFacing().asString());
        }));
        ELEMENTS.add(new El("time", () -> List.of(LocalTime.now().format(TIME))));
        int y = 6;
        for (El e : ELEMENTS) { cfg.pos.put(e.id(), new Pos(6, y)); y += 36; }
        load();
        try { Files.createDirectories(FabricLoader.getInstance().getConfigDir().resolve("cleanhud/fonts")); } catch (Exception ignored) {}
        buildFontPack();
        DiscordRpc.start(cfg.discordAppId);
        ClientSendMessageEvents.ALLOW_CHAT.register(CleanHudClient::handleChat);

        KeyBinding toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cleanhud.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "category.cleanhud"));
        KeyBinding theme = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cleanhud.theme", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, "category.cleanhud"));
        KeyBinding clickGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cleanhud.clickgui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.cleanhud"));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (toggle.wasPressed()) enabled = !enabled;
            if (c.currentScreen instanceof TitleScreen && !(c.currentScreen instanceof NlsMainMenu)) c.setScreen(new NlsMainMenu());
            while (clickGui.wasPressed()) c.setScreen(c.currentScreen instanceof CleanHudClickGui ? null : new CleanHudClickGui());
            while (theme.wasPressed()) { cfg.accent = (cfg.accent + 1) % ACCENTS.length; save(); }
            if (!fontChecked && c.getOverlay() == null) { fontChecked = true; enableFontPack(c); }
            if (++ticks % 20 == 0) {
                fontActive = c.getResourcePackManager().getEnabledIds().contains(PACK_ID);
                DiscordRpc.details = "Ник: " + c.getSession().getUsername();
                DiscordRpc.state = c.getCurrentServerEntry() != null ? "Сервер: " + c.getCurrentServerEntry().address
                        : c.player != null ? "Одиночная игра" : "В меню";
            }
        });
        HudRenderCallback.EVENT.register(CleanHudClient::renderHud);
        HudRenderCallback.EVENT.register(CleanHudClient::renderGameAssist);
        HudRenderCallback.EVENT.register(CleanHudClient::renderGps);
        HudRenderCallback.EVENT.register(CleanHudClient::renderDynamicIsland);
        HudRenderCallback.EVENT.register(ItemHotbarRenderer::render);

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            dragging = null;
            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> {
                if (button != 0 || !enabled) return true;
                String id = hit(mx, my, s.width, s.height);
                if (id == null) return true;
                int[] o = origin(id, s.width, s.height);
                dragging = id; grabX = mx - o[0]; grabY = my - o[1];
                return false;
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, mx, my, button) -> {
                if (dragging == null) return true;
                dragging = null; save();
                return false;
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hAmt, vAmt) -> {
                String id = enabled ? hit(mx, my, s.width, s.height) : null;
                if (id == null) return true;
                float d = vAmt > 0 ? 0.1f : -0.1f;
                if (id.equals("hand")) cfg.hs = clamp(cfg.hs + d, 0.4f, 1.6f);
                else { Pos p = cfg.pos.get(id); p.s = clamp(p.s + d, 0.5f, 2.5f); }
                save();
                return false;
            });
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                if (!enabled) return;
                if (dragging != null) {
                    if (dragging.equals("hand")) {
                        cfg.hx = clamp((float) (mx - grabX) - handX(s.width), -200f, 200f);
                        cfg.hy = clamp((float) (my - grabY) - handY(s.height), -150f, 150f);
                    } else {
                        Pos p = cfg.pos.get(dragging);
                        p.x = (int) clamp((float) (mx - grabX), 0, s.width - 12);
                        p.y = (int) clamp((float) (my - grabY), 0, s.height - 12);
                    }
                }
                var tr = MinecraftClient.getInstance().textRenderer;
                int hx = handX(s.width) + (int) cfg.hx, hy = handY(s.height) + (int) cfg.hy;
                ctx.fill(hx, hy, hx + 60, hy + 22, "hand".equals(dragging) ? 0xB0305060 : 0x90000000);
                ctx.drawBorder(hx, hy, 60, 22, 0xAAFFFFFF);
                ctx.drawCenteredTextWithShadow(tr, String.format("Рука x%.1f", cfg.hs), hx + 30, hy + 7, 0xFFFFFFFF);
                ctx.drawCenteredTextWithShadow(tr, "Тяните панели мышью, колесо меняет размер", s.width / 2, 4, 0xFFCCCCCC);
            });
        });
    }

    private static int handX(int sw) { return sw - 90; }
    private static int handY(int sh) { return sh - 70; }

    private static int[] origin(String id, int sw, int sh) {
        if (id.equals("hand")) return new int[]{handX(sw) + (int) cfg.hx, handY(sh) + (int) cfg.hy};
        Pos p = cfg.pos.get(id);
        return new int[]{p.x, p.y};
    }

    private static String hit(double mx, double my, int sw, int sh) {
        int[] o = origin("hand", sw, sh);
        if (mx >= o[0] && mx <= o[0] + 60 && my >= o[1] && my <= o[1] + 22) return "hand";
        for (int i = ELEMENTS.size() - 1; i >= 0; i--) {
            String id = ELEMENTS.get(i).id();
            int[] sz = sizes.get(id);
            if (sz == null) continue;
            Pos p = cfg.pos.get(id);
            if (mx >= p.x && mx <= p.x + sz[0] * p.s && my >= p.y && my <= p.y + sz[1] * p.s) return id;
        }
        return null;
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.options.hudHidden) return;
        boolean edit = mc.currentScreen instanceof ChatScreen;
        for (El el : ELEMENTS) {
            if (!isElementEnabled(el.id())) continue;
            List<String> lines = el.lines().get();
            if (lines.isEmpty()) continue;
            Pos p = cfg.pos.get(el.id());
            int tw = 0;
            for (String l : lines) tw = Math.max(tw, mc.textRenderer.getWidth(text(l)));
            if (el.id().equals("brand")) tw = Math.max(tw, mc.textRenderer.getWidth(text("CLEAN HUD  •  " + lines.get(1))));
            int pad = 4, lh = 11, w = tw + pad * 2 + 3, h = lines.size() * lh + pad * 2 - 2;
            sizes.put(el.id(), new int[]{w, h});
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(p.x, p.y, 0);
            m.scale(p.s, p.s, 1f);
            int style = getElementStyle(el.id());
            int accent = ACCENTS[cfg.accent % ACCENTS.length];
            if (style == 0) {
                ctx.fill(0, 0, w, h, 0xB0101722);
                ctx.fill(0, 0, 2, h, accent);
            } else if (style == 1) {
                ctx.fill(0, 0, w, h, 0x50101722);
                ctx.drawBorder(0, 0, w, h, 0xCC000000 | (accent & 0x00FFFFFF));
            } else {
                ctx.fill(0, 0, w, 1, accent);
                ctx.fill(0, h - 1, w, h, 0x665E7185);
            }
            if (el.id().equals("brand")) {
                if (style == 0) ctx.fill(4, 3, 6, h - 3, accent);
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal("NLS").setStyle(Style.EMPTY.withBold(true)),
                        pad + 4, pad, ACCENTS[cfg.accent % ACCENTS.length]);
                ctx.drawTextWithShadow(mc.textRenderer, text("CLEAN HUD  •  " + lines.get(1)),
                        pad + 4, pad + 12, 0xFFEAF5FA);
            } else {
                for (int i = 0; i < lines.size(); i++)
                    ctx.drawTextWithShadow(mc.textRenderer, text(lines.get(i)), pad + 3, pad + i * lh, 0xFFFFFFFF);
            }
            if (edit) ctx.drawBorder(0, 0, w, h, el.id().equals(dragging) ? 0xFFFFFFFF : 0x88FFFFFF);
            m.pop();
        }
    }

    private static void renderGps(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || !cfg.gpsEnabled || mc.player == null || mc.options.hudHidden) return;
        double dx = cfg.gpsX - mc.player.getX();
        double dz = cfg.gpsZ - mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.5) {
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("GPS  ·  DESTINATION REACHED"),
                    mc.getWindow().getScaledWidth() / 2, 54, 0xFF69E6B0);
            return;
        }
        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float relative = targetYaw - mc.player.getYaw();
        while (relative <= -180f) relative += 360f;
        while (relative > 180f) relative -= 360f;
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = 58;
        int accent = ACCENTS[cfg.accent % ACCENTS.length];
        ctx.fill(cx - 55, cy - 18, cx + 55, cy + 19, 0xD908111B);
        ctx.drawBorder(cx - 55, cy - 18, 110, 37, 0xFF294657);
        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(cx, cy - 5, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(relative));
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("▲"), -4, -6, accent);
        matrices.pop();
        String distanceText = distance < 1000 ? String.format(java.util.Locale.ROOT, "%.0f m", distance)
                : String.format(java.util.Locale.ROOT, "%.2f km", distance / 1000.0);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(distanceText + "  ·  " +
                (int)Math.round(cfg.gpsX) + " " + (int)Math.round(cfg.gpsZ)), cx, cy + 7, 0xFFEAF5FA);
    }

    private static void renderDynamicIsland(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || !cfg.dynamicIsland || mc.player == null || mc.options.hudHidden) return;
        int sw = mc.getWindow().getScaledWidth();
        String title = "NLS VISUALS";
        String detail = LocalTime.now().format(TIME) + "  ·  " + mc.getCurrentFps() + " FPS";
        int accent = ACCENTS[cfg.accent % ACCENTS.length];
        boolean expanded = false;
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target && target.isAlive()) {
            title = target.getName().getString();
            detail = String.format(java.util.Locale.ROOT, "%.1f HP  ·  TARGET", target.getHealth());
            expanded = true;
        } else if (mc.player.getHealth() <= 6f) {
            title = "LOW HEALTH";
            detail = String.format(java.util.Locale.ROOT, "%.1f HP  ·  BE CAREFUL", mc.player.getHealth());
            expanded = true;
        } else if (!mc.player.getStatusEffects().isEmpty()) {
            var effect = mc.player.getStatusEffects().iterator().next();
            title = effect.getEffectType().value().getName().getString();
            detail = effect.isInfinite() ? "ACTIVE  ·  ∞" : "ACTIVE  ·  " + net.minecraft.util.StringHelper.formatTicks(effect.getDuration(), 20);
            expanded = true;
        }
        float targetWidth = expanded ? Math.max(190, Math.min(270, mc.textRenderer.getWidth(title) + mc.textRenderer.getWidth(detail) + 48)) : 142;
        islandWidth += (targetWidth - islandWidth) * 0.18f;
        int w = Math.round(islandWidth), h = expanded ? 38 : 28;
        int x = (sw - w) / 2, y = 7;
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x663E657A);
        ctx.fill(x, y, x + w, y + h, 0xF5060A10);
        ctx.drawBorder(x, y, w, h, 0xFF263C4C);
        ctx.fill(x + 11, y + 10, x + 17, y + 16, expanded ? 0xFF69E6B0 : accent);
        if (expanded) {
            ctx.drawText(mc.textRenderer, title, x + 25, y + 6, 0xFFF2F7FC, true);
            ctx.drawText(mc.textRenderer, detail, x + 25, y + 21, 0xFF9EB2C4, false);
        } else {
            ctx.drawText(mc.textRenderer, "NLS", x + 26, y + 6, accent, true);
            ctx.drawText(mc.textRenderer, detail, x + 57, y + 6, 0xFFD6E2EC, false);
        }
    }

    private static void renderGameAssist(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.options.hudHidden) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int accent = ACCENTS[cfg.accent % ACCENTS.length];
        if (cfg.assistLowHealth && mc.player.getHealth() <= 6.0f) {
            int alpha = 40 + (int)(Math.sin((System.currentTimeMillis() % 1200L) / 1200.0 * Math.PI * 2) * 25);
            ctx.fill(0, 0, sw, 4, (Math.min(90, alpha) << 24) | 0x00FF3030);
            ctx.fill(0, sh - 4, sw, sh, (Math.min(90, alpha) << 24) | 0x00FF3030);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("LOW HP  ·  " + (int)mc.player.getHealth() + " HP"), sw / 2, 12, 0xFFFF7777);
        }
        if (cfg.assistTargetHud && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target && target.isAlive()) {
            int x = sw / 2 + 12, y = sh / 2 + 12, w = 142;
            ctx.fill(x, y, x + w, y + 34, 0xD9101722);
            ctx.drawBorder(x, y, w, 34, 0xFF31566A);
            ctx.drawTextWithShadow(mc.textRenderer, target.getName(), x + 7, y + 5, 0xFFF0F4FA);
            float hp = Math.max(0, target.getHealth()), max = Math.max(1, target.getMaxHealth());
            ctx.fill(x + 7, y + 19, x + w - 7, y + 25, 0xFF303B49);
            ctx.fill(x + 7, y + 19, x + 7 + Math.round((w - 14) * Math.min(1f, hp / max)), y + 25, accent);
            ctx.drawText(mc.textRenderer, String.format(java.util.Locale.ROOT, "%.1f / %.1f HP", hp, max), x + 7, y + 26, 0xFFD7E5F0, false);
        }
        if (cfg.assistArmorHud) {
            int x = sw / 2 - 36, y = sh - 62;
            for (int i = 0; i < mc.player.getInventory().armor.size(); i++) {
                var stack = mc.player.getInventory().armor.get(3 - i);
                int sx = x + i * 18;
                ctx.fill(sx, y, sx + 17, y + 17, 0xA8101722);
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx, y);
                    int pct = stack.getMaxDamage() == 0 ? 100 : Math.round(100f * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage());
                    ctx.drawText(mc.textRenderer, pct + "%", sx, y + 17, pct < 25 ? 0xFFFF7777 : 0xFFEAF5FA, true);
                }
            }
        }
        if (cfg.assistPotionHud && !mc.player.getStatusEffects().isEmpty()) {
            int x = sw - 126, y = 8;
            for (var effect : mc.player.getStatusEffects()) {
                String name = effect.getEffectType().value().getName().getString();
                String duration = effect.isInfinite() ? "∞" : net.minecraft.util.StringHelper.formatTicks(effect.getDuration(), 20);
                ctx.fill(x, y, sw - 8, y + 14, 0xB8101722);
                ctx.fill(x, y, x + 2, y + 14, accent);
                ctx.drawText(mc.textRenderer, name + " " + duration, x + 6, y + 3, 0xFFEAF5FA, false);
                y += 16;
            }
        }
    }

    public static boolean isAssistEnabled(String id) {
        return switch (id) {
            case "target" -> cfg.assistTargetHud;
            case "armor" -> cfg.assistArmorHud;
            case "potions" -> cfg.assistPotionHud;
            case "lowhp" -> cfg.assistLowHealth;
            default -> false;
        };
    }
    public static void toggleAssist(String id) {
        switch (id) {
            case "target" -> cfg.assistTargetHud = !cfg.assistTargetHud;
            case "armor" -> cfg.assistArmorHud = !cfg.assistArmorHud;
            case "potions" -> cfg.assistPotionHud = !cfg.assistPotionHud;
            case "lowhp" -> cfg.assistLowHealth = !cfg.assistLowHealth;
        }
        save();
    }

    private static Text text(String s) {
        return fontActive ? Text.literal(s).setStyle(Style.EMPTY.withFont(FONT_ID)) : Text.literal(s);
    }

    private static void applyLayout(Config c) {
        cfg.hx = c.hx; cfg.hy = c.hy; cfg.hs = c.hs == 0 ? 1f : c.hs; cfg.accent = Math.max(0, c.accent);
        cfg.showBrand = c.showBrand; cfg.showStats = c.showStats; cfg.showCoords = c.showCoords; cfg.showTime = c.showTime;
        cfg.dynamicIsland = c.dynamicIsland; cfg.animatedHotbar = c.animatedHotbar; cfg.hotbarStyle = Math.max(0, Math.min(2, c.hotbarStyle));
        cfg.assistTargetHud = c.assistTargetHud; cfg.assistArmorHud = c.assistArmorHud;
        cfg.assistPotionHud = c.assistPotionHud; cfg.assistLowHealth = c.assistLowHealth;
        if (c.styles != null) cfg.styles.putAll(c.styles);
        if (c.pos != null) c.pos.forEach((k, v) -> { if (cfg.pos.containsKey(k) && v != null) cfg.pos.put(k, v); });
    }

    private static void msg(String s) {
        var p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.literal("[NLS] " + s), false);
    }

    /** Команды в чате с точкой: .config save/load/list, .fps on/off, .rpc ID, .help */
    private static boolean handleChat(String raw) {
        if (!raw.startsWith(".")) return true;
        String[] a = raw.substring(1).trim().split("\\s+");
        if (a.length == 0 || !COMMANDS.contains(a[0].toLowerCase())) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("cleanhud/configs");
        try {
            switch (a[0].toLowerCase()) {
                case "config" -> {
                    String sub = a.length > 1 ? a[1].toLowerCase() : "list";
                    String name = a.length > 2 ? a[2] : "";
                    if (sub.equals("list")) {
                        Files.createDirectories(dir);
                        try (var st = Files.list(dir)) {
                            msg("Конфиги: " + st.map(f -> f.getFileName().toString().replace(".json", "")).sorted().toList());
                        }
                    } else if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
                        msg("Имя: до 32 символов, буквы/цифры/_/-");
                    } else if (sub.equals("save")) {
                        Files.createDirectories(dir);
                        Config copy = new Config();
                        copy.pos = cfg.pos; copy.hx = cfg.hx; copy.hy = cfg.hy; copy.hs = cfg.hs; copy.accent = cfg.accent;
                        try (Writer w = Files.newBufferedWriter(dir.resolve(name + ".json"))) { GSON.toJson(copy, w); }
                        msg("Конфиг «" + name + "» сохранён.");
                    } else if (sub.equals("load")) {
                        try (Reader r = Files.newBufferedReader(dir.resolve(name + ".json"))) {
                            Config c = GSON.fromJson(r, Config.class);
                            if (c != null) { applyLayout(c); save(); msg("Конфиг «" + name + "» загружен."); }
                        } catch (java.nio.file.NoSuchFileException e) { msg("Конфиг «" + name + "» не найден."); }
                    } else msg(".config save <имя> | load <имя> | list");
                }
                case "fps" -> {
                    if (a.length > 1 && a[1].equalsIgnoreCase("on")) { fpsBoost(mc, true); msg("FPS-режим включён."); }
                    else if (a.length > 1 && a[1].equalsIgnoreCase("off")) { fpsBoost(mc, false); msg("Настройки возвращены."); }
                    else msg(".fps on | off");
                }
                case "rpc" -> {
                    if (a.length > 1) { cfg.discordAppId = a[1]; save(); DiscordRpc.start(a[1]); msg("Discord Application ID сохранён."); }
                    else msg(".rpc <Application ID с discord.com/developers>");
                }
                default -> msg(".config save|load|list <имя>, .fps on|off, .gps <X> <Z>|off, .rpc <ID>");
            }
        } catch (Exception e) { msg("Ошибка: " + e.getMessage()); }
        return false;
    }

    private static <T extends Enum<T>> void setEnum(SimpleOption<T> opt, String name) {
        opt.setValue(Enum.valueOf(opt.getValue().getDeclaringClass(), name));
    }

    private static <T extends Enum<T>> void setEnumOrdinal(SimpleOption<T> opt, int i) {
        opt.setValue(opt.getValue().getDeclaringClass().getEnumConstants()[i]);
    }

    /** Снижает нагрузку: mipmap 0 (текстуры без уровней детализации), быстрая графика, минимум частиц и т.д. */
    private static void fpsBoost(MinecraftClient mc, boolean on) {
        var o = mc.options;
        if (on) {
            if (cfg.prev == null) cfg.prev = new int[]{o.getMipmapLevels().getValue(), o.getViewDistance().getValue(),
                    o.getGraphicsMode().getValue().ordinal(), o.getParticles().getValue().ordinal(),
                    o.getCloudRenderMode().getValue().ordinal(), o.getEntityShadows().getValue() ? 1 : 0,
                    o.getBiomeBlendRadius().getValue()};
            o.getMipmapLevels().setValue(0);
            o.getGraphicsMode().setValue(GraphicsMode.FAST);
            setEnum(o.getParticles(), "MINIMAL");
            o.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            o.getEntityShadows().setValue(false);
            o.getViewDistance().setValue(Math.min(o.getViewDistance().getValue(), 8));
            o.getBiomeBlendRadius().setValue(0);
            cfg.fpsBoost = true;
        } else if (cfg.prev != null) {
            int[] p = cfg.prev;
            o.getMipmapLevels().setValue(p[0]);
            o.getViewDistance().setValue(p[1]);
            o.getGraphicsMode().setValue(GraphicsMode.values()[p[2]]);
            setEnumOrdinal(o.getParticles(), p[3]);
            o.getCloudRenderMode().setValue(CloudRenderMode.values()[p[4]]);
            o.getEntityShadows().setValue(p[5] == 1);
            o.getBiomeBlendRadius().setValue(p[6]);
            cfg.prev = null; cfg.fpsBoost = false;
        }
        o.write(); save();
        mc.reloadResources();
    }

    /** Берёт первый .ttf/.otf из config/cleanhud/fonts и пакует его в ресурспак resourcepacks/NLS-Font.zip. */
    private static void buildFontPack() {
        try {
            Path fonts = FabricLoader.getInstance().getConfigDir().resolve("cleanhud/fonts");
            Path font;
            try (var st = Files.list(fonts)) {
                font = st.filter(p -> p.toString().toLowerCase().matches(".*\\.(ttf|otf)")).findFirst().orElse(null);
            }
            if (font == null) return;
            String ext = font.toString().toLowerCase().endsWith(".otf") ? "otf" : "ttf";
            Path zip = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PACK_NAME);
            Files.createDirectories(zip.getParent());
            String json = "{\"providers\":[{\"type\":\"ttf\",\"file\":\"cleanhud:custom." + ext
                    + "\",\"size\":10.0,\"oversample\":8.0,\"shift\":[0,0]}]}";
            try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
                z.putNextEntry(new ZipEntry("pack.mcmeta"));
                z.write("{\"pack\":{\"pack_format\":46,\"description\":\"NLS font\"}}".getBytes());
                z.putNextEntry(new ZipEntry("assets/cleanhud/font/custom.json"));
                z.write(json.getBytes());
                z.putNextEntry(new ZipEntry("assets/cleanhud/font/custom." + ext));
                Files.copy(font, z);
                z.closeEntry();
            }
            fontPackWritten = true;
        } catch (Exception ignored) {}
    }

    private static void enableFontPack(MinecraftClient mc) {
        if (!fontPackWritten) return;
        var mgr = mc.getResourcePackManager();
        mgr.scanPacks();
        if (mgr.getProfile(PACK_ID) != null && !mgr.getEnabledIds().contains(PACK_ID)) {
            mgr.enable(PACK_ID);
            mc.options.refreshResourcePacks(mgr);
        }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static void load() {
        try (Reader r = Files.newBufferedReader(FILE)) {
            Config c = GSON.fromJson(r, Config.class);
            if (c == null) return;
            applyLayout(c);
            cfg.discordAppId = c.discordAppId == null ? "" : c.discordAppId;
            cfg.fpsBoost = c.fpsBoost; cfg.prev = c.prev;
        } catch (Exception ignored) {}
    }

    public static boolean isDynamicIslandEnabled() { return cfg.dynamicIsland; }
    public static void setDynamicIslandEnabled(boolean value) { cfg.dynamicIsland = value; save(); }
    public static boolean isAnimatedHotbarEnabled() { return cfg.animatedHotbar; }
    public static void setAnimatedHotbarEnabled(boolean value) { cfg.animatedHotbar = value; save(); }
    public static int getElementStyle(String id) { return Math.max(0, Math.min(2, cfg.styles.getOrDefault(id, 0))); }
    public static void cycleElementStyle(String id) { cfg.styles.put(id, (getElementStyle(id) + 1) % 3); save(); }
    public static int getHotbarStyle() { return Math.max(0, Math.min(2, cfg.hotbarStyle)); }
    public static void cycleHotbarStyle() { cfg.hotbarStyle = (cfg.hotbarStyle + 1) % 3; save(); }

    public static boolean isHudEnabled() { return enabled; }
    public static void setHudEnabled(boolean value) { enabled = value; save(); }
    public static boolean isElementEnabled(String id) {
        return switch (id) {
            case "brand" -> cfg.showBrand;
            case "stats" -> cfg.showStats;
            case "coords" -> cfg.showCoords;
            case "time" -> cfg.showTime;
            default -> true;
        };
    }
    public static void setElementEnabled(String id, boolean value) {
        switch (id) {
            case "brand" -> cfg.showBrand = value;
            case "stats" -> cfg.showStats = value;
            case "coords" -> cfg.showCoords = value;
            case "time" -> cfg.showTime = value;
        }
        save();
    }
    public static void toggleFpsBoost() { fpsBoost(MinecraftClient.getInstance(), !cfg.fpsBoost); }
    public static boolean isFontPackActive() { return fontActive; }
    public static void rebuildFontPack() {
        fontPackWritten = false;
        buildFontPack();
        if (MinecraftClient.getInstance() != null) enableFontPack(MinecraftClient.getInstance());
        save();
    }
    public static void cycleAccent() { cfg.accent = (cfg.accent + 1) % ACCENTS.length; save(); }
    public static void saveFromGui() { save(); }

    private static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(cfg, w); } catch (Exception ignored) {}
    }
}
