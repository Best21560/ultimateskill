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
    private static final int VERSION = 2;

    // skill 0 = Rampage (per hit), 1 = Meteor Slam (per punch), 2 = Swoop Grab (per hit, the final slam hits 3x)
    public static final float[] DAMAGE = {6.0f, 12.0f, 8.0f};
    public static final int[] COOLDOWN_SECONDS = {15, 12, 35};

    // passive Asura (second life): buffs after the revive
    public static float buffAttack = 5.0f;
    public static float buffArmor = 5.0f;
    public static float buffSpeed = 0.05f;
    public static int buffSeconds = 15;
    public static int reviveCooldownSeconds = 300;
    public static float reviveBlastDamage = 50.0f;
    public static boolean cutscene = true;       // revive cutscene

    // skill 2: Meteor Slam
    public static float leapMaxDistance = 30.0f;
    public static int chargeSeconds = 5;

    // skill 0: Rampage
    public static int rampageSeconds = 10;
    public static boolean breakBlocks = true;

    // skill 3 (ultimate): Swoop Grab
    public static float dashDistance = 15.0f;
    public static boolean ultCutscene = true;

    /** Client only: keep the equipped skills after leaving the world. */
    public static boolean keepSlots = false;
    public static final int[] SAVED_SLOTS = {-1, -1, -1};

    private SkillConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("ultimateskill.properties");
    }

    public static void resetDefaults() {
        DAMAGE[0] = 6.0f; DAMAGE[1] = 12.0f; DAMAGE[2] = 8.0f;
        COOLDOWN_SECONDS[0] = 15; COOLDOWN_SECONDS[1] = 12; COOLDOWN_SECONDS[2] = 35;
        buffAttack = 5.0f;
        buffArmor = 5.0f;
        buffSpeed = 0.05f;
        buffSeconds = 15;
        reviveCooldownSeconds = 300;
        reviveBlastDamage = 50.0f;
        cutscene = true;
        leapMaxDistance = 30.0f;
        chargeSeconds = 5;
        rampageSeconds = 10;
        breakBlocks = true;
        dashDistance = 15.0f;
        ultCutscene = true;
        keepSlots = false;
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
        // keep the equipped-slot memory even from an older file
        for (int i = 0; i < 3; i++) SAVED_SLOTS[i] = (int) clamp(getI(p, "slot" + i, -1), -1f, 2f);
        keepSlots = Boolean.parseBoolean(p.getProperty("keepSlots", String.valueOf(keepSlots)));
        // a settings file from an older version of the mod uses different skills: start from the new defaults
        if (getI(p, "version", 1) < VERSION) return;

        for (int i = 0; i < 3; i++) {
            DAMAGE[i] = clamp(getF(p, "damage" + i, DAMAGE[i]), 1f, 1000f);
            COOLDOWN_SECONDS[i] = (int) clamp(getI(p, "cooldown" + i, COOLDOWN_SECONDS[i]), 1f, 600f);
        }
        buffAttack = clamp(getF(p, "buffAttack", buffAttack), 0f, 100f);
        buffArmor = clamp(getF(p, "buffArmor", buffArmor), 0f, 30f);
        buffSpeed = clamp(getF(p, "buffSpeed", buffSpeed), 0f, 0.5f);
        buffSeconds = (int) clamp(getI(p, "buffSeconds", buffSeconds), 1f, 600f);
        reviveCooldownSeconds = (int) clamp(getI(p, "reviveCooldownSeconds", reviveCooldownSeconds), 10f, 3600f);
        reviveBlastDamage = clamp(getF(p, "reviveBlastDamage", reviveBlastDamage), 0f, 500f);
        cutscene = Boolean.parseBoolean(p.getProperty("cutscene", String.valueOf(cutscene)));
        leapMaxDistance = clamp(getF(p, "leapMaxDistance", leapMaxDistance), 12f, 60f);
        chargeSeconds = (int) clamp(getI(p, "chargeSeconds", chargeSeconds), 1f, 10f);
        rampageSeconds = (int) clamp(getI(p, "rampageSeconds", rampageSeconds), 2f, 30f);
        breakBlocks = Boolean.parseBoolean(p.getProperty("breakBlocks", String.valueOf(breakBlocks)));
        dashDistance = clamp(getF(p, "dashDistance", dashDistance), 5f, 30f);
        ultCutscene = Boolean.parseBoolean(p.getProperty("ultCutscene", String.valueOf(ultCutscene)));
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("version", String.valueOf(VERSION));
        for (int i = 0; i < 3; i++) {
            p.setProperty("damage" + i, String.valueOf(DAMAGE[i]));
            p.setProperty("cooldown" + i, String.valueOf(COOLDOWN_SECONDS[i]));
        }
        p.setProperty("buffAttack", String.valueOf(buffAttack));
        p.setProperty("buffArmor", String.valueOf(buffArmor));
        p.setProperty("buffSpeed", String.valueOf(buffSpeed));
        p.setProperty("buffSeconds", String.valueOf(buffSeconds));
        p.setProperty("reviveCooldownSeconds", String.valueOf(reviveCooldownSeconds));
        p.setProperty("reviveBlastDamage", String.valueOf(reviveBlastDamage));
        p.setProperty("cutscene", String.valueOf(cutscene));
        p.setProperty("leapMaxDistance", String.valueOf(leapMaxDistance));
        p.setProperty("chargeSeconds", String.valueOf(chargeSeconds));
        p.setProperty("rampageSeconds", String.valueOf(rampageSeconds));
        p.setProperty("breakBlocks", String.valueOf(breakBlocks));
        p.setProperty("dashDistance", String.valueOf(dashDistance));
        p.setProperty("ultCutscene", String.valueOf(ultCutscene));
        p.setProperty("keepSlots", String.valueOf(keepSlots));
        for (int i = 0; i < 3; i++) p.setProperty("slot" + i, String.valueOf(SAVED_SLOTS[i]));
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
