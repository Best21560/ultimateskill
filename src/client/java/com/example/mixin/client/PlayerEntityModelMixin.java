package com.example.mixin.client;

import com.example.AsuraCutscene;
import com.example.PoseFx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the local player's arms, legs and head (revive cutscene, Rampage run, dash wind-up). */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void ultimateskill$pose(LivingEntity entity, float limbAngle, float limbDistance,
                                    float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;
        float[] l = AsuraCutscene.limbPose();
        if (l == null) l = PoseFx.limbs();
        if (l == null) return;
        PlayerEntityModel<?> m = (PlayerEntityModel<?>) (Object) this;
        set(m.rightArm, l[0], l[1]);
        set(m.leftArm, l[2], l[3]);
        set(m.rightLeg, l[4], l[5]);
        set(m.leftLeg, l[6], l[7]);
        set(m.head, l[8], l[9]);
        // the overlay layers (sleeves, pants, hat) must follow
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftPants.copyTransform(m.leftLeg);
        m.rightPants.copyTransform(m.rightLeg);
        m.hat.copyTransform(m.head);
    }

    private static void set(ModelPart part, float pitch, float roll) {
        if (!Float.isNaN(pitch)) part.pitch = pitch;
        if (!Float.isNaN(roll)) part.roll = roll;
    }
}
