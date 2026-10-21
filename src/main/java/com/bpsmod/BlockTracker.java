package com.bpsmod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/** Counts blocks that a server removes for you (block turns into air / bedrock / barrier). */
public final class BlockTracker {
    private static long debugWindow = 0;
    private static int debugCount = 0;

    public static boolean isCrop(BlockState s) {
        return s.is(BlockTags.CROPS)
                || s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)
                || s.is(Blocks.NETHER_WART) || s.is(Blocks.SUGAR_CANE)
                || s.is(Blocks.CACTUS) || s.is(Blocks.COCOA)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.BAMBOO);
    }

    private static boolean gone(BlockState s) {
        return s.isAir() || s.is(Blocks.BEDROCK) || s.is(Blocks.BARRIER) || s.is(Blocks.STRUCTURE_VOID);
    }

    /** Crop that was reset to a younger growth stage (e.g. harvested and replanted by the server). */
    private static boolean cropReset(BlockState oldS, BlockState newS) {
        if (oldS.getBlock() != newS.getBlock()) return false;
        for (Property<?> p : oldS.getProperties()) {
            if (p instanceof IntegerProperty ip && p.getName().equals("age") && newS.hasProperty(ip)) {
                return newS.getValue(ip) < oldS.getValue(ip);
            }
        }
        return false;
    }

    public static void onUpdate(BlockPos pos, BlockState newState) {
        BpsConfig c = BpsConfig.get();
        if (!c.serverBlocks) return;
        Minecraft mc = Minecraft.getInstance();
        // the packet handler is first run on the network thread, only count on the game thread
        if (!mc.isSameThread()) return;
        if (mc.level == null || mc.player == null) return;

        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        if (dx * dx + dy * dy + dz * dz > 16.0 * 16.0) return;

        BlockState oldState = mc.level.getBlockState(pos);
        if (oldState == newState) return;

        boolean counted = false;
        if (!gone(oldState) && gone(newState)) {
            Stats.record(Stats.Metric.BLOCKS);
            if (isCrop(oldState)) Stats.record(Stats.Metric.CROPS);
            counted = true;
        } else if (isCrop(oldState) && cropReset(oldState, newState)) {
            Stats.record(Stats.Metric.BLOCKS);
            Stats.record(Stats.Metric.CROPS);
            counted = true;
        }

        if (c.debug) {
            long now = System.currentTimeMillis();
            if (now - debugWindow > 1000) { debugWindow = now; debugCount = 0; }
            if (debugCount++ < 6) {
                mc.player.sendSystemMessage(Component.literal(
                        "[BPS] " + oldState.getBlock() + " -> " + newState.getBlock() + (counted ? "  (counted)" : "")));
            }
        }
    }

    private BlockTracker() {}
}
