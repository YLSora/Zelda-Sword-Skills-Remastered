package zeldaswordskills_remastered.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityDimensions;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.entity.ZSSDamageSources;

import java.util.EnumSet;

/** Elemental Chu with color-specific contact effects, charge defenses and limited merging. */
public final class ChuCreature extends LegacyCreature {
    private static final EntityDataAccessor<Integer> CHU_SIZE = SynchedEntityData.defineId(ChuCreature.class, EntityDataSerializers.INT);
    private float targetSquish;
    private float squish;
    private float oldSquish;
    private boolean wasOnGround;
    private int mergeCount = 1;
    private int shockTicks;

    public ChuCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level, kind);
        setSize(1, false);
        moveControl = new ChuMoveControl(this);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CHU_SIZE, 1);
    }

    /** Uses the same tracked size and dimension refresh path as vanilla Slime. */
    public void setSize(int size, boolean heal) {
        int boundedSize = Mth.clamp(size, 1, 127);
        entityData.set(CHU_SIZE, boundedSize);
        reapplyPosition();
        refreshDimensions();
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(kind().attackDamage() * boundedSize / 2.0D);
        if (heal) setHealth(getMaxHealth());
    }

    public int getSize() {
        return entityData.get(CHU_SIZE);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(0.5F * getSize());
    }

    @Override
    public void refreshDimensions() {
        double x = getX();
        double y = getY();
        double z = getZ();
        super.refreshDimensions();
        setPos(x, y, z);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (CHU_SIZE.equals(data)) refreshDimensions();
        super.onSyncedDataUpdated(data);
    }

    public float getSquish(float partialTick) {
        return Mth.lerp(partialTick, oldSquish, squish);
    }

    @Override
    public void tick() {
        squish += (targetSquish - squish) * 0.5F;
        oldSquish = squish;
        super.tick();
        if (onGround() && !wasOnGround) {
            targetSquish = -0.5F;
        } else if (!onGround() && wasOnGround) {
            targetSquish = 1.0F;
        }
        wasOnGround = onGround();
        targetSquish *= 0.6F;
    }

    protected int getJumpDelay() {
        return 10 + random.nextInt(20);
    }

    protected boolean doPlayJumpSound() {
        return getSize() > 0;
    }

    protected SoundEvent getJumpSound() {
        return isTiny() ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP;
    }

    protected float getSoundVolume() {
        return 0.4F * getSize();
    }

    protected float getSoundPitch() {
        float base = isTiny() ? 1.4F : 0.8F;
        return ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * base;
    }

    public boolean isTiny() {
        return getSize() <= 1;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        SpawnGroupData spawnData, CompoundTag dataTag) {
        RandomSource random = level.getRandom();
        int sizeRoll = random.nextInt(3);
        if (sizeRoll < 2 && random.nextFloat() < 0.5F * difficulty.getSpecialMultiplier()) sizeRoll++;
        setSize(1 << sizeRoll, true);
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new ChuFloatGoal(this));
        goalSelector.addGoal(2, new ChuAttackGoal(this));
        goalSelector.addGoal(3, new ChuRandomDirectionGoal(this));
        goalSelector.addGoal(5, new ChuKeepOnJumpingGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void aiStep() {
        super.aiStep();
        if (shockTicks > 0) shockTicks--;
        if (!level().isClientSide && kind() == Kind.CHU_YELLOW && getTarget() != null && tickCount % 160 == 0)
            shockTicks = 40;
        if (!level().isClientSide && tickCount % 40 == 0 && getSize() < 4 && mergeCount < 4
                && getHealth() < getMaxHealth() * .5F) {
            level().getEntitiesOfClass(ChuCreature.class, getBoundingBox().inflate(2.0D), other ->
                    other != this && other.kind() == kind() && other.getSize() == getSize()
                            && other.mergeCount + mergeCount <= 4
                            && other.getHealth() < other.getMaxHealth() * .5F).stream().findFirst().ifPresent(other -> {
                mergeCount += other.mergeCount;
                setHealth(Math.min(getMaxHealth(), getHealth() + other.getHealth()));
                setSize(getSize() * 2, false);
                other.discard();
            });
        }
    }

    private boolean charged() {
        return shockTicks > 0 || kind() == Kind.CHU_YELLOW && tickCount % 160 < 20
                || kind() == Kind.CHU_BLUE && tickCount % 120 < 30;
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL)) return super.hurt(source, amount);
        ElementalDamage.Element element = ElementalDamage.from(source);
        if (kind() == Kind.CHU_RED && element == ElementalDamage.Element.FIRE) return false;
        if (kind() == Kind.CHU_RED && element == ElementalDamage.Element.ICE) amount *= 2.0F;
        if (kind() == Kind.CHU_BLUE && (element == ElementalDamage.Element.ICE || element == ElementalDamage.Element.LIGHTNING
                || element == ElementalDamage.Element.MAGIC)) amount *= .5F;
        if (kind() == Kind.CHU_YELLOW && element == ElementalDamage.Element.LIGHTNING) amount *= .5F;
        if (kind() == Kind.CHU_YELLOW && element == ElementalDamage.Element.MAGIC) return super.hurt(source, amount);
        if (kind() == Kind.CHU_YELLOW && source.is(DamageTypeTags.IS_EXPLOSION)) {
            shockTicks = 0;
            return super.hurt(source, amount);
        }
        if (charged() && element == ElementalDamage.Element.NONE) {
            if (source.getEntity() instanceof LivingEntity attacker && !usesWoodenWeapon(attacker)) {
                attacker.hurt(damageSources().mobAttack(this),
                        Math.max(1.0F, (float) getAttributeValue(Attributes.ATTACK_DAMAGE)));
                attacker.addEffect(new MobEffectInstance(ZSSRegistries.STUN.get(), 30, 0));
            }
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && kind() == Kind.CHU_YELLOW) shockTicks = 40;
        return hurt;
    }

    @Override public boolean doHurtTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean hit = living.hurt(damageSources().mobAttack(this), damage);
        if (!hit) return false;
        switch (kind()) {
            case CHU_RED -> living.setSecondsOnFire(3);
            case CHU_GREEN -> living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            case CHU_BLUE -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            case CHU_YELLOW -> living.addEffect(new MobEffectInstance(ZSSRegistries.STUN.get(), 35, 0));
            default -> { }
        }
        return true;
    }

    @Override
    public void playerTouch(Player player) {
        if (isEffectiveAi()) doHurtTarget(player);
    }

    private static boolean usesWoodenWeapon(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof TieredItem item && item.getTier() == Tiers.WOOD;
    }

    private static final class ChuMoveControl extends MoveControl {
        private final ChuCreature chu;
        private float yRot;
        private int jumpDelay;
        private boolean aggressive;

        private ChuMoveControl(ChuCreature chu) {
            super(chu);
            this.chu = chu;
            yRot = chu.getYRot();
        }

        private void setDirection(float yRot, boolean aggressive) {
            this.yRot = yRot;
            this.aggressive = aggressive;
        }

        private void setWantedMovement(double speed) {
            speedModifier = speed;
            operation = Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            chu.setYRot(rotlerp(chu.getYRot(), yRot, 90.0F));
            chu.yHeadRot = chu.getYRot();
            chu.yBodyRot = chu.getYRot();
            if (operation != Operation.MOVE_TO) {
                chu.setZza(0.0F);
                return;
            }
            operation = Operation.WAIT;
            if (chu.onGround()) {
                chu.setSpeed((float) (speedModifier * chu.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                if (--jumpDelay <= 0) {
                    jumpDelay = chu.getJumpDelay();
                    if (aggressive) jumpDelay /= 3;
                    chu.getJumpControl().jump();
                    if (chu.doPlayJumpSound()) {
                        chu.playSound(chu.getJumpSound(), chu.getSoundVolume(), chu.getSoundPitch());
                    }
                } else {
                    chu.setXxa(0.0F);
                    chu.setZza(0.0F);
                    chu.setSpeed(0.0F);
                }
            } else {
                chu.setSpeed((float) (speedModifier * chu.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            }
        }
    }

    private static final class ChuFloatGoal extends Goal {
        private final ChuCreature chu;

        private ChuFloatGoal(ChuCreature chu) {
            this.chu = chu;
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
            chu.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
            return (chu.isInWater() || chu.isInLava()) && chu.getMoveControl() instanceof ChuMoveControl;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (chu.getRandom().nextFloat() < 0.8F) chu.getJumpControl().jump();
            ((ChuMoveControl) chu.getMoveControl()).setWantedMovement(1.2D);
        }
    }

    private static final class ChuAttackGoal extends Goal {
        private final ChuCreature chu;
        private int growTiredTimer;

        private ChuAttackGoal(ChuCreature chu) {
            this.chu = chu;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = chu.getTarget();
            return target != null && chu.canAttack(target) && chu.getMoveControl() instanceof ChuMoveControl;
        }

        @Override
        public void start() {
            growTiredTimer = 300;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = chu.getTarget();
            return target != null && chu.canAttack(target) && --growTiredTimer > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = chu.getTarget();
            if (target != null) {
                chu.lookAt(target, 10.0F, 10.0F);
                ((ChuMoveControl) chu.getMoveControl()).setDirection(chu.getYRot(), true);
            }
        }
    }

    private static final class ChuRandomDirectionGoal extends Goal {
        private final ChuCreature chu;
        private float chosenDegrees;
        private int nextRandomizeTime;

        private ChuRandomDirectionGoal(ChuCreature chu) {
            this.chu = chu;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return chu.getTarget() == null
                    && (chu.onGround() || chu.isInWater() || chu.isInLava() || chu.hasEffect(MobEffects.LEVITATION))
                    && chu.getMoveControl() instanceof ChuMoveControl;
        }

        @Override
        public void tick() {
            if (--nextRandomizeTime <= 0) {
                nextRandomizeTime = 40 + chu.getRandom().nextInt(60);
                chosenDegrees = chu.getRandom().nextInt(360);
            }
            ((ChuMoveControl) chu.getMoveControl()).setDirection(chosenDegrees, false);
        }
    }

    private static final class ChuKeepOnJumpingGoal extends Goal {
        private final ChuCreature chu;

        private ChuKeepOnJumpingGoal(ChuCreature chu) {
            this.chu = chu;
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !chu.isPassenger();
        }

        @Override
        public void tick() {
            if (chu.getMoveControl() instanceof ChuMoveControl moveControl) moveControl.setWantedMovement(1.0D);
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Size", getSize() - 1);
        tag.putInt("merge_count", mergeCount);
        tag.putInt("shock_ticks", shockTicks);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        setSize(tag.getInt("Size") + 1, false);
        super.readAdditionalSaveData(tag); mergeCount = Math.max(1, Math.min(4, tag.getInt("merge_count")));
        shockTicks = Math.max(0, Math.min(40, tag.getInt("shock_ticks")));
    }
}
