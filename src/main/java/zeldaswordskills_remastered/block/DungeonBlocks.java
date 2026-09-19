package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.worldgen.DungeonType;

public final class DungeonBlocks {
    private DungeonBlocks() {}

    @SuppressWarnings("deprecation")
    public static class Stone extends Block implements ZSSBlockInteractions.Explodable {
        public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
        public Stone(Properties properties) {
            super(properties); registerDefaultState(stateDefinition.any().setValue(SEALED, true));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(SEALED); }
        @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
            return state.getValue(SEALED) ? 0 : super.getDestroyProgress(state, player, level, pos);
        }
        @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) { return !state.getValue(SEALED); }
        @Override public boolean onZssExplosion(ServerLevel level, BlockPos pos, BlockState state,
                                                ZSSBlockInteractions.ExplosionKind kind, @Nullable ServerPlayer source) {
            if (state.getValue(SEALED) || kind == ZSSBlockInteractions.ExplosionKind.NORMAL) return false;
            level.destroyBlock(pos, true, source); return true;
        }
    }

    @SuppressWarnings("deprecation")
    public static final class Core extends BaseEntityBlock implements ZSSBlockInteractions.Explodable {
        public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
        public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.Rotation>
                STRUCTURE_ROTATION = net.minecraft.world.level.block.state.properties.EnumProperty.create(
                        "structure_rotation", net.minecraft.world.level.block.Rotation.class);
        public Core(Properties properties) {
            super(properties); registerDefaultState(stateDefinition.any().setValue(SEALED, true)
                    .setValue(STRUCTURE_ROTATION, net.minecraft.world.level.block.Rotation.NONE));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(SEALED, STRUCTURE_ROTATION); }
        @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
            return state.setValue(STRUCTURE_ROTATION, state.getValue(STRUCTURE_ROTATION).getRotated(rotation));
        }
        @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StageNineBlockEntities.DungeonCore(pos, state); }
        @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                         BlockEntityType<T> type) {
            return level.isClientSide ? null : createTickerHelper(type, zeldaswordskills_remastered.registry.ZSSRegistries.DUNGEON_CORE_BLOCK_ENTITY.get(),
                    StageNineBlockEntities.DungeonCore::serverTick);
        }
        @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
            return state.getValue(SEALED) ? 0 : super.getDestroyProgress(state, player, level, pos);
        }
        @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) { return !state.getValue(SEALED); }
        @Override public boolean onZssExplosion(ServerLevel level, BlockPos pos, BlockState state,
                                                ZSSBlockInteractions.ExplosionKind kind, @Nullable ServerPlayer source) {
            if (state.getValue(SEALED) || kind == ZSSBlockInteractions.ExplosionKind.NORMAL) return false;
            level.destroyBlock(pos, true, source); return true;
        }
    }

    public static boolean link(ServerLevel level, BlockPos pos, DungeonType dungeonType) {
        return level.getBlockEntity(pos) instanceof StageNineBlockEntities.DungeonCore core && core.setDungeonType(dungeonType);
    }

    public static boolean setSealed(ServerLevel level, BlockPos pos, boolean sealed) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(Core.SEALED)) level.setBlock(pos, state.setValue(Core.SEALED, sealed), Block.UPDATE_ALL);
        else if (state.hasProperty(Stone.SEALED)) level.setBlock(pos, state.setValue(Stone.SEALED, sealed), Block.UPDATE_ALL);
        else return false;
        return true;
    }
}
