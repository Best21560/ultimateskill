package com.example;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Settings window (key K): damage and cooldown of each skill, Asura buffs, cutscene on/off. */
public class SkillSettingsScreen extends Screen {
    private final Screen parent;

    public SkillSettingsScreen(Screen parent) {
        super(Text.literal("Skill Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int colW = 156, gap = 8, rowH = 22;
        int xL = this.width / 2 - colW - gap / 2;
        int xR = this.width / 2 + gap / 2;
        int y = 28;

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            addDrawableChild(new ValueSlider(xL, y, colW, 20, Skills.NAMES[i] + " dmg", 1, 200, 1, "%.0f",
                    SkillConfig.DAMAGE[i], v -> SkillConfig.DAMAGE[idx] = (float) v));
            addDrawableChild(new ValueSlider(xR, y, colW, 20, Skills.NAMES[i] + " cd", 1, 120, 1, "%.0fs",
                    SkillConfig.COOLDOWN_SECONDS[i], v -> SkillConfig.COOLDOWN_SECONDS[idx] = (int) Math.round(v)));
            y += rowH;
        }

        addDrawableChild(new ValueSlider(xL, y, colW, 20, "Buff attack", 0, 20, 1, "+%.0f",
                SkillConfig.buffAttack, v -> SkillConfig.buffAttack = (float) v));
        addDrawableChild(new ValueSlider(xR, y, colW, 20, "Buff armor", 0, 20, 1, "+%.0f",
                SkillConfig.buffArmor, v -> SkillConfig.buffArmor = (float) v));
        y += rowH;
        addDrawableChild(new ValueSlider(xL, y, colW, 20, "Buff speed", 0, 0.20, 0.01, "+%.2f",
                SkillConfig.buffSpeed, v -> SkillConfig.buffSpeed = (float) v));
        addDrawableChild(new ValueSlider(xR, y, colW, 20, "Buff time (aura)", 1, 120, 1, "%.0fs",
                SkillConfig.buffSeconds, v -> SkillConfig.buffSeconds = (int) Math.round(v)));
        y += rowH;
        addDrawableChild(new ValueSlider(xL, y, colW, 20, "Dash distance", 2, 15, 1, "%.0fm",
                SkillConfig.dashDistance, v -> SkillConfig.dashDistance = (float) v));
        addDrawableChild(new ValueSlider(xR, y, colW, 20, "Leap max", 8, 40, 1, "%.0fm",
                SkillConfig.leapMaxDistance, v -> SkillConfig.leapMaxDistance = (float) v));
        y += rowH;
        addDrawableChild(new ValueSlider(xL, y, colW, 20, "Max charge time", 1, 10, 1, "%.0fs",
                SkillConfig.chargeSeconds, v -> SkillConfig.chargeSeconds = (int) Math.round(v)));
        addDrawableChild(ButtonWidget.builder(cutsceneLabel(), b -> {
            SkillConfig.cutscene = !SkillConfig.cutscene;
            b.setMessage(cutsceneLabel());
        }).dimensions(xR, y, colW, 20).build());
        y += rowH;

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset to defaults"), b -> {
            SkillConfig.resetDefaults();
            this.client.setScreen(new SkillSettingsScreen(parent));
        }).dimensions(this.width / 2 - colW / 2, y, colW, 20).build());

        int doneY = Math.max(y + 24, this.height - 34);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions(this.width / 2 - 60, doneY, 120, 20).build());
    }

    private static Text cutsceneLabel() {
        return Text.literal("Asura cutscene: " + (SkillConfig.cutscene ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                "Damage and cooldown are applied by whoever runs the world (single player: you).",
                this.width / 2, this.height - 10, 0xFFA0A0A0);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void removed() {
        SkillConfig.save();
    }

    /** Slider that maps 0..1 to [min, max] rounded to a step. */
    private static class ValueSlider extends SliderWidget {
        private final String label;
        private final double min, max, step;
        private final String fmt;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max, double step,
                    String fmt, double initial, DoubleConsumer onChange) {
            super(x, y, w, h, Text.empty(), (initial - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.fmt = fmt;
            this.onChange = onChange;
            this.updateMessage();
        }

        private double current() {
            double raw = min + this.value * (max - min);
            return Math.round(raw / step) * step;
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            this.setMessage(Text.literal(label + ": " + String.format(Locale.ROOT, fmt, current())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
