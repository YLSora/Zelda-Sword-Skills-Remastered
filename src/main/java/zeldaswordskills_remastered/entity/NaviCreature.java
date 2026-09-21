package zeldaswordskills_remastered.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.TargetingService;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/** A transient companion entity; its identity and active state belong to the player. */
public final class NaviCreature extends LegacyCreature {
    public static final String OWNER_NAVI_KEY = "zss_navi_uuid";
    private static final double TELEPORT_DISTANCE_SQR = 32.0D * 32.0D;
    private static final double OWNER_TETHER = 3.5D;
    private static final double OWNER_TETHER_SQR = OWNER_TETHER * OWNER_TETHER;
    private static final double OWNER_ORBIT_RADIUS = 1.5D;
    private UUID ownerUuid;
    private final NaviFlight.Motion ownerMotion = new NaviFlight.Motion();
    private final NaviFlight.SphereOrbit ownerOrbit = new NaviFlight.SphereOrbit();
    private final RandomSource ownerOrbitSpeedRandom;
    private final NaviFlight.LockVoice lockVoice = new NaviFlight.LockVoice();
    private final Vec3 phases;
    private double orbitAngle;
    private double activity;
    private double ownerOrbitAngularSpeed;
    private int ownerOrbitSpeedTicks;
    private Vec3 orbitRight;
    private UUID orbitTarget;
    private Vec3 stationaryAnchor;
    private Vec3 previousTarget;
    private Vec3 moveTarget;
    private Vec3 feedForward = Vec3.ZERO;
    private double moveSpeed;
    private int blockedTicks;
    private int ambientVoiceTicks;

