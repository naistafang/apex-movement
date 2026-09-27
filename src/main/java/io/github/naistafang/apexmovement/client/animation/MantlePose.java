package io.github.naistafang.apexmovement.client.animation;

import io.github.naistafang.apexmovement.movement.state.MantlePhase;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

// Client only: third-person climbing/mantling poses (original poses, not based on any game's animation).
//   CLIMB: facing the wall, arms reaching up it (angled forward so they are also in view in first person, see
//          FirstPersonBody) and alternating hand over hand, legs stepping.
//   RISE:  both hands gripping the ledge overhead, one knee pulled up.
//   PULL:  leaning over the ledge, hands pushing down on it, the lead leg stepping up onto it.
// When the phase changes, the pose blends from the previous phase's pose. Blended over vanilla's pose by `amount`.
// See PoseHelper for model coordinates.
public final class MantlePose {
    // Climbing rhythm: radians of the hand-over-hand cycle per tick, and how far limbs swing.
    private static final float CLIMB_CYCLE_PER_TICK = 0.9F;
    private static final float CLIMB_ARM_SWING = 0.35F;
    private static final float CLIMB_LEG_SWING = 0.45F;

    // Angles in the world (radians), i.e. before compensating for the body lean.
    private record Pose(float lean, float rightArm, float leftArm, float armSpread, float rightLeg, float leftLeg) {
        Pose lerp(float t, Pose to) {
            return new Pose(Mth.lerp(t, this.lean, to.lean), Mth.lerp(t, this.rightArm, to.rightArm), Mth.lerp(t, this.leftArm, to.leftArm),
                    Mth.lerp(t, this.armSpread, to.armSpread), Mth.lerp(t, this.rightLeg, to.rightLeg), Mth.lerp(t, this.leftLeg, to.leftLeg));
        }
    }

    private MantlePose() {}

    public static void apply(HumanoidModel<?> model, HumanoidRenderState state, float amount, MantlePhase from, MantlePhase to, float phaseBlend) {
        Pose pose = pose(from, state.ageInTicks).lerp(phaseBlend, pose(to, state.ageInTicks));

        PoseHelper.undoCrouchOffsets(model, amount);
        float lean = pose.lean() * amount;
        PoseHelper.leanAroundHips(model, lean, 0.0F);

        // Limbs are relative to the leaning body, so subtract the lean from the world angles.
        // The head keeps looking where the player looks.
        model.head.xRot = state.xRot * Mth.DEG_TO_RAD - lean;
        PoseHelper.blendRotation(model.rightArm, amount, pose.rightArm() - pose.lean(), 0.0F, pose.armSpread());
        PoseHelper.blendRotation(model.leftArm, amount, pose.leftArm() - pose.lean(), 0.0F, -pose.armSpread());
        PoseHelper.blendRotation(model.rightLeg, amount, pose.rightLeg() - pose.lean(), 0.0F, 0.0F);
        PoseHelper.blendRotation(model.leftLeg, amount, pose.leftLeg() - pose.lean(), 0.0F, 0.0F);
    }

    private static Pose pose(MantlePhase phase, float ageInTicks) {
        return switch (phase) {
            case CLIMB, NONE -> {
                float cycle = Mth.sin(ageInTicks * CLIMB_CYCLE_PER_TICK);
                yield new Pose(0.15F,
                        -2.3F + CLIMB_ARM_SWING * cycle, -2.3F - CLIMB_ARM_SWING * cycle, 0.1F,
                        -0.5F + CLIMB_LEG_SWING * cycle, -0.5F - CLIMB_LEG_SWING * cycle);
            }
            case RISE -> new Pose(0.35F, -2.1F, -2.1F, 0.15F, -1.1F, 0.1F);
            case PULL -> new Pose(0.7F, -0.9F, -0.9F, 0.25F, -1.3F, 0.3F);
        };
    }
}
