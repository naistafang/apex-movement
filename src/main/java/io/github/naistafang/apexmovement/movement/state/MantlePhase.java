package io.github.naistafang.apexmovement.movement.state;

// Sub-steps of the MANTLING state.
public enum MantlePhase {
    // Not mantling.
    NONE,
    // Climbing up the wall because the ledge is still out of reach.
    CLIMB,
    // Pulling the body up until the feet are level with the ledge (Apex's "grab").
    RISE,
    // Moving forward onto the ledge (Apex's "pull").
    PULL
}
