package io.github.naistafang.apexmovement.client.firstperson;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.config.ClientConfig;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.Nullable;

// Client only: shows your own body in first person while sliding or mantling, so you see your legs when looking down
// in a slide and your full arms (and legs) while climbing. Vanilla never draws the camera's own player in first
// person; LevelExtractorMixin lets it through while this is active. The body is the normal third-person model in the
// slide/mantle pose (SlidePose, MantlePose), with the parts the camera would be inside of hidden:
//   always hidden: head and torso (with whatever is worn on them)
//   sliding:       arms hidden too, and held items removed, since the normal first-person hand/item stays on screen
//   mantling:      arms shown; the first-person hand/item is hidden instead (FirstPersonEffects#onRenderHand)
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class FirstPersonBody {
    private FirstPersonBody() {}

    // Whether the local player's model should be drawn although the camera is in first person.
    public static boolean shouldRender() {
        return activeData() != null;
    }

    // Whether the first-person hand/item should be hidden because the body's own arms are shown (mantling).
    static boolean showsArms() {
        MovementData data = activeData();
        return data != null && data.mantleAnimation(1.0F) > 0.0F;
    }

    private static @Nullable MovementData activeData() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getCameraEntity() != player || !minecraft.options.getCameraType().isFirstPerson()
                || !ClientConfig.SPEC.isLoaded() || !ClientConfig.FIRST_PERSON_EFFECTS.getAsBoolean() || !ClientConfig.SHOW_BODY.getAsBoolean()) {
            return null;
        }
        MovementData data = player.getData(MovementAttachments.MOVEMENT);
        // Blends at the end of the tick (partial tick 1): the body appears as soon as a slide/mantle starts.
        return data.slideAnimation(1.0F) > 0.0F || data.mantleAnimation(1.0F) > 0.0F ? data : null;
    }

    @SubscribeEvent
    static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                if (avatar != Minecraft.getInstance().player || !shouldRender()) {
                    return;
                }
                state.overrideModelPartVisibility(PartNames.HEAD, false);
                state.overrideModelPartVisibility(PartNames.BODY, false);
                if (!showsArms()) {
                    state.overrideModelPartVisibility(PartNames.RIGHT_ARM, false);
                    state.overrideModelPartVisibility(PartNames.LEFT_ARM, false);
                    state.rightHandItemState.clear();
                    state.leftHandItemState.clear();
                }
            }
        });
    }
}
