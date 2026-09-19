package zeldaswordskills_remastered.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ZSSWorldData extends SavedData {
    private static final String DATA_NAME = ZeldaSwordSkills_Remastered.MOD_ID + "_world";
    private long weatherSongCooldownUntil;
    private long timeSongCooldownUntil;
    private final Map<ResourceLocation, DungeonState> dungeons = new LinkedHashMap<>();

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
        return tag;
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
        setDirty();
    }

    public record DungeonState(boolean completed, long cooldownUntil) {}
}
