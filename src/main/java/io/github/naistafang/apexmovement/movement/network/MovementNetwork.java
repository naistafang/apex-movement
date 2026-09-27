package io.github.naistafang.apexmovement.movement.network;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Registers our packets. The version string must match between client and server; bump it whenever a
// payload's format changes so mismatched versions refuse to connect instead of misreading data.
@EventBusSubscriber(modid = ApexMovement.MODID)
public final class MovementNetwork {
    private static final String PROTOCOL_VERSION = "4";

    private MovementNetwork() {}

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToServer(MovementStatePayload.TYPE, MovementStatePayload.STREAM_CODEC, MovementNetwork::handleMovementState);
    }

    // Server side, runs on the server thread: store the state/phase the client reported for its player, then forward it
    // to the other players who can see them (attachment sync, see MovementAttachments).
    private static void handleMovementState(MovementStatePayload payload, IPayloadContext context) {
        context.player().getData(MovementAttachments.MOVEMENT).setSyncedState(payload.state(), payload.mantlePhase(), payload.superglideFlight());
        context.player().syncData(MovementAttachments.MOVEMENT);
    }
}
