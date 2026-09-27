package io.github.naistafang.apexmovement.movement.sound;

import io.github.naistafang.apexmovement.ApexMovement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Sound events for movement feedback (sliding and climbing use the touched block's own step sound instead). A sound event is a name ("apexmovement:slide.start"); which audio files it plays
// is set in assets/apexmovement/sounds.json, so resource packs can replace them. They currently point at existing
// Minecraft sound files (none from Apex, see CLAUDE.md); original recordings can be swapped in there later.
// Registered on both sides (the registry is shared with the server), but only played on the client (MovementSoundPlayer).
public final class MovementSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ApexMovement.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MANTLE_GRAB = register("mantle.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANTLE_PULL = register("mantle.pull");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERGLIDE = register("superglide");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALL_JUMP = register("wall_jump");

    private MovementSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ApexMovement.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
