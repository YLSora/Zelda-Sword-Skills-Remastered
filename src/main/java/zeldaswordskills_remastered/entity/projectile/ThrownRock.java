package zeldaswordskills_remastered.entity.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.nbt.CompoundTag;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.entity.DarknutCreature;

public final class ThrownRock extends ThrowableItemProjectile {
    private float damage = 2.0F;
    public ThrownRock(EntityType<? extends ThrownRock> type, Level level) {
        super(type, level);
    }

    public ThrownRock(Level level, LivingEntity owner) {
        super(ZSSRegistries.ROCK.get(), owner, level);
    }

    public ThrownRock configureDamage(float damage) { this.damage = Math.max(0.0F, damage); return this; }

    @Override
    protected Item getDefaultItem() {
        return ZSSRegistries.THROWING_ROCK.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        hit.getEntity().hurt(damageSources().thrown(this, getOwner()), damage);
    }

    @Override
    protected void onHit(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof DarknutCreature darknut
                && darknut.reflectBodyProjectile(this, entityHit.getLocation())) return;
        super.onHit(hit);
        if (!level().isClientSide) {
            discard();
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putFloat("damage", damage); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); damage = Math.max(0.0F, tag.getFloat("damage")); }
}
