package io.github.naistafang.apexmovement.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.config.MovementConfig;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

// Client only: catches jump presses that matter during a mantle: in the end-of-mantle window (superglide) and while
// climbing a wall (wall-bounce). Presses are caught as they arrive (a quick tap can start and end between two game
// ticks), stored on the player's data, and Mantling acts on them on the next tick. Uses the player's own jump binding
// (keyboard key or mouse button).
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class MantleJumpInput {
    private MantleJumpInput() {}

    @SubscribeEvent
    static void onKey(InputEvent.Key event) {
        if (event.getAction() == InputConstants.PRESS) {
            Options options = Minecraft.getInstance().options;
            if (options.keyJump.matches(event.getKeyEvent())) {
                onJumpPressed();
            }
        }
    }

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == InputConstants.PRESS) {
            Options options = Minecraft.getInstance().options;
            MouseButtonEvent button = new MouseButtonEvent(0.0, 0.0, event.getMouseButtonInfo());
            if (options.keyJump.matchesMouse(button)) {
                onJumpPressed();
            }
        }
    }

    private static void onJumpPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.screen() != null || !MovementConfig.SPEC.isLoaded()) {
            return;
        }
        // Ignored unless a superglide window is open or the player is climbing (see MovementData#onJumpPressed).
        player.getData(MovementAttachments.MOVEMENT).onJumpPressed();
    }
}
