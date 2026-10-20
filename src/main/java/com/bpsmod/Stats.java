package com.bpsmod;

import java.util.ArrayDeque;

public final class Stats {
    public enum Metric {
        BLOCKS("Blocks"), CROPS("Crops"), FISH("Fish");
        public final String label;
        Metric(String label) { this.label = label; }
    }

    public enum Window {
        SECOND("/s"), TEN_MIN("/10m"), HOUR("/1h");
        public final String suffix;
        Window(String suffix) { this.suffix = suffix; }
    }

    private static final int SECONDS = 3600;

    private static final class Track {
        final int[] buckets = new int[SECONDS];
        long headSec = 0;
        final ArrayDeque<Long> recent = new ArrayDeque<>();

        void advance(long nowSec) {
            if (nowSec > headSec) {
                long gap = Math.min(nowSec - headSec, SECONDS);
                for (long i = 1; i <= gap; i++) {
                    buckets[(int) ((headSec + i) % SECONDS)] = 0;
                }
                headSec = nowSec;
            }
        }

        void add(long nowMs) {
            advance(nowMs / 1000);
            buckets[(int) (headSec % SECONDS)]++;
            recent.addLast(nowMs);
            while (!recent.isEmpty() && recent.peekFirst() < nowMs - 1000) recent.pollFirst();
        }

        int perSecond(long nowMs) {
            while (!recent.isEmpty() && recent.peekFirst() < nowMs - 1000) recent.pollFirst();
            return recent.size();
        }

        int sum(long nowMs, int seconds) {
            advance(nowMs / 1000);
            int s = 0;
            for (int i = 0; i < seconds; i++) {
                s += buckets[(int) Math.floorMod(headSec - i, (long) SECONDS)];
            }
            return s;
        }
    }

    private static final Track[] TRACKS = new Track[Metric.values().length];

    static {
        reset();
    }

    public static void reset() {
        for (int i = 0; i < TRACKS.length; i++) TRACKS[i] = new Track();
    }

    public static void record(Metric m) {
        if (!BpsConfig.get().tracker[m.ordinal()]) return;
        TRACKS[m.ordinal()].add(System.currentTimeMillis());
    }

    public static int count(Metric m, Window w, long nowMs) {
        Track t = TRACKS[m.ordinal()];
        return switch (w) {
            case SECOND -> t.perSecond(nowMs);
            case TEN_MIN -> t.sum(nowMs, 600);
            case HOUR -> t.sum(nowMs, 3600);
        };
    }

    private Stats() {}
}
