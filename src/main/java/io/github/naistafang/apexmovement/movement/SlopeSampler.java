package io.github.naistafang.apexmovement.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

// Estimates the ground slope in the direction of movement. Minecraft terrain has no real slopes, so a hill is a
// staircase of blocks (or stairs/slabs); we compare the ground height one block ahead with the player's feet.
final class SlopeSampler {
    // Horizontal distance ahead to sample the ground height.
    static final double RUN = 1.0;
    // Only look this far up/down for ground ahead; anything beyond is a wall or a drop, not a slope.
    private static final double MAX_RISE = 1.0;

    private SlopeSampler() {}

    // Height change (positive = uphill) of the ground RUN blocks ahead along `direction`, or 0 if there is no
    // ground within MAX_RISE (a ledge or a wall; the slide then just falls or collides normally).
    static double rise(Level level, Vec3 feet, Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8) {
            return 0.0;
        }
        Vec3 dir = horizontal.normalize();
        double x = feet.x + dir.x * RUN;
        double z = feet.z + dir.z * RUN;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(feet.y + MAX_RISE);
        int bottom = Mth.floor(feet.y - MAX_RISE - 0.5);
        for (int y = top; y >= bottom; y--) {
            pos.set(Mth.floor(x), y, Mth.floor(z));
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double groundTop = y + shape.max(Direction.Axis.Y);
            double rise = groundTop - feet.y;
            if (rise > MAX_RISE + 1.0E-6) {
                return 0.0; // wall
            }
            return rise < -MAX_RISE - 1.0E-6 ? 0.0 : rise;
        }
        return 0.0;
    }
}
