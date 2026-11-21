package com.example.cleanhud.mixin;

import com.example.cleanhud.CleanHudClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    /** Сдвиг и масштаб предмета в руке (чисто визуально, только у вас на экране). */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void cleanhud$transform(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                    float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        CleanHudClient.Config c = CleanHudClient.cfg;
        float side = hand == Hand.MAIN_HAND ? 1f : -1f;
        matrices.translate(c.hx * 0.004f * side, -c.hy * 0.004f, 0f);
        matrices.scale(c.hs, c.hs, c.hs);
    }
}
