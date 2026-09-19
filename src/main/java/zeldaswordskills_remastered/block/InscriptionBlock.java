package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.item.InstrumentItem;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.song.SongCatalog;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

import java.util.ArrayList;

@SuppressWarnings("deprecation")
public final class InscriptionBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = DirectionalBlock.FACING;
    public InscriptionBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StageNineBlockEntities.Inscription(pos, state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace()); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> box(2, 15, 2, 14, 16, 14); case UP -> box(2, 0, 2, 14, 1, 14);
            case NORTH -> box(2, 2, 15, 14, 14, 16); case SOUTH -> box(2, 2, 0, 14, 14, 1);
            case WEST -> box(15, 2, 2, 16, 14, 14); case EAST -> box(0, 2, 2, 1, 14, 14);
        };
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof StageNineBlockEntities.Inscription inscription)) return InteractionResult.CONSUME;
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && held.is(ZSSRegistries.getItem("book_of_mudora"))) {
            var songs = new ArrayList<>(SongCatalog.all().keySet());
            int next = (songs.indexOf(inscription.songId()) + 1) % songs.size();
            boolean changed = inscription.setSong(player, songs.get(next));
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(changed
                    ? "message.zeldaswordskills_remastered.inscription.changed" : "message.zeldaswordskills_remastered.inscription.denied"));
            return InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof InstrumentItem && player instanceof ServerPlayer serverPlayer) {
            ZSSCapabilities.get(serverPlayer).ifPresent(data -> {
                if (data.learnSong(inscription.songId()))
                    ZSSAdvancementService.songLearned(serverPlayer, inscription.songId(), data.songs().size());
                ZSSNetwork.syncPlayerData(serverPlayer);
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.inscription.learned",
                        net.minecraft.network.chat.Component.translatable("song.zeldaswordskills_remastered." + inscription.songId().getPath())));
            });
            return InteractionResult.CONSUME;
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.inscription.instrument"));
        return InteractionResult.CONSUME;
    }
}
