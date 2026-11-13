package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
 * Client side: slot keys (Z / X / C), skill menu key (K) and the skill bar HUD.
 * You start with no skills; open the menu with K and equip them first.
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

    // skill 2 (hold to charge)
    private static boolean charging = false;
    private static int chargeTicks = 0;
    private static int prevChargeTicks = 0;

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

        int[] defaults = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C};
        for (int i = 0; i < 3; i++) {
            slotKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "Skill slot " + (i + 1), InputUtil.Type.KEYSYM, defaults[i], "Ultimate Skill Mod"));
        }
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Skill menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "Ultimate Skill Mod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean inWorld = client.player != null && client.world != null;
            SkillLoadout.worldState(inWorld);
            if (!inWorld) {
                AsuraCutscene.stop(client, true);
                charging = false;
                chargeTicks = 0;
                prevChargeTicks = 0;
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
            for (int i = 0; i < 3; i++) {
                prevAppear[i] = appear[i];
                float target = SkillLoadout.slots[i] >= 0 ? 1f : 0f;
                appear[i] += (target - appear[i]) * 0.3f;
            }
            if (toastTicks > 0) toastTicks--;
            prevChargeTicks = chargeTicks;

            // the charging skill was removed from its slot -> cancel
            if (charging && SkillLoadout.slotOf(1) < 0) { charging = false; chargeTicks = 0; prevChargeTicks = 0; }

            for (int i = 0; i < 3; i++) {
                int skill = SkillLoadout.slots[i];
                KeyBinding key = slotKeys[i];
                if (skill < 0) {
                    while (key.wasPressed()) showToast("Slot " + (i + 1) + " is empty - press K to choose a skill");
                    continue;
                }
                if (skill == 1) {
                    handleCharge(client, key);
                } else {
                    while (key.wasPressed()) useSkill(client, skill);
                }
            }

            AsuraCutscene.update(client);
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

    /** Skills 0 and 2: a single key press. */
    private static void useSkill(MinecraftClient client, int skill) {
        if (cdLeft[skill] > 0 || !client.player.isAlive()) return;
        cdLeft[skill] = Skills.cooldownTicks(skill);
        pressed[skill] = 6;
        boolean cutscene = skill == 2 && SkillConfig.cutscene;
        int arg = skill == 2 ? (cutscene ? Skills.ASURA_DELAY_CUTSCENE : Skills.ASURA_DELAY_QUICK) : 0;
        sendSkill(skill, arg);
        if (cutscene) AsuraCutscene.start(client);
    }

    /** Skill 1 (Leap Slam): hold the key to charge, release to leap. */
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
        int n = 1 + (int) (frac * 4);
        for (int i = 0; i < n; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = 1.2 + rnd.nextDouble() * 0.8;
            Fx.spawnVel(client.world, (i % 2 == 0) ? Fx.RED : Fx.BLACK,
                    px + Math.cos(ang) * r, py + 0.1 + rnd.nextDouble() * 0.4, pz + Math.sin(ang) * r,
                    -Math.cos(ang) * 0.08, 0.02, -Math.sin(ang) * 0.08);
        }
    }

    // ------------------------------------------------------------------ HUD

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

            float cd = Math.max(0f, cdLeft[skill] - td);
            boolean ready = cdLeft[skill] == 0;
            float total = Skills.cooldownTicks(skill);

            int border = ready ? 0xD03040 : 0x606878;
            if (pressed[skill] > 0) border = Ui.mix(border, 0xFFFFFF, pressed[skill] / 6f);
            int fill = ready ? 0x1A0F14 : 0x101218;

            Ui.framed(ctx, x, y, slotW, slotH, Ui.argb(fill, 0.86f * f), Ui.argb(border, f));
            ctx.fill(x + 3, y + 3, x + slotW - 3, y + 4, Ui.argb(ready ? 0xFF4050 : 0x707888, f)); // accent line

            ctx.drawTextWithShadow(tr, "[" + slotKeys[i].getBoundKeyLocalizedText().getString() + "]", x + 4, y + 7, Ui.argb(0xFFD060, f));
            ctx.drawCenteredTextWithShadow(tr, Skills.NAMES[skill], x + slotW / 2, y + 19, Ui.argb(0xFFFFFF, f));

            // smooth cooldown sweep (darkens from the top, shrinking as the cooldown runs out)
            if (!ready) {
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

            String dmg = "DMG " + (int) Skills.damage(skill) + (skill == 0 ? " x2" : "");
            ctx.drawCenteredTextWithShadow(tr, dmg, x + slotW / 2, y0 + slotH + 3, Ui.argb(0xFFA0A0, f));
            ctx.drawCenteredTextWithShadow(tr, "CD " + (Skills.cooldownTicks(skill) / 20) + "s", x + slotW / 2, y0 + slotH + 12, Ui.argb(0xA0C8FF, f));

            // charge bar above the Leap Slam slot
            if (skill == 1 && charging) {
                float c = MathHelper.lerp(td, prevChargeTicks, chargeTicks);
                float frac2 = MathHelper.clamp(c / Skills.chargeTicksMax(), 0f, 1f);
                int bw = slotW;
                Ui.panel(ctx, x - 1, y0 - 11, bw + 2, 8, Ui.argb(0x000000, 0.8f));
                int col = Ui.mix(0xE09020, 0xFF2030, frac2);
                ctx.fill(x, y0 - 10, x + (int) (bw * frac2), y0 - 4, Ui.argb(col, 1f));
                String dist = String.format(Locale.ROOT, "%.0f m", Skills.leapDistance((int) c));
                ctx.drawCenteredTextWithShadow(tr, dist, x + slotW / 2, y0 - 22, Ui.argb(0xFFFFFF, 1f));
            }
        }
    }
}
