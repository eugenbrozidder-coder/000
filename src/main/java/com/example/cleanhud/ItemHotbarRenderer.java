package com.example.cleanhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/** NLS glass-style replacement for the vanilla nine-slot hotbar. */
public final class ItemHotbarRenderer {
    private static float selectedX = 0f;
    private static float visibility = 0f;
    private static boolean initialized = false;
    private static final int CYAN = 0xFF54D6F5;
    private ItemHotbarRenderer() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !CleanHudClient.isAnimatedHotbarEnabled()) return;

        float target = mc.player.getInventory().selectedSlot;
        if (!initialized) { selectedX = target; initialized = true; }
        float delta = Math.max(0.05f, Math.min(1f, tickCounter.getTickDelta(false) * 0.45f));
        selectedX = MathHelper.lerp(delta, selectedX, target);
        visibility = Math.min(1f, visibility + 0.12f);

        int center = mc.getWindow().getScaledWidth() / 2;
        int baseY = mc.getWindow().getScaledHeight() - 38;
        int slotW = 25, gap = 3, totalW = 9 * slotW + 8 * gap;
        int startX = center - totalW / 2;
        int alpha = (int)(visibility * 255f);
        int bg = (alpha << 24) | 0x101722;
        int slot = (int)(visibility * 0xB8) << 24 | 0x1B2635;
        int selected = (int)(visibility * 0xE8) << 24 | 0x183D4B;

        ctx.fill(startX - 7, baseY - 7, startX + totalW + 7, baseY + 31, bg);
        ctx.fill(startX - 7, baseY - 7, startX + totalW + 7, baseY - 5, (int)(visibility * 0xA0) << 24 | 0x54D6F5);
        for (int i = 0; i < 9; i++) {
            int x = startX + i * (slotW + gap);
            boolean active = i == mc.player.getInventory().selectedSlot;
            ctx.fill(x, baseY, x + slotW, baseY + slotW, active ? selected : slot);
            if (active) {
                int glowX = startX + Math.round(selectedX * (slotW + gap));
                ctx.fill(glowX - 1, baseY - 2, glowX + slotW + 1, baseY, CYAN);
                ctx.fill(glowX - 1, baseY + slotW, glowX + slotW + 1, baseY + slotW + 2, CYAN);
                ctx.fill(glowX - 2, baseY, glowX, baseY + slotW, CYAN);
                ctx.fill(glowX + slotW, baseY, glowX + slotW + 2, baseY + slotW, CYAN);
            }
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                int itemSize = active ? 19 : 17;
                int offset = (25 - itemSize) / 2;
                ctx.drawItem(stack, x + offset, baseY + offset);
                if (stack.getCount() > 1) {
                    String count = Integer.toString(stack.getCount());
                    ctx.drawTextWithShadow(mc.textRenderer, count, x + slotW - mc.textRenderer.getWidth(count) - 2,
                            baseY + slotW - 9, 0xFFFFFFFF);
                }
            }
        }
        String key = Integer.toString(mc.player.getInventory().selectedSlot + 1);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, key, center, baseY + 27, 0xFFB9EFFF);
    }
}
