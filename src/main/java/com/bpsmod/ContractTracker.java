package com.bpsmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the 2 server contract slots.
 *
 * - Reads the server's chat ("You've activated Ultra Contract in slot 1!" / "Your Prime Contract is complete!")
 * - Remembers the task of a contract when you right-click it (reads the item lore: "FARM 22,500 CROPS")
 * - Counts crops / ores / blocks / fish since activation (estimated progress)
 * - Re-syncs empty slots whenever the /contracts menu is open
 * - Chat message + ding while a slot is empty
 */
public final class ContractTracker {
    public enum State { UNKNOWN, EMPTY, ACTIVE }

    public static final int SLOTS = 2;
    /** Menu positions of the two slot items inside the /contracts GUI (row 3, columns 3 and 5). */
    private static final int[] GUI_SLOT = {20, 22};

    public static final class Slot {
        public State state = State.UNKNOWN;
        public String tier = "";
        /** crops / ores / blocks / fish, or empty when the task is unknown */
        public String taskType = "";
        public long target = 0;
        public long progress = 0;
        public long startedAt = 0;
    }

    private static final class Save {
        Slot[] slots = new Slot[SLOTS];
    }

    public record Line(String text, int color) {}

    private record Pending(String tier, String type, long target, long at) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Slot[] SLOT = new Slot[SLOTS];

    private static final Pattern ACTIVATED = Pattern.compile("activated\\s+(.+?)\\s+contract\\s+in\\s+slot\\s+(\\d+)");
    private static final Pattern COMPLETE = Pattern.compile("your\\s+(.+?)\\s+contract\\s+is\\s+complete");
    private static final Pattern TASK = Pattern.compile("(?:farm|mine|break|catch|fish)\\w*\\s+([\\d,.]+)\\s+(crops?|ores?|blocks?|fish)");
    private static final Pattern NAME_TIER = Pattern.compile("(\\S+)\\s+contract");

    private static Pending pending = null;
    private static boolean dirty = false;
    private static long lastSave = 0;
    private static long nextAlertAt = 0;
    private static long graceUntil = 0;
    private static long dingAt = 0;
    private static int syncTick = 0;
    private static LocalPlayer lastPlayer = null;

    static {
        for (int i = 0; i < SLOTS; i++) SLOT[i] = new Slot();
    }

    // ------------------------------------------------------------------ text helpers
    private static final String SC_FROM = "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21\u028F\u1D22";
    private static final String SC_TO = "abcdefghijklmnopqrstuvwyz";

