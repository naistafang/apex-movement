package io.github.naistafang.apexmovement.movement;

import io.github.naistafang.apexmovement.config.MovementConfig;
import io.github.naistafang.apexmovement.movement.physics.MomentumPhysics;
import io.github.naistafang.apexmovement.movement.state.MantlePhase;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

// Wall climbing and mantling (client side; the client simulates its own player and reports the MANTLING state).
// Starting: in the air, facing a wall and holding forward toward it, with a ledge the player fits on within
// maxLedgeHeight. Then: CLIMB up the wall until the ledge is within reach (skipped if it already is), RISE until the
// feet are level with the ledge, PULL forward onto it. Like Apex, a started mantle finishes on its own; only the climb
// can be abandoned (turning away or releasing the movement keys). One climb per jump: touching the ground allows the
// next. While climbing, left/right input moves along the wall (sideways climbing) and a jump press pushes off the wall
// (wall-bounce, see wallJump()). A jump pressed in the mantle's last moments turns it into a superglide (see superglide()).
// All movement goes through move(), so collisions stay vanilla and the server's re-check of the moves agrees.
final class Mantling {
    // How far in front of the player's side to look for a wall, and the gap kept when placing the player on the ledge.
    private static final double WALL_PROBE = 0.2;
    private static final double LEDGE_INSET = 0.05;
    // Height kept above the ledge while moving onto it, so the move doesn't catch on the ledge's edge.
    private static final double LEDGE_CLEARANCE = 0.01;
    // Slight push into the wall while climbing so the player stays against it.
    private static final double WALL_PUSH = 0.01;
    // Vanilla scales full movement input to this length.
    private static final double FULL_INPUT = 0.98;

    private record Ledge(double top, double targetX, double targetZ) {}

    private Mantling() {}

    // Tries to start a climb or mantle this tick. Returns true if one started (the caller switches to MANTLING).
    static boolean tryStart(Player player, MovementData data, Vec3 input) {
        if (player.onGround() || data.climbUsed()) {
            return false;
        }
        Direction face = wallFace(player);
        if (!facingWall(player, input, face)) {
            return false;
        }
        if (!wallAhead(player, face)) {
            return false;
        }

        double feetY = player.getY();
        Ledge ledge = findLedge(player, face, feetY + MovementConfig.STEP_HEIGHT.getAsDouble(),
                feetY + MovementConfig.MANTLE_MAX_LEDGE_HEIGHT.getAsDouble());
        if (ledge == null) {
            return false;
        }
        boolean inReach = ledge.top() - player.getY() <= MovementConfig.MANTLE_REACH.getAsDouble();
        data.startMantle(inReach ? MantlePhase.RISE : MantlePhase.CLIMB, face, ledge.top(), ledge.targetX(), ledge.targetZ(), player.getY());
        if (inReach) {
            data.setMantlePhase(MantlePhase.RISE, secondsToTicks(MovementConfig.MANTLE_RISE_TIME.getAsDouble()));
        }
        return true;
    }

    // Runs one tick of the current mantle phase, moving the player. Returns false once the mantle is over
    // (finished or abandoned); the caller then goes back to the normal state machine next tick.
    static boolean tick(Player player, MovementData data, Vec3 input) {
        return switch (data.mantlePhase()) {
            case CLIMB -> climbTick(player, data, input);
            case RISE -> riseTick(player, data);
            case PULL -> pullTick(player, data);
            case NONE -> false;
        };
    }

    // Climbing up the wall, and along it with left/right input (Apex allows much faster sideways than upward).
    private static boolean climbTick(Player player, MovementData data, Vec3 input) {
        Direction face = data.wallFace();
        if (!keepsClimbing(player, input, face)) {
            return detach(player, data);
        }

        Vec3 along = new Vec3(-face.getStepZ(), 0.0, face.getStepX()); // horizontal, parallel to the wall
        Vec3 wish = MomentumPhysics.wishVelocity(input, 1.0, player.getYRot());
        double side = Mth.clamp(wish.dot(along) / FULL_INPUT, -1.0, 1.0);
        Vec3 sideVelocity = along.scale(side * MomentumPhysics.perTick(MovementConfig.CLIMB_SIDE_SPEED.getAsDouble()));
        double climbStep = MomentumPhysics.perTick(MovementConfig.CLIMB_SPEED.getAsDouble());
        if (data.consumeWallJumpRequest()) {
            wallJump(player, data, face, sideVelocity, climbStep);
            return false;
        }

        double startY = player.getY();
        player.setDeltaMovement(face.getStepX() * WALL_PUSH + sideVelocity.x, climbStep, face.getStepZ() * WALL_PUSH + sideVelocity.z);
        player.move(MoverType.SELF, player.getDeltaMovement());

        boolean blocked = player.getY() - startY < climbStep * 0.5;
        double maxTop = data.climbStartY() + MovementConfig.MANTLE_MAX_LEDGE_HEIGHT.getAsDouble();
        boolean tooHigh = player.getY() > maxTop;
        if (blocked || tooHigh) {
            return detach(player, data);
        }
        if (sideVelocity.lengthSqr() > 1.0E-8) {
            // Moved along the wall: it may have ended, and the ledge above may be at a different height now.
            if (!wallAhead(player, face)) {
                return detach(player, data);
            }
            Ledge ledge = findLedge(player, face, player.getY(), maxTop);
            if (ledge != null) {
                data.setLedge(ledge.top(), ledge.targetX(), ledge.targetZ());
            } else {
                data.setLedge(Double.POSITIVE_INFINITY, player.getX(), player.getZ()); // nothing to mantle here (yet)
            }
        }
        if (data.ledgeTop() - player.getY() <= MovementConfig.MANTLE_REACH.getAsDouble()) {
            data.setMantlePhase(MantlePhase.RISE, secondsToTicks(MovementConfig.MANTLE_RISE_TIME.getAsDouble()));
        }
        return true;
    }

