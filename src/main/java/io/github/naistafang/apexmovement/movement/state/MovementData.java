package io.github.naistafang.apexmovement.movement.state;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

// Per-player movement data, stored on the player as a NeoForge data attachment (see MovementAttachments).
// Each side keeps its own copy. The client decides the state of the local player it simulates and sends
// changes to the server (MovementStatePayload), which stores them here via setSyncedState().
public final class MovementData {
    private MovementState state = MovementState.GROUNDED;
    private int ticksInState;
    private boolean stateChanged;
    // Why vanilla movement ran instead of ours on the last tick, or null if ours ran. Shown in the debug HUD.
    private @Nullable String vanillaReason;

    // Slide timing: ticks spent in the current slide (paused while sliding downhill), and the player's tickCount
    // when a slide was last entered (for the boost cooldown).
    private int slideTicks;
    private int lastSlideStartTick = Integer.MIN_VALUE / 2;
    // Ground slope along the direction of movement, in degrees (positive = uphill). For the debug HUD.
    private double slopeDegrees;
    // Slide boost being applied over several ticks: speed added per tick (blocks/tick) and ticks remaining.
    private double boostPerTick;
    private int boostTicksLeft;
    // Vertical speed (blocks/tick, negative = falling) on the last airborne tick, i.e. the landing impact speed.
    private double lastAirVerticalSpeed;
    // Boost speed (blocks/tick) added so far in the current slide; removed again by a too-early slide jump.
    private double boostApplied;

    // Mantling (client): current phase, ticks left in a timed phase, the ledge being climbed onto, where the feet
    // were when the climb started, and whether a climb was already used since last touching the ground.
    private MantlePhase mantlePhase = MantlePhase.NONE;
    private Direction wallFace = Direction.NORTH;
    private int mantleTicksLeft;
    private double ledgeTop;
    private double mantleTargetX;
    private double mantleTargetZ;
    private double climbStartY;
    private boolean climbUsed;
    // Superglide: whether the end-of-mantle window is open, and whether the client input asked for a superglide.
    private boolean superglideWindowOpen;
    private boolean superglideRequested;
    // In the air after a superglide (shown with the slide animation, like Apex's mid-air slide). Synced to others.
    private boolean superglideFlight;
    // Fall stun: highest Y reached in the current time in the air, and slowdown ticks left after a hard landing.
    private double airPeakY;
    private int stunTicksLeft;
    // The player's tickCount when they last touched down from the air (for jump fatigue).
    private int lastLandingTick = Integer.MIN_VALUE / 2;
    // Coyote time: ticks left in which a jump still works after leaving the ground without jumping.
    private int coyoteTicks;
    // Whether LowGapSlide forced the crawling pose (so it only clears a forced pose it set itself).
    private boolean lowSlideForced;
    // Wall-bounce: the client input asked for a jump off the wall during a climb.
    private boolean wallJumpRequested;

    // Client only: camera offset (blocks) that eases the view over step-ups (see StepSmoothing), this tick and last.
    private double stepOffset;
    private double stepOffsetO;
    // Client only: what the sound player last saw for this player, to play sounds on changes (see MovementSounds).
    private MovementState soundState = MovementState.GROUNDED;
    private MantlePhase soundPhase = MantlePhase.NONE;
    private boolean soundGlide;
    private int soundTicks;
    private boolean wallJumpSoundPending;

    // Client only: animation blends (0 = normal pose, 1 = full pose), this tick and last tick. For the mantle, the
    // pose also blends from the previous phase's pose to the current one when the phase changes.
    private float slideAnimation;
    private float slideAnimationO;
    private float mantleAnimation;
    private float mantleAnimationO;
    private MantlePhase animPhase = MantlePhase.CLIMB;
    private MantlePhase animPhaseFrom = MantlePhase.CLIMB;
    private float phaseBlend = 1.0F;
    private float phaseBlendO = 1.0F;

    public MovementState state() {
        return this.state;
    }

