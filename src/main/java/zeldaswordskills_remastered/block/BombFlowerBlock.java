package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@SuppressWarnings("deprecation")
public final class BombFlowerBlock extends BushBlock implements ZSSBlockInteractions.Explodable,
        ZSSBlockInteractions.QuakeReactive, ZSSBlockInteractions.Hookable {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    private static final VoxelShape[] SHAPES = { box(3, 0, 3, 13, 4, 13), box(2, 0, 2, 14, 7, 14),
            box(2, 0, 2, 14, 10, 14), box(1, 0, 1, 15, 13, 15) };
    public BombFlowerBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[state.getValue(AGE)]; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AGE) == 0 ? Shapes.empty() : SHAPES[state.getValue(AGE)];
    }
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) { return state.isSolidRender(level, pos); }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!mayPlaceOn(level.getBlockState(pos.below()), level, pos.below())) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) if (!level.getFluidState(pos.below().relative(direction)).isEmpty()
                && level.getFluidState(pos.below().relative(direction)).is(net.minecraft.tags.FluidTags.LAVA)) return true;
        return false;
    }
    @Override public boolean isRandomlyTicking(BlockState state) { return true; }
    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < 3 && random.nextInt(6) == 0) level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        else if (age == 3 && random.nextInt(16) == 0) level.setBlock(pos, defaultBlockState(), Block.UPDATE_ALL);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(AGE) != 3 || !player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            player.setItemInHand(hand, new ItemStack(ZSSRegistries.getItem("standard_bomb")));
            level.setBlock(pos, defaultBlockState(), Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && state.getValue(AGE) == 3) explode((ServerLevel) level, hit.getBlockPos());
    }
    @Override public boolean canHook(BlockState state, Direction face) { return state.getValue(AGE) == 3; }
    @Override public boolean hookBreaks(BlockState state, Direction face) { return state.getValue(AGE) < 2; }
    @Override public boolean onQuake(ServerLevel level, BlockPos pos, BlockState state, @Nullable ServerPlayer source) {
        if (state.getValue(AGE) != 3) return false; explode(level, pos); return true;
    }
    @Override public boolean onZssExplosion(ServerLevel level, BlockPos pos, BlockState state,
                                            ZSSBlockInteractions.ExplosionKind kind, @Nullable ServerPlayer source) {
        if (state.getValue(AGE) != 3) return false; level.scheduleTick(pos, this, 5); return true;
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) == 3) explode(level, pos);
    }
    private void explode(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 3F, Level.ExplosionInteraction.BLOCK);
    }
}
