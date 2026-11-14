package com.example.mixin.client;

import com.example.AsuraCutscene;
import com.example.ScreenShake;
import com.example.UltCutscene;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the cutscenes place and rotate the camera freely, and adds the screen shake. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("RETURN"))
    private void ultimateskill$cutsceneCamera(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                              boolean inverseView, float tickDelta, CallbackInfo ci) {
        double[] c = UltCutscene.camera(tickDelta);
        if (c == null) c = AsuraCutscene.camera(tickDelta);
        float[] sh = ScreenShake.offset();
        if (c != null) {
            this.setPos(c[0], c[1], c[2]);
            this.setRotation((float) c[3] + (sh != null ? sh[0] : 0f), (float) c[4] + (sh != null ? sh[1] : 0f));
        } else if (sh != null) {
            Camera self = (Camera) (Object) this;
            this.setRotation(self.getYaw() + sh[0], self.getPitch() + sh[1]);
        }
    }
}