    public int ticksInState() {
        return this.ticksInState;
    }

    public @Nullable String vanillaReason() {
        return this.vanillaReason;
    }

    // Client: called once per movement tick in which our system runs, with the state for this tick.
    public void tick(MovementState next) {
        this.vanillaReason = null;
        if (next != this.state) {
            this.state = next;
            this.ticksInState = 0;
            this.stateChanged = true;
        } else {
            this.ticksInState++;
        }
    }

    // Server: our system runs this tick, but the state itself comes from the client.
    public void clearVanillaReason() {
        this.vanillaReason = null;
    }

    // Called on ticks where vanilla movement runs instead. The state is kept so it resumes cleanly;
    // a superglide flight ends (e.g. landing in water).
    public void markVanilla(String reason) {
        this.vanillaReason = reason;
        this.setSuperglideFlight(false);
    }

    // Client: returns true once after each state, mantle phase or superglide flag change, so it can be sent to the server.
    public boolean consumeStateChanged() {
        boolean changed = this.stateChanged;
        this.stateChanged = false;
        return changed;
    }

    // Server, and clients for other players: applies a state, mantle phase and superglide flag reported by the
    // owning client.
    public void setSyncedState(MovementState synced, MantlePhase syncedPhase, boolean syncedSuperglide) {
        this.mantlePhase = syncedPhase;
        this.superglideFlight = syncedSuperglide;
        this.setSyncedState(synced);
    }

    private void setSyncedState(MovementState synced) {
        if (synced != this.state) {
            if (synced == MovementState.SLIDING) {
                this.slideTicks = 0;
            }
            this.state = synced;
            this.ticksInState = 0;
        }
    }

    public int slideTicks() {
        return this.slideTicks;
    }

    public void advanceSlide() {
        this.slideTicks++;
    }

    public int lastSlideStartTick() {
        return this.lastSlideStartTick;
    }

    public void startSlide(int tickCount) {
        this.slideTicks = 0;
        this.lastSlideStartTick = tickCount;
        this.boostTicksLeft = 0;
        this.boostApplied = 0.0;
    }

    public void startBoost(double perTick, int ticks) {
        this.boostPerTick = perTick;
        this.boostTicksLeft = ticks;
    }

    public boolean isBoosting() {
        return this.boostTicksLeft > 0;
    }

    // Returns this tick's boost (blocks/tick) and counts it down; 0 once the boost is used up.
    public double takeBoostTick() {
        if (this.boostTicksLeft <= 0) {
            return 0.0;
        }
        this.boostTicksLeft--;
        this.boostApplied += this.boostPerTick;
        return this.boostPerTick;
    }

    // Cancels the rest of the boost and returns how much was already added, so the caller can take it back.
    public double revokeBoost() {
        double applied = this.boostApplied;
        this.boostTicksLeft = 0;
        this.boostApplied = 0.0;
        return applied;
    }

    public double lastAirVerticalSpeed() {
        return this.lastAirVerticalSpeed;
    }

    public void setLastAirVerticalSpeed(double verticalSpeed) {
        this.lastAirVerticalSpeed = verticalSpeed;
    }

    public MantlePhase mantlePhase() {
        return this.mantlePhase;
    }

    public boolean isMantling() {
        return this.mantlePhase != MantlePhase.NONE;
    }

    public void startMantle(MantlePhase phase, Direction wallFace, double ledgeTop, double targetX, double targetZ, double climbStartY) {
        this.stateChanged |= this.mantlePhase != phase;
        this.mantlePhase = phase;
        this.wallFace = wallFace;
        this.ledgeTop = ledgeTop;
        this.mantleTargetX = targetX;
        this.mantleTargetZ = targetZ;
        this.climbStartY = climbStartY;
        this.climbUsed = true;
    }

    public void setMantlePhase(MantlePhase phase, int ticks) {
        this.stateChanged |= this.mantlePhase != phase;
        this.mantlePhase = phase;
        this.mantleTicksLeft = ticks;
        this.wallJumpRequested = false;
    }

