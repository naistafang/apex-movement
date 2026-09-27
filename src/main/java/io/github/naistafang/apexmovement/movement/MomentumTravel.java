package io.github.naistafang.apexmovement.movement;

import io.github.naistafang.apexmovement.config.MovementConfig;
import io.github.naistafang.apexmovement.movement.physics.MomentumPhysics;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import io.github.naistafang.apexmovement.movement.state.MovementState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleSupplier;

// Replacement for vanilla's ground/air movement tick (LivingEntity#travelInAir) for players.
// Called from PlayerTravelMixin on both sides: the client simulates its local player, and the server
// runs the same simulation for each connected player (vanilla players are AUTHORITATIVE_SIDE_AND_SERVER).
// Acceleration, friction and gravity follow our config; collisions and everything else stay vanilla.
public final class MomentumTravel {
    // Vanilla player defaults, used to express attribute changes (effects, enchantments) as a multiplier
    // on our configured speeds instead of replacing them.
    private static final double VANILLA_MOVEMENT_SPEED = 0.1;
    private static final double VANILLA_SPRINT_MULTIPLIER = 1.3;
    private static final double VANILLA_SNEAKING_SPEED = 0.3;
    private static final double VANILLA_GRAVITY = 0.08;
    // Vanilla scales full movement input to this length.
    private static final double FULL_INPUT = 0.98;
    // Ground ahead lower than this (blocks per block) counts as downhill, pausing the slide friction timer.
    private static final double DOWNHILL_RISE = 0.05;
    // Time in the air (ticks) before touching down counts as a landing for jump fatigue.
    private static final int MIN_AIR_TICKS_FOR_LANDING = 3;
    // Vertical jumps (blocks) beyond the velocity's own movement that count as a step for camera smoothing. Above the
    // per-tick gravity (~0.05), which the ground cancels every tick.
    private static final double MIN_CAMERA_STEP = 0.1;

    private MomentumTravel() {}

