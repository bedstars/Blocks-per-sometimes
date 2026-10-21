package com.bpsmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Custom drawn "high tech" settings menu. Everything is drawn with rectangles + text. */
public final class BpsScreen extends Screen {
    private static final int W = 392, H = 244, ROW_H = 22, HEAD_H = 16;

    private static final int T_HEADER = 0, T_TOGGLE = 1, T_CELLS = 2, T_CYCLE = 3,
            T_STEP = 4, T_TEXT = 5, T_ACTION = 6, T_INFO = 7;

    // palette (ARGB)
    private static final int BG = 0xF00B0F14, TITLE_BG = 0xFF070A0E, SIDE_BG = 0xFF0D1218;
    private static final int ROW = 0xFF141A21, ROW_HOVER = 0xFF1C242D, ROW_DARK = 0xFF0F1419;
    private static final int ACCENT = 0xFF2DE2E6, ACCENT_DIM = 0xFF17606A, LINE = 0xFF26323D;
    private static final int GREEN = 0xFF3DFF8A, GREEN_BG = 0xFF123A27, GREEN_BG_HOVER = 0xFF1A4E35;
    private static final int TEXT = 0xFFE6EDF3, TEXT_OFF = 0xFFC4CED8, DIM = 0xFF7D8A97, RED = 0xFFFF5C6C;

    private static final String[] TABS = {"Overlay", "Lines", "Style", "Text", "Juice", "Extras"};
    private static final int[] TAB_ICONS = {Icons.MONITOR, Icons.LIST, Icons.PALETTE, Icons.TEXT, Icons.BOTTLE, Icons.STAR};
    private static int page = 0;

    // ---- model ----
    private record Cell(Supplier<String> label, BooleanSupplier on, Runnable click) {}

    private static final class Field {
        Supplier<String> get;
        Consumer<String> set;
        int max = 24;
        Predicate<String> valid = null;
        boolean color = false;
    }

    private static final class Row {
        int type;
        int icon = -1;
        String label = "";
        BooleanSupplier on;
        Runnable click;
        Runnable minus, plus;
        Supplier<String> value;
        int fixedW = 0;
        final List<Cell> cells = new ArrayList<>();
        final List<Field> fields = new ArrayList<>();

