package com.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Cinematic for the ultimate (Swoop Grab): the grabbed target is thrown up, the player swoops
 * through it again and again, then slams it down. The timing matches the server combo (see Skills).
 * Time 0 = the moment the target is thrown. Press Esc to skip.
 */
public final class UltCutscene {
    private static final int T_THROW = Skills.ULT_THROW_TICKS;
    private static final int T_SWOOP_END = T_THROW + Skills.ULT_SWOOPS * Skills.ULT_SWOOP_INTERVAL;
    private static final int AFTER_IMPACT = 28;
    private static final int TOTAL_MAX = T_SWOOP_END + Skills.ULT_SLAM_TICKS + 40;
    private static final int BASE_FOV = 62;

    private static int tick = -1;
    private static int impactTick = -1;
    private static int targetId = -1;
    private static Vec3d lastTarget = Vec3d.ZERO;
    private static Vec3d impactPos = null;
    private static int savedFov;
    private static Perspective savedPerspective;
    private static boolean savedHudHidden;

    private UltCutscene() {}

    public static boolean isPlaying() { return tick >= 0; }

    public static void start(MinecraftClient client, int entityId) {
        if (tick >= 0 || client.player == null || AsuraCutscene.isPlaying()) return;
        tick = 0;
        impactTick = -1;
        impactPos = null;
        targetId = entityId;
        Entity e = client.world == null ? null : client.world.getEntityById(entityId);
        lastTarget = e != null ? e.getPos().add(0, e.getHeight() * 0.5, 0) : client.player.getPos().add(0, 3, 0);
        savedFov = client.options.getFov().getValue();
        savedPerspective = client.options.getPerspective();
        savedHudHidden = client.options.hudHidden;
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        client.options.hudHidden = true;
        client.setScreen(new CutsceneScreen());
    }

    public static void stop(MinecraftClient client, boolean closeScreen) {
        if (tick < 0) return;
        tick = -1;
        if (savedPerspective != null) client.options.setPerspective(savedPerspective);
        if (savedFov > 0) client.options.getFov().setValue(savedFov);
        client.options.hudHidden = savedHudHidden;
        if (closeScreen && client.currentScreen instanceof CutsceneScreen) client.setScreen(null);
    }

    /** The server sends a heavy shake at the moment of the final impact. */
    public static void notifyShake(float strength) {
        if (tick >= T_SWOOP_END - 4 && strength >= 2.0f && impactTick < 0) {
            impactTick = tick;
            impactPos = lastTarget;
        }
    }

    public static void update(MinecraftClient client) {
        if (tick < 0) return;
        if (client.player == null || client.world == null) { stop(client, true); return; }

        int fov = BASE_FOV;
        int k = (tick - T_THROW) % Skills.ULT_SWOOP_INTERVAL;
        if (tick >= T_THROW && tick < T_SWOOP_END && (k == 1 || k == 2)) fov = 50;   // punch in on every hit
        if (tick >= T_SWOOP_END) fov = 72;
        client.options.getFov().setValue(fov);

        tick++;
        if (impactTick >= 0 && tick - impactTick >= AFTER_IMPACT) stop(client, true);
        else if (tick >= TOTAL_MAX) stop(client, true);
    }

    // ------------------------------------------------------------------ camera

    private static Vec3d targetCenter(float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(targetId);
        if (e != null && e.isAlive()) {
            lastTarget = e.getLerpedPos(td).add(0, e.getHeight() * 0.5, 0);
        }
        return lastTarget;
    }

