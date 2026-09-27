package io.github.naistafang.apexmovement.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.config.ClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

// Client-only key bindings. They appear under their own category in Options > Controls > Key Binds,
// where players can rebind them. Note: since 26.x, key codes are SDL scancodes (use InputConstants.KEY_*).
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class MovementKeyMappings {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(ApexMovement.MODID, "main"));

    public static final KeyMapping TOGGLE_DEBUG_HUD = new KeyMapping("key.apexmovement.toggle_debug_hud", InputConstants.KEY_F7, CATEGORY);

    private MovementKeyMappings() {}

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_DEBUG_HUD);
    }

    // consumeClick() returns true once per key press, so this toggles once per press.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_DEBUG_HUD.consumeClick()) {
            ClientConfig.SHOW_DEBUG_HUD.set(!ClientConfig.SHOW_DEBUG_HUD.getAsBoolean());
            ClientConfig.SHOW_DEBUG_HUD.save();
        }
    }
}
