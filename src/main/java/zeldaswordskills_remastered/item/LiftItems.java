package zeldaswordskills_remastered.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

public final class LiftItems {
    private LiftItems() {}

    public static final class Gauntlet extends Item {
        private final ZSSBlockInteractions.Weight strength;
        public Gauntlet(ZSSBlockInteractions.Weight strength, Properties properties) { super(properties); this.strength = strength; }

        @Override public InteractionResult useOn(UseOnContext context) {
            if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level))
                return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ZSSBlockInteractions.Liftable liftable) || !strength.allows(liftable.liftWeight(state))) return InteractionResult.PASS;
            ItemStack carried = new ItemStack(ZSSRegistries.getItem("held_block"));
            CompoundTag tag = carried.getOrCreateTag();
            tag.put("block_state", NbtUtils.writeBlockState(state));
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) tag.put("block_entity", blockEntity.saveWithFullMetadata());
            liftable.onLifted(level, pos, state, player, carried);
            level.removeBlock(pos, false);
            if (!player.getInventory().add(carried)) player.drop(carried, false);
            if (state.getBlock() instanceof zeldaswordskills_remastered.block.MechanismBlocks.Heavy)
                ZSSAdvancementService.hammerProgress(player, false, true, false,
                        liftable.liftWeight(state) == ZSSBlockInteractions.Weight.VERY_HEAVY);
            return InteractionResult.CONSUME;
        }
    }

    public static final class HeldBlock extends Item {
        public HeldBlock(Properties properties) { super(properties); }

        @Override public InteractionResult useOn(UseOnContext context) {
            if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level))
                return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            ItemStack stack = context.getItemInHand();
            if (!stack.hasTag() || !stack.getTag().contains("block_state", Tag.TAG_COMPOUND)) return InteractionResult.FAIL;
            BlockPos target = context.getClickedPos().relative(context.getClickedFace());
            if (!level.getBlockState(target).canBeReplaced()) return InteractionResult.FAIL;
            if (!restore(level, target, stack)) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResult.CONSUME;
        }

        public static boolean restore(ServerLevel level, BlockPos target, ItemStack stack) {
            if (!stack.hasTag() || !stack.getTag().contains("block_state", Tag.TAG_COMPOUND)
                    || !level.getBlockState(target).canBeReplaced()) return false;
            BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stack.getTag().getCompound("block_state"));
            if (state.isAir() || !level.setBlock(target, state, 3)) return false;
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (stack.getTag().contains("block_entity", Tag.TAG_COMPOUND) && blockEntity != null) {
                CompoundTag entityTag = stack.getTag().getCompound("block_entity").copy();
                entityTag.putInt("x", target.getX()); entityTag.putInt("y", target.getY()); entityTag.putInt("z", target.getZ());
                blockEntity.load(entityTag);
                blockEntity.setChanged();
            }
            return true;
        }

        public static void settleInventory(ServerPlayer player) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!(stack.getItem() instanceof HeldBlock)) continue;
                BlockPos base = player.blockPosition();
                for (BlockPos candidate : new BlockPos[]{base, base.above(), base.north(), base.south(), base.east(), base.west()}) {
                    if (restore(player.serverLevel(), candidate, stack)) { stack.shrink(1); break; }
                }
            }
        }
    }
}
