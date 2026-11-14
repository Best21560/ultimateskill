package com.example;

import net.minecraft.util.Identifier;

/** Shared skill constants and accessors (used by the server logic and the client HUD). */
public final class Skills {
    // client -> server: use a skill (int id, int arg)
    public static final Identifier USE_SKILL = new Identifier("ultimateskill", "use_skill");
    // server -> client
    public static final Identifier ULT_CUTSCENE = new Identifier("ultimateskill", "ult_cutscene"); // int targetEntityId
    public static final Identifier REVIVE = new Identifier("ultimateskill", "revive");             // int blastDelayTicks
    public static final Identifier PASSIVE = new Identifier("ultimateskill", "passive");           // int cooldownTicksLeft
    public static final Identifier SHAKE = new Identifier("ultimateskill", "shake");               // float strength, int ticks

    // index 0 = Rampage (hold), 1 = Meteor Slam (hold to charge), 2 = Swoop Grab (ultimate)
    public static final String[] NAMES = {"Rampage", "Meteor Slam", "Swoop Grab"};

    // ---- Rampage
    public static final double RAMPAGE_REACH = 2.4;
    public static final int RAMPAGE_HIT_INTERVAL = 6;     // ticks between hits on the same target
    public static final double RAMPAGE_SPEED_BONUS = 0.28; // added to the walking speed (0.1)

    // ---- Meteor Slam
    public static final double LEAP_MIN_DISTANCE = 10.0;
    public static final int PUNCHES = 5;
    public static final int PUNCH_INTERVAL = 7;
    public static final double SLAM_RADIUS_MIN = 6.0;
    public static final double SLAM_RADIUS_MAX = 10.0;

    // ---- Swoop Grab (ultimate); timings shared by the server combo and the client cinematic
    public static final double ULT_SPEED = 1.5;      // blocks per tick while dashing
    public static final int ULT_THROW_TICKS = 14;    // target is thrown up
    public static final double ULT_THROW_HEIGHT = 7.0;
    public static final int ULT_SWOOPS = 6;
    public static final int ULT_SWOOP_INTERVAL = 8;
    public static final int ULT_SLAM_TICKS = 10;     // max time of the final slam
    public static final double ULT_SLAM_RADIUS = 5.5;

    // ---- passive Asura: ticks between the revive and the blast (cutscene on / off)
    public static final int ASURA_DELAY_CUTSCENE = 85;
    public static final int ASURA_DELAY_QUICK = 10;
    public static final double ASURA_RADIUS = 6.0;

    private Skills() {}

    public static float damage(int id) { return SkillConfig.DAMAGE[id]; }
    public static int cooldownTicks(int id) { return SkillConfig.COOLDOWN_SECONDS[id] * 20; }
    public static double buffAttack() { return SkillConfig.buffAttack; }
    public static double buffArmor() { return SkillConfig.buffArmor; }
    public static double buffSpeed() { return SkillConfig.buffSpeed; }
    public static int buffTicks() { return SkillConfig.buffSeconds * 20; }

    public static int rampageTicks() { return SkillConfig.rampageSeconds * 20; }
    public static int chargeTicksMax() { return SkillConfig.chargeSeconds * 20; }
    public static double dashDistance() { return SkillConfig.dashDistance; }
    public static double leapMaxDistance() { return Math.max(LEAP_MIN_DISTANCE, SkillConfig.leapMaxDistance); }
    public static int reviveCooldownTicks() { return SkillConfig.reviveCooldownSeconds * 20; }

    /** Leap distance in blocks for a given number of charge ticks. */
    public static double leapDistance(int chargeTicks) {
        double frac = Math.max(0.0, Math.min(1.0, chargeTicks / (double) chargeTicksMax()));
        return LEAP_MIN_DISTANCE + (leapMaxDistance() - LEAP_MIN_DISTANCE) * frac;
    }

    /** Slam radius grows with the charge. */
    public static double slamRadius(int chargeTicks) {
        double frac = Math.max(0.0, Math.min(1.0, chargeTicks / (double) chargeTicksMax()));
        return SLAM_RADIUS_MIN + (SLAM_RADIUS_MAX - SLAM_RADIUS_MIN) * frac;
    }

    /** Short damage text for the HUD and menus. */
    public static String damageLabel(int id) {
        int d = (int) damage(id);
        return switch (id) {
            case 0 -> "DMG " + d + "/hit";
            case 1 -> "DMG " + d + " x" + PUNCHES;
            default -> "DMG " + d + " x" + (ULT_SWOOPS + 4);
        };
    }
}
