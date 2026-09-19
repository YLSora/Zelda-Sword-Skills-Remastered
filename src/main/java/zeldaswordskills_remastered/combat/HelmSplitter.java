package zeldaswordskills_remastered.combat;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.UUID;

/** One airborne follow-up to a confirmed parry, independent of Double Jump's allowance. */
public final class HelmSplitter {
    // Vanilla v = (v - 0.08) * 0.98, inverted for heights 1.2 / 1.5 / 1.8 / 2.1 / 2.4.
    private static final double[] JUMP_VELOCITY = {0.0D, 0.409761136D, 0.463413072D, 0.514520301D, 0.560018115D, 0.604347864D};

    private HelmSplitter() {}

    public static final class State {
        private int targetId = -1;
        private long readyUntil;
        private double parryY;
        private boolean jumped;
        private UUID protectedTarget;
        private boolean airborne;
        private long immuneUntil;
        private long hitImmuneUntil;
        private double fallGrace;

        public void arm(int target, long until, double y) {
            targetId = target;
            readyUntil = until;
            parryY = y;
            jumped = false;
        }

        public boolean ready(long now) { return now < readyUntil; }
        public long readyUntil() { return readyUntil; }
        public int targetId() { return targetId; }
        public void observeJump(double y, boolean onGround) {
            if (!onGround && y > parryY + 0.05D) jumped = true;
        }
        public boolean jumped() { return jumped; }
        public void clearOpening() { targetId = -1; readyUntil = 0L; jumped = false; }
        public void launch(UUID target, double grace) {
            protectedTarget = target;
            airborne = true;
            immuneUntil = 0L;
            fallGrace = grace;
        }
        public void land(long now) {
            if (!airborne) return;
            airborne = false;
            immuneUntil = now + 20L;
        }
        public boolean immune(UUID attacker, long now) {
            return attacker.equals(protectedTarget) && (airborne || now < immuneUntil);
        }
        public void hit(long now) { hitImmuneUntil = now + 30L; }
        public boolean hitImmune(long now) { return now < hitImmuneUntil; }
        public double fallGrace() { return fallGrace; }
        public void clearFallGrace() { fallGrace = 0.0D; }
        public void interruptFlight() {
            protectedTarget = null;
            airborne = false;
            immuneUntil = 0L;
            clearFallGrace();
        }
        public void reset() { clearOpening(); interruptFlight(); hitImmuneUntil = 0L; }
    }

    public static long window(int level) { return AdvancedSwordSkills.swordBreakWindow(level); }
    public static double jumpHeight(int level) { return (0.75D + 0.25D * Mth.clamp(level, 1, 5)) * 1.2D; }
    public static double jumpLift(int level) { return JUMP_VELOCITY[Mth.clamp(level, 1, 5)]; }
    public static double fallGrace(int level) { return jumpHeight(level) + 5.0D; }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        State state = data.combat().helmSplitter();
        long now = player.level().getGameTime();
        if (!state.ready(now) || state.targetId() != data.combat().targetId()) state.clearOpening();
        else state.observeJump(player.getY(), player.onGround());
        if (!player.isAlive() || player.isPassenger() || player.isInWater() || player.isInLava()
                || player.onClimbable() || player.isFallFlying() || player.getAbilities().flying) {
            state.reset();
        } else if (player.onGround()) {
            state.land(now);
            // Ground flags can precede the actual fall event.
            if (player.fallDistance <= 0.0F && player.getDeltaMovement().y <= 0.0D) state.clearFallGrace();
        }
    }

    public static boolean tryStrike(ServerPlayer player, ZSSPlayerData data) {
        State state = data.combat().helmSplitter();
        long now = player.level().getGameTime();
        int level = data.activeSkillLevel(ZSSContentIds.HELM_SPLITTER);
        state.observeJump(player.getY(), player.onGround());
        if (level <= 0 || !state.ready(now) || !state.jumped() || player.onGround()
                || !TargetingService.isHoldingSword(player)) return false;
        int targetId = state.targetId();
        state.clearOpening();
        data.combat().useSwordBreak();
        data.combat().clearCharge();
        ZSSNetwork.sendParryState(player, data.combat());
        try (var attempt = FatalStrike.watchAttack(player, data)) {
            var target = TargetingService.getValidTarget(player, data).orElse(null);
            if (target == null || target.getId() != targetId || !player.isAlive() || player.isSpectator()
                    || player.isBlocking() || player.isPassenger() || player.isInWater() || player.isInLava()
                    || player.onClimbable() || player.isFallFlying() || player.getAbilities().flying) return true;
            // Sample the rear point once. Vanilla travel resolves the jump and all collisions.
            Vec3 destination = FlashAssaultMovement.rearPosition(target.position(), target.getYRot());
            player.setDeltaMovement(jumpVelocity(player.position(), destination, jumpLift(level)));
            player.resetFallDistance();
            player.hasImpulse = player.hurtMarked = true;
            state.launch(target.getUUID(), fallGrace(level));
            player.resetAttackStrengthTicker();
            player.swing(InteractionHand.MAIN_HAND, true);
            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 1.5F * level;
            DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, ZSSContentIds.HELM_SPLITTER)), player);
            if (attempt.hit(target.hurt(source, damage))) {
                state.hit(now);
                player.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState()),
                        target.getX(), target.getBoundingBox().maxY, target.getZ(),
                        20, target.getBbWidth() * 0.25D, 0.1D, target.getBbWidth() * 0.25D, 0.15D);
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        ZSSRegistries.HELM_SPLITTER_HIT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                BasicSwordSkill.recordComboHit(player, data, target, damage);
            }
        }
        return true;
    }

    public static Vec3 jumpVelocity(Vec3 start, Vec3 destination, double lift) {
        double y = start.y;
        double velocity = lift;
        double travel = 0.0D;
        double drag = 1.0D;
        for (int tick = 0; tick < 80; tick++) {
            y += velocity;
            travel += drag;
            if (velocity < 0.0D && y <= destination.y) break;
            velocity = (velocity - 0.08D) * 0.98D;
            drag *= 0.91D;
        }
        Vec3 offset = destination.subtract(start);
        return new Vec3(offset.x / travel, lift, offset.z / travel);
    }
}
