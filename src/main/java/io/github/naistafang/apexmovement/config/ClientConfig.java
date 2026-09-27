package io.github.naistafang.apexmovement.config;

import net.neoforged.neoforge.common.ModConfigSpec;

// Client-only preferences (debug HUD, first-person camera/arm feedback). Registered as a CLIENT config:
// stored in config/apexmovement-client.toml on each player's machine and never synced.
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Debugging and tuning aids").translation("apexmovement.configuration.debug").push("debug");
    }

    public static final ModConfigSpec.BooleanValue SHOW_DEBUG_HUD = BUILDER
            .comment("Show the momentum debug HUD (movement state, speed, velocity). Toggle in-game with F7 by default.")
            .translation("apexmovement.configuration.debug.showDebugHud")
            .define("showDebugHud", false);

    static {
        BUILDER.pop();
        BUILDER.comment("First-person feedback: camera tilt/bob, field of view and arm animations while sliding and mantling")
                .translation("apexmovement.configuration.firstPerson").push("firstPerson");
    }

    public static final ModConfigSpec.BooleanValue FIRST_PERSON_EFFECTS = BUILDER
            .comment("Enable first-person camera and arm animations for sliding and mantling")
            .translation("apexmovement.configuration.firstPerson.enabled")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue SHOW_BODY = BUILDER
            .comment("Show your own legs (sliding) and full arms and legs (climbing/mantling) in first person")
            .translation("apexmovement.configuration.firstPerson.showBody")
            .define("showBody", true);

    public static final ModConfigSpec.DoubleValue CAMERA_EFFECT_STRENGTH = BUILDER
            .comment("Strength of camera tilt, bob and field-of-view changes (0 = off, 1 = default)")
            .translation("apexmovement.configuration.firstPerson.cameraStrength")
            .defineInRange("cameraStrength", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue SMOOTH_STEPS = BUILDER
            .comment("Ease the first-person camera over step-ups (and a slide's drops down steps) instead of popping")
            .translation("apexmovement.configuration.firstPerson.smoothSteps")
            .define("smoothSteps", true);

    public static final ModConfigSpec.DoubleValue STEP_SMOOTH_TIME = BUILDER
            .comment("Seconds the camera takes to catch up over a 1-block step (smaller steps take proportionally less)")
            .translation("apexmovement.configuration.firstPerson.stepSmoothTime")
            .defineInRange("stepSmoothTime", 0.15, 0.05, 1.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Movement sounds (slides, climbing, mantling, superglides, wall-bounces)")
                .translation("apexmovement.configuration.sounds").push("sounds");
    }

    public static final ModConfigSpec.BooleanValue SOUNDS = BUILDER
            .comment("Play movement sounds, for your own movement and other players'")
            .translation("apexmovement.configuration.sounds.enabled")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue SOUND_VOLUME = BUILDER
            .comment("Volume of the movement sounds (on top of the Players volume slider)")
            .translation("apexmovement.configuration.sounds.volume")
            .defineInRange("volume", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue SLIDE_PITCH = BUILDER
            .comment("Sliding plays the step sound of the block underneath, sped up by this factor (higher = faster and",
                    "higher-pitched, 1 = like walking)")
            .translation("apexmovement.configuration.sounds.slidePitch")
            .defineInRange("slidePitch", 1.5, 0.5, 2.0);

    public static final ModConfigSpec.DoubleValue CLIMB_PITCH = BUILDER
            .comment("Climbing plays the step sound of the wall block, slowed down by this factor (lower = slower and",
                    "deeper, 1 = like walking)")
            .translation("apexmovement.configuration.sounds.climbPitch")
            .defineInRange("climbPitch", 0.7, 0.5, 2.0);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}
}
