package zeldaswordskills_remastered.song;

import net.minecraft.server.level.ServerPlayer;

public interface SongEntityListener {
    boolean onSongPlayed(ServerPlayer player, SongDefinition song, int strength, int affectedBefore);
}