    // Runs one movement tick with our physics. Returns false if vanilla should handle this tick instead.
    // inFluid and gravity come from protected LivingEntity methods, which only the mixin can call.
    public static boolean travel(Player player, Vec3 input, boolean jumpHeld, boolean inFluid, DoubleSupplier gravity) {
        MovementData data = player.getData(MovementAttachments.MOVEMENT);
        BlockPos posBelow = player.getBlockPosBelowThatAffectsMyMovement();
        String vanillaReason = vanillaReason(player, inFluid, posBelow);
        if (vanillaReason != null) {
            data.markVanilla(vanillaReason);
            LowGapSlide.update(player, data, false, Vec3.ZERO); // give the pose back to vanilla
            return false;
        }

        Level level = player.level();
        Vec3 before = player.getDeltaMovement();
        MovementState state;
        if (level.isClientSide()) {
            // The client decides the state of the player it simulates; changes are sent to the server
            // (see MovementStateSync), which uses them instead of deciding for itself.
            MovementState previous = data.state();
            if (player.onGround()) {
                data.resetClimb();
                data.setSuperglideFlight(false);
            }
            if (previous == MovementState.MANTLING && data.isMantling()) {
                state = MovementState.MANTLING;
            } else if (Mantling.tryStart(player, data, input)) {
                state = MovementState.MANTLING;
            } else {
                state = nextState(player, data, previous, before);
                if (state == MovementState.SLIDING && previous != MovementState.SLIDING) {
                    before = startSlide(player, data, before);
                }
            }
            data.tick(state);
            before = coyoteJump(player, data, previous, state, jumpHeld, before);
            before = fallStun(player, data, previous, state, before);
        } else {
            state = data.state();
            data.clearVanillaReason();
            // The server can't reproduce client-only speed changes (slide boost, superglide, mantle), so its own copy
            // of the velocity would drift toward zero. That copy matters: taking damage (even fall damage) sends it to
            // the client (vanilla's knockback sync), which would wipe the player's momentum. So each tick starts from
            // the horizontal movement the client last reported, which equals its velocity (friction comes first).
            Vec3 known = player.getKnownMovement();
            before = new Vec3(known.x, before.y, known.z);
            player.setDeltaMovement(before);
        }

        // Sliding into 1-block-high gaps: switch to the crawling hitbox ahead of time (see LowGapSlide). The server
        // judges by the movement the client reports, like the edge rule.
        if (level.isClientSide()) {
            LowGapSlide.update(player, data, state == MovementState.SLIDING, before);
        } else {
            LowGapSlide.update(player, data, isServerSliding(player, data), player.getKnownMovement());
        }

        // Mantling moves the player along its own path (no gravity, input ignored once past the climb).
        // The server doesn't simulate it: the client reports the resulting positions as usual.
        if (state == MovementState.MANTLING) {
            if (level.isClientSide()) {
                Mantling.tick(player, data, input);
            } else {
                player.setDeltaMovement(Vec3.ZERO);
            }
            return true;
        }
        boolean onGroundState = state != MovementState.AIRBORNE;
        double gravityScale = gravity.getAsDouble() / VANILLA_GRAVITY;

        // Block slipperiness (0.6 normal, 0.98 ice) only matters on the ground; in the air it is 1.0 like vanilla.
        double blockFriction = onGroundState
                ? MomentumPhysics.modifiedFriction(level.getBlockState(posBelow).getFriction(level, posBelow, player), player.getAttributeValue(Attributes.FRICTION_MODIFIER))
                : 1.0;

        // Order matters. Vanilla accelerates, moves, then applies friction, so the velocity it carries into the next
        // tick is only ~55% of the speed actually moved on the ground, and jumping would drop to that. We apply friction
        // FIRST (Source-engine order): top speed is identical, but velocity always equals the real movement speed,
        // so leaving the ground keeps all of it.
        Vec3 accelerated = switch (state) {
            case SLIDING -> slideTick(player, data, before, blockFriction, gravityScale);
            case AIRBORNE -> {
                data.setLastAirVerticalSpeed(before.y);
                yield airTick(player, input, before);
            }
            default -> groundTick(player, input, before, blockFriction);
        };
        player.setDeltaMovement(MomentumPhysics.limitAcceleratedSpeed(before, accelerated, MovementConfig.MAX_HORIZONTAL_SPEED.getAsDouble()));

        // Move with collision handling (also updates onGround, step-up, etc.).
        double startY = player.getY();
        double intendedY = player.getDeltaMovement().y;
        player.move(MoverType.SELF, player.getDeltaMovement());
        if (state == MovementState.AIRBORNE && player.onGround() && data.ticksInState() >= MIN_AIR_TICKS_FOR_LANDING) {
            // Touched down this tick; a jump on the next tick is the soonest possible (for jump fatigue). Tiny drops
            // (e.g. the few centimetres at the end of a mantle) aren't landings.
            data.setLastLandingTick(player.tickCount);
        }
        if (state == MovementState.SLIDING) {
            snapDownStep(player);
        }
        if (level.isClientSide() && onGroundState) {
            // A step up (or a slide's snap down a step) moved the feet much more than the velocity did: let the camera
            // catch up smoothly (client camera only, see StepSmoothing).
            double stepped = player.getY() - startY - intendedY;
            if (Math.abs(stepped) > MIN_CAMERA_STEP) {
                data.addStepOffset(stepped, MovementConfig.STEP_HEIGHT.getAsDouble() + 0.5);
            }
        }

        // Gravity (Apex-style: no vertical drag, only a terminal speed). It stays after the move, like vanilla,
        // which the jump calibration relies on. Vanilla's effective gravity already includes Slow Falling and the
        // gravity attribute; we keep its ratio to the vanilla default so those still work on top of our gravity.
        Vec3 movement = player.getDeltaMovement();
        double movementY = movement.y - MomentumPhysics.perTickSquared(MovementConfig.GRAVITY.getAsDouble()) * gravityScale;
        movementY = Math.max(movementY, -MomentumPhysics.perTick(MovementConfig.MAX_FALL_SPEED.getAsDouble()));
        player.setDeltaMovement(movement.x, movementY, movement.z);
        return true;
    }

