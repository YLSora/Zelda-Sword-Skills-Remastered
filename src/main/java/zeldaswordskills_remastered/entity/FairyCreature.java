package zeldaswordskills_remastered.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Floating ambient fairy that remains catchable by a Fairy Bottle. */
public final class FairyCreature extends LegacyCreature {
    private net.minecraft.core.BlockPos habitatPos;
    private Vec3 spawnOrigin;
    private final NaviFlight.Wander wander = new NaviFlight.Wander();
    private final Vec3 phases;
    private Vec3 moveTarget;
    private Vec3 targetMotion = Vec3.ZERO;

    private Vec3 spawnOrigin() {
        if (spawnOrigin == null) spawnOrigin = position();
        return spawnOrigin;
    }

    public void bindToHabitat(net.minecraft.core.BlockPos pos) {
        habitatPos = pos.immutable();
        setPersistenceRequired();
    }

    public boolean belongsToHabitat(net.minecraft.core.BlockPos pos) { return pos.equals(habitatPos); }
    public boolean canUpgradeEquipment() { return habitatPos != null && isAlive(); }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (habitatPos != null) tag.putLong("FairyPool", habitatPos.asLong());
        Vec3 origin = spawnOrigin();
        var savedOrigin = new net.minecraft.nbt.CompoundTag();
        savedOrigin.putDouble("X", origin.x);
        savedOrigin.putDouble("Y", origin.y);
        savedOrigin.putDouble("Z", origin.z);
        tag.put("WanderOrigin", savedOrigin);
    }

    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        habitatPos = tag.contains("FairyPool", net.minecraft.nbt.Tag.TAG_LONG)
                ? net.minecraft.core.BlockPos.of(tag.getLong("FairyPool")) : null;
        if (habitatPos != null) setPersistenceRequired();
        spawnOrigin = null;
        if (tag.contains("WanderOrigin", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var origin = tag.getCompound("WanderOrigin");
            spawnOrigin = new Vec3(origin.getDouble("X"), origin.getDouble("Y"), origin.getDouble("Z"));
        }
        moveTarget = null;
        targetMotion = Vec3.ZERO;
    }

    @Override public void aiStep() {
        if (!level().isClientSide) spawnOrigin();
        super.aiStep();
        if (!level().isClientSide && position().distanceToSqr(spawnOrigin()) > 64.0D) {
            Vec3 center = spawnOrigin();
            moveTo(center.x, center.y, center.z);
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override public boolean canChangeDimensions() { return habitatPos == null && super.canChangeDimensions(); }

    @Override public net.minecraft.world.entity.Entity changeDimension(net.minecraft.server.level.ServerLevel destination,
                                                                       net.minecraftforge.common.util.ITeleporter teleporter) {
        var transferred = super.changeDimension(destination, teleporter);
        if (transferred instanceof FairyCreature fairy) {
            fairy.spawnOrigin = fairy.position();
            fairy.moveTarget = null;
            fairy.targetMotion = Vec3.ZERO;
        }
        return transferred;
    }

    @Override public boolean fireImmune() {
        return habitatPos != null && level().dimension().equals(Level.NETHER) || super.fireImmune();
    }

    public FairyCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level, kind);
        phases = new Vec3(random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI,
                random.nextDouble() * Mth.TWO_PI);
        setNoGravity(true);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatWanderGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override public boolean isNoGravity() { return true; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean canBeSeenAsEnemy() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean skipAttackInteraction(Entity entity) { return true; }

    @Override public void travel(Vec3 input) {
        if (!isEffectiveAi()) {
            super.travel(input);
            return;
        }
        Vec3 velocity = moveTarget == null ? getDeltaMovement().scale(0.6D)
                : NaviFlight.steer(getDeltaMovement(), moveTarget.subtract(position()), targetMotion, 0.08D);
        Vec3 nextOffset = position().add(velocity).subtract(spawnOrigin());
        velocity = spawnOrigin().add(NaviFlight.limit(nextOffset, 8.0D)).subtract(position());
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);
        calculateEntityAnimation(false);
    }

    private static final class FloatWanderGoal extends Goal {
        private final FairyCreature fairy;

        private FloatWanderGoal(FairyCreature fairy) {
            this.fairy = fairy;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void tick() {
            Vec3 target = fairy.spawnOrigin().add(fairy.wander.tick(fairy.random, fairy.tickCount, fairy.phases, 6.0D));
            fairy.targetMotion = fairy.moveTarget == null ? Vec3.ZERO : target.subtract(fairy.moveTarget);
            fairy.moveTarget = target;
        }
    }
}
