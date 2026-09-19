package zeldaswordskills_remastered.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Wall-climbing Skulltula whose armored front exposes a vulnerable back. */
public class SkulltulaCreature extends LegacyCreature {
    public SkulltulaCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) { super(type, level, kind); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void aiStep() {
        super.aiStep();
        if (horizontalCollision) setDeltaMovement(getDeltaMovement().x, .2D, getDeltaMovement().z);
    }

    @Override public boolean onClimbable() { return horizontalCollision; }

    @Override public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && inFront(attacker) && ElementalDamage.from(source) == ElementalDamage.Element.NONE)
            amount *= .2F;
        else if (attacker != null) amount *= 1.75F;
        return super.hurt(source, amount);
    }

    private boolean inFront(Entity attacker) {
        Vec3 towardAttacker = attacker.position().subtract(position()).multiply(1, 0, 1).normalize();
        Vec3 facing = getLookAngle().multiply(1, 0, 1).normalize();
        return towardAttacker.dot(facing) > 0;
    }
}
