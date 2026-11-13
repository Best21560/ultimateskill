package com.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Asura Mode "awakening" cutscene (client side).
 *
 *   0-10    black screen + glitch bars
 *  10-45    close-up of the face, slow push in, grey wash
 *  45-75    camera tilts down the body, title text
 *  75-100   camera rises back to the face, white glow builds
 *  100      BLAST (same moment the server deals damage) + white flash
 * 100-150   camera pulls back to full body, red aura, "AWAKENED" banner
 * 150-160   fade out
 *
 * The camera itself is moved by CameraMixin (see camera()).
 * Press Esc during the cutscene to skip it.
 */
public final class AsuraCutscene {
    private static final int T_GLITCH = 10;
    private static final int T_PUSH_END = 45;
    private static final int T_TILT_END = 75;
    private static final int T_RISE_END = Skills.ASURA_DELAY_CUTSCENE; // = blast tick
    private static final int T_HOLD_END = 150;
    private static final int TOTAL = 160;
    private static final int CUT_FOV = 55;

    private static int tick = -1;
    private static int savedFov;
    private static Perspective savedPerspective;
    private static boolean savedHudHidden;

    private static final DustParticleEffect RED_DUST =
            new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.4f);

    private AsuraCutscene() {}

    public static boolean isPlaying() { return tick >= 0; }

    // ------------------------------------------------------------------ control

    public static void start(MinecraftClient client) {
        if (tick >= 0 || client.player == null) return;
        tick = 0;
        savedFov = client.options.getFov().getValue();
        savedPerspective = client.options.getPerspective();
        savedHudHidden = client.options.hudHidden;
        client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        client.options.hudHidden = true;
        client.setScreen(new CutsceneScreen());
        client.player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
    }

    public static void stop(MinecraftClient client, boolean closeScreen) {
        if (tick < 0) return;
        tick = -1;
        if (savedPerspective != null) client.options.setPerspective(savedPerspective);
        if (savedFov > 0) client.options.getFov().setValue(savedFov);
        client.options.hudHidden = savedHudHidden;
        if (closeScreen && client.currentScreen instanceof CutsceneScreen) client.setScreen(null);
    }

    /** Called once per client tick while the cutscene is playing. */
    public static void update(MinecraftClient client) {
        if (tick < 0) return;
        if (client.player == null || client.world == null) { stop(client, true); return; }

        // field of view: fixed during the cutscene, widening back to normal during the pull-back
        int fov = CUT_FOV;
        if (tick >= T_RISE_END) {
            double t = ease(clamp((tick - T_RISE_END) / (float) (T_HOLD_END - T_RISE_END)));
            fov = (int) lerp(t, CUT_FOV, savedFov);
        }
        client.options.getFov().setValue(fov);

        spawnAura(client);
        if (tick == T_PUSH_END) client.player.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 0.8f);
        if (tick == T_RISE_END) blast(client);

        tick++;
        if (tick >= TOTAL) stop(client, true);
    }

    // ------------------------------------------------------------------ camera

    /** Returns {x, y, z, yaw, pitch} for the camera, or null when no cutscene is playing. */
    public static double[] camera(float tickDelta) {
        if (tick < 0) return null;
        ClientPlayerEntity pl = MinecraftClient.getInstance().player;
        if (pl == null) return null;
        float p = tick + tickDelta;

        double h, d, pitch;
        if (p < T_PUSH_END) {
            double t = ease(clamp(p / T_PUSH_END));
            h = 1.55; d = lerp(t, 1.3, 0.95); pitch = lerp(t, 0, 3);
        } else if (p < T_TILT_END) {
            double t = ease(clamp((p - T_PUSH_END) / (T_TILT_END - T_PUSH_END)));
            h = lerp(t, 1.55, 1.1); d = lerp(t, 0.95, 2.0); pitch = lerp(t, 3, 28);
        } else if (p < T_RISE_END) {
            double t = ease(clamp((p - T_TILT_END) / (T_RISE_END - T_TILT_END)));
            h = lerp(t, 1.1, 1.6); d = lerp(t, 2.0, 0.9); pitch = lerp(t, 28, 0);
        } else {
            double t = ease(clamp((p - T_RISE_END) / (T_HOLD_END - T_RISE_END)));
            h = lerp(t, 1.6, 1.5); d = lerp(t, 0.9, 4.5); pitch = lerp(t, 0, -4);
        }

        Vec3d base = pl.getLerpedPos(tickDelta);
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, pl.prevHeadYaw, pl.headYaw);
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        // camera stands in front of the player and looks back at them
        return new double[]{base.x + fx * d, base.y + h, base.z + fz * d, yaw + 180.0f, pitch};
    }

    // ------------------------------------------------------------------ effects

    private static void spawnAura(MinecraftClient client) {
        var rnd = client.world.random;
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        int count = tick < T_PUSH_END ? 0 : (tick < T_RISE_END ? 5 : 12);
        for (int i = 0; i < count; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = 0.5 + rnd.nextDouble() * 0.5;
            client.world.addParticle(RED_DUST,
                    px + Math.cos(ang) * r, py + rnd.nextDouble() * 2.0, pz + Math.sin(ang) * r,
                    0, 0.06 + rnd.nextDouble() * 0.05, 0);
        }
        // tall red "spikes" rising around the player after the blast
        if (tick >= T_RISE_END && tick % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double r = 1.5 + rnd.nextDouble() * 2.0;
                client.world.addParticle(RED_DUST,
                        px + Math.cos(ang) * r, py, pz + Math.sin(ang) * r,
                        0, 0.35 + rnd.nextDouble() * 0.3, 0);
            }
        }
    }

    private static void blast(MinecraftClient client) {
        var rnd = client.world.random;
        double px = client.player.getX();
        double py = client.player.getY() + 1.0;
        double pz = client.player.getZ();
        client.world.addParticle(ParticleTypes.EXPLOSION_EMITTER, px, py, pz, 0, 0, 0);
        for (int i = 0; i < 80; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double speed = 0.3 + rnd.nextDouble() * 0.6;
            client.world.addParticle(RED_DUST, px, py, pz,
                    Math.cos(ang) * speed, (rnd.nextDouble() - 0.3) * 0.4, Math.sin(ang) * speed);
        }
        client.player.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
    }

    // ------------------------------------------------------------------ 2D overlay

    static void renderOverlay(DrawContext ctx, int w, int h, float td) {
        if (tick < 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        float p = tick + td;

        // black fade-in at the start
        if (p < 14) {
            int ba = (int) (255 * (1f - clamp((p - 2) / 12f)));
            if (ba > 0) ctx.fill(0, 0, w, h, ba << 24);
        }
        // glitch bars on the black screen
        if (p < T_GLITCH) {
            Random r = new Random(tick * 31L);
            for (int i = 0; i < 14; i++) {
                int y = r.nextInt(Math.max(1, h));
                int bh = 2 + r.nextInt(h / 10 + 1);
                int x1 = r.nextInt(Math.max(1, w / 2));
                int x2 = Math.min(w, x1 + w / 4 + r.nextInt(Math.max(1, w / 2)));
                ctx.fill(x1, y, x2, Math.min(h, y + bh), r.nextBoolean() ? 0xFFFFFFFF : 0xFF202020);
            }
        }

        // grey wash = washed-out "desaturated" look of the close-up
        float gray = clamp((p - 4) / 8f) * (1f - clamp((p - T_TILT_END) / 10f));
        int ga = (int) (120 * gray);
        if (ga > 0) ctx.fill(0, 0, w, h, (ga << 24) | 0x8C8C8C);

        // red wash after the blast
        float red = clamp((p - T_RISE_END) / 12f) * (1f - clamp((p - T_HOLD_END) / 10f));
        int ra = (int) (105 * red);
        if (ra > 0) ctx.fill(0, 0, w, h, (ra << 24) | 0x8A0A1E);

        // title text while the camera tilts down
        if (p >= 48 && p < 98) {
            float a = clamp((p - 48) / 6f) * (1f - clamp((p - 92) / 6f));
            if (a > 0.05f) {
                String name = mc.player != null ? mc.player.getName().getString() : "";
                int jitter = p < 56 ? (tick % 2 == 0 ? 2 : -2) : 0;
                int cy = (int) (h * 0.50f);
                drawOutlined(ctx, tr, "ASURA", w / 2 + jitter, cy, 3.0f, a);
                drawOutlined(ctx, tr, "AWAKENING", w / 2 + jitter, cy + 28, 3.0f, a);
                drawOutlined(ctx, tr, name, w / 2 + jitter, cy + 56, 1.8f, a);
            }
        }

        // white glow building up to the blast, then fading
        if (p >= 86) {
            float fl = p < T_RISE_END ? clamp((p - 86) / 14f) : 1f - clamp((p - T_RISE_END) / 16f);
            int fa = (int) (235 * fl);
            if (fa > 0) ctx.fill(0, 0, w, h, (fa << 24) | 0xFFE6E6);
        }

        // "AWAKENED" banner
        if (p >= T_RISE_END + 8 && p < T_HOLD_END + 8) {
            float s = ease(clamp((p - (T_RISE_END + 8)) / 10f));
            float a = 1f - clamp((p - T_HOLD_END) / 8f);
            int bw = 230, bh = 36;
            int x = (int) (w / 2f - bw / 2f - (1f - s) * 40f);
            int y = h - (int) (h * 0.12f) - bh - 8;
            int alpha = (int) (255 * s * a);
            if (alpha > 4) {
                ctx.fill(x, y, x + bw, y + bh, ((int) (170 * s * a) << 24));
                ctx.fill(x + 3, y + 3, x + bh - 3, y + bh - 3, (alpha << 24) | 0xB01020);
                ctx.drawCenteredTextWithShadow(tr, "A", x + bh / 2, y + bh / 2 - 4, (alpha << 24) | 0xFFFFFF);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + bh + 6, y + 5, 0);
                ctx.getMatrices().scale(1.6f, 1.6f, 1f);
                ctx.drawText(tr, "AWAKENED", 0, 0, (alpha << 24) | 0xFFFFFF, true);
                ctx.getMatrices().pop();
                String name = mc.player != null ? mc.player.getName().getString() : "";
                ctx.drawText(tr, name, x + bh + 6, y + 24, (alpha << 24) | 0xC8C8C8, true);
            }
        }

        // cinematic black bars (top and bottom)
        float barIn = clamp(p / 10f);
        float barOut = 1f - clamp((p - (TOTAL - 10)) / 10f);
        int barH = (int) (h * 0.12f * barIn * barOut);
        if (barH > 0) {
            ctx.fill(0, 0, w, barH, 0xFF000000);
            ctx.fill(0, h - barH, w, h, 0xFF000000);
        }

        // fade to black at the very end
        if (p > TOTAL - 10) {
            int fa = (int) (255 * clamp((p - (TOTAL - 10)) / 10f));
            if (fa > 0) ctx.fill(0, 0, w, h, fa << 24);
        }
    }

    private static void drawOutlined(DrawContext ctx, TextRenderer tr, String text, int cx, int y, float scale, float a) {
        int alpha = Math.max(4, (int) (255 * a));
        int red = (alpha << 24) | 0xE02030;
        int black = (alpha << 24);
        int tw = tr.getWidth(text);
        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            drawScaled(ctx, tr, text, cx - tw * scale / 2f + o[0] * scale, y + o[1] * scale, scale, black);
        }
        drawScaled(ctx, tr, text, cx - tw * scale / 2f, y, scale, red);
    }

    private static void drawScaled(DrawContext ctx, TextRenderer tr, String text, float x, float y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, text, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------------ math helpers

    private static float clamp(float v) { return MathHelper.clamp(v, 0f, 1f); }
    private static double ease(double t) { return t * t * (3 - 2 * t); }
    private static double lerp(double t, double a, double b) { return a + (b - a) * t; }
}
