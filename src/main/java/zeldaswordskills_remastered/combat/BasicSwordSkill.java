package zeldaswordskills_remastered.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.entity.ElementalDamage;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class BasicSwordSkill {
    private static final int[] COMBO_MAXIMUM = {0, 5, 5, 7, 7, 10, 10, 12, 14, 16, 20};
    private static final int[] COMBO_DURATION = {0, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60};
    private static final int[] DAMAGE_BREAK_THRESHOLD = {0, 1, 1, 3, 3, 5, 5, 7, 7, 9, 10};
    /**
     * Fraction of the main-hand weapon's recovery that must have elapsed before a plain locked
     * attack lands. Vanilla derives that recovery from the held item's {@code ATTACK_SPEED}
     * attribute, so the weapon's own attack speed is what paces an ordinary hit; sword skills keep
     * their own windows and are not paced by this value.
     */
    public static final float LOCKED_ATTACK_RECOVERY = 0.9F;
    /** How often the True Master Sword adds its true damage: on every tenth hit of the combo. */
    public static final int TRUE_MASTER_SWORD_BONUS_EVERY = 10;
    /** True damage per trigger; it bypasses armor and the target's post-hit invulnerability. */
    public static final float TRUE_MASTER_SWORD_BONUS_DAMAGE = 5.0F;
    private static final ResourceKey<DamageType> TRUE_MASTER_SWORD_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID, "true_master_sword"));

    private BasicSwordSkill() {
    }

    public static void handleIntent(ServerPlayer player, ZSSPlayerData data, SkillIntentMessage intent) {
        if (intent.action() == SkillIntentMessage.Action.CANCEL) {
            finishCombo(player, data);
        } else if (intent.action() == SkillIntentMessage.Action.ATTACK) {
            data.combat().clearCharge();
            attack(player, data);
        }
    }

    public static boolean attack(ServerPlayer player, ZSSPlayerData data) {
        boolean flashAssault = data.combat().flashAssault().busy();
        if (!flashAssault && TargetingService.isHoldingSword(player) && HelmSplitter.tryStrike(player, data)) return true;
        int level = TargetingService.basicSkillLevel(data);
        // A plain locked attack is paced by the main-hand weapon: this scale comes straight from
        // the held item's ATTACK_SPEED attribute, so a heavy weapon cannot be spammed and a fast
        // one keeps its own cadence. Sword skills above and below are gated by their own windows.
        if (level <= 0 || player.getAttackStrengthScale(0.5F) < LOCKED_ATTACK_RECOVERY) return false;
        LivingEntity target = TargetingService.getLockedTarget(player, data).orElse(null);
        boolean holdingWeapon = TargetingService.isHoldingWeapon(player);
        if (!flashAssault && holdingWeapon && GroundSlam.tryStrike(player, data, target)) return true;
        if (!flashAssault && holdingWeapon && FatalStrike.tryStrike(player, data, target)) {
            player.resetAttackStrengthTicker();
            player.swing(InteractionHand.MAIN_HAND, false);
            return true;
        }
        if (target == null || !player.canReach(target, 0.0D)) {
            miss(player, data);
            return false;
        }
        int bonus = data.combat().nextDamageBonus();
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean vanillaCritical = FatalStrike.isJumpCritical(player);
        var critical = net.minecraftforge.common.ForgeHooks.getCriticalHit(player, target, vanillaCritical,
                vanillaCritical ? 1.5F : 1.0F);
        if (critical != null) damage *= critical.getDamageModifier();
        damage += bonus;
        player.resetAttackStrengthTicker();
        // The initiating client already swung on press; only observers need the animation packet.
        player.swing(InteractionHand.MAIN_HAND, false);
        if (!hurtLockedTarget(player, data, target, player.damageSources().playerAttack(player), damage)) {
            miss(player, data);
            return false;
        }
        if (critical != null) {
            player.crit(target);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            player.level().playSound(null, player.blockPosition(), ZSSRegistries.SWORD_CUT.get(), SoundSource.PLAYERS, 0.6F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        }
        int before = data.combat().comboCount();
        data.combat().recordHit(target.getId(), damage, comboMaximum(level), player.level().getGameTime() + comboDuration(level));
        trueMasterSwordBonus(player, data, target, before);
        ZSSAdvancementService.comboHit(player, data.combat().comboCount());
        ZSSNetwork.syncCombatState(player, data.combat());
        return true;
    }

    /** Keep shared-target hits and ordinary attacks between Flash Assault hits from suppressing each other. */
    public static boolean hurtLockedTarget(ServerPlayer player, ZSSPlayerData data, LivingEntity target,
                                           DamageSource source, float damage) {
        DamageSource previous = target.getLastDamageSource();
        if (target.invulnerableTime > 10 && data.combat().targetId() == target.getId()
                && previous != null && previous.getEntity() instanceof ServerPlayer other
                && (other == player && source.is(DamageTypes.PLAYER_ATTACK) && FlashAssault.isDamage(previous)
                    || other != player && ZSSCapabilities.get(other)
                        .map(otherData -> otherData.combat().targetId() == target.getId()).orElse(false))) {
            // Bypass only this hit's cooldown check; retain armor, shields and damage event hooks.
            source = new DamageSource(source.typeHolder(), source.getDirectEntity(), source.getEntity(), source.sourcePositionRaw()) {
                @Override
                public boolean is(net.minecraft.tags.TagKey<DamageType> tag) {
                    return tag.equals(DamageTypeTags.BYPASSES_COOLDOWN) || super.is(tag);
                }
            };
        }
        return target.hurt(source, damage);
    }

    /**
     * Records a sword-skill hit on the locked target as a basic-skill combo hit. Only the locked
     * target advances the combo, so area skills cannot reset the chain on a bystander, and the
     * combo bounds always follow the Basic Sword Skill level.
     */
    public static void recordComboHit(ServerPlayer player, ZSSPlayerData data, LivingEntity target, float damage) {
        int level = TargetingService.basicSkillLevel(data);
        if (level <= 0) return;
        // Only self-directed creatures take part in the chain; items, item frames and the transient
        // entities an enemy attack spawns can never be a lock and so never advance the combo.
        if (!TargetingService.hasAutonomousBehaviour(target)) return;
        // Friends are not enemies, so they never advance the chain either, even if a stale lock or
        // a wide area skill happens to touch one.
        if (TargetingService.isFriendly(target)) return;
        // The caller already confirmed the hit; a killing blow must still advance the lock's combo.
        if (data.combat().targetId() != target.getId()) return;
        int before = data.combat().comboCount();
        data.combat().recordHit(target.getId(), damage, comboMaximum(level),
                player.level().getGameTime() + comboDuration(level));
        trueMasterSwordBonus(player, data, target, before);
        ZSSAdvancementService.comboHit(player, data.combat().comboCount());
        ZSSNetwork.syncCombatState(player, data.combat());
    }

    /**
     * The True Master Sword adds 5 points of true damage each time the combo reaches a multiple of
     * ten hits. "True" means it bypasses armor, so it uses its own damage type that is registered
     * under {@code minecraft:bypasses_armor}; it also bypasses the post-hit invulnerability window
     * of the strike that earned it, otherwise the bonus would silently be eaten by the combo hit
     * that fired it. The bonus is part of the same combo record, so it advances the recorded combo
     * damage without adding a hit of its own.
     */
    private static void trueMasterSwordBonus(ServerPlayer player, ZSSPlayerData data, LivingEntity target, int before) {
        if (!isTrueMasterSword(player.getMainHandItem())) return;
        int count = data.combat().comboCount();
        if (count <= before || count % TRUE_MASTER_SWORD_BONUS_EVERY != 0) return;
        if (!target.isAlive()) return;
        DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(TRUE_MASTER_SWORD_DAMAGE), player);
        float previousHealth = target.getHealth();
        if (!target.hurt(source, TRUE_MASTER_SWORD_BONUS_DAMAGE)) return;
        // The strike above already opened the target's invulnerability window; a direct blow that
        // reports damage proves the bonus was not swallowed by it.
        data.combat().addComboDamage(Math.max(0.0F, previousHealth - target.getHealth()));
        player.level().playSound(null, target.blockPosition(), ZSSRegistries.SWORD_CUT.get(),
                SoundSource.PLAYERS, 0.9F, 1.4F);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                    target.getX(), target.getY(0.6D), target.getZ(), 14,
                    target.getBbWidth() * 0.4D, target.getBbHeight() * 0.35D, target.getBbWidth() * 0.4D, 0.05D);
        }
    }

    private static boolean isTrueMasterSword(ItemStack stack) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID)
                && id.getPath().equals("true_master_sword");
    }

    public static void onPlayerDamaged(ServerPlayer player, ZSSPlayerData data, DamageSource source, float damage) {
        int level = TargetingService.basicSkillLevel(data);
        if (level > 0 && damage > damageBreakThreshold(level) && canBreakCombo(source)) finishCombo(player, data);
    }

    private static boolean canBreakCombo(DamageSource source) {
        ElementalDamage.Element element = ElementalDamage.from(source);
        // Elemental damage takes priority even when delivered by a melee attack or projectile.
        if (source.is(DamageTypeTags.IS_FIRE) || element == ElementalDamage.Element.FIRE || element == ElementalDamage.Element.MAGIC
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER_SKULL)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRAGON_BREATH)
                || source.is(DamageTypes.THORNS) || source.is(DamageTypes.SONIC_BOOM)) return true;
        return !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_FALL)
                && !source.is(DamageTypes.MOB_ATTACK) && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                && !source.is(DamageTypes.PLAYER_ATTACK)
                && !(element == ElementalDamage.Element.NONE && source.getDirectEntity() instanceof LivingEntity
                    && source.getDirectEntity() == source.getEntity());
    }

    public static int comboMaximum(int level) { return COMBO_MAXIMUM[Math.max(1, Math.min(level, 10))]; }
    public static int comboDuration(int level) { return COMBO_DURATION[Math.max(1, Math.min(level, 10))]; }
    public static int damageBreakThreshold(int level) { return DAMAGE_BREAK_THRESHOLD[Math.max(1, Math.min(level, 10))]; }

    /**
     * Whether the main-hand weapon has recovered enough for a plain locked attack. Vanilla computes
     * this scale from the held item's {@code ATTACK_SPEED} attribute, so the weapon's own attack
     * speed is what paces an ordinary hit - a slow hammer simply cannot be swung faster, and a fast
     * sword keeps its cadence. Skill attacks are checked before this gate and keep their own windows.
     */
    public static boolean lockedAttackRecovered(net.minecraft.world.entity.player.Player player) {
        return player.getAttackStrengthScale(0.5F) >= LOCKED_ATTACK_RECOVERY;
    }

    private static void miss(ServerPlayer player, ZSSPlayerData data) {
        FatalStrike.miss(player, data);
        player.level().playSound(null, player.blockPosition(), ZSSRegistries.SWORD_MISS.get(), SoundSource.PLAYERS, 0.5F, 0.9F);
        finishCombo(player, data);
    }

    private static void finishCombo(ServerPlayer player, ZSSPlayerData data) {
        if (data.combat().comboCount() <= 0 || data.combat().comboFinished()) return;
        data.combat().finishCombo();
        ZSSNetwork.syncCombatState(player, data.combat());
    }
}
