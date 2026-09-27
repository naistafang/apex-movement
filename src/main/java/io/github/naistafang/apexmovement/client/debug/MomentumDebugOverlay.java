package io.github.naistafang.apexmovement.client.debug;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.config.ClientConfig;
import io.github.naistafang.apexmovement.config.MovementConfig;
import io.github.naistafang.apexmovement.movement.MomentumTravel;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import io.github.naistafang.apexmovement.movement.state.MovementState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.List;
import java.util.Locale;

// Client-only debug HUD for tuning: movement state, horizontal speed and velocity of the local player.
// Registered as a GUI layer, NeoForge's way of adding something to the in-game HUD.
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class MomentumDebugOverlay {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(ApexMovement.MODID, "momentum_debug");
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = 0x90505050;
    private static final int PADDING = 2;

    private MomentumDebugOverlay() {}

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, MomentumDebugOverlay::render);
    }

    // Slide timer (seconds of friction timer), friction phase, ground slope, and boost cooldown remaining.
    // While mantling: the mantle phase and how far the ledge is above the feet.
    private static String slideLine(LocalPlayer player, MovementData data) {
        if (data.state() == MovementState.MANTLING && data.isMantling()) {
            String ledge = Double.isInfinite(data.ledgeTop()) ? "none"
                    : String.format(Locale.ROOT, "%+.2f m", data.ledgeTop() - player.getY());
            return String.format(Locale.ROOT, "Mantle: %s  ledge %s%s", data.mantlePhase(), ledge,
                    data.superglideWindowOpen() ? "  SUPERGLIDE WINDOW" : "");
        }
        double sinceStart = (player.tickCount - data.lastSlideStartTick()) / 20.0;
        double cooldown = MovementConfig.SPEC.isLoaded() ? MovementConfig.SLIDE_BOOST_COOLDOWN.getAsDouble() : 0.0;
        String boost = sinceStart >= cooldown ? "ready" : String.format(Locale.ROOT, "%.1fs", cooldown - sinceStart);
        if (data.state() != MovementState.SLIDING) {
            return "Slide boost: " + boost;
        }
        double delay = MovementConfig.SPEC.isLoaded() ? MovementConfig.SLIDE_FRICTION_DELAY.getAsDouble() : 0.0;
        double timer = data.slideTicks() / 20.0;
        String phase = data.isBoosting() ? "BOOST" : timer > delay ? "LATE" : "early";
        String jump = MomentumTravel.slideJumpReady(data) ? "ready" : "wait";
        return String.format(Locale.ROOT, "Slide: %.2fs %s  slope %+.0f°  jump %s  boost %s",
                timer, phase, data.slopeDegrees(), jump, boost);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden() || !ClientConfig.SPEC.isLoaded() || !ClientConfig.SHOW_DEBUG_HUD.getAsBoolean()) {
            return;
        }

        MovementData data = player.getData(MovementAttachments.MOVEMENT);
        Vec3 velocity = player.getDeltaMovement();
        // Actual distance moved during the last tick. This is the speed the player sees, while velocity
        // is what is left after friction, carried into the next tick.
        double speed = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);

        String stateLine = data.vanillaReason() == null
                ? "State: " + data.state() + " (" + data.ticksInState() + " ticks)"
                : "State: VANILLA (" + data.vanillaReason() + ")";
        List<String> lines = List.of(
                stateLine,
                String.format(Locale.ROOT, "Speed: %.3f b/t  %.2f m/s", speed, speed * 20.0),
                String.format(Locale.ROOT, "Velocity: %.3f / %.3f / %.3f", velocity.x, velocity.y, velocity.z),
                "On ground: " + player.onGround() + "  Sprinting: " + player.isSprinting()
                        + (data.stunTicksLeft() > 0 ? String.format(Locale.ROOT, "  STUN %.2fs", data.stunTicksLeft() / 20.0) : ""),
                slideLine(player, data));

        Font font = minecraft.font;
        int width = lines.stream().mapToInt(font::width).max().orElse(0);
        int lineHeight = font.lineHeight + 1;
        int x = 4;
        int y = graphics.guiHeight() / 2 - lines.size() * lineHeight / 2;
        graphics.fill(x - PADDING, y - PADDING, x + width + PADDING, y + lines.size() * lineHeight + PADDING - 1, BACKGROUND_COLOR);
        for (String line : lines) {
            graphics.text(font, line, x, y, TEXT_COLOR, false);
            y += lineHeight;
        }
    }
}
