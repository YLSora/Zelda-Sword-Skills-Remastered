package zeldaswordskills_remastered.song;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.item.InstrumentItem;
import zeldaswordskills_remastered.network.SongStateMessage;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.ZSSWorldData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SongService {
    public static final int NOTIFY_RADIUS = 8;
    public static final int MAX_NOTIFY_TARGETS = 16;
    public static final int RECOGNITION_TICKS = 60;
    public static final long SCARECROW_CONFIRM_DELAY = 7L * 24000L;
    private static final ResourceLocation LON_LON_COW = ResourceLocation.fromNamespaceAndPath("zeldaswordskills_remastered", "lon_lon_cow");

    private SongService() {
    }

    public static void begin(ServerPlayer player, ZSSPlayerData data, boolean scarecrowMode) {
        HeldInstrument instrument = heldInstrument(player);
        if (instrument == null || scarecrowMode && !isLookingAtScarecrow(player)) {
            close(player, data, SongStateMessage.Status.FAILED);
            return;
        }
        data.songState().open(instrument.id(), scarecrowMode);
        sync(player, data, SongStateMessage.Status.OPEN, Optional.empty(), 0L);
    }

    public static void cancel(ServerPlayer player, ZSSPlayerData data) {
        if (!data.songState().open()) return;
        data.songState().reset();
        ZSSNetwork.syncSongState(player, new SongStateMessage(SongStateMessage.Status.CLOSED, List.of(), Optional.empty(), 0L, false));
    }

    public static void setFreePlay(ServerPlayer player, ZSSPlayerData data, boolean freePlay) {
        PlayerSongState state = data.songState();
        if (!state.open() || state.matchedSong() != null) return;
        state.setFreePlay(freePlay);
        sync(player, data, freePlay ? SongStateMessage.Status.FREE_PLAY : SongStateMessage.Status.OPEN, Optional.empty(), 0L);
    }

    public static void note(ServerPlayer player, ZSSPlayerData data, SongNote note, int semitoneOffset) {
        PlayerSongState state = data.songState();
        HeldInstrument instrument = heldInstrument(player);
        long now = player.level().getGameTime();
        if (!state.open() || instrument == null || !instrument.id().equals(state.instrumentId()) || !state.addNote(note)) return;
        float pitch = note.pitch() * (float) Math.pow(2.0D, semitoneOffset / 12.0D);
        player.level().playSound(null, player.blockPosition(), ZSSRegistries.NOTE_OCARINA.get(), SoundSource.PLAYERS, 1.2F, pitch);
        if (state.freePlay()) {
            sync(player, data, SongStateMessage.Status.FREE_PLAY, Optional.empty(), 0L);
            return;
        }
        state.waitForRecognition(now + RECOGNITION_TICKS);
        if (state.scarecrowMode()) {
            if (state.notes().size() == 8) handleScarecrowEntry(player, data);
            else sync(player, data, SongStateMessage.Status.OPEN, Optional.empty(), 0L);
            return;
        }
        Optional<SongDefinition> exact = SongCatalog.exact(state.notes(), data.songs());
        if (exact.isPresent()) {
            match(player, data, exact.get().id(), exact.get().minimumDuration());
            return;
        }
        List<SongNote> custom = scarecrowNotes(data);
        if (data.songs().contains(ZSSContentIds.SCARECROW) && custom.equals(state.notes())) {
            match(player, data, ZSSContentIds.SCARECROW, 160);
            return;
        }
        boolean prefix = SongCatalog.isLearnedPrefix(state.notes(), data.songs())
                || data.songs().contains(ZSSContentIds.SCARECROW) && startsWith(custom, state.notes());
        if (prefix) {
            sync(player, data, SongStateMessage.Status.OPEN, Optional.empty(), 0L);
        } else {
            boolean noteCanStartSong = SongCatalog.isLearnedPrefix(List.of(note), data.songs())
                    || data.songs().contains(ZSSContentIds.SCARECROW) && startsWith(custom, List.of(note));
            state.restartCandidate(note, noteCanStartSong);
            sync(player, data, SongStateMessage.Status.OPEN, Optional.empty(), 0L);
        }
    }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        trackHorse(player, data);
        PlayerSongState state = data.songState();
        if (!state.open()) return;
        long now = player.level().getGameTime();
        if (state.matchedSong() == null) {
            if (state.recognitionDeadline() > 0L && now >= state.recognitionDeadline()) {
                ZSSNetwork.syncSongState(player, new SongStateMessage(SongStateMessage.Status.FAILED,
                        state.hudNotes(), Optional.empty(), 0L, state.scarecrowMode()));
                state.clearCandidate();
                state.clearRecognitionDeadline();
            }
            return;
        }
        HeldInstrument instrument = heldInstrument(player);
        if (instrument == null || !instrument.id().equals(state.instrumentId())) {
            cancel(player, data);
            return;
        }
        if (now < state.completionDeadline()) return;
        ResourceLocation song = state.matchedSong();
        int strength = instrument.item().songStrength();
        boolean success = perform(player, data, song, strength);
        List<SongNote> notes = state.hudNotes();
        state.reset();
        SongStateMessage.Status status = success ? SongStateMessage.Status.SUCCESS
                : strength < 5 ? SongStateMessage.Status.INSTRUMENT_TOO_WEAK : SongStateMessage.Status.EFFECT_FAILED;
        ZSSNetwork.syncSongState(player, new SongStateMessage(status,
                notes, Optional.of(song), 0L, false));
        ZSSNetwork.syncPlayerData(player);
    }

    private static void match(ServerPlayer player, ZSSPlayerData data, ResourceLocation song, int duration) {
        long deadline = player.level().getGameTime() + duration;
        data.songState().match(song, deadline);
        player.level().playSound(null, player.blockPosition(), ZSSRegistries.SECRET_MEDLEY.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        var sound = ZSSRegistries.SONG_SOUNDS.get(song);
        if (sound != null) player.level().playSound(null, player.blockPosition(), sound.get(), SoundSource.RECORDS, 1.0F, 1.0F);
        sync(player, data, SongStateMessage.Status.MATCHED, Optional.of(song), deadline);
    }

    private static void handleScarecrowEntry(ServerPlayer player, ZSSPlayerData data) {
        List<SongNote> notes = data.songState().notes();
        long now = player.server.overworld().getGameTime();
        if (data.scarecrowNotes().isEmpty()) {
            if (!SongCatalog.isUniqueScarecrowMelody(notes)) {
                failAndClear(player, data, "message.zeldaswordskills_remastered.scarecrow_not_unique");
                return;
            }
            data.setScarecrowMelody(notes, now + SCARECROW_CONFIRM_DELAY);
            data.songState().reset();
            ZSSNetwork.syncSongState(player, new SongStateMessage(SongStateMessage.Status.RECORDED, notes,
                    Optional.of(ZSSContentIds.SCARECROW), 0L, true));
            ZSSNetwork.syncPlayerData(player);
            return;
        }
        if (!scarecrowNotes(data).equals(notes)) {
            failAndClear(player, data, "message.zeldaswordskills_remastered.scarecrow_mismatch");
            return;
        }
        if (now < data.scarecrowConfirmedAt()) {
            failAndClear(player, data, "message.zeldaswordskills_remastered.scarecrow_too_early");
            return;
        }
        data.learnSong(ZSSContentIds.SCARECROW);
        ZSSAdvancementService.songLearned(player, ZSSContentIds.SCARECROW, data.songs().size());
        data.songState().reset();
        player.level().playSound(null, player.blockPosition(), ZSSRegistries.SUCCESS.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        ZSSNetwork.syncSongState(player, new SongStateMessage(SongStateMessage.Status.SUCCESS, notes,
                Optional.of(ZSSContentIds.SCARECROW), 0L, true));
        ZSSNetwork.syncPlayerData(player);
    }

    private static void failAndClear(ServerPlayer player, ZSSPlayerData data, String message) {
        List<SongNote> notes = data.songState().hudNotes();
        data.songState().clearCandidate();
        data.songState().clearRecognitionDeadline();
        player.sendSystemMessage(Component.translatable(message));
        ZSSNetwork.syncSongState(player, new SongStateMessage(SongStateMessage.Status.FAILED, notes,
                Optional.of(ZSSContentIds.SCARECROW), 0L, true));
    }

    private static boolean perform(ServerPlayer player, ZSSPlayerData data, ResourceLocation songId, int strength) {
        SongDefinition definition = SongCatalog.get(songId).orElse(null);
        if (definition != null) notifyListeners(player, definition, strength, 8);
        if (songId.equals(ZSSContentIds.SCARECROW)) return successSound(player);
        if (songId.equals(ZSSContentIds.EPONA)) return epona(player, data, strength);
        if (songId.equals(ZSSContentIds.HEALING)) return healing(player, data, strength);
        if (songId.equals(ZSSContentIds.SOARING)) return soaring(player, strength);
        if (songId.equals(ZSSContentIds.STORMS)) return storms(player, strength);
        if (songId.equals(ZSSContentIds.SUN)) return sun(player, strength);
        if (isWarpSong(songId)) return warp(player, data, songId, strength);
        return successSound(player);
    }

    private static boolean epona(ServerPlayer player, ZSSPlayerData data, int strength) {
        AABB bounds = player.getBoundingBox().inflate(8.0D, 4.0D, 8.0D);
        for (AbstractHorse horse : player.level().getEntitiesOfClass(AbstractHorse.class, bounds)) {
            if (!horse.isTamed()) horse.tameWithName(player);
        }
        zeldaswordskills_remastered.progression.AcquisitionService.markLonLonCows(player);
        if (strength < 5) return true;
        if (!player.level().dimension().equals(Level.OVERWORLD) || !player.level().canSeeSky(player.blockPosition().above())) return false;
        if (data.horseUuid().isEmpty()) return false;
        ServerLevel overworld = player.server.overworld();
        ChunkPos chunk = new ChunkPos(data.horseChunk());
        overworld.getChunk(chunk.x, chunk.z);
        Entity entity = overworld.getEntity(data.horseUuid().get());
        if (!(entity instanceof AbstractHorse horse) || !horse.isAlive()) return false;
        horse.ejectPassengers();
        if (horse.isLeashed()) horse.dropLeash(true, true);
        Vec3 look = player.getLookAngle();
        BlockPos destination = BlockPos.containing(player.getX() + look.x * 2.0D, player.getY(), player.getZ() + look.z * 2.0D);
        Vec3 safe = findSafe(overworld, horse, destination, 6);
        if (safe == null) return false;
        horse.teleportTo(safe.x, safe.y, safe.z);
        data.setHorse(horse.getUUID(), horse.chunkPosition().toLong());
        return successSound(player);
    }

    private static boolean healing(ServerPlayer player, ZSSPlayerData data, int strength) {
        long now = player.server.overworld().getGameTime();
        if (strength < 5 || player.getHealth() >= player.getMaxHealth() || now < data.healingCooldownUntil()) return false;
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        data.setHealingCooldownUntil(now + 24000L);
        return successSound(player);
    }

    private static boolean soaring(ServerPlayer player, int strength) {
        if (strength < 5 || player.level().dimension().equals(Level.NETHER) || player.level().dimension().equals(Level.END)) return false;
        ServerLevel level = player.serverLevel();
        BlockPos destination = null;
        BlockPos respawn = player.getRespawnPosition();
        if (player.getRespawnDimension().equals(level.dimension()) && respawn != null
                && level.getBlockState(respawn).isBed(level, respawn, player)) destination = respawn;
        if (destination == null) destination = level.getSharedSpawnPos();
        Vec3 safe = findSafe(level, player, destination, 16);
        if (safe == null) return false;
        player.stopRiding();
        player.teleportTo(level, safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
        return successSound(player);
    }

    private static boolean storms(ServerPlayer player, int strength) {
        if (strength < 5) return false;
        ServerLevel overworld = player.server.overworld();
        long now = overworld.getGameTime();
        ZSSWorldData worldData = ZSSWorldData.get(overworld);
        if (!player.isCreative() && now < worldData.weatherSongCooldownUntil()) return false;
        if (overworld.isRaining()) overworld.setWeatherParameters(2000, 0, false, false);
        else overworld.setWeatherParameters(0, 2000, true, true);
        worldData.setWeatherSongCooldownUntil(now + ZSSConfig.SERVER.weatherSongCooldownTicks.get());
        return successSound(player);
    }

    private static boolean sun(ServerPlayer player, int strength) {
        if (strength < 5) return false;
        ServerLevel overworld = player.server.overworld();
        long now = overworld.getGameTime();
        ZSSWorldData worldData = ZSSWorldData.get(overworld);
        if (!player.isCreative() && now < worldData.timeSongCooldownUntil()) return false;
        long dayTime = overworld.getDayTime();
        long withinDay = Math.floorMod(dayTime, 24000L);
        long advance = withinDay < 12000L ? 12000L - withinDay : 24000L - withinDay;
        for (ServerLevel level : player.server.getAllLevels()) level.setDayTime(level.getDayTime() + advance);
        worldData.setTimeSongCooldownUntil(now + ZSSConfig.SERVER.timeSongCooldownTicks.get());
        return successSound(player);
    }

    private static boolean warp(ServerPlayer player, ZSSPlayerData data, ResourceLocation songId, int strength) {
        if (strength < 5) return false;
        ZSSPlayerData.WarpPoint warp = data.warpPoints().get(songId);
        if (warp == null) return false;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, warp.dimension());
        ServerLevel targetLevel = player.server.getLevel(dimension);
        if (targetLevel == null) return false;
        BlockState state = targetLevel.getBlockState(warp.pos());
        if (!(state.getBlock() instanceof zeldaswordskills_remastered.block.WarpStoneBlock warpStone)
                || !warpStone.warpSong().songId().equals(songId)) return false;
        Vec3 safe = findSafe(targetLevel, player, warp.pos().above(), 12);
        if (safe == null) return false;
        player.stopRiding();
        player.teleportTo(targetLevel, safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
        return successSound(player);
    }

    public static int notifyListeners(ServerPlayer player, SongDefinition song, int strength, int requestedRadius) {
        int radius = Math.max(0, Math.min(NOTIFY_RADIUS, requestedRadius));
        int affected = 0;
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), center.offset(radius, radius / 2, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SongBlockListener listener
                    && listener.onSongPlayed(level, pos.immutable(), state, player, song, strength, affected)) affected++;
            if (affected >= MAX_NOTIFY_TARGETS) return affected;
        }
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(radius, radius / 2.0D, radius))) {
            if (entity instanceof SongEntityListener listener && listener.onSongPlayed(player, song, strength, affected)) affected++;
            if (affected >= MAX_NOTIFY_TARGETS) return affected;
        }
        return affected;
    }

    private static void trackHorse(ServerPlayer player, ZSSPlayerData data) {
        if (player.getVehicle() instanceof AbstractHorse horse) data.setHorse(horse.getUUID(), horse.chunkPosition().toLong());
    }

    private static Vec3 findSafe(ServerLevel level, Entity entity, BlockPos base, int upwardLimit) {
        int minimumY = Math.max(level.getMinBuildHeight() + 1, base.getY());
        for (int y = minimumY; y <= Math.min(level.getMaxBuildHeight() - 2, minimumY + upwardLimit); y++) {
            BlockPos feet = new BlockPos(base.getX(), y, base.getZ());
            if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) continue;
            AABB box = entity.getDimensions(Pose.STANDING).makeBoundingBox(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
            if (level.noCollision(entity, box)) return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        }
        return null;
    }

    private static HeldInstrument heldInstrument(ServerPlayer player) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.getItem() instanceof InstrumentItem instrument) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) return new HeldInstrument(instrument, id);
            }
        }
        return null;
    }

    private static boolean isLookingAtScarecrow(ServerPlayer player) {
        HitResult hit = player.pick(5.0D, 0.0F, false);
        return hit instanceof BlockHitResult blockHit && ScarecrowStructure.isScarecrowNear(player.level(), blockHit.getBlockPos());
    }

    private static List<SongNote> scarecrowNotes(ZSSPlayerData data) {
        List<SongNote> notes = new ArrayList<>();
        for (String value : data.scarecrowNotes()) {
            try { notes.add(SongNote.valueOf(value)); }
            catch (IllegalArgumentException ignored) { return List.of(); }
        }
        return List.copyOf(notes);
    }

    private static boolean startsWith(List<SongNote> sequence, List<SongNote> prefix) {
        if (prefix.size() > sequence.size()) return false;
        for (int i = 0; i < prefix.size(); i++) if (sequence.get(i) != prefix.get(i)) return false;
        return true;
    }

    private static boolean isWarpSong(ResourceLocation id) {
        return id.equals(ZSSContentIds.BOLERO) || id.equals(ZSSContentIds.MINUET) || id.equals(ZSSContentIds.PRELUDE)
                || id.equals(ZSSContentIds.OATH) || id.equals(ZSSContentIds.NOCTURNE)
                || id.equals(ZSSContentIds.REQUIEM) || id.equals(ZSSContentIds.SERENADE);
    }

    private static boolean successSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), ZSSRegistries.SUCCESS.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private static void sync(ServerPlayer player, ZSSPlayerData data, SongStateMessage.Status status,
                             Optional<ResourceLocation> song, long deadline) {
        ZSSNetwork.syncSongState(player, new SongStateMessage(status, data.songState().hudNotes(), song, deadline,
                data.songState().scarecrowMode()));
    }

    private static void close(ServerPlayer player, ZSSPlayerData data, SongStateMessage.Status status) {
        data.songState().reset();
        ZSSNetwork.syncSongState(player, new SongStateMessage(status, List.of(), Optional.empty(), 0L, false));
    }

    private record HeldInstrument(InstrumentItem item, ResourceLocation id) {}
}