    // Client-side state machine. Sliding starts when crouch is held on the ground at slide speed, or when landing
    // with crouch held after a fall fast enough (Apex's air slide, e.g. jumping into a slide), and continues while
    // crouch is held and speed stays above the end speed.
    private static MovementState nextState(Player player, MovementData data, MovementState previous, Vec3 velocity) {
        if (!player.onGround()) {
            return MovementState.AIRBORNE;
        }
        if (player.isShiftKeyDown()) {
            double speed = velocity.horizontalDistance();
            if (previous == MovementState.SLIDING) {
                if (speed >= MomentumPhysics.perTick(MovementConfig.SLIDE_END_SPEED.getAsDouble())) {
                    return MovementState.SLIDING;
                }
            } else if (speed >= MomentumPhysics.perTick(MovementConfig.SLIDE_START_SPEED.getAsDouble())) {
                return MovementState.SLIDING;
            } else if (previous == MovementState.AIRBORNE
                    && speed >= MomentumPhysics.perTick(MovementConfig.AIR_SLIDE_SPEED.getAsDouble())
                    && -data.lastAirVerticalSpeed() >= MomentumPhysics.perTick(MovementConfig.AIR_SLIDE_FALL_SPEED.getAsDouble())) {
                return MovementState.SLIDING;
            }
        }
        return MovementState.GROUNDED;
    }

    // Coyote time (client): leaving the ground without jumping (walking or sliding off an edge) leaves coyoteTime
    // seconds in which pressing jump still jumps. Vanilla only jumps on the ground, so we do the jump ourselves through
    // vanilla's jumpFromGround(), which also runs our JumpHandler adjustments. Returns the (possibly new) velocity.
    private static Vec3 coyoteJump(Player player, MovementData data, MovementState previous, MovementState state, boolean jumpHeld, Vec3 velocity) {
        if (state != MovementState.AIRBORNE) {
            data.setCoyoteTicks(0);
            return velocity;
        }
        boolean leftGround = previous == MovementState.GROUNDED || previous == MovementState.SLIDING || previous == MovementState.LANDING;
        if (leftGround && velocity.y <= 0.0) {
            data.setCoyoteTicks(secondsToTicks(MovementConfig.COYOTE_TIME.getAsDouble()));
        }
        if (data.coyoteTicks() <= 0) {
            return velocity;
        }
        if (jumpHeld) {
            data.setCoyoteTicks(0);
            player.jumpFromGround();
            return player.getDeltaMovement();
        }
        data.setCoyoteTicks(data.coyoteTicks() - 1);
        return velocity;
    }

    // Fall stun (client): tracks the highest point of each time in the air; landing after a fall of more than
    // startHeight cuts speed and slows ground movement for a while, both growing quadratically up to fullHeight.
    // Landing straight into a slide takes only part of it (slideLandingFactor).
    // Returns the (possibly reduced) velocity.
    private static Vec3 fallStun(Player player, MovementData data, MovementState previous, MovementState state, Vec3 velocity) {
        if (data.stunTicksLeft() > 0) {
            data.setStunTicksLeft(data.stunTicksLeft() - 1);
        }
        if (state == MovementState.AIRBORNE) {
            data.setAirPeakY(previous == MovementState.AIRBORNE ? Math.max(data.airPeakY(), player.getY()) : player.getY());
            return velocity;
        }
        if (previous != MovementState.AIRBORNE) {
            return velocity;
        }

        double fall = data.airPeakY() - player.getY();
        double start = MovementConfig.FALL_STUN_START_HEIGHT.getAsDouble();
        double full = Math.max(MovementConfig.FALL_STUN_FULL_HEIGHT.getAsDouble(), start + 1.0E-3);
        if (fall <= start) {
            return velocity;
        }
        double linear = Math.min(1.0, (fall - start) / (full - start));
        double stun = linear * linear;
        if (state == MovementState.SLIDING) {
            stun *= MovementConfig.FALL_STUN_SLIDE_FACTOR.getAsDouble(); // landing into a slide softens the stun
        }
        data.setStunTicksLeft(secondsToTicks(MovementConfig.FALL_STUN_MAX_DURATION.getAsDouble() * stun));
        double kept = 1.0 - MovementConfig.FALL_STUN_MAX_SPEED_LOSS.getAsDouble() * stun;
        Vec3 result = new Vec3(velocity.x * kept, velocity.y, velocity.z * kept);
        player.setDeltaMovement(result);
        return result;
    }

