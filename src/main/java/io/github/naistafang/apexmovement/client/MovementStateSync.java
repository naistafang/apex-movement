package io.github.naistafang.apexmovement.client;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.movement.network.MovementStatePayload;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Client only: after each tick, tells the server if the local player's movement state or mantle phase changed.
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class MovementStateSync {
    private MovementStateSync() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) {
            return;
        }
        MovementData data = player.getData(MovementAttachments.MOVEMENT);
        if (data.consumeStateChanged()) {
            ClientPacketDistributor.sendToServer(new MovementStatePayload(data.state(), data.mantlePhase(), data.superglideFlight()));
        }
    }
}
