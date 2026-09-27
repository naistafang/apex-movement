package io.github.naistafang.apexmovement.movement.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Pure movement math: no world access, no side effects. Velocities are in blocks per tick.
// "Friction" follows vanilla's convention: it is the fraction of speed KEPT per tick
// (1.0 = no friction, 0.546 = vanilla ground on a normal block).
public final class MomentumPhysics {
    // Slipperiness of a normal block. Vanilla scales ground acceleration relative to it.
    private static final double NORMAL_BLOCK_FRICTION = 0.6;
    // Vanilla scales full movement input to this length (LocalPlayer#modifyInput).
    private static final double FULL_INPUT = 0.98;
    // Converts meters (= blocks) per second to blocks per tick.
    public static final double TICKS_PER_SECOND = 20.0;

    private MomentumPhysics() {}

    // Converts player input (x = strafe, z = forward, each roughly -1..1) into a world-space velocity change
    // of the given magnitude, rotated by the player's yaw. Same math as vanilla's Entity#getInputVector.
    public static Vec3 wishVelocity(Vec3 input, double magnitude, float yRotDegrees) {
        double lengthSqr = input.lengthSqr();
        if (lengthSqr < 1.0E-7) {
            return Vec3.ZERO;
        }

        Vec3 scaled = (lengthSqr > 1.0 ? input.normalize() : input).scale(magnitude);
        float sin = Mth.sin(yRotDegrees * (float) (Math.PI / 180.0));
        float cos = Mth.cos(yRotDegrees * (float) (Math.PI / 180.0));
        return new Vec3(scaled.x * cos - scaled.z * sin, 0.0, scaled.z * cos + scaled.x * sin);
    }

    // Per-tick acceleration that makes full forward input level off at targetSpeed (blocks/tick) on a normal block.
    // Each tick velocity keeps `friction` of itself and then gains the acceleration, so it settles at
    // acceleration / (1 - friction). Vanilla scales full input to 0.98, which is divided out.
    public static double accelerationForTopSpeed(double targetSpeed, double groundFriction) {
        double totalFriction = NORMAL_BLOCK_FRICTION * groundFriction;
        return targetSpeed * (1.0 - totalFriction) / FULL_INPUT;
    }

    // Source/Quake-style air acceleration (what makes air strafing possible). Input can only raise the velocity
    // component along the wish direction up to airSpeedCap; speed in other directions is kept, not reduced.
    // wishSpeed is the top speed the input asks for (blocks/tick); airAccelerate is Source's dimensionless factor.
    public static Vec3 airAccelerate(Vec3 velocity, Vec3 wishDir, double wishSpeed, double airSpeedCap, double airAccelerate) {
        if (wishDir.lengthSqr() < 1.0E-7 || wishSpeed <= 0.0) {
            return velocity;
        }

        double currentSpeed = velocity.x * wishDir.x + velocity.z * wishDir.z;
        double addSpeed = Math.min(wishSpeed, airSpeedCap) - currentSpeed;
        if (addSpeed <= 0.0) {
            return velocity;
        }

        double accelSpeed = Math.min(airAccelerate * wishSpeed / TICKS_PER_SECOND, addSpeed);
        return velocity.add(wishDir.x * accelSpeed, 0.0, wishDir.z * accelSpeed);
    }

    // Air steering: when the input direction is within maxAngle of the horizontal momentum, the momentum turns
    // toward it by at most maxTurn this tick, keeping its speed exactly. Larger or sudden changes are left to
    // airAccelerate. Angles are in radians; wishDir must be normalized (or zero for no input).
    public static Vec3 airSteer(Vec3 velocity, Vec3 wishDir, double maxAngle, double maxTurn) {
        double speedSqr = velocity.horizontalDistanceSqr();
        if (speedSqr < 1.0E-8 || wishDir.lengthSqr() < 1.0E-7) {
            return velocity;
        }

        // Signed angle from the momentum direction to the wish direction.
        double dot = velocity.x * wishDir.x + velocity.z * wishDir.z;
        double cross = velocity.x * wishDir.z - velocity.z * wishDir.x;
        double angle = Math.atan2(cross, dot);
        if (Math.abs(angle) > maxAngle) {
            return velocity;
        }

        double turn = Mth.clamp(angle, -maxTurn, maxTurn);
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        return new Vec3(velocity.x * cos - velocity.z * sin, velocity.y, velocity.x * sin + velocity.z * cos);
    }

    // One tick of air control: steering (airSteer), then Source-style acceleration (airAccelerate).
    // When allowSpeedLoss is false (input with no backward component: W, A, D and diagonals), the result can turn
    // the momentum and add speed but never reduce it. If acceleration would slow the player (input pointing far
    // away from the momentum), it is skipped and only the steered velocity is kept.
    public static Vec3 airMove(Vec3 velocity, Vec3 wishDir, double wishSpeed, double airSpeedCap, double airAccelerate,
                               double steerMaxAngle, double steerMaxTurn, boolean allowSpeedLoss) {
        Vec3 steered = airSteer(velocity, wishDir, steerMaxAngle, steerMaxTurn);
        Vec3 accelerated = airAccelerate(steered, wishDir, wishSpeed, airSpeedCap, airAccelerate);
        if (!allowSpeedLoss && accelerated.horizontalDistanceSqr() < steered.horizontalDistanceSqr()) {
            return steered;
        }
        return accelerated;
    }