    // Entering a slide: restart the slide timer and, if the boost is off cooldown, queue the boost. It is added as a
    // short burst of acceleration (see slideTick) lasting as long as Apex makes you wait before a slide jump keeps
    // the boost: until an instant boost would have decayed below jumpSpeed, or jumpTime, whichever is first.
    // The cooldown counts from the last slide entry, boosted or not (as in Apex), so chaining slides doesn't chain boosts.
    private static Vec3 startSlide(Player player, MovementData data, Vec3 velocity) {
        int cooldownTicks = secondsToTicks(MovementConfig.SLIDE_BOOST_COOLDOWN.getAsDouble());
        boolean boostReady = player.tickCount - data.lastSlideStartTick() >= cooldownTicks;
        data.startSlide(player.tickCount);
        if (!boostReady) {
            return velocity;
        }

        double speed = velocity.horizontalDistance();
        double peak = MomentumPhysics.boostedSpeed(speed,
                MomentumPhysics.perTick(MovementConfig.SLIDE_BOOST.getAsDouble()),
                MomentumPhysics.perTick(MovementConfig.SLIDE_BOOST_MAX_SPEED.getAsDouble()));
        int maxTicks = secondsToTicks(MovementConfig.SLIDE_JUMP_TIME.getAsDouble());
        if (maxTicks <= 0) {
            return MomentumPhysics.addAlongDirection(velocity, peak - speed);
        }
        int boostTicks = MomentumPhysics.ticksToDecayBelow(peak,
                MomentumPhysics.perTick(MovementConfig.SLIDE_JUMP_SPEED.getAsDouble()),
                MovementConfig.SLIDE_FRICTION.getAsDouble(), maxTicks);
        data.startBoost((peak - speed) / boostTicks, boostTicks);
        return velocity;
    }

    // Sliding: first the boost burst (if any), then low friction until frictionDelay seconds in (the timer pauses
    // while going downhill), then late friction.
    // Gravity along the slope speeds the slide up downhill and slows it uphill. Input doesn't accelerate a slide.
    private static Vec3 slideTick(Player player, MovementData data, Vec3 velocity, double blockFriction, double gravityScale) {
        double rise = SlopeSampler.rise(player.level(), player.position(), velocity);
        data.setSlopeDegrees(Math.toDegrees(Math.atan2(rise, SlopeSampler.RUN)));
        boolean downhill = rise < -DOWNHILL_RISE;
        if (!downhill) {
            data.advanceSlide();
        }

        Vec3 result = velocity;
        if (data.isBoosting()) {
            // Boost burst: speed rises by equal steps to the boosted speed; friction waits until it is used up.
            result = MomentumPhysics.addAlongDirection(result, data.takeBoostTick());
        } else if (!player.shouldDiscardFriction()) {
            boolean late = data.slideTicks() > secondsToTicks(MovementConfig.SLIDE_FRICTION_DELAY.getAsDouble());
            double friction = late ? MovementConfig.SLIDE_LATE_FRICTION.getAsDouble() : MovementConfig.SLIDE_FRICTION.getAsDouble();
            double kept = MomentumPhysics.surfaceAdjustedFriction(friction, blockFriction);
            result = new Vec3(result.x * kept, result.y, result.z * kept);
        }

        double gravityPerTick = MomentumPhysics.perTickSquared(MovementConfig.GRAVITY.getAsDouble()) * gravityScale
                * MovementConfig.SLIDE_SLOPE_STRENGTH.getAsDouble();
        return MomentumPhysics.addAlongDirection(result, MomentumPhysics.slopeAcceleration(gravityPerTick, rise, SlopeSampler.RUN));
    }

