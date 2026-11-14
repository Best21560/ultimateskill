package com.example;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Settings window (opened from the skill menu): damage, cooldown and options of every skill. */
public class SkillSettingsScreen extends Screen {
    private final Screen parent;

    public SkillSettingsScreen(Screen parent) {
        super(Text.literal("Skill Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int colW = 156, gap = 8, rowH = 20, hh = 18;
        int xL = this.width / 2 - colW - gap / 2;
        int xR = this.width / 2 + gap / 2;
        int y = 26;

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            addDrawableChild(new ValueSlider(xL, y, colW, hh, Skills.NAMES[i] + " dmg", 1, 200, 1, "%.0f",
                    SkillConfig.DAMAGE[i], v -> SkillConfig.DAMAGE[idx] = (float) v));
            addDrawableChild(new ValueSlider(xR, y, colW, hh, Skills.NAMES[i] + " cd", 1, 120, 1, "%.0fs",
                    SkillConfig.COOLDOWN_SECONDS[i], v -> SkillConfig.COOLDOWN_SECONDS[idx] = (int) Math.round(v)));
            y += rowH;
        }

        // Asura (passive second life)
        addDrawableChild(new ValueSlider(xL, y, colW, hh, "Revive cooldown", 10, 1200, 10, "%.0fs",
                SkillConfig.reviveCooldownSeconds, v -> SkillConfig.reviveCooldownSeconds = (int) Math.round(v)));
        addDrawableChild(new ValueSlider(xR, y, colW, hh, "Revive blast dmg", 0, 200, 5, "%.0f",
                SkillConfig.reviveBlastDamage, v -> SkillConfig.reviveBlastDamage = (float) v));
        y += rowH;
        addDrawableChild(new ValueSlider(xL, y, colW, hh, "Buff attack", 0, 20, 1, "+%.0f",
                SkillConfig.buffAttack, v -> SkillConfig.buffAttack = (float) v));
        addDrawableChild(new ValueSlider(xR, y, colW, hh, "Buff armor", 0, 20, 1, "+%.0f",
                SkillConfig.buffArmor, v -> SkillConfig.buffArmor = (float) v));
        y += rowH;
        addDrawableChild(new ValueSlider(xL, y, colW, hh, "Buff speed", 0, 0.20, 0.01, "+%.2f",
                SkillConfig.buffSpeed, v -> SkillConfig.buffSpeed = (float) v));
        addDrawableChild(new ValueSlider(xR, y, colW, hh, "Buff time (aura)", 1, 120, 1, "%.0fs",
                SkillConfig.buffSeconds, v -> SkillConfig.buffSeconds = (int) Math.round(v)));
        y += rowH;

        // Rampage
        addDrawableChild(new ValueSlider(xL, y, colW, hh, "Rampage time", 2, 30, 1, "%.0fs",
                SkillConfig.rampageSeconds, v -> SkillConfig.rampageSeconds = (int) Math.round(v)));
        addDrawableChild(toggle(xR, y, colW, hh, "Rampage breaks blocks", () -> SkillConfig.breakBlocks,
                () -> SkillConfig.breakBlocks = !SkillConfig.breakBlocks));
        y += rowH;

        // Meteor Slam
        addDrawableChild(new ValueSlider(xL, y, colW, hh, "Leap max", 12, 60, 1, "%.0fm",
                SkillConfig.leapMaxDistance, v -> SkillConfig.leapMaxDistance = (float) v));
        addDrawableChild(new ValueSlider(xR, y, colW, hh, "Max charge time", 1, 10, 1, "%.0fs",
                SkillConfig.chargeSeconds, v -> SkillConfig.chargeSeconds = (int) Math.round(v)));
        y += rowH;

        // Swoop Grab (ultimate)
        addDrawableChild(new ValueSlider(xL, y, colW, hh, "Dash distance", 5, 30, 1, "%.0fm",
                SkillConfig.dashDistance, v -> SkillConfig.dashDistance = (float) v));
        addDrawableChild(toggle(xR, y, colW, hh, "Ultimate cutscene", () -> SkillConfig.ultCutscene,
                () -> SkillConfig.ultCutscene = !SkillConfig.ultCutscene));
        y += rowH;

        addDrawableChild(toggle(xL, y, colW, hh, "Revive cutscene", () -> SkillConfig.cutscene,
                () -> SkillConfig.cutscene = !SkillConfig.cutscene));
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset to defaults"), b -> {
            SkillConfig.resetDefaults();
            this.client.setScreen(new SkillSettingsScreen(parent));
        }).dimensions(xR, y, colW, hh).build());
        y += rowH;

        int doneY = Math.max(y + 6, this.height - 32);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions(this.width / 2 - 60, doneY, 120, 20).build());
    }

    private static ButtonWidget toggle(int x, int y, int w, int h, String label,
                                       java.util.function.BooleanSupplier get, Runnable flip) {
        return ButtonWidget.builder(Text.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), b -> {
            flip.run();
            b.setMessage(Text.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")));
        }).dimensions(x, y, w, h).build();
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
