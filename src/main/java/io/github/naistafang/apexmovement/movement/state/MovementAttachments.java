package io.github.naistafang.apexmovement.movement.state;

import io.github.naistafang.apexmovement.ApexMovement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

// Data attachments let us store our own data on vanilla objects (here: players) without mixins.
// Read it with player.getData(MovementAttachments.MOVEMENT); it is created on first access.
public final class MovementAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ApexMovement.MODID);

    // Not serialized: movement state is transient and starts fresh on login/respawn.
    // Synced: the server sends each player's movement state to the other clients tracking them (not back to the
    // player it came from), so they can show slide and mantle animations. Only the state, mantle phase and
    // superglide flag are sent.
    public static final Supplier<AttachmentType<MovementData>> MOVEMENT = ATTACHMENT_TYPES.register("movement",
            () -> AttachmentType.builder(MovementData::new).sync(new StateSyncHandler()).build());

    private static final class StateSyncHandler implements AttachmentSyncHandler<MovementData> {
        private static final StreamCodec<FriendlyByteBuf, MovementState> STATE_CODEC = NeoForgeStreamCodecs.enumCodec(MovementState.class);
        private static final StreamCodec<FriendlyByteBuf, MantlePhase> PHASE_CODEC = NeoForgeStreamCodecs.enumCodec(MantlePhase.class);

        @Override
        public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer to) {
            return holder != to;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf, MovementData data, boolean initialSync) {
            STATE_CODEC.encode(buf, data.state());
            PHASE_CODEC.encode(buf, data.mantlePhase());
            buf.writeBoolean(data.superglideFlight());
        }

        // Updates the existing data in place, so client-only values (like the animation blend) survive updates.
        @Override
        public MovementData read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable MovementData previous) {
            MovementData data = previous != null ? previous : new MovementData();
            MovementState state = STATE_CODEC.decode(buf);
            MantlePhase phase = PHASE_CODEC.decode(buf);
            data.setSyncedState(state, phase, buf.readBoolean());
            return data;
        }
    }

    private MovementAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
