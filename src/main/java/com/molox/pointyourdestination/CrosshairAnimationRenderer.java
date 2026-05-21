package com.molox.pointyourdestination;

import net.minecraft.client.gui.GuiGraphics;

public class CrosshairAnimationRenderer {
    private static final int DURATION_MS = 300;
    private static final float MAX_OUTER = 12f;
    private static final float MAX_INNER = 12f;
    private static final int COLOR = 0xFFFFFFFF;

    private static long animationStartTime = -1;

    public static void startAnimation() {
        animationStartTime = System.currentTimeMillis();
    }

    public static void render(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (animationStartTime < 0) return;
        long elapsed = System.currentTimeMillis() - animationStartTime;
        if (elapsed > DURATION_MS) {
            animationStartTime = -1;
            return;
        }

        float tOuter = (float) elapsed / DURATION_MS;
        float outerSize = (float) (MAX_OUTER * (1 - Math.cos(tOuter * Math.PI)) / 2);

        float innerSize = 0f;
        if (elapsed > DURATION_MS / 2) {
            float tInner = (float) (elapsed - DURATION_MS / 2) / (DURATION_MS / 2);
            innerSize = (float) (MAX_INNER * (1 - Math.cos(tInner * Math.PI)) / 2);
        }

        int cx = guiGraphics.guiWidth() / 2;
        int cy = guiGraphics.guiHeight() / 2;
        drawSquareRing(guiGraphics, cx, cy, innerSize, outerSize, COLOR);
    }

    private static void drawSquareRing(GuiGraphics g, int cx, int cy,
                                       float inner, float outer, int color) {
        if (outer <= 0) return;
        int x0 = (int)(cx - outer), y0 = (int)(cy - outer);
        int x1 = (int)(cx + outer), y1 = (int)(cy + outer);
        int xi0 = (int)(cx - inner), yi0 = (int)(cy - inner);
        int xi1 = (int)(cx + inner), yi1 = (int)(cy + inner);
        g.fill(x0, y0, x1, yi0, color);
        g.fill(x0, yi1, x1, y1, color);
        g.fill(x0, yi0, xi0, yi1, color);
        g.fill(xi1, yi0, x1, yi1, color);
    }
}