package com.example;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Skill selection screen (key K): equip skills into slots 1-3 (Asura is a passive and needs no slot). */
public class SkillSelectScreen extends Screen {
    private static String[][] desc() {
        return new String[][]{
                {"Hold to run and", "tear through all", "ahead (max " + SkillConfig.rampageSeconds + "s)."},
                {"Hold to charge, leap", "far, then punch the", "ground " + Skills.PUNCHES + " times."},
                {"Dash, grab a target,", "throw it up, swoop", "it, then slam it down."},
        };
    }
    private static final String[] SLOT_KEYS = {"Z", "X", "C"};

    private final Screen parent;
    private long openedAt;
    private long lastFrame;
    private final float[] hover = new float[3];
    private String hint = null;
    private long hintUntil = 0;

    private int cardW, cardH = 94, gap = 12, startX, rowSlotsY = 44, rowCardsY = 88;
    private ButtonWidget keepBtn;

    public SkillSelectScreen(Screen parent) {
        super(Text.literal("Choose your skills"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openedAt = Util.getMeasuringTimeMs();
        lastFrame = openedAt;

        cardW = Math.min(120, (this.width - 40 - 2 * gap) / 3);
        startX = (this.width - (3 * cardW + 2 * gap)) / 2;

        int by = Math.max(rowCardsY + cardH + 10, this.height - 36);
        int total = 150 + 100 + 100 + 16;
        int bx = (this.width - total) / 2;

        keepBtn = ButtonWidget.builder(keepLabel(), b -> {
            SkillConfig.keepSlots = !SkillConfig.keepSlots;
            if (SkillConfig.keepSlots) SkillLoadout.persist(); else SkillConfig.save();
            b.setMessage(keepLabel());
        }).dimensions(bx, by, 150, 20).build();
        addDrawableChild(keepBtn);

        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"),
                b -> this.client.setScreen(new SkillSettingsScreen(this)))
                .dimensions(bx + 158, by, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions(bx + 266, by, 100, 20).build());
    }

    private static Text keepLabel() {
        return Text.literal("Keep after relog: " + (SkillConfig.keepSlots ? "ON" : "OFF"));
    }

    private int cardX(int i) { return startX + i * (cardW + gap); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float fade = Ui.smooth((now - openedAt) / 220f);

        ctx.fill(0, 0, this.width, this.height, Ui.argb(0x05060C, 0.72f * fade));

        // title
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, Ui.argb(0xFFFFFF, fade));
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                "Click a skill to equip it. Click an equipped slot to remove it.",
                this.width / 2, 24, Ui.argb(0xA0A8B8, fade));

        // equipped slots row
        for (int i = 0; i < 3; i++) {
            int x = cardX(i), y = rowSlotsY + (int) ((1f - fade) * 6);
            int skill = SkillLoadout.slots[i];
            boolean over = inside(mouseX, mouseY, x, y, cardW, 30);
            if (skill >= 0) {
                Ui.framed(ctx, x, y, cardW, 30, Ui.argb(0x1A2A20, 0.85f * fade),
                        Ui.argb(over ? 0xFFFFFF : 0x40D070, fade));
                ctx.drawTextWithShadow(this.textRenderer, "[" + SLOT_KEYS[i] + "] Slot " + (i + 1), x + 6, y + 5, Ui.argb(0xFFD060, fade));
                ctx.drawTextWithShadow(this.textRenderer, Skills.NAMES[skill], x + 6, y + 17, Ui.argb(0xFFFFFF, fade));
            } else {
                Ui.framed(ctx, x, y, cardW, 30, Ui.argb(0x10121A, 0.6f * fade), Ui.argb(0x606878, 0.7f * fade));
                ctx.drawTextWithShadow(this.textRenderer, "[" + SLOT_KEYS[i] + "] Slot " + (i + 1), x + 6, y + 5, Ui.argb(0x909090, fade));
                ctx.drawTextWithShadow(this.textRenderer, "Empty", x + 6, y + 17, Ui.argb(0x707070, fade));
            }
        }

        // skill cards
        for (int i = 0; i < 3; i++) {
            int x = cardX(i);
            int y0 = rowCardsY + (int) ((1f - fade) * 10);
            boolean over = inside(mouseX, mouseY, x, y0, cardW, cardH);
            hover[i] += ((over ? 1f : 0f) - hover[i]) * Math.min(1f, dt * 14f);
            float hv = hover[i];
            int y = y0 - Math.round(hv * 3f);

            int slot = SkillLoadout.slotOf(i);
            boolean equipped = slot >= 0;
            int border = equipped ? Ui.mix(0x40D070, 0xFFFFFF, hv * 0.6f) : Ui.mix(0xB02A38, 0xFFFFFF, hv * 0.8f);
            int fill = equipped ? Ui.mix(0x14261C, 0x1E3A2A, hv) : Ui.mix(0x161018, 0x2A1820, hv);

            Ui.framed(ctx, x, y, cardW, cardH, Ui.argb(fill, 0.92f * fade), Ui.argb(border, fade));
            ctx.fill(x + 3, y + 3, x + cardW - 3, y + 5, Ui.argb(equipped ? 0x40D070 : 0xD03040, fade));

            drawScaled(ctx, Skills.NAMES[i], x + 8, y + 10, 1.25f, Ui.argb(0xFFFFFF, fade));
            for (int l = 0; l < 3; l++) {
                ctx.drawTextWithShadow(this.textRenderer, desc()[i][l], x + 8, y + 27 + l * 10, Ui.argb(0xB0B8C8, fade));
            }
            String dmg = Skills.damageLabel(i);
            ctx.drawTextWithShadow(this.textRenderer, dmg, x + 8, y + 60, Ui.argb(0xFFA0A0, fade));
            ctx.drawTextWithShadow(this.textRenderer, "CD " + (Skills.cooldownTicks(i) / 20) + "s", x + 8, y + 70, Ui.argb(0xA0C8FF, fade));

            if (equipped) {
                ctx.drawTextWithShadow(this.textRenderer, "EQUIPPED - slot " + (slot + 1), x + 8, y + 82, Ui.argb(0x60FF90, fade));
            } else {
                float pulse = 0.55f + 0.2f * hv;
                ctx.drawTextWithShadow(this.textRenderer, "Click to equip", x + 8, y + 82, Ui.argb(0x909090, pulse * fade));
            }
        }

        // hint (e.g. all slots full)
        if (hint != null && now < hintUntil) {
            float a = Math.min(1f, (hintUntil - now) / 400f);
            ctx.drawCenteredTextWithShadow(this.textRenderer, hint, this.width / 2, rowCardsY + cardH + 2, Ui.argb(0xFF7070, a));
        }

        super.render(ctx, mouseX, mouseY, delta); // buttons
    }

    private void drawScaled(DrawContext ctx, String text, int x, int y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawTextWithShadow(this.textRenderer, text, 0, 0, color);
        ctx.getMatrices().pop();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        for (int i = 0; i < 3; i++) {
            if (inside(mouseX, mouseY, cardX(i), rowSlotsY, cardW, 30) && SkillLoadout.slots[i] >= 0) {
                SkillLoadout.clear(i);
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (inside(mouseX, mouseY, cardX(i), rowCardsY - 3, cardW, cardH + 3)) {
                int slot = SkillLoadout.slotOf(i);
                if (slot >= 0) {
                    SkillLoadout.clear(slot);
                } else if (!SkillLoadout.equip(i)) {
                    hint = "All 3 slots are full - click a slot above to remove one.";
                    hintUntil = Util.getMeasuringTimeMs() + 2500;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
