package io.github.naistafang.apexmovement.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.naistafang.apexmovement.client.firstperson.FirstPersonBody;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Why a mixin: vanilla skips drawing the camera's own player unless the camera is detached (third person), in
// LevelExtractor#extractVisibleEntities (`entity != camera.entity() || camera.isDetached() || sleeping`). There is no
// event to change that. We treat the camera as "detached" for that single check while FirstPersonBody wants the
// body drawn; the camera itself stays in first person. The check is only reached for the camera's own entity.
// Uses MixinExtras (bundled with NeoForge) to wrap just that one isDetached() call.
// Client only (listed under "client" in apexmovement.mixins.json).
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean apexmovement$drawOwnBody(boolean detached) {
        return detached || FirstPersonBody.shouldRender();
    }
}
