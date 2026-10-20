package com.bpsmod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class BpsScreen extends Screen {
    private static final String[] TABS = {"Display", "Style", "Text"};
    private static int page = 0;

    public BpsScreen() {
        super(Component.literal("Blocks per sometimes"));
    }

    private static Component onOff(String label, boolean on) {
        return Component.literal(label + ": ")
                .append(Component.literal(on ? "ON" : "OFF")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void label(String text, int x, int y, int w) {
        int tw = this.font.width(text);
        StringWidget sw = new StringWidget(x + (w - tw) / 2, y, tw, 10, Component.literal(text), this.font);
        this.addRenderableWidget(sw);
    }

    private EditBox box(int x, int y, int w, int maxLen, String value, java.util.function.Consumer<String> onChange) {
        EditBox eb = new EditBox(this.font, x, y, w, 20, Component.literal("text"));
        eb.setMaxLength(maxLen);
        eb.setValue(value);
        eb.setResponder(onChange);
        this.addRenderableWidget(eb);
        return eb;
    }

    private void button(Component text, int x, int y, int w, Button.OnPress press) {
        this.addRenderableWidget(Button.builder(text, press).bounds(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        BpsConfig c = BpsConfig.get();
        int cx = this.width / 2;
        int top = Math.max(6, (this.height - 200) / 2);
        int left = cx - 154;
        int y0 = top + 26;

        // Tabs
        for (int i = 0; i < TABS.length; i++) {
            final int target = i;
            button(Component.literal(i == page ? "> " + TABS[i] + " <" : TABS[i]),
                    left + i * 104, top, 100, b -> {
                        page = target;
                        this.clearWidgets();
                        this.init();
                    });
        }

        switch (page) {
            case 0 -> buildDisplay(c, left, y0);
            case 1 -> buildStyle(c, left, y0);
            default -> buildText(c, left, y0);
        }

        // Bottom row
        button(Component.literal("Reset stats"), left, top + 176, 150, b -> Stats.reset());
        button(Component.literal("Done"), left + 158, top + 176, 150, b -> this.onClose());
    }

    private void buildDisplay(BpsConfig c, int left, int y0) {
        button(onOff("Overlay", c.enabled), left, y0, 308, b -> {
            c.enabled = !c.enabled;
            b.setMessage(onOff("Overlay", c.enabled));
            c.save();
        });

        label("Trackers (OFF = not counted at all)", left, y0 + 28, 308);
        for (Stats.Metric m : Stats.Metric.values()) {
            final int mi = m.ordinal();
            button(onOff(m.label, c.tracker[mi]), left + mi * 104, y0 + 40, 100, b -> {
                c.tracker[mi] = !c.tracker[mi];
                b.setMessage(onOff(m.label, c.tracker[mi]));
                c.save();
            });
        }

        label("Lines shown", left, y0 + 66, 308);
        for (Stats.Metric m : Stats.Metric.values()) {
            for (Stats.Window w : Stats.Window.values()) {
                final int idx = m.ordinal() * 3 + w.ordinal();
                final String text = m.label + " " + w.suffix;
                button(onOff(text, c.show[idx]), left + w.ordinal() * 104, y0 + 78 + m.ordinal() * 22, 100, b -> {
                    c.show[idx] = !c.show[idx];
                    b.setMessage(onOff(text, c.show[idx]));
                    c.save();
                });
            }
        }
    }

    private void buildStyle(BpsConfig c, int left, int y0) {
        label("Text color (hex, e.g. FF8800)", left, y0, 308);
        box(left, y0 + 12, 100, 7, c.colorHex, s -> {
            if (c.setHex(s)) c.save();
        });
        button(Component.literal("Mode: " + c.colorMode.name()), left + 104, y0 + 12, 100, b -> {
            BpsConfig.ColorMode[] modes = BpsConfig.ColorMode.values();
            c.colorMode = modes[(c.colorMode.ordinal() + 1) % modes.length];
            b.setMessage(Component.literal("Mode: " + c.colorMode.name()));
            c.save();
        });
        button(onOff("Shadow", c.shadow), left + 208, y0 + 12, 100, b -> {
            c.shadow = !c.shadow;
            b.setMessage(onOff("Shadow", c.shadow));
            c.save();
        });

        // Scale
        final Button[] scaleMid = new Button[1];
        button(Component.literal("-"), left, y0 + 44, 50, b -> {
            c.scale = Math.max(0.5f, Math.round((c.scale - 0.1f) * 10f) / 10f);
            scaleMid[0].setMessage(scaleText(c));
            c.save();
        });
        button(scaleText(c), left + 54, y0 + 44, 200, b -> {
            c.scale = 1.0f;
            b.setMessage(scaleText(c));
            c.save();
        });
        scaleMid[0] = lastButton();
        button(Component.literal("+"), left + 258, y0 + 44, 50, b -> {
            c.scale = Math.min(4.0f, Math.round((c.scale + 0.1f) * 10f) / 10f);
            scaleMid[0].setMessage(scaleText(c));
            c.save();
        });

        // Position X / Y
        positionRow(c, left, y0 + 68, true);
        positionRow(c, left, y0 + 92, false);
        label("Click a middle button to reset it", left, y0 + 118, 308);
    }

    private Button lastButton() {
        Button found = null;
        for (var child : this.children()) {
            if (child instanceof Button b) found = b;
        }
        return found;
    }

    private static Component scaleText(BpsConfig c) {
        return Component.literal(String.format(Locale.ROOT, "Scale: %.1fx", c.scale));
    }

    private void positionRow(BpsConfig c, int left, int y, boolean isX) {
        final String name = isX ? "X" : "Y";
        final int def = 4;
        final Button[] mid = new Button[1];
        button(Component.literal("-"), left, y, 50, b -> {
            if (isX) c.x = Math.max(0, c.x - 5); else c.y = Math.max(0, c.y - 5);
            mid[0].setMessage(Component.literal(name + ": " + (isX ? c.x : c.y)));
            c.save();
        });
        button(Component.literal(name + ": " + (isX ? c.x : c.y)), left + 54, y, 200, b -> {
            if (isX) c.x = def; else c.y = def;
            b.setMessage(Component.literal(name + ": " + (isX ? c.x : c.y)));
            c.save();
        });
        mid[0] = lastButton();
        button(Component.literal("+"), left + 258, y, 50, b -> {
            if (isX) c.x = Math.min(4000, c.x + 5); else c.y = Math.min(4000, c.y + 5);
            mid[0].setMessage(Component.literal(name + ": " + (isX ? c.x : c.y)));
            c.save();
        });
    }

    private void buildText(BpsConfig c, int left, int y0) {
        label("Tracker names", left, y0, 308);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            box(left + i * 104, y0 + 12, 100, 24, c.names[i], s -> {
                c.names[idx] = s;
                c.save();
            });
        }

        label("Time window labels (per second / 10 min / 1 hour)", left, y0 + 40, 308);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            box(left + i * 104, y0 + 52, 100, 16, c.windows[i], s -> {
                c.windows[idx] = s;
                c.save();
            });
        }

        label("Line format - use {name} {window} {count}", left, y0 + 80, 308);
        box(left, y0 + 92, 308, 64, c.format, s -> {
            c.format = s;
            c.save();
        });

        button(Component.literal("Restore default texts"), left, y0 + 120, 308, b -> {
            c.resetTexts();
            c.save();
            this.clearWidgets();
            this.init();
        });
    }
}