    // Slide boost: the speed a boost of `boost` takes a player moving at `speed` to, never above maxSpeed.
    // A player already faster than maxSpeed keeps their speed (the result is never below `speed`).
    public static double boostedSpeed(double speed, double boost, double maxSpeed) {
        return Math.max(speed, Math.min(speed + boost, maxSpeed));
    }

    // Ticks (at least 1, at most maxTicks) for a speed of `peak` to decay below `threshold` when keeping `friction`
    // of it per tick. Used to turn Apex's "slide jump once speed drops below 350 units/s" into a boost duration.
    public static int ticksToDecayBelow(double peak, double threshold, double friction, int maxTicks) {
        if (peak < threshold) {
            return Math.min(1, maxTicks);
        }
        if (friction >= 1.0 || threshold <= 0.0) {
            return maxTicks;
        }
        int ticks = (int) Math.ceil(Math.log(threshold / peak) / Math.log(friction));
        return Mth.clamp(ticks, Math.min(1, maxTicks), maxTicks);
    }

    // Scales a slide friction value by the block's slipperiness: normal blocks (0.6) use it as-is, slipperier
    // blocks lose proportionally less speed (ice at 0.98 loses only 5% as much).
    public static double surfaceAdjustedFriction(double friction, double blockFriction) {
        double surfaceFactor = Math.max(0.0, (1.0 - blockFriction) / (1.0 - NORMAL_BLOCK_FRICTION));
        return Mth.clamp(1.0 - (1.0 - friction) * surfaceFactor, 0.0, 1.0);
    }

    // Horizontal speed change per tick from gravity along a slope: g * sin(angle) * cos(angle), where the slope is
    // given as height change (rise) over horizontal distance (run). Negative when going uphill.
    public static double slopeAcceleration(double gravityPerTick, double rise, double run) {
        double lengthSqr = rise * rise + run * run;
        if (lengthSqr < 1.0E-9) {
            return 0.0;
        }
        return -gravityPerTick * rise * run / lengthSqr;
    }

    // Changes horizontal speed by `delta` along the current direction, never reversing it (stops at zero).
    public static Vec3 addAlongDirection(Vec3 velocity, double delta) {
        double speed = velocity.horizontalDistance();
        if (speed < 1.0E-6) {
            return velocity;
        }
        double scale = Math.max(0.0, speed + delta) / speed;
        return new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
    }

    // Peak height (blocks) of a jump starting at `jumpVelocity` (blocks/tick) under `gravity` (blocks/tick^2),
    // integrated tick by tick the way MomentumTravel moves the player (move by velocity, then apply gravity).
    public static double jumpHeight(double jumpVelocity, double gravity) {
        double y = 0.0;
        double vy = jumpVelocity;
        double peak = 0.0;
        for (int tick = 0; tick < 1000 && vy > 0.0; tick++) {
            y += vy;
            peak = Math.max(peak, y);
            vy -= gravity;
        }
        return peak;
    }

    // The jump velocity (blocks/tick) whose peak height (see jumpHeight) is `height` blocks.
    public static double jumpVelocityForHeight(double height, double gravity) {
        if (height <= 0.0 || gravity <= 0.0) {
            return 0.0;
        }
        double low = 0.0;
        double high = Math.max(height, 1.0);
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2.0;
            if (jumpHeight(mid, gravity) < height) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2.0;
    }

    // m/s -> blocks/tick, and m/s^2 -> blocks/tick^2 (1 block = 1 m).
    public static double perTick(double metersPerSecond) {
        return metersPerSecond / TICKS_PER_SECOND;
    }

    public static double perTickSquared(double metersPerSecondSquared) {
        return metersPerSecondSquared / (TICKS_PER_SECOND * TICKS_PER_SECOND);
    }

    // Vanilla ground acceleration scaling: on slippery blocks (friction above a normal block's) acceleration shrinks
    // by (0.6 / friction)^3 so that top speed on ice stays about the same as on stone.
    public static double groundAcceleration(double baseAcceleration, double blockFriction) {
        if (blockFriction <= NORMAL_BLOCK_FRICTION) {
            return baseAcceleration;
        }
        double ratio = NORMAL_BLOCK_FRICTION / blockFriction;
        return baseAcceleration * ratio * ratio * ratio;
    }

    // Applies an attribute modifier to a friction value the way vanilla does (LivingEntity#computeModifiedFriction):
    // modifier 1 = unchanged, 0 = no friction, 2 = twice the speed loss.
    public static double modifiedFriction(double friction, double modifier) {
        return Mth.clamp(1.0 - (1.0 - friction) * modifier, 0.0, 1.0);
    }

    // Keeps acceleration from pushing horizontal speed past the cap. Speed that is already above the cap
    // (from knockback, explosions, ...) is not cut, only prevented from growing further.
    public static Vec3 limitAcceleratedSpeed(Vec3 before, Vec3 after, double cap) {
        double afterSpeed = after.horizontalDistance();
        if (afterSpeed <= cap) {
            return after;
        }

        double limit = Math.max(cap, before.horizontalDistance());
        if (afterSpeed <= limit) {
            return after;
        }

        double scale = limit / afterSpeed;
        return new Vec3(after.x * scale, after.y, after.z * scale);
    }
}
