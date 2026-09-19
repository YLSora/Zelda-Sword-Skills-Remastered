package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.item.InstrumentItem;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;

@SuppressWarnings("deprecation") // Minecraft 1.20.1 marks Block overrides deprecated because callers dispatch through BlockState.
public final class WarpStoneBlock extends Block implements ZSSBlockInteractions.Liftable, ZSSBlockInteractions.Smashable {
    private final WarpSong warpSong;

    public WarpStoneBlock(Properties properties, WarpSong warpSong) {
        super(properties); this.warpSong = warpSong;
    }
    public WarpSong warpSong() { return warpSong; }

    @Override public ZSSBlockInteractions.Weight liftWeight(BlockState state) {
        return ZSSBlockInteractions.Weight.IMPOSSIBLE;
    }

    @Override public ZSSBlockInteractions.Weight smashWeight(BlockState state) {
        return ZSSBlockInteractions.Weight.IMPOSSIBLE;
    }

    @Override public InteractionResult smash(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                              ItemStack tool, ZSSBlockInteractions.Weight strength, Direction face) {
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof InstrumentItem)) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ZSSCapabilities.get(serverPlayer).ifPresent(data -> {
                boolean learned = data.learnSong(warpSong.songId());
                data.setWarpPoint(warpSong.songId(), level.dimension().location(), pos);
                ZSSNetwork.syncPlayerData(serverPlayer);
                if (learned) ZSSAdvancementService.songLearned(serverPlayer, warpSong.songId(), data.songs().size());
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        learned ? "message.zeldaswordskills_remastered.inscription.learned" : "message.zeldaswordskills_remastered.warp_saved",
                        net.minecraft.network.chat.Component.translatable("song.zeldaswordskills_remastered."
                                + warpSong.songId().getPath())));
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public enum WarpSong implements StringRepresentable {
        BOLERO(ZSSContentIds.BOLERO), MINUET(ZSSContentIds.MINUET), PRELUDE(ZSSContentIds.PRELUDE),
        OATH(ZSSContentIds.OATH), NOCTURNE(ZSSContentIds.NOCTURNE), REQUIEM(ZSSContentIds.REQUIEM),
        SERENADE(ZSSContentIds.SERENADE);

        private final ResourceLocation songId;

        WarpSong(ResourceLocation songId) { this.songId = songId; }
        public ResourceLocation songId() { return songId; }
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
}
