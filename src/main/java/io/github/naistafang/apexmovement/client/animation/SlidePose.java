package io.github.naistafang.apexmovement.client.animation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

// Client only: the slide pose, an original pose (not based on any game's animation). The body leans back
// with the lead (right) leg stretched forward, the other leg tucked, the right arm out for balance and the left
// hand reaching down. Blended over vanilla's pose by `amount` (0..1). See PoseHelper for model coordinates.
public final class SlidePose {
    // How far the whole body leans back, in radians (~35 degrees).
    private static final float LEAN_BACK = 0.6F;
    // How far (pixels) the hips sink toward the ground, on top of vanilla's 2-pixel crouch offset.
    private static final float HIP_DROP = 5.0F;

    // Target limb angles in the world, i.e. before compensating for the body lean (radians).
    private static final float LEAD_LEG_PITCH = -1.31F;  // ~75 degrees forward, almost flat
    private static final float TUCKED_LEG_PITCH = -1.0F;
    private static final float TUCKED_LEG_YAW = -0.25F;
    private static final float TUCKED_LEG_ROLL = -0.25F;
    private static final float BALANCE_ARM_PITCH = -0.3F;
    private static final float BALANCE_ARM_ROLL = 0.9F;   // right arm out to the side
    private static final float REACH_ARM_PITCH = -1.5F;   // left hand forward and down
    private static final float REACH_ARM_ROLL = -0.2F;

    private SlidePose() {}

    public static void apply(HumanoidModel<?> model, HumanoidRenderState state, float amount) {
        PoseHelper.undoCrouchOffsets(model, amount);
        float lean = LEAN_BACK * amount;
        PoseHelper.leanAroundHips(model, -lean, HIP_DROP * amount);

        // Limbs are relative to the leaning body, so add the lean back onto the world angles.
        // The head keeps looking where the player looks.
        model.head.xRot = state.xRot * Mth.DEG_TO_RAD + lean;
        PoseHelper.blendRotation(model.rightLeg, amount, LEAD_LEG_PITCH + LEAN_BACK, 0.1F, 0.05F);
        PoseHelper.blendRotation(model.leftLeg, amount, TUCKED_LEG_PITCH + LEAN_BACK, TUCKED_LEG_YAW, TUCKED_LEG_ROLL);
        PoseHelper.blendRotation(model.rightArm, amount, BALANCE_ARM_PITCH + LEAN_BACK, 0.0F, BALANCE_ARM_ROLL);
        PoseHelper.blendRotation(model.leftArm, amount, REACH_ARM_PITCH + LEAN_BACK, 0.0F, REACH_ARM_ROLL);
    }
}
