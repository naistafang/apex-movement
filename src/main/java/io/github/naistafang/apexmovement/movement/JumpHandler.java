package io.github.naistafang.apexmovement.movement;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.config.MovementConfig;
import io.github.naistafang.apexmovement.movement.physics.MomentumPhysics;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import io.github.naistafang.apexmovement.movement.state.MovementState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

// Adjusts vanilla jumps to our config (jump velocity, sprint-jump push, slide-jump timing, jump fatigue). LivingJumpEvent fires right after vanilla's jumpFromGround() has set
// the jump velocity, on both sides (client for its own player, server when it sees the player jump),
// so no mixin is needed. Only affects players whose movement our system handled on the last tick.
@EventBusSubscriber(modid = ApexMovement.MODID)
public final class JumpHandler {
    // Vanilla values from LivingEntity#jumpFromGround and the default JUMP_STRENGTH attribute.
    private static final double VANILLA_JUMP_VELOCITY = 0.42;
    private static final double VANILLA_SPRINT_JUMP_BOOST = 0.2;

    private JumpHandler() {}

    @SubscribeEvent
    static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !MovementConfig.SPEC.isLoaded()
                || player.getData(MovementAttachments.MOVEMENT).vanillaReason() != null) {
            return;
        }

        MovementData data = player.getData(MovementAttachments.MOVEMENT);
        if (data.state() == MovementState.SLIDING && !MomentumTravel.slideJumpReady(data)) {
            // Jumped too early out of a slide (Apex slide-jump timing): the boost gained so far is taken back and the
            // rest of it cancelled, so the jump leaves at roughly the speed the slide started with.
            player.setDeltaMovement(MomentumPhysics.addAlongDirection(player.getDeltaMovement(), -data.revokeBoost()));
        }

        Vec3 movement = player.getDeltaMovement();
        // Scale rather than replace, so Jump Boost, honey blocks and the jump strength attribute keep working.
        double jumpScale = MomentumPhysics.perTick(MovementConfig.JUMP_VELOCITY.getAsDouble()) / VANILLA_JUMP_VELOCITY;
        int sinceLanding = player.tickCount - data.lastLandingTick();
        double y = Math.min(movement.y * jumpScale, fatigueLimit(sinceLanding));

        double x = movement.x;
        double z = movement.z;
        if (player.isSprinting()) {
            // Swap vanilla's fixed forward push for the configured one (same direction math as vanilla).
            double boostChange = MomentumPhysics.perTick(MovementConfig.SPRINT_JUMP_BOOST.getAsDouble()) - VANILLA_SPRINT_JUMP_BOOST;
            float angle = player.getYRot() * (float) (Math.PI / 180.0);
            x += -Mth.sin(angle) * boostChange;
            z += Mth.cos(angle) * boostChange;
        }

        player.setDeltaMovement(x, y, z);
    }

    // Jump fatigue: the highest jump velocity (blocks/tick) allowed this soon after landing. For fatigueTime the jump
    // only reaches fatigueHeight; the allowed height then recovers linearly to a full jump by fatigueRecovery.
    private static double fatigueLimit(int sinceLanding) {
        double seconds = sinceLanding / MomentumPhysics.TICKS_PER_SECOND;
        double fatigueTime = MovementConfig.JUMP_FATIGUE_TIME.getAsDouble();
        double recovery = MovementConfig.JUMP_FATIGUE_RECOVERY.getAsDouble();
        if (seconds >= recovery) {
            return Double.MAX_VALUE;
        }
        double gravity = MomentumPhysics.perTickSquared(MovementConfig.GRAVITY.getAsDouble());
        double fullHeight = MomentumPhysics.jumpHeight(MomentumPhysics.perTick(MovementConfig.JUMP_VELOCITY.getAsDouble()), gravity);
        double fatigueHeight = MovementConfig.JUMP_FATIGUE_HEIGHT.getAsDouble();
        if (fatigueHeight >= fullHeight - 0.01) {
            return Double.MAX_VALUE; // fatigue off: the limit is at (or above) a full jump
        }
        double progress = recovery > fatigueTime ? Mth.clamp((seconds - fatigueTime) / (recovery - fatigueTime), 0.0, 1.0) : 1.0;
        return MomentumPhysics.jumpVelocityForHeight(Mth.lerp(progress, fatigueHeight, fullHeight), gravity);
    }
}
