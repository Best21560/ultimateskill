package com.example;

/** Camera shake driven by the server (impacts, heavy hits). Applied to the camera in CameraMixin. */
public final class ScreenShake {
    private static float strength = 0f;
    private static int ticks = 0;
    private static int total = 1;

    private ScreenShake() {}

    private static float current() {
        return ticks <= 0 ? 0f : strength * ticks / (float) total;
    }

    public static void add(float s, int t) {
        if (s >= current()) {
            strength = s;
            ticks = Math.max(1, t);
            total = ticks;
        }
    }

    public static void tick() {
        if (ticks > 0) ticks--;
    }

    /** {yaw, pitch} offsets in degrees, or null when not shaking. */
    public static float[] offset() {
        float c = current();
        if (c <= 0.01f) return null;
        double ms = System.nanoTime() / 1_000_000.0;
        float yaw = (float) (Math.sin(ms * 0.091) + 0.6 * Math.sin(ms * 0.173)) * c * 0.7f;
        float pitch = (float) (Math.cos(ms * 0.083) + 0.6 * Math.sin(ms * 0.157)) * c * 0.55f;
        return new float[]{yaw, pitch};
    }
}
