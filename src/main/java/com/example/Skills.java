package com.example;

import net.minecraft.util.Identifier;

/** Shared skill constants (used by both the server logic and the client HUD). */
public final class Skills {
    public static final Identifier USE_SKILL = new Identifier("ultimateskill", "use_skill");

    // index 0 = Demon Claw, 1 = Leap Slam, 2 = Asura Mode
    public static final String[] NAMES = {"Demon Claw", "Leap Slam", "Asura Mode"};
    public static final float[] DAMAGE = {8.0f, 12.0f, 50.0f};
    public static final int[] COOLDOWN_TICKS = {60, 160, 600}; // 20 ticks = 1 second -> 3s, 8s, 30s

    public static final double CLAW_RANGE = 4.0;
    public static final double SLAM_RADIUS = 4.5;
    public static final double ASURA_RADIUS = 6.0;

    /** Ticks between pressing the Asura key and the blast (matches the cutscene). */
    public static final int ASURA_DELAY = 70;

    /** Asura buff: duration in ticks (300 = 15 seconds) and amounts. */
    public static final int BUFF_DURATION = 300;
    public static final double BUFF_ATTACK = 5.0;   // +5 attack damage
    public static final double BUFF_ARMOR = 5.0;    // +5 armor
    public static final double BUFF_SPEED = 0.05;   // +0.05 movement speed (= +50% of the default 0.1)

    private Skills() {}
}
