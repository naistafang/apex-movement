package io.github.naistafang.apexmovement.client.animation;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.movement.state.MantlePhase;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

// Client only: drives the slide and mantle animations for every visible player (the local one and others).
// Each tick, the blends ease toward 1 while a player slides/mantles and back to 0 after. Each frame, they are
// copied into the player's render state: since 1.21 the renderer draws from a snapshot of the entity (a "render
// state") instead of the entity itself, so extra data has to be added to that snapshot. SlidePose/MantlePose read it.
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class MovementAnimations {
    public static final ContextKey<Float> SLIDE_AMOUNT = key("slide_amount");
    public static final ContextKey<Float> MANTLE_AMOUNT = key("mantle_amount");
    public static final ContextKey<MantlePhase> MANTLE_PHASE = key("mantle_phase");
    public static final ContextKey<MantlePhase> MANTLE_PHASE_FROM = key("mantle_phase_from");
    public static final ContextKey<Float> MANTLE_PHASE_BLEND = key("mantle_phase_blend");

    // Blend speed per tick: into a pose in 2 ticks (0.1 s), back out in 4 ticks (0.2 s); between mantle phases in 3.
    private static final float BLEND_IN_PER_TICK = 0.5F;
    private static final float BLEND_OUT_PER_TICK = 0.25F;
    private static final float PHASE_BLEND_PER_TICK = 0.34F;

    private MovementAnimations() {}

    private static <T> ContextKey<T> key(String name) {
        return new ContextKey<>(Identifier.fromNamespaceAndPath(ApexMovement.MODID, name));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Player player : level.players()) {
            player.getData(MovementAttachments.MOVEMENT).tickAnimations(BLEND_IN_PER_TICK, BLEND_OUT_PER_TICK, PHASE_BLEND_PER_TICK);
        }
    }

    @SubscribeEvent
    static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                if (avatar instanceof Player player) {
                    MovementData data = player.getData(MovementAttachments.MOVEMENT);
                    float partialTick = state.partialTick;
                    state.setRenderData(SLIDE_AMOUNT, data.slideAnimation(partialTick));
                    state.setRenderData(MANTLE_AMOUNT, data.mantleAnimation(partialTick));
                    state.setRenderData(MANTLE_PHASE, data.animPhase());
                    state.setRenderData(MANTLE_PHASE_FROM, data.animPhaseFrom());
                    state.setRenderData(MANTLE_PHASE_BLEND, data.phaseBlend(partialTick));
                }
            }
        });
    }
}