    private static boolean riseTick(Player player, MovementData data) {
        int ticksLeft = data.takeMantleTick();
        double dy = Math.max(0.0, data.ledgeTop() + LEDGE_CLEARANCE - player.getY()) / ticksLeft;
        player.setDeltaMovement(0.0, dy, 0.0);
        player.move(MoverType.SELF, player.getDeltaMovement());
        // A long superglide window can open before the pull-over. A jump pressed now stays requested and fires on the
        // first pull tick, once the feet are level with the ledge (launching from below it would hit the wall).
        int ticksLeftInMantle = ticksLeft - 1 + secondsToTicks(MovementConfig.MANTLE_PULL_TIME.getAsDouble());
        if (ticksLeftInMantle <= secondsToTicks(MovementConfig.SUPERGLIDE_WINDOW.getAsDouble())) {
            data.setSuperglideWindowOpen(true);
        }
        if (ticksLeft <= 1) {
            data.setMantlePhase(MantlePhase.PULL, secondsToTicks(MovementConfig.MANTLE_PULL_TIME.getAsDouble()));
        }
        return true;
    }

    private static boolean pullTick(Player player, MovementData data) {
        int ticksLeft = data.takeMantleTick();
        if (data.consumeSuperglideRequest()) {
            superglide(player, data);
            return false;
        }

        double dx = (data.mantleTargetX() - player.getX()) / ticksLeft;
        double dz = (data.mantleTargetZ() - player.getZ()) / ticksLeft;
        double dy = Math.max(0.0, data.ledgeTop() + LEDGE_CLEARANCE - player.getY()) / ticksLeft;
        player.setDeltaMovement(dx, dy, dz);
        player.move(MoverType.SELF, player.getDeltaMovement());

        // The superglide window covers the mantle's last `window` seconds: open it once the remaining ticks fit in it,
        // so jump presses from now on count (MantleJumpInput records them between ticks). It may already be open
        // from the end of the rise (see riseTick).
        if (ticksLeft - 1 <= secondsToTicks(MovementConfig.SUPERGLIDE_WINDOW.getAsDouble())) {
            data.setSuperglideWindowOpen(true);
        }
        if (ticksLeft > 1) {
            return true;
        }
        // Done: on the ledge, moving on at the exit speed (ground friction takes it from there; holding forward keeps
        // going). Like Apex, finishing a mantle clears jump fatigue.
        data.endMantle();
        data.clearJumpFatigue();
        Direction face = data.wallFace();
        double exit = MomentumPhysics.perTick(MovementConfig.MANTLE_EXIT_SPEED.getAsDouble());
        player.setDeltaMovement(face.getStepX() * exit, 0.0, face.getStepZ() * exit);
        player.resetFallDistance();
        return false;
    }

    // Wall-bounce: a jump pressed while climbing pushes off the wall, keeping the climb's upward speed plus a small
    // jump, and any sideways climbing speed. Like any detach, no new climb until the ground is touched.
    private static void wallJump(Player player, MovementData data, Direction face, Vec3 sideVelocity, double climbStep) {
        data.endMantle();
        data.markWallJumpSound();
        double push = MomentumPhysics.perTick(MovementConfig.WALL_JUMP_SPEED.getAsDouble());
        double gravity = MomentumPhysics.perTickSquared(MovementConfig.GRAVITY.getAsDouble());
        double up = climbStep + MomentumPhysics.jumpVelocityForHeight(MovementConfig.WALL_JUMP_HEIGHT.getAsDouble(), gravity);
        player.setDeltaMovement(-face.getStepX() * push + sideVelocity.x, up, -face.getStepZ() * push + sideVelocity.z);
        player.move(MoverType.SELF, player.getDeltaMovement());
    }