    public void endMantle() {
        this.stateChanged |= this.mantlePhase != MantlePhase.NONE;
        this.mantlePhase = MantlePhase.NONE;
        this.setSuperglideWindowOpen(false);
        this.mantleTicksLeft = 0;
        this.wallJumpRequested = false;
    }

    // The ledge ahead changed (sideways climbing reached another block column). An infinite top means there is no
    // ledge to mantle onto in this column (yet): keep climbing.
    public void setLedge(double top, double targetX, double targetZ) {
        this.ledgeTop = top;
        this.mantleTargetX = targetX;
        this.mantleTargetZ = targetZ;
    }

    // Counts down the current timed phase; returns the ticks that were left before this tick (at least 1).
    public int takeMantleTick() {
        int left = Math.max(1, this.mantleTicksLeft);
        this.mantleTicksLeft = left - 1;
        return left;
    }

    // Direction from the player toward the wall being climbed.
    public Direction wallFace() {
        return this.wallFace;
    }

    public double ledgeTop() {
        return this.ledgeTop;
    }

    public double mantleTargetX() {
        return this.mantleTargetX;
    }

    public double mantleTargetZ() {
        return this.mantleTargetZ;
    }

    public double climbStartY() {
        return this.climbStartY;
    }

    public boolean climbUsed() {
        return this.climbUsed;
    }

    public boolean superglideFlight() {
        return this.superglideFlight;
    }

    public void setSuperglideFlight(boolean flight) {
        this.stateChanged |= flight != this.superglideFlight;
        this.superglideFlight = flight;
    }

    public boolean superglideWindowOpen() {
        return this.superglideWindowOpen;
    }

    // Opening or closing the window also drops any stale request.
    public void setSuperglideWindowOpen(boolean open) {
        if (open != this.superglideWindowOpen) {
            this.superglideWindowOpen = open;
            this.superglideRequested = false;
        }
    }

    // Client input: jump was pressed. In the superglide window it asks for a superglide; while climbing a wall,
    // for a wall-bounce. Ignored otherwise (normal jumps go through vanilla).
    public void onJumpPressed() {
        if (this.superglideWindowOpen) {
            this.superglideRequested = true;
        } else if (this.mantlePhase == MantlePhase.CLIMB) {
            this.wallJumpRequested = true;
        }
    }

    public boolean consumeWallJumpRequest() {
        boolean requested = this.wallJumpRequested;
        this.wallJumpRequested = false;
        return requested;
    }

    public boolean consumeSuperglideRequest() {
        boolean requested = this.superglideRequested;
        this.superglideRequested = false;
        return requested;
    }

    public double airPeakY() {
        return this.airPeakY;
    }

    public void setAirPeakY(double y) {
        this.airPeakY = y;
    }

    public int stunTicksLeft() {
        return this.stunTicksLeft;
    }

    public void setStunTicksLeft(int ticks) {
        this.stunTicksLeft = ticks;
    }

    public int lastLandingTick() {
        return this.lastLandingTick;
    }

    public void setLastLandingTick(int tickCount) {
        this.lastLandingTick = tickCount;
    }

    // Clears jump fatigue (as if the last landing was long ago).
    public void clearJumpFatigue() {
        this.lastLandingTick = Integer.MIN_VALUE / 2;
    }

    public int coyoteTicks() {
        return this.coyoteTicks;
    }

    public void setCoyoteTicks(int ticks) {
        this.coyoteTicks = ticks;
    }

    public boolean lowSlideForced() {
        return this.lowSlideForced;
    }

    public void setLowSlideForced(boolean forced) {
        this.lowSlideForced = forced;
    }

    // Client only: the player's feet were moved up (positive) or down by a step this tick; the camera starts that
    // far behind and catches up. Capped so fast stair runs can't leave the camera far behind.
    public void addStepOffset(double dy, double max) {
        this.stepOffset = Mth.clamp(this.stepOffset + dy, -max, max);
    }

