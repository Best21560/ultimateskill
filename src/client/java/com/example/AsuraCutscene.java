package com.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Asura Mode awakening cutscene (client side).
 *
 *   0-12    the character is knocked down and lies on their back
 *  12-55    lying on the ground, camera drifts to a close-up of the face, energy gathers
 *  55-85    the body is lifted up to standing, camera low and looking up, glow builds
 *  85       BLAST (same moment the server deals damage) + flash
 *  85-150   camera pulls back, red-black pillar of energy, "AWAKENED" banner
 * 150-160   fade out
 *
 * The camera is moved by CameraMixin and the lying pose is applied by LivingEntityRendererMixin.
 * Press Esc during the cutscene to skip it.
 */
public final class AsuraCutscene {
    private static final int T_FALL = 12;
    private static final int T_FACE = 50;
    private static final int T_RISE_START = 55;
    private static final int T_BLAST = Skills.ASURA_DELAY_CUTSCENE; // 85, also standing again
    private static final int T_HOLD_END = 150;
    private static final int TOTAL = 160;

    /**
     * If the character lies FACE DOWN instead of face up, change this to -1.0.
     */
    private static final double LIE_SIGN = 1.0;

    // ---- camera keyframes: time, camera (forward, right, up), focus (forward, right, up), fov
    // "forward" is the direction the character faces; the lying body extends BEHIND the feet (negative forward).
    private static final double[][] KEYS = {
            //  t     camF  camR  camU    fcsF  fcsR  fcsU   fov
            {  0,    3.2,  0.0,  2.2,    0.0,  0.0,  1.0,   60},  // standing, seen from the front
            { 12,    2.4,  1.0,  3.0,   -0.9,  0.0,  0.2,   60},  // lying, seen from above
            { 30,    1.2,  0.5,  2.0,   -1.5,  0.0,  0.2,   55},  // drifting toward the head
            { 50,   -0.9,  0.0,  1.1,   -1.65, 0.0,  0.25,  42},  // face close-up from above
            { 55,   -0.9,  0.0,  1.1,   -1.65, 0.0,  0.25,  42},  // hold
            { 70,    2.6,  0.0,  0.5,    0.0,  0.0,  0.9,   58},  // low angle, body rising
            { 85,    3.2,  0.0,  0.6,    0.0,  0.0,  1.3,   62},  // standing, looking up at them
            {150,    5.8,  0.0,  1.7,    0.0,  0.0,  1.9,   -1},  // pull back (fov = player's normal)
    };

    private static int tick = -1;
    private static int savedFov;
    private static Perspective savedPerspective;
    private static boolean savedHudHidden;

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

        client.options.getFov().setValue((int) fovAt(tick));

