package zeldaswordskills_remastered.combat;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.network.ZSSNetwork;

/** A successful descending melee hit primes one landing; its fall immunity outlives the impact window. */
public final class GroundSlam {
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ZSSContentIds.LEAPING_BLOW);

    private GroundSlam() { }

    public static boolean canStrike(ServerPlayer player, ZSSPlayerData data) {
        // Server-player velocity can retain the launch impulse; fallDistance follows movement packets.
        return data.activeSkillLevel(ZSSContentIds.LEAPING_BLOW) > 0 && data.combat().groundSlamReady()
                && player.isAlive() && !player.isSpectator() && !player.onGround() && player.fallDistance > 0.0F
                && !fallInterrupted(player) && TargetingService.isHoldingSword(player);
    }

    /** True consumes the attack, including refusals; no ordinary hit accompanies this skill. */
    public static boolean tryStrike(ServerPlayer player, ZSSPlayerData data, LivingEntity target) {
        if (!canStrike(player, data)) return false;
        if (player.getAttackStrengthScale(0.5F) < 0.9F) return true;
        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, true);
        try (var attempt = FatalStrike.watchAttack(player, data)) {
            if (target == null || !target.isAlive() || !target.isAttackable() || target.isSpectator()
                    || player.distanceToSqr(target) > 16.0D || !player.hasLineOfSight(target)) return true;
            int level = data.activeSkillLevel(ZSSContentIds.LEAPING_BLOW);
            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 2.0F * level;
            if (attempt.hit(target.hurt(source(player), damage))) {
                data.combat().groundSlamHit(target.getId(), player.level().getGameTime());
                BasicSwordSkill.recordComboHit(player, data, target, damage);
                feedback(player, radius(level));
            }
        }
        return true;
    }

    public static double radius(int level) { return 0.5D + 0.5D * level; }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        if (data.activeSkillLevel(ZSSContentIds.LEAPING_BLOW) <= 0 || !player.isAlive() || fallInterrupted(player)) {
            clear(player, data);
        } else if (player.onGround() && player.fallDistance <= 0.0F && player.getDeltaMovement().y <= 0.0D) {
            // Also handles a short landing or a block that never emits LivingFallEvent.
            land(player, data);
        }
    }

    /** Consumes the landing exactly once, before any fall damage is applied. */
    public static boolean land(ServerPlayer player, ZSSPlayerData data) {
        boolean immune = data.combat().groundSlamFallImmune();
        boolean impact = data.combat().groundSlamLandingActive(player.level().getGameTime());
        var directHits = data.combat().groundSlamHitTargets();
        clear(player, data);
        int level = data.activeSkillLevel(ZSSContentIds.LEAPING_BLOW);
        if (!impact || level <= 0 || !player.isAlive() || fallInterrupted(player)
                || !TargetingService.isHoldingSword(player)) return immune;
        double radius = radius(level);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 0.75F * level;
        int weakness = (AdvancedSwordSkills.isMasterSwordAtFullHealth(player) ? 110 : 50) + 10 * level;
        boolean hit = false;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), target -> target != player && target.isAlive()
                        && !directHits.contains(target.getId())
                        && !target.isSpectator() && !player.isAlliedTo(target)
                        && player.distanceToSqr(target) <= radius * radius)) {
            if (target.hurt(source(player), damage)) {
                hit = true;
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weakness));
                BasicSwordSkill.recordComboHit(player, data, target, damage);
            }
        }
        if (hit) feedback(player, radius);
        return immune;
    }

    private static boolean fallInterrupted(ServerPlayer player) {
        return player.isInWater() || player.isInLava() || player.onClimbable() || player.isFallFlying()
                || player.getAbilities().flying || player.isPassenger();
    }

    private static void clear(ServerPlayer player, ZSSPlayerData data) {
        boolean wasReady = data.combat().groundSlamReady();
        data.combat().clearGroundSlam();
        if (wasReady) ZSSNetwork.syncCombatState(player, data.combat());
    }

    private static DamageSource source(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE), player);
    }

    private static void feedback(ServerPlayer player, double radius) {
        var dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI * 2.0D / 32.0D;
            player.serverLevel().sendParticles(dirt,
                    player.getX() + Math.cos(angle) * radius, player.getY() + 0.15D,
                    player.getZ() + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        player.level().playSound(null, player.blockPosition(), ZSSRegistries.LEAPING_BLOW_SOUND.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
