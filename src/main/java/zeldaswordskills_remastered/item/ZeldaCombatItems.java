package zeldaswordskills_remastered.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.entity.projectile.ZeldaArrow;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class ZeldaCombatItems {
    private ZeldaCombatItems() {}

    public static final class Sword extends SwordItem implements ZSSBlockInteractions.SacredFlameReceiver {
        private final boolean twoHanded;
        private final boolean masterSword;
        private final boolean sacredFlameSword;
        public Sword(Tier tier, int damage, float speed, boolean twoHanded, boolean masterSword, boolean sacredFlameSword, Properties properties) {
            super(tier, damage, speed, properties); this.twoHanded = twoHanded; this.masterSword = masterSword; this.sacredFlameSword = sacredFlameSword;
        }
        public boolean twoHanded() { return twoHanded; }
        public boolean masterSword() { return masterSword; }
        @Override public void appendHoverText(ItemStack stack, Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            if (stack.is(ZSSRegistries.TEMPERED_SWORD.get())) {
                int kills = stack.hasTag() ? stack.getTag().getInt("zss_kills") : 0;
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "tooltip.zeldaswordskills_remastered.tempered_sword_kills", kills,
                        zeldaswordskills_remastered.progression.AcquisitionService.GOLDEN_SWORD_REQUIRED_KILLS));
            }
        }
        @Override public boolean canBeDepleted() { return !masterSword && super.canBeDepleted(); }
        @Override public boolean isEnchantable(ItemStack stack) { return true; }
        @Override public boolean receiveFlame(ItemStack stack, ServerLevel level, ServerPlayer player, ZSSBlockInteractions.SacredFlame flame) {
            if (!sacredFlameSword) return false;
            String id = "zeldaswordskills_remastered:" + flame.name().toLowerCase(java.util.Locale.ROOT);
            net.minecraft.nbt.ListTag absorbed = stack.getOrCreateTag().getList("sacred_flames", net.minecraft.nbt.Tag.TAG_STRING);
            for (int index = 0; index < absorbed.size(); index++) if (absorbed.getString(index).equals(id)) return false;
            absorbed.add(net.minecraft.nbt.StringTag.valueOf(id));
            stack.getOrCreateTag().put("sacred_flames", absorbed);
            var enchantments = new java.util.LinkedHashMap<>(EnchantmentHelper.getEnchantments(stack));
            var element = switch (flame) {
                case DIN -> Enchantments.FIRE_ASPECT;
                case FARORE -> Enchantments.KNOCKBACK;
                case NAYRU -> Enchantments.MOB_LOOTING;
            };
            enchantments.put(element, Math.max(enchantments.getOrDefault(element, 0),
                    flame == ZSSBlockInteractions.SacredFlame.NAYRU ? 3 : 2));
            int sharpness = enchantments.getOrDefault(Enchantments.SHARPNESS, 0);
            enchantments.put(Enchantments.SHARPNESS, Math.min(Enchantments.SHARPNESS.getMaxLevel(), sharpness + 1));
            EnchantmentHelper.setEnchantments(enchantments, stack);
            return true;
        }
    }

    public static final class Shield extends ShieldItem {
        private final boolean reflective;
        public Shield(boolean reflective, Properties properties) {
            super(properties); this.reflective = reflective;
        }
        public boolean reflective() { return reflective; }
    }

    public static final class Hammer extends SwordItem {
        private final ZSSBlockInteractions.Weight strength;
        private final boolean charged;
        public Hammer(Tier tier, int damage, float speed, ZSSBlockInteractions.Weight strength, boolean charged, Properties properties) {
            super(tier, damage, speed, properties.durability(512)); this.strength = strength; this.charged = charged;
        }
        @Override public InteractionResult useOn(UseOnContext context) {
            if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level))
                return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            InteractionResult result = ZSSBlockInteractions.smash(level, context.getClickedPos(), player, context.getItemInHand(), strength, context.getClickedFace());
            if (result.consumesAction()) context.getItemInHand().hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(context.getHand()));
            return result;
        }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
        @Override public boolean isEnchantable(ItemStack stack) { return true; }
        @Override public int getUseDuration(ItemStack stack) { return charged ? 72000 : 0; }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!charged) return InteractionResultHolder.pass(player.getItemInHand(hand));
            player.startUsingItem(hand); return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int remaining) {
            if (!(living instanceof ServerPlayer player) || !(level instanceof ServerLevel server) || getUseDuration(stack) - remaining < 10) return;
            BlockPos center = player.blockPosition();
            BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4)).forEach(pos -> ZSSBlockInteractions.quake(server, pos, player));
            server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4), entity -> entity != player && entity.isAlive())
                    .forEach(entity -> entity.hurt(player.damageSources().playerAttack(player), 8.0F));
            player.getCooldowns().addCooldown(this, 30);
            stack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(player.getUsedItemHand()));
        }
    }

    public static final class HeroBow extends BowItem {
        public HeroBow(Properties properties) { super(properties.durability(512)); }
        @Override public boolean isEnchantable(ItemStack stack) { return true; }
        public static int upgradeLevel(ItemStack stack) {
            return stack.hasTag() ? net.minecraft.util.Mth.clamp(stack.getTag().getInt("fairy_level"), 1, 3) : 1;
        }

        @Override public void appendHoverText(ItemStack stack, Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.zeldaswordskills_remastered.hero_bow_level", upgradeLevel(stack)));
        }

        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int remaining) {
            if (living instanceof Player player && player.getProjectile(stack).getItem() instanceof ElementArrow arrow) {
                int required = switch (arrow.kind()) { case LIGHT -> 3; case FIRE, ICE -> 2; default -> 1; };
                if (upgradeLevel(stack) < required) {
                    if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.zeldaswordskills_remastered.hero_bow_upgrade_required", required), true);
                    return;
                }
            }
            super.releaseUsing(stack, level, living, remaining);
        }
    }

    public static final class ElementArrow extends ArrowItem {
        private final ArrowKind kind;
        public ElementArrow(ArrowKind kind, Properties properties) { super(properties); this.kind = kind; }
        public ArrowKind kind() { return kind; }
        @Override public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter) {
            EntityType<ZeldaArrow> type = switch (kind) {
                case BOMB, FIRE_BOMB, WATER_BOMB -> ZSSRegistries.ARROW_BOMB.get();
                case FIRE, ICE, LIGHT -> ZSSRegistries.ARROW_ELEMENTAL.get();
            };
            return new ZeldaArrow(type, level, shooter, kind);
        }
    }

    public enum ArrowKind { BOMB, FIRE_BOMB, WATER_BOMB, FIRE, ICE, LIGHT }
}
