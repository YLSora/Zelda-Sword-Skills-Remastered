package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.entity.PedestalBlockEntity;

@SuppressWarnings("deprecation") // Minecraft 1.20.1 marks Block overrides deprecated because callers must dispatch through BlockState.
public final class PedestalBlock extends BaseEntityBlock {
    public static final IntegerProperty PENDANTS = IntegerProperty.create("pendants", 0, 7);
    public static final BooleanProperty UNLOCKED = BooleanProperty.create("unlocked");
    public static final BooleanProperty HAS_SWORD = BooleanProperty.create("has_sword");
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);

    public PedestalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PENDANTS, 0).setValue(UNLOCKED, false).setValue(HAS_SWORD, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal)) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (pedestal.isUnlocked() && !pedestal.hasSword() && PedestalBlockEntity.isAllowedSword(held)) {
            if (!level.isClientSide && pedestal.setSword(held, player) && !player.getAbilities().instabuild) held.shrink(1);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (pedestal.isUnlocked() && pedestal.hasSword()) {
            boolean master = pedestal.sword().is(zeldaswordskills_remastered.registry.ZSSRegistries.MASTER_SWORD.get());
            if (!level.isClientSide && pedestal.retrieveSword() && master && player instanceof ServerPlayer serverPlayer)
                zeldaswordskills_remastered.progression.ZSSAdvancementService.swordProgress(serverPlayer, "sword.master");
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, pedestal, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) {
            pedestal.changeOrientation();
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal ? pedestal.getPowerLevel() : 0;
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        if (level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) pedestal.saveToItem(stack);
        return stack;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) {
            pedestal.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(PENDANTS, UNLOCKED, HAS_SWORD);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PedestalBlockEntity(pos, state);
    }
}