    // Superglide: the mantle ends at once and turns into a launch, like a slide and a jump combined but in mid-air
    // (so no ground friction): superglide speed forward in the look direction plus a normal jump upward. The flight
    // is shown with the slide animation; holding crouch while landing goes straight into a real slide.
    private static void superglide(Player player, MovementData data) {
        data.endMantle();
        data.clearJumpFatigue();
        data.setSuperglideFlight(true);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double speed = MomentumPhysics.perTick(MovementConfig.SUPERGLIDE_SPEED.getAsDouble());
        double up = MomentumPhysics.perTick(MovementConfig.JUMP_VELOCITY.getAsDouble());
        player.setDeltaMovement(-Mth.sin(yaw) * speed, up, Mth.cos(yaw) * speed);
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.resetFallDistance();
    }

    // Abandons a climb: the player falls from where they are, and can't climb again until they touch the ground.
    private static boolean detach(Player player, MovementData data) {
        data.endMantle();
        player.setDeltaMovement(0.0, 0.0, 0.0);
        return false;
    }

    // The horizontal direction (N/E/S/W) the player is looking toward.
    private static Direction wallFace(Player player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        return Direction.getApproximateNearest(-Mth.sin(yaw), 0.0F, Mth.cos(yaw));
    }

    // A wall right in front of the player.
    private static boolean wallAhead(Player player, Direction face) {
        AABB probe = player.getBoundingBox().move(face.getStepX() * WALL_PROBE, 0.0, face.getStepZ() * WALL_PROBE);
        return !player.level().noCollision(player, probe);
    }

    // Keeping a climb going is looser than starting one: the view must still face the wall, but the input may be
    // anything that doesn't point away from it, so pure left/right climbs sideways.
    private static boolean keepsClimbing(Player player, Vec3 input, Direction face) {
        Vec3 faceNormal = new Vec3(face.getStepX(), 0.0, face.getStepZ());
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 look = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        if (angleDegrees(look, faceNormal) > MovementConfig.MANTLE_MAX_LOOK_ANGLE.getAsDouble()) {
            return false;
        }
        Vec3 wish = MomentumPhysics.wishVelocity(input, 1.0, player.getYRot());
        return wish.lengthSqr() > 1.0E-6 && wish.dot(faceNormal) >= -1.0E-3;
    }

    // View within maxLookAngle of the wall direction, and movement input within maxInputAngle of it.
    private static boolean facingWall(Player player, Vec3 input, Direction face) {
        Vec3 faceNormal = new Vec3(face.getStepX(), 0.0, face.getStepZ());
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 look = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        if (angleDegrees(look, faceNormal) > MovementConfig.MANTLE_MAX_LOOK_ANGLE.getAsDouble()) {
            return false;
        }
        Vec3 wish = MomentumPhysics.wishVelocity(input, 1.0, player.getYRot());
        return wish.lengthSqr() > 1.0E-6 && angleDegrees(wish, faceNormal) <= MovementConfig.MANTLE_MAX_INPUT_ANGLE.getAsDouble();
    }

    // The lowest ledge in the block column in front of the player that the player fits on (standing or crouching),
    // with its top above minTop and at most maxTop. When starting, minTop is the step height above the feet (lower
    // ledges are simply stepped or jumped onto, which is faster).
    private static @Nullable Ledge findLedge(Player player, Direction face, double minTop, double maxTop) {
        Level level = player.level();
        double halfWidth = player.getBbWidth() / 2.0;
        int columnX = Mth.floor(player.getX() + face.getStepX() * (halfWidth + 0.5));
        int columnZ = Mth.floor(player.getZ() + face.getStepZ() * (halfWidth + 0.5));

        // Place the player just past the wall's face, keeping their position along the wall.
        double targetX = player.getX();
        double targetZ = player.getZ();
        if (face.getAxis() == Direction.Axis.X) {
            double plane = face.getStepX() > 0 ? columnX : columnX + 1;
            targetX = plane + face.getStepX() * (halfWidth + LEDGE_INSET);
        } else {
            double plane = face.getStepZ() > 0 ? columnZ : columnZ + 1;
            targetZ = plane + face.getStepZ() * (halfWidth + LEDGE_INSET);
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = Mth.floor(minTop) - 1; y <= Mth.floor(maxTop); y++) {
            pos.set(columnX, y, columnZ);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double top = y + shape.max(Direction.Axis.Y);
            if (top <= minTop) {
                continue;
            }
            if (top > maxTop) {
                break;
            }
            Vec3 target = new Vec3(targetX, top + LEDGE_CLEARANCE, targetZ);
            if (fits(player, Pose.STANDING, target) || fits(player, Pose.CROUCHING, target)) {
                return new Ledge(top, targetX, targetZ);
            }
        }
        return null;
    }

    private static boolean fits(Player player, Pose pose, Vec3 position) {
        return player.level().noCollision(player, player.getDimensions(pose).makeBoundingBox(position));
    }

    private static double angleDegrees(Vec3 a, Vec3 b) {
        double lengths = Math.sqrt(a.lengthSqr() * b.lengthSqr());
        if (lengths < 1.0E-9) {
            return 180.0;
        }
        return Math.toDegrees(Math.acos(Mth.clamp(a.dot(b) / lengths, -1.0, 1.0)));
    }

    private static int secondsToTicks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * MomentumPhysics.TICKS_PER_SECOND));
    }
}
