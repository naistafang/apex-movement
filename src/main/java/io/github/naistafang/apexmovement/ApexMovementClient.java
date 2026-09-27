package io.github.naistafang.apexmovement;

import io.github.naistafang.apexmovement.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Client entrypoint: only constructed on the physical client, so client-only classes
// (rendering, input, Minecraft.getInstance()) are safe to reference from here.
@Mod(value = ApexMovement.MODID, dist = Dist.CLIENT)
public class ApexMovementClient {
    public ApexMovementClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Adds a "Config" button for this mod in the Mods screen, generated from our config specs.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
