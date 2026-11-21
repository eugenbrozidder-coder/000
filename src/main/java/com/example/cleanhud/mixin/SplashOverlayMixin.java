package com.example.cleanhud.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Своя заставка загрузки: тёмный фон, логотип NLS и полоса прогресса (без текста: шрифт ещё не загружен). */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
    @Shadow private float progress;

    private static final String[][] LETTERS = {
        {"X...X", "XX..X", "X.X.X", "X..XX", "X...X"},
        {"X....", "X....", "X....", "X....", "XXXXX"},
        {".XXXX", "X....", ".XXX.", "....X", "XXXX."}};

    @Inject(method = "render", at = @At("TAIL"))
    private void nls$render(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        ctx.fill(0, 0, w, h, 0xFF0D1117);
        int cell = Math.max(3, h / 45);
        int totalW = (3 * 5 + 2) * cell, x0 = (w - totalW) / 2, y0 = h / 2 - 5 * cell;
        for (int l = 0; l < 3; l++)
            for (int r = 0; r < 5; r++)
                for (int c = 0; c < 5; c++)
                    if (LETTERS[l][r].charAt(c) == 'X') {
                        int x = x0 + (l * 6 + c) * cell, y = y0 + r * cell;
                        ctx.fill(x, y, x + cell, y + cell, 0xFF4FC3F7);
                    }
        int bw = totalW + 4 * cell, bx = (w - bw) / 2, by = y0 + 7 * cell;
        ctx.fill(bx, by, bx + bw, by + 4, 0xFF222B36);
        ctx.fill(bx, by, bx + (int) (bw * MathHelper.clamp(progress, 0f, 1f)), by + 4, 0xFF4FC3F7);
    }
}
