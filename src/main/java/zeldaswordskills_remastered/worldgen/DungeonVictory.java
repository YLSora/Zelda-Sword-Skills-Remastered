package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;
import zeldaswordskills_remastered.registry.ZSSRegistries;

final class DungeonVictory {
    private DungeonVictory() {}

    static void reward(ServerLevel level, DungeonCore core, int difficulty) {
        ExperienceOrb.award(level, Vec3.atCenterOf(core.getBlockPos().above(2)), 1000 * difficulty);
        level.playSound(null, core.getBlockPos(), ZSSRegistries.SECRET_MEDLEY.get(), SoundSource.BLOCKS, 1, 1);
        var tablets = java.util.List.of(ZSSRegistries.ANCIENT_TABLET_BOMBOS,
                ZSSRegistries.ANCIENT_TABLET_ETHER, ZSSRegistries.ANCIENT_TABLET_QUAKE);
        int radius = Math.max(1, core.arenaRadius() - 1);
        int center = core.arenaWidth() / 2;
        var positions = new java.util.ArrayList<BlockPos>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos pos = DungeonArena.localPos(core, center + dx, core.arenaHeight(), center + dz);
            if (level.isEmptyBlock(pos) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
                positions.add(pos);
        }
        if (positions.isEmpty()) return;
        BlockPos pos = positions.get(level.random.nextInt(positions.size()));
        level.setBlockAndUpdate(pos, tablets.get(level.random.nextInt(3)).get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, level.random.nextBoolean() ? Direction.SOUTH : Direction.EAST));
    }
}
