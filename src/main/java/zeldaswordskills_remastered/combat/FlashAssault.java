package zeldaswordskills_remastered.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.network.FlashAssaultStateMessage;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Optional;

/** A confirmed dodge opens one approach and one weapon-dependent attack sequence. */
public final class FlashAssault {
    public static final int READY_TICKS = 60;
    public static final int FOLLOW_UP_TICKS = 20;
    public static final int IMMUNITY_TICKS = 20;
    /** Time after a successful Dodge in which Flash Assault may confirm a perfect dodge. */
    public static final int PERFECT_DODGE_WINDOW_TICKS = 12;
    public static final int COOLDOWN_TICKS = 60;
    public static final int DOUBLE_TAP_TICKS = 6;
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ZSSContentIds.FLASH_ASSAULT);
    private static final TagKey<DamageType> MELEE_ATTACKS = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "melee_attacks"));
    private static final float[] PITCHES = {1.2F, 1.3F, 1.45F, 1.6F, 1.8F, 2.0F};

    private FlashAssault() {}

    /** All fields are transient and belong to the player's existing combat capability. */
    public static final class State {
        private int dodgeTarget = -1;
        private boolean dodgeConfirmed;
        private long dodgeStartedAt = Long.MIN_VALUE;
        private long dodgeUntil;
        private int target = -1;
        private boolean targetInvalidated;
        private long readyUntil;
        private long firstTap = Long.MIN_VALUE;
        private long followUpUntil;
        private long dashUntil;
        private long attackUntil;
        private long immuneUntil;
        private long cooldownUntil;
        private boolean dashing;
        private boolean attacking;
        private boolean attackQueued;
        private boolean attackHit;
        private Vec3 dashStart;
        private Vec3 dashDestination;
        private int slot = -1;
        private ItemStack weapon;
        private int level;
        private int hits;
        private int interval;
        private int nextHit;
        private long hitAt;
        private float damage;
        private boolean armorChecked;
        private FatalStrike.Attempt attempt;

        public void startDodge(long now, int targetId) {
            dodgeTarget = targetId;
            dodgeConfirmed = false;
            dodgeStartedAt = now;
            dodgeUntil = now + PERFECT_DODGE_WINDOW_TICKS;
        }
        public boolean dodgeConfirmable(long now) {
            return dodgeTarget >= 0 && !dodgeConfirmed && dodgeStartedAt != Long.MIN_VALUE
                    && now >= dodgeStartedAt && now < dodgeUntil;
        }
        public boolean confirmDodge(int targetId, long now) {
            if (targetId < 0 || dodgeTarget != targetId || dodgeConfirmed || busy() || coolingDown(now)
                    || !dodgeConfirmable(now)) return false;
            dodgeConfirmed = true;
            target = targetId;
            targetInvalidated = false;
            readyUntil = now + READY_TICKS;
            firstTap = Long.MIN_VALUE;
            return true;
        }
        public boolean ready(long now) { return target >= 0 && !targetInvalidated && now < readyUntil; }
        public boolean followUp(long now) { return target >= 0 && !targetInvalidated && now < followUpUntil && !attacking; }
        public void targetChanged() {
            dodgeTarget = -1;
            targetInvalidated = true;
            dodgeStartedAt = Long.MIN_VALUE;
            dodgeUntil = 0L;
        }
        public boolean busy() { return dashing || attacking; }
        public boolean dashing(long now) { return dashing && now < dashUntil; }
        public boolean immune(long now) { return now < immuneUntil; }
        public boolean coolingDown(long now) { return now < cooldownUntil; }
        public boolean acceptForwardTap(long now) {
            if (!ready(now) || busy() || coolingDown(now)) return false;
            if (firstTap != Long.MIN_VALUE && now - firstTap <= DOUBLE_TAP_TICKS) {
                firstTap = Long.MIN_VALUE;
                return true;
            }
            firstTap = now;
            return false;
        }
        private void stopAction() {
            if (attempt != null) attempt.close();
            attempt = null;
            target = -1;
            targetInvalidated = false;
            readyUntil = followUpUntil = dashUntil = attackUntil = 0L;
            firstTap = Long.MIN_VALUE;
            dashing = attacking = attackQueued = false;
            dashStart = dashDestination = null;
            slot = -1;
            weapon = null;
        }
        private void finishAttack(long now) {
            immuneUntil = now + IMMUNITY_TICKS + (attackHit ? 30L : 0L);
            stopAction();
            attackHit = false;
        }
        public void reset() {
            // Lifecycle resets must not report an unfinished attack as a new miss.
            attempt = null;
            stopAction();
            immuneUntil = 0L;
            cooldownUntil = 0L;
            dodgeTarget = -1;
            dodgeConfirmed = false;
            dodgeStartedAt = Long.MIN_VALUE;
            dodgeUntil = 0L;
        }
    }

    public static boolean confirmDodge(ServerPlayer player, ZSSPlayerData data, DamageSource source) {
        State state = data.combat().flashAssault();
        long now = player.level().getGameTime();
        if (data.activeSkillLevel(ZSSContentIds.FLASH_ASSAULT) <= 0 || data.activeSkillLevel(ZSSContentIds.DODGE) <= 0
                || player.getAbilities().invulnerable || !player.isAlive()
                || !(source.getEntity() instanceof LivingEntity attacker)
                || !isMeleeAttack(source)
                || TargetingService.getLockedTarget(player, data).filter(target -> target == attacker).isEmpty()
                || !state.confirmDodge(attacker.getId(), now)) return false;
        player.level().playSound(null, player.blockPosition(), ZSSRegistries.FLASH_ASSAULT_DODGE.get(), SoundSource.PLAYERS, 1.0F, 0.5F);
        sync(player, state);
        return true;
    }

    public static boolean isMeleeAttack(DamageSource source) {
        return source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
                && !source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                && !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                && !source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                && source.is(MELEE_ATTACKS);
    }

    public static boolean isDamage(DamageSource source) {
        return source.is(DAMAGE);
    }

    public static void handle(ServerPlayer player, ZSSPlayerData data, SkillIntentMessage.Action action) {
        State state = data.combat().flashAssault();
        long now = player.level().getGameTime();
        if (data.activeSkillLevel(ZSSContentIds.FLASH_ASSAULT) <= 0 || !player.isAlive() || player.isSpectator() || player.isPassenger()) {
            stop(player, state);
            return;
        }
        LivingEntity target = target(player, data, state);
        if (target == null) { stop(player, state); return; }
        if (action == SkillIntentMessage.Action.BEGIN) {
            if (!state.acceptForwardTap(now) || data.combat().spinActive(now) || data.combat().dashActive(now)) return;
            state.readyUntil = 0L;
            state.cooldownUntil = now + COOLDOWN_TICKS;
            state.followUpUntil = now + FOLLOW_UP_TICKS;
            state.dashUntil = now + FOLLOW_UP_TICKS;
            state.dashing = true;
            state.attackQueued = false;
            state.attackHit = false;
            state.slot = player.getInventory().selected;
            state.weapon = player.getMainHandItem().copy();
            state.immuneUntil = state.dashUntil + IMMUNITY_TICKS;
            data.combat().clearCharge();
            AdvancedSwordSkills.endParryGuard(player, data);
            state.dashStart = player.position();
            state.dashDestination = rearPosition(target, player);
            var ground = player.getOnPos();
            double drag = player.isInWater() ? 0.8D : player.isInLava() ? 0.5D : player.onGround()
                    ? player.level().getBlockState(ground).getFriction(player.level(), ground, player) * 0.91F : 0.91F;
            Vec3 horizontalImpulse = FlashAssaultMovement.impulse(state.dashStart, state.dashDestination, drag);
            Vec3 impulse = new Vec3(horizontalImpulse.x, player.getDeltaMovement().y, horizontalImpulse.z);
            player.setDeltaMovement(impulse);
            // The approved impulse uses doubles; vanilla's self-motion packet clamps each axis to 3.9.
            player.hurtMarked = false;
            player.level().playSound(null, player.blockPosition(), ZSSRegistries.FLASH_ASSAULT_DASH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            sync(player, state, Optional.of(impulse));
        } else if (action == SkillIntentMessage.Action.ATTACK && state.dashing) {
            state.attackQueued = true;
            sync(player, state);
        } else if (action == SkillIntentMessage.Action.ATTACK && state.followUp(now)) {
            // A press is sufficient, including one aimed at air while still approaching.
            enforceSlot(player, state);
            state.followUpUntil = 0L;
            state.attacking = true;
            state.attackQueued = false;
            state.slot = player.getInventory().selected;
            state.weapon = player.getMainHandItem().copy();
            state.level = data.activeSkillLevel(ZSSContentIds.FLASH_ASSAULT);
            // Hit count follows the held weapon's own attack speed. The weapon is read directly
            // rather than through the player attribute so a same-tick slot change cannot report the
            // previous weapon's speed, the same reason IaiSlash rebuilds its attack-damage value.
            state.hits = hitCount(weaponAttackSpeed(state.weapon));
            state.interval = hitInterval(state.hits);
            state.nextHit = 0;
            state.armorChecked = false;
            state.damage = damage(player.getAttributeValue(Attributes.ATTACK_DAMAGE), state.weapon, state.level);
            state.hitAt = now;
            state.attackUntil = now + (state.hits - 1L) * state.interval + 1L;
            state.immuneUntil = state.attackUntil - 1L + IMMUNITY_TICKS;
            state.attempt = FatalStrike.watchAttack(player, data);
            data.combat().clearCharge();
            AdvancedSwordSkills.endParryGuard(player, data);
            strike(player, data, state, target, now);
            sync(player, state);
        } else if (action == SkillIntentMessage.Action.ATTACK) {
            // A late click must receive an ACK too, otherwise the client keeps swallowing attacks.
            sync(player, state);
        }
    }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        State state = data.combat().flashAssault();
        if (state.target < 0) return;
        if (data.activeSkillLevel(ZSSContentIds.FLASH_ASSAULT) <= 0) {
            disable(player, state);
            return;
        }
        long now = player.level().getGameTime();
        // A normal finishing blow between burst hits still completes a successful assault.
        if (state.attacking && state.attackHit
                && player.level().getEntity(state.target) instanceof LivingEntity defeated && defeated.isDeadOrDying()
                && defeated.getLastDamageSource() != null && defeated.getLastDamageSource().getEntity() == player) {
            state.finishAttack(now);
            sync(player, state);
            return;
        }
        if (state.attacking && now >= state.attackUntil) {
            stop(player, state);
            return;
        }
        LivingEntity target = target(player, data, state);
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || target == null
                || (!state.busy() && !state.ready(now) && !state.followUp(now))) {
            stop(player, state);
            return;
        }
        enforceSlot(player, state);
        if (state.busy() && !ItemStack.matches(state.weapon, player.getMainHandItem())) {
            stop(player, state);
            return;
        }
        if (state.dashing) {
            Vec3 remaining = state.dashDestination.subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
            Vec3 path = state.dashDestination.subtract(state.dashStart).multiply(1.0D, 0.0D, 1.0D);
            // Recompute the target's rear point every tick so moving targets are approached using
            // the current collision-box separation. noPhysics lets the approach pass through mobs
            // and blocks without being truncated by vanilla collision resolution.
            state.dashDestination = rearPosition(target, player);
            player.noPhysics = true;
            remaining = state.dashDestination.subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
            long ticksLeft = Math.max(1L, state.dashUntil - now);
            player.noPhysics = true;
            Vec3 impulse = remaining.scale(1.0D / ticksLeft);
            player.setDeltaMovement(impulse.x, player.getDeltaMovement().y, impulse.z);
            if (remaining.horizontalDistanceSqr() <= 0.04D || now + 1L >= state.dashUntil) {
                player.setPos(state.dashDestination.x, state.dashDestination.y, state.dashDestination.z);
                state.dashing = false;
                stopMotion(player);
                player.noPhysics = false;
                state.immuneUntil = now + IMMUNITY_TICKS;
                state.followUpUntil = now + IMMUNITY_TICKS;
                if (state.attackQueued) beginAttack(player, data, state, target, now);
                sync(player, state);
            }
        }
        if (state.attacking && now >= state.hitAt) strike(player, data, state, target, now);
    }

    private static void strike(ServerPlayer player, ZSSPlayerData data, State state, LivingEntity target, long now) {
        // The locked target owns the sequence; aiming and vanilla attack recovery are irrelevant.
        Vec3 motion = target.getDeltaMovement();
        DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE), player);
        boolean hit = state.attempt.hit(target.hurt(source, state.damage));
        state.attackHit |= hit;
        target.setDeltaMovement(motion);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, target.blockPosition(), ZSSRegistries.FLASH_ASSAULT_ATTACK.get(),
                SoundSource.PLAYERS, 1.0F, PITCHES[state.nextHit]);
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, target.getX(), target.getY(0.5D), target.getZ(),
                12, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.08D);
        if (hit) {
            BasicSwordSkill.recordComboHit(player, data, target, state.damage);
            if (!state.armorChecked) {
                state.armorChecked = true;
                tryRemoveArmor(player, target, state);
            }
        }
        state.nextHit++;
        state.hitAt = now + state.interval;
        if (state.nextHit >= state.hits || !target.isAlive()) {
            if (hit && target.isAlive()) {
                AdvancedSwordSkills.releaseParryHold(target);
                var pos = target.getOnPos();
                float friction = target.level().getBlockState(pos).getFriction(target.level(), pos, target);
                Vec3 away = target.position().subtract(player.position());
                if (away.horizontalDistanceSqr() < 1.0E-6D) away = player.getLookAngle();
                target.setDeltaMovement(0.0D, motion.y, 0.0D);
                target.knockback(AdvancedSwordSkills.swordBreakImpulseSpeed(friction) * knockbackDistance(state.weapon, state.level) / 4.0D, -away.x, -away.z);                target.hurtMarked = true;
            }
            // Completion (including a killing hit) keeps protection for twenty ticks.
            // Stop any remaining approach without replacing the attack's final deadline.
            if (state.dashing) stopMotion(player);
            state.finishAttack(now);
            sync(player, state);
        }
    }

    private static void beginAttack(ServerPlayer player, ZSSPlayerData data, State state, LivingEntity target, long now) {
        enforceSlot(player, state);
        state.followUpUntil = 0L;
        state.attacking = true;
        state.attackQueued = false;
        state.slot = player.getInventory().selected;
        state.weapon = player.getMainHandItem().copy();
        state.level = data.activeSkillLevel(ZSSContentIds.FLASH_ASSAULT);
        state.hits = hitCount(weaponAttackSpeed(state.weapon));
        state.interval = hitInterval(state.hits);
        state.nextHit = 0;
        state.armorChecked = false;
        state.damage = damage(player.getAttributeValue(Attributes.ATTACK_DAMAGE), state.weapon, state.level);
        state.hitAt = now;
        state.attackUntil = now + (state.hits - 1L) * state.interval + 1L;
        state.immuneUntil = state.attackUntil - 1L + IMMUNITY_TICKS;
        state.attempt = FatalStrike.watchAttack(player, data);
        data.combat().clearCharge();
        AdvancedSwordSkills.endParryGuard(player, data);
        strike(player, data, state, target, now);
    }

    private static void tryRemoveArmor(ServerPlayer player, LivingEntity target, State state) {
        if (target instanceof Slime || target instanceof Blaze || target instanceof SnowGolem) return;
        ItemStack chest = target.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty() || target instanceof Player && !ZSSConfig.SERVER.flashAssaultCanRemovePlayerArmor.get()) return;
        if (player.getRandom().nextFloat() < AdvancedSwordSkills.flashAssaultArmorChance(chest, state.weapon, state.level)) {
            target.spawnAtLocation(chest.copy());
            target.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
    }

    private static LivingEntity target(ServerPlayer player, ZSSPlayerData data, State state) {
        if (state.targetInvalidated) return null;
        LivingEntity locked = TargetingService.getLockedTarget(player, data).orElse(null);
        if (locked != null && locked.getId() == state.target) return locked;
        return player.level().getEntity(state.target) instanceof LivingEntity entity && entity.isAlive() ? entity : null;
    }

    private static Vec3 rearPosition(LivingEntity target, ServerPlayer player) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, target.getYRot());
        double gap = target.getBbWidth() * 0.5D + player.getBbWidth() * 0.5D + 0.1D;
        return target.getBoundingBox().getCenter().subtract(forward.scale(gap));
    }

    public static void enforceSlot(ServerPlayer player, State state) {
        if (!state.busy() || state.slot < 0 || player.getInventory().selected == state.slot) return;
        player.getInventory().selected = state.slot;
        player.connection.send(new ClientboundSetCarriedItemPacket(state.slot));
    }

    private static void stop(ServerPlayer player, State state) {
        // An interrupted burst still clears protection; normal completion uses finishAttack.
        if (state.attacking) state.immuneUntil = 0L;
        else if (state.dashing) state.immuneUntil =
                Math.min(player.level().getGameTime(), state.dashUntil) + IMMUNITY_TICKS;
        if (state.dashing) stopMotion(player);
        player.noPhysics = false;
        state.stopAction();
        sync(player, state);
    }

    public static void disable(ServerPlayer player, State state) {
        if (state.dashing) stopMotion(player);
        player.noPhysics = false;
        state.stopAction();
        state.immuneUntil = 0L;
        state.targetChanged();
        sync(player, state);
    }

    private static void sync(ServerPlayer player, State state) {
        sync(player, state, Optional.empty());
    }

    private static void sync(ServerPlayer player, State state, Optional<Vec3> impulse) {
        ZSSNetwork.syncFlashAssault(player, new FlashAssaultStateMessage(state.target, state.readyUntil,
                state.followUpUntil, state.busy() ? state.slot : -1, state.dashing, state.attacking,
                state.dashUntil, state.cooldownUntil, impulse));
    }

    private static void stopMotion(ServerPlayer player) {
        player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
        player.hurtMarked = true;
    }

    /**
     * Flash Assault's number of hits follows the held weapon's attack speed rather than its class;
     * see {@link FlashAssaultBurst} for the thresholds themselves.
     */
    public static int hitCount(double attackSpeed) {
        return FlashAssaultBurst.hitCount(attackSpeed);
    }
    /** Ticks between hits for the selected speed tier. */
    public static int hitInterval(int hits) { return FlashAssaultBurst.hitInterval(hits); }

    /** Per-hit damage uses the same attack-speed tier as the hit count. */
    public static float damage(double attackDamage, ItemStack weapon, int level) {
        return FlashAssaultBurst.damage(attackDamage, weaponAttackSpeed(weapon), level);
    }

    /** Distance is calibrated through the existing vanilla knockback impulse helper. */
    public static double knockbackDistance(ItemStack weapon, int level) {
        if (isSword(weapon)) return 3.0D + 0.5D * level;
        if (isAxe(weapon)) return 3.0D + 1.5D * level;
        return 3.0D + level;
    }

    private static boolean isSword(ItemStack weapon) {
        return weapon.getItem() instanceof SwordItem || weapon.is(ItemTags.SWORDS) || weapon.is(TargetingService.SWORDS);
    }

    private static boolean isAxe(ItemStack weapon) {
        return weapon.getItem() instanceof AxeItem || weapon.is(ItemTags.AXES);
    }

    /**
     * The held weapon's own attack speed, read from the stack so a slot change on the same tick
     * cannot report the previous weapon. Vanilla's {@code ATTACK_SPEED} base is 4.0, and a weapon
     * contributes its MAINHAND modifier; anything with no such modifier (a stick, a bare hand) keeps
     * the player's base 4.0, which is the fast end of the scale.
     */
    static double weaponAttackSpeed(ItemStack weapon) {
        double speed = Attributes.ATTACK_SPEED.getDefaultValue();
        for (AttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED)) {
            speed = modifier.getOperation() == AttributeModifier.Operation.ADDITION
                    ? speed + modifier.getAmount()
                    : speed * (1.0D + modifier.getAmount());
        }
        // Vanilla weapon modifiers originate as floats (4 - 3.1F is slightly above 0.9).
        return Math.round(speed * 1_000_000.0D) / 1_000_000.0D;
    }
}
