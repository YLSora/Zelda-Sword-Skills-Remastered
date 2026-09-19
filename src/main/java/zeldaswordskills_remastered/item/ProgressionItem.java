package zeldaswordskills_remastered.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.List;

/** Server-authoritative progression pickups and administrator utilities. */
public final class ProgressionItem extends Item {
    public static final String SKILL_TAG = "skill";
    public static final String CHEST_SKILL_TAG = "zss_chest_skill";
    private final Kind kind;

    public ProgressionItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Tokens are delivered as physical items to Cursed Man.
        if (kind == Kind.SKULLTULA_TOKEN) return InteractionResultHolder.fail(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.sidedSuccess(stack, true);
        boolean changed = apply(serverPlayer, stack);
        if (!changed) return InteractionResultHolder.fail(stack);
        if (!serverPlayer.getAbilities().instabuild && kind != Kind.HEART_PIECE) stack.shrink(1);
        serverPlayer.getCooldowns().addCooldown(this, 5);
        return InteractionResultHolder.consume(stack);
    }

    private boolean apply(ServerPlayer serverPlayer, ItemStack stack) {
        return switch (kind) {
            case SKILL_ORB -> learnSkill(serverPlayer, stack);
            case HEART_PIECE -> assembleHeart(serverPlayer, stack);
            case SKILL_WIPER -> wipeSkills(serverPlayer);
            case SMALL_MAGIC -> magic(serverPlayer, 100.0F);
            case LARGE_MAGIC -> magic(serverPlayer, Float.MAX_VALUE);
            case SKULLTULA_TOKEN -> false;
            case SMALL_HEART -> heal(serverPlayer, 2.0F);
            case POWER_PIECE -> power(serverPlayer);
        };
    }

    public boolean applyOnPickup(ServerPlayer player, ItemStack stack) {
        if (kind == Kind.SMALL_HEART) return consumeSmallHearts(player, stack, true);
        if (kind == Kind.SKILL_ORB || kind == Kind.HEART_PIECE || kind == Kind.SKILL_WIPER
                || kind == Kind.SKULLTULA_TOKEN) return false;
        if (!apply(player, stack)) return false;
        stack.shrink(1);
        return true;
    }

    public static boolean consumeSmallHearts(ServerPlayer player, ItemStack stack) {
        return consumeSmallHearts(player, stack, false);
    }

    public static boolean consumeSmallHearts(ServerPlayer player, ItemStack stack, boolean playSound) {
        if (player.isCreative() || !player.isAlive() || stack.isEmpty()
                || !(stack.getItem() instanceof ProgressionItem item) || item.kind != Kind.SMALL_HEART) return false;
        int count = stack.getCount();
        stack.setCount(0);
        player.heal(2.0F * count);
        if (playSound) {
            // The event identifies the consumed item; each client chooses its own pickup sound.
            player.playNotifySound(ZSSRegistries.GET_HEART.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return true;
    }

    private static boolean learnSkill(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        ResourceLocation skill = ResourceLocation.tryParse(tag.getString(SKILL_TAG));
        if (skill == null) skill = ZSSContentIds.SWORD_BASIC;
        if (!ZSSContentIds.SKILLS.contains(skill)) return false;
        ResourceLocation finalSkill = skill;
        return ZSSCapabilities.get(player).map(data -> {
            int before = data.skills().getOrDefault(finalSkill, 0);
            data.setSkillLevel(finalSkill, before + 1);
            int after = data.skills().getOrDefault(finalSkill, 0);
            if (after == before) {
                player.sendSystemMessage(Component.translatable("message.zeldaswordskills_remastered.skill_max", skillName(finalSkill)));
                return false;
            }
            ZSSNetwork.syncPlayerData(player);
            ZSSAdvancementService.skillLearned(player, finalSkill, after, data.allSkillTypesLearned());
            player.level().playSound(null, player.blockPosition(), ZSSRegistries.LEVEL_UP.get(), player.getSoundSource(), 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("message.zeldaswordskills_remastered.skill_added", skillName(finalSkill), after));
            return true;
        }).orElse(false);
    }

    private static boolean assembleHeart(ServerPlayer player, ItemStack stack) {
        if (stack.getCount() < 4 && !player.getAbilities().instabuild) return false;
        ItemStack orb = new ItemStack(ZSSRegistries.getItem("skill_orb"));
        orb.getOrCreateTag().putString(SKILL_TAG, ZSSContentIds.BONUS_HEART.toString());
        if (!player.getAbilities().instabuild) stack.shrink(4);
        if (!player.getInventory().add(orb)) player.drop(orb, false);
        return true;
    }

    private static boolean wipeSkills(ServerPlayer player) {
        if (!player.getAbilities().instabuild && !player.hasPermissions(2)) return false;
        return ZSSCapabilities.get(player).map(data -> {
            boolean changed = data.clearSkills();
            if (changed) ZSSNetwork.syncPlayerData(player);
            return changed;
        }).orElse(false);
    }

    private static boolean magic(ServerPlayer player, float amount) {
        return ZSSCapabilities.get(player).map(data -> {
            boolean changed = data.restoreMagic(amount);
            if (changed) ZSSNetwork.syncPlayerData(player);
            return changed;
        }).orElse(false);
    }

    private static boolean heal(ServerPlayer player, float amount) {
        if (player.getHealth() >= player.getMaxHealth()) return false;
        player.heal(amount);
        return true;
    }

    private static boolean power(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
        player.getPersistentData().putLong("zss_power_piece_until", player.level().getGameTime() + 600L);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (kind == Kind.SKILL_ORB) {
            ResourceLocation skill = skillFromStack(stack);
            if (skill != null) {
                tooltip.add(Component.translatable("tooltip.zeldaswordskills_remastered.skill_orb.skill", skillName(skill))
                        .withStyle(ChatFormatting.AQUA));
                tooltip.add(Component.translatable("tooltip.zeldaswordskills_remastered.skill_orb.id", skill.toString())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (kind == Kind.HEART_PIECE) {
            tooltip.add(Component.translatable("tooltip.zeldaswordskills_remastered.heart_piece").withStyle(ChatFormatting.RED));
        }
    }

    private static ResourceLocation skillFromStack(ItemStack stack) {
        if (!stack.hasTag() || stack.getTag().getString(SKILL_TAG).isBlank()) return ZSSContentIds.SWORD_BASIC;
        return ResourceLocation.tryParse(stack.getTag().getString(SKILL_TAG));
    }

    private static Component skillName(ResourceLocation skill) {
        return Component.translatable("skill." + skill.getNamespace() + "." + skill.getPath());
    }

    public enum Kind {
        SKILL_ORB, HEART_PIECE, SKILL_WIPER, SMALL_MAGIC, LARGE_MAGIC,
        SKULLTULA_TOKEN, SMALL_HEART, POWER_PIECE
    }
}
