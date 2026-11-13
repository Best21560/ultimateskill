package com.example.mixin.client;

import com.example.AsuraCutscene;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tips the local player over (lying on the back) during the Asura cutscene. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void ultimateskill$lieDown(LivingEntity entity, MatrixStack matrices, float animationProgress,
                                       float bodyYaw, float tickDelta, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;
        float angle = AsuraCutscene.lieAngle(tickDelta);
        float[] j = AsuraCutscene.bodyJitter(tickDelta);
        if (j == null) return;
        // lift slightly so the back rests on the ground instead of sinking into it, plus the twitching
        matrices.translate(j[0], 0.14 * Math.abs(angle) / 90.0 + j[1], j[2]);
        if (j[3] != 0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(j[3]));
        if (angle != 0f || j[4] != 0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle + j[4]));
    }
}
