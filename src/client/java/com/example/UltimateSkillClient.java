package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

/** Client side: skill keys (Z / X / C), settings key (K) and the skill bar HUD. */
public class UltimateSkillClient implements ClientModInitializer {

    private static final KeyBinding[] skillKeys = new KeyBinding[3];
    private static KeyBinding settingsKey;
    private static final int[] cdLeft = new int[3]; // remaining cooldown in ticks (display only)

    // skill 2 (hold to charge)
    private static boolean charging = false;
    private static int chargeTicks = 0;

    @Override
    public void onInitializeClient() {
        int[] defaults = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C};
        for (int i = 0; i < 3; i++) {
            skillKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "Skill: " + Skills.NAMES[i], InputUtil.Type.KEYSYM, defaults[i], "Ultimate Skill Mod"));
        }
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Skill settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "Ultimate Skill Mod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                AsuraCutscene.stop(client, true);
                charging = false;
                chargeTicks = 0;
                for (int i = 0; i < 3; i++) cdLeft[i] = 0;
                return;
            }

            while (settingsKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new SkillSettingsScreen(null));
            }

            for (int i = 0; i < 3; i++) {
                if (cdLeft[i] > 0) cdLeft[i]--;
                if (i == 1) {
                    handleCharge(client);
                    continue;
                }
                while (skillKeys[i].wasPressed()) {
                    if (cdLeft[i] == 0 && client.player.isAlive()) {
                        cdLeft[i] = Skills.cooldownTicks(i);
                        boolean cutscene = i == 2 && SkillConfig.cutscene;
                        int arg = i == 2 ? (cutscene ? Skills.ASURA_DELAY_CUTSCENE : Skills.ASURA_DELAY_QUICK) : 0;
                        sendSkill(i, arg);
                        if (cutscene) AsuraCutscene.start(client);
                    }
                }
            }

            AsuraCutscene.update(client);
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            drawSkillBar(ctx, mc, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        });
    }

    private static void sendSkill(int id, int arg) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(id);
        buf.writeInt(arg);
        ClientPlayNetworking.send(Skills.USE_SKILL, buf);
    }

    /** Skill 2: hold the key to charge, release to leap. */
    private static void handleCharge(MinecraftClient client) {
        KeyBinding k = skillKeys[1];
        boolean pressedEvent = false;
        while (k.wasPressed()) pressedEvent = true;
        boolean down = k.isPressed() && client.currentScreen == null && client.player.isAlive();

        if (!charging) {
            if (pressedEvent && cdLeft[1] == 0 && client.currentScreen == null && client.player.isAlive()) {
                charging = true;
                chargeTicks = 0;
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
            }
            charging = false;
            chargeTicks = 0;
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
            // particles drawn in toward the player's feet
            Fx.spawnVel(client.world, (i % 2 == 0) ? Fx.RED : Fx.BLACK,
                    px + Math.cos(ang) * r, py + 0.1 + rnd.nextDouble() * 0.4, pz + Math.sin(ang) * r,
                    -Math.cos(ang) * 0.08, 0.02, -Math.sin(ang) * 0.08);
        }
    }

    private static void drawSkillBar(DrawContext ctx, MinecraftClient mc, int w, int h) {
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
                float frac = MathHelper.clamp(cdLeft[i] / (float) Skills.cooldownTicks(i), 0f, 1f);
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

            String dmg = "DMG " + (int) Skills.damage(i) + (i == 0 ? " x2" : "");
            ctx.drawCenteredTextWithShadow(tr, dmg, x + slotW / 2, y0 + slotH + 3, 0xFFFFA0A0);
            ctx.drawCenteredTextWithShadow(tr, "CD " + (Skills.cooldownTicks(i) / 20) + "s", x + slotW / 2, y0 + slotH + 12, 0xFFA0C8FF);
        }

        // charge bar above the Leap Slam slot
        if (charging) {
            int x = x0 + (slotW + gap);
            float frac = MathHelper.clamp(chargeTicks / (float) Skills.chargeTicksMax(), 0f, 1f);
            ctx.fill(x - 1, y0 - 9, x + slotW + 1, y0 - 3, 0xFF000000);
            ctx.fill(x, y0 - 8, x + (int) (slotW * frac), y0 - 4, frac >= 1f ? 0xFFFF3030 : 0xFFE08020);
            String t = String.format(Locale.ROOT, "%.0f m", Skills.leapDistance(chargeTicks));
            ctx.drawCenteredTextWithShadow(tr, t, x + slotW / 2, y0 - 20, 0xFFFFFFFF);
        }
    }
}
