package zeldaswordskills_remastered.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class SpiritCrystalItem extends Item implements ZSSBlockInteractions.SacredFlameReceiver {
    private static final String RECALL_DIMENSION = "recall_dimension";
    private static final String RECALL_POSITION = "recall_position";
    private final Kind kind;
    public SpiritCrystalItem(Kind kind, Properties properties) { super(kind == Kind.EMPTY ? properties : properties.durability(64)); this.kind = kind; }

    public Kind kind() { return kind; }
    @Override public boolean isFoil(ItemStack stack) { return kind != Kind.EMPTY; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return kind == Kind.FARORE ? UseAnim.BOW : UseAnim.NONE; }
    @Override public int getUseDuration(ItemStack stack) { return kind == Kind.FARORE ? 40 : 0; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || kind == Kind.EMPTY)
            return kind == Kind.EMPTY ? InteractionResultHolder.pass(stack) : InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        if (kind == Kind.FARORE && player.isShiftKeyDown()) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString(RECALL_DIMENSION, level.dimension().location().toString());
            tag.putLong(RECALL_POSITION, player.blockPosition().asLong());
            return InteractionResultHolder.consume(stack);
        }
        if (kind == Kind.FARORE) {
            if (!hasRecall(stack)) return InteractionResultHolder.fail(stack);
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (kind == Kind.DIN) useDin(serverPlayer);
        else useNayru(serverPlayer);
        stack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(hand));
        return InteractionResultHolder.consume(stack);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (kind != Kind.FARORE || !(living instanceof ServerPlayer player) || !hasRecall(stack)) return stack;
        ResourceLocation dimension = ResourceLocation.tryParse(stack.getTag().getString(RECALL_DIMENSION));
        if (dimension == null || !dimension.equals(level.dimension().location())) return stack;
        ServerLevel destination = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (destination == null) return stack;
        BlockPos pos = BlockPos.of(stack.getTag().getLong(RECALL_POSITION));
        if (!safe(destination, pos)) return stack;
        player.teleportTo(destination, pos.getX() + 0.5D, pos.getY() + 0.1D, pos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        player.getCooldowns().addCooldown(this, 40);
        stack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(player.getUsedItemHand()));
        return stack;
    }

    private void useDin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5.0D), entity -> entity != player && entity.isAlive())
                .forEach(entity -> { entity.hurt(player.damageSources().magic(), 32.0F); entity.setSecondsOnFire(5); });
        BlockPos center = player.blockPosition();
        BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 1, 4)).forEach(pos -> {
            BlockPos fire = pos.above();
            if (level.random.nextInt(8) == 0 && level.getBlockState(fire).canBeReplaced()
                    && BaseFireBlock.canBePlacedAt(level, fire, net.minecraft.core.Direction.UP))
                level.setBlockAndUpdate(fire, BaseFireBlock.getState(level, fire));
        });
        player.getCooldowns().addCooldown(this, 60);
    }

    private void useNayru(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 2));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 1));
        player.getCooldowns().addCooldown(this, 40);
    }

    private static boolean hasRecall(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(RECALL_DIMENSION) && stack.getTag().contains(RECALL_POSITION);
    }

    private static boolean safe(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    @Override public boolean receiveFlame(ItemStack stack, ServerLevel level, ServerPlayer player, ZSSBlockInteractions.SacredFlame flame) {
        Kind flameKind = switch (flame) { case DIN -> Kind.DIN; case FARORE -> Kind.FARORE; case NAYRU -> Kind.NAYRU; };
        if (kind != Kind.EMPTY) return false;
        ItemStack charged = new ItemStack(ZSSRegistries.getItem(flameKind.itemId));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!player.getInventory().add(charged)) player.drop(charged, false);
        return true;
    }

    public enum Kind {
        EMPTY("empty_spirit_crystal"), DIN("din_crystal"), FARORE("farore_crystal"), NAYRU("nayru_crystal");
        private final String itemId;
        Kind(String itemId) { this.itemId = itemId; }
    }
}