    // Client only, once per client tick before the player moves: the offset shrinks by up to `perTick` blocks.
    public void tickStepOffset(double perTick) {
        this.stepOffsetO = this.stepOffset;
        this.stepOffset = Math.signum(this.stepOffset) * Math.max(0.0, Math.abs(this.stepOffset) - perTick);
    }

    public double stepOffset(float partialTick) {
        return Mth.lerp(partialTick, this.stepOffsetO, this.stepOffset);
    }

    public void clearStepOffset() {
        this.stepOffset = 0.0;
        this.stepOffsetO = 0.0;
    }

    public MovementState soundState() {
        return this.soundState;
    }

    public MantlePhase soundPhase() {
        return this.soundPhase;
    }

    public boolean soundGlide() {
        return this.soundGlide;
    }

    // Client only: remembers what the sound player saw this tick; returns ticks spent in an unchanged state.
    public int updateSoundState(MovementState state, MantlePhase phase, boolean glide) {
        if (state != this.soundState || phase != this.soundPhase) {
            this.soundTicks = 0;
        } else {
            this.soundTicks++;
        }
        this.soundState = state;
        this.soundPhase = phase;
        this.soundGlide = glide;
        return this.soundTicks;
    }

    public void markWallJumpSound() {
        this.wallJumpSoundPending = true;
    }

    public boolean consumeWallJumpSound() {
        boolean pending = this.wallJumpSoundPending;
        this.wallJumpSoundPending = false;
        return pending;
    }

    // Touching the ground allows the next climb.
    public void resetClimb() {
        this.climbUsed = false;
    }

    // Client only: eases the slide and mantle animations in/out once per client tick.
    public void tickAnimations(float blendInPerTick, float blendOutPerTick, float phaseBlendPerTick) {
        boolean active = this.vanillaReason == null;
        this.slideAnimationO = this.slideAnimation;
        boolean slidePose = this.state == MovementState.SLIDING || this.superglideFlight;
        this.slideAnimation = ease(this.slideAnimation, active && slidePose, blendInPerTick, blendOutPerTick);

        boolean mantling = active && this.state == MovementState.MANTLING && this.mantlePhase != MantlePhase.NONE;
        this.mantleAnimationO = this.mantleAnimation;
        this.mantleAnimation = ease(this.mantleAnimation, mantling, blendInPerTick, blendOutPerTick);

        this.phaseBlendO = this.phaseBlend;
        if (mantling && this.mantlePhase != this.animPhase) {
            // Start blending from wherever the pose currently is toward the new phase's pose.
            this.animPhaseFrom = this.phaseBlend >= 0.5F ? this.animPhase : this.animPhaseFrom;
            this.animPhase = this.mantlePhase;
            this.phaseBlend = 0.0F;
            this.phaseBlendO = 0.0F;
        }
        this.phaseBlend = Math.min(1.0F, this.phaseBlend + phaseBlendPerTick);
    }

    private static float ease(float value, boolean on, float inPerTick, float outPerTick) {
        return on ? Math.min(1.0F, value + inPerTick) : Math.max(0.0F, value - outPerTick);
    }

    // Client only: animation values smoothed between ticks for rendering.
    public float slideAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.slideAnimationO, this.slideAnimation);
    }

    public float mantleAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.mantleAnimationO, this.mantleAnimation);
    }

    public MantlePhase animPhase() {
        return this.animPhase;
    }

    public MantlePhase animPhaseFrom() {
        return this.animPhaseFrom;
    }

    public float phaseBlend(float partialTick) {
        return Mth.lerp(partialTick, this.phaseBlendO, this.phaseBlend);
    }

    public double slopeDegrees() {
        return this.slopeDegrees;
    }

    public void setSlopeDegrees(double slopeDegrees) {
        this.slopeDegrees = slopeDegrees;
    }
}
