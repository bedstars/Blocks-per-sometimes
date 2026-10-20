package com.bpsmod;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BpsMod implements ClientModInitializer {
    public static final String MOD_ID = "bpsmod";

    /** Opening a screen straight from a chat command gets overwritten when chat closes, so we wait a couple of ticks. */
    private static int openDelay = 0;

    @Override
    public void onInitializeClient() {
        BpsConfig.load();

        // HUD (top-left)
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                BpsHud::render);

        // /bpsmod
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bpsmod")
                        .executes(ctx -> {
                            openDelay = 2;
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset")
                                .executes(ctx -> {
                                    Stats.reset();
                                    return Command.SINGLE_SUCCESS;
                                }))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FishTracker.tick();
            if (openDelay > 0 && --openDelay == 0) {
                Minecraft.getInstance().setScreen(new BpsScreen());
            }
        });

        // Blocks + crops
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
            Stats.record(Stats.Metric.BLOCKS);
            if (isCrop(state)) Stats.record(Stats.Metric.CROPS);
        });

        // Fish
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> FishTracker.onLoad(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> FishTracker.onUnload(entity));
    }

    private static boolean isCrop(BlockState s) {
        return s.is(BlockTags.CROPS)
                || s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)
                || s.is(Blocks.NETHER_WART) || s.is(Blocks.SUGAR_CANE)
                || s.is(Blocks.CACTUS) || s.is(Blocks.COCOA)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.BAMBOO);
    }
}
