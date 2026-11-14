package com.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Invisible screen opened during a cutscene (revive or ultimate). It draws the 2D overlays,
 * freezes player input, and lets Esc skip the cutscene.
 */
public class CutsceneScreen extends Screen {
    public CutsceneScreen() {
        super(Text.empty());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (UltCutscene.isPlaying()) {
            UltCutscene.renderOverlay(ctx, this.width, this.height, delta);
        } else {
            AsuraCutscene.renderOverlay(ctx, this.width, this.height, delta);
        }
    }

    @Override
    public void removed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        UltCutscene.stop(mc, false);
        AsuraCutscene.stop(mc, false);
    }
}
