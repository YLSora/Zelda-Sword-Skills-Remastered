package zeldaswordskills_remastered.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.entity.ZSSDamageSources;

/** Rooted Deku Baba state machine: warning, strike, prone recovery and Fire Baba casting. */
public final class DekuCreature extends LegacyCreature {
    private int attackTicks;
    private int proneTicks;
    private int cooldown;
    private boolean gland = true;

    public DekuCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) { super(type, level, kind); }

    public boolean isProne() { return proneTicks > 0; }
    public boolean hasFireGland() { return gland; }

    @Override protected void registerGoals() {
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void aiStep() {
        super.aiStep();
        setDeltaMovement(new Vec3(0, getDeltaMovement().y, 0));
        getNavigation().stop();
        if (level().isClientSide) return;
        if (cooldown > 0) cooldown--;
        if (proneTicks > 0) { proneTicks--; attackTicks = 0; return; }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = distanceToSqr(target);
        double reach = kind() == Kind.BABA_WITHERED ? 9.0D : 16.0D;
        if (distance <= reach && cooldown == 0 && attackTicks == 0) attackTicks = kind() == Kind.BABA_WITHERED ? 5 : 11;
        if (kind() == Kind.BABA_FIRE && gland && distance > 16.0D && distance <= 1600.0D && cooldown == 0) {
            launchFire(target); cooldown = 40; proneTicks = 20;
        }
        if (attackTicks > 0 && --attackTicks == 4) {
            if (distanceToSqr(target) <= reach) {
                if (target instanceof Player player && player.isBlocking()) proneTicks = 60;
                else doHurtTarget(target);
            }
            cooldown = 24; proneTicks = Math.max(proneTicks, 60);
        }
    }

    @Override public boolean doHurtTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        boolean hit = living.hurt(damageSources().mobAttack(this),
                (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (hit && kind() == Kind.BABA_FIRE) living.setSecondsOnFire(3);
        return hit;
    }

    private void launchFire(LivingEntity target) {
        ToolProjectile spell = new ToolProjectile(ZSSRegistries.MAGIC_SPELL.get(), level(), this, ToolProjectile.Mode.FIRE).configureDamage(4.0F);
        double y = target.getEyeY() - spell.getY();
        spell.shoot(target.getX() - getX(), y, target.getZ() - getZ(), 0.9F, 2.0F);
        level().addFreshEntity(spell);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (kind() == Kind.BABA_FIRE && ElementalDamage.from(source) == ElementalDamage.Element.FIRE) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && kind() == Kind.BABA_FIRE && isProne() && gland && source.getEntity() instanceof Player) gland = false;
        return hurt;
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("prone_ticks", proneTicks); tag.putInt("cooldown", cooldown); tag.putBoolean("gland", gland);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); proneTicks = Math.max(0, tag.getInt("prone_ticks")); cooldown = Math.max(0, tag.getInt("cooldown")); gland = tag.getBoolean("gland");
    }
}
