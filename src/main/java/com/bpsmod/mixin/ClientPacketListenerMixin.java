package com.bpsmod.mixin;

import com.bpsmod.BlockTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Servers like Hypixel "break" blocks by sending block updates (block -> air/bedrock) instead of the
 * client breaking them. We watch those updates. require = 0 means: if a name ever changes the mixin is
 * skipped quietly instead of crashing the game.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), require = 0)
    private void bps$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockTracker.onUpdate(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), require = 0)
    private void bps$sectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates(BlockTracker::onUpdate);
    }
}
