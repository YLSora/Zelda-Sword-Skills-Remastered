package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/** Instance binding shared by bosses with different vanilla entity base classes. */
public interface DungeonBoss {
    boolean isBoss();
    Optional<DungeonType> dungeonType();
    Optional<BlockPos> dungeonCorePos();
    void linkDungeon(DungeonType type, BlockPos corePos);
}