        spawnEffects(client);
        if (tick == 3) client.player.playSound(SoundEvents.ENTITY_GENERIC_BIG_FALL, 1.0f, 0.7f);
        if (tick == T_FACE) client.player.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 0.8f);
        if (tick == T_BLAST) blast(client);

        tick++;
        if (tick >= TOTAL) stop(client, true);
    }

    // ------------------------------------------------------------------ pose + camera

    /** Degrees the body is tipped over (0 = standing, 90 = lying on the back). Used by LivingEntityRendererMixin. */
    public static float lieAngle(float tickDelta) {
        if (tick < 0) return 0f;
        float p = tick + tickDelta;
        if (p < T_FALL) return (float) (90.0 * LIE_SIGN * easeOut(clamp(p / T_FALL)));
        if (p < T_RISE_START) return (float) (90.0 * LIE_SIGN);
        if (p < T_BLAST) {
            double t = clamp((p - T_RISE_START) / (T_BLAST - T_RISE_START));
            return (float) (90.0 * LIE_SIGN * (1.0 - ease(t)));
        }
        return 0f;
    }

    private static double fovAt(float p) {
        double[] k = frame(p);
        double fov = k[7];
        return fov < 0 ? savedFov : fov;
    }

    /** Interpolated keyframe values for time p. */
    private static double[] frame(float p) {
        if (p <= KEYS[0][0]) return KEYS[0].clone();
        for (int i = 0; i < KEYS.length - 1; i++) {
            double[] a = KEYS[i], b = KEYS[i + 1];
            if (p <= b[0]) {
                double t = ease(clamp((float) ((p - a[0]) / (b[0] - a[0]))));
                double[] out = new double[8];
                out[0] = p;
                for (int j = 1; j < 8; j++) {
                    double av = a[j], bv = b[j];
                    if (j == 7) { // fov: -1 means "player's normal fov"
                        if (av < 0) av = savedFov;
                        if (bv < 0) bv = savedFov;
                    }
                    out[j] = av + (bv - av) * t;
                }
                return out;
            }
        }
        double[] last = KEYS[KEYS.length - 1].clone();
        if (last[7] < 0) last[7] = savedFov;
        return last;
    }

    /** Returns {x, y, z, yaw, pitch} for the camera, or null when no cutscene is playing. */
    public static double[] camera(float tickDelta) {
        if (tick < 0) return null;
        ClientPlayerEntity pl = MinecraftClient.getInstance().player;
        if (pl == null) return null;
        float p = tick + tickDelta;
        double[] k = frame(p);

        Vec3d base = pl.getLerpedPos(tickDelta);
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, pl.prevBodyYaw, pl.bodyYaw);
        double rad = Math.toRadians(bodyYaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad); // forward
        double rx = -fz, rz = fx;                       // right

        double cx = base.x + fx * k[1] + rx * k[2];
        double cy = base.y + k[3];
        double cz = base.z + fz * k[1] + rz * k[2];
        double tx = base.x + fx * k[4] + rx * k[5];
        double ty = base.y + k[6];
        double tz = base.z + fz * k[4] + rz * k[5];

        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        double hd = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new double[]{cx, cy, cz, yaw, pitch};
    }

    // ------------------------------------------------------------------ effects

    private static void spawnEffects(MinecraftClient client) {
        var rnd = client.world.random;
        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
        float bodyYaw = client.player.bodyYaw;
        double rad = Math.toRadians(bodyYaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);

        if (tick >= 4 && tick < T_BLAST) {
            // energy drawn in toward the lying / rising body
            double mx = px - fx * 0.9, mz = pz - fz * 0.9;
            int n = tick < T_RISE_START ? 2 : 6;
            for (int i = 0; i < n; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double r = 1.5 + rnd.nextDouble() * 1.5;
                double sx = mx + Math.cos(ang) * r, sz = mz + Math.sin(ang) * r;
                double sy = py + 0.1 + rnd.nextDouble() * 1.2;
                Fx.spawnVel(client.world, (i % 2 == 0) ? Fx.RED : Fx.BLACK, sx, sy, sz,
                        (mx - sx) * 0.08, (py + 0.4 - sy) * 0.05, (mz - sz) * 0.08);
            }
        }
        if (tick > T_BLAST) {
            // red-black flames rising around the character
            if (tick % 2 == 0) {
                for (int i = 0; i < 3; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    double r = 0.4 + rnd.nextDouble() * 1.4;
                    Fx.flame(client.world, px + Math.cos(ang) * r, py, pz + Math.sin(ang) * r, 1.0f + rnd.nextFloat() * 1.0f);
                }
            }
            // tall red-black pillar of energy
            for (int i = 0; i < 10; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double r = rnd.nextDouble() * 0.9;
                Fx.spawnVel(client.world, (i % 2 == 0) ? Fx.RED : Fx.BLACK,
                        px + Math.cos(ang) * r, py + 0.1, pz + Math.sin(ang) * r,
                        0, 0.5 + rnd.nextDouble() * 0.5, 0);
            }
            if (tick % 2 == 0) {
                for (int i = 0; i < 6; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    double r = 1.5 + rnd.nextDouble() * 2.0;
                    Fx.spawnVel(client.world, Fx.DARK_RED, px + Math.cos(ang) * r, py, pz + Math.sin(ang) * r,
                            0, 0.35 + rnd.nextDouble() * 0.3, 0);
                }
            }
        }
    }

    private static void blast(MinecraftClient client) {
        var rnd = client.world.random;
        double px = client.player.getX(), py = client.player.getY() + 1.0, pz = client.player.getZ();
        for (int i = 0; i < 90; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double speed = 0.3 + rnd.nextDouble() * 0.6;
            Fx.spawnVel(client.world, (i % 2 == 0) ? Fx.RED : Fx.BLACK, px, py, pz,
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

        // impact flash when knocked down
        if (p < 6) {
            int a = (int) (170 * (1f - p / 6f));
            if (a > 0) ctx.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
        }

        // dark vignette while lying on the ground
        float dark = clamp((p - 3) / 8f) * (1f - clamp((p - T_RISE_START) / 20f));
        int da = (int) (90 * dark);
        if (da > 0) ctx.fill(0, 0, w, h, da << 24);

        // title during the face close-up
        if (p >= 26 && p < 58) {
            float a = clamp((p - 26) / 8f) * (1f - clamp((p - 50) / 8f));
            if (a > 0.05f) {
                String name = mc.player != null ? mc.player.getName().getString() : "";
                int cy = (int) (h * 0.62f);
                drawOutlined(ctx, tr, "ASURA", w / 2, cy, 3.0f, a);
                drawOutlined(ctx, tr, "AWAKENING", w / 2, cy + 28, 3.0f, a);
                drawOutlined(ctx, tr, name, w / 2, cy + 56, 1.8f, a);
            }
        }

        // red-white glow building while the body rises, then the blast flash
        if (p >= 62) {
            float glow = p < T_BLAST ? clamp((p - 62) / 23f) : 1f - clamp((p - T_BLAST) / 18f);
            int ga = (int) (200 * glow * glow);
            if (ga > 0) ctx.fill(0, 0, w, h, (ga << 24) | 0xFFD2D2);
        }

        // red wash after the blast
        float red = clamp((p - T_BLAST) / 12f) * (1f - clamp((p - T_HOLD_END) / 10f));
        int ra = (int) (95 * red);
        if (ra > 0) ctx.fill(0, 0, w, h, (ra << 24) | 0x8A0A1E);

        // "AWAKENED" banner
        if (p >= T_BLAST + 8 && p < T_HOLD_END + 8) {
            float s = (float) ease(clamp((p - (T_BLAST + 8)) / 10f));
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

        // cinematic black bars
        float barIn = clamp(p / 10f);
        float barOut = 1f - clamp((p - (TOTAL - 10)) / 10f);
        int barH = (int) (h * 0.10f * barIn * barOut);
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
    private static double easeOut(double t) { return 1 - (1 - t) * (1 - t); }
}