    public NaviCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level, kind);
        setPersistenceRequired();
        setNoGravity(true);
        ownerOrbitSpeedRandom = RandomSource.create(random.nextLong());
        phases = new Vec3(random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI,
                random.nextDouble() * Mth.TWO_PI);
        ambientVoiceTicks = 540 + random.nextInt(121);
    }

    public void setOwner(Player owner) { ownerUuid = owner.getUUID(); }
    public UUID ownerUuid() { return ownerUuid; }

    @Override protected void registerGoals() { goalSelector.addGoal(1, new NaviFlightGoal(this)); }
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    // She must remain selectable for the owner's empty-bottle capture interaction.
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean canAddPassenger(Entity passenger) { return false; }
    @Override public boolean skipAttackInteraction(Entity entity) { return true; }
    // Empty-hand interaction remains a harmless vanilla interaction window; every held item is rejected
    // here, while the dedicated empty-bottle capture event is handled before vanilla interaction.
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).isEmpty() ? InteractionResult.PASS : InteractionResult.FAIL;
    }
    @Override public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
        return player.getItemInHand(hand).isEmpty() ? InteractionResult.PASS : InteractionResult.FAIL;
    }
    @Override protected void doPush(Entity pusher) {}
    @Override protected void pushEntities() {}
    // Dimension following is recreated by NaviService at the owner's position, never through portals.
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean canBeSeenAsEnemy() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && super.hurt(source, amount);
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override
    public void aiStep() {
        fallDistance = 0.0F;
        if (!level().isClientSide) {
            if (ownerUuid == null) {
                Player nearest = level().getNearestPlayer(this, 16.0D);
                if (nearest instanceof ServerPlayer player && player.isAlive()) {
                    if (savedNaviId(player).isEmpty()) {
                        player.getPersistentData().putUUID(OWNER_NAVI_KEY, getUUID());
                        setOwner(player);
                        NaviService.markActive(player, true);
                    } else {
                        discard();
                        NaviService.summon(player);
                        return;
                    }
                } else if (tickCount > 20) {
                    discard();
                    return;
                }
            } else {
                ServerPlayer owner = onlineOwner().orElse(null);
                if (owner == null || !owner.isAlive() || owner.level() != level()
                        || !NaviService.isActive(owner) || !getUUID().equals(savedNaviId(owner).orElse(null))) {
                    discard();
                    return;
                }
            }
        }
        super.aiStep();
        fallDistance = 0.0F;
    }

    public Optional<ServerPlayer> onlineOwner() {
        if (ownerUuid == null || !(level() instanceof ServerLevel)) return Optional.empty();
        Player local = level().getPlayerByUUID(ownerUuid);
        if (local instanceof ServerPlayer player && !player.isRemoved()) return Optional.of(player);
        return Optional.empty();
    }

    public Optional<LivingEntity> lockedEnemy() {
        return onlineOwner().flatMap(owner -> ZSSCapabilities.get(owner).resolve()
                .flatMap(data -> TargetingService.getLockedTarget(owner, data)));
    }

    private void tickFlightState() {
        ServerPlayer owner = onlineOwner().orElse(null);
        if (owner == null || owner.level() != level() || !owner.isAlive()) return;
        Vec3 ownerVelocity = ownerMotion.sample(owner.position(), level().getGameTime());
        activity = Mth.lerp(0.12D, activity, Mth.clamp(ownerVelocity.length() / 0.28D, 0, 1));
        LivingEntity enemy = lockedEnemy().orElse(null);
        UUID targetId = enemy == null ? null : enemy.getUUID();
        boolean stationary = owner.isShiftKeyDown();
        boolean stationaryChanged = stationary != (stationaryAnchor != null);
        tickVoice(owner, targetId);
        if (!stationary && (distanceToSqr(owner) > (enemy == null ? OWNER_TETHER_SQR : TELEPORT_DISTANCE_SQR)
                || blockedTicks >= 20)) {
            if (relocateNear(owner)) return;
        }
        Vec3 desired;
        boolean changed = stationaryChanged || !java.util.Objects.equals(orbitTarget, targetId);
        if (stationary) {
            Vec3 drift = NaviFlight.smallHover(tickCount, phases);
            if (stationaryAnchor == null) stationaryAnchor = position().subtract(drift);
            desired = stationaryAnchor.add(drift);
            moveSpeed = 0.08D;
        } else if (enemy == null) {
            stationaryAnchor = null;
            Vec3 center = new Vec3(owner.getX(), owner.getBoundingBox().maxY + 0.15D, owner.getZ());
            if (changed || !ownerOrbit.isInitialized()) ownerOrbit.reset(random, position().subtract(center));
            if (--ownerOrbitSpeedTicks <= 0) {
                ownerOrbitSpeedTicks = 60 + ownerOrbitSpeedRandom.nextInt(141);
                double blocksPerTick = (1.0D + ownerOrbitSpeedRandom.nextDouble() * 0.25D) / 20.0D;
                ownerOrbitAngularSpeed = blocksPerTick / OWNER_ORBIT_RADIUS;
            }
            double angularStep = ownerOrbitAngularSpeed + activity * 0.0275D;
            desired = center.add(ownerOrbit.tick(random, angularStep, OWNER_ORBIT_RADIUS));
            double anchorSpeed = previousTarget == null || changed ? 0 : desired.distanceTo(previousTarget);
            moveSpeed = Math.min(1.5D, Math.max(0.3D, Math.max(ownerVelocity.length(), anchorSpeed) * 0.6D + 0.2D));
        } else {
            stationaryAnchor = null;
            Vec3 center = enemy.getBoundingBox().getCenter().add(0, 0.45D, 0);
            Vec3 normal = NaviFlight.facingNormal(owner.getEyePosition(), center);
            orbitRight = NaviFlight.planeRight(normal, orbitRight);
            orbitAngle += 0.075D + activity * 0.055D;
            desired = center.add(NaviFlight.orbit(orbitAngle, phases, enemy.getBbWidth(), enemy.getBbHeight(), normal, orbitRight));
            double anchorSpeed = previousTarget == null || changed ? 0 : desired.distanceTo(previousTarget);
            moveSpeed = Math.min(3.0D, Math.max(0.6D, Math.max(ownerVelocity.length(), anchorSpeed) * 1.2D + 0.4D));
        }
        orbitTarget = targetId;
        Vec3 clearTarget = stationary ? (hasRoom(desired) ? desired : null) : clearPoint(desired);
        moveTarget = clearTarget == null ? (stationary ? position() : desired) : clearTarget;
        boolean displaced = clearTarget == null || clearTarget.distanceToSqr(desired) > 1.0E-6D;
        feedForward = previousTarget == null || changed || displaced
                ? Vec3.ZERO : NaviFlight.limit(desired.subtract(previousTarget), moveSpeed);
        previousTarget = desired;
        getLookControl().setLookAt(enemy == null ? owner : enemy, 40, 40);
    }

    private void tickVoice(ServerPlayer owner, @Nullable UUID targetId) {
        if (lockVoice.update(targetId)) {
            owner.playNotifySound(ZSSRegistries.NAVI_VOICE.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        } else if (--ambientVoiceTicks <= 0) {
            owner.playNotifySound(ZSSRegistries.NAVI_FLOAT.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        } else {
            return;
        }
        // Leave 27-33 seconds between an ambient voice and the preceding voice.
        ambientVoiceTicks = 540 + random.nextInt(121);
    }

    @Override
    public void travel(Vec3 input) {
        if (!isEffectiveAi()) {
            super.travel(input);
            return;
        }
        Vec3 velocity = moveTarget == null ? getDeltaMovement().scale(0.6D)
                : NaviFlight.steer(getDeltaMovement(), moveTarget.subtract(position()), feedForward, moveSpeed);
        ServerPlayer owner = onlineOwner().orElse(null);
        if (owner != null && orbitTarget == null && stationaryAnchor == null) {
            Vec3 nextOffset = position().add(velocity).subtract(owner.position());
            velocity = owner.position().add(NaviFlight.limit(nextOffset, OWNER_TETHER)).subtract(position());
        }
        Vec3 requestedVelocity = velocity;
        var obstacles = new ArrayList<AABB>();
        AABB search = getBoundingBox().inflate(NaviObstacleAvoidance.lookAhead(velocity) + NaviObstacleAvoidance.CLEARANCE);
        for (var shape : level().getBlockCollisions(this, search)) obstacles.addAll(shape.toAabbs());
        velocity = stationaryAnchor != null
                ? NaviObstacleAvoidance.limitHover(getBoundingBox(), velocity, obstacles)
                : NaviObstacleAvoidance.steer(getBoundingBox(), velocity, obstacles, candidate -> {
                    Vec3 next = position().add(candidate);
                    AABB nextBox = getBoundingBox().move(candidate);
                    return (owner == null || orbitTarget != null || next.distanceToSqr(owner.position()) <= OWNER_TETHER_SQR)
                            && nextBox.minY >= level().getMinBuildHeight() && nextBox.maxY < level().getMaxBuildHeight()
                            && level().getWorldBorder().isWithinBounds(nextBox);
                });
        Vec3 before = position();
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);
        if (owner != null && orbitTarget == null && stationaryAnchor == null && distanceToSqr(owner) > OWNER_TETHER_SQR) {
            relocateNear(owner);
        }
        boolean blocked = stationaryAnchor == null && requestedVelocity.lengthSqr() > 0.0004D
                && (horizontalCollision || verticalCollision
                || position().distanceToSqr(before) < 0.0001D);
        blockedTicks = blocked ? blockedTicks + 1 : 0;
        calculateEntityAnimation(false);
    }

    private boolean hasRoom(Vec3 point) {
        AABB box = getBoundingBox().move(point.subtract(position()));
        return point.y >= level().getMinBuildHeight() && box.maxY < level().getMaxBuildHeight()
                && level().hasChunkAt(BlockPos.containing(point)) && level().getWorldBorder().isWithinBounds(box)
                && level().noCollision(this, box);
    }

    @Nullable
    private Vec3 clearPoint(Vec3 desired) {
        if (hasRoom(desired)) return desired;
        for (Vec3 offset : CLEAR_OFFSETS) {
            Vec3 point = desired.add(offset);
            if (hasRoom(point)) return point;
        }
        return null;
    }

    private static final Vec3[] CLEAR_OFFSETS = {
            new Vec3(0, 0.6D, 0), new Vec3(0, -0.6D, 0), new Vec3(0.7D, 0, 0),
            new Vec3(-0.7D, 0, 0), new Vec3(0, 0, 0.7D), new Vec3(0, 0, -0.7D)
    };

    public boolean relocateNear(Player owner) {
        Vec3 point = clearPoint(owner.getEyePosition().add(0.8D, 0.1D, 0.6D));
        if (point == null) point = clearPoint(owner.getEyePosition());
        if (point == null) return false;
        teleportTo(point.x, point.y, point.z);
        resetFlight();
        return true;
    }

    private void resetFlight() {
        setDeltaMovement(Vec3.ZERO);
        moveTarget = null;
        previousTarget = null;
        feedForward = Vec3.ZERO;
        stationaryAnchor = null;
        blockedTicks = 0;
        ownerMotion.reset();
    }

    private static final class NaviFlightGoal extends Goal {
        private final NaviCreature navi;
        private NaviFlightGoal(NaviCreature navi) {
            this.navi = navi;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() { return !navi.level().isClientSide && navi.onlineOwner().isPresent(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void stop() { navi.resetFlight(); }
        @Override public void tick() { navi.tickFlightState(); }
    }

    public static UUID storedNaviId(Player owner) {
        CompoundTag tag = owner.getPersistentData();
        if (!tag.hasUUID(OWNER_NAVI_KEY)) tag.putUUID(OWNER_NAVI_KEY, UUID.randomUUID());
        return tag.getUUID(OWNER_NAVI_KEY);
    }

    public static Optional<UUID> savedNaviId(Player owner) {
        CompoundTag tag = owner.getPersistentData();
        return tag.hasUUID(OWNER_NAVI_KEY) ? Optional.of(tag.getUUID(OWNER_NAVI_KEY)) : Optional.empty();
    }

    public static void clearStoredNaviId(Player owner) { owner.getPersistentData().remove(OWNER_NAVI_KEY); }

    public static Optional<NaviCreature> findOwned(ServerLevel level, UUID naviId) {
        return level.getEntity(naviId) instanceof NaviCreature navi && navi.isAlive() ? Optional.of(navi) : Optional.empty();
    }

    @Nullable
    public static NaviCreature spawnFor(ServerPlayer owner, ServerLevel level) {
        UUID naviId = storedNaviId(owner);
        NaviCreature reuse = findOwned(level, naviId).orElse(null);
        if (reuse != null) {
            reuse.setOwner(owner);
            if (reuse.distanceToSqr(owner) > TELEPORT_DISTANCE_SQR) reuse.relocateNear(owner);
            return reuse;
        }
        NaviCreature created = (NaviCreature) ZSSRegistries.NAVI.get().create(level);
        if (created == null) return null;
        created.setUUID(naviId);
        created.setOwner(owner);
        created.setPos(owner.position());
        if (!created.relocateNear(owner) || !level.addFreshEntity(created)) {
            created.discard();
            return null;
        }
        return created;
    }
}
