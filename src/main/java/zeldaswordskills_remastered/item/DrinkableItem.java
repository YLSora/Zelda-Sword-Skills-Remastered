package zeldaswordskills_remastered.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

/** Potions and milk share one consumption pipeline, including correct bottle return. */
public final class DrinkableItem extends Item {
    private static final String SERVINGS_TAG = "servings";
    private final Kind kind;

    public DrinkableItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public int getUseDuration(ItemStack stack) { return 32; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (!(living instanceof ServerPlayer player)) return stack;
        if (kind == Kind.MAGIC_CONTAINER) {
            boolean changed = ZSSCapabilities.get(player).map(data -> {
                boolean increased = data.increaseMaxMagic(10.0F);
                boolean restored = data.restoreMagic(Float.MAX_VALUE);
                if (increased || restored) ZSSNetwork.syncPlayerData(player);
                if (increased) ZSSAdvancementService.magicChanged(player, data.maxMagic());
                return increased || restored;
            }).orElse(false);
            if (changed) {
                player.playSound(SoundEvents.GENERIC_DRINK, 0.5F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                    if (stack.isEmpty()) return bottle;
                    if (!player.getInventory().add(bottle)) player.drop(bottle, false);
                }
            }
            return stack;
        }
        apply(player);
        player.playSound(SoundEvents.GENERIC_DRINK, 0.5F, 1.0F);
        if (kind.multiServing()) {
            int remaining = stack.hasTag() ? stack.getTag().getInt(SERVINGS_TAG) : 2;
            if (remaining > 1) {
                stack.getOrCreateTag().putInt(SERVINGS_TAG, remaining - 1);
                return stack;
            }
        }
        if (player.getAbilities().instabuild) return stack;
        stack.shrink(1);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) return bottle;
        if (!player.getInventory().add(bottle)) player.drop(bottle, false);
        return stack;
    }

    private void apply(ServerPlayer player) {
        switch (kind) {
            case RED -> player.heal(20.0F);
            case GREEN -> restoreMagic(player, 50.0F);
            case BLUE -> { player.heal(10.0F); restoreMagic(player, 25.0F); }
            case YELLOW -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 1));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200));
                player.getPersistentData().putLong("zss_yellow_protection_until", player.level().getGameTime() + 1200L);
            }
            case PURPLE -> {
                player.heal(20.0F);
                restoreMagic(player, 50.0F);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 3));
            }
            case MILK -> { player.heal(5.0F); restoreMagic(player, 5.0F); }
            case SPECIAL -> {
                player.heal(20.0F);
                restoreMagic(player, 50.0F);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 3));
            }
        }
    }

    private static void restoreMagic(ServerPlayer player, float amount) {
        ZSSCapabilities.get(player).ifPresent(data -> {
            if (data.restoreMagic(amount)) ZSSNetwork.syncPlayerData(player);
        });
    }

    public enum Kind {
        RED(false), GREEN(false), BLUE(false), YELLOW(false), PURPLE(false), MILK(true), SPECIAL(true), MAGIC_CONTAINER(false);
        private final boolean multiServing;
        Kind(boolean multiServing) { this.multiServing = multiServing; }
        public boolean multiServing() { return multiServing; }
    }
}
