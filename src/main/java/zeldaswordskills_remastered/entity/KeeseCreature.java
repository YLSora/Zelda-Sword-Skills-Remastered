package zeldaswordskills_remastered.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import zeldaswordskills_remastered.item.EquipmentItem;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Free-flying Keese with contact attacks and explicit elemental variants. */
public final class KeeseCreature extends LegacyCreature {
    private static final double STEERING_INTERPOLATION = 0.125D;
    private BlockPos flightTarget;
    private int attackCooldown;

    public KeeseCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level, kind);
        setNoGravity(true);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                player -> !wearsSkullMask(player)));
    }

    @Override public boolean isNoGravity() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) { }
    @Override protected void pushEntities() { }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void checkFallDamage(double distance, boolean onGround, BlockState state, BlockPos pos) { }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(getDeltaMovement().multiply(1.0D, 0.6D, 1.0D));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            flightTarget = BlockPos.containing(target.getX(), target.getEyeY(), target.getZ());
        } else {
            if (flightTarget != null && (!level().isEmptyBlock(flightTarget)
                    || flightTarget.getY() <= level().getMinBuildHeight())) flightTarget = null;
            if (flightTarget == null || random.nextInt(30) == 0
                    || flightTarget.closerToCenterThan(position(), 2.0D)) {
                flightTarget = BlockPos.containing(getX() + random.nextInt(7) - random.nextInt(7),
                        getY() + random.nextInt(6) - 2.0D,
                        getZ() + random.nextInt(7) - random.nextInt(7));
            }
        }

        double x = flightTarget.getX() + 0.5D - getX();
        double y = flightTarget.getY() + 0.1D - getY();
        double z = flightTarget.getZ() + 0.5D - getZ();
        Vec3 velocity = getDeltaMovement();
        Vec3 next = velocity.add((Math.signum(x) * 0.5D - velocity.x) * STEERING_INTERPOLATION,
                (Math.signum(y) * 0.7D - velocity.y) * STEERING_INTERPOLATION,
                (Math.signum(z) * 0.5D - velocity.z) * STEERING_INTERPOLATION);
        setDeltaMovement(next);
        float desiredYaw = (float) (Mth.atan2(next.z, next.x) * Mth.RAD_TO_DEG) - 90.0F;
        zza = 0.5F;
        setYRot(getYRot() + Mth.wrapDegrees(desiredYaw - getYRot()));

        if (attackCooldown > 0) attackCooldown--;
        if (target != null && getBoundingBox().inflate(0.4D).intersects(target.getBoundingBox())
                && attackCooldown == 0) {
            doHurtTarget(target);
            attackCooldown = 20;
        }
    }

    @Override public boolean doHurtTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        boolean hit = living.hurt(damageSources().mobAttack(this),
                (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (!hit) return false;
        switch (kind()) {
            case KEESE_FIRE -> living.setSecondsOnFire(5);
            case KEESE_ICE -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            case KEESE_THUNDER -> living.addEffect(new MobEffectInstance(ZSSRegistries.STUN.get(), 40, 0));
            case KEESE_CURSED -> living.addEffect(new MobEffectInstance(switch (random.nextInt(3)) {
                case 0 -> MobEffects.WEAKNESS; case 1 -> MobEffects.BLINDNESS; default -> MobEffects.MOVEMENT_SLOWDOWN;
            }, 120, 0));
            default -> { }
        }
        return true;
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        ElementalDamage.Element element = ElementalDamage.from(source);
        if (kind() == Kind.KEESE_FIRE && element == ElementalDamage.Element.FIRE) return false;
        if (kind() == Kind.KEESE_FIRE && element == ElementalDamage.Element.ICE) amount *= 2;
        if (kind() == Kind.KEESE_ICE && element == ElementalDamage.Element.ICE) return false;
        if (kind() == Kind.KEESE_ICE && element == ElementalDamage.Element.FIRE) amount *= 2;
        if (kind() == Kind.KEESE_CURSED && element == ElementalDamage.Element.LIGHT) amount *= 2;
        return super.hurt(source, amount);
    }

    private static boolean wearsSkullMask(LivingEntity entity) {
        return entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof EquipmentItem item
                && item.gear() == EquipmentItem.Gear.SKULL_MASK;
    }

}
