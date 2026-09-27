package io.github.naistafang.apexmovement.movement.network;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.movement.state.MantlePhase;
import io.github.naistafang.apexmovement.movement.state.MovementState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

// Client -> server: the local player's movement state, mantle phase or superglide flight changed (e.g. started
// sliding, mantle moved from climbing to pulling up, launched a superglide).
// A payload is NeoForge's way to define a custom network packet: an id (TYPE) plus how to write/read it (STREAM_CODEC).
public record MovementStatePayload(MovementState state, MantlePhase mantlePhase, boolean superglideFlight) implements CustomPacketPayload {
    public static final Type<MovementStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ApexMovement.MODID, "movement_state"));

    public static final StreamCodec<FriendlyByteBuf, MovementStatePayload> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(MovementState.class), MovementStatePayload::state,
            NeoForgeStreamCodecs.enumCodec(MantlePhase.class), MovementStatePayload::mantlePhase,
            ByteBufCodecs.BOOL, MovementStatePayload::superglideFlight,
            MovementStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
