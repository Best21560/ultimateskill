package com.example;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Small drawing helpers shared by the HUD and the screens. */
final class Ui {
    private Ui() {}

    /** Smoothstep easing, input clamped to 0..1. */
    static float smooth(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** rgb (0xRRGGBB) + alpha 0..1 -> 0xAARRGGBB. */
    static int argb(int rgb, float alpha) {
        int a = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Blend two rgb colours (0xRRGGBB), t = 0..1. */
    static int mix(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Rectangle with a 1px border and softly clipped corners (fill / border are ARGB). */
    static void framed(DrawContext c, int x, int y, int w, int h, int fill, int border) {
        if (w < 3 || h < 3) return;
        c.fill(x + 1, y, x + w - 1, y + 1, border);             // top
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, border);     // bottom
        c.fill(x, y + 1, x + 1, y + h - 1, border);             // left
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, border);     // right
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);       // inside
    }

    /** Plain filled panel with softly clipped corners (ARGB). */
    static void panel(DrawContext c, int x, int y, int w, int h, int fill) {
        if (w < 3 || h < 3) return;
        c.fill(x + 1, y, x + w - 1, y + 1, fill);
        c.fill(x, y + 1, x + w, y + h - 1, fill);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, fill);
    }
}
