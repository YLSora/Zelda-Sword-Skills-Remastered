package zeldaswordskills_remastered.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Focus is transient, server-owned, and bound to the current lock. */
public final class FatalStrike {
    private static final float[] MULTIPLIERS = {2.0F, 2.2F, 2.4F, 2.7F, 3.0F};
    /** The hit cue plays a touch higher than the vanilla wither death sound. */
    private static final float FATAL_STRIKE_HIT_PITCH = 1.4F;
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID, "fatal_strike"));

    private FatalStrike() { }

    public static float multiplier(int level) { return MULTIPLIERS[Mth.clamp(level, 1, 5) - 1]; }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        PlayerCombatState state = data.combat();
        long now = player.level().getGameTime();
        LivingEntity target = TargetingService.getWeaponTarget(player, data).orElse(null);
        int level = data.activeSkillLevel(ZSSContentIds.ENDING_BLOW);
        if (!player.isAlive() || player.isSpectator() || target == null || level <= 0) {
            if (state.clearFocus()) ZSSNetwork.syncCombatState(player, state);
            state.resetFocusRoll();
            return;
        }
        if (state.focusUntil() > 0 && !state.focusActive(now)) {
            state.clearFocus();
            ZSSNetwork.syncCombatState(player, state);
        }
        if (state.focusActive(now) || state.fatalStrikeCoolingDown(now)) return;
        if (state.pollFocusRoll(now) && target.getHealth() < target.getMaxHealth() * 0.1F
                && player.getRandom().nextInt(100) < 2 * Mth.clamp(level, 1, 5)) {
            state.startFocus(now);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ZSSRegistries.FATAL_STRIKE_FOCUS.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            ZSSNetwork.syncCombatState(player, state);
        }
    }

    /** Matches Player.attack's vanilla critical conditions before the attack ticker is reset. */
    public static boolean isJumpCritical(Player player) {
        return player.getAttackStrengthScale(0.5F) > 0.9F && player.fallDistance > 0.0F
                && !player.onGround() && !player.onClimbable() && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger() && !player.isSprinting();
    }

    /** True means this jump attack was consumed, even when its damage was refused. */
    public static boolean tryStrike(ServerPlayer player, ZSSPlayerData data, LivingEntity target) {
        long now = player.level().getGameTime();
        if (data.activeSkillLevel(ZSSContentIds.ENDING_BLOW) <= 0
                || !data.combat().focusActive(now) || !isJumpCritical(player)) return false;
        if (target == null) {
            miss(player, data);
            return true;
        }
        // All vanilla jump-critical damage is suppressed during focus; other skill damage is untouched.
        if (TargetingService.getWeaponTarget(player, data).filter(locked -> locked == target).isEmpty()) {
            miss(player, data);
            return true;
        }
        if (!player.canReach(target, 0.0D)) {
            miss(player, data);
            return true;
        }
        int level = data.activeSkillLevel(ZSSContentIds.ENDING_BLOW);
        if (level <= 0) return true;
        float health = target.getHealth();
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier(level);
        DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE), player);
        if (!BasicSwordSkill.hurtLockedTarget(player, data, target, source, damage)) {
            miss(player, data);
            return true;
        }
        data.combat().finishFocus(now, 300);
        // Record the killing blow as well: normal target validation rejects a newly dead target.
        int basicLevel = TargetingService.basicSkillLevel(data);
        if (basicLevel > 0) {
            data.combat().recordHit(target.getId(), damage, BasicSwordSkill.comboMaximum(basicLevel),
                    now + BasicSwordSkill.comboDuration(basicLevel));
            zeldaswordskills_remastered.progression.ZSSAdvancementService.comboHit(player, data.combat().comboCount());
        }
        player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 6));
        player.getFoodData().setSaturation(Math.max(0.0F, player.getFoodData().getSaturationLevel() - 6.0F));
        player.crit(target);
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                ZSSRegistries.FATAL_STRIKE_HIT.get(), SoundSource.PLAYERS, 1.0F, FATAL_STRIKE_HIT_PITCH);
        SwordSkillParticles.blockBreak(player, target, Blocks.REDSTONE_BLOCK.defaultBlockState(), 40);
        if (!target.isAlive()) player.giveExperiencePoints(level + 1 + player.getRandom().nextInt(Math.max(2, Mth.ceil(health))));
        ZSSNetwork.syncCombatState(player, data.combat());
        return true;
    }

    public static void miss(ServerPlayer player, ZSSPlayerData data) {
        long now = player.level().getGameTime();
        if (!data.combat().focusActive(now)) return;
        data.combat().finishFocus(now, 60);
        ZSSNetwork.syncCombatState(player, data.combat());
    }

    public static Attempt watchAttack(ServerPlayer player, ZSSPlayerData data) {
        return new Attempt(player, data);
    }

    /** One released attack, including all of a sweep or a projectile's eventual contacts. */
    public static final class Attempt implements AutoCloseable {
        private final ServerPlayer player;
        private final ZSSPlayerData data;
        private final long focusVersion;
        private boolean hit;
        private boolean closed;

        private Attempt(ServerPlayer player, ZSSPlayerData data) {
            this.player = player;
            this.data = data;
            focusVersion = data.combat().focusActive(player.level().getGameTime()) ? data.combat().focusVersion() : -1;
        }

        public boolean hit(boolean success) { hit |= success; return success; }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (!hit && focusVersion == data.combat().focusVersion()) miss(player, data);
        }
    }
}
