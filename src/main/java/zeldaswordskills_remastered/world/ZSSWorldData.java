package zeldaswordskills_remastered.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ZSSWorldData extends SavedData {
    private static final String DATA_NAME = ZeldaSwordSkills_Remastered.MOD_ID + "_world";
    private long weatherSongCooldownUntil;
    private long timeSongCooldownUntil;
    private final Map<ResourceLocation, DungeonState> dungeons = new LinkedHashMap<>();
    private final Map<ResourceLocation, ProtectedTemple> peacefulForestTemples = new LinkedHashMap<>();

    public static ZSSWorldData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(ZSSWorldData::load, ZSSWorldData::new, DATA_NAME);
    }

    public static ZSSWorldData load(CompoundTag tag) {
        ZSSWorldData data = new ZSSWorldData();
        data.weatherSongCooldownUntil = Math.max(0L, tag.getLong("weather_song_cooldown_until"));
        data.timeSongCooldownUntil = Math.max(0L, tag.getLong("time_song_cooldown_until"));
        ListTag entries = tag.getList("dungeons", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null) {
                ZeldaSwordSkills_Remastered.LOGGER.warn("Skipping invalid dungeon ID in SavedData");
                continue;
            }
            data.dungeons.put(id, new DungeonState(entry.getBoolean("completed"), Math.max(0L, entry.getLong("cooldown_until"))));
        }
        ListTag forests = tag.getList("peaceful_forest_temples", Tag.TAG_COMPOUND);
        for (int i = 0; i < forests.size(); i++) {
            CompoundTag entry = forests.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (id == null || dimension == null || !data.dungeonState(id).completed()) continue;
            data.peacefulForestTemples.put(id, new ProtectedTemple(dimension,
                    new AABB(entry.getDouble("min_x"), entry.getDouble("min_y"), entry.getDouble("min_z"),
                            entry.getDouble("max_x"), entry.getDouble("max_y"), entry.getDouble("max_z"))));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 1);
        tag.putLong("weather_song_cooldown_until", weatherSongCooldownUntil);
        tag.putLong("time_song_cooldown_until", timeSongCooldownUntil);
        ListTag entries = new ListTag();
        dungeons.forEach((id, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putBoolean("completed", state.completed());
            entry.putLong("cooldown_until", state.cooldownUntil());
            entries.add(entry);
        });
        tag.put("dungeons", entries);
        ListTag forests = new ListTag();
        peacefulForestTemples.forEach((id, temple) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putString("dimension", temple.dimension().toString());
            AABB room = temple.room();
            entry.putDouble("min_x", room.minX);
            entry.putDouble("min_y", room.minY);
            entry.putDouble("min_z", room.minZ);
            entry.putDouble("max_x", room.maxX);
            entry.putDouble("max_y", room.maxY);
            entry.putDouble("max_z", room.maxZ);
            forests.add(entry);
        });
        tag.put("peaceful_forest_temples", forests);
        return tag;
    }

    public void protectForestTemple(ResourceLocation id, ServerLevel level, AABB room) {
        peacefulForestTemples.put(id, new ProtectedTemple(level.dimension().location(), room));
        setDirty();
    }

    public boolean insidePeacefulForestTemple(ServerLevel level, BlockPos pos) {
        return peacefulForestTemples.values().stream().anyMatch(temple ->
                temple.dimension().equals(level.dimension().location()) && temple.room().contains(pos.getCenter()));
    }

    public long weatherSongCooldownUntil() { return weatherSongCooldownUntil; }
    public long timeSongCooldownUntil() { return timeSongCooldownUntil; }
    public Map<ResourceLocation, DungeonState> dungeons() { return Map.copyOf(dungeons); }
    public DungeonState dungeonState(ResourceLocation id) { return dungeons.getOrDefault(id,new DungeonState(false,0L)); }

    public void setWeatherSongCooldownUntil(long gameTime) {
        weatherSongCooldownUntil = Math.max(0L, gameTime);
        setDirty();
    }

    public void setTimeSongCooldownUntil(long gameTime) {
        timeSongCooldownUntil = Math.max(0L, gameTime);
        setDirty();
    }

    public void setDungeonState(ResourceLocation id, boolean completed, long cooldownUntil) {
        dungeons.put(id, new DungeonState(completed, Math.max(0L, cooldownUntil)));
        if (!completed) peacefulForestTemples.remove(id);
        setDirty();
    }

    public record DungeonState(boolean completed, long cooldownUntil) {}
    private record ProtectedTemple(ResourceLocation dimension, AABB room) {}
}
