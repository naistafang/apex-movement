package io.github.naistafang.apexmovement.mixin;

import io.github.naistafang.apexmovement.movement.MomentumTravel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Why a mixin: NeoForge has no event that lets us replace the per-tick movement physics
// (acceleration and friction live inside LivingEntity#travelInAir, a private method).
//
// Why Player (not LivingEntity): Player overrides travel(), so injecting here only ever affects
// players. Mobs never run this code. Runs on both sides; MomentumTravel decides per tick whether
// our physics applies, and returns control to vanilla (swimming, flying, elytra, ...) otherwise.
//
// Extending LivingEntity is a mixin idiom: it lets this class call LivingEntity's protected methods
// on the player. The constructor is never actually used.
@Mixin(Player.class)
public abstract class PlayerTravelMixin extends LivingEntity {
    protected PlayerTravelMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void apexmovement$momentumTravel(Vec3 input, CallbackInfo ci) {
        boolean inFluid = this.shouldTravelInFluid(this.level().getFluidState(this.blockPosition()));
        if (MomentumTravel.travel((Player) (Object) this, input, this.jumping, inFluid, this::getEffectiveGravity)) {
            ci.cancel();
        }
    }
}
