package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.DungeonType;

@SuppressWarnings("deprecation")
public final class LockedDoorBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = EnumProperty.create("half", DoubleBlockHalf.class);
    public static final BooleanProperty UNLOCKED = BooleanProperty.create("unlocked");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    private static final VoxelShape NORTH = box(0, 0, 13, 16, 16, 16);
    private static final VoxelShape SOUTH = box(0, 0, 0, 16, 16, 3);
    private static final VoxelShape WEST = box(13, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = box(0, 0, 0, 3, 16, 16);
    @Nullable private final DungeonType dungeonType;

    public LockedDoorBlock(Properties properties, @Nullable DungeonType dungeonType) {
        super(properties); this.dungeonType = dungeonType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(UNLOCKED, false).setValue(OPEN, false).setValue(POWERED, false));
    }
    public boolean bossDoor() { return dungeonType != null; }
    public @Nullable DungeonType dungeonType() { return dungeonType; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, UNLOCKED, OPEN, POWERED);
    }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        if (above.getY() >= context.getLevel().getMaxBuildHeight() || !context.getLevel().getBlockState(above).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(OPEN)) facing = facing.getClockWise();
        return switch (facing) { case NORTH -> NORTH; case SOUTH -> SOUTH; case WEST -> WEST; default -> EAST; };
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState lowerState = level.getBlockState(lower);
        if (!lowerState.is(this)) return InteractionResult.CONSUME;
        if (!lowerState.getValue(UNLOCKED)) {
            if (!consumeKey(player, hand) && !consumeKey(player,
                    hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)) {
                level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, .7F, .8F);
                return InteractionResult.CONSUME;
            }
            setPair(level, lower, lowerState, true, true);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, .8F, 1F);
        } else {
            setPair(level, lower, lowerState, true, !lowerState.getValue(OPEN));
            level.playSound(null, pos, lowerState.getValue(OPEN) ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.BLOCKS, .8F, 1F);
        }
        return InteractionResult.CONSUME;
    }
    private boolean consumeKey(Player player, InteractionHand hand) {
        ItemStack key = player.getItemInHand(hand);
        if (key.is(ZSSRegistries.getItem("skeleton_key"))) return true;
        String required = bossDoor() ? "big_key" : "small_key";
        if (!key.is(ZSSRegistries.getItem(required))) return false;
        if (dungeonType != null && !zeldaswordskills_remastered.item.BigKeyItem.dungeon(key).filter(dungeonType::equals).isPresent()) return false;
        if (!player.getAbilities().instabuild) key.shrink(1);
        return true;
    }
    private void setPair(Level level, BlockPos lower, BlockState state, boolean unlocked, boolean open) {
        boolean powered = level.hasNeighborSignal(lower) || level.hasNeighborSignal(lower.above());
        BlockState updated = state.setValue(UNLOCKED, unlocked).setValue(OPEN, open)
                .setValue(POWERED, powered).setValue(HALF, DoubleBlockHalf.LOWER);
        level.setBlock(lower, updated, Block.UPDATE_ALL);
        if (level.getBlockState(lower.above()).is(this)) level.setBlock(lower.above(), updated.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean moved) {
        if (!level.isClientSide && state.getValue(UNLOCKED)) {
            boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
            if (powered != state.getValue(POWERED)) {
                BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                setPair(level, lower, level.getBlockState(lower), true, powered);
            }
        }
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock())) {
            BlockPos other = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this) && otherState.getValue(HALF) != state.getValue(HALF)) level.removeBlock(other, false);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
    @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) { return state.getValue(UNLOCKED) ? super.getDestroyProgress(state, player, level, pos) : 0; }
}
