package io.github.naistafang.apexmovement.client.sound;

import io.github.naistafang.apexmovement.ApexMovement;
import io.github.naistafang.apexmovement.config.ClientConfig;
import io.github.naistafang.apexmovement.movement.sound.MovementSounds;
import io.github.naistafang.apexmovement.movement.state.MantlePhase;
import io.github.naistafang.apexmovement.movement.state.MovementAttachments;
import io.github.naistafang.apexmovement.movement.state.MovementData;
import io.github.naistafang.apexmovement.movement.state.MovementState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

// Client only: plays movement sounds for every nearby player (the local one and others), from the movement state each
// client already knows (the local player's own, and the synced state of others). Sounds follow the player.
// Sliding and climbing use the step sound of the block being touched (the ground, or the wall), played faster for a
// slide and slower for a climb; mantle grabs, pull-ups, superglides and wall-bounces have their own sound events.
// Wall-bounces aren't part of the synced state, so they are only heard for your own player.
@EventBusSubscriber(modid = ApexMovement.MODID, value = Dist.CLIENT)
public final class MovementSoundPlayer {
    // Ticks between slide scrapes and between climbing "hand grabs".
    private static final int SCRAPE_INTERVAL = 4;
    private static final int CLIMB_INTERVAL = 5;
    // Slide speed (m/s) at which the scrape is at full volume.
    private static final double LOUD_SLIDE_SPEED = 10.0;
    // Vanilla plays walking steps at this fraction of the block sound's volume.
    private static final float STEP_VOLUME = 0.15F;
    // Heights (blocks above the feet) checked for the wall block being climbed, most likely first.
    private static final double[] WALL_SAMPLE_HEIGHTS = {1.0, 0.3, 1.6};

    private MovementSoundPlayer() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        boolean enabled = ClientConfig.SPEC.isLoaded() && ClientConfig.SOUNDS.getAsBoolean();
        for (Player player : level.players()) {
            MovementData data = player.getData(MovementAttachments.MOVEMENT);
            MovementState previousState = data.soundState();
            MantlePhase previousPhase = data.soundPhase();
            boolean previousGlide = data.soundGlide();
            MovementState state = data.state();
            MantlePhase phase = state == MovementState.MANTLING ? data.mantlePhase() : MantlePhase.NONE;
            int ticks = data.updateSoundState(state, phase, data.superglideFlight());
            boolean wallJump = data.consumeWallJumpSound();
            if (!enabled || data.vanillaReason() != null) {
                continue;
            }

            if (state == MovementState.SLIDING && (previousState != MovementState.SLIDING || ticks % SCRAPE_INTERVAL == 0)) {
                // Speed from the position change, which is known for other players too. Louder at the start.
                double speed = Math.sqrt(Mth.square(player.getX() - player.xo) + Mth.square(player.getZ() - player.zo)) * 20.0;
                float loudness = previousState != MovementState.SLIDING ? 1.5F : (float) Mth.clamp(speed / LOUD_SLIDE_SPEED, 0.4, 1.2);
                playStep(level, player, player.getOnPos(), loudness, ClientConfig.SLIDE_PITCH.get().floatValue());
            }
            if (phase == MantlePhase.CLIMB && ticks % CLIMB_INTERVAL == 0) {
                BlockPos wall = wallBlock(level, player);
                if (wall != null) {
                    playStep(level, player, wall, 1.2F, ClientConfig.CLIMB_PITCH.get().floatValue());
                }
            }
            if (phase == MantlePhase.RISE && previousPhase != MantlePhase.RISE) {
                play(level, player, MovementSounds.MANTLE_GRAB, 1.0F);
            }
            if (phase == MantlePhase.PULL && previousPhase != MantlePhase.PULL) {
                play(level, player, MovementSounds.MANTLE_PULL, 1.0F);
            }
            if (data.superglideFlight() && !previousGlide) {
                play(level, player, MovementSounds.SUPERGLIDE, 1.0F);
            }
            if (wallJump) {
                play(level, player, MovementSounds.WALL_JUMP, 1.0F);
            }
        }
    }

    // The block's own step sound (what walking on it sounds like), at `loudness` times the walking volume and
    // `speed` times its playback rate.
    private static void playStep(ClientLevel level, Player player, BlockPos pos, float loudness, float speed) {
        BlockState block = level.getBlockState(pos);
        if (block.isAir()) {
            return;
        }
        SoundType type = block.getSoundType(level, pos, player);
        float pitch = type.getPitch() * speed * (0.95F + level.getRandom().nextFloat() * 0.1F);
        float volume = type.getVolume() * STEP_VOLUME * loudness * ClientConfig.SOUND_VOLUME.get().floatValue();
        level.playLocalSound(player, type.getStepSound(), SoundSource.PLAYERS, volume, pitch);
    }

    // The solid block right in front of a climbing player (the direction they face, like Mantling's wall choice).
    private static @Nullable BlockPos wallBlock(ClientLevel level, Player player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Direction face = Direction.getApproximateNearest(-Mth.sin(yaw), 0.0F, Mth.cos(yaw));
        double reach = player.getBbWidth() / 2.0 + 0.2;
        for (double height : WALL_SAMPLE_HEIGHTS) {
            BlockPos pos = BlockPos.containing(player.getX() + face.getStepX() * reach, player.getY() + height, player.getZ() + face.getStepZ() * reach);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    private static void play(ClientLevel level, Player player, Supplier<SoundEvent> sound, float volume) {
        float pitch = 0.95F + level.getRandom().nextFloat() * 0.1F;
        level.playLocalSound(player, sound.get(), SoundSource.PLAYERS, volume * ClientConfig.SOUND_VOLUME.get().floatValue(), pitch);
    }
}