        int height() {
            return switch (type) {
                case T_HEADER -> HEAD_H;
                case T_INFO -> 12 + 10 * (label.split("\n").length);
                default -> ROW_H;
            };
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final long openedAt = System.currentTimeMillis();
    private final List<Hit> hits = new ArrayList<>();
    private List<Row> rows;
    private Field active = null;
    private final StringBuilder buf = new StringBuilder();
    private float alpha = 1f;
    private float curScale = 1f;
    private float mouseLX, mouseLY;

    public BpsScreen() {
        super(Component.literal("Blocks per sometimes"));
        rows = build(page);
    }

    // ------------------------------------------------------------------ pages
    private static Row header(String text) {
        Row r = new Row();
        r.type = T_HEADER;
        r.label = text;
        return r;
    }

    private static Row info(String text) {
        Row r = new Row();
        r.type = T_INFO;
        r.label = text;
        return r;
    }

    private static Row toggle(int icon, String label, BooleanSupplier on, Runnable flip) {
        Row r = new Row();
        r.type = T_TOGGLE;
        r.icon = icon;
        r.label = label;
        r.on = on;
        r.click = () -> {
            flip.run();
            BpsConfig.get().save();
        };
        return r;
    }

    private static Row action(int icon, String label, Runnable run) {
        Row r = new Row();
        r.type = T_ACTION;
        r.icon = icon;
        r.label = label;
        r.click = run;
        return r;
    }

    private static Row cycle(int icon, String label, Supplier<String> value, Runnable next) {
        Row r = new Row();
        r.type = T_CYCLE;
        r.icon = icon;
        r.label = label;
        r.value = value;
        r.click = () -> {
            next.run();
            BpsConfig.get().save();
        };
        return r;
    }

    private static Row stepper(int icon, String label, Supplier<String> value, Runnable minus, Runnable plus, Runnable reset) {
        Row r = new Row();
        r.type = T_STEP;
        r.icon = icon;
        r.label = label;
        r.value = value;
        r.minus = () -> { minus.run(); BpsConfig.get().save(); };
        r.plus = () -> { plus.run(); BpsConfig.get().save(); };
        r.click = () -> { reset.run(); BpsConfig.get().save(); };
        return r;
    }

    private static Field field(Supplier<String> get, Consumer<String> set, int max) {
        Field f = new Field();
        f.get = get;
        f.set = set;
        f.max = max;
        return f;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static Row numberRow(int icon, String label, java.util.function.DoubleSupplier get,
                                 java.util.function.DoubleConsumer set, double max) {
        Field f = field(() -> fmt(get.getAsDouble()), s -> {
            try {
                double v = Double.parseDouble(s.trim());
                if (v >= 0 && v <= max) {
                    set.accept(v);
                    BpsConfig.get().save();
                }
            } catch (NumberFormatException ignored) {
            }
        }, 8);
        f.valid = s -> s.trim().matches("\\d+(\\.\\d*)?|\\.\\d+");
        return textRow(icon, label, 80, f);
    }

    private static Row textRow(int icon, String label, int fixedW, Field... fs) {
        Row r = new Row();
        r.type = T_TEXT;
        r.icon = icon;
        r.label = label;
        r.fixedW = fixedW;
        for (Field f : fs) r.fields.add(f);
        return r;
    }

    private List<Row> build(int p) {
        BpsConfig c = BpsConfig.get();
        List<Row> r = new ArrayList<>();
        int[] metricIcons = {Icons.CUBE, Icons.SPROUT, Icons.FISH};

        switch (p) {
            case 0 -> {
                r.add(toggle(Icons.POWER, "Overlay", () -> c.enabled, () -> c.enabled = !c.enabled));
                r.add(header("Trackers"));
                for (Stats.Metric m : Stats.Metric.values()) {
                    final int mi = m.ordinal();
                    r.add(toggle(metricIcons[mi], m.label + " tracker", () -> c.tracker[mi], () -> c.tracker[mi] = !c.tracker[mi]));
                }
                r.add(header("Data"));
                r.add(action(Icons.RESET, "Reset all stats", Stats::reset));
            }
            case 1 -> {
                r.add(header("Visible time windows"));
                for (Stats.Metric m : Stats.Metric.values()) {
                    final int mi = m.ordinal();
                    Row row = new Row();
                    row.type = T_CELLS;
                    row.icon = metricIcons[mi];
                    row.label = m.label;
                    for (Stats.Window w : Stats.Window.values()) {
                        final int idx = mi * 3 + w.ordinal();
                        final int wi = w.ordinal();
                        row.cells.add(new Cell(() -> c.windows[wi], () -> c.show[idx], () -> {
                            c.show[idx] = !c.show[idx];
                            c.save();
                        }));
                    }
                    r.add(row);
                }
                r.add(info("Each cell is one line on the overlay.\nGreen = shown."));
            }
            case 2 -> {
                Field hex = field(() -> c.colorHex, s -> { if (c.setHex(s)) c.save(); }, 7);
                hex.color = true;
                hex.valid = s -> s.trim().replace("#", "").matches("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}");
                r.add(textRow(Icons.PALETTE, "Hex color", 80, hex));
                r.add(cycle(Icons.EYE, "Color mode", () -> "< " + c.colorMode.name() + " >", () -> {
                    BpsConfig.ColorMode[] modes = BpsConfig.ColorMode.values();
                    c.colorMode = modes[(c.colorMode.ordinal() + 1) % modes.length];
                }));
                r.add(toggle(Icons.SHADOW, "Text shadow", () -> c.shadow, () -> c.shadow = !c.shadow));
                r.add(stepper(Icons.SCALE, "Scale", () -> String.format(Locale.ROOT, "%.1fx", c.scale),
                        () -> c.scale = Math.max(0.5f, Math.round((c.scale - 0.1f) * 10f) / 10f),
                        () -> c.scale = Math.min(4.0f, Math.round((c.scale + 0.1f) * 10f) / 10f),
                        () -> c.scale = 1.0f));
                r.add(stepper(Icons.ARROWS, "Position X", () -> String.valueOf(c.x),
                        () -> c.x = Math.max(0, c.x - 5), () -> c.x = Math.min(4000, c.x + 5), () -> c.x = 4));
                r.add(stepper(Icons.ARROWS, "Position Y", () -> String.valueOf(c.y),
                        () -> c.y = Math.max(0, c.y - 5), () -> c.y = Math.min(4000, c.y + 5), () -> c.y = 4));
            }
            case 3 -> {
                r.add(header("Tracker names"));
                Field[] names = new Field[4];
                for (int i = 0; i < 3; i++) {
                    final int idx = i;
                    names[i] = field(() -> c.names[idx], s -> { c.names[idx] = s; c.save(); }, 12);
                }
                names[3] = field(() -> c.juiceName, s -> { c.juiceName = s; c.save(); }, 12);
                r.add(textRow(Icons.TEXT, "Blocks Crops Fish Juice", 0, names));
                r.add(header("Window labels  (s / 10m / 1h)"));
                Field[] wins = new Field[3];
                for (int i = 0; i < 3; i++) {
                    final int idx = i;
                    wins[i] = field(() -> c.windows[idx], s -> { c.windows[idx] = s; c.save(); }, 8);
                }
                r.add(textRow(Icons.CLOCK, "Windows", 0, wins));
                r.add(header("Line format"));
                r.add(textRow(Icons.INFO, "{name} {window} {count}", 0,
                        field(() -> c.format, s -> { c.format = s; c.save(); }, 48)));
                r.add(action(Icons.RESET, "Restore default texts", () -> { c.resetTexts(); c.save(); }));
            }
            case 4 -> {
                r.add(header("Juice boxes"));
                r.add(toggle(Icons.BOTTLE, "Measured per hour (1 min test)", () -> c.showJuice, () -> c.showJuice = !c.showJuice));
                r.add(toggle(Icons.CLOCK, "Estimated per hour (from rates)", () -> c.showJuiceEstimate, () -> c.showJuiceEstimate = !c.showJuiceEstimate));
                r.add(numberRow(Icons.SPROUT, "Juicer amount (farming)", () -> c.juicerAmount, v -> c.juicerAmount = v, 100000));
                r.add(numberRow(Icons.SPROUT, "Juicer chance %", () -> c.juicerChance, v -> c.juicerChance = v, 100));
                r.add(numberRow(Icons.CUBE, "Juicy amount (mining)", () -> c.juicyAmount, v -> c.juicyAmount = v, 100000));
                r.add(numberRow(Icons.CUBE, "Juicy chance %", () -> c.juicyChance, v -> c.juicyChance = v, 100));
                r.add(info("Estimate uses your last minute of crops and\nother blocks (those trackers must be on)."));
            }
            default -> {
                r.add(header("Detection"));
                r.add(toggle(Icons.SIGNAL, "Server-side block breaks", () -> c.serverBlocks, () -> c.serverBlocks = !c.serverBlocks));
                r.add(toggle(Icons.BUG, "Debug log in chat", () -> c.debug, () -> c.debug = !c.debug));
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ helpers
    private int col(int argb) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, col(color));
    }

    private void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        rect(g, x, y, w, 1, color);
        rect(g, x, y + h - 1, w, 1, color);
        rect(g, x, y, 1, h, color);
        rect(g, x + w - 1, y, 1, h, color);
    }

    private void txt(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, s, x, y, col(color), false);
    }

    private void txtCenter(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
        txt(g, s, cx - this.font.width(s) / 2, y, color);
    }

    private void icon(GuiGraphicsExtractor g, int icon, int x, int y, int color) {
        Icons.draw(g, icon, x, y, col(color));
    }

    private boolean hov(int x, int y, int w, int h) {
        return mouseLX >= x && mouseLX < x + w && mouseLY >= y && mouseLY < y + h;
    }

    private String tail(String s, int maxW) {
        while (s.length() > 1 && this.font.width(s) > maxW) s = s.substring(1);
        return s;
    }

    private void apply() {
        if (active != null) active.set.accept(buf.toString());
    }

    private void iconBox(GuiGraphicsExtractor g, int icon, int x, int y, int color) {
        rect(g, x, y, 14, 14, 0xFF0A0E13);
        outline(g, x, y, 14, 14, LINE);
        icon(g, icon, x + 3, y + 3, color);
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        hits.clear();

        long now = System.currentTimeMillis();
        float t = Math.min(1f, (now - openedAt) / 300f);
        float u = t - 1f;
        float ease = 1f + 2.70158f * u * u * u + 1.70158f * u * u;
        float fit = Math.min(1f, Math.min((this.height - 8f) / H, (this.width - 8f) / W));
        curScale = (0.80f + 0.20f * ease) * fit;
        alpha = Math.min(1f, t * 2.2f);

        float cx = this.width / 2f, cy = this.height / 2f;
        mouseLX = cx + (mx - cx) / curScale;
        mouseLY = cy + (my - cy) / curScale;

        int x0 = (this.width - W) / 2;
        int y0 = (this.height - H) / 2;

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(curScale, curScale);
        pose.translate(-cx, -cy);

        // panel + glow
        rect(g, x0 - 2, y0 - 2, W + 4, H + 4, 0x40000000 | (ACCENT & 0xFFFFFF));
        rect(g, x0 - 1, y0 - 1, W + 2, H + 2, ACCENT_DIM);
        rect(g, x0, y0, W, H, BG);

        // title bar
        rect(g, x0, y0, W, 26, TITLE_BG);
        iconBox(g, Icons.LOGO, x0 + 6, y0 + 6, ACCENT);
        txt(g, "BLOCKS PER SOMETIMES", x0 + 26, y0 + 5, ACCENT);
        txt(g, "SYS // OVERLAY CONFIG  v1.0", x0 + 26, y0 + 15, DIM);
        int closeX = x0 + W - 22, closeY = y0 + 5;
        boolean hc = hov(closeX, closeY, 16, 16);
        rect(g, closeX, closeY, 16, 16, hc ? 0xFF3A1A20 : ROW_DARK);
        outline(g, closeX, closeY, 16, 16, hc ? RED : LINE);
        icon(g, Icons.CLOSE, closeX + 4, closeY + 4, hc ? RED : DIM);
        hits.add(new Hit(closeX, closeY, 16, 16, this::onClose));

        // title underline with a moving highlight
        rect(g, x0, y0 + 26, W, 1, ACCENT_DIM);
        int seg = 70;
        int sx = (int) ((now / 7) % (W + seg)) - seg;
        int a = Math.max(0, sx), b = Math.min(W, sx + seg);
        if (b > a) rect(g, x0 + a, y0 + 26, b - a, 1, ACCENT);

        // corner brackets
        int bl = 9;
        rect(g, x0 - 2, y0 - 2, bl, 2, ACCENT);
        rect(g, x0 - 2, y0 - 2, 2, bl, ACCENT);
        rect(g, x0 + W - bl + 2, y0 - 2, bl, 2, ACCENT);
        rect(g, x0 + W, y0 - 2, 2, bl, ACCENT);
        rect(g, x0 - 2, y0 + H, bl, 2, ACCENT);
        rect(g, x0 - 2, y0 + H - bl + 2, 2, bl, ACCENT);
        rect(g, x0 + W - bl + 2, y0 + H, bl, 2, ACCENT);
        rect(g, x0 + W, y0 + H - bl + 2, 2, bl, ACCENT);

        // sidebar
        rect(g, x0 + 4, y0 + 32, 96, H - 32 - 30, SIDE_BG);
        for (int i = 0; i < TABS.length; i++) {
            int ty = y0 + 36 + i * 26;
            int tx = x0 + 6, tw = 92, th = 22;
            boolean sel = i == page;
            boolean h = hov(tx, ty, tw, th);
            if (sel) {
                rect(g, tx, ty, tw, th, ROW_HOVER);
                rect(g, tx, ty, 2, th, ACCENT);
            } else if (h) {
                rect(g, tx, ty, tw, th, ROW);
            }
            icon(g, TAB_ICONS[i], tx + 8, ty + 7, sel ? ACCENT : DIM);
            txt(g, TABS[i], tx + 22, ty + 7, sel ? TEXT : TEXT_OFF);
            final int target = i;
            hits.add(new Hit(tx, ty, tw, th, () -> {
                page = target;
                rows = build(page);
            }));
        }

        // content rows
        int xr = x0 + 106, rw = W - 106 - 6;
        int ry = y0 + 34;
        for (Row r : rows) {
            drawRow(g, r, xr, ry, rw, now);
            ry += r.height();
        }

        // footer
        int fy = y0 + H - 24;
        rect(g, x0 + 4, fy - 2, W - 8, 1, LINE);
        boolean blink = (now / 600) % 2 == 0;
        rect(g, x0 + 8, fy + 7, 5, 5, blink ? GREEN : 0xFF1F7A47);
        txt(g, "ACTIVE", x0 + 18, fy + 6, GREEN);
        txt(g, "[O] toggle menu", x0 + 62, fy + 6, DIM);
        int dx = x0 + W - 70, dy = fy + 2;
        boolean hd = hov(dx, dy, 62, 16);
        rect(g, dx, dy, 62, 16, hd ? 0xFF14323A : 0xFF10222A);
        outline(g, dx, dy, 62, 16, hd ? ACCENT : ACCENT_DIM);
        txtCenter(g, "DONE", dx + 31, dy + 4, ACCENT);
        hits.add(new Hit(dx, dy, 62, 16, this::onClose));

        pose.popMatrix();
    }

    private void drawRow(GuiGraphicsExtractor g, Row r, int xr, int y, int rw, long now) {
        int h = r.height();
        switch (r.type) {
            case T_HEADER -> {
                String t = r.label.toUpperCase(Locale.ROOT);
                txt(g, t, xr + 2, y + 4, ACCENT);
                int tw = this.font.width(t);
                rect(g, xr + 8 + tw, y + 8, rw - 8 - tw, 1, ACCENT_DIM);
            }
            case T_INFO -> {
                String[] lines = r.label.split("\n");
                for (int i = 0; i < lines.length; i++) txt(g, lines[i], xr + 4, y + 3 + i * 10, DIM);
            }
            case T_TOGGLE -> {
                boolean on = r.on.getAsBoolean();
                boolean hv = hov(xr, y, rw, h - 2);
                rect(g, xr, y, rw, h - 2, on ? (hv ? GREEN_BG_HOVER : GREEN_BG) : (hv ? ROW_HOVER : ROW));
                if (on) rect(g, xr, y, 2, h - 2, GREEN);
                iconBox(g, r.icon, xr + 6, y + 2, on ? GREEN : DIM);
                txt(g, r.label, xr + 26, y + 6, on ? 0xFFC8FFE0 : TEXT_OFF);
                String st = on ? "ON" : "OFF";
                txt(g, st, xr + rw - 8 - this.font.width(st), y + 6, on ? GREEN : DIM);
                int bx = xr + rw - 8 - this.font.width("OFF") - 14;
                if (on) {
                    rect(g, bx, y + 6, 8, 8, GREEN);
                } else {
                    outline(g, bx, y + 6, 8, 8, DIM);
                }
                hits.add(new Hit(xr, y, rw, h - 2, r.click));
            }
            case T_CELLS -> {
                rect(g, xr, y, rw, h - 2, ROW);
                iconBox(g, r.icon, xr + 6, y + 2, DIM);
                txt(g, r.label, xr + 26, y + 6, TEXT_OFF);
                int n = r.cells.size();
                int ax = xr + 100, aw = rw - 100 - 4;
                int cw = (aw - (n - 1) * 3) / n;
                for (int i = 0; i < n; i++) {
                    Cell cell = r.cells.get(i);
                    int cx = ax + i * (cw + 3);
                    boolean on = cell.on().getAsBoolean();
                    boolean hv = hov(cx, y + 2, cw, h - 6);
                    rect(g, cx, y + 2, cw, h - 6, on ? (hv ? GREEN_BG_HOVER : GREEN_BG) : (hv ? ROW_HOVER : ROW_DARK));
                    outline(g, cx, y + 2, cw, h - 6, on ? GREEN : LINE);
                    txtCenter(g, cell.label().get(), cx + cw / 2, y + 6, on ? 0xFFC8FFE0 : TEXT_OFF);
                    hits.add(new Hit(cx, y + 2, cw, h - 6, cell.click()));
                }
            }
            case T_CYCLE -> {
                boolean hv = hov(xr, y, rw, h - 2);
                rect(g, xr, y, rw, h - 2, hv ? ROW_HOVER : ROW);
                iconBox(g, r.icon, xr + 6, y + 2, ACCENT);
                txt(g, r.label, xr + 26, y + 6, TEXT);
                String v = r.value.get();
                txt(g, v, xr + rw - 8 - this.font.width(v), y + 6, ACCENT);
                hits.add(new Hit(xr, y, rw, h - 2, r.click));
            }
            case T_STEP -> {
                rect(g, xr, y, rw, h - 2, ROW);
                iconBox(g, r.icon, xr + 6, y + 2, ACCENT);
                txt(g, r.label, xr + 26, y + 6, TEXT);
                int pb = xr + rw - 6 - 16;
                int vx = pb - 4 - 70;
                int mb = vx - 4 - 16;
                stepBox(g, mb, y + 2, 16, h - 6, "-", r.minus);
                stepBox(g, vx, y + 2, 70, h - 6, r.value.get(), r.click);
                stepBox(g, pb, y + 2, 16, h - 6, "+", r.plus);
            }
            case T_TEXT -> {
                rect(g, xr, y, rw, h - 2, ROW);
                iconBox(g, r.icon, xr + 6, y + 2, ACCENT);
                int n = r.fields.size();
                int ax, aw;
                if (r.fixedW > 0) {
                    aw = r.fixedW;
                    ax = xr + rw - 4 - aw;
                    txt(g, r.label, xr + 26, y + 6, TEXT);
                } else {
                    ax = xr + 26;
                    aw = rw - 26 - 4;
                    // label is shown as a tiny caption above the fields only for multi fields
                }
                int fw = (aw - (n - 1) * 3) / n;
                for (int i = 0; i < n; i++) {
                    Field f = r.fields.get(i);
                    int fx = ax + i * (fw + 3);
                    boolean act = active == f;
                    String shown = act ? buf.toString() : f.get.get();
                    boolean ok = f.valid == null || f.valid.test(shown);
                    rect(g, fx, y + 2, fw, h - 6, 0xFF090D11);
                    outline(g, fx, y + 2, fw, h - 6, !ok ? RED : (act ? ACCENT : LINE));
                    String text = shown;
                    if (act && (now / 500) % 2 == 0) text += "_";
                    txt(g, tail(text, fw - 6), fx + 3, y + 6, TEXT);
                    if (f.color) {
                        int sw = 12;
                        rect(g, fx - sw - 4, y + 4, sw, sw, 0xFF000000 | BpsConfig.get().rgb);
                        outline(g, fx - sw - 4, y + 4, sw, sw, LINE);
                    }
                    final Field target = f;
                    hits.add(new Hit(fx, y + 2, fw, h - 6, () -> {
                        active = target;
                        buf.setLength(0);
                        buf.append(target.get.get());
                    }));
                }
            }
            case T_ACTION -> {
                boolean hv = hov(xr, y, rw, h - 2);
                rect(g, xr, y, rw, h - 2, hv ? 0xFF14323A : 0xFF10222A);
                outline(g, xr, y, rw, h - 2, hv ? ACCENT : ACCENT_DIM);
                icon(g, r.icon, xr + 8, y + 7, ACCENT);
                txtCenter(g, r.label, xr + rw / 2, y + 6, ACCENT);
                hits.add(new Hit(xr, y, rw, h - 2, r.click));
            }
            default -> {}
        }
        // caption for multi-field text rows (drawn after so it sits under the icon column)
        if (r.type == T_TEXT && r.fixedW == 0) {
            // nothing extra: the preceding header row already explains the fields
        }
    }

    private void stepBox(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, Runnable run) {
        boolean hv = hov(x, y, w, h);
        rect(g, x, y, w, h, hv ? ROW_HOVER : ROW_DARK);
        outline(g, x, y, w, h, hv ? ACCENT : LINE);
        txtCenter(g, label, x + w / 2, y + 4, hv ? ACCENT : TEXT);
        hits.add(new Hit(x, y, w, h, run));
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
        float cx = this.width / 2f, cy = this.height / 2f;
        float lx = cx + ((float) ev.x() - cx) / curScale;
        float ly = cy + ((float) ev.y() - cy) / curScale;
        if (ev.button() == 0) {
            active = null;
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (lx >= h.x() && lx < h.x() + h.w() && ly >= h.y() && ly < h.y() + h.h()) {
                    h.run().run();
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        int k = ev.key();
        if (active != null) {
            if (k == 259) { // backspace
                if (buf.length() > 0) {
                    buf.deleteCharAt(buf.length() - 1);
                    apply();
                }
            } else if (k == 257 || k == 335 || k == 256) { // enter / esc
                active = null;
            }
            return true;
        }
        if (k == 79) { // O
            this.onClose();
            return true;
        }
        return super.keyPressed(ev);
    }

    @Override
    public boolean charTyped(CharacterEvent ev) {
        if (active == null) return false;
        int cp = ev.codepoint();
        if (cp < 32 || cp == 127) return true;
        if (buf.length() < active.max) {
            buf.append(Character.toChars(cp));
            apply();
        }
        return true;
    }
}