    /** Returns {x, y, z, yaw, pitch} for the camera, or null when no cutscene is playing. */
    public static double[] camera(float td) {
        if (tick < 0) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity pl = mc.player;
        if (pl == null) return null;
        float p = tick + td;
        Vec3d t = targetCenter(td);
        Vec3d pp = pl.getLerpedPos(td);
        Vec3d cam, look;

        if (impactTick >= 0 && impactPos != null) {
            // aftermath: slow orbit around the crater
            float q = p - impactTick;
            double ang = 1.0 + q * 0.035;
            Vec3d base = new Vec3d(impactPos.x, pp.y, impactPos.z);
            cam = base.add(Math.cos(ang) * 9.0, 2.6 + q * 0.03, Math.sin(ang) * 9.0);
            look = base.add(0, 0.8, 0);
        } else if (p < T_THROW) {
            // look up from behind the player as the target flies up
            Vec3d away = new Vec3d(pp.x - t.x, 0, pp.z - t.z);
            away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
            cam = pp.add(away.x * 3.4, 0.5 + 0.5 * (p / T_THROW), away.z * 3.4);
            look = t;
        } else if (p < T_SWOOP_END) {
            int n = (int) ((p - T_THROW) / Skills.ULT_SWOOP_INTERVAL);
            float u = ((p - T_THROW) % Skills.ULT_SWOOP_INTERVAL) / Skills.ULT_SWOOP_INTERVAL;
            double a = n * 2.4 + 0.6 + Math.PI / 2;     // sit to the side of the swoop line
            double r = 9.0 - 1.6 * u;
            double hy = (n % 2 == 0) ? 0.7 : -2.2;      // alternate a high and a low angle
            cam = t.add(Math.cos(a) * r, hy + 0.4 * u, Math.sin(a) * r);
            look = t;
        } else {
            // the slam: high angle following the falling target
            double a = Skills.ULT_SWOOPS * 2.4 + 1.6;
            cam = t.add(Math.cos(a) * 7.0, 3.4, Math.sin(a) * 7.0);
            look = t.add(0, -1.0, 0);
        }

        double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new double[]{cam.x, cam.y, cam.z, yaw, pitch};
    }

    // ------------------------------------------------------------------ overlay

    static void renderOverlay(DrawContext ctx, int w, int h, float td) {
        if (tick < 0) return;
        float p = tick + td;

        // white flash at the throw
        if (p < 4) ctx.fill(0, 0, w, h, Ui.argb(0xFFFFFF, 0.8f * (1f - p / 4f)));

        // every swoop hit: white flash + speed lines
        if (p >= T_THROW && p < T_SWOOP_END) {
            float k = (p - T_THROW) % Skills.ULT_SWOOP_INTERVAL;
            if (k >= 1f && k < 3.5f) {
                float f = 1f - (k - 1f) / 2.5f;
                ctx.fill(0, 0, w, h, Ui.argb(0xFFFFFF, 0.42f * f));
                speedLines(ctx, w, h, (int) (p * 7), f);
            }
        }

        // red tint while the target is slammed down
        if (p >= T_SWOOP_END && impactTick < 0) {
            ctx.fill(0, 0, w, h, Ui.argb(0x8A0A1E, 0.22f * Ui.smooth((p - T_SWOOP_END) / 6f)));
        }

        // impact flash
        if (impactTick >= 0) {
            float q = p - impactTick;
            if (q < 14f) ctx.fill(0, 0, w, h, Ui.argb(0xFFF0F0, 0.9f * (1f - q / 14f)));
            if (q < 20f) speedLines(ctx, w, h, impactTick * 13, 1f - q / 20f);
        }

        // cinematic bars
        float in = Ui.smooth(p / 8f);
        float out = impactTick >= 0 ? 1f - Ui.smooth((p - impactTick - (AFTER_IMPACT - 10)) / 10f) : 1f;
        int barH = (int) (h * 0.11f * in * out);
        if (barH > 0) {
            ctx.fill(0, 0, w, barH, 0xFF000000);
            ctx.fill(0, h - barH, w, h, 0xFF000000);
        }

        // fade to black at the end
        float end = impactTick >= 0 ? (p - impactTick - (AFTER_IMPACT - 8)) / 8f : (p - (TOTAL_MAX - 8)) / 8f;
        if (end > 0f) ctx.fill(0, 0, w, h, Ui.argb(0x000000, Math.min(1f, end)));
    }

    private static void speedLines(DrawContext ctx, int w, int h, int seed, float alpha) {
        Random r = new Random(seed);
        float cx = w / 2f, cy = h / 2f;
        float diag = (float) Math.sqrt(cx * cx + cy * cy);
        for (int i = 0; i < 26; i++) {
            float ang = r.nextFloat() * (float) (Math.PI * 2);
            float r0 = diag * (0.3f + 0.25f * r.nextFloat());
            float r1 = diag * (0.75f + 0.3f * r.nextFloat());
            int th = 1 + r.nextInt(2);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation(ang));
            ctx.fill((int) r0, -th, (int) r1, th, Ui.argb(i % 3 == 0 ? 0xFF4050 : 0xFFFFFF, alpha * 0.85f));
            ctx.getMatrices().pop();
        }
    }
}
