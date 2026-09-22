package zeldaswordskills_remastered.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.entity.projectile.SwordBeam;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative runtime for every phase-six sword skill. */
public final class AdvancedSwordSkills {
    public static final UUID BONUS_HEART_MODIFIER_ID = UUID.fromString("9b032a88-020b-4d15-955e-8a29cfce55a5");
    private static final ResourceKey<DamageType> SWORD_BREAK_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "sword_break"));
    private static final ResourceKey<DamageType> SPIN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ZSSContentIds.SPIN_ATTACK);
    private static final double MELEE_DISTANCE_SQUARED = 16.0D;
    /** Ticks the crouch-jump activation stays armed; long enough to cover a full vanilla jump. */
    private static final long RISING_CUT_WINDOW_TICKS = 20L;
    private static final long RISING_CUT_COOLDOWN_TICKS = 60L;
    private static final long RISING_CUT_TRAIL_TICKS = 20L;
    /** Number of samples in the vertical slash column drawn above the player. */
    private static final int RISING_CUT_TRAIL_STEPS = 8;
    private static final long DASH_COOLDOWN_BASE_TICKS = 100L;
    private static final long DASH_COOLDOWN_PER_LEVEL_TICKS = 10L;
    /** Extra reach beyond the two bounding boxes that counts as a dash contact. */
    private static final double DASH_CONTACT_DISTANCE = 1.25D;
    /** Double Jump clears 0.75 + 0.25 x skill level blocks and adds the same amount to the safe fall height. */
    private static final double DOUBLE_JUMP_BASE_HEIGHT = 0.75D;
    private static final double DOUBLE_JUMP_HEIGHT_PER_LEVEL = 0.25D;
    /** Ticks a parried attacker can neither attack nor move. */
    private static final long PARRY_STUN_TICKS = 20L;
    /** A landed Sword Break starts a fresh twenty-tick attack and movement lock. */
    private static final long SWORD_BREAK_STUN_TICKS = 20L;
    /** Ticks a Sword Break knockback is allowed to carry before the enemy is pinned again. */
    private static final long SWORD_BREAK_HOLD_GRACE_TICKS = 8L;
    private static final double SWORD_BREAK_KNOCKBACK_DISTANCE = 4.0D;
    /**
     * Parried attackers cannot act while their stun runs. The lock is transient server state and is
     * never written to a save; it holds both halves of the stun, because an attack-speed modifier
     * alone does not gate mob melee goals and a movement modifier alone does not stop a flying
     * creature. Entries last at most {@link #PARRY_STUN_TICKS} and are dropped as soon as they are
     * seen expired, so a reference to the enemy is held only for the length of its own stun.
     */
    private static final Map<UUID, ParryLock> PARRY_LOCK = new HashMap<>();

    /**
     * One parried attacker: the entity, when its stun ends, and where it is held. Both halves of
     * the stun share this record, because an attack-speed modifier alone does not gate mob melee
     * goals and a movement modifier alone does not stop a flying creature.
     *
     * <p>The hold can be re-armed by the Sword Break follow-up. That follow-up knocks the enemy
     * back on the same tick it lands, so the pin is given a short grace: the enemy keeps its
     * knockback momentum until {@code holdFromTick}, and only then does the pin capture wherever it
     * came to rest and freeze it there for the rest of the extended window. Cancelling the hold
     * entirely (as the old standalone knockback did) would forfeit the "cannot move" half.
     */
    private static final class ParryLock {
        private final LivingEntity entity;
        private final UUID owner;
        private long until;
        private double x;
        private double z;
        private long holdFromTick;
        private boolean held;
        /** True while the pin still needs to snapshot the position once the grace runs out. */
        private boolean pinPending;

        private ParryLock(LivingEntity entity, UUID owner, long until, double x, double z) {
            this.entity = entity;
            this.owner = owner;
            this.until = until;
            this.x = x;
            this.z = z;
            this.holdFromTick = 0L;
            this.held = true;
        }

        private void releaseHold() {
            held = false;
        }

        /** Extends the stun and re-arms the pin, letting the current knockback carry until it lands. */
        private void restun(long until, long holdFromTick) {
            this.until = until;
            this.holdFromTick = holdFromTick;
            this.held = true;
            // The position is captured when the grace ends, after the knockback has moved the enemy.
            this.pinPending = true;
        }
    }

    private AdvancedSwordSkills() {
    }

    public static void handleIntent(ServerPlayer player, ZSSPlayerData data, SkillIntentMessage intent) {
        ResourceLocation skill = intent.skill();
        if (data.activeSkillLevel(skill) <= 0) return;
        if (data.combat().flashAssault().busy() && !skill.equals(ZSSContentIds.FLASH_ASSAULT)) return;
        if (skill.equals(ZSSContentIds.HELM_SPLITTER) && intent.action() == SkillIntentMessage.Action.ATTACK) {
            if (!HelmSplitter.tryStrike(player, data)) ZSSNetwork.sendParryState(player, data.combat());
        }
        else if (skill.equals(ZSSContentIds.DODGE) && intent.action() == SkillIntentMessage.Action.BEGIN) dodge(player, data, intent.direction().orElse(Vec3.ZERO));
        else if (skill.equals(ZSSContentIds.PARRY)) parry(player, data, intent.action());
        else if (skill.equals(ZSSContentIds.DASH) && intent.action() == SkillIntentMessage.Action.BEGIN) dash(player, data);
        else if (skill.equals(ZSSContentIds.CONTINUOUS_FLASH) && intent.action() == SkillIntentMessage.Action.ATTACK) {
            if (level(data, ZSSContentIds.DASH) > 0 && TargetingService.getWeaponTarget(player, data).isPresent()
                    && data.combat().queueDashTap(player.level().getGameTime())
                    && !data.combat().dashPending()) continueDash(player, data);
        }
        else if (skill.equals(ZSSContentIds.SPIN_ATTACK)) spin(player, data, intent);
        else if (skill.equals(ZSSContentIds.SUPER_SPIN_ATTACK) && intent.action() == SkillIntentMessage.Action.ATTACK) refreshSpin(player, data);
        else if (skill.equals(ZSSContentIds.SWORD_BEAM) && intent.action() == SkillIntentMessage.Action.ATTACK) swordBeam(player, data);
        else if (skill.equals(ZSSContentIds.SWORD_BREAK) && intent.action() == SkillIntentMessage.Action.ATTACK) swordBreak(player, data);
        else if (skill.equals(ZSSContentIds.MORTAL_DRAW) && intent.action() == SkillIntentMessage.Action.CANCEL) IaiSlash.cancel(player, data);
        else if (skill.equals(ZSSContentIds.RISING_CUT)) risingCut(player, data, intent.action());
        else if (skill.equals(ZSSContentIds.FLASH_ASSAULT)) FlashAssault.handle(player, data, intent.action());
        else if (skill.equals(ZSSContentIds.DOUBLE_JUMP) && intent.action() == SkillIntentMessage.Action.BEGIN) doubleJump(player, data);
    }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        long now = player.level().getGameTime();
        FlashAssault.tick(player, data);
        HelmSplitter.tick(player, data);
        IaiSlash.tick(player, data);
        tickParry(player, data, now);
        // Touching the ground restores the extra mid-air jump.
        if (player.onGround()) data.combat().resetDoubleJump();
        // The Rising Cut waiver covers one climb only, so it ends the moment that climb does.
        if (player.isInWater() || player.onClimbable() || player.isFallFlying() || player.getAbilities().flying) {
            // Water, a ladder or flight breaks the fall without ever landing on it.
            data.combat().clearRisingCutFallGrace();
        } else if (!player.onGround()) {
            // Start counting only once the lift has actually left the ground. A hit that lands on
            // the ground is lifted by that same tick, and the server's ground flag lags the client
            // by a tick or two, so clearing on the ground immediately would throw the waiver away
            // before the climb it belongs to had even begun.
            data.combat().markRisingCutGraceAirborne();
        } else if (data.combat().risingCutGraceAirborne() && player.fallDistance <= 0.0F
                && player.getDeltaMovement().y <= 0.0D) {
            // A ground flag can precede doCheckFallDamage; retain the waiver until it settles.
            data.combat().clearRisingCutFallGrace();
        }
        tickBonusHearts(player, data);
        FatalStrike.tick(player, data);
        GroundSlam.tick(player, data);
        tickDash(player, data, now);
        tickSpin(player, data, now);
        if (!data.combat().risingCutActive(now)) data.combat().clearRisingCut();
        if (data.combat().risingCutImmune(now) && player.getDeltaMovement().y <= 0.0D) {
            data.combat().finishRisingCutAscent(now);
        }
        tickRisingCutTrail(player, data, now);
        if (data.combat().charging(ZSSContentIds.SPIN_ATTACK)) {
            Vec3 motion = player.getDeltaMovement();
            double maxSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.3D;
            double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (horizontal > maxSpeed && horizontal > 1.0E-6D) {
                double scale = maxSpeed / horizontal;
                player.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
            }
        }
    }

    private static void dodge(ServerPlayer player, ZSSPlayerData data, Vec3 direction) {
        int level = level(data, ZSSContentIds.DODGE);
        long now = player.level().getGameTime();
        if (level <= 0 || !player.isAlive() || player.isSpectator() || player.isPassenger()
                || !player.onGround() || player.isInFluidType() || data.combat().dodgeOnCooldown(now)) return;
        Vec3 side = validatedSide(player, direction);
        if (side.lengthSqr() < 0.25D) return;
        if (!data.combat().acceptDodgeTap(now, side)) return;
        data.combat().startDodge(now, level);
        var groundPos = player.getOnPos();
        float friction = player.level().getBlockState(groundPos).getFriction(player.level(), groundPos, player);
        double speed = dodgeImpulseSpeed(friction);
        LivingEntity target = TargetingService.getLockedTarget(player, data).orElse(null);
        data.combat().flashAssault().startDodge(now, target == null ? -1 : target.getId());
        if (target != null && horizontal(player.position().subtract(target.position())).lengthSqr() >= 1.0E-6D) {
            Vec3 look = horizontal(player.getLookAngle()).normalize();
            boolean right = side.dot(new Vec3(-look.z, 0.0D, look.x)) > 0.0D;
            Vec3 motion = DodgeMovement.orbitVelocity(player.position(), target.position(), speed, right);
            player.setDeltaMovement(motion.x, player.getDeltaMovement().y, motion.z);
            player.hasImpulse = true;
            ZSSNetwork.startDodgeOrbit(player, new zeldaswordskills_remastered.network.DodgeOrbitMessage(target.getId(), right, speed));
            return;
        }
        // Replace existing horizontal motion so the first tap's walking speed does
        // not increase the impulse. Vanilla handles all subsequent travel and collisions.
        player.setDeltaMovement(side.x * speed, player.getDeltaMovement().y, side.z * speed);
        player.hurtMarked = true;
    }

    public static double dodgeImpulseSpeed(float groundFriction) {
        // v + v*r + v*r^2 + ... = distance, with vanilla ground drag r = friction * 0.91.
        // Both modes target four blocks on uniform level ground without further input;
        // vanilla's small-velocity cutoff and network quantization slightly shorten it.
        return DodgeMovement.DISTANCE * (1.0D - groundFriction * 0.91F);
    }

    /** The Parry guard window a single arming covers at this skill level. */
    public static long parryWindow(int level) {
        return 20L + 2L * Mth.clamp(level, 1, 5);
    }

    public static long swordBreakWindow(int level) {
        return 20L + 2L * Mth.clamp(level, 1, 5);
    }

    private static boolean canGuard(ServerPlayer player, ZSSPlayerData data) {
        return player.isAlive() && !player.isSpectator() && level(data, ZSSContentIds.PARRY) > 0
                && !ItemUsePriority.takesPriority(player) && TargetingService.isHoldingSword(player)
                && TargetingService.getLockedTarget(player, data).isPresent();
    }

    private static void tickParry(ServerPlayer player, ZSSPlayerData data, long now) {
        if (data.combat().parryPending() && (!data.combat().parryActive(now) || !canGuard(player, data))) {
            endParryGuard(player, data);
        }
    }

    /** Also called for actual, non-parried damage, including environmental damage. */
    public static void endParryGuard(ServerPlayer player, ZSSPlayerData data) {
        if (data.combat().finishParry(player.level().getGameTime())) ZSSNetwork.sendParryState(player, data.combat());
    }

    /** Holding use opens one finite guard, then waits through its full cooldown. */
    private static void parry(ServerPlayer player, ZSSPlayerData data, SkillIntentMessage.Action action) {
        int level = level(data, ZSSContentIds.PARRY);
        long now = player.level().getGameTime();
        if (action == SkillIntentMessage.Action.CANCEL) {
            // Releasing use spends only an existing guard. A late release after a successful
            // parry must neither restart its cooldown nor discard the Sword Break opening.
            data.combat().finishParry(now);
            ZSSNetwork.sendParryState(player, data.combat());
            return;
        }
        if (action != SkillIntentMessage.Action.BEGIN) return;
        tickParry(player, data, now);
        if (canGuard(player, data)) data.combat().startParry(now, now + parryWindow(level));
        // Confirm starts and rejections as well as endings; animations follow server state.
        ZSSNetwork.sendParryState(player, data.combat());
    }

    /**
     * The Sword Break follow-up a successful Parry opened. It always strikes the enemy whose
     * attack the Parry just stopped, so the player never has to aim it, but only while that enemy
     * is still the locked target: the follow-up reaches nobody else.
     *
     * <p>The blow is the entire attack. Nothing here swings a normal weapon hit as well, so the
     * press that releases Sword Break deals this damage and no other.
     */
    private static void swordBreak(ServerPlayer player, ZSSPlayerData data) {
        int level = level(data, ZSSContentIds.SWORD_BREAK);
        long now = player.level().getGameTime();
        if (level <= 0 || !data.combat().swordBreakActive(now)) return;
        try (var attempt = FatalStrike.watchAttack(player, data)) {
        LivingEntity target = player.level().getEntity(data.combat().swordBreakTargetId()) instanceof LivingEntity living
                ? living : null;
        // The window is spent by the attempt, so a target that died in the meantime or a player who
        // switched their lock cannot be struck by a later press.
        data.combat().useSwordBreak();
        data.combat().helmSplitter().clearOpening();
        ZSSNetwork.sendParryState(player, data.combat());
        player.resetAttackStrengthTicker();
        if (player.isBlocking() || !TargetingService.isHoldingSword(player)) return;
        // Only the enemy the Parry recorded, and only while it is still the locked target.
        if (target == null || !target.isAlive()) return;
        if (TargetingService.getValidTarget(player, data).filter(locked -> locked == target).isEmpty()) return;
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 1.2F * level;
        player.swing(InteractionHand.MAIN_HAND, true);
        // Armor-piercing: the blow lands in full against any armor.
        DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SWORD_BREAK_DAMAGE), player);
        if (attempt.hit(BasicSwordSkill.hurtLockedTarget(player, data, target, source, damage))) {
            // A landed Sword Break leaves the enemy unable to attack or move for its own, longer
            // window. The pin is re-armed after a short grace so the knockback below still carries
            // before the hold takes over; releasing it outright would forfeit the immobility half.
            restunSwordBreakTarget(target, now);
            knockBackSwordBreak(target, player);
            play(player, ZSSRegistries.SWORD_BREAK_SOUND.get());
            player.serverLevel().sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    20, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.5D,
                    target.getBbWidth() * 0.5D, 0.15D);
            BasicSwordSkill.recordComboHit(player, data, target, damage);
        }
        }
    }

    private static void dash(ServerPlayer player, ZSSPlayerData data) {
        if (data.combat().dashPending() || data.combat().dashCoolingDown(player.level().getGameTime())) return;
        releaseDash(player, data, false);
    }

    private static void releaseDash(ServerPlayer player, ZSSPlayerData data, boolean continued) {
        int level = continued ? level(data, ZSSContentIds.CONTINUOUS_FLASH) : level(data, ZSSContentIds.DASH);
        long now = player.level().getGameTime();
        LivingEntity target = TargetingService.getWeaponTarget(player, data).orElse(null);
        if (level <= 0 || !player.isAlive() || player.isSpectator() || (!continued && !player.onGround()) || target == null) return;
        double range = dashDistance(level);
        if (player.distanceToSqr(target) > range * range || !player.hasLineOfSight(target)) return;
        // The trajectory is fixed once, at activation: the dash never curves back toward a
        // target that moves, and never shortens as the player closes in.
        Vec3 direction = horizontal(target.position().subtract(player.position()));
        if (direction.lengthSqr() < 1.0E-6D) return;
        if (continued && !data.consumeMagic(10.0F)) return;
        direction = direction.normalize();
        var groundPos = player.getOnPos();
        float friction = player.level().getBlockState(groundPos).getFriction(player.level(), groundPos, player);
        // Same one-shot impulse as Dodge: replacing the existing horizontal motion means the
        // walking speed the player already had never adds to the dash distance.
        double speed = dashImpulseSpeed(level, friction);
        player.setDeltaMovement(direction.x * speed, player.getDeltaMovement().y, direction.z * speed);
        player.hurtMarked = true;
        // The triggering left click deals no damage of its own; the dash carries all of it.
        player.resetAttackStrengthTicker();
        long until = now + dashDuration(level);
        int continuationRemaining = continued
                ? data.combat().dashContinuationRemaining() - 1
                : level(data, ZSSContentIds.CONTINUOUS_FLASH);
        data.combat().startDash(target.getId(), player.position(), until, level, continuationRemaining);
        data.combat().markDashRelease(continued);
        data.combat().watchDash(FatalStrike.watchAttack(player, data));
        // The continuation's movement and immunity scale with Continuous Dash, while the
        // underlying Dash skill retains ownership of its normal cooldown.
        data.combat().startDashCooldown(now + dashCooldown(level(data, ZSSContentIds.DASH)));
        data.combat().startDashImmunity(Math.max(data.combat().dashImmuneUntil(), until));
        dashEffects(player, data, now);
        ZSSNetwork.syncCombatState(player, data.combat());
        if (continued) ZSSNetwork.syncPlayerData(player);
    }

    private static void continueDash(ServerPlayer player, ZSSPlayerData data) {
        if (!data.combat().takeDashContinuation()) return;
        if (level(data, ZSSContentIds.CONTINUOUS_FLASH) > 0) releaseDash(player, data, true);
    }

    private static void finishDash(ServerPlayer player, ZSSPlayerData data) {
        data.combat().finishDash();
        continueDash(player, data);
    }

    private static void dashEffects(ServerPlayer player, ZSSPlayerData data, long now) {
        if (!data.combat().pollDashTrail(now)) return;
        if (data.combat().pollDashSound(now)) player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.8F, 1.0F);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.9D,
                player.getZ(), 3, 0.2D, 0.4D, 0.2D, 0.01D);
    }

    /** Total ground distance a Dash covers at the given skill level. */
    public static double dashDistance(int level) {
        return 3.0D + Mth.clamp(level, 1, 5);
    }

    public static long dashDuration(int level) {
        return 8L + 2L * Mth.clamp(level, 1, 5);
    }

    /** Ticks before Dash can be used again. */
    public static long dashCooldown(int level) {
        return DASH_COOLDOWN_BASE_TICKS - DASH_COOLDOWN_PER_LEVEL_TICKS * level;
    }

    public static double dashImpulseSpeed(int level, float groundFriction) {
        // v + v*r + v*r^2 + ... = distance, with vanilla ground drag r = friction * 0.91.
        // The distance is fixed by the skill level, so unlike Dodge the impulse is
        // independent of how fast the player happens to be moving.
        return dashDistance(level) * (1.0D - groundFriction * 0.91F);
    }

    private static void tickDash(ServerPlayer player, ZSSPlayerData data, long now) {
        if (!data.combat().dashPending()) return;
        dashEffects(player, data, now);
        int level = data.combat().dashLevel();
        double maxRange = dashDistance(level);
        Vec3 start = data.combat().dashStart();
        Vec3 offset = player.position().subtract(start);
        double traveled = offset.length();
        // The whole path is swept every tick, not just the segment covered since the previous
        // one: the server position follows client movement packets, so a single sample can jump
        // clean past the target, and a per-tick segment would never see it at all. The path is
        // clipped to the distance the skill actually covers, so overshooting extends no reach.
        Vec3 end = traveled > maxRange ? start.add(offset.scale(maxRange / traveled)) : player.position();
        sweepDashPath(player, data, level, start, end);
        if (!(player.level().getEntity(data.combat().dashTargetId()) instanceof LivingEntity target) || !target.isAlive()) {
            finishDash(player, data);
            return;
        }
        // Contact is tested before any timeout: the tick that carries the player past the target
        // is exactly the tick that can also overshoot the range limit.
        if (dashContact(player, target, start, end)) {
            float damage = dashTargetDamage((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), level);
            player.resetAttackStrengthTicker();
            boolean hit = hurtDash(player, data, target, damage);
            data.combat().recordDashDamage(hit);
            if (hit) {
                player.level().playSound(null, player.blockPosition(), ZSSRegistries.SWORD_CUT.get(),
                        SoundSource.PLAYERS, 0.6F, 0.9F + player.getRandom().nextFloat() * 0.2F);
                BasicSwordSkill.recordComboHit(player, data, target, damage);
            }
            finishDash(player, data);
            return;
        }
        if (traveled > maxRange || now >= data.combat().dashImmuneUntil()) finishDash(player, data);
    }

    private static boolean hurtDash(ServerPlayer player, ZSSPlayerData data, LivingEntity target, float damage) {
        int immunity = target.invulnerableTime;
        if (data.combat().continuedDash()) target.invulnerableTime = 0;
        boolean hit = BasicSwordSkill.hurtLockedTarget(player, data, target, player.damageSources().playerAttack(player), damage);
        if (!hit) target.invulnerableTime = immunity;
        return hit;
    }

    /** Damages every other enemy the dash passes through, at most once each. */
    private static void sweepDashPath(ServerPlayer player, ZSSPlayerData data, int level, Vec3 start, Vec3 end) {
        float damage = dashSweepDamage((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), level);
        for (LivingEntity entity : alongPath(player, start, end)) {
            // The locked target takes the larger dash hit instead and is never swept here:
            // hitting it twice would only be swallowed by its invulnerability window.
            if (entity.getId() == data.combat().dashTargetId() || !dashContact(player, entity, start, end)) continue;
            if (!data.combat().registerDashHit(entity.getId())) continue;
            data.combat().recordDashDamage(hurtDash(player, data, entity, damage));
        }
    }

    private static boolean dashContact(ServerPlayer player, LivingEntity target, Vec3 start, Vec3 end) {
        double clearance = player.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D;
        return distanceToSegment(target.position(), start, end) - clearance <= DASH_CONTACT_DISTANCE;
    }

    private static List<LivingEntity> alongPath(ServerPlayer player, Vec3 start, Vec3 end) {
        AABB bounds = new AABB(start, end).inflate(DASH_CONTACT_DISTANCE + 1.0D);
        return player.level().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity != player && entity.isAlive() && !entity.isSpectator());
    }

    /** Damage of the locked target hit. Follow-up Dashes deliberately use this same formula. */
    public static float dashTargetDamage(float weaponAttackDamage, int dashLevel) {
        return weaponAttackDamage + 1.5F * Mth.clamp(dashLevel, 1, 5);
    }

    /** Damage of a non-locked enemy swept by the Dash path. */
    public static float dashSweepDamage(float weaponAttackDamage, int dashLevel) {
        return weaponAttackDamage + 0.5F * Mth.clamp(dashLevel, 1, 5);
    }

    private static void spin(ServerPlayer player, ZSSPlayerData data, SkillIntentMessage intent) {
        int level = level(data, ZSSContentIds.SPIN_ATTACK);
        if (intent.action() == SkillIntentMessage.Action.ATTACK) {
            long now = player.level().getGameTime();
            if (level > 0 && !data.combat().spinPending() && !data.combat().spinCoolingDown(now) && TargetingService.isHoldingWeapon(player)) {
                data.combat().startSpin(now + PlayerCombatState.SPIN_ROUND_TICKS, now + 20L);
                applySpinRound(player, data, Vec3.ZERO);
                ZSSNetwork.syncSpinState(player, data.combat());
            }
            return;
        }
        if (intent.action() == SkillIntentMessage.Action.BEGIN) {
            if (level > 0 && !data.combat().spinPending() && !data.combat().spinCoolingDown(player.level().getGameTime()) && TargetingService.isHoldingWeapon(player)) {
                data.combat().beginCharge(ZSSContentIds.SPIN_ATTACK, player.level().getGameTime(), intent.direction().orElse(Vec3.ZERO));
            }
            return;
        }
        if (intent.action() == SkillIntentMessage.Action.CANCEL) {
            data.combat().clearCharge();
            return;
        }
        if (intent.action() != SkillIntentMessage.Action.RELEASE) return;
        boolean charged = data.combat().charged(ZSSContentIds.SPIN_ATTACK, player.level().getGameTime(), 26 - level);
        Vec3 chargeDirection = intent.direction().orElse(data.combat().chargeDirection());
        data.combat().clearCharge();
        if (!charged || data.combat().spinPending() || data.combat().spinCoolingDown(player.level().getGameTime()) || !TargetingService.isHoldingWeapon(player)) return;
        long now = player.level().getGameTime();
        data.combat().startSpin(now + PlayerCombatState.SPIN_ROUND_TICKS, now + 20L);
        applySpinRound(player, data, chargeDirection);
        ZSSNetwork.syncSpinState(player, data.combat());
    }

    private static void refreshSpin(ServerPlayer player, ZSSPlayerData data) {
        long now = player.level().getGameTime();
        int superLevel = level(data, ZSSContentIds.SUPER_SPIN_ATTACK);
        int spinLevel = level(data, ZSSContentIds.SPIN_ATTACK);
        if (superLevel <= 0 || spinLevel <= 0 || !data.combat().spinActive(now) || !fullHealth(player)
                || data.combat().spinRounds() >= superLevel + 2 || !TargetingService.isHoldingWeapon(player)) return;
        data.combat().registerSuperSpinTap(now);
    }

    private static void tickSpin(ServerPlayer player, ZSSPlayerData data, long now) {
        if (!data.combat().spinPending()) return;
        if (!player.isAlive() || player.isSpectator() || !TargetingService.isHoldingWeapon(player)) {
            data.combat().finishSpin();
            ZSSNetwork.syncSpinState(player, data.combat());
            return;
        }
        if (data.combat().spinActive(now)) return;
        int superLevel = level(data, ZSSContentIds.SUPER_SPIN_ATTACK);
        float cost = 5.75F - 0.75F * superLevel + 2.0F * (data.combat().spinRounds() - 1);
        if (!data.combat().spinContinuationQueued() || superLevel <= 0
                || level(data, ZSSContentIds.SPIN_ATTACK) <= 0 || !fullHealth(player)
                || data.combat().spinRounds() >= superLevel + 2 || !data.consumeMagic(cost)) {
            data.combat().finishSpin();
            ZSSNetwork.syncSpinState(player, data.combat());
            return;
        }
        // Only one boundary can settle per tick. Late ticks never burst multiple prepaid rounds.
        data.combat().addSpinRound(now);
        applySpinRound(player, data, Vec3.ZERO);
        ZSSNetwork.syncPlayerData(player);
        ZSSNetwork.syncSpinState(player, data.combat());
    }

    private static void applySpinRound(ServerPlayer player, ZSSPlayerData data, Vec3 chargeDirection) {
        try (var attempt = FatalStrike.watchAttack(player, data)) {
        int level = level(data, ZSSContentIds.SPIN_ATTACK);
        int superLevel = fullHealth(player) ? level(data, ZSSContentIds.SUPER_SPIN_ATTACK) : 0;
        double range = 3.0D + 0.5D * (level + superLevel);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 1.5F * level;
        DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SPIN_DAMAGE), player);
        nearby(player, range).forEach(target -> {
            if (attempt.hit(target.hurt(source, damage))) {
                BasicSwordSkill.recordComboHit(player, data, target, damage);
            }
        });
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0D / 16.0D);
            double x = player.getX() + Math.cos(angle) * range;
            double z = player.getZ() + Math.sin(angle) * range;
            player.serverLevel().sendParticles(ParticleTypes.CRIT, x, player.getY() + 1.0D, z, 1, 0.0D, 0.08D, 0.0D, 0.02D);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        play(player, ZSSRegistries.SPIN_ATTACK_SOUND.get());
        }
    }

    private static void swordBeam(ServerPlayer player, ZSSPlayerData data) {
        int level = level(data, ZSSContentIds.SWORD_BEAM);
        long now = player.level().getGameTime();
        if (level <= 0 || !TargetingService.isHoldingSword(player) || !player.onGround() || !player.isShiftKeyDown()
                || player.getAttackStrengthScale(0.5F) < 0.9F
                || !fullHealth(player) || data.combat().swordBeamCoolingDown(now)) return;
        float multiplier = switch (level) {
            case 1 -> 0.30F;
            case 2 -> 0.40F;
            case 3 -> 0.50F;
            case 4 -> 0.60F;
            default -> 0.75F;
        };
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier;
        SwordBeam beam = new SwordBeam(player.level(), player, damage, 8.0F + level, isMasterSword(player.getMainHandItem()));
        if (!player.level().addFreshEntity(beam)) {
            beam.discard();
            return;
        }
        data.combat().startSwordBeamCooldown(now);
        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void risingCut(ServerPlayer player, ZSSPlayerData data, SkillIntentMessage.Action action) {
        int level = level(data, ZSSContentIds.RISING_CUT);
        long now = player.level().getGameTime();
        if (action == SkillIntentMessage.Action.BEGIN) {
            // Sneak + jump while locked on arms the window. No ground check: it would race the
            // jump packet and randomly reject the activation. The lift happens on a landed hit.
            if (level > 0 && player.isShiftKeyDown() && TargetingService.isHoldingWeapon(player)
                    && !data.combat().risingCutCoolingDown(now)
                    && TargetingService.getLockedTarget(player, data).isPresent()) {
                data.combat().armRisingCut(now + RISING_CUT_WINDOW_TICKS);
            }
            return;
        }
        if (action != SkillIntentMessage.Action.ATTACK || !data.combat().risingCutActive(now)) return;
        try (var attempt = FatalStrike.watchAttack(player, data)) {
        data.combat().clearRisingCut();
        LivingEntity target = validWeaponTarget(player, data);
        double range = 2.0D + level;
        if (target == null || player.distanceToSqr(target) > range * range) return;
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (!attempt.hit(BasicSwordSkill.hurtLockedTarget(player, data, target, player.damageSources().playerAttack(player), damage))) return;
        player.resetAttackStrengthTicker();
        // A landed hit lifts the player again: the crouch-jump already carried them up, and the
        // connecting strike adds a second boost, producing the double-jump effect.
        player.push(0.0D, risingCutLift(level), 0.0D);
        player.resetFallDistance();
        if (level(data, ZSSContentIds.LEAPING_BLOW) > 0) data.combat().armGroundSlam();
        player.hurtMarked = true;
        data.combat().startRisingCutCooldown(now + RISING_CUT_COOLDOWN_TICKS);
        data.combat().startRisingCutImmunity(now + RISING_CUT_TRAIL_TICKS + 30L);
        data.combat().startRisingCutTrail(now, now + RISING_CUT_TRAIL_TICKS);
        boolean blockingPlayer = target instanceof Player targetPlayer && targetPlayer.isUsingItem();
        if (!blockingPlayer) {
            double resistance = 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            target.push(0.0D, (0.3D + 0.125D * level) * resistance, 0.0D);
            target.hurtMarked = true;
        }
        // The climb this hit just started is free of fall damage up to its own grace height.
        data.combat().startRisingCutFallGrace(risingCutFallGrace(level));
        // Rising Cut uses no hunger and counts as a combo hit on the locked target.
        BasicSwordSkill.recordComboHit(player, data, target, damage);
        }
    }

    /**
     * Upward velocity of the Rising Cut double jump, indexed by skill level (1-5).
     * The values are the velocities that raise the player 2 / 2.5 / 3 / 3.5 / 4 blocks, obtained
     * by inverting vanilla jump physics: the first tick displaces by the full velocity and each
     * later tick applies {@code v = (v - 0.08) * 0.98} until the player stops climbing.
     * For reference, a vanilla jump (0.42) rises about 1.25 blocks.
     */
    private static final double[] RISING_CUT_LIFT = {0.0D, 0.544852D, 0.617749D, 0.684757D, 0.745200D, 0.803191D};

    public static double risingCutLift(int level) {
        return RISING_CUT_LIFT[Mth.clamp(level, 1, RISING_CUT_LIFT.length - 1)];
    }

    /** How far the Rising Cut lift raises the player: 2 / 2.5 / 3 / 3.5 / 4 blocks. */
    public static double risingCutHeight(int level) {
        return 1.5D + 0.5D * Mth.clamp(level, 1, RISING_CUT_LIFT.length - 1);
    }

    /** The natural descent waives the lift height plus five; vanilla handles the excess. */
    public static double risingCutFallGrace(int level) {
        return risingCutHeight(level) + 5.0D;
    }

    /**
     * Upward velocity of the Double Jump, indexed by skill level (1-5). The values come from
     * inverting the same vanilla jump physics as the Rising Cut lift, so the levels rise exactly
     * 1 / 1.25 / 1.5 / 1.75 / 2 blocks. Level five matches a level-one Rising Cut.
     */
    private static final double[] DOUBLE_JUMP_VELOCITY = {0.0D, 0.368129D, 0.419614D, 0.463413D, 0.506937D, 0.544852D};

    /** One extra mid-air jump per airtime, unlocked by the Double Jump skill. */
    private static void doubleJump(ServerPlayer player, ZSSPlayerData data) {
        int level = level(data, ZSSContentIds.DOUBLE_JUMP);
        if (level <= 0 || !player.isAlive() || player.isSpectator() || player.isPassenger()
                || player.onGround() || player.isFallFlying()) return;
        if (!data.combat().useDoubleJump(player.level().getGameTime())) return;
        // Replace the vertical velocity exactly like a vanilla jump, so the height depends only on
        // the skill level rather than on the fall speed at the moment the jump is pressed. The
        // horizontal motion is left untouched, and vanilla carries the player from there.
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, doubleJumpVelocity(level), motion.z);
        player.resetFallDistance();
        if (level(data, ZSSContentIds.LEAPING_BLOW) > 0) data.combat().armGroundSlam();
        ZSSNetwork.syncCombatState(player, data.combat());
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    public static double doubleJumpVelocity(int level) {
        return DOUBLE_JUMP_VELOCITY[Mth.clamp(level, 1, DOUBLE_JUMP_VELOCITY.length - 1)];
    }

    /**
     * Height of the Double Jump in blocks: 0.75 + 0.25 x skill level. The same value is added to
     * the fall distance a player survives without damage, so the skill never hurts its own user.
     */
    public static double doubleJumpHeight(int level) {
        return DOUBLE_JUMP_BASE_HEIGHT + DOUBLE_JUMP_HEIGHT_PER_LEVEL * Mth.clamp(level, 1, DOUBLE_JUMP_VELOCITY.length - 1);
    }

    public static double doubleJumpFallHeight(int level) { return doubleJumpHeight(level); }

    /** Draws the vertical enchanted slash in front of the player while they climb. */
    private static void tickRisingCutTrail(ServerPlayer player, ZSSPlayerData data, long now) {
        if (!data.combat().risingCutTrailActive(now)) return;
        if (player.getDeltaMovement().y <= 0.0D) return;
        // One vanilla sword sweep every two ticks for as long as the climb lasts, so the strike
        // reads as a continuous rising slash from lift-off until the Rising Cut is over.
        if (data.combat().risingCutSlashDue(now)) {
            player.level().playSound(null, player.blockPosition(), ZSSRegistries.RISING_CUT_SLASH.get(),
                    SoundSource.PLAYERS, 0.8F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        }
        Vec3 look = horizontal(player.getLookAngle());
        if (look.lengthSqr() < 1.0E-6D) return;
        Vec3 base = player.position().add(look.normalize().scale(0.9D));
        double top = player.getY() + player.getBbHeight() + 0.5D;
        double bottom = player.getY() - 0.1D;
        double progress = data.combat().risingCutTrailProgress(now);
        for (int i = 0; i < RISING_CUT_TRAIL_STEPS; i++) {
            double t = i / (double) (RISING_CUT_TRAIL_STEPS - 1);
            // The slash sweeps downward from the top of the column.
            if (t > progress) break;
            player.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT,
                    base.x, top + (bottom - top) * t, base.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static float flashAssaultArmorChance(ItemStack armor, ItemStack weapon, int level) {
        float chance = level * 0.05F;
        if (!armor.isEmpty()) {
            chance += (5.0F - armor.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR).stream()
                    .mapToDouble(AttributeModifier::getAmount).sum()) * 0.05F;
            chance -= EnchantmentHelper.getTagEnchantmentLevel(Enchantments.UNBREAKING, armor) * 0.05F;
        }
        chance += EnchantmentHelper.getTagEnchantmentLevel(Enchantments.SHARPNESS, weapon) * 0.05F;
        return Mth.clamp(chance, 0.0F, 1.0F);
    }

    public static boolean onAttacked(ServerPlayer player, ZSSPlayerData data, DamageSource source) {
        data.combat().clearParriedAttack();
        long now = player.level().getGameTime();
        tickParry(player, data, now);
        // Cancel before attacker checks: environmental and projectile damage are immune too.
        if (data.combat().dodgeActive(now)) {
            FlashAssault.confirmDodge(player, data, source);
            play(player, ZSSRegistries.SWORD_MISS.get());
            return true;
        }
        if (data.combat().risingCutImmune(now)) return true;
        // Dash immunity is granted at release and covers every damage source, not just the target.
        if (data.combat().dashImmune(now)) return true;
        if (data.combat().flashAssault().immune(now)) return true;
        if (data.combat().helmSplitter().hitImmune(now)) return true;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return false;
        if (data.combat().helmSplitter().immune(attacker.getUUID(), now)) return true;
        return tryParry(player, data, attacker, source.getDirectEntity() instanceof Projectile projectile ? projectile : null);
    }

    public static boolean parryProjectile(ServerPlayer player, ZSSPlayerData data, Projectile projectile) {
        data.combat().clearParriedAttack();
        if (!(projectile.getOwner() instanceof LivingEntity attacker) || !projectile.isAlive()) return false;
        long now = player.level().getGameTime();
        tickParry(player, data, now);
        if (data.combat().dodgeActive(now) || data.combat().dashImmune(now)
                || data.combat().helmSplitter().hitImmune(now)
                || data.combat().flashAssault().immune(now) || data.combat().helmSplitter().immune(attacker.getUUID(), now)) return false;
        return tryParry(player, data, attacker, projectile);
    }

    private static boolean tryParry(ServerPlayer player, ZSSPlayerData data, LivingEntity attacker,
                                    @Nullable Projectile projectile) {
        long now = player.level().getGameTime();
        int parryLevel = level(data, ZSSContentIds.PARRY);
        boolean lockedAttacker = TargetingService.getValidTarget(player, data).filter(locked -> locked == attacker).isPresent();
        if (parryLevel > 0 && lockedAttacker && data.combat().parryActive(now)) {
            // The window is spent by the parry that used it: the cooldown starts now, and no later
            // attack can be stopped by the same window.
            data.combat().finishParry(now);
            data.combat().recordParriedAttack(now, attacker.getUUID(),
                    projectile == null ? attacker.getUUID() : projectile.getUUID());
            if (projectile == null) {
                attacker.addEffect(new MobEffectInstance(ZSSRegistries.STUN.get(), (int) PARRY_STUN_TICKS, 0));
                lockParriedAttacker(player, attacker, now);
            } else {
                Vec3 direction = attacker.getBoundingBox().getCenter().subtract(projectile.position()).normalize();
                if (direction.lengthSqr() < 1.0E-8D) direction = projectile.getDeltaMovement().reverse().normalize();
                if (direction.lengthSqr() < 1.0E-8D) direction = player.getLookAngle();
                double speed = Math.max(0.1D, projectile.getDeltaMovement().length());
                projectile.setOwner(player);
                projectile.shoot(direction.x, direction.y, direction.z, (float) speed, 0.0F);
                // Fireballs accelerate every tick; their thrust must follow the reflected velocity.
                if (projectile instanceof AbstractHurtingProjectile fireball) {
                    double power = new Vec3(fireball.xPower, fireball.yPower, fireball.zPower).length();
                    fireball.xPower = direction.x * power;
                    fireball.yPower = direction.y * power;
                    fireball.zPower = direction.z * power;
                }
                projectile.hurtMarked = true;
                projectile.hasImpulse = true;
            }
            play(player, ZSSRegistries.PARRY_SUCCESS_SOUND.get());
            Vec3 smoke = player.getEyePosition().add(player.getLookAngle().scale(0.8D));
            player.serverLevel().sendParticles(ParticleTypes.SMOKE, smoke.x, smoke.y, smoke.z,
                    10, 0.18D, 0.12D, 0.18D, 0.01D);
            // A reflected projectile is already the complete ranged-parry response. It must not
            // open melee-only Sword Break or Helm Splitter follow-ups.
            if (projectile == null) {
                int breakLevel = level(data, ZSSContentIds.SWORD_BREAK);
                if (breakLevel > 0) data.combat().startSwordBreak(attacker.getId(), now + swordBreakWindow(breakLevel));
                int helmLevel = level(data, ZSSContentIds.HELM_SPLITTER);
                if (helmLevel > 0) data.combat().helmSplitter().arm(attacker.getId(), now + HelmSplitter.window(helmLevel), player.getY());
            }
            // The landed parry is reported even without Sword Break, so the client mirrors the
            // cooldown it just started instead of asking for guards the server will refuse. A zero
            // window means the parry landed but no follow-up is available.
            ZSSNetwork.sendParryState(player, data.combat());
            return true;
        }
        return false;
    }

    /**
     * Records the parried attacker, when its stun expires, and where it was standing when that
     * happened. Every write drops the locks that have already expired, so the map only ever holds
     * the parries of the last {@link #PARRY_STUN_TICKS} ticks.
     */
    private static void lockParriedAttacker(ServerPlayer player, LivingEntity attacker, long now) {
        PARRY_LOCK.values().removeIf(lock -> lock.until <= now);
        PARRY_LOCK.put(attacker.getUUID(), new ParryLock(attacker, player.getUUID(), now + PARRY_STUN_TICKS, attacker.getX(), attacker.getZ()));
    }

    public static void clearParryLocks(ServerPlayer player) {
        PARRY_LOCK.values().removeIf(lock -> {
            if (!lock.owner.equals(player.getUUID())) return false;
            var effect = lock.entity.getEffect(ZSSRegistries.STUN.get());
            if (effect != null && effect.getAmplifier() == 0
                    && effect.getDuration() <= Math.max(0L, lock.until - lock.entity.level().getGameTime())) {
                lock.entity.removeEffect(ZSSRegistries.STUN.get());
            }
            return true;
        });
    }

    /** Whether this entity was parried recently enough that it still cannot move or attack. */
    public static boolean parryLocked(LivingEntity entity) {
        return parryLock(entity) != null;
    }

    /** The live lock on this entity, or null when it was not parried or the stun has run out. */
    @Nullable
    private static ParryLock parryLock(LivingEntity entity) {
        ParryLock lock = PARRY_LOCK.get(entity.getUUID());
        if (lock == null) return null;
        if (lock.until <= entity.level().getGameTime()) {
            PARRY_LOCK.remove(entity.getUUID());
            return null;
        }
        return lock;
    }

    /** The tick at which this entity's parry/Sword Break immobility ends, or -1 when unlocked. */
    public static long parryLockUntil(LivingEntity entity) {
        ParryLock lock = parryLock(entity);
        return lock == null ? -1L : lock.until;
    }

    /** The tick from which this entity's position is pinned again after a Sword Break knockback. */
    public static long parryHoldFromTick(LivingEntity entity) {
        ParryLock lock = parryLock(entity);
        return lock == null ? -1L : lock.holdFromTick;
    }

    /**
     * Pins every currently parried attacker back to where its parry caught it, horizontally only so
     * gravity still applies. The Stun effect lowers the movement attribute, which a mob's own AI, a
     * knockback or a projectile can all push past; writing the position back makes the immobilise
     * hold regardless, and covers flying creatures whose movement never reads that attribute.
     *
     * <p>This runs at the end of the level tick, after every entity has already moved: the
     * per-entity tick event fires before the entity's own AI, so a hold applied there would simply
     * be overwritten by the movement that follows. Expired locks are dropped as they are seen.
     */
    public static void holdParriedAttackers() {
        if (PARRY_LOCK.isEmpty()) return;
        PARRY_LOCK.values().removeIf(lock -> {
            if (lock.entity.isRemoved() || !lock.entity.isAlive()) return true;
            long now = lock.entity.level().getGameTime();
            if (lock.until <= now) return true;
            if (!lock.held) return false;
            // A Sword Break re-arms the pin after its knockback has had time to carry; the position
            // frozen is the one the enemy reached by then, never the spot it was struck at.
            if (now < lock.holdFromTick) return false;
            if (lock.pinPending) {
                lock.x = lock.entity.getX();
                lock.z = lock.entity.getZ();
                lock.pinPending = false;
            }
            lock.entity.setPos(lock.x, lock.entity.getY(), lock.z);
            lock.entity.setDeltaMovement(0.0D, lock.entity.getDeltaMovement().y, 0.0D);
            lock.entity.hurtMarked = true;
            return false;
        });
    }

    /**
     * Re-arms a landed Sword Break's target: the enemy stays unable to move or attack for the full
     * {@link #SWORD_BREAK_STUN_TICKS} window, and is pinned again at wherever the knockback carries
     * it once the grace runs out. The stun effect is refreshed to match the new deadline.
     */
    private static void restunSwordBreakTarget(LivingEntity target, long now) {
        long until = now + SWORD_BREAK_STUN_TICKS;
        target.addEffect(new MobEffectInstance(ZSSRegistries.STUN.get(), (int) SWORD_BREAK_STUN_TICKS, 0));
        ParryLock lock = PARRY_LOCK.get(target.getUUID());
        if (lock != null) {
            lock.restun(until, now + SWORD_BREAK_HOLD_GRACE_TICKS);
            return;
        }
        // The enemy was not parried by this player (for example the entity was recreated); hold it
        // in place on its own, pinned wherever the knockback leaves it.
        PARRY_LOCK.put(target.getUUID(), new ParryLock(target, UUID.randomUUID(), until,
                target.getX(), target.getZ()));
        ParryLock created = PARRY_LOCK.get(target.getUUID());
        created.restun(until, now + SWORD_BREAK_HOLD_GRACE_TICKS);
    }

    /**
     * Lets a parried enemy be moved again without ending its stun, so the player's Sword Break can
     * knock it back. The attack half of the stun stays live: the enemy still cannot hit anything
     * until the window runs out.
     */
    static void releaseParryHold(LivingEntity entity) {
        ParryLock lock = PARRY_LOCK.get(entity.getUUID());
        if (lock != null) lock.releaseHold();
    }

    public static void tickBonusHearts(ServerPlayer player, ZSSPlayerData data) {
        int level = level(data, ZSSContentIds.BONUS_HEART);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        AttributeModifier existing = health.getModifier(BONUS_HEART_MODIFIER_ID);
        double expected = 2.0D * level;
        if (existing != null && existing.getAmount() == expected) return;
        float oldMaximum = player.getMaxHealth();
        if (existing != null) health.removeModifier(BONUS_HEART_MODIFIER_ID);
        if (level > 0) health.addPermanentModifier(new AttributeModifier(BONUS_HEART_MODIFIER_ID, "Bonus Hearts", expected,
                AttributeModifier.Operation.ADDITION));
        float gained = player.getMaxHealth() - oldMaximum;
        if (gained > 0.0F) player.heal(gained);
        else if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static LivingEntity validWeaponTarget(ServerPlayer player, ZSSPlayerData data) {
        LivingEntity target = TargetingService.getWeaponTarget(player, data).orElse(null);
        return target != null && player.distanceToSqr(target) <= MELEE_DISTANCE_SQUARED ? target : null;
    }

    private static int level(ZSSPlayerData data, ResourceLocation skill) {
        return data.activeSkillLevel(skill);
    }

    private static List<LivingEntity> nearby(ServerPlayer player, double range) {
        AABB bounds = player.getBoundingBox().inflate(range);
        return player.level().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity != player && entity.isAlive() && !entity.isSpectator() && player.distanceToSqr(entity) <= range * range)
                .stream().sorted(Comparator.comparingDouble(player::distanceToSqr)).toList();
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0D, vector.z);
    }

    private static Vec3 validatedSide(ServerPlayer player, Vec3 requested) {
        Vec3 look = horizontal(player.getLookAngle()).normalize();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        Vec3 horizontalRequest = horizontal(requested);
        if (horizontalRequest.lengthSqr() < 0.25D) return Vec3.ZERO;
        double sideDot = horizontalRequest.normalize().dot(right);
        if (Math.abs(sideDot) < 0.5D) return Vec3.ZERO;
        return right.scale(Math.signum(sideDot));
    }

    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        // A zero-length sweep must fall back to a plain distance: dividing by it yields NaN,
        // and every NaN comparison is false, which would silently pass a contact test.
        if (lengthSqr < 1.0E-12D) return point.distanceTo(start);
        double t = Mth.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return point.distanceTo(start.add(segment.scale(t)));
    }

    private static boolean fullHealth(Player player) {
        return player.isCreative() || player.getHealth() >= player.getMaxHealth();
    }

    static boolean isMasterSwordAtFullHealth(ServerPlayer player) {
        return isMasterSword(player.getMainHandItem()) && player.getHealth() >= player.getMaxHealth();
    }

    private static boolean isMasterSword(ItemStack stack) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(ZeldaSwordSkills_Remastered.MOD_ID)
                && (id.getPath().equals("master_sword") || id.getPath().equals("true_master_sword"));
    }

    /** Four blocks on level ground, retaining vanilla lift, resistance and knockback events. */
    private static void knockBackSwordBreak(LivingEntity target, LivingEntity source) {
        var pos = target.getOnPos();
        float friction = target.level().getBlockState(pos).getFriction(target.level(), pos, target);
        Vec3 away = horizontal(target.position().subtract(source.position()));
        if (away.lengthSqr() < 1.0E-6D) away = Vec3.directionFromRotation(0.0F, source.getYRot());
        // Replace pre-existing horizontal momentum, including hurt()'s ordinary knockback.
        target.setDeltaMovement(0.0D, target.getDeltaMovement().y, 0.0D);
        target.knockback(swordBreakImpulseSpeed(friction), -away.x, -away.z);
        target.hurtMarked = true;
    }

    public static double swordBreakImpulseSpeed(float friction) {
        // Integrate a unit horizontal impulse with vanilla's 0.4 knockback lift. Travel chooses
        // drag before movement: ground drag on takeoff, air drag through the landing tick.
        double height = 0.0D, vertical = 0.4D, speed = 1.0D, distance = 0.0D;
        boolean grounded = true;
        for (int tick = 0; tick < 200; tick++) {
            distance += speed;
            speed *= grounded ? friction * 0.91F : 0.91F;
            height += vertical;
            grounded = height <= 0.0D;
            if (grounded) { height = 0.0D; vertical = 0.0D; }
            vertical = (vertical - 0.08D) * (double) 0.98F;
        }
        return SWORD_BREAK_KNOCKBACK_DISTANCE / distance;
    }

    private static void play(ServerPlayer player, SoundEvent sound) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.5F,
                0.9F + player.getRandom().nextFloat() * 0.2F);
    }
}
