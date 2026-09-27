package io.github.naistafang.apexmovement.mixin.client;

import io.github.naistafang.apexmovement.client.animation.MantlePose;
import io.github.naistafang.apexmovement.client.animation.MovementAnimations;
import io.github.naistafang.apexmovement.client.animation.SlidePose;
import io.github.naistafang.apexmovement.movement.state.MantlePhase;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Why a mixin: vanilla poses the arms, legs and head in HumanoidModel#setupAnim every frame, and no event runs
// after it to adjust the pose, so the slide and mantle poses are applied at the end of that method.
//
// Why HumanoidModel (not PlayerModel): armor and the cape are drawn with their own humanoid models posed from the
// same render state, so they must get the same pose or they would float in the crouch pose. Only players' render
// states carry our animation data (see MovementAnimations), so for mobs this returns right away.
// Client only (listed under "client" in apexmovement.mixins.json).
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void apexmovement$movementPoses(HumanoidRenderState state, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        float mantle = state.getRenderDataOrDefault(MovementAnimations.MANTLE_AMOUNT, 0.0F);
        // In a 1-block-high gap the player is in the crawling pose; let vanilla's crawl animation take over there.
        float slide = state.getRenderDataOrDefault(MovementAnimations.SLIDE_AMOUNT, 0.0F) * (1.0F - state.swimAmount);
        // Only one full-body pose at a time: the stronger one (e.g. a superglide's slide pose takes over as the
        // mantle pose fades out).
        if (mantle > 0.0F && mantle >= slide) {
            MantlePose.apply(model, state, mantle,
                    state.getRenderDataOrDefault(MovementAnimations.MANTLE_PHASE_FROM, MantlePhase.CLIMB),
                    state.getRenderDataOrDefault(MovementAnimations.MANTLE_PHASE, MantlePhase.CLIMB),
                    state.getRenderDataOrDefault(MovementAnimations.MANTLE_PHASE_BLEND, 1.0F));
            return;
        }
        if (slide > 0.0F) {
            SlidePose.apply(model, state, slide);
        }
    }
}
