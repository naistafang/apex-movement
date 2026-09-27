package io.github.naistafang.apexmovement.client.camera;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.config.ClientConfig;
import io.github.naistafang.apexmovement.movement.physics.MomentumPhysics;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Client only: smooths the first-person camera over step-ups. Walking up a 1-block rise moves the player up a whole
// block in a single tick, so the view pops. MomentumTravel records how far each step moved the feet; the camera starts
// that far behind (CameraMixin subtracts the offset) and catches up at a constant rate.
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class StepSmoothing {
    private StepSmoothing() {}

    // Before the player moves each tick (Pre), so the offset added by this tick's step starts at full size.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        MovementData data = player.getData(MovementAttachments.MOVEMENT);
        if (!ClientConfig.SPEC.isLoaded() || !ClientConfig.SMOOTH_STEPS.getAsBoolean()) {
            data.clearStepOffset();
            return;
        }
        double blocksPerTick = 1.0 / (ClientConfig.STEP_SMOOTH_TIME.getAsDouble() * MomentumPhysics.TICKS_PER_SECOND);
        data.tickStepOffset(blocksPerTick);
    }

    // How far below its normal height the camera should be this frame (negative = above).
    public static double cameraOffset(LocalPlayer player, float partialTick) {
        if (!ClientConfig.SPEC.isLoaded() || !ClientConfig.SMOOTH_STEPS.getAsBoolean()) {
            return 0.0;
        }
        return player.getData(MovementAttachments.MOVEMENT).stepOffset(partialTick);
    }
}
