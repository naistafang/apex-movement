package io.github.naistafang.apexmovement.client.animation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

// Client only: shared steps for our full-body poses (SlidePose, MantlePose).
//
// Model coordinates are in pixels (1/16 block), with y pointing DOWN from the top of the head: the hips are at
// y = 12 and the feet at y = 24. Negative xRot swings a limb forward (and up, for arms).
final class PoseHelper {
    private static final float HIP_Y = 12.0F;

    private PoseHelper() {}

    // Blends vanilla's crouch offsets (shifted body/head/arms/legs) back to the neutral positions and straightens the
    // body, so our pose starts from a standing layout. Players keep the crouch hitbox while sliding or mantling.
    static void undoCrouchOffsets(HumanoidModel<?> model, float amount) {
        for (ModelPart part : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            PartPose initial = part.getInitialPose();
            part.x = Mth.lerp(amount, part.x, initial.x());
            part.y = Mth.lerp(amount, part.y, initial.y());
            part.z = Mth.lerp(amount, part.z, initial.z());
        }
        model.body.xRot = Mth.lerp(amount, model.body.xRot, 0.0F);
    }

    // Tilts the whole model around the hips (positive = lean forward, negative = lean back) and moves it down by
    // `drop` pixels. Rotating the root turns everything around its pivot at the top of the head, so it is shifted to
    // keep the hip point in place. Limb angles set afterwards are relative to the tilted body.
    static void leanAroundHips(HumanoidModel<?> model, float pitch, float drop) {
        ModelPart root = model.root();
        root.xRot += pitch;
        root.y += HIP_Y - HIP_Y * Mth.cos(pitch) + drop;
        root.z += -HIP_Y * Mth.sin(pitch);
    }

    static void blendRotation(ModelPart part, float amount, float xRot, float yRot, float zRot) {
        part.xRot = Mth.lerp(amount, part.xRot, xRot);
        part.yRot = Mth.lerp(amount, part.yRot, yRot);
        part.zRot = Mth.lerp(amount, part.zRot, zRot);
    }
}
