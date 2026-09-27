package io.github.naistafang.apexmovement;

import com.mojang.logging.LogUtils;
import io.github.naistafang.apexmovement.config.MovementConfig;
import io.github.naistafang.apexmovement.example.ExampleContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

// Common entrypoint: loaded on both the client and the dedicated server.
// Client-only setup lives in ApexMovementClient so the server never touches client classes.
// The value here must match the modId in META-INF/neoforge.mods.toml.
@Mod(ApexMovement.MODID)
public class ApexMovement {
    public static final String MODID = "apexmovement";
    public static final Logger LOGGER = LogUtils.getLogger();

    // FML passes in the mod event bus (registration and lifecycle events) and our ModContainer.
    public ApexMovement(IEventBus modEventBus, ModContainer modContainer) {
        ExampleContent.register(modEventBus);

        // SERVER configs are loaded by the server and synced to clients on join, so the server
        // stays the authority on movement tuning values.
        modContainer.registerConfig(ModConfig.Type.SERVER, MovementConfig.SPEC);
    }
}
