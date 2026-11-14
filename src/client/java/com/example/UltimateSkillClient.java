package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Client side: slot keys (Z / X / C), skill menu key (K), skill bar HUD and the server messages.
 * You start with no skills; open the menu with K and equip them first.
 *   skill 0 Rampage     : hold the key to run through everything in front
 *   skill 1 Meteor Slam : hold to charge, release to leap
 *   skill 2 Swoop Grab  : press once (ultimate)
 * The Asura second life is a passive and needs no key.
 */
public class UltimateSkillClient implements ClientModInitializer {

    private static final KeyBinding[] slotKeys = new KeyBinding[3];
    private static KeyBinding menuKey;

    // per skill id
    private static final int[] cdLeft = new int[3];   // remaining cooldown in ticks
    private static final int[] flash = new int[3];    // "ready again" flash timer
    private static final int[] pressed = new int[3];  // "just used" flash timer

    // per slot (smooth appear animation)
    private static final float[] appear = new float[3];
    private static final float[] prevAppear = new float[3];

    // skill 1 (hold to charge)
    private static boolean charging = false;
    private static int chargeTicks = 0;
    private static int prevChargeTicks = 0;

    // skill 0 (hold to run)
    private static boolean running = false;
    private static int runLeft = 0;
    private static int prevRunLeft = 0;

    // passive Asura (second life)
    private static int passiveCd = 0;
    private static int passiveFlash = 0;

    // small message above the skill bar
    private static String toast = null;
    private static int toastTicks = 0;

    private static final String[] SLOT_KEY_NAMES = {"Z", "X", "C"};

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
        reg.register(ModParticles.CLAW, sp -> new FxParticle.Factory(sp, FxParticle.Kind.CLAW));
        reg.register(ModParticles.CRACK, sp -> new FxParticle.Factory(sp, FxParticle.Kind.CRACK));
        reg.register(ModParticles.FLAME, sp -> new FxParticle.Factory(sp, FxParticle.Kind.FLAME));
        reg.register(ModParticles.RING, sp -> new FxParticle.Factory(sp, FxParticle.Kind.RING));
        reg.register(ModParticles.BURST, sp -> new FxParticle.Factory(sp, FxParticle.Kind.BURST));
        reg.register(ModParticles.DUST, sp -> new FxParticle.Factory(sp, FxParticle.Kind.DUST));
        reg.register(ModParticles.SPARK, sp -> new FxParticle.Factory(sp, FxParticle.Kind.SPARK));
        reg.register(ModParticles.ARC, sp -> new FxParticle.Factory(sp, FxParticle.Kind.ARC));

