package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

import java.util.Locale;

@SuppressWarnings("deprecation")
public final class SacredFlameBlock extends BaseEntityBlock {
    public static final BooleanProperty EXTINGUISHED = BooleanProperty.create("extinguished");
    private static final long RESET_TICKS = 24000L;
    private static final VoxelShape OUTLINE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    private final FlameType flameType;

    public SacredFlameBlock(Properties properties, FlameType flameType) {
        super(properties); this.flameType = flameType;
        registerDefaultState(stateDefinition.any().setValue(EXTINGUISHED, false));
    }
    public FlameType flameType() { return flameType; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(EXTINGUISHED); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return OUTLINE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StageNineBlockEntities.SacredFlame(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ZSSRegistries.SACRED_FLAME_BLOCK_ENTITY.get() ? null : (tickerLevel, pos, tickerState, blockEntity) -> {
            StageNineBlockEntities.SacredFlame flame = (StageNineBlockEntities.SacredFlame) blockEntity;
            if (tickerState.getValue(EXTINGUISHED) && flame.resetAt() > 0 && tickerLevel.getGameTime() >= flame.resetAt()) {
                tickerLevel.setBlock(pos, tickerState.setValue(EXTINGUISHED, false), Block.UPDATE_ALL); flame.clearReset();
            }
        };
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (state.getValue(EXTINGUISHED)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.flame.extinguished"));
            return InteractionResult.CONSUME;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack held = player.getItemInHand(hand);
        FlameType type = flameType;
        ItemStack replacement = ItemStack.EMPTY;
        if (held.is(ZSSRegistries.getItem("empty_spirit_crystal"))) replacement = new ItemStack(ZSSRegistries.getItem(type.crystal));
        else if (held.is(Items.ARROW)) replacement = new ItemStack(ZSSRegistries.getItem(type.arrow), held.getCount());
        else if (held.getItem() instanceof ZSSBlockInteractions.SacredFlameReceiver receiver
                && receiver.receiveFlame(held, (ServerLevel) level, serverPlayer, type.apiType)) {
            if (held.getItem() instanceof zeldaswordskills_remastered.item.ZeldaCombatItems.Sword)
                ZSSAdvancementService.swordProgress(serverPlayer, "sword.flame");
            extinguish((ServerLevel) level, pos, state); return InteractionResult.CONSUME;
        }
        if (replacement.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.flame.unaffected"));
            return InteractionResult.CONSUME;
        }
        player.setItemInHand(hand, replacement);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1, 1);
        extinguish((ServerLevel) level, pos, state);
        return InteractionResult.CONSUME;
    }
    private static void extinguish(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(EXTINGUISHED, true), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof StageNineBlockEntities.SacredFlame flame) flame.extinguish(level.getGameTime() + RESET_TICKS);
    }

    public enum FlameType implements StringRepresentable {
        DIN("din_crystal", "fire_arrow", ZSSBlockInteractions.SacredFlame.DIN),
        FARORE("farore_crystal", "light_arrow", ZSSBlockInteractions.SacredFlame.FARORE),
        NAYRU("nayru_crystal", "ice_arrow", ZSSBlockInteractions.SacredFlame.NAYRU);
        private final String crystal, arrow;
        private final ZSSBlockInteractions.SacredFlame apiType;
        FlameType(String crystal, String arrow, ZSSBlockInteractions.SacredFlame apiType) {
            this.crystal = crystal; this.arrow = arrow; this.apiType = apiType;
        }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
}
