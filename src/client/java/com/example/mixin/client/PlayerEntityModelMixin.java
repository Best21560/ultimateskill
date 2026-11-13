package com.example.mixin.client;

import com.example.AsuraCutscene;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the local player's arms, legs and head during the Asura cutscene (twitching, struggling, power stance). */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void ultimateskill$cutscenePose(LivingEntity entity, float limbAngle, float limbDistance,
                                            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;
        float[] l = AsuraCutscene.limbPose();
        if (l == null) return;
        PlayerEntityModel<?> m = (PlayerEntityModel<?>) (Object) this;
        m.rightArm.pitch = l[0];
        m.rightArm.roll = l[1];
        m.leftArm.pitch = l[2];
        m.leftArm.roll = l[3];
        m.rightLeg.pitch = l[4];
        m.rightLeg.roll = l[5];
        m.leftLeg.pitch = l[6];
        m.leftLeg.roll = l[7];
        m.head.pitch = l[8];
        m.head.roll = l[9];
        // the overlay layers (sleeves, pants, hat) must follow
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftPants.copyTransform(m.leftLeg);
        m.rightPants.copyTransform(m.rightLeg);
        m.hat.copyTransform(m.head);
    }
}
