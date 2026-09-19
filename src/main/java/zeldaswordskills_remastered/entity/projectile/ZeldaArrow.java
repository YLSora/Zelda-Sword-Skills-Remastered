package zeldaswordskills_remastered.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import zeldaswordskills_remastered.item.ZeldaCombatItems;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.entity.ElementalDamage;
import zeldaswordskills_remastered.entity.DarknutCreature;
import zeldaswordskills_remastered.entity.ZSSDamageSources;

import java.util.Locale;

/** Persistent arrow entity retaining its exact Zelda ammunition type. */
public final class ZeldaArrow extends AbstractArrow {
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(ZeldaArrow.class, EntityDataSerializers.STRING);

    public ZeldaArrow(EntityType<? extends ZeldaArrow> type, Level level) { super(type, level); }

    public ZeldaArrow(EntityType<? extends ZeldaArrow> type, Level level, LivingEntity owner, ZeldaCombatItems.ArrowKind kind) {
        super(type, owner, level);
        entityData.set(KIND, kind.name().toLowerCase(Locale.ROOT));
        getPersistentData().putString("zss_arrow", kind.name().toLowerCase(Locale.ROOT));
        if (kind == ZeldaCombatItems.ArrowKind.LIGHT) setBaseDamage(5.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIND, "bomb");
    }

    public ZeldaCombatItems.ArrowKind kind() {
        return ZeldaCombatItems.ArrowKind.valueOf(entityData.get(KIND).toUpperCase(Locale.ROOT));
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        ElementalDamage.Element element = elementalKind();
        if (element == ElementalDamage.Element.NONE) {
            super.onHitEntity(hit);
            return;
        }
        if (!level().isClientSide) {
            if (hit.getEntity() instanceof LivingEntity living) {
                Entity owner = getOwner();
                boolean damaged = living.hurt(ZSSDamageSources.projectile(level(), element, this, owner), (float) getBaseDamage());
                if (damaged) {
                    super.doPostHurtEffects(living);
                    switch (kind()) {
                        case FIRE -> living.setSecondsOnFire(5);
                        case ICE -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                        case LIGHT -> living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                        default -> { }
                    }
                }
            }
            discard();
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof DarknutCreature darknut
                && darknut.reflectBodyProjectile(this, entityHit.getLocation())) return;
        if (isBombArrow()) {
            if (!level().isClientSide) explode();
            return;
        }
        super.onHit(hit);
    }

    private ElementalDamage.Element elementalKind() {
        return switch (kind()) {
            case FIRE -> ElementalDamage.Element.FIRE;
            case ICE -> ElementalDamage.Element.ICE;
            case LIGHT -> ElementalDamage.Element.LIGHT;
            default -> ElementalDamage.Element.NONE;
        };
    }

    private boolean isBombArrow() {
        return kind() == ZeldaCombatItems.ArrowKind.BOMB || kind() == ZeldaCombatItems.ArrowKind.FIRE_BOMB
                || kind() == ZeldaCombatItems.ArrowKind.WATER_BOMB;
    }

    private void explode() {
        if (!(level() instanceof ServerLevel server)) {
            discard();
            return;
        }
        boolean fire = kind() == ZeldaCombatItems.ArrowKind.FIRE_BOMB;
        server.explode(this, getX(), getY(), getZ(), fire ? 3.5F : 3.0F, fire, Level.ExplosionInteraction.TNT);
        if (kind() == ZeldaCombatItems.ArrowKind.WATER_BOMB) {
            net.minecraft.core.BlockPos center = blockPosition();
            net.minecraft.core.BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 2, 3))
                    .forEach(pos -> {
                        if (server.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FIRE)) server.removeBlock(pos, false);
                    });
        }
        discard();
    }

    @Override protected ItemStack getPickupItem() {
        return new ItemStack(ZSSRegistries.getItem(switch (kind()) {
            case BOMB -> "bomb_arrow"; case FIRE_BOMB -> "fire_bomb_arrow"; case WATER_BOMB -> "water_bomb_arrow";
            case FIRE -> "fire_arrow"; case ICE -> "ice_arrow"; case LIGHT -> "light_arrow";
        }));
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("kind", entityData.get(KIND));
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ZeldaCombatItems.ArrowKind kind;
        try { kind = ZeldaCombatItems.ArrowKind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { kind = ZeldaCombatItems.ArrowKind.BOMB; }
        entityData.set(KIND, kind.name().toLowerCase(Locale.ROOT));
        getPersistentData().putString("zss_arrow", kind.name().toLowerCase(Locale.ROOT));
    }
}
