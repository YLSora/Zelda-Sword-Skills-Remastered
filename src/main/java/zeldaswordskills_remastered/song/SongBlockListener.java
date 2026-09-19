package zeldaswordskills_remastered.song;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public interface SongBlockListener {
    boolean onSongPlayed(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                         SongDefinition song, int strength, int affectedBefore);
}
