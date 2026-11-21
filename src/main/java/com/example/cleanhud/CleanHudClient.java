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
        public int[] prev = null;
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
    private static final Set<String> COMMANDS = Set.of("config", "fps", "rpc", "help");
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
            ctx.fill(0, 0, w, h, 0x90000000);
            ctx.fill(0, 0, 2, h, ACCENTS[cfg.accent % ACCENTS.length]);
            if (el.id().equals("brand")) {
                ctx.fill(4, 3, 6, h - 3, ACCENTS[cfg.accent % ACCENTS.length]);
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

    private static Text text(String s) {
        return fontActive ? Text.literal(s).setStyle(Style.EMPTY.withFont(FONT_ID)) : Text.literal(s);
    }

    private static void applyLayout(Config c) {
        cfg.hx = c.hx; cfg.hy = c.hy; cfg.hs = c.hs == 0 ? 1f : c.hs; cfg.accent = Math.max(0, c.accent);
        cfg.showBrand = c.showBrand; cfg.showStats = c.showStats; cfg.showCoords = c.showCoords; cfg.showTime = c.showTime;
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
                default -> msg(".config save|load|list <имя>, .fps on|off, .rpc <ID>");
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

    public static boolean isAnimatedHotbarEnabled() { return cfg.animatedHotbar; }
    public static void setAnimatedHotbarEnabled(boolean value) { cfg.animatedHotbar = value; save(); }

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
