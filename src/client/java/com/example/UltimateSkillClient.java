package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Ultimate skill (client-side only), Minecraft 1.20.1 + Fabric (Yarn mappings).
 * Press R:
 *  1) camera goes to the front of the player and zooms into the face (FOV 30)
 *  2) zoom out, screen tints dark red-purple, red aura particles intensify
 *  3) blast with flash and explosion sound
 *  4) camera and FOV are restored
 */
public class UltimateSkillClient implements ClientModInitializer {

    // ความยาวคัตซีนเป็น tick (20 tick = 1 วินาที)
    private static final int TOTAL = 120;     // ทั้งหมด 6 วินาที
    private static final int ZOOM_END = 50;   // ซูมหน้าจบที่ tick นี้
    private static final int BLAST = 70;      // จังหวะระเบิด
    private static final int ZOOM_FOV = 30;   // ค่า FOV ต่ำสุดที่ Minecraft ยอมให้

    private static KeyBinding ultKey;
    private static int tick = -1;             // -1 = ไม่ได้เล่นอยู่
    private static int savedFov;
    private static Perspective savedPerspective;

    private static final DustParticleEffect RED_DUST =
            new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.4f);

    @Override
    public void onInitializeClient() {
        ultKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Ultimate Skill", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "Ultimate Skill Mod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                if (tick >= 0) stop(client);
                return;
            }
            while (ultKey.wasPressed()) {
                if (tick < 0) start(client);
            }
            if (tick >= 0) update(client);
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (tick < 0) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            float p = tick + tickDelta;

            // สีแดงม่วงคลุมจอหลังซูมหน้า
            float tintIn = MathHelper.clamp((p - ZOOM_END) / 20f, 0f, 1f);
            float tintOut = 1f - MathHelper.clamp((p - (TOTAL - 20)) / 20f, 0f, 1f);
            int tintA = (int) (110 * tintIn * tintOut);
            if (tintA > 0) ctx.fill(0, 0, w, h, (tintA << 24) | 0x5A0A28);

            // แฟลชขาวแดงตอนระเบิด
            if (p >= BLAST) {
                float f = 1f - MathHelper.clamp((p - BLAST) / 10f, 0f, 1f);
                int flashA = (int) (200 * f);
                if (flashA > 0) ctx.fill(0, 0, w, h, (flashA << 24) | 0xFFC8C8);
            }

            // แถบดำบนล่างแบบหนัง (เลื่อนเข้า-ออก) วาดทีหลังสุดเพื่อบังแถบ HUD บางส่วน
            float barIn = MathHelper.clamp(p / 10f, 0f, 1f);
            float barOut = 1f - MathHelper.clamp((p - (TOTAL - 10)) / 10f, 0f, 1f);
            int barH = (int) (h * 0.16f * barIn * barOut);
            if (barH > 0) {
                ctx.fill(0, 0, w, barH, 0xFF000000);
                ctx.fill(0, h - barH, w, h, 0xFF000000);
            }
        });
    }

    private static void start(MinecraftClient client) {
        tick = 0;
        savedFov = client.options.getFov().getValue();
        savedPerspective = client.options.getPerspective();
        client.options.setPerspective(Perspective.THIRD_PERSON_FRONT); // กล้องอยู่ด้านหน้าหันมาที่หน้า
        client.player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
    }

    private static void update(MinecraftClient client) {
        // ซูม FOV เข้า แล้วค่อยๆ ถอยกลับ
        float target;
        if (tick <= ZOOM_END) {
            float t = tick / (float) ZOOM_END;
            t = t * t * (3 - 2 * t); // smoothstep
            target = MathHelper.lerp(t, savedFov, ZOOM_FOV);
        } else {
            float t = (tick - ZOOM_END) / (float) (TOTAL - ZOOM_END);
            target = MathHelper.lerp(t, ZOOM_FOV, savedFov);
        }
        client.options.getFov().setValue((int) target);

        spawnAura(client);
        if (tick == BLAST) blast(client);

        tick++;
        if (tick >= TOTAL) stop(client);
    }

    private static void spawnAura(MinecraftClient client) {
        var rnd = client.world.random;
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        // ยิ่งเลยช่วงซูมไป ออร่ายิ่งแรง
        int count = tick < ZOOM_END ? 4 : 10;
        for (int i = 0; i < count; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = 0.5 + rnd.nextDouble() * 0.4;
            client.world.addParticle(RED_DUST,
                    px + Math.cos(ang) * r, py + rnd.nextDouble() * 2.0, pz + Math.sin(ang) * r,
                    0, 0.06 + rnd.nextDouble() * 0.05, 0);
        }
        if (tick % 2 == 0) {
            client.world.addParticle(ParticleTypes.FLAME,
                    px + (rnd.nextDouble() - 0.5) * 0.8, py + 1.9, pz + (rnd.nextDouble() - 0.5) * 0.8,
                    0, 0.08, 0);
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

    private static void stop(MinecraftClient client) {
        if (savedPerspective != null) client.options.setPerspective(savedPerspective);
        if (savedFov > 0) client.options.getFov().setValue(savedFov);
        tick = -1;
    }
}
