package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.song.SongBlockListener;
import zeldaswordskills_remastered.song.SongDefinition;

import java.util.Locale;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

@SuppressWarnings("deprecation")
public final class MechanismBlocks {
    private MechanismBlocks() {}

    public static final class Beam extends RotatedPillarBlock implements ZSSBlockInteractions.Hookable {
        private static final VoxelShape X = box(0, 4, 4, 16, 12, 12);
        private static final VoxelShape Y = box(4, 0, 4, 12, 16, 12);
        private static final VoxelShape Z = box(4, 4, 0, 12, 12, 16);

        public Beam(Properties properties) { super(properties); }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
        }

        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return switch (state.getValue(AXIS)) { case X -> X; case Y -> Y; case Z -> Z; };
        }

        @Override public boolean canHook(BlockState state, Direction face) { return face.getAxis() != state.getValue(AXIS); }
    }

    public static final class Heavy extends Block implements ZSSBlockInteractions.Liftable, ZSSBlockInteractions.Smashable {
        private final ZSSBlockInteractions.Weight weight;
        public Heavy(Properties properties, ZSSBlockInteractions.Weight weight) { super(properties); this.weight = weight; }
        @Override public ZSSBlockInteractions.Weight liftWeight(BlockState state) { return weight; }
        @Override public ZSSBlockInteractions.Weight smashWeight(BlockState state) { return weight; }
        @Override public InteractionResult smash(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                                  ItemStack tool, ZSSBlockInteractions.Weight strength, Direction face) {
            if (!strength.allows(weight)) return InteractionResult.FAIL;
            level.destroyBlock(pos, false, player);
            ZSSAdvancementService.hammerProgress(player, false, true, false, weight == ZSSBlockInteractions.Weight.VERY_HEAVY);
            return InteractionResult.SUCCESS;
        }
    }

    public static final class HookTarget extends DirectionalBlock implements ZSSBlockInteractions.Hookable {
        private final boolean allFaces;
        public HookTarget(Properties properties, boolean allFaces) {
            super(properties); this.allFaces = allFaces; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getClickedFace());
        }
        @Override public boolean canHook(BlockState state, Direction hitFace) {
            return allFaces || hitFace == state.getValue(FACING);
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    }

    public static final class Peg extends Block implements ZSSBlockInteractions.Hookable, ZSSBlockInteractions.Smashable {
        public static final IntegerProperty HITS = IntegerProperty.create("hits", 0, 3);
        private final ZSSBlockInteractions.Weight weight;
        private static final VoxelShape[] SHAPES = {
                box(4, 0, 4, 12, 13, 12), box(4, 0, 4, 12, 10, 12),
                box(4, 0, 4, 12, 7, 12), box(4, 0, 4, 12, 4, 12)};

        public Peg(Properties properties, ZSSBlockInteractions.Weight weight) {
            super(properties); this.weight = weight; registerDefaultState(stateDefinition.any().setValue(HITS, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HITS); }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[state.getValue(HITS)]; }
        @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(HITS) == 3 ? Shapes.empty() : SHAPES[state.getValue(HITS)];
        }
        @Override public boolean canHook(BlockState state, Direction face) { return face.getAxis().isHorizontal() && state.getValue(HITS) < 3; }
        @Override public ZSSBlockInteractions.Weight smashWeight(BlockState state) { return weight; }
        @Override public InteractionResult smash(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                                  ItemStack tool, ZSSBlockInteractions.Weight strength, Direction face) {
            if (face != Direction.UP || !strength.allows(weight)) return InteractionResult.FAIL;
            int impact = Math.max(1, strength.ordinal() - weight.ordinal() + 1);
            int hits = state.getValue(HITS);
            if (hits == 3 && impact > 1) level.destroyBlock(pos, false, player);
            else {
                level.setBlock(pos, state.setValue(HITS, Math.min(3, hits + impact)), Block.UPDATE_ALL);
                level.scheduleTick(pos, this, 60);
            }
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, weight.ordinal() < 2 ? 1.3F : 0.8F);
            ZSSAdvancementService.hammerProgress(player, true, false, state.is(ZSSRegistries.PEG_RUSTY.get()), false);
            return InteractionResult.SUCCESS;
        }
        @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            int hits = state.getValue(HITS);
            if (hits > 0) {
                level.setBlock(pos, state.setValue(HITS, hits - 1), Block.UPDATE_ALL);
                if (hits > 1) level.scheduleTick(pos, this, 60);
            }
        }
    }

    public static final class QuakeStone extends Block implements ZSSBlockInteractions.QuakeReactive, ZSSBlockInteractions.Smashable {
        public QuakeStone(Properties properties) { super(properties); }
        @Override public boolean onQuake(ServerLevel level, BlockPos pos, BlockState state, @Nullable ServerPlayer source) {
            level.destroyBlock(pos, true, source); return true;
        }
        @Override public ZSSBlockInteractions.Weight smashWeight(BlockState state) { return ZSSBlockInteractions.Weight.IMPOSSIBLE; }
        @Override public InteractionResult smash(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                                  ItemStack tool, ZSSBlockInteractions.Weight strength, Direction face) { return InteractionResult.FAIL; }
    }

    public static final class SecretStone extends Block implements ZSSBlockInteractions.Explodable, ZSSBlockInteractions.Liftable,
            ZSSBlockInteractions.Smashable {
        public static final BooleanProperty UNBREAKABLE = BooleanProperty.create("unbreakable");
        private final SecretVariant variant;
        public SecretStone(Properties properties, SecretVariant variant) {
            super(properties); this.variant = variant;
            registerDefaultState(stateDefinition.any().setValue(UNBREAKABLE, false));
        }
        public SecretVariant variant() { return variant; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(UNBREAKABLE); }
        @Override public boolean onZssExplosion(ServerLevel level, BlockPos pos, BlockState state,
                                                ZSSBlockInteractions.ExplosionKind kind, @Nullable ServerPlayer source) {
            if (state.getValue(UNBREAKABLE) || kind == ZSSBlockInteractions.ExplosionKind.NORMAL) return false;
            level.setBlock(pos, variant.replacement().defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        @Override public ZSSBlockInteractions.Weight liftWeight(BlockState state) {
            return state.getValue(UNBREAKABLE) ? ZSSBlockInteractions.Weight.IMPOSSIBLE : ZSSBlockInteractions.Weight.VERY_HEAVY;
        }
        @Override public ZSSBlockInteractions.Weight smashWeight(BlockState state) { return liftWeight(state); }
        @Override public InteractionResult smash(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                                  ItemStack tool, ZSSBlockInteractions.Weight strength, Direction face) {
            if (!strength.allows(smashWeight(state))) return InteractionResult.FAIL;
            level.setBlock(pos, variant.replacement().defaultBlockState(), Block.UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
    }

    public enum SecretVariant implements StringRepresentable {
        STONE(net.minecraft.world.level.block.Blocks.STONE), SANDSTONE(net.minecraft.world.level.block.Blocks.SANDSTONE),
        NETHER_BRICKS(net.minecraft.world.level.block.Blocks.NETHER_BRICKS), STONE_BRICKS(net.minecraft.world.level.block.Blocks.STONE_BRICKS),
        MOSSY_COBBLESTONE(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE), ICE(net.minecraft.world.level.block.Blocks.ICE),
        COBBLESTONE(net.minecraft.world.level.block.Blocks.COBBLESTONE), END_STONE(net.minecraft.world.level.block.Blocks.END_STONE),
        NETHER_WART_BLOCK(net.minecraft.world.level.block.Blocks.NETHER_WART_BLOCK),
        PURPUR_BLOCK(net.minecraft.world.level.block.Blocks.PURPUR_BLOCK),
        END_STONE_BRICKS(net.minecraft.world.level.block.Blocks.END_STONE_BRICKS);
        private final Block replacement;
        SecretVariant(Block replacement) { this.replacement = replacement; }
        public Block replacement() { return replacement; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final class TimeBlock extends Block implements SongBlockListener {
        public static final BooleanProperty ETHEREAL = BooleanProperty.create("ethereal");
        private final ResourceLocation requiredSong;
        public TimeBlock(Properties properties, ResourceLocation requiredSong) {
            super(properties); this.requiredSong = requiredSong; registerDefaultState(stateDefinition.any().setValue(ETHEREAL, false));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ETHEREAL); }
        @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(ETHEREAL) ? Shapes.empty() : Shapes.block();
        }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(ETHEREAL) ? Shapes.empty() : Shapes.block();
        }
        @Override public boolean onSongPlayed(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                              SongDefinition song, int strength, int affected) {
            if (strength < 5 || !song.id().equals(requiredSong)) return false;
            level.setBlock(pos, state.cycle(ETHEREAL), Block.UPDATE_ALL);
            if (affected == 0) level.playSound(null, pos, ZSSRegistries.SECRET_MEDLEY.get(), SoundSource.BLOCKS, 1, 1);
            return true;
        }
    }

    public static final class GiantLever extends DirectionalBlock implements ZSSBlockInteractions.WhipActivatable {
        public static final BooleanProperty POWERED = BooleanProperty.create("powered");
        public GiantLever(Properties properties) {
            super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, POWERED); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getClickedFace());
        }
        @Override public boolean activateByWhip(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, int whipLevel) {
            level.setBlock(pos, state.cycle(POWERED), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, state.getValue(POWERED) ? 0.5F : 0.6F);
            return true;
        }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            return InteractionResult.PASS;
        }
        @Override public boolean isSignalSource(BlockState state) { return true; }
        @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return state.getValue(POWERED) ? 15 : 0; }
        @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
            return state.getValue(POWERED) ? super.getDestroyProgress(state, player, level, pos) : 0;
        }
    }

    public static final class AncientTablet extends HorizontalDirectionalBlock implements ZSSBlockInteractions.Hookable {
        public enum Type { BOMBOS("bombos_medallion"), ETHER("ether_medallion"), QUAKE("quake_medallion");
            private final String reward; Type(String reward) { this.reward = reward; } }
        private static final VoxelShape NS = box(2, 0, 6, 14, 15, 10);
        private static final VoxelShape EW = box(6, 0, 2, 10, 15, 14);
        private final Type type;
        public AncientTablet(Properties properties, Type type) {
            super(properties); this.type = type; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? EW : NS;
        }
        @Override public boolean canHook(BlockState state, Direction face) { return face.getAxis().isHorizontal(); }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            ServerPlayer serverPlayer = (ServerPlayer) player;
            if (level.getMaxLocalRawBrightness(pos.above()) < 8) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.tablet.dark"));
                return InteractionResult.CONSUME;
            }
            if (!player.getItemInHand(hand).is(ZSSRegistries.getItem("book_of_mudora"))) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.tablet.unknown"));
                return InteractionResult.CONSUME;
            }
            boolean masterSword = player.getInventory().contains(new ItemStack(ZSSRegistries.MASTER_SWORD.get()))
                    || player.getInventory().contains(new ItemStack(ZSSRegistries.TRUE_MASTER_SWORD.get()));
            if (!masterSword) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.tablet.master_sword"));
                return InteractionResult.CONSUME;
            }
            for (int dy = 1; dy <= 5; dy++) if (!level.getBlockState(pos.above(dy)).isAir()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.tablet.confined"));
                return InteractionResult.CONSUME;
            }
            popResource(level, pos.above(5), new ItemStack(ZSSRegistries.getItem(type.reward)));
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1, 1);
            return InteractionResult.CONSUME;
        }
    }
}
