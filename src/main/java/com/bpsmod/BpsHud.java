package com.bpsmod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class BpsHud {

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        BpsConfig c = BpsConfig.get();
        if (!c.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        Font font = mc.font;
        long now = System.currentTimeMillis();

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) c.x, (float) c.y);
        pose.scale(c.scale, c.scale);

        int y = 0;
        int line = 0;
        for (Stats.Metric m : Stats.Metric.values()) {
            if (!c.tracker[m.ordinal()]) continue;
            for (Stats.Window w : Stats.Window.values()) {
                if (!c.show[m.ordinal() * 3 + w.ordinal()]) continue;
                String text = c.format
                        .replace("{name}", c.names[m.ordinal()])
                        .replace("{window}", c.windows[w.ordinal()])
                        .replace("{count}", String.valueOf(Stats.count(m, w, now)));
                drawLine(g, font, text, 0, y, line, c, now);
                y += font.lineHeight + 2;
                line++;
            }
        }

        if (c.showJuice) {
            int ph = JuiceTracker.perHour();
            String text = c.format
                    .replace("{name}", c.juiceName)
                    .replace("{window}", "/h")
                    .replace("{count}", ph < 0 ? "..." : String.valueOf(ph));
            drawLine(g, font, text, 0, y, line, c, now);
            y += font.lineHeight + 2;
            line++;
        }

        if (c.showJuiceEstimate) {
            long est = Math.round(JuiceTracker.estimatePerHour());
            String text = c.format
                    .replace("{name}", c.juiceName + " est")
                    .replace("{window}", "/h")
                    .replace("{count}", String.valueOf(est));
            drawLine(g, font, text, 0, y, line, c, now);
        }

        pose.popMatrix();
    }

    private static void drawLine(GuiGraphicsExtractor g, Font font, String text, int x, int y,
                                 int line, BpsConfig c, long now) {
        float base = (now % 4000L) / 4000f;
        switch (c.colorMode) {
            case SOLID -> g.text(font, text, x, y, 0xFF000000 | c.rgb, c.shadow);
            case CHROMA -> g.text(font, text, x, y, 0xFF000000 | hsv(base), c.shadow);
            case RAINBOW -> {
                int cx = x;
                for (int i = 0; i < text.length(); i++) {
                    String ch = String.valueOf(text.charAt(i));
                    float hue = (base + i * 0.04f + line * 0.06f) % 1f;
                    g.text(font, ch, cx, y, 0xFF000000 | hsv(hue), c.shadow);
                    cx += font.width(ch);
                }
            }
        }
    }

    /** Hue 0..1, full saturation + brightness, returns 0xRRGGBB. */
    private static int hsv(float h) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float q = 1f - f;
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private BpsHud() {}
}
