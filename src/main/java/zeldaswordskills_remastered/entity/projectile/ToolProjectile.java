package zeldaswordskills_remastered.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.entity.LegacyCreature;
import zeldaswordskills_remastered.entity.DarknutCreature;
import zeldaswordskills_remastered.entity.ZSSDamageSources;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.HashSet;
import java.util.Set;

/** Server-authoritative projectiles shared by the stage-nine ranged tools. */
public final class ToolProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ToolProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(ToolProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(ToolProjectile.class, EntityDataSerializers.BOOLEAN);
    private final Set<Integer> hitEntities = new HashSet<>();
    private Mode initialMode;
    private double configuredRange;
    private int toolLevel = 1;
    private boolean claw;
    private boolean multi;
    private float configuredDamage = -1.0F;
    private ItemStack carried = ItemStack.EMPTY;
    private HookshotPull hookPull;
    private HookshotPull whipPull;
    private Vec3 hookAnchor;
    private float hookLaunchYaw;
    private int hookPhaseTicks;
    private BlockPos whipAnchorBlock;
    private WhipSwing whipSwing;

    public ToolProjectile(EntityType<? extends ToolProjectile> type, Level level, Mode initialMode) {
        super(type, level);
        this.initialMode = initialMode;
        entityData.set(MODE, initialMode.ordinal());
        configuredRange = initialMode.maximumRange();
        setNoGravity(initialMode.isTethered() || initialMode.isBoomerang());
    }

    public ToolProjectile(EntityType<? extends ToolProjectile> type, Level level, LivingEntity owner, Mode mode) {
        super(type, owner, level);
        this.initialMode = mode;
        entityData.set(MODE, mode.ordinal());
        if (mode == Mode.HOOKSHOT) setPos(hookOrigin(owner, 1.0F));
        else setPos(owner.getX(), owner.getEyeY() - 0.15D, owner.getZ());
        configuredRange = mode.maximumRange();
        setNoGravity(mode.isTethered() || mode.isBoomerang());
    }

    public static Vec3 hookOrigin(Entity owner, float partialTick) {
        return owner.getEyePosition(partialTick).add(0.0D, -0.25D, 0.0D);
    }

    @Override public void shootFromRotation(Entity shooter, float pitch, float yaw, float roll,
                                            float velocity, float inaccuracy) {
        if (mode() == Mode.HOOKSHOT) {
            hookLaunchYaw = yaw;
            Vec3 eye = shooter.getEyePosition();
            Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(configuredRange));
            var aim = level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, shooter));
            // Converge on the crosshair from the lowered origin, then keep that flight direction.
            Vec3 direction = aim.getLocation().subtract(position());
            shoot(direction.x, direction.y, direction.z, velocity, inaccuracy);
            return;
        }
        super.shootFromRotation(shooter, pitch, yaw, roll, velocity, inaccuracy);
    }

    public ToolProjectile configureHook(double range, boolean claw, boolean multi) {
        configuredRange = Math.max(1.0D, range); this.claw = claw; this.multi = multi; return this;
    }

    public ToolProjectile configureWhip(int level) {
        toolLevel = Math.max(1, Math.min(2, level));
        configuredRange = toolLevel == 2 ? 18.0D : Mode.WHIP.maximumRange();
        if (toolLevel == 2) configureDamage(Mode.WHIP.damage() * 2.0F);
        setItem(new ItemStack(ZSSRegistries.getItem(toolLevel == 2 ? "magic_whip" : "whip")));
        return this;
    }

    public boolean isMagicWhip() { return mode() == Mode.WHIP && getItem().is(ZSSRegistries.getItem("magic_whip")); }

    public ToolProjectile configureDamage(float damage) {
        configuredDamage = Math.max(0.0F, damage); return this;
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(MODE, initialMode == null ? 0 : initialMode.ordinal());
        entityData.define(RETURNING, false);
        entityData.define(ANCHORED, false);
    }

    public Mode mode() {
        int value = entityData.get(MODE);
        return Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, value))];
    }

    public ItemStack renderStack() {
        return new ItemStack(getDefaultItem());
    }

    public boolean isAnchored() {
        return entityData.get(ANCHORED);
    }

    public boolean isReturning() {
        return entityData.get(RETURNING);
    }

    public boolean referencesHookEntity(Entity entity) {
        return mode().isTethered() && (ownedBy(entity) || hookPull != null && hookPull.target() == entity
                || whipPull != null && whipPull.target() == entity);
    }

    @Override public boolean canChangeDimensions() {
        return !mode().isTethered() && super.canChangeDimensions();
    }

    @Override public void tick() {
        Mode mode = mode();
        if (mode == Mode.HOOKSHOT) {
            tickHook();
            return;
        }
        if (!level().isClientSide && mode == Mode.WHIP && (!(getOwner() instanceof Player owner)
                || !owner.isAlive() || owner.isRemoved() || owner.isSpectator() || owner.level() != level())) {
            discard();
            return;
        }
        if (mode == Mode.WHIP && isAnchored()) {
            setDeltaMovement(Vec3.ZERO);
            super.tick();
            if (!level().isClientSide && getOwner() instanceof Player owner) {
                if (whipAnchorBlock == null || !level().hasChunkAt(whipAnchorBlock)
                        || !ToolBlockRules.canAttachWhip(level(), whipAnchorBlock, level().getBlockState(whipAnchorBlock))
                        || !owner.isUsingItem() || !owner.getUseItem().is(ZSSRegistries.getItem("magic_whip"))
                        || owner.isPassenger() || distanceToSqr(owner) > configuredRange * configuredRange || tickCount > 6000) {
                    discard();
                } else whipSwing.tick(owner, position(), configuredRange);
            }
            return;
        }
        if (!level().isClientSide && mode == Mode.WHIP && whipPull != null
                && getOwner() instanceof Player player) {
            if (!whipPull.tick(player.position(), 0.2D)) {
                if (whipPull.target() instanceof ItemEntity item && item.isAlive()) {
                    item.setNoPickUpDelay();
                    item.playerTouch(player);
                }
                whipPull.finish();
                whipPull = null;
                discard();
                return;
            }
        }
        if (mode.isTethered()) setNoGravity(true);
        if (mode.isBoomerang()) {
            Entity owner = getOwner();
            if (!(owner instanceof LivingEntity living) || !living.isAlive() || living.level() != level()) {
                if (!level().isClientSide) discard();
                return;
            }
            if (!level().isClientSide && (tickCount >= (mode == Mode.MAGIC_BOOMERANG ? 17 : 16)
                    || distanceToSqr(owner) >= mode.maximumRange() * mode.maximumRange()))
                entityData.set(RETURNING, true);
            if (entityData.get(RETURNING)) {
                Vec3 target = living.position().add(0.0D, 1.0D, 0.0D).subtract(position());
                if (target.lengthSqr() <= 1.0D) {
                    if (!level().isClientSide) finishReturn(living);
                    return;
                }
                setDeltaMovement(target.normalize().scale(Math.min(1.025D, target.length())));
            }
            if (!level().isClientSide && carried.isEmpty()) {
                level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(0.6D), HookshotPull::canCollect).stream().findFirst().ifPresent(item -> {
                    carried = item.getItem().copy();
                    item.discard();
                    entityData.set(RETURNING, true);
                });
            }
        }
        if (!level().isClientSide && mode.isTethered() && getOwner() != null
                && distanceToSqr(getOwner()) > configuredRange * configuredRange) {
            discard();
            return;
        }
        super.tick();
        if (mode == Mode.WHIP && isAnchored() && hookAnchor != null) {
            setPos(hookAnchor);
            setDeltaMovement(Vec3.ZERO);
        }
        if (!level().isClientSide && tickCount > mode.maximumTicks()) {
            if (getOwner() instanceof LivingEntity owner && mode.isBoomerang()) finishReturn(owner); else discard();
        }
        if (level() instanceof ServerLevel server) spawnElementParticles(server, mode, 2, 0.1D);
    }

    private void tickHook() {
        setNoGravity(true);
        if (isAnchored()) setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide) {
            if (!(getOwner() instanceof Player player) || !player.isAlive() || player.isRemoved() || player.isSpectator()
                    || player.level() != level()) {
                discard();
                return;
            }
            if (++hookPhaseTicks > 80) {
                if (entityData.get(RETURNING)) discard(); else retractHook();
            }
            if (isRemoved()) return;
            if (hookPull != null) {
                Entity target = hookPull.target();
                boolean pullingOwner = target == player;
                Vec3 destination = pullingOwner ? hookAnchor.subtract(0.0D, player.getBbHeight() * 0.5D, 0.0D)
                        : HookshotPull.destination(player, target, hookLaunchYaw);
                if (target.level() != level() || (!pullingOwner && !HookshotPull.canGrab(target))
                        || (pullingOwner && player.getBoundingBox().inflate(0.5D).contains(hookAnchor))
                        || distanceToSqr(player) > (configuredRange + 4.0D) * (configuredRange + 4.0D)
                        || !hookPull.tick(destination, pullingOwner ? 0.8D : 0.15D)) {
                    if (target instanceof ItemEntity item && item.distanceToSqr(player) < 1.0D) {
                        item.setNoPickUpDelay();
                        item.playerTouch(player);
                    }
                    retractHook();
                } else {
                    setPos(pullingOwner ? hookAnchor : target.getBoundingBox().getCenter());
                }
            }
            if (!isAnchored() && !entityData.get(RETURNING)
                    && distanceToSqr(player) >= configuredRange * configuredRange) retractHook();
            if (isRemoved()) return;
            if (entityData.get(RETURNING)) {
                Vec3 delta = hookOrigin(player, 1.0F).subtract(position());
                if (delta.lengthSqr() <= 1.0D) { discard(); return; }
                setDeltaMovement(delta.normalize().scale(Math.min(1.2D, delta.length())));
            }
        }
        Vec3 flightMotion = getDeltaMovement();
        super.tick();
        if (!isAnchored()) setDeltaMovement(flightMotion);
        // ThrowableProjectile integrates the pre-impact velocity after its impact callback.
        if (!level().isClientSide && hookPull != null) {
            setPos(hookPull.target() == getOwner() ? hookAnchor : hookPull.target().getBoundingBox().getCenter());
            setDeltaMovement(Vec3.ZERO);
        }
    }

    private void retractHook() {
        if (hookPull != null) { hookPull.finish(); hookPull = null; }
        entityData.set(ANCHORED, false);
        entityData.set(RETURNING, true);
        hookPhaseTicks = 0;
    }

    @Override public void remove(RemovalReason reason) {
        if (hookPull != null) { hookPull.finish(); hookPull = null; }
        if (whipPull != null) { whipPull.finish(); whipPull = null; }
        if (!level().isClientSide && isMagicWhip() && getOwner() instanceof Player player
                && player.isUsingItem() && player.getUseItem().is(ZSSRegistries.getItem("magic_whip")))
            player.stopUsingItem();
        if (!level().isClientSide && reason.shouldDestroy() && !carried.isEmpty()) {
            spawnAtLocation(carried);
            carried = ItemStack.EMPTY;
        }
        super.remove(reason);
    }

    @Override protected boolean canHitEntity(Entity entity) {
        if (entityData.get(RETURNING) || isAnchored() || hitEntities.contains(entity.getId())) return false;
        if (entity instanceof ItemEntity item && !HookshotPull.canCollect(item)) return false;
        if (mode() == Mode.HOOKSHOT && entity instanceof ItemEntity)
            return entity != getOwner() && entity.isAlive() && !entity.isSpectator();
        if (mode() == Mode.WHIP && entity instanceof ItemEntity)
            return entity != getOwner() && entity.isAlive() && !entity.isSpectator();
        return super.canHitEntity(entity);
    }

    @Override protected void onHit(HitResult hit) {
        if (entityData.get(RETURNING) || isAnchored()) return;
        super.onHit(hit);
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (hit.getEntity() instanceof ItemEntity item && !HookshotPull.canCollect(item)) return;
        if (level().isClientSide || !hitEntities.add(hit.getEntity().getId())) return;
        Entity owner = getOwner();
        Mode mode = mode();
        if (mode == Mode.HOOKSHOT) {
            Entity target = hit.getEntity();
            if (target instanceof LivingEntity living)
                living.hurt(ZSSDamageSources.toolProjectile(level(), mode, this, owner),
                        configuredDamage >= 0.0F ? configuredDamage : mode.damage());
            boolean alreadyPulled = !level().getEntitiesOfClass(ToolProjectile.class, target.getBoundingBox().inflate(64.0D),
                    hook -> hook != this && hook.hookPull != null && hook.hookPull.target() == target).isEmpty();
            if (owner instanceof Player && HookshotPull.canGrab(target) && !alreadyPulled) {
                hookPull = new HookshotPull(target);
                entityData.set(ANCHORED, true);
                hookPhaseTicks = 0;
                setDeltaMovement(Vec3.ZERO);
            } else retractHook();
            return;
        }
        if (hit.getEntity() instanceof LivingEntity living) {
            if (living instanceof DarknutCreature darknut && darknut.reflectBodyProjectile(this, hit.getLocation())) return;
            living.hurt(ZSSDamageSources.toolProjectile(level(), mode, this, owner),
                    configuredDamage >= 0.0F ? configuredDamage : mode.damage());
            switch (mode) {
                case FIRE -> living.setSecondsOnFire(5);
                case ICE -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                case LIGHTNING -> living.addEffect(new MobEffectInstance(ZSSRegistries.STUN.get(), 50, 0));
                case SEED, NUT -> living.addEffect(new MobEffectInstance(ZSSRegistries.STUN.get(), mode == Mode.NUT ? 40 : 15));
                default -> { }
            }
            if (mode == Mode.WHIP) {
                if (toolLevel == 2) living.addEffect(new MobEffectInstance(ZSSRegistries.STUN.get(), 40));
                Vec3 pull = getOwner() == null ? Vec3.ZERO : getOwner().position().subtract(living.position());
                if (pull.lengthSqr() > 0.25D) living.setDeltaMovement(pull.normalize().scale(0.7D));
            }
            if (mode == Mode.WHIP && living instanceof LegacyCreature creature && owner instanceof Player player)
                creature.tryWhipLoot(player);
        } else if (mode == Mode.WHIP && hit.getEntity() instanceof ItemEntity item && getOwner() != null) {
            whipPull = new HookshotPull(item);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        applyRodImpact(hit.getLocation());
        if (level() instanceof ServerLevel server) spawnElementParticles(server, mode, 16, 0.3D);
        if (mode.isBoomerang()) entityData.set(RETURNING, true);
        else discard();
    }

    @Override protected void onHitBlock(BlockHitResult hit) {
        Mode mode = mode();
        if (mode == Mode.HOOKSHOT && (isAnchored() || isReturning())) return;
        if (!level().isClientSide && level() instanceof ServerLevel server) {
            if (mode == Mode.HOOKSHOT && getOwner() instanceof net.minecraft.server.level.ServerPlayer player) {
                var state = server.getBlockState(hit.getBlockPos());
                boolean hookable = ToolBlockRules.canHook(state, hit.getDirection(), claw, multi);
                if (hookable) {
                    if (state.getBlock() instanceof ZSSBlockInteractions.Hookable target && target.hookBreaks(state, hit.getDirection()))
                        server.destroyBlock(hit.getBlockPos(), true, player);
                    setPos(hit.getLocation());
                    hookAnchor = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.05D));
                    hookPull = new HookshotPull(player);
                    hookPhaseTicks = 0;
                    setDeltaMovement(Vec3.ZERO);
                    entityData.set(ANCHORED, true);
                    hasImpulse = true;
                } else {
                    if (!(state.getBlock() instanceof ZSSBlockInteractions.Hookable)
                            && ToolBlockRules.hookBreaks(state, claw, multi) && player.mayBuild()
                            && server.mayInteract(player, hit.getBlockPos())
                            && state.getDestroySpeed(server, hit.getBlockPos()) >= 0
                            && net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(server,
                                    player.gameMode.getGameModeForPlayer(), player, hit.getBlockPos()) != -1)
                        server.destroyBlock(hit.getBlockPos(), false, player);
                    retractHook();
                }
            } else if (mode == Mode.WHIP && getOwner() instanceof net.minecraft.server.level.ServerPlayer player) {
                boolean activated = ZSSBlockInteractions.activateWhip(server, hit.getBlockPos(), player, toolLevel);
                if (!activated && toolLevel == 2 && player.isUsingItem()
                        && ToolBlockRules.canAttachWhip(server, hit.getBlockPos(), server.getBlockState(hit.getBlockPos()))) {
                    whipAnchorBlock = hit.getBlockPos().immutable();
                    whipSwing = new WhipSwing();
                    hookAnchor = hit.getLocation();
                    setPos(hit.getLocation());
                    setDeltaMovement(Vec3.ZERO);
                    entityData.set(ANCHORED, true);
                    return;
                }
            }
            if (mode == Mode.ICE) {
                BlockPos source = hit.getBlockPos();
                if (server.getBlockState(source).is(Blocks.WATER)) server.setBlockAndUpdate(source, Blocks.ICE.defaultBlockState());
                else if (server.getBlockState(source).is(Blocks.LAVA)) server.setBlockAndUpdate(source, Blocks.OBSIDIAN.defaultBlockState());
            }
            // Keep impacts on a block boundary in the space outside the struck face.
            applyRodImpact(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.001D)));
        }
        if (mode.isBoomerang()) entityData.set(RETURNING, true);
        else if (mode != Mode.HOOKSHOT) discard();
        if (level() instanceof ServerLevel server) spawnElementParticles(server, mode, 16, 0.3D);
    }

    private void applyRodImpact(Vec3 impact) {
        if (!(level() instanceof ServerLevel server)) return;
        if (mode() == Mode.WIND) {
            Vec3 horizontal = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize();
            for (Entity target : server.getEntities(getOwner(), new AABB(impact, impact).inflate(2.0D),
                    entity -> entity.isAlive() && !entity.isSpectator()
                            && (entity instanceof LivingEntity || entity instanceof ItemEntity))) {
                // Vertical shots spread out from the impact instead of losing their horizontal push.
                Vec3 direction = horizontal.lengthSqr() > 1.0E-8D ? horizontal
                        : target.position().subtract(impact).multiply(1.0D, 0.0D, 1.0D).normalize();
                target.push(direction.x * 0.8D, target instanceof LivingEntity ? 0.65D : 0.15D, direction.z * 0.8D);
                target.hurtMarked = true;
            }
        } else if (mode() == Mode.ICE || mode() == Mode.FIRE) {
            BlockPos source = BlockPos.containing(impact);
            for (BlockPos pos : BlockPos.betweenClosed(source.offset(-1, 0, -1), source.offset(1, 1, 1))) {
                if (mode() == Mode.ICE) {
                    if (server.getBlockState(pos).is(BlockTags.FIRE)) server.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                } else if (server.getBlockState(pos).canBeReplaced()
                        && BaseFireBlock.canBePlacedAt(server, pos, net.minecraft.core.Direction.UP)) {
                    server.setBlockAndUpdate(pos, BaseFireBlock.getState(server, pos));
                }
            }
        }
    }

    private void spawnElementParticles(ServerLevel level, Mode mode, int count, double spread) {
        ParticleOptions particle = switch (mode) {
            case FIRE -> ParticleTypes.FLAME;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case WIND -> ZSSRegistries.CYCLONE.get();
            default -> null;
        };
        if (particle != null) {
            level.sendParticles(particle, getX(), getY(), getZ(), count, spread, spread, spread, 0.02D);
        }
    }

    private void finishReturn(LivingEntity owner) {
        if (owner instanceof net.minecraft.server.level.ServerPlayer player)
            zeldaswordskills_remastered.item.ProgressionItem.consumeSmallHearts(player, carried, true);
        if (!carried.isEmpty()) {
            if (!(owner instanceof Player player) || !player.getInventory().add(carried))
                owner.spawnAtLocation(carried);
            carried = ItemStack.EMPTY;
        }
        discard();
    }

    @Override protected Item getDefaultItem() {
        return ZSSRegistries.getItem(switch (mode()) {
            case BOOMERANG -> "boomerang";
            case MAGIC_BOOMERANG -> "magic_boomerang";
            case NUT -> "deku_nut";
            case SEED -> "bomb_flower_seed";
            case FIRE -> "fire_rod";
            case ICE -> "ice_rod";
            case LIGHTNING -> "ether_medallion";
            case WIND -> "tornado_rod";
            case HOOKSHOT -> "hookshot";
            case WHIP -> "whip";
        });
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("mode", mode().id().toString());
        tag.putBoolean("returning", entityData.get(RETURNING));
        tag.putBoolean("anchored", entityData.get(ANCHORED));
        tag.putDouble("configured_range", configuredRange);
        tag.putInt("tool_level", toolLevel);
        tag.putBoolean("claw", claw);
        tag.putBoolean("multi", multi);
        if (mode() == Mode.HOOKSHOT) tag.putFloat("hook_launch_yaw", hookLaunchYaw);
        tag.putFloat("configured_damage", configuredDamage);
        if (!carried.isEmpty()) tag.put("carried", carried.save(new CompoundTag()));
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("mode")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("mode"));
            Mode savedMode = null;
            for (Mode mode : Mode.values()) if (mode.id().equals(id)) savedMode = mode;
            if (savedMode == null) { discard(); return; }
            entityData.set(MODE, savedMode.ordinal());
        }
        entityData.set(RETURNING, tag.getBoolean("returning"));
        entityData.set(ANCHORED, tag.getBoolean("anchored"));
        if (mode() == Mode.WHIP && isAnchored()) { discard(); return; }
        configuredRange = Math.max(1.0D, tag.getDouble("configured_range"));
        toolLevel = Math.max(1, tag.getInt("tool_level"));
        claw = tag.getBoolean("claw");
        multi = tag.getBoolean("multi");
        hookLaunchYaw = tag.getFloat("hook_launch_yaw");
        configuredDamage = tag.contains("configured_damage") ? Math.max(0.0F, tag.getFloat("configured_damage")) : -1.0F;
        carried = tag.contains("carried") ? ItemStack.of(tag.getCompound("carried")) : ItemStack.EMPTY;
    }

    public enum Mode {
        BOOMERANG("boomerang", 2.0F, 12.0D, 50), MAGIC_BOOMERANG("magic_boomerang", 4.0F, 24.0D, 70),
        NUT("deku_nut", 2.5F, 16.0D, 40), SEED("seedshot", 2.5F, 20.0D, 50),
        FIRE("fire", 8.0F, 28.0D, 60), ICE("ice", 6.0F, 28.0D, 60),
        LIGHTNING("lightning", 6.0F, 28.0D, 60), WIND("wind", 4.0F, 28.0D, 60),
        HOOKSHOT("hookshot", 2.0F, 24.0D, 45), WHIP("whip", 5.0F, 12.0D, 30);
        private final ResourceLocation id;
        private final float damage;
        private final double maximumRange;
        private final int maximumTicks;
        Mode(String path, float damage, double maximumRange, int maximumTicks) {
            this.id = ResourceLocation.fromNamespaceAndPath(zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID, path);
            this.damage = damage; this.maximumRange = maximumRange; this.maximumTicks = maximumTicks;
        }
        public ResourceLocation id() { return id; }
        public float damage() { return damage; }
        public double maximumRange() { return maximumRange; }
        public int maximumTicks() { return maximumTicks; }
        public boolean isBoomerang() { return this == BOOMERANG || this == MAGIC_BOOMERANG; }
        public boolean isTethered() { return this == HOOKSHOT || this == WHIP; }
    }
}
