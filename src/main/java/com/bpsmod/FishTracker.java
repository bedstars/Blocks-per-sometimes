package com.bpsmod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects a fish catch on the client: when your own fishing bobber is removed and a dropped item
 * appears at the bobber's position within a few ticks, that is counted as one catch.
 * Works on any server because it only relies on entities the client already sees.
 */
public final class FishTracker {
    private static long tick = 0;
    private static long hookGoneTick = -1000;
    private static double hx, hy, hz;
    private static final List<double[]> items = new ArrayList<>();

    public static void tick() {
        tick++;
    }

    private static boolean near(double x, double y, double z) {
        double dx = x - hx, dy = y - hy, dz = z - hz;
        return dx * dx + dy * dy + dz * dz < 9.0;
    }

    public static void onLoad(Entity entity) {
        if (!entity.getClass().getSimpleName().equals("ItemEntity")) return;
        if (tick - hookGoneTick <= 3 && near(entity.getX(), entity.getY(), entity.getZ())) {
            hookGoneTick = -1000;
            Stats.record(Stats.Metric.FISH);
            return;
        }
        items.add(new double[]{tick, entity.getX(), entity.getY(), entity.getZ()});
        if (items.size() > 16) items.remove(0);
    }

    public static void onUnload(Entity entity) {
        if (!entity.getClass().getSimpleName().equals("FishingHook")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(entity instanceof Projectile p) || p.getOwner() != mc.player) return;
        hookGoneTick = tick;
        hx = entity.getX();
        hy = entity.getY();
        hz = entity.getZ();
        for (double[] it : items) {
            if (tick - it[0] <= 3 && near(it[1], it[2], it[3])) {
                hookGoneTick = -1000;
                items.clear();
                Stats.record(Stats.Metric.FISH);
                return;
            }
        }
    }

    private FishTracker() {}
}
