package zeldaswordskills_remastered.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class BombFlowerSeedItem extends Item {
    public BombFlowerSeedItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        BlockPos soil = context.getClickedPos();
        BlockPos target = soil.above();
        if (!context.getClickedFace().equals(net.minecraft.core.Direction.UP)
                || !context.getLevel().getBlockState(target).canBeReplaced()
                || !(context.getLevel().getBlockState(soil).is(Blocks.GRASS_BLOCK)
                || context.getLevel().getBlockState(soil).is(Blocks.DIRT)
                || context.getLevel().getBlockState(soil).is(Blocks.COARSE_DIRT)
                || context.getLevel().getBlockState(soil).is(Blocks.FARMLAND))) return InteractionResult.FAIL;
        if (!context.getLevel().isClientSide) {
            context.getLevel().setBlock(target, ZSSRegistries.BOMB_FLOWER.get().defaultBlockState(), 3);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
