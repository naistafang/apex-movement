package io.github.naistafang.apexmovement.movement.state;

// The player's movement state. Each state has its own friction and acceleration rules.
public enum MovementState {
    // On the ground, normal friction.
    GROUNDED,
    // Sliding along the ground with low friction.
    SLIDING,
    // In the air, no horizontal friction.
    AIRBORNE,
    // Short window right after landing where ground friction is skipped. Not entered yet (step 3).
    LANDING,
    // Climbing a wall and/or mantling onto a ledge; the mantle controls movement (see MantlePhase).
    MANTLING
}
