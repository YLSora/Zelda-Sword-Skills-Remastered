package zeldaswordskills_remastered.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.entity.projectile.ThrownBomb;
import zeldaswordskills_remastered.entity.projectile.ThrownRock;

/** Amphibious Octorok that alternates between movement and its registered ranged payload. */
public class OctorokCreature extends LegacyCreature implements RangedAttackMob {
    public OctorokCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) { super(type, level, kind); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RangedAttackGoal(this, .8D, 40, 60, 16.0F));
        goalSelector.addGoal(5, new RandomStrollGoal(this, .7D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void performRangedAttack(LivingEntity target, float distanceFactor) {
        double dx = target.getX() - getX();
        double dy = target.getEyeY() - getEyeY();
        double dz = target.getZ() - getZ();
        if (kind() == Kind.OCTOROK_BOMB) {
            ThrownBomb bomb = new ThrownBomb(level(), this, ThrownBomb.BombKind.WATER);
            bomb.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * .15D, dz, .8F, 4.0F);
            level().addFreshEntity(bomb);
        } else {
            ThrownRock rock = new ThrownRock(level(), this).configureDamage(2.0F);
            rock.shoot(dx, dy, dz, 1.1F, 6.0F);
            level().addFreshEntity(rock);
        }
    }

    @Override public boolean canBreatheUnderwater() { return true; }
}
