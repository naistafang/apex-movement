package io.github.naistafang.apexmovement.mixin;

import io.github.naistafang.apexmovement.movement.MomentumTravel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Why a mixin: holding crouch makes vanilla stop players at block edges (Player#maybeBackOffFromEdge asks
// isStayingOnGroundSurface(), which just returns "is crouch held"). Slides are crouch-held but must be able to
// go off ledges and down steps. There is no event for this check.
//
// Why Player: isStayingOnGroundSurface() is declared in Player, so only players are affected. It is checked on
// both sides: the client for its own movement, the server when re-checking the moves the client reports.
@Mixin(Player.class)
public abstract class PlayerEdgeMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void apexmovement$slideIgnoresEdges(CallbackInfoReturnable<Boolean> cir) {
        if (MomentumTravel.ignoresSneakEdges((Player) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
