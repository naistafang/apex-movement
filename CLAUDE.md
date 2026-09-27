# Apex Movement — project context

## What this is
A Minecraft mod that brings Apex Legends–inspired movement to Minecraft Java Edition.
It is the first of a planned set of related mods:

- **apex-core**: shared library (networking, config, keybinds, shared APIs). Not yet created.
- **apex-movement** (this repo): movement mechanics
- **apex-weapons**: weapons and grenades. Planned.
- **apex-legends**: characters and abilities. Planned; depends on core, movement, weapons.

For now everything lives in this single project. Write code so that generic pieces
(networking helpers, config, keybind handling) could later move into a separate
`apex-core` mod without major rewrites. Keep them in their own package, not mixed
into movement logic.

## Tech stack
- Minecraft 26.3 (Java Edition), NeoForge 26.3.0.23-beta
- Java 25, Gradle 9.1.0 (wrapper), foojay-resolver-convention 1.0.0
- Mixins enabled
- Mod ID: `apexmovement`
- Base package: `io.github.naistafang.apexmovement`
- Developed on Ubuntu 26.04 in IntelliJ IDEA with the Minecraft Development plugin

Minecraft 26.x ships unobfuscated with official Mojang names; no Parchment mappings are used.
NeoForge APIs change between versions, and many online tutorials target 1.20/1.21.
Check APIs against the current NeoForge sources and docs instead of assuming older patterns still apply.

## Movement goals
Final feature set: **sliding, bunny hopping, tap strafing, supergliding, mantling**.
All of them depend on a reworked momentum system, which is built first.

### Build order (one step at a time; confirm with me before starting the next)
1. **Momentum core + debug HUD**: state machine, state-based friction/acceleration, no new mechanics yet.
   With default config it should feel close to vanilla, but all ground/air horizontal movement runs through the new system.
2. **Sliding**: crouch while sprinting; low friction, initial speed boost, momentum carry.
3. **Bunny hopping**: landing friction-skip window; slide-hopping (jump out of a slide keeping speed).
4. **Air strafing, then tap strafing**
5. **Mantling**: automatic ledge climb onto 1–2 block ledges.
6. **Superglide**: requires mantling.

## Momentum system design

### Why
Vanilla applies heavy ground friction every tick (horizontal velocity × ~0.546 on normal blocks),
so speed can never build up or carry between actions. Air friction is ~0.91 with very small air
acceleration (~0.02/tick). Before changing anything, read the vanilla logic in `LivingEntity#travel`
and the related friction/speed methods (the 26.x code is unobfuscated) and summarize it for me.

### Model (Source/Titanfall-style)
- **State-based friction.** Each movement state defines its own friction and acceleration:
  - `GROUNDED`: normal friction (close to vanilla by default)
  - `SLIDING`: very low friction, slow speed decay
  - `AIRBORNE`: near-zero horizontal friction
  - `LANDING`: short window after touching ground where friction is not applied yet (enables bunny hopping)
  - `MANTLING`: velocity controlled by the mantle itself, player input ignored
- **Friction skip on landing.** If the player jumps within the landing window (config, ~1–2 ticks,
  since the game runs at 20 TPS), no ground friction is applied and horizontal speed is kept.
- **Quake-style air acceleration.** In the air, input only adds velocity along the wish direction
  up to a cap: `addSpeed = maxAirSpeed - dot(velocity, wishDir)`, clamped to `[0, airAccel]`.
  This makes air strafing (curving + gaining speed by turning while strafing) possible.
- **Tap strafing (adapted).** Apex's version depends on high-frame-rate input and does not map to 20 TPS.
  Our version: a forward input in the air within a window after a jump strongly rotates velocity toward
  the look direction while keeping most of the speed. Exact rules to be designed together when we get there.
- **Superglide.** Jumping within a short window at the end of a mantle converts it into a forward burst.
- **Speed cap.** A configurable hard cap on horizontal speed to keep it playable and multiplayer-safe.

### Code structure
- `movement/state/`: the movement state enum and per-player state data, stored with NeoForge data attachments.
- `movement/physics/`: pure functions for friction, acceleration, air strafing (no Minecraft side effects,
  easy to reason about and test).
- **Players only.** The momentum system must never affect mobs or other non-player entities.
  The vanilla movement code lives in `LivingEntity` and is inherited by `Player`, so: prefer mixins targeting
  `Player` (or client-side `LocalPlayer` where appropriate), not `LivingEntity`. If a hook truly must go in
  `LivingEntity`, its first line must return to vanilla behavior unless `this` is a `Player`.
  Explain the target class choice for every mixin.
- **One mixin into the player's travel logic** that, when our system applies, replaces vanilla's horizontal
  acceleration/friction with ours. Keep everything else vanilla: swimming, lava, elytra, creative/spectator
  flight, riding, ladders/vines, levitation, powder snow, etc. Fall back to vanilla in all those cases.
- `config/`: all tunable numbers (friction per state, air accel, max air speed, landing window, slide boost,
  speed cap, ...) in a NeoForge config file.
- `client/debug/`: a toggleable debug HUD showing current state, horizontal speed, and velocity vector.
  Required from step 1; we tune by numbers, not only by feel.
- Feedback (camera tilt/dip, FOV change with speed, sounds) is planned from the start, kept client-only,
  and toggleable in config.

### Client/server
- Player movement is simulated on the client and sent to the server. Keep the physics on the client for
  responsiveness, but the server must know each player's movement state (sync with network packets)
  so other players see slides/mantles and so server logic stays consistent.
- The server's "moved too quickly" checks may reject faster-than-vanilla movement. Handle this deliberately
  (e.g. account for our speed cap server-side) instead of disabling checks globally. Test on a dedicated
  server (`./gradlew runServer`) with the client, not only in singleplayer.

### Principles
- Tunable values go in config, never hard-coded.
- Prefer NeoForge events and APIs; use mixins only when there is no event or hook for the behavior.
  Keep each mixin small and focused, and document why it is needed.
- Be explicit about which side (client/server) each piece of logic runs on.
- Should behave reasonably in multiplayer and alongside other mods.

## Assets and legal
Apex Legends is owned by EA / Respawn. Never use or recreate ripped game assets
(models, textures, sounds, voice lines). All assets must be original.
Do not use "Apex Legends" or character names as official branding. The mod is "inspired by" the game.

## Commands
- Build: `./gradlew build`
- Launch dev client: `./gradlew runClient`
- Launch dev server: `./gradlew runServer`
- The dev game's world, logs and configs live in `run/` (git-ignored)

## How to work in this repo
- I am new to Minecraft modding. Briefly explain NeoForge- or Minecraft-specific concepts when you introduce them.
- Make changes in small, testable steps. After a change, make sure `./gradlew build` succeeds.
- Ask before large refactors, new dependencies, or changing Gradle/NeoForge versions.
- Don't commit or push without asking. I commit working states myself.
- Remove the template's example block/item code once real features exist.