    // Walking/sprinting/crouch-walking: ground friction, then vanilla-style acceleration toward the input.
    private static Vec3 groundTick(Player player, Vec3 input, Vec3 velocity, double blockFriction) {
        Vec3 result = velocity;
        if (!player.shouldDiscardFriction()) {
            double groundFriction = MomentumPhysics.modifiedFriction(MovementConfig.GROUND_FRICTION.getAsDouble(), player.getAttributeValue(Attributes.AIR_DRAG_MODIFIER));
            double horizontalFriction = blockFriction * groundFriction;
            result = new Vec3(result.x * horizontalFriction, result.y, result.z * horizontalFriction);
        }
        double acceleration = MomentumPhysics.groundAcceleration(groundBaseAcceleration(player), blockFriction);
        if (player.getData(MovementAttachments.MOVEMENT).stunTicksLeft() > 0) {
            // Fall stun: less acceleration means a proportionally lower top speed while it lasts.
            acceleration *= MovementConfig.FALL_STUN_SPEED_FACTOR.getAsDouble();
        }
        return result.add(MomentumPhysics.wishVelocity(input, acceleration, player.getYRot()));
    }

    // In the air: no friction. Air strafing steers the momentum toward the input without losing speed; input
    // without a backward component (W, A, D, diagonals) never reduces speed, backward input (S) can brake.
    private static Vec3 airTick(Player player, Vec3 input, Vec3 velocity) {
        // wishDir's length is the input strength (up to ~0.98 for a full key press).
        Vec3 wishDir = MomentumPhysics.wishVelocity(input, 1.0, player.getYRot());
        double inputStrength = Math.min(wishDir.length() / FULL_INPUT, 1.0);
        double wishSpeed = MomentumPhysics.perTick(topSpeed(player)) * inputStrength;
        boolean backwardInput = input.z < 0.0;
        return MomentumPhysics.airMove(velocity, wishDir.normalize(), wishSpeed,
                MomentumPhysics.perTick(MovementConfig.MAX_AIR_SPEED.getAsDouble()), MovementConfig.AIR_ACCELERATE.getAsDouble(),
                Math.toRadians(MovementConfig.AIR_STEER_MAX_ANGLE.getAsDouble()),
                Math.toRadians(MovementConfig.AIR_STEER_RATE.getAsDouble()) / MomentumPhysics.TICKS_PER_SECOND,
                backwardInput);
    }

    // Whether crouching should stop protecting the player from walking off edges (see PlayerEdgeMixin).
    // Slides must be able to go off ledges and down stairs.
    public static boolean ignoresSneakEdges(Player player) {
        if (!MovementConfig.SPEC.isLoaded() || !MovementConfig.ENABLED.getAsBoolean()) {
            return false;
        }
        MovementData data = player.getData(MovementAttachments.MOVEMENT);
        if (data.vanillaReason() != null) {
            return false;
        }
        if (data.state() == MovementState.SLIDING) {
            return true;
        }
        return !player.level().isClientSide() && isServerSliding(player, data);
    }

    // Server: the client's "sliding" state arrives a little late, but the server re-checks the client's moves
    // (edges, hitbox) right away. So it also treats a crouching player moving at slide speed as sliding.
    private static boolean isServerSliding(Player player, MovementData data) {
        return data.state() == MovementState.SLIDING
                || player.isShiftKeyDown()
                && player.getKnownMovement().horizontalDistance() >= MomentumPhysics.perTick(MovementConfig.SLIDE_END_SPEED.getAsDouble());
    }

    // A slide that runs off a step of up to stepHeight follows it down instead of flying off, so 1-block
    // steps act like a slope in both directions (step-up is handled by LivingEntityStepMixin).
    private static void snapDownStep(Player player) {
        if (player.onGround() || player.getDeltaMovement().y > 0.0) {
            return; // still grounded, or jumped out of the slide
        }
        double stepDown = MovementConfig.STEP_HEIGHT.getAsDouble();
        boolean groundWithinStep = !player.level().noCollision(player, player.getBoundingBox().move(0.0, -stepDown, 0.0));
        if (groundWithinStep) {
            player.move(MoverType.SELF, new Vec3(0.0, -stepDown, 0.0));
        }
    }

