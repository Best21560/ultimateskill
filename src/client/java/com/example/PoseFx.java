package com.example;

/**
 * Body poses of the local player for the skills (leaning, arms swept back, fist drawn back).
 * Only the local player's own view is affected.
 */
public final class PoseFx {
    /** Rampage is running (key held). */
    public static boolean running = false;
    /** Ticks left of the dash wind-up pose (Swoop Grab). */
    public static int dashTicks = 0;

    private PoseFx() {}

    public static void tick() {
        if (dashTicks > 0) dashTicks--;
    }

    /** Forward lean in degrees (negative = leaning forward), 0 when no pose is active. */
    public static float lean() {
        if (dashTicks > 0) return -32f;
        if (running) return -22f;
        return 0f;
    }

    /**
     * Limb angles in radians (NaN = leave as the game animates it):
     * {rightArm pitch, rightArm roll, leftArm pitch, leftArm roll, rightLeg pitch, rightLeg roll,
     *  leftLeg pitch, leftLeg roll, head pitch, head roll}, or null when no pose is active.
     */
    public static float[] limbs() {
        float n = Float.NaN;
        if (dashTicks > 0) { // fist drawn back, the other hand reaching forward
            return new float[]{1.35f, 0.3f, -1.5f, -0.15f, n, n, n, n, 0.2f, 0f};
        }
        if (running) {       // arms swept back
            return new float[]{1.25f, 0.2f, 1.25f, -0.2f, n, n, n, n, 0.2f, 0f};
        }
        return null;
    }
}
