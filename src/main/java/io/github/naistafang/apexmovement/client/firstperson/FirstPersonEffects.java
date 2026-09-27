package io.github.naistafang.apexmovement.client.firstperson;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.config.ClientConfig;
import io.github.naistafang.apexmovement.movement.state.MantlePhase;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

// Client only: first-person camera feedback for sliding and mantling (toggle and strength in the client config).
//   Slide:  the camera rolls slightly and the field of view widens a little, for a sense of speed.
//   Mantle: the camera bobs with the climb, tilts up while pulling up and nods forward over the ledge.
// Your own legs/arms are shown by FirstPersonBody. Uses the same animation blends as the third-person poses
// (MovementData#tickAnimations), so they stay in sync.
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class FirstPersonEffects {
    private static final float SLIDE_ROLL_DEGREES = 4.0F;
    private static final float SLIDE_FOV_BOOST = 0.08F;

    private static final float CLIMB_CYCLE_PER_TICK = 0.9F;
    private static final float CLIMB_BOB_PITCH_DEGREES = 1.5F;
    private static final float CLIMB_BOB_ROLL_DEGREES = 2.0F;
    private static final float RISE_PITCH_DEGREES = -3.0F;  // look up slightly while pulling up
    private static final float PULL_PITCH_DEGREES = 8.0F;   // nod forward over the ledge

    private FirstPersonEffects() {}

    @SubscribeEvent
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        MovementData data = localData(true);
        if (data == null) {
            return;
        }
        float partialTick = event.getPartialTick();
        float strength = (float) ClientConfig.CAMERA_EFFECT_STRENGTH.getAsDouble();

        float slide = data.slideAnimation(partialTick);
        float roll = SLIDE_ROLL_DEGREES * slide;
        float pitch = 0.0F;

        float mantle = data.mantleAnimation(partialTick);
        if (mantle > 0.0F) {
            float age = Minecraft.getInstance().player.tickCount + partialTick;
            float blend = data.phaseBlend(partialTick);
            float[] from = mantleCamera(data.animPhaseFrom(), age);
            float[] to = mantleCamera(data.animPhase(), age);
            pitch += Mth.lerp(blend, from[0], to[0]) * mantle;
            roll += Mth.lerp(blend, from[1], to[1]) * mantle;
        }

        event.setPitch(event.getPitch() + pitch * strength);
        event.setRoll(event.getRoll() + roll * strength);
    }

    // {pitch, roll} camera offsets in degrees for a mantle phase.
    private static float[] mantleCamera(MantlePhase phase, float age) {
        return switch (phase) {
            case CLIMB, NONE -> new float[] {
                    CLIMB_BOB_PITCH_DEGREES * Mth.sin(age * CLIMB_CYCLE_PER_TICK),
                    CLIMB_BOB_ROLL_DEGREES * Mth.sin(age * CLIMB_CYCLE_PER_TICK * 0.5F)};
            case RISE -> new float[] {RISE_PITCH_DEGREES, 0.0F};
            case PULL -> new float[] {PULL_PITCH_DEGREES, 0.0F};
        };
    }

    @SubscribeEvent
    static void onComputeFov(ViewportEvent.ComputeFov event) {
        MovementData data = localData(false);
        if (data == null) {
            return;
        }
        float slide = data.slideAnimation(event.getPartialTick());
        float strength = (float) ClientConfig.CAMERA_EFFECT_STRENGTH.getAsDouble();
        event.setFOV(event.getFOV() * (1.0F + SLIDE_FOV_BOOST * slide * strength));
    }

    // While mantling, the body's own arms are shown (FirstPersonBody), so hide the first-person hand and held item.
    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        if (FirstPersonBody.showsArms()) {
            event.setCanceled(true);
        }
    }

    // The local player's movement data, or null if effects are off (or, when firstPersonOnly, not in first person).
    private static @Nullable MovementData localData(boolean firstPersonOnly) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !ClientConfig.SPEC.isLoaded() || !ClientConfig.FIRST_PERSON_EFFECTS.getAsBoolean()) {
            return null;
        }
        if (firstPersonOnly && !minecraft.options.getCameraType().isFirstPerson()) {
            return null;
        }
        return player.getData(MovementAttachments.MOVEMENT);
    }
}
