package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Client side: skill keys (Z / X / C), skill bar HUD with damage + cooldown,
 * and the Asura Mode cutscene (face zoom, red aura, tint, blast).
 */
public class UltimateSkillClient implements ClientModInitializer {

    // ---- cutscene timing (ticks, 20 = 1 second)
    private static final int TOTAL = 120;
    private static final int ZOOM_END = 50;
    private static final int BLAST = Skills.ASURA_DELAY; // same moment the server applies damage
    private static final int ZOOM_FOV = 30;

    private static final KeyBinding[] skillKeys = new KeyBinding[3];
    private static final int[] cdLeft = new int[3]; // remaining cooldown in ticks (client side display)

    private static int tick = -1; // cutscene tick, -1 = not playing
    private static int savedFov;
    private static Perspective savedPerspective;

    private static final DustParticleEffect RED_DUST =
            new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.4f);

    @Override
    public void onInitializeClient() {
        int[] defaults = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C};
        for (int i = 0; i < 3; i++) {
            skillKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "Skill: " + Skills.NAMES[i], InputUtil.Type.KEYSYM, defaults[i], "Ultimate Skill Mod"));
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                if (tick >= 0) stopCutscene(client);
                for (int i = 0; i < 3; i++) cdLeft[i] = 0;
                return;
            }
            for (int i = 0; i < 3; i++) {
                if (cdLeft[i] > 0) cdLeft[i]--;
                while (skillKeys[i].wasPressed()) {
                    if (cdLeft[i] == 0 && client.player.isAlive()) {
                        cdLeft[i] = Skills.COOLDOWN_TICKS[i];
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(i);
                        ClientPlayNetworking.send(Skills.USE_SKILL, buf);
                        if (i == 2 && tick < 0) startCutscene(client);
                    }
                }
            }
            if (tick >= 0) updateCutscene(client);
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();

            drawSkillBar(ctx, mc, w, h);

            if (tick < 0) return;
            float p = tick + tickDelta;

            // red-purple tint after the zoom
            float tintIn = MathHelper.clamp((p - ZOOM_END) / 20f, 0f, 1f);
            float tintOut = 1f - MathHelper.clamp((p - (TOTAL - 20)) / 20f, 0f, 1f);
            int tintA = (int) (110 * tintIn * tintOut);
            if (tintA > 0) ctx.fill(0, 0, w, h, (tintA << 24) | 0x5A0A28);

            // flash on blast
            if (p >= BLAST) {
                float f = 1f - MathHelper.clamp((p - BLAST) / 10f, 0f, 1f);
                int flashA = (int) (200 * f);
                if (flashA > 0) ctx.fill(0, 0, w, h, (flashA << 24) | 0xFFC8C8);
            }

            // cinematic black bars
            float barIn = MathHelper.clamp(p / 10f, 0f, 1f);
            float barOut = 1f - MathHelper.clamp((p - (TOTAL - 10)) / 10f, 0f, 1f);
            int barH = (int) (h * 0.16f * barIn * barOut);
            if (barH > 0) {
                ctx.fill(0, 0, w, barH, 0xFF000000);
                ctx.fill(0, h - barH, w, h, 0xFF000000);
            }
        });
    }

    // ------------------------------------------------------------------ skill bar

    private static void drawSkillBar(net.minecraft.client.gui.DrawContext ctx, MinecraftClient mc, int w, int h) {
        final int slotW = 64, slotH = 34, gap = 6;
        int totalW = 3 * slotW + 2 * gap;
        int x0 = w - totalW - 8;
        int y0 = h - 64;
        TextRenderer tr = mc.textRenderer;

        for (int i = 0; i < 3; i++) {
            int x = x0 + i * (slotW + gap);
            boolean ready = cdLeft[i] == 0;

            ctx.fill(x - 1, y0 - 1, x + slotW + 1, y0 + slotH + 1, ready ? 0xFFE0B040 : 0xFF555555);
            ctx.fill(x, y0, x + slotW, y0 + slotH, 0xCC101018);

            if (!ready) {
                float frac = cdLeft[i] / (float) Skills.COOLDOWN_TICKS[i];
                int hh = (int) (slotH * frac);
                ctx.fill(x, y0, x + slotW, y0 + hh, 0xAA000000);
            }

            String key = skillKeys[i].getBoundKeyLocalizedText().getString();
            ctx.drawTextWithShadow(tr, "[" + key + "]", x + 3, y0 + 3, 0xFFFFD060);
            ctx.drawCenteredTextWithShadow(tr, Skills.NAMES[i], x + slotW / 2, y0 + 14, 0xFFFFFFFF);

            if (ready) {
                ctx.drawCenteredTextWithShadow(tr, "READY", x + slotW / 2, y0 + 24, 0xFF60FF60);
            } else {
                String t = String.format(Locale.ROOT, "%.1fs", cdLeft[i] / 20f);
                ctx.drawCenteredTextWithShadow(tr, t, x + slotW / 2, y0 + 24, 0xFFFF6060);
            }

            ctx.drawCenteredTextWithShadow(tr, "DMG " + (int) Skills.DAMAGE[i], x + slotW / 2, y0 + slotH + 3, 0xFFFFA0A0);
            ctx.drawCenteredTextWithShadow(tr, "CD " + (Skills.COOLDOWN_TICKS[i] / 20) + "s", x + slotW / 2, y0 + slotH + 12, 0xFFA0C8FF);
        }
    }

    // ------------------------------------------------------------------ cutscene

    private static void startCutscene(MinecraftClient client) {
        tick = 0;
        savedFov = client.options.getFov().getValue();
        savedPerspective = client.options.getPerspective();
        client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        client.player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
    }

    private static void updateCutscene(MinecraftClient client) {
        float target;
        if (tick <= ZOOM_END) {
            float t = tick / (float) ZOOM_END;
            t = t * t * (3 - 2 * t);
            target = MathHelper.lerp(t, savedFov, ZOOM_FOV);
        } else {
            float t = (tick - ZOOM_END) / (float) (TOTAL - ZOOM_END);
            target = MathHelper.lerp(t, ZOOM_FOV, savedFov);
        }
        client.options.getFov().setValue((int) target);

        spawnAura(client);
        if (tick == BLAST) blast(client);

        tick++;
        if (tick >= TOTAL) stopCutscene(client);
    }

    private static void spawnAura(MinecraftClient client) {
        var rnd = client.world.random;
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
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

    private static void stopCutscene(MinecraftClient client) {
        if (savedPerspective != null) client.options.setPerspective(savedPerspective);
        if (savedFov > 0) client.options.getFov().setValue(savedFov);
        tick = -1;
    }
}
