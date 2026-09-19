package zeldaswordskills_remastered.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.entity.projectile.ZeldaArrow;
import zeldaswordskills_remastered.item.ZeldaCombatItems;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

/** Armored swordsman with directional armor, charged attacks and a boss form. */
public final class DarknutCreature extends LegacyCreature {
    // MoveControl uses this value as both speed and forward input (Mob.setSpeed),
    // so ground acceleration is proportional to its square. The melee goal uses 1x speed.
    private static final double PLAYER_SPRINT_CHASE_SPEED = Math.sqrt(.1D * 1.3D);
    private static final float ATTACK_RESISTANCE_MULTIPLIER = .8F;
    private static final float FACE_WEAKNESS_MULTIPLIER = 2.0F;
    private static final float MELEE_BLOCK_CHANCE = .5F;
    private final ServerBossEvent bossBar;
    private float armorRemaining;
    private int charge;
    private int hitStreak;

    public DarknutCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level, kind);
        armorRemaining = kind == Kind.DARKNUT_BOSS ? 60.0F : 20.0F;
        setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.DARKNUT_SWORD.get()));
        bossBar = kind == Kind.DARKNUT_BOSS
                ? new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS)
                : null;
    }

    public float armorRemaining() { return armorRemaining; }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .75D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void aiStep() {
        updateMovementSpeed();
        super.aiStep();
        LivingEntity target = getTarget();
        if (!level().isClientSide && target != null && target.isAlive() && distanceToSqr(target) < 20.0D) charge++;
        else charge = Math.max(0, charge - 2);
        if (bossBar != null) bossBar.setProgress(getHealth() / getMaxHealth());
    }

    private void updateMovementSpeed() {
        if (level().isClientSide) return;
        LivingEntity target = getTarget();
        double desiredSpeed = target != null && target.isAlive()
                ? PLAYER_SPRINT_CHASE_SPEED
                : armorRemaining <= 0.0F ? kind().speed() + .35D : kind().speed();
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed.getBaseValue() != desiredSpeed) speed.setBaseValue(desiredSpeed);
    }

    @Override public boolean doHurtTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return super.doHurtTarget(entity);
        int threshold = switch (level().getDifficulty()) {
            case EASY -> 23; case HARD -> 13; default -> 18;
        };
        boolean power = charge >= threshold;
        if (power) charge = 0;
        // The variant defines total attack power; its carried sword must not add damage again.
        var attack = new net.minecraft.world.entity.ai.attributes.AttributeInstance(Attributes.ATTACK_DAMAGE, ignored -> {});
        attack.replaceFrom(getAttribute(Attributes.ATTACK_DAMAGE));
        getMainHandItem().getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE).forEach(attack::removeModifier);
        float damage = (float) attack.getValue() * (power ? 1.5F : 1.0F);
        boolean hit = living.hurt(damageSources().mobAttack(this), damage);
        if (!hit) return false;
        if (++hitStreak >= 3) {
            hitStreak = 0;
            for (LivingEntity nearby : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.25D),
                    candidate -> candidate != this && candidate != living && candidate.isAlive()))
                nearby.hurt(damageSources().mobAttack(this), damage * .65F);
        }
        return true;
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL)) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        if (source.is(DamageTypeTags.IS_FIRE) && kind() != Kind.DARKNUT_STANDARD) return false;
        boolean ranged = isRanged(source);
        var projectileHit = ranged ? projectileHitLocation(source) : java.util.Optional.<Vec3>empty();
        boolean headHit = projectileHit.filter(this::hitsHead).isPresent();
        boolean faceWeakness = headHit && isInFront(projectileHit.orElseThrow());
        if (ranged && !headHit) {
            reflectBodyProjectileFromDamage(source.getDirectEntity());
            return false;
        }
        if (source.getDirectEntity() instanceof ZeldaArrow arrow && arrow.kind() == ZeldaCombatItems.ArrowKind.LIGHT) {
            if (kind() != Kind.DARKNUT_BOSS) amount = getHealth() + getMaxHealth(); else amount *= 4.0F;
        }
        Entity attacker = source.getDirectEntity();
        if (!ranged && attacker instanceof LivingEntity && attacker == source.getEntity()
                && !source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypeTags.BYPASSES_SHIELD)
                && getMainHandItem().is(ZSSRegistries.DARKNUT_SWORD.get()) && isInFront(attacker)
                && random.nextFloat() < MELEE_BLOCK_CHANCE) {
            level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    SoundSource.HOSTILE, 1.0F, 1.0F);
            return false;
        }
        if (armorRemaining > 0 && !source.is(DamageTypeTags.BYPASSES_ARMOR) && !isMagicProjectile(source)) {
            armorRemaining = Math.max(0.0F, armorRemaining - amount * .5F);
            if (armorRemaining <= 0.0F) {
                updateMovementSpeed();
                level().playSound(null, blockPosition(), ZSSRegistries.ARMOR_SHATTER.get(), SoundSource.HOSTILE, 1.0F, .9F);
                if (source.getEntity() instanceof ServerPlayer player) ZSSAdvancementService.orcaProgress(player, 0, false, true);
            }
        }
        if (faceWeakness) amount *= FACE_WEAKNESS_MULTIPLIER;
        if (ranged || source.getEntity() instanceof LivingEntity) amount *= ATTACK_RESISTANCE_MULTIPLIER;
        return super.hurt(source, amount);
    }

    private boolean isInFront(Entity attacker) {
        Vec3 towardAttacker = attacker.position().subtract(position()).multiply(1, 0, 1).normalize();
        Vec3 facing = getLookAngle().multiply(1, 0, 1).normalize();
        return towardAttacker.dot(facing) > .5D;
    }

    private java.util.Optional<Vec3> projectileHitLocation(DamageSource source) {
        Entity projectile = source.getDirectEntity();
        if (!(projectile instanceof Projectile)) return java.util.Optional.empty();
        Vec3 start = projectile.position();
        Vec3 end = start.add(projectile.getDeltaMovement());
        var hit = getBoundingBox().inflate(projectile.getBbWidth() * .5D).clip(start, end);
        if (hit.isEmpty() && getBoundingBox().contains(start)) hit = java.util.Optional.of(start);
        return hit;
    }

    private boolean hitsHead(Vec3 hit) {
        double faceBottom = getEyeY() - getBbHeight() * .2D;
        return hit.y >= faceBottom && hit.y <= getY() + getBbHeight() + .25D;
    }

    private boolean isInFront(Vec3 point) {
        Vec3 towardPoint = point.subtract(position()).multiply(1, 0, 1).normalize();
        Vec3 facing = getLookAngle().multiply(1, 0, 1).normalize();
        return towardPoint.dot(facing) > .5D;
    }

    private static boolean isRanged(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile;
    }

    public boolean reflectBodyProjectile(Projectile projectile, Vec3 hitLocation) {
        if (hitsHead(hitLocation)) return false;
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-.8D));
        projectile.hasImpulse = true;
        return true;
    }

    private static void reflectBodyProjectileFromDamage(Entity directEntity) {
        if (!(directEntity instanceof Projectile projectile)) return;
        if (projectile instanceof AbstractArrow && !(projectile instanceof ZeldaArrow)) return;
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-.8D));
        projectile.hasImpulse = true;
    }

    private static boolean isMagicProjectile(DamageSource source) {
        return source.getDirectEntity() instanceof ToolProjectile projectile
                && switch (projectile.mode()) {
                    case FIRE, ICE, LIGHTNING, WIND -> true;
                    default -> false;
                };
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player); if (bossBar != null) bossBar.addPlayer(player);
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player); if (bossBar != null) bossBar.removePlayer(player);
    }
    @Override public void setCustomName(net.minecraft.network.chat.Component name) {
        super.setCustomName(name); if (bossBar != null) bossBar.setName(getDisplayName());
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putFloat("armor_remaining", armorRemaining); tag.putInt("charge", charge);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); armorRemaining = Math.max(0, tag.getFloat("armor_remaining")); charge = Math.max(0, tag.getInt("charge"));
        updateMovementSpeed();
    }
}