        int[] defaults = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C};
        for (int i = 0; i < 3; i++) {
            slotKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "Skill slot " + (i + 1), InputUtil.Type.KEYSYM, defaults[i], "Ultimate Skill Mod"));
        }
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Skill menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "Ultimate Skill Mod"));

        // ---- messages from the server
        ClientPlayNetworking.registerGlobalReceiver(Skills.ULT_CUTSCENE, (client, handler, buf, sender) -> {
            int targetId = buf.readInt();
            client.execute(() -> UltCutscene.start(client, targetId));
        });
        ClientPlayNetworking.registerGlobalReceiver(Skills.REVIVE, (client, handler, buf, sender) -> {
            int delay = buf.readInt();
            client.execute(() -> {
                UltCutscene.stop(client, true);
                if (delay >= 50) AsuraCutscene.start(client);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Skills.PASSIVE, (client, handler, buf, sender) -> {
            int ticks = buf.readInt();
            client.execute(() -> passiveCd = ticks);
        });
        ClientPlayNetworking.registerGlobalReceiver(Skills.SHAKE, (client, handler, buf, sender) -> {
            float strength = buf.readFloat();
            int ticks = buf.readInt();
            client.execute(() -> {
                ScreenShake.add(strength, ticks);
                UltCutscene.notifyShake(strength);
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean inWorld = client.player != null && client.world != null;
            SkillLoadout.worldState(inWorld);
            if (!inWorld) {
                AsuraCutscene.stop(client, true);
                UltCutscene.stop(client, true);
                charging = false;
                running = false;
                PoseFx.running = false;
                PoseFx.dashTicks = 0;
                chargeTicks = 0;
                prevChargeTicks = 0;
                runLeft = 0;
                prevRunLeft = 0;
                passiveCd = 0;
                for (int i = 0; i < 3; i++) { cdLeft[i] = 0; flash[i] = 0; pressed[i] = 0; appear[i] = 0; prevAppear[i] = 0; }
                return;
            }

            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new SkillSelectScreen(null));
            }

            // timers + smooth animations
            for (int id = 0; id < 3; id++) {
                if (cdLeft[id] > 0) {
                    cdLeft[id]--;
                    if (cdLeft[id] == 0) flash[id] = 10;
                }
                if (flash[id] > 0) flash[id]--;
                if (pressed[id] > 0) pressed[id]--;
            }
            if (passiveCd > 0) {
                passiveCd--;
                if (passiveCd == 0) passiveFlash = 12;
            }
            if (passiveFlash > 0) passiveFlash--;
            for (int i = 0; i < 3; i++) {
                prevAppear[i] = appear[i];
                float target = SkillLoadout.slots[i] >= 0 ? 1f : 0f;
                appear[i] += (target - appear[i]) * 0.3f;
            }
            if (toastTicks > 0) toastTicks--;
            prevChargeTicks = chargeTicks;
            ScreenShake.tick();
            PoseFx.tick();

            // a held skill was removed from its slot -> cancel
            if (charging && SkillLoadout.slotOf(1) < 0) { charging = false; chargeTicks = 0; prevChargeTicks = 0; }
            if (running && SkillLoadout.slotOf(0) < 0) stopRun(true);

            for (int i = 0; i < 3; i++) {
                int skill = SkillLoadout.slots[i];
                KeyBinding key = slotKeys[i];
                if (skill < 0) {
                    while (key.wasPressed()) showToast("Slot " + (i + 1) + " is empty - press K to choose a skill");
                    continue;
                }
                switch (skill) {
                    case 0 -> handleRun(client, key);
                    case 1 -> handleCharge(client, key);
                    default -> {
                        while (key.wasPressed()) useUltimate(client);
                    }
                }
            }

            AsuraCutscene.update(client);
            UltCutscene.update(client);
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            drawSkillBar(ctx, mc, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), tickDelta);
        });
    }

    // ------------------------------------------------------------------ using skills

    private static void showToast(String text) {
        toast = text;
        toastTicks = 50;
    }

    private static void sendSkill(int id, int arg) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(id);
        buf.writeInt(arg);
        ClientPlayNetworking.send(Skills.USE_SKILL, buf);
    }

    /** Skill 0 (Rampage): hold the key to run, release to stop (or after the maximum time). */
    private static void handleRun(MinecraftClient client, KeyBinding k) {
        boolean pressedEvent = false;
        while (k.wasPressed()) pressedEvent = true;
        boolean down = k.isPressed() && client.currentScreen == null && client.player.isAlive();

        if (!running) {
            if (pressedEvent && cdLeft[0] == 0 && client.currentScreen == null && client.player.isAlive()) {
                running = true;
                runLeft = Skills.rampageTicks();
                prevRunLeft = runLeft;
                pressed[0] = 6;
                PoseFx.running = true;
                sendSkill(0, 1);
            }
            return;
        }
        prevRunLeft = runLeft;
        runLeft--;
        if (!down || runLeft <= 0 || !client.player.isAlive()) stopRun(true);
    }

    private static void stopRun(boolean send) {
        if (!running) return;
        running = false;
        PoseFx.running = false;
        cdLeft[0] = Skills.cooldownTicks(0);
        if (send) sendSkill(0, 0);
    }

    /** Skill 1 (Meteor Slam): hold the key to charge, release to leap. */
    private static void handleCharge(MinecraftClient client, KeyBinding k) {
        boolean pressedEvent = false;
        while (k.wasPressed()) pressedEvent = true;
        boolean down = k.isPressed() && client.currentScreen == null && client.player.isAlive();

        if (!charging) {
            if (pressedEvent && cdLeft[1] == 0 && client.currentScreen == null && client.player.isAlive()) {
                charging = true;
                chargeTicks = 0;
                prevChargeTicks = 0;
            }
            return;
        }

        if (down) {
            chargeTicks = Math.min(chargeTicks + 1, Skills.chargeTicksMax());
            chargeFx(client);
        } else {
            if (client.currentScreen == null && client.player.isAlive()) {
                sendSkill(1, chargeTicks);
                cdLeft[1] = Skills.cooldownTicks(1);
                pressed[1] = 6;
            }
            charging = false;
            chargeTicks = 0;
            prevChargeTicks = 0;
        }
    }

    private static void chargeFx(MinecraftClient client) {
        float frac = chargeTicks / (float) Skills.chargeTicksMax();
        var rnd = client.world.random;
        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
        int n = 1 + (int) (frac * 3);
        for (int i = 0; i < n; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = 1.4 + rnd.nextDouble() * 0.9;
            Fx.spark(client.world, px + Math.cos(ang) * r, py + 0.2 + rnd.nextDouble() * 0.5, pz + Math.sin(ang) * r,
                    -Math.cos(ang) * 0.1, 0.03, -Math.sin(ang) * 0.1);
        }
        if (chargeTicks % 10 == 0) Fx.ring(client.world, px, py, pz, 1.2f + frac * 1.5f);
    }

    /** Skill 2 (Swoop Grab, the ultimate): a single key press. */
    private static void useUltimate(MinecraftClient client) {
        if (cdLeft[2] > 0 || !client.player.isAlive()) return;
        cdLeft[2] = Skills.cooldownTicks(2);
        pressed[2] = 6;
        PoseFx.dashTicks = (int) Math.ceil(Skills.dashDistance() / Skills.ULT_SPEED) + 3;
        sendSkill(2, SkillConfig.ultCutscene ? 1 : 0);
    }

    // ------------------------------------------------------------------ HUD

    private static String clock(int ticks) {
        int s = (ticks + 19) / 20;
        return s >= 60 ? String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60) : s + "s";
    }

    private static void drawSkillBar(DrawContext ctx, MinecraftClient mc, int w, int h, float td) {
        final int slotW = 62, slotH = 42, gap = 6;
        int totalW = 3 * slotW + 2 * gap;
        int x0 = w - totalW - 10;
        int y0 = h - 70;
        TextRenderer tr = mc.textRenderer;
        long ms = System.currentTimeMillis();

        // hint while nothing is equipped
        if (!SkillLoadout.anyEquipped()) {
            float pulse = 0.55f + 0.35f * (float) Math.sin(ms / 450.0);
            ctx.drawCenteredTextWithShadow(tr, "Press K to choose your skills", x0 + totalW / 2, y0 - 14, Ui.argb(0xFFD060, pulse));
        } else if (toastTicks > 0 && toast != null) {
            float a = Math.min(1f, toastTicks / 10f);
            ctx.drawCenteredTextWithShadow(tr, toast, x0 + totalW / 2, y0 - 14, Ui.argb(0xFF8080, a));
        }
        if (!SkillLoadout.anyEquipped() && toastTicks > 0 && toast != null) {
            float a = Math.min(1f, toastTicks / 10f);
            ctx.drawCenteredTextWithShadow(tr, toast, x0 + totalW / 2, y0 - 26, Ui.argb(0xFF8080, a));
        }

        drawPassive(ctx, tr, x0 - 68, y0, td);

        for (int i = 0; i < 3; i++) {
            int x = x0 + i * (slotW + gap);
            int skill = SkillLoadout.slots[i];

            if (skill < 0) {
                // empty slot: quiet outline
                Ui.framed(ctx, x, y0, slotW, slotH, Ui.argb(0x0C0E14, 0.45f), Ui.argb(0x707888, 0.45f));
                ctx.drawTextWithShadow(tr, "[" + SLOT_KEY_NAMES[i] + "]", x + 4, y0 + 4, Ui.argb(0x909090, 0.7f));
                ctx.drawCenteredTextWithShadow(tr, "empty", x + slotW / 2, y0 + 20, Ui.argb(0x808080, 0.6f));
                continue;
            }

            float f = Ui.smooth(MathHelper.lerp(td, prevAppear[i], appear[i]));
            int y = y0 + Math.round((1f - f) * 8f);

            boolean active = skill == 0 && running;
            float cd = Math.max(0f, cdLeft[skill] - td);
            boolean ready = cdLeft[skill] == 0 && !active;
            float total = Skills.cooldownTicks(skill);

            int border = active ? 0xFF6040 : (ready ? 0xD03040 : 0x606878);
            if (pressed[skill] > 0) border = Ui.mix(border, 0xFFFFFF, pressed[skill] / 6f);
            int fill = ready || active ? 0x1A0F14 : 0x101218;

            Ui.framed(ctx, x, y, slotW, slotH, Ui.argb(fill, 0.86f * f), Ui.argb(border, f));
            ctx.fill(x + 3, y + 3, x + slotW - 3, y + 4, Ui.argb(ready || active ? 0xFF4050 : 0x707888, f)); // accent line

            ctx.drawTextWithShadow(tr, "[" + slotKeys[i].getBoundKeyLocalizedText().getString() + "]", x + 4, y + 7, Ui.argb(0xFFD060, f));
            ctx.drawCenteredTextWithShadow(tr, Skills.NAMES[skill], x + slotW / 2, y + 19, Ui.argb(0xFFFFFF, f));

            if (active) {
                float rl = MathHelper.lerp(td, prevRunLeft, runLeft);
                String t = String.format(Locale.ROOT, "%.1fs", Math.max(0f, rl) / 20f);
                ctx.drawCenteredTextWithShadow(tr, t, x + slotW / 2, y + 30, Ui.argb(0xFFA060, f));
            } else if (!ready) {
                // smooth cooldown sweep (darkens from the top, shrinking as the cooldown runs out)
                float frac = MathHelper.clamp(cd / total, 0f, 1f);
                int hh = (int) ((slotH - 2) * frac);
                ctx.fill(x + 1, y + 1, x + slotW - 1, y + 1 + hh, Ui.argb(0x000000, 0.62f * f));
                String t = String.format(Locale.ROOT, "%.1fs", cd / 20f);
                ctx.drawCenteredTextWithShadow(tr, t, x + slotW / 2, y + 30, Ui.argb(0xFF7070, f));
            } else {
                ctx.drawCenteredTextWithShadow(tr, "READY", x + slotW / 2, y + 30, Ui.argb(0x60FF90, f));
            }

            // soft white flash when the skill becomes ready again
            if (flash[skill] > 0) {
                float a = Math.max(0f, (flash[skill] - td) / 10f) * 0.45f * f;
                ctx.fill(x + 1, y + 1, x + slotW - 1, y + slotH - 1, Ui.argb(0xFFFFFF, a));
            }

            ctx.drawCenteredTextWithShadow(tr, Skills.damageLabel(skill), x + slotW / 2, y0 + slotH + 3, Ui.argb(0xFFA0A0, f));
            ctx.drawCenteredTextWithShadow(tr, "CD " + (Skills.cooldownTicks(skill) / 20) + "s", x + slotW / 2, y0 + slotH + 12, Ui.argb(0xA0C8FF, f));

            // draining bar above the Rampage slot while running
            if (active) {
                float rl = MathHelper.lerp(td, prevRunLeft, runLeft);
                float frac2 = MathHelper.clamp(rl / Skills.rampageTicks(), 0f, 1f);
                Ui.panel(ctx, x - 1, y0 - 11, slotW + 2, 8, Ui.argb(0x000000, 0.8f));
                ctx.fill(x, y0 - 10, x + (int) (slotW * frac2), y0 - 4, Ui.argb(Ui.mix(0xFF2030, 0xFFA030, frac2), 1f));
            }

            // charge bar above the Meteor Slam slot
            if (skill == 1 && charging) {
                float c = MathHelper.lerp(td, prevChargeTicks, chargeTicks);
                float frac2 = MathHelper.clamp(c / Skills.chargeTicksMax(), 0f, 1f);
                Ui.panel(ctx, x - 1, y0 - 11, slotW + 2, 8, Ui.argb(0x000000, 0.8f));
                int col = Ui.mix(0xE09020, 0xFF2030, frac2);
                ctx.fill(x, y0 - 10, x + (int) (slotW * frac2), y0 - 4, Ui.argb(col, 1f));
                String dist = String.format(Locale.ROOT, "%.0f m", Skills.leapDistance((int) c));
                ctx.drawCenteredTextWithShadow(tr, dist, x + slotW / 2, y0 - 22, Ui.argb(0xFFFFFF, 1f));
            }
        }
    }

    /** Small box for the passive second life (Asura). */
    private static void drawPassive(DrawContext ctx, TextRenderer tr, int x, int y, float td) {
        final int w = 62, h = 42;
        boolean ready = passiveCd <= 0;
        int border = ready ? 0xB452FF : 0x606878;
        Ui.framed(ctx, x, y, w, h, Ui.argb(0x120C1A, 0.8f), Ui.argb(border, 0.9f));
        ctx.fill(x + 3, y + 3, x + w - 3, y + 4, Ui.argb(ready ? 0xB452FF : 0x707888, 0.9f));
        ctx.drawCenteredTextWithShadow(tr, "ASURA", x + w / 2, y + 8, Ui.argb(ready ? 0xFFFFFF : 0xB0B0B0, 1f));
        ctx.drawCenteredTextWithShadow(tr, "2nd life", x + w / 2, y + 18, Ui.argb(0xC8A0FF, ready ? 1f : 0.6f));
        if (ready) {
            ctx.drawCenteredTextWithShadow(tr, "READY", x + w / 2, y + 29, Ui.argb(0x60FF90, 1f));
        } else {
            ctx.drawCenteredTextWithShadow(tr, clock(passiveCd), x + w / 2, y + 29, Ui.argb(0xFF7070, 1f));
        }
        if (passiveFlash > 0) {
            float a = Math.max(0f, (passiveFlash - td) / 12f) * 0.5f;
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, Ui.argb(0xFFFFFF, a));
        }
        ctx.drawCenteredTextWithShadow(tr, "passive", x + w / 2, y + h + 3, Ui.argb(0xA0A0B8, 0.8f));
    }
}
