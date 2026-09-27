package io.github.naistafang.apexmovement.mixin.client;

import io.github.naistafang.apexmovement.client.camera.StepSmoothing;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Why a mixin: NeoForge's camera event (ViewportEvent.ComputeCameraAngles) can only change the camera's rotation, not
// its position, and smoothing step-ups needs to lower the camera for a moment (see StepSmoothing).
//
// Why Camera: it places itself at the player's eyes in alignWithEntity, every frame. We shift it down afterwards, only
// in first person and only when it follows the local player. Client only (listed under "client" in the mixin config).
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private @Nullable Entity entity;

    @Shadow
    private boolean detached;

    @Shadow
    public abstract Vec3 position();

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void apexmovement$smoothStepUps(float partialTicks, CallbackInfo ci) {
        if (!this.detached && this.entity instanceof LocalPlayer player) {
            double offset = StepSmoothing.cameraOffset(player, partialTicks);
            if (offset != 0.0) {
                this.setPosition(this.position().subtract(0.0, offset, 0.0));
            }
        }
    }
}
