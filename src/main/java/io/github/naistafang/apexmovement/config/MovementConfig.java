package io.github.naistafang.apexmovement.config;

import net.neoforged.neoforge.common.ModConfigSpec;

// Tunable movement values. Registered as a SERVER config: stored in config/apexmovement-server.toml
// on the server and synced to connecting clients, so both sides simulate with the same numbers.
// Read values at use time (e.g. AIR_ACCELERATION.getAsDouble()); don't cache them in static fields,
// since they change when the config is reloaded or a client joins a different server.
//
// Units: speeds are blocks per tick (x20 for blocks per second). "Friction" follows vanilla's
// convention: the fraction of horizontal speed KEPT each tick, so 1.0 means no friction at all.
// Defaults match Apex (1 block = 1 m, 1 Apex unit = 1 inch) where Apex's values are published; otherwise vanilla.
public final class MovementConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Master switch for all Apex movement mechanics. When false, movement is fully vanilla.")
            .translation("apexmovement.configuration.enabled")
            .define("enabled", true);

    static {
        BUILDER.comment("Movement while standing or walking on the ground").translation("apexmovement.configuration.ground").push("ground");
    }

    public static final ModConfigSpec.DoubleValue GROUND_FRICTION = BUILDER
            .comment("Fraction of horizontal speed kept per tick on the ground, before the block's own slipperiness is",
                    "multiplied in (normal blocks 0.6, ice 0.98). Vanilla: 0.91, giving 0.546 on normal blocks.")
            .translation("apexmovement.configuration.ground.friction")
            .defineInRange("friction", 0.91, 0.0, 1.0);

    // Top speeds in m/s (1 block = 1 m). Defaults are Apex's holstered speeds: 199.5 / 299 / 92 game units/s
    // at 1 unit = 1 inch. Acceleration is derived from these and the friction above, and still scales with the
    // movement speed attribute (Speed/Slowness effects), the Sneaking Speed attribute (Swift Sneak), and ice.
    public static final ModConfigSpec.DoubleValue WALK_SPEED = BUILDER
            .comment("Top walking speed on a normal block, in m/s (= blocks/s). Apex: 5.07. Vanilla: 4.317")
            .translation("apexmovement.configuration.ground.walkSpeed")
            .defineInRange("walkSpeed", 5.07, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue SPRINT_SPEED = BUILDER
            .comment("Top sprinting speed on a normal block, in m/s. Apex: 7.59. Vanilla: 5.612")
            .translation("apexmovement.configuration.ground.sprintSpeed")
            .defineInRange("sprintSpeed", 7.59, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue CROUCH_SPEED = BUILDER
            .comment("Top crouch-walking speed on a normal block, in m/s. Apex: 2.34. Vanilla: 1.295")
            .translation("apexmovement.configuration.ground.crouchSpeed")
            .defineInRange("crouchSpeed", 2.34, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue STEP_HEIGHT = BUILDER
            .comment("Walking, sprinting and sliding step up ledges up to this height (blocks) without jumping, so a",
                    "1-block rise counts as a steep slope. A slide also follows steps this high down instead of flying off.",
                    "Vanilla: 0.6")
            .translation("apexmovement.configuration.ground.stepHeight")
            .defineInRange("stepHeight", 1.0, 0.0, 2.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Movement while in the air. There is no air friction: horizontal speed is always kept, like Apex.").translation("apexmovement.configuration.air").push("air");
    }

    public static final ModConfigSpec.DoubleValue AIR_ACCELERATE = BUILDER
            .comment("Source-engine style air acceleration factor: speed added per second is up to this times the",
                    "wanted top speed, limited by maxAirSpeed. Apex: 500 (reaches the cap within one tick)")
            .translation("apexmovement.configuration.air.accelerate")
            .defineInRange("accelerate", 500.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue MAX_AIR_SPEED = BUILDER
            .comment("Air control cap in m/s: input can only push velocity along the input direction up to this speed.",
                    "Faster movement is kept but can't be increased by holding a direction. Apex: 1.524 (60 units)")
            .translation("apexmovement.configuration.air.maxAirSpeed")
            .defineInRange("maxAirSpeed", 1.524, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue AIR_STEER_MAX_ANGLE = BUILDER
            .comment("Air strafing: when the input direction is within this many degrees of the momentum direction,",
                    "the momentum turns toward it without losing speed. Beyond it, the momentum stops following",
                    "(it still never loses speed unless the input has a backward component).")
            .translation("apexmovement.configuration.air.steerMaxAngle")
            .defineInRange("steerMaxAngle", 90.0, 0.0, 180.0);

    public static final ModConfigSpec.DoubleValue AIR_STEER_RATE = BUILDER
            .comment("Air strafing: the fastest the momentum can turn, in degrees per second.",
                    "Turning the view faster than this makes the momentum lag behind (up to steerMaxAngle).")
            .translation("apexmovement.configuration.air.steerRate")
            .defineInRange("steerRate", 720.0, 0.0, 3600.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Jumping and gravity. Defaults reproduce Apex's jump: 1.42 m high (56 units), 0.74 s airtime,",
                "calibrated for Minecraft's 20 ticks per second").translation("apexmovement.configuration.jump").push("jump");
    }

    public static final ModConfigSpec.DoubleValue JUMP_VELOCITY = BUILDER
            .comment("Upward speed at the start of a jump, in m/s. Jump Boost scales it proportionally.",
                    "Apex: 7.17 (published ~7.24). Vanilla: 8.4")
            .translation("apexmovement.configuration.jump.velocity")
            .defineInRange("velocity", 7.17, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue JUMP_FATIGUE_HEIGHT = BUILDER
            .comment("Jump fatigue: right after landing, jumps only reach this height (m); a full jump is about 1.42.",
                    "Set it to 1.42 or more to turn fatigue off. Apex: 0.41 (16.3 units); default 1.15, a milder version")
            .translation("apexmovement.configuration.jump.fatigueHeight")
            .defineInRange("fatigueHeight", 1.15, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue JUMP_FATIGUE_TIME = BUILDER
            .comment("Jump fatigue: seconds after landing during which jumps are limited to fatigueHeight. Apex: 0.15")
            .translation("apexmovement.configuration.jump.fatigueTime")
            .defineInRange("fatigueTime", 0.15, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue JUMP_FATIGUE_RECOVERY = BUILDER
            .comment("Jump fatigue: seconds after landing by which jumps are back to full height (linear recovery). Apex: 0.75")
            .translation("apexmovement.configuration.jump.fatigueRecovery")
            .defineInRange("fatigueRecovery", 0.75, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue COYOTE_TIME = BUILDER
            .comment("Coyote time: seconds after walking or sliding off an edge (without jumping) during which a jump",
                    "still works. Apex: 0.2")
            .translation("apexmovement.configuration.jump.coyoteTime")
            .defineInRange("coyoteTime", 0.2, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue SPRINT_JUMP_BOOST = BUILDER
            .comment("Extra forward speed added when jumping while sprinting, in m/s. Apex: 0.0. Vanilla: 4.0")
            .translation("apexmovement.configuration.jump.sprintJumpBoost")
            .defineInRange("sprintJumpBoost", 0.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue GRAVITY = BUILDER
            .comment("Downward acceleration in m/s^2. Slow Falling and the gravity attribute scale it proportionally.",
                    "Apex: 20.78 (no vertical air drag). Vanilla: 32 plus 2% vertical drag per tick")
            .translation("apexmovement.configuration.jump.gravity")
            .defineInRange("gravity", 20.78, 0.0, 200.0);

    public static final ModConfigSpec.DoubleValue MAX_FALL_SPEED = BUILDER
            .comment("Terminal falling speed in m/s. Vanilla's terminal speed: 78.4")
            .translation("apexmovement.configuration.jump.maxFallSpeed")
            .defineInRange("maxFallSpeed", 78.4, 1.0, 200.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Fall stun: landing from a big fall costs speed and slows you briefly. Apex scales it from 7.6 m",
                "(300 units) to 20.3 m (800 units) of fall, non-linearly").translation("apexmovement.configuration.fallStun").push("fallStun");
    }

    public static final ModConfigSpec.DoubleValue FALL_STUN_START_HEIGHT = BUILDER
            .comment("Falls shorter than this (m, from the highest point in the air to the landing) cause no stun. Apex: 7.6; default 10")
            .translation("apexmovement.configuration.fallStun.startHeight")
            .defineInRange("startHeight", 10.0, 0.0, 256.0);

    public static final ModConfigSpec.DoubleValue FALL_STUN_FULL_HEIGHT = BUILDER
            .comment("Falls this high (m) or higher cause the full stun. In between, the stun grows quadratically. Apex: 20.3")
            .translation("apexmovement.configuration.fallStun.fullHeight")
            .defineInRange("fullHeight", 20.3, 0.0, 256.0);

    public static final ModConfigSpec.DoubleValue FALL_STUN_MAX_DURATION = BUILDER
            .comment("Seconds of slowdown at full stun. Apex: 1.0")
            .translation("apexmovement.configuration.fallStun.maxDuration")
            .defineInRange("maxDuration", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue FALL_STUN_MAX_SPEED_LOSS = BUILDER
            .comment("Fraction of horizontal speed lost on landing at full stun (1.0 = all of it). Apex: full reduction")
            .translation("apexmovement.configuration.fallStun.maxSpeedLoss")
            .defineInRange("maxSpeedLoss", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue FALL_STUN_SPEED_FACTOR = BUILDER
            .comment("While stunned, top ground speed is this fraction of normal. Not published for Apex; tuned by feel")
            .translation("apexmovement.configuration.fallStun.speedFactor")
            .defineInRange("speedFactor", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue FALL_STUN_SLIDE_FACTOR = BUILDER
            .comment("Landing into a slide (crouch held) takes only this fraction of the stun (speed loss and duration),",
                    "so slides survive most big drops. 1.0 = no protection, 0.0 = slide landings are never stunned")
            .translation("apexmovement.configuration.fallStun.slideLandingFactor")
            .defineInRange("slideLandingFactor", 0.5, 0.0, 1.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Sliding: hold crouch while moving fast on the ground. Values marked Apex are converted",
                "from Apex game units at 1 unit = 1 inch (1 block = 1 m).").translation("apexmovement.configuration.slide").push("slide");
    }

    public static final ModConfigSpec.DoubleValue SLIDE_START_SPEED = BUILDER
            .comment("Minimum horizontal speed (m/s) to start a slide. Apex: 5.08 (200 units/s), just above walking speed")
            .translation("apexmovement.configuration.slide.startSpeed")
            .defineInRange("startSpeed", 5.08, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue AIR_SLIDE_SPEED = BUILDER
            .comment("Jumping into a slide: landing with crouch held starts a slide when horizontal speed is above this (m/s)",
                    "and the landing fall speed is above airSlideFallSpeed, even below startSpeed. Apex: 2.29 (90 units/s)")
            .translation("apexmovement.configuration.slide.airSlideSpeed")
            .defineInRange("airSlideSpeed", 2.29, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue AIR_SLIDE_FALL_SPEED = BUILDER
            .comment("Jumping into a slide: minimum downward speed (m/s) at landing. A normal jump lands at about 7.2. Apex: 5.08 (200 units/s)")
            .translation("apexmovement.configuration.slide.airSlideFallSpeed")
            .defineInRange("airSlideFallSpeed", 5.08, 0.0, 80.0);

    public static final ModConfigSpec.DoubleValue SLIDE_BOOST = BUILDER
            .comment("Speed added (m/s) when a slide starts, if the boost is off cooldown. Apex: 3.81 (150 units/s)")
            .translation("apexmovement.configuration.slide.boost")
            .defineInRange("boost", 3.81, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue SLIDE_BOOST_MAX_SPEED = BUILDER
            .comment("The boost can't take speed above this (m/s); faster players keep their speed. Apex: 10.16 (400 units/s)")
            .translation("apexmovement.configuration.slide.boostMaxSpeed")
            .defineInRange("boostMaxSpeed", 10.16, 0.0, 40.0);

    // Apex's slide-jump timing also sets how long the boost takes to build up: the boost is added as a burst of
    // acceleration lasting exactly until a slide jump would become possible in Apex. Jumping during it is "too early".
    public static final ModConfigSpec.DoubleValue SLIDE_JUMP_TIME = BUILDER
            .comment("Slide jump timing: a slide jump keeps the boost once this many seconds have passed since the slide",
                    "started, or once an instantly applied boost would have decayed below jumpSpeed, whichever is first.",
                    "The boost builds up over exactly that time; jumping earlier removes the boost gained so far. Apex: 0.24")
            .translation("apexmovement.configuration.slide.jumpTime")
            .defineInRange("jumpTime", 0.24, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue SLIDE_JUMP_SPEED = BUILDER
            .comment("Slide jump timing: speed (m/s) below which a slide jump keeps the boost. From sprint speed the",
                    "boost is reached in ~0.25 s; from just above startSpeed almost instantly. Apex: 8.89 (350 units/s)")
            .translation("apexmovement.configuration.slide.jumpSpeed")
            .defineInRange("jumpSpeed", 8.89, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue SLIDE_BOOST_COOLDOWN = BUILDER
            .comment("Seconds after entering any slide before the next slide gets a boost. Apex: 2.0")
            .translation("apexmovement.configuration.slide.boostCooldown")
            .defineInRange("boostCooldown", 2.0, 0.0, 60.0);

    public static final ModConfigSpec.DoubleValue SLIDE_FRICTION = BUILDER
            .comment("Fraction of speed kept per tick early in the slide. Apex: 0.9687 (400 -> 350 units/s in 0.21 s)")
            .translation("apexmovement.configuration.slide.friction")
            .defineInRange("friction", 0.9687, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SLIDE_FRICTION_DELAY = BUILDER
            .comment("Seconds of sliding (on flat ground or uphill) before the stronger late friction kicks in.",
                    "The timer pauses while sliding downhill. Tuned with lateFriction so a flat slide from sprint lasts about",
                    "1 s and ~7.5 m, estimated from Apex's published boost, decay and ~1 s duration")
            .translation("apexmovement.configuration.slide.frictionDelay")
            .defineInRange("frictionDelay", 0.75, 0.0, 60.0);

    public static final ModConfigSpec.DoubleValue SLIDE_LATE_FRICTION = BUILDER
            .comment("Fraction of speed kept per tick once the late friction has kicked in. Not published for Apex;",
                    "tuned with frictionDelay to match Apex's ~1 s slide")
            .translation("apexmovement.configuration.slide.lateFriction")
            .defineInRange("lateFriction", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SLIDE_END_SPEED = BUILDER
            .comment("The slide ends (leaving you crouching) when speed drops below this (m/s).",
                    "Not published for Apex; defaults to Apex crouch-walk speed")
            .translation("apexmovement.configuration.slide.endSpeed")
            .defineInRange("endSpeed", 2.34, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue SLIDE_SLOPE_STRENGTH = BUILDER
            .comment("Multiplier on how strongly gravity pulls a slide along slopes (speeds up downhill, slows uphill).",
                    "1.0 = physically based on the jump gravity")
            .translation("apexmovement.configuration.slide.slopeStrength")
            .defineInRange("slopeStrength", 1.0, 0.0, 10.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Superglide: press jump at the very end of a mantle to launch forward")
                .translation("apexmovement.configuration.superglide").push("superglide");
    }

    public static final ModConfigSpec.DoubleValue SUPERGLIDE_WINDOW = BUILDER
            .comment("A jump pressed within the last this many seconds of a mantle superglides. If the window reaches back",
                    "before the pull-over (riseTime + pullTime is 0.9 s), an early press is held until the feet reach the",
                    "ledge. Apex: 0.15; default 0.6 (starts earlier, same end) to be much more forgiving")
            .translation("apexmovement.configuration.superglide.window")
            .defineInRange("window", 0.6, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue SUPERGLIDE_SPEED = BUILDER
            .comment("Horizontal launch speed (m/s); the upward speed is a normal jump's. Apex: 11.66 (about 540 units/s",
                    "combined minus the jump's 285 units/s upward; roughly sprint + slide boost)")
            .translation("apexmovement.configuration.superglide.speed")
            .defineInRange("speed", 11.66, 0.0, 40.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Mantling and wall climbing: jump at a wall while holding forward and facing it. Ledges within",
                "reach are mantled directly; higher ones are climbed up to first. Apex values converted at 1 unit = 1 inch")
                .translation("apexmovement.configuration.mantle").push("mantle");
    }

    public static final ModConfigSpec.DoubleValue MANTLE_MAX_LEDGE_HEIGHT = BUILDER
            .comment("Highest ledge (m above the feet when the climb starts) that can be climbed and mantled.",
                    "Apex: about 6 m of wall climb (147 units of climb plus reach); default 5.8")
            .translation("apexmovement.configuration.mantle.maxLedgeHeight")
            .defineInRange("maxLedgeHeight", 5.8, 0.0, 32.0);

    public static final ModConfigSpec.DoubleValue MANTLE_REACH = BUILDER
            .comment("Ledges up to this height (m above the feet) are mantled right away without climbing. Apex: 2.46 (97 units)")
            .translation("apexmovement.configuration.mantle.reach")
            .defineInRange("reach", 2.46, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue CLIMB_SPEED = BUILDER
            .comment("Upward wall climb speed in m/s. Apex: 2.92 (115 units/s)")
            .translation("apexmovement.configuration.mantle.climbSpeed")
            .defineInRange("climbSpeed", 2.92, 0.1, 40.0);

    public static final ModConfigSpec.DoubleValue MANTLE_RISE_TIME = BUILDER
            .comment("Seconds to pull up until the feet are level with the ledge. Apex: 0.45 (mantle grab)")
            .translation("apexmovement.configuration.mantle.riseTime")
            .defineInRange("riseTime", 0.45, 0.05, 5.0);

    public static final ModConfigSpec.DoubleValue MANTLE_PULL_TIME = BUILDER
            .comment("Seconds to move forward onto the ledge. Apex: 0.45 (mantle pull)")
            .translation("apexmovement.configuration.mantle.pullTime")
            .defineInRange("pullTime", 0.45, 0.05, 5.0);

    public static final ModConfigSpec.DoubleValue MANTLE_MAX_LOOK_ANGLE = BUILDER
            .comment("Most the view can be turned away from facing the wall (degrees) to start or keep climbing. Apex: 45.57")
            .translation("apexmovement.configuration.mantle.maxLookAngle")
            .defineInRange("maxLookAngle", 45.57, 0.0, 90.0);

    public static final ModConfigSpec.DoubleValue MANTLE_MAX_INPUT_ANGLE = BUILDER
            .comment("Most the movement input can point away from the wall (degrees) to start or keep climbing. Apex: 50")
            .translation("apexmovement.configuration.mantle.maxInputAngle")
            .defineInRange("maxInputAngle", 50.0, 0.0, 90.0);

    public static final ModConfigSpec.DoubleValue CLIMB_SIDE_SPEED = BUILDER
            .comment("Sideways speed along the wall (m/s) while climbing with left/right input held. Apex: 6.55 (258 units/s)")
            .translation("apexmovement.configuration.mantle.climbSideSpeed")
            .defineInRange("climbSideSpeed", 6.55, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue WALL_JUMP_SPEED = BUILDER
            .comment("Wall-bounce: pressing jump while climbing pushes off the wall at this horizontal speed (m/s).",
                    "Apex: 6.55 (258 units/s)")
            .translation("apexmovement.configuration.mantle.wallJumpSpeed")
            .defineInRange("wallJumpSpeed", 6.55, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue WALL_JUMP_HEIGHT = BUILDER
            .comment("Height gained by a wall-bounce (m), on top of the climb's upward speed. Apex: 0.72 (28.21 units)")
            .translation("apexmovement.configuration.mantle.wallJumpHeight")
            .defineInRange("wallJumpHeight", 0.72, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue MANTLE_EXIT_SPEED = BUILDER
            .comment("Forward speed (m/s) kept when a mantle ends normally (not superglided), so you don't stop dead on",
                    "the ledge. Apex doesn't publish a value; default 5.07 (walk speed). 0 = stop")
            .translation("apexmovement.configuration.mantle.exitSpeed")
            .defineInRange("exitSpeed", 5.07, 0.0, 40.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Hard limits").translation("apexmovement.configuration.limits").push("limits");
    }

    public static final ModConfigSpec.DoubleValue MAX_HORIZONTAL_SPEED = BUILDER
            .comment("Horizontal speed (blocks/tick) that player acceleration can never push past.",
                    "Speed from outside sources (knockback, explosions) is not cut, only kept from growing.",
                    "Vanilla sprinting is about 0.28; the default is high enough not to affect vanilla movement.")
            .translation("apexmovement.configuration.limits.maxHorizontalSpeed")
            .defineInRange("maxHorizontalSpeed", 2.0, 0.1, 10.0);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MovementConfig() {}
}
