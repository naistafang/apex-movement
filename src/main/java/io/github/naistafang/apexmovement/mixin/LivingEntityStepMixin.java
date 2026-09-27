package io.github.naistafang.apexmovement.mixin;

import io.github.naistafang.apexmovement.movement.MomentumTravel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Why a mixin: running and sliding should treat 1-block rises as a steep slope and go straight up them, but
// players can only step up 0.6 blocks. The step height comes from maxUpStep(); changing the STEP_HEIGHT attribute
// instead would be synced from server to client and saved with the player. There is no event for it.
//
// Why LivingEntity: maxUpStep() is defined in LivingEntity and Player doesn't override it, so there is no
// Player method to target. The first check returns vanilla behavior for anything that isn't a player.
// Runs on both sides, so the server's re-check of client moves allows the same step-up.
@Mixin(LivingEntity.class)
public abstract class LivingEntityStepMixin {
    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void apexmovement$stepHeight(CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof Player player)) {
            return;
        }
        float step = MomentumTravel.stepHeight(player);
        if (step > cir.getReturnValueF()) {
            cir.setReturnValue(step);
        }
    }
}
