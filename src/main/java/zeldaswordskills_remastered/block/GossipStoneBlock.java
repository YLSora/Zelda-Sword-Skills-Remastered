package zeldaswordskills_remastered.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.song.SongBlockListener;
import zeldaswordskills_remastered.song.SongDefinition;

@SuppressWarnings("deprecation")
public final class GossipStoneBlock extends BaseEntityBlock implements SongBlockListener, ZSSBlockInteractions.Hookable,
        ZSSBlockInteractions.Liftable, ZSSBlockInteractions.Smashable {
    public static final BooleanProperty UNBREAKABLE = BooleanProperty.create("unbreakable");
    public GossipStoneBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(UNBREAKABLE, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(UNBREAKABLE); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StageNineBlockEntities.GossipStone(pos, state); }
    @Override public boolean canHook(BlockState state, net.minecraft.core.Direction face) { return true; }
    @Override public ZSSBlockInteractions.Weight liftWeight(BlockState state) { return state.getValue(UNBREAKABLE) ? ZSSBlockInteractions.Weight.IMPOSSIBLE : ZSSBlockInteractions.Weight.MEDIUM; }
    @Override public void onLifted(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, ItemStack carrier) {
        if (level.getBlockEntity(pos) instanceof StageNineBlockEntities.GossipStone stone) {
            carrier.getOrCreateTag().put("BlockEntityTag", stone.saveWithoutMetadata());
        }
    }
    @Override public ZSSBlockInteractions.Weight smashWeight(BlockState state) { return state.getValue(UNBREAKABLE) ? ZSSBlockInteractions.Weight.IMPOSSIBLE : ZSSBlockInteractions.Weight.VERY_HEAVY; }
    @Override public InteractionResult smash(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, ItemStack tool,
                                              ZSSBlockInteractions.Weight strength, net.minecraft.core.Direction face) {
        if (!strength.allows(smashWeight(state))) return InteractionResult.FAIL;
        level.destroyBlock(pos, true, player); return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof StageNineBlockEntities.GossipStone stone)) return InteractionResult.CONSUME;
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && (held.is(Items.WRITABLE_BOOK) || held.is(Items.WRITTEN_BOOK))) {
            String message = readBook(held);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(stone.setMessage(player, message)
                    ? "message.zeldaswordskills_remastered.gossip.saved" : "message.zeldaswordskills_remastered.gossip.denied"));
            return InteractionResult.CONSUME;
        }
        boolean truth = held.is(ZSSRegistries.getItem("mask_of_truth"))
                || player.getInventory().armor.get(3).is(ZSSRegistries.getItem("mask_of_truth"));
        if (!truth) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.gossip.silent"));
        else {
            String message = stone.message();
            player.sendSystemMessage(message.startsWith("message.")
                    ? net.minecraft.network.chat.Component.translatable(message) : net.minecraft.network.chat.Component.literal(message));
        }
        return InteractionResult.CONSUME;
    }
    private static String readBook(ItemStack book) {
        ListTag pages = book.getOrCreateTag().getList("pages", Tag.TAG_STRING);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < pages.size() && text.length() < StageNineBlockEntities.GossipStone.MAX_MESSAGE_LENGTH; i++) {
            if (!text.isEmpty()) text.append(' ');
            String page = pages.getString(i);
            if (page.startsWith("{\"text\":")) {
                try { page = net.minecraft.network.chat.Component.Serializer.fromJson(page).getString(); } catch (Exception ignored) {}
            }
            text.append(page);
        }
        return text.substring(0, Math.min(text.length(), StageNineBlockEntities.GossipStone.MAX_MESSAGE_LENGTH));
    }
    @Override public boolean onSongPlayed(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                          SongDefinition song, int strength, int affected) {
        if (strength < 5 || level.isDay() || !(level.getBlockEntity(pos) instanceof StageNineBlockEntities.GossipStone stone)
                || level.getGameTime() < stone.nextFairySpawn()) return false;
        if (!song.id().equals(ZSSContentIds.STORMS) && !song.id().equals(ZSSContentIds.SUN) && !song.id().equals(ZSSContentIds.LULLABY)) return false;
        Mob fairy = ZSSRegistries.FAIRY.get().create(level);
        if (fairy == null) return false;
        fairy.moveTo(pos.getX() + .5, pos.getY() + 2, pos.getZ() + .5, 0, 0);
        level.addFreshEntity(fairy);
        stone.setNextFairySpawn(level.getGameTime() + 24000L);
        if (affected == 0) level.playSound(null, pos, ZSSRegistries.SECRET_MEDLEY.get(), SoundSource.BLOCKS, 1, 1);
        return true;
    }
}
