package zeldaswordskills_remastered.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.entity.projectile.ThrownBomb;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class BombItem extends Item {
    private final ThrownBomb.BombKind kind;
    public BombItem(ThrownBomb.BombKind kind, Properties properties) { super(properties); this.kind = kind; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer && storeInBag(serverPlayer, stack))
            return InteractionResultHolder.consume(stack);
        level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.7F, 0.7F);
        if (!level.isClientSide) {
            ThrownBomb bomb = new ThrownBomb(level, player, kind);
            bomb.setItem(stack);
            bomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.9F, 2.0F);
            level.addFreshEntity(bomb);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        BombBagItem.addSharedCooldown(player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private boolean storeInBag(ServerPlayer player, ItemStack bomb) {
        String type = switch (kind) { case STANDARD -> "standard_bomb"; case FIRE -> "fire_bomb"; case WATER -> "water_bomb"; };
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack bag = player.getInventory().getItem(slot);
            if (!(bag.getItem() instanceof BombBagItem)) continue;
            ItemStack single = bomb.copyWithCount(1);
            if (!BombBagItem.add(bag, single)) return false;
            if (!player.getAbilities().instabuild) bomb.shrink(1);
            return true;
        }
        return false;
    }
}
