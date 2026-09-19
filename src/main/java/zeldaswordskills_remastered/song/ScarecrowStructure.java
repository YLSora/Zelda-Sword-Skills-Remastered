package zeldaswordskills_remastered.song;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;

public final class ScarecrowStructure {
    private ScarecrowStructure() {
    }

    public static boolean isScarecrowNear(BlockGetter level, BlockPos clicked) {
        for (BlockPos head : BlockPos.betweenClosed(clicked.offset(-2, -2, -2), clicked.offset(2, 2, 2))) {
            if (!isHead(level, head) || !level.getBlockState(head.below()).is(Blocks.HAY_BLOCK)
                    || !level.getBlockState(head.below(2)).is(Blocks.HAY_BLOCK)) continue;
            BlockPos shoulder = head.below();
            boolean eastWest = level.getBlockState(shoulder.east()).is(Blocks.HAY_BLOCK)
                    && level.getBlockState(shoulder.west()).is(Blocks.HAY_BLOCK);
            boolean northSouth = level.getBlockState(shoulder.north()).is(Blocks.HAY_BLOCK)
                    && level.getBlockState(shoulder.south()).is(Blocks.HAY_BLOCK);
            if (eastWest || northSouth) return true;
        }
        return false;
    }

    private static boolean isHead(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.PUMPKIN) || level.getBlockState(pos).is(Blocks.CARVED_PUMPKIN)
                || level.getBlockState(pos).is(Blocks.JACK_O_LANTERN);
    }
}
