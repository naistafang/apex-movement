package io.github.naistafang.apexmovement.movement;

import io.github.naistafang.apexmovement.movement.state.MovementData;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Lets a slide go into gaps only 1 block high. Sliding uses the crouch hitbox (1.5 blocks tall), which hits the edge
// of such a gap. Vanilla only switches to the low crawling hitbox (0.6 tall, the SWIMMING pose) once a player is
// already under a low ceiling, never to enter one. So while sliding toward a gap that fits the crawl hitbox but not
// the crouch one, we force the crawl pose early (NeoForge's Player#setForcedPose) and keep it while under the low
// ceiling, then hand the pose back to vanilla (which keeps crawling if there is still no room to stand).
// Runs on both sides with the same rule, so the server re-checks the client's moves with the same hitbox.
final class LowGapSlide {
    // How far ahead to look, in ticks of travel at the current speed (the pose changes a tick after we set it).
    private static final double LOOKAHEAD_TICKS = 3.0;
    private static final double LOOKAHEAD_MARGIN = 0.3;
    private static final double STEP = 0.2;

    private LowGapSlide() {}

    // Called every movement tick. `sliding` is whether the player is in (or, on the server, about to be in) a slide;
    // `velocity` is the horizontal direction and speed of travel (blocks/tick).
    static void update(Player player, MovementData data, boolean sliding, Vec3 velocity) {
        boolean wantLow = sliding && (!fits(player, Pose.CROUCHING, player.position()) || lowGapAhead(player, velocity));
        if (wantLow && !data.lowSlideForced() && player.getForcedPose() == null) {
            player.setForcedPose(Pose.SWIMMING);
            data.setLowSlideForced(true);
        } else if (!wantLow && data.lowSlideForced()) {
            if (player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
            data.setLowSlideForced(false);
        }
    }

    // Along the direction of travel: a spot where the crawl hitbox fits but the crouch one doesn't, before any wall.
    private static boolean lowGapAhead(Player player, Vec3 velocity) {
        Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
        double speed = horizontal.length();
        if (speed < 1.0E-4) {
            return false;
        }
        Vec3 dir = horizontal.scale(1.0 / speed);
        double reach = speed * LOOKAHEAD_TICKS + LOOKAHEAD_MARGIN;
        for (double distance = STEP; distance <= reach; distance += STEP) {
            Vec3 ahead = player.position().add(dir.scale(distance));
            if (!fits(player, Pose.SWIMMING, ahead)) {
                return false; // a wall even crawling won't pass
            }
            if (!fits(player, Pose.CROUCHING, ahead)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fits(Player player, Pose pose, Vec3 position) {
        Level level = player.level();
        return level.noCollision(player, player.getDimensions(pose).makeBoundingBox(position).deflate(1.0E-7));
    }
}
