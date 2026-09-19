package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.song.SongBlockListener;
import zeldaswordskills_remastered.song.SongDefinition;

@SuppressWarnings("deprecation")
public class LockedChestBlock extends BaseEntityBlock implements SongBlockListener {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty UNLOCKED = BooleanProperty.create("unlocked");
    public static final BooleanProperty VISIBLE = BooleanProperty.create("visible");
    private static final VoxelShape CHEST = box(1, 0, 1, 15, 14, 15);
    private final boolean initiallyVisible;

    public LockedChestBlock(Properties properties, boolean initiallyVisible) {
        super(properties); this.initiallyVisible = initiallyVisible;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(UNLOCKED, false).setValue(VISIBLE, initiallyVisible));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, UNLOCKED, VISIBLE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return state.getValue(VISIBLE) ? RenderShape.MODEL : RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(VISIBLE) ? CHEST : Shapes.empty();
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(VISIBLE) ? CHEST : Shapes.empty();
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StageNineBlockEntities.Storage(pos, state); }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (!state.getValue(VISIBLE) && !player.isCreative()) return InteractionResult.CONSUME;
        if (!state.getValue(UNLOCKED)) {
            if (!consumeKey(player, hand)) {
                level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, .7F, .8F);
                return InteractionResult.CONSUME;
            }
            state = state.setValue(UNLOCKED, true).setValue(VISIBLE, true);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, .7F, 1.2F);
        }
        if (level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) NetworkHooks.openScreen(serverPlayer, storage, pos);
        return InteractionResult.CONSUME;
    }

    private static boolean consumeKey(Player player, InteractionHand hand) {
        ItemStack key = player.getItemInHand(hand);
        if (!key.is(ZSSRegistries.getItem("small_key"))) return false;
        if (!player.getAbilities().instabuild) key.shrink(1);
        return true;
    }

    @Override public boolean onSongPlayed(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                          SongDefinition song, int strength, int affected) {
        if (initiallyVisible || strength < 5 || !song.id().equals(ZSSContentIds.LULLABY)) return false;
        if (!state.getValue(VISIBLE)) {
            level.setBlock(pos, state.setValue(VISIBLE, true).setValue(UNLOCKED, true), Block.UPDATE_ALL);
            if (affected == 0) level.playSound(null, pos, ZSSRegistries.SECRET_MEDLEY.get(), SoundSource.BLOCKS, 1, 1);
        }
        return true;
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) storage.dropContents();
        super.onRemove(state, level, pos, replacement, moved);
    }
    @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(UNLOCKED) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }
}
