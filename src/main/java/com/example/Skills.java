package com.example;

import net.minecraft.util.Identifier;

/** Shared skill constants and accessors (used by the server logic and the client HUD). */
public final class Skills {
    public static final Identifier USE_SKILL = new Identifier("ultimateskill", "use_skill");

    // index 0 = Demon Claw, 1 = Leap Slam, 2 = Asura Mode
    public static final String[] NAMES = {"Demon Claw", "Leap Slam", "Asura Mode"};

    public static final double CLAW_RANGE = 4.0;
    public static final double SLAM_RADIUS = 4.5;
    public static final double ASURA_RADIUS = 6.0;

    /** Ticks between pressing the Asura key and the blast (cutscene on / off). */
    public static final int ASURA_DELAY_CUTSCENE = 85;
    public static final int ASURA_DELAY_QUICK = 10;

    private Skills() {}

    public static float damage(int id) { return SkillConfig.DAMAGE[id]; }
    public static int cooldownTicks(int id) { return SkillConfig.COOLDOWN_SECONDS[id] * 20; }
    public static double buffAttack() { return SkillConfig.buffAttack; }
    public static double buffArmor() { return SkillConfig.buffArmor; }
    public static double buffSpeed() { return SkillConfig.buffSpeed; }
    public static int buffTicks() { return SkillConfig.buffSeconds * 20; }

    public static final double LEAP_MIN_DISTANCE = 5.0;
    public static double dashDistance() { return SkillConfig.dashDistance; }
    public static int chargeTicksMax() { return SkillConfig.chargeSeconds * 20; }
    public static double leapMaxDistance() { return Math.max(LEAP_MIN_DISTANCE, SkillConfig.leapMaxDistance); }

    /** Leap distance in blocks for a given number of charge ticks (5 blocks uncharged up to the max when fully charged). */
    public static double leapDistance(int chargeTicks) {
        double frac = Math.max(0.0, Math.min(1.0, chargeTicks / (double) chargeTicksMax()));
        return LEAP_MIN_DISTANCE + (leapMaxDistance() - LEAP_MIN_DISTANCE) * frac;
    }
}
