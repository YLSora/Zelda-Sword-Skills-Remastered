package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zeldaswordskills_remastered.block.entity.SecretRoomCore;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class SecretRoomCoreBlock extends BaseEntityBlock {
    public SecretRoomCoreBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new SecretRoomCore(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,ZSSRegistries.SECRET_ROOM_CORE_ENTITY.get(),SecretRoomCore::tick);
    }
}