    // Step-up height (0 = vanilla) for LivingEntityStepMixin: whenever our movement system is running the player,
    // 1-block rises can be walked, sprinted and slid up without jumping. Depends only on config and on whether our
    // system ran last tick, so client and server agree without waiting for state packets.
    public static float stepHeight(Player player) {
        if (!MovementConfig.SPEC.isLoaded() || !MovementConfig.ENABLED.getAsBoolean()
                || player.getData(MovementAttachments.MOVEMENT).vanillaReason() != null) {
            return 0.0F;
        }
        return MovementConfig.STEP_HEIGHT.get().floatValue();
    }

    // Apex slide-jump timing: a jump out of a slide keeps the boost once the boost burst is over (its length is
    // Apex's slide-jump wait, see startSlide). Jumping while it is still building up is too early.
    public static boolean slideJumpReady(MovementData data) {
        return !data.isBoosting();
    }

    private static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * MomentumPhysics.TICKS_PER_SECOND);
    }

    // Configured top speed for the current movement mode in m/s, scaled by the movement speed attribute
    // relative to vanilla's, so Speed/Slowness still apply.
    private static double topSpeed(Player player) {
        boolean sprinting = player.isSprinting();
        double attributeFactor = player.getSpeed() / (VANILLA_MOVEMENT_SPEED * (sprinting ? VANILLA_SPRINT_MULTIPLIER : 1.0));
        return (sprinting ? MovementConfig.SPRINT_SPEED : MovementConfig.WALK_SPEED).getAsDouble() * attributeFactor;
    }

    // Ground acceleration on a normal block that levels off at the configured walk/sprint/crouch speed.
    private static double groundBaseAcceleration(Player player) {
        double groundFriction = MovementConfig.GROUND_FRICTION.getAsDouble();
        if (player.isCrouching()) {
            // The client already scales crouching input by the Sneaking Speed attribute (0.3 by default).
            // Dividing by the vanilla value makes the default land on crouchSpeed, while Swift Sneak still helps.
            double attributeFactor = player.getSpeed() / VANILLA_MOVEMENT_SPEED;
            double crouchSpeed = MomentumPhysics.perTick(MovementConfig.CROUCH_SPEED.getAsDouble()) * attributeFactor;
            return MomentumPhysics.accelerationForTopSpeed(crouchSpeed, groundFriction) / VANILLA_SNEAKING_SPEED;
        }

        return MomentumPhysics.accelerationForTopSpeed(MomentumPhysics.perTick(topSpeed(player)), groundFriction);
    }

    // Cases where vanilla keeps full control, or null if our system applies.
    // Anything with its own special movement rules stays vanilla.
    private static @Nullable String vanillaReason(Player player, boolean inFluid, BlockPos posBelow) {
        if (!MovementConfig.SPEC.isLoaded()) return "config not loaded";
        if (!MovementConfig.ENABLED.getAsBoolean()) return "disabled in config";
        if (player.isPassenger()) return "riding";
        if (player.getAbilities().flying) return "flying";
        if (player.isFallFlying()) return "elytra";
        if (player.isSwimming() || inFluid) return "in fluid";
        if (player.onClimbable()) return "climbing";
        if (player.isInPowderSnow || player.wasInPowderSnow) return "powder snow";
        if (player.hasEffect(MobEffects.LEVITATION)) return "levitation";
        if (player.isAutoSpinAttack()) return "riptide";
        // Vanilla freezes players in unloaded chunks on the client; keep that behavior.
        if (player.level().isClientSide() && !player.level().hasChunk(SectionPos.blockToSectionCoord(posBelow.getX()), SectionPos.blockToSectionCoord(posBelow.getZ()))) return "chunk not loaded";
        return null;
    }
}
