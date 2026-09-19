package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@SuppressWarnings("deprecation")
public final class CeramicJarBlock extends BaseEntityBlock implements ZSSBlockInteractions.Hookable,
        ZSSBlockInteractions.Liftable, ZSSBlockInteractions.QuakeReactive, ZSSBlockInteractions.Smashable,
        ZSSBlockInteractions.Explodable {
    private static final VoxelShape SHAPE = box(4, 0, 4, 12, 12, 12);

    public CeramicJarBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StageNineBlockEntities.Storage(pos, state); }
    @Override public boolean canHook(BlockState state, Direction face) { return true; }
    @Override public boolean hookBreaks(BlockState state, Direction face) { return true; }
    @Override public ZSSBlockInteractions.Weight liftWeight(BlockState state) { return ZSSBlockInteractions.Weight.VERY_LIGHT; }
    @Override public ZSSBlockInteractions.Weight smashWeight(BlockState state) { return ZSSBlockInteractions.Weight.VERY_LIGHT; }

    @Override public void onLifted(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, ItemStack carrier) {
        if (!(level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage)) return;
        carrier.getOrCreateTag().put("BlockEntityTag", storage.saveWithoutMetadata());
        storage.clearContent();
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) {
            ItemStack jar = new ItemStack(ZSSRegistries.CERAMIC_JAR_ITEM.get());
            CompoundTag blockEntityTag = storage.saveWithoutMetadata();
            jar.getOrCreateTag().put("BlockEntityTag", blockEntityTag);
            storage.clearContent();
            level.removeBlock(pos, false);
            if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, jar);
            else if (!player.getInventory().add(jar)) player.drop(jar, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag != null && level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) storage.load(tag);
    }

    @Override public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide) breakJar((ServerLevel) level, hit.getBlockPos(), state);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) storage.dropContents();
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override public boolean onQuake(ServerLevel level, BlockPos pos, BlockState state, @Nullable ServerPlayer source) { return breakJar(level, pos, state); }
    @Override public boolean onZssExplosion(ServerLevel level, BlockPos pos, BlockState state,
                                            ZSSBlockInteractions.ExplosionKind kind, @Nullable ServerPlayer source) { return breakJar(level, pos, state); }
    @Override public InteractionResult smash(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                              ItemStack tool, ZSSBlockInteractions.Weight strength, Direction face) {
        return strength.allows(smashWeight(state)) && breakJar(level, pos, state) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private boolean breakJar(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).is(this)) return false;
        level.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, .8F, 1F);
        level.destroyBlock(pos, false);
        return true;
    }
}