    /** Lower-cases and turns small-caps letters (the server font) into normal letters. */
    static String norm(String in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            int k = SC_FROM.indexOf(ch);
            if (k >= 0) ch = SC_TO.charAt(k);
            if (ch == '\u2019' || ch == '\u2018') ch = '\'';
            sb.append(Character.toLowerCase(ch));
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static long parseNum(String s) {
        try {
            return Long.parseLong(s.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String typeOf(String word) {
        if (word.startsWith("crop")) return "crops";
        if (word.startsWith("ore")) return "ores";
        if (word.startsWith("block")) return "blocks";
        if (word.startsWith("fish")) return "fish";
        return "";
    }

    // ------------------------------------------------------------------ persistence
    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("bpsmod-contracts.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Save s = GSON.fromJson(Files.readString(p), Save.class);
                if (s != null && s.slots != null) {
                    for (int i = 0; i < SLOTS && i < s.slots.length; i++) {
                        if (s.slots[i] != null) SLOT[i] = s.slots[i];
                        Slot sl = SLOT[i];
                        if (sl.state == null) sl.state = State.UNKNOWN;
                        if (sl.tier == null) sl.tier = "";
                        if (sl.taskType == null) sl.taskType = "";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Save s = new Save();
            for (int i = 0; i < SLOTS; i++) s.slots[i] = SLOT[i];
            Files.writeString(path(), GSON.toJson(s));
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Forget everything; slots show "?" until the server tells us (or you open /contracts). */
    public static void resetAll() {
        for (int i = 0; i < SLOTS; i++) SLOT[i] = new Slot();
        pending = null;
        nextAlertAt = 0;
        save();
    }

    // ------------------------------------------------------------------ events
    /** Right-click with an item in hand: remember its contract task so the next "activated" message gets it. */
    public static void onUseItem(Player player, InteractionHand hand) {
        try {
            ItemStack s = player.getItemInHand(hand);
            if (s.isEmpty()) return;
            String name = norm(s.getHoverName().getString());
            if (!name.contains("contract")) return;
            Matcher nm = NAME_TIER.matcher(name);
            String tier = nm.find() ? nm.group(1) : "";
            String type = "";
            long target = 0;
            ItemLore lore = s.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    Matcher m = TASK.matcher(norm(line.getString()));
                    if (m.find()) {
                        target = parseNum(m.group(1));
                        type = typeOf(m.group(2));
                        break;
                    }
                }
            }
            pending = new Pending(tier, type, target, System.currentTimeMillis());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Every system / server chat line. */
    public static void onGameMessage(Component message, boolean overlay) {
        if (overlay || message == null) return;
        String s = norm(message.getString());
        if (s.isEmpty()) return;

        Matcher a = ACTIVATED.matcher(s);
        if (a.find()) {
            try {
                activate(Integer.parseInt(a.group(2)) - 1, a.group(1).trim());
            } catch (NumberFormatException ignored) {
            }
            return;
        }
        Matcher c = COMPLETE.matcher(s);
        if (c.find()) complete(c.group(1).trim());
    }

    private static void activate(int idx, String tier) {
        if (idx < 0 || idx >= SLOTS) return;
        long now = System.currentTimeMillis();
        Slot sl = new Slot();
        sl.state = State.ACTIVE;
        sl.tier = cap(tier);
        sl.startedAt = now;
        if (pending != null && now - pending.at() < 8000 && (pending.tier().isEmpty() || pending.tier().equalsIgnoreCase(tier))) {
            sl.taskType = pending.type();
            sl.target = pending.target();
        }
        pending = null;
        SLOT[idx] = sl;
        save();
    }

    private static void complete(String tier) {
        int pick = -1;
        // 1) active slot with the same tier (if both match, the one closest to its target / oldest)
        for (int i = 0; i < SLOTS; i++) {
            Slot sl = SLOT[i];
            if (sl.state != State.ACTIVE || !sl.tier.equalsIgnoreCase(tier)) continue;
            if (pick < 0) {
                pick = i;
            } else {
                Slot best = SLOT[pick];
                double a = sl.target > 0 ? (double) sl.progress / sl.target : 0;
                double b = best.target > 0 ? (double) best.progress / best.target : 0;
                if (a > b || (a == b && sl.startedAt < best.startedAt)) pick = i;
            }
        }
        // 2) any active slot, 3) any slot we did not know about
        if (pick < 0) for (int i = 0; i < SLOTS && pick < 0; i++) if (SLOT[i].state == State.ACTIVE) pick = i;
        if (pick < 0) for (int i = 0; i < SLOTS && pick < 0; i++) if (SLOT[i].state == State.UNKNOWN) pick = i;
        if (pick < 0) return;
        SLOT[pick] = new Slot();
        SLOT[pick].state = State.EMPTY;
        nextAlertAt = 0; // alert right away
        save();
    }

    /** Called from Stats.record for blocks / crops / fish. */
    public static void onCount(Stats.Metric m) {
        count(switch (m) {
            case BLOCKS -> "blocks";
            case CROPS -> "crops";
            case FISH -> "fish";
        });
    }

    /** Called when a block was broken (client or server side). */
    public static void onBlockBroken(BlockState old) {
        try {
            String p = BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath();
            if (p.endsWith("_ore") || p.equals("ancient_debris")) count("ores");
        } catch (Throwable ignored) {
        }
    }

    private static void count(String type) {
        for (Slot sl : SLOT) {
            if (sl.state == State.ACTIVE && sl.taskType.equals(type)) {
                sl.progress++;
                dirty = true;
            }
        }
    }

    // ------------------------------------------------------------------ tick
    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (dingAt != 0 && now >= dingAt) {
            dingAt = 0;
            playDing(1.5f);
        }
        if (dirty && now - lastSave > 10_000L) save();

        LocalPlayer p = mc.player;
        if (p == null) {
            lastPlayer = null;
            return;
        }
        if (p != lastPlayer) {
            lastPlayer = p;
            graceUntil = now + 6000L;
        }

        boolean contractsMenu = false;
        if (mc.screen instanceof AbstractContainerScreen<?> acs) {
            if (norm(acs.getTitle().getString()).contains("contracts")) {
                contractsMenu = true;
                if (syncTick++ % 5 == 0) syncFromGui(acs);
            }
        }

        BpsConfig c = BpsConfig.get();
        boolean anyEmpty = false;
        for (Slot sl : SLOT) if (sl.state == State.EMPTY) anyEmpty = true;
        if (!anyEmpty) {
            nextAlertAt = 0;
            return;
        }
        if (!c.contractAlerts || contractsMenu || now < graceUntil || now < nextAlertAt) return;
        alert(p);
        nextAlertAt = c.contractRepeat > 0 ? now + c.contractRepeat * 1000L : Long.MAX_VALUE;
    }

    /** Reads the two slot items of the /contracts GUI and corrects stale states. */
    private static void syncFromGui(AbstractContainerScreen<?> acs) {
        var slots = acs.getMenu().slots;
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            int idx = GUI_SLOT[i];
            if (idx >= slots.size()) continue;
            net.minecraft.world.inventory.Slot ms = slots.get(idx);
            if (ms.container instanceof Inventory) continue;
            ItemStack st = ms.getItem();
            if (st.isEmpty()) continue;
            String name = norm(st.getHoverName().getString());
            if (name.isEmpty()) continue;
            Slot cur = SLOT[i];
            if (name.contains("empty")) {
                if (cur.state != State.EMPTY) {
                    SLOT[i] = new Slot();
                    SLOT[i].state = State.EMPTY;
                    changed = true;
                }
            } else if (name.contains("contract") && cur.state != State.ACTIVE) {
                Slot n = new Slot();
                n.state = State.ACTIVE;
                Matcher nm = NAME_TIER.matcher(name);
                n.tier = nm.find() ? cap(nm.group(1)) : "";
                n.startedAt = System.currentTimeMillis();
                SLOT[i] = n;
                changed = true;
            }
        }
        if (changed) save();
    }

    // ------------------------------------------------------------------ alert
    private static List<Integer> emptySlots() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) if (SLOT[i].state == State.EMPTY) out.add(i + 1);
        return out;
    }

    /** Chat message + ding (what the settings "Test alert" button runs too). */
    public static void alert(LocalPlayer p) {
        List<Integer> empty = emptySlots();
        String what;
        if (empty.isEmpty()) what = "Test alert - contract slot is empty";
        else if (empty.size() == 1) what = "No contract running in slot " + empty.get(0);
        else what = "No contract running in slots 1 and 2";
        if (p != null) {
            p.sendSystemMessage(Component.literal("[Contracts] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(what + " - activate one!").withStyle(ChatFormatting.RED)));
        }
        if (BpsConfig.get().contractSound) {
            playDing(1.0f);
            dingAt = System.currentTimeMillis() + 180L;
        }
    }

    private static void playDing(float pitch) {
        try {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 1.0f));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ HUD
    public static List<Line> lines() {
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            Slot sl = SLOT[i];
            String head = "Contract " + (i + 1) + ": ";
            switch (sl.state) {
                case ACTIVE -> {
                    String t = head + (sl.tier.isEmpty() ? "Active" : sl.tier);
                    if (sl.target > 0 && !sl.taskType.isEmpty()) {
                        long pr = Math.min(sl.progress, sl.target);
                        int pct = (int) Math.min(100, (pr * 100) / sl.target);
                        t += "  " + String.format(Locale.ROOT, "%,d/%,d", pr, sl.target) + " " + sl.taskType + " (" + pct + "%)";
                    }
                    out.add(new Line(t, 0x3DFF8A));
                }
                case EMPTY -> out.add(new Line(head + "EMPTY", 0xFF5C6C));
                default -> out.add(new Line(head + "? (open /contracts)", 0x9AA5B1));
            }
        }
        return out;
    }

    public static Slot slot(int i) { return SLOT[i]; }

    private ContractTracker() {}
}
