package com.bpsmod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class BpsMod implements ClientModInitializer {
    public static final String MOD_ID = "bpsmod";

    /** Opening a screen straight from a chat command gets overwritten when chat closes, so we wait a couple of ticks. */
    private static int openDelay = 0;
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        BpsConfig.load();
        ContractTracker.load();

        // HUD (top-left)
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                BpsHud::render);

        // Key bind (default: O)
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bpsmod.open", InputConstants.KEY_O, category));

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
            JuiceTracker.tick();
            ContractTracker.tick(client);
            while (openKey.consumeClick()) {
                openScreen(new BpsScreen());
            }
            if (openDelay > 0 && --openDelay == 0) {
                openScreen(new BpsScreen());
            }
        });

        // Blocks + crops broken by the client itself (vanilla / singleplayer style)
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
            Stats.record(Stats.Metric.BLOCKS);
            if (BlockTracker.isCrop(state)) Stats.record(Stats.Metric.CROPS);
            ContractTracker.onBlockBroken(state);
        });

        // Contracts: server chat (activated / complete), right-clicked contract item, save on exit
        ClientReceiveMessageEvents.GAME.register(ContractTracker::onGameMessage);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ContractTracker.onUseItem(player, hand);
            return InteractionResult.PASS;
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ContractTracker.save());

        // Fish
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> FishTracker.onLoad(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> FishTracker.onUnload(entity));
    }

    private static void openScreen(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        for (String name : new String[]{"setScreenAndShow", "setScreen"}) {
            try {
                mc.getClass().getMethod(name, net.minecraft.client.gui.screens.Screen.class).invoke(mc, screen);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
