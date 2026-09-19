package zeldaswordskills_remastered.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.item.EquipmentItem;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.entity.ZSSDamageSources;

import java.util.EnumSet;

/** Free-flying Keese with contact attacks and explicit elemental variants. */
public final class KeeseCreature extends LegacyCreature {
    public KeeseCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level, kind);
        moveControl = new FlyingMoveControl(this, 12, true);
        getAttribute(Attributes.FLYING_SPEED).setBaseValue(Math.max(kind.speed() * 2.0D, 0.5D));
        setNoGravity(true);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FlyAttackGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                player -> !wearsSkullMask(player)));
    }

    @Override public boolean isNoGravity() { return true; }

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

    private static final class FlyAttackGoal extends Goal {
        private final KeeseCreature keese;
        private int cooldown;
        FlyAttackGoal(KeeseCreature keese) { this.keese = keese; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return keese.getTarget() != null && keese.getTarget().isAlive(); }
        @Override public void tick() {
            LivingEntity target = keese.getTarget();
            if (target == null) return;
            keese.getLookControl().setLookAt(target, 30.0F, 30.0F);
            keese.getMoveControl().setWantedPosition(target.getX(), target.getEyeY(), target.getZ(), 1.25D);
            if (cooldown > 0) cooldown--;
            if (keese.getBoundingBox().inflate(.4D).intersects(target.getBoundingBox()) && cooldown == 0) {
                keese.doHurtTarget(target); cooldown = 20;
            }
        }
    }
}
