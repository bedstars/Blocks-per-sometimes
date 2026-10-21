package com.bpsmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Juice boxes per hour: counts Juice Boxes that appear in your inventory during a 60 second window,
 * then multiplies by 60. Updates once a minute.
 */
public final class JuiceTracker {
    private static int last = -1;
    private static int gained = 0;
    private static long windowStart = 0;
    private static int perHour = -1;

    private static boolean isJuice(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains("juice box");
    }

    private static int count(LocalPlayer p) {
        int total = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isJuice(s)) total += s.getCount();
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (isJuice(carried)) total += carried.getCount();
        return total;
    }

    private static void reset() {
        last = -1;
        gained = 0;
        windowStart = 0;
        perHour = -1;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!BpsConfig.get().showJuice || p == null) {
            if (last != -1 || windowStart != 0) reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (windowStart == 0) windowStart = now;

        int total = count(p);
        boolean containerOpen = p.containerMenu != p.inventoryMenu;
        if (last >= 0 && total > last && !containerOpen) gained += total - last;
        last = total;

        if (now - windowStart >= 60_000L) {
            perHour = gained * 60;
            gained = 0;
            windowStart = now;
        }
    }

    /**
     * Expected juice boxes per hour from your drop settings:
     * crops/h x juicer chance x juicer amount + other blocks/h x juicy chance x juicy amount.
     * The pace comes from the last 60 seconds of crops and blocks.
     */
    public static double estimatePerHour() {
        BpsConfig c = BpsConfig.get();
        long now = System.currentTimeMillis();
        double crops = Stats.last(Stats.Metric.CROPS, 60, now) * 60.0;
        double blocks = Stats.last(Stats.Metric.BLOCKS, 60, now) * 60.0;
        double mining = Math.max(0.0, blocks - crops);
        return crops * (c.juicerChance / 100.0) * c.juicerAmount
                + mining * (c.juicyChance / 100.0) * c.juicyAmount;
    }

    /** -1 until the first full minute has passed. */
    public static int perHour() { return perHour; }

    private JuiceTracker() {}
}
