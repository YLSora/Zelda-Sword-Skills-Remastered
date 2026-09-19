package zeldaswordskills_remastered.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.entity.NaviService;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.MagicMirrorService;

import javax.annotation.Nullable;
import java.util.List;

public final class SpecialItems {
    private SpecialItems() {}

    public static final class MagicMirror extends Item {
        public MagicMirror(Properties properties) { super(properties); }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
        @Override public int getUseDuration(ItemStack stack) { return MagicMirrorService.CHARGE_TICKS; }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!MagicMirrorService.canUse(player)) return InteractionResultHolder.fail(player.getItemInHand(hand));
            player.startUsingItem(hand); return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
            if (living instanceof ServerPlayer player) MagicMirrorService.start(player);
            return stack;
        }
    }

    public static final class FairyBottle extends Item {
        public FairyBottle(Properties properties) { super(properties); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getHealth() >= player.getMaxHealth()) return InteractionResultHolder.fail(stack);
            if (!level.isClientSide) {
                player.heal(10.0F);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        public static boolean releaseFairy(ServerPlayer player, DamageSource source) {
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty() && stack.getItem() instanceof FairyBottle) {
                    ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
                    stack.shrink(1);
                    if (stack.isEmpty()) player.getInventory().setItem(slot, empty);
                    else if (!player.getInventory().add(empty)) player.drop(empty, false);
                    player.setHealth(Math.min(player.getMaxHealth(), 10.0F));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 9));
                    player.sendSystemMessage(Component.translatable("message.zeldaswordskills_remastered.fairy_revive"));
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A bottle holding the player's Navi. Capturing the fairy stores nothing but the player's
     * stable Navi identity, so releasing it always restores the same fairy rather than a copy.
     * Right-clicking an owned Navi with an empty vanilla bottle fills this item; using the filled
     * bottle releases the fairy again.
     */
    public static final class NaviBottle extends Item {
        public NaviBottle(Properties properties) { super(properties); }

        /**
         * Bottles {@code navi} into the player's held hand. The fairy is only marked inactive; its
         * UUID survives on the player so the same Navi can be released later.
         */
        public static InteractionResult capture(ServerPlayer player, NaviCreature navi, InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Items.GLASS_BOTTLE)) return InteractionResult.PASS;
            if (!navi.isAlive() || navi.level() != player.level()) return InteractionResult.FAIL;
            if (!player.getUUID().equals(navi.ownerUuid())
                    || !navi.getUUID().equals(NaviCreature.savedNaviId(player).orElse(null))) {
                player.displayClientMessage(Component.translatable("message.zeldaswordskills_remastered.navi_not_yours"), true);
                return InteractionResult.FAIL;
            }
            NaviService.markActive(player, false);
            navi.discard();
            ItemStack bottled = new ItemStack(ZSSRegistries.NAVI_BOTTLE.get());
            player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(held, player, bottled));
            player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            if (NaviService.hasLoadedEntity(serverPlayer)) {
                serverPlayer.displayClientMessage(Component.translatable("message.zeldaswordskills_remastered.navi_already_out"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (NaviService.summon(serverPlayer) == null) {
                serverPlayer.displayClientMessage(Component.translatable("message.zeldaswordskills_remastered.navi_no_space"), true);
                return InteractionResultHolder.fail(stack);
            }
            level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.zeldaswordskills_remastered.navi_bottle").withStyle(ChatFormatting.AQUA));
        }
    }

    public static final class LinksHouse extends Item {
        public LinksHouse(Properties properties) { super(properties); }
        @Override public InteractionResult useOn(UseOnContext context) {
            if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            BlockPos origin = context.getClickedPos().relative(context.getClickedFace()).offset(-3, 0, -3);
            for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(6, 4, 6))) if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
            for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
                level.setBlock(origin.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                level.setBlock(origin.offset(x, 4, z), Blocks.OAK_SLAB.defaultBlockState(), 3);
            }
            for (int y = 1; y < 4; y++) for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
                if (x != 0 && x != 6 && z != 0 && z != 6) continue;
                if (z == 0 && x == 3 && y < 3) continue;
                level.setBlock(origin.offset(x, y, z), Blocks.OAK_LOG.defaultBlockState(), 3);
            }
            furnish(level, origin);
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }

        private static void furnish(ServerLevel level, BlockPos origin) {
            // Install both halves before shape updates can remove an incomplete door or bed.
            var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
            level.setBlock(origin.offset(3, 1, 0), door, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(origin.offset(3, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);

            var pane = Blocks.GLASS_PANE.defaultBlockState()
                    .setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
            for (int x = 2; x <= 4; x++) for (int y = 2; y <= 3; y++) {
                level.setBlock(origin.offset(x, y, 6), pane, Block.UPDATE_ALL);
            }
            var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
            level.setBlock(origin.offset(2, 1, 5), bed, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(origin.offset(3, 1, 5), bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);

            BlockPos chestPos = origin.offset(5, 1, 4);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                List<Item> supplies = List.of(ZSSRegistries.getItem("hero_tunic_helmet"),
                        ZSSRegistries.getItem("hero_tunic_chestplate"), ZSSRegistries.getItem("hero_tunic_leggings"),
                        ZSSRegistries.getItem("hero_tunic_boots"), ZSSRegistries.KOKIRI_SWORD.get(),
                        ZSSRegistries.DEKU_SHIELD.get(), ZSSRegistries.NAVI_BOTTLE.get());
                for (int slot = 0; slot < supplies.size(); slot++) chest.setItem(slot, new ItemStack(supplies.get(slot)));
                chest.setChanged();
            }

            level.setBlock(origin.offset(1, 1, 3), Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(FenceBlock.WEST, true), Block.UPDATE_ALL);
            level.setBlock(origin.offset(1, 2, 3), Blocks.OAK_PRESSURE_PLATE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(origin.offset(2, 1, 3), Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
            level.setBlock(origin.offset(1, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(origin.offset(1, 1, 2), Blocks.FURNACE.defaultBlockState()
                    .setValue(FurnaceBlock.FACING, Direction.EAST), Block.UPDATE_ALL);

            var painting = new Painting(level, origin.offset(1, 2, 4), Direction.EAST,
                    level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.PLANT));
            if (painting.survives()) level.addFreshEntity(painting);
        }
    }
}
