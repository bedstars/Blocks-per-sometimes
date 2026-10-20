package com.bpsmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BpsConfig {
    public enum ColorMode { SOLID, CHROMA, RAINBOW }

    public static final String[] DEFAULT_NAMES = {"Blocks", "Crops", "Fish"};
    public static final String[] DEFAULT_WINDOWS = {"/s", "/10m", "/1h"};
    public static final String DEFAULT_FORMAT = "{name}{window}: {count}";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BpsConfig instance = new BpsConfig();

    public boolean enabled = true;
    /** Is each tracker (blocks, crops, fish) counting at all? */
    public boolean[] tracker = {true, true, true};
    /** index = metric.ordinal() * 3 + window.ordinal() */
    public boolean[] show = {true, true, true, true, true, true, true, true, true};
    public String colorHex = "FFFFFF";
    public ColorMode colorMode = ColorMode.SOLID;
    public boolean shadow = true;

    public float scale = 1.0f;
    public int x = 4;
    public int y = 4;

    public String[] names = DEFAULT_NAMES.clone();
    public String[] windows = DEFAULT_WINDOWS.clone();
    public String format = DEFAULT_FORMAT;

    public transient int rgb = 0xFFFFFF;

    public static BpsConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("bpsmod.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                BpsConfig loaded = GSON.fromJson(Files.readString(p), BpsConfig.class);
                if (loaded != null) instance = loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        BpsConfig c = instance;
        if (c.show == null || c.show.length != 9) c.show = new boolean[]{true, true, true, true, true, true, true, true, true};
        if (c.tracker == null || c.tracker.length != 3) c.tracker = new boolean[]{true, true, true};
        if (c.names == null || c.names.length != 3) c.names = DEFAULT_NAMES.clone();
        if (c.windows == null || c.windows.length != 3) c.windows = DEFAULT_WINDOWS.clone();
        if (c.format == null) c.format = DEFAULT_FORMAT;
        if (c.colorMode == null) c.colorMode = ColorMode.SOLID;
        if (c.scale < 0.5f || c.scale > 4f) c.scale = 1.0f;
        if (!c.setHex(c.colorHex)) {
            c.colorHex = "FFFFFF";
            c.rgb = 0xFFFFFF;
        }
        c.save();
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void resetTexts() {
        names = DEFAULT_NAMES.clone();
        windows = DEFAULT_WINDOWS.clone();
        format = DEFAULT_FORMAT;
    }

    /** Accepts RRGGBB or #RRGGBB (also RGB shorthand). Returns false if invalid. */
    public boolean setHex(String input) {
        if (input == null) return false;
        String s = input.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (!s.matches("[0-9a-fA-F]{6}")) return false;
        this.rgb = Integer.parseInt(s, 16);
        this.colorHex = s.toUpperCase();
        return true;
    }
}
