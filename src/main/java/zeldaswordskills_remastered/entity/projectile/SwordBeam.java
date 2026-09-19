package zeldaswordskills_remastered.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.FatalStrike;
import zeldaswordskills_remastered.entity.DarknutCreature;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.HashSet;
import java.util.Set;

public final class SwordBeam extends ThrowableItemProjectile {
    private float damage;
    private float maximumRange;
    private float traveled;
    private boolean piercing;
    private FatalStrike.Attempt focusAttempt;
    private final Set<Integer> hitEntities = new HashSet<>();

    public SwordBeam(EntityType<? extends SwordBeam> type, Level level) {
        super(type, level);
    }

    public SwordBeam(Level level, ServerPlayer owner, float damage, float maximumRange, boolean piercing) {
        super(ZSSRegistries.SWORD_BEAM.get(), owner, level);
        this.damage = Math.max(0.0F, damage);
        this.maximumRange = Math.max(1.0F, maximumRange);
        this.piercing = piercing;
        ZSSCapabilities.get(owner).ifPresent(data -> focusAttempt = FatalStrike.watchAttack(owner, data));
        Vec3 direction = owner.getLookAngle().normalize();
        setPos(owner.getX(), owner.getEyeY() - 0.2D, owner.getZ());
        setDeltaMovement(direction.scale(0.9D));
        setYRot(owner.getYRot());
        setXRot(owner.getXRot());
    }

    @Override
    public void tick() {
        // Clip the collision sweep before moving, including the final partial tick.
        double step = getDeltaMovement().length();
        if (!level().isClientSide) {
            if (getOwner() instanceof ServerPlayer player && ZSSCapabilities.get(player).map(data -> data.activeSkillLevel(
                    zeldaswordskills_remastered.registry.ZSSContentIds.SWORD_BEAM) <= 0).orElse(false)) {
                discard();
                return;
            }
            double remaining = maximumRange - traveled;
            if (remaining <= 0.0D || tickCount >= 80) {
                discard();
                return;
            }
            if (step > remaining) {
                setDeltaMovement(getDeltaMovement().scale(remaining / step));
                step = remaining;
            }
        }
        super.tick();
        if (!level().isClientSide) {
            traveled += (float) step;
            if (traveled >= maximumRange) discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return !hitEntities.contains(entity.getId()) && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide || !hitEntities.add(hit.getEntity().getId())) return;
        if (hit.getEntity() instanceof DarknutCreature darknut && darknut.reflectBodyProjectile(this, hit.getLocation())) return;
        Entity owner = getOwner();
        if (owner instanceof ServerPlayer player) {
            boolean struck = hit.getEntity().hurt(player.damageSources().thrown(this, player), damage);
            if (focusAttempt != null) focusAttempt.hit(struck);
            damage *= 0.8F;
        }
        if (!piercing) discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (focusAttempt != null) {
            focusAttempt.close();
            focusAttempt = null;
        }
        super.remove(reason);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = Math.max(0.0F, tag.getFloat("damage"));
        maximumRange = Math.max(1.0F, tag.getFloat("maximum_range"));
        traveled = Math.max(0.0F, tag.getFloat("traveled"));
        piercing = tag.getBoolean("piercing");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putFloat("maximum_range", maximumRange);
        tag.putFloat("traveled", traveled);
        tag.putBoolean("piercing", piercing);
    }

    @Override
    protected Item getDefaultItem() {
        return ZSSRegistries.MASTER_SWORD.get();
    }
}
