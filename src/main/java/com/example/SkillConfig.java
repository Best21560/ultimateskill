package com.example;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** User-editable skill settings, stored in config/ultimateskill.properties. */
public final class SkillConfig {
    public static final float[] DAMAGE = {8.0f, 12.0f, 50.0f};
    public static final int[] COOLDOWN_SECONDS = {3, 8, 30};
    public static float buffAttack = 5.0f;
    public static float buffArmor = 5.0f;
    public static float buffSpeed = 0.05f;
    public static int buffSeconds = 15;
    public static boolean cutscene = true;
    public static float dashDistance = 5.0f;
    public static float leapMaxDistance = 20.0f;
    public static int chargeSeconds = 5;

    private SkillConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("ultimateskill.properties");
    }

    public static void resetDefaults() {
        DAMAGE[0] = 8.0f; DAMAGE[1] = 12.0f; DAMAGE[2] = 50.0f;
        COOLDOWN_SECONDS[0] = 3; COOLDOWN_SECONDS[1] = 8; COOLDOWN_SECONDS[2] = 30;
        buffAttack = 5.0f;
        buffArmor = 5.0f;
        buffSpeed = 0.05f;
        buffSeconds = 15;
        cutscene = true;
        dashDistance = 5.0f;
        leapMaxDistance = 20.0f;
        chargeSeconds = 5;
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            DAMAGE[i] = clamp(getF(p, "damage" + i, DAMAGE[i]), 1f, 1000f);
            COOLDOWN_SECONDS[i] = (int) clamp(getI(p, "cooldown" + i, COOLDOWN_SECONDS[i]), 1f, 600f);
        }
        buffAttack = clamp(getF(p, "buffAttack", buffAttack), 0f, 100f);
        buffArmor = clamp(getF(p, "buffArmor", buffArmor), 0f, 30f);
        buffSpeed = clamp(getF(p, "buffSpeed", buffSpeed), 0f, 0.5f);
        buffSeconds = (int) clamp(getI(p, "buffSeconds", buffSeconds), 1f, 600f);
        cutscene = Boolean.parseBoolean(p.getProperty("cutscene", String.valueOf(cutscene)));
        dashDistance = clamp(getF(p, "dashDistance", dashDistance), 2f, 15f);
        leapMaxDistance = clamp(getF(p, "leapMaxDistance", leapMaxDistance), 8f, 40f);
        chargeSeconds = (int) clamp(getI(p, "chargeSeconds", chargeSeconds), 1f, 10f);
    }

    public static void save() {
        Properties p = new Properties();
        for (int i = 0; i < 3; i++) {
            p.setProperty("damage" + i, String.valueOf(DAMAGE[i]));
            p.setProperty("cooldown" + i, String.valueOf(COOLDOWN_SECONDS[i]));
        }
        p.setProperty("buffAttack", String.valueOf(buffAttack));
        p.setProperty("buffArmor", String.valueOf(buffArmor));
        p.setProperty("buffSpeed", String.valueOf(buffSpeed));
        p.setProperty("buffSeconds", String.valueOf(buffSeconds));
        p.setProperty("cutscene", String.valueOf(cutscene));
        p.setProperty("dashDistance", String.valueOf(dashDistance));
        p.setProperty("leapMaxDistance", String.valueOf(leapMaxDistance));
        p.setProperty("chargeSeconds", String.valueOf(chargeSeconds));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Ultimate Skill settings");
        } catch (IOException ignored) {
            // settings simply won't persist
        }
    }

    private static float getF(Properties p, String key, float def) {
        try {
            return Float.parseFloat(p.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int getI(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
