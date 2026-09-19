package zeldaswordskills_remastered.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Watches one opponent without camera lock, then replaces a manually landed draw attack. */
public final class IaiSlash {
    public static final int OBSERVE_TICKS = 40;
    public static final int ATTACK_TICKS = 40;
    public static final int STANCE_TICKS = 100;
    public static final int CANCEL_COOLDOWN_TICKS = 200;
    private static final double MAXIMUM_DISTANCE = 5.0D;
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ZSSContentIds.MORTAL_DRAW);

    private IaiSlash() {}

    public static final class State {
        private final Map<Integer, Long> visibleSince = new HashMap<>();
        private int targetId = -1;
        private int emptySlot = -1;
        private long attackUntil;
        private long stanceUntil;
        private long cooldownUntil;
        private ItemStack attributeWeapon;

        public int targetId() { return targetId; }
        public boolean active() { return targetId >= 0; }
        public long attackUntil() { return attackUntil; }
        public boolean coolingDown(long now) { return now < cooldownUntil; }
        public void observe(Set<Integer> visible, long now) {
            visibleSince.keySet().retainAll(visible);
            visible.forEach(id -> visibleSince.putIfAbsent(id, now));
        }
        public boolean observed(int id, long now) {
            Long since = visibleSince.get(id);
            return since != null && now - since >= OBSERVE_TICKS;
        }
        public void arm(int id, int slot, long now) {
            targetId = id;
            emptySlot = slot;
            attackUntil = 0L;
            stanceUntil = now + STANCE_TICKS;
            visibleSince.clear();
        }
        public void draw(int slot, boolean weapon, long now) {
            if (active() && attackUntil == 0L && slot != emptySlot && weapon) attackUntil = now + ATTACK_TICKS;
        }
        public boolean expired(long now) {
            return active() && (now >= stanceUntil || attackUntil > 0L && now >= attackUntil);
        }
        public boolean ready(long now) { return active() && attackUntil > 0L && !expired(now); }
        public void clear() {
            targetId = emptySlot = -1;
            attackUntil = 0L;
            stanceUntil = 0L;
            visibleSince.clear();
        }
        public void cancel(long now) {
            if (active()) cooldownUntil = now + CANCEL_COOLDOWN_TICKS;
            clear();
        }
        public void reset() { clear(); cooldownUntil = 0L; attributeWeapon = null; }
    }

    public static boolean isAttackWeapon(Player player) {
        return !player.getMainHandItem().isEmpty() && (TargetingService.isHoldingSword(player)
                || player.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE).stream().anyMatch(modifier -> modifier.getAmount() > 0.0D));
    }

    private static boolean validEnemy(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && target.isAttackable() && !target.isSpectator()
                && TargetingService.hasAutonomousBehaviour(target) && !TargetingService.isFriendly(target)
                && !target.isInvisible() && !(target instanceof ArmorStand) && !player.isAlliedTo(target)
                && (!(target instanceof Player other) || ZSSConfig.SERVER.canTargetPlayers.get() && player.canHarmPlayer(other));
    }

    public static boolean visibleCandidate(ServerPlayer player, LivingEntity target) {
        return validEnemy(player, target) && player.distanceToSqr(target) <= MAXIMUM_DISTANCE * MAXIMUM_DISTANCE
                && player.hasLineOfSight(target)
                && player.getLookAngle().dot(target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize()) >= 0.5D;
    }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        State state = data.combat().iaiSlash();
        // Equipment attributes have been updated by vanilla at PlayerTick END. Remember the
        // applied stack so a carried-slot packet followed immediately by attack uses the new weapon.
        state.attributeWeapon = state.active() ? player.getMainHandItem().copy() : null;
        if (!eligiblePlayer(player, data)) {
            cancel(player, data);
            return;
        }
        if (state.active()) {
            updateDraw(player, data);
            return;
        }
        long now = player.level().getGameTime();
        var candidates = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MAXIMUM_DISTANCE),
                target -> visibleCandidate(player, target));
        state.observe(candidates.stream().map(LivingEntity::getId).collect(Collectors.toSet()), now);
        if (!player.getMainHandItem().isEmpty()) return;
        candidates.stream().filter(target -> state.observed(target.getId(), now))
                .min(Comparator.comparingDouble(player::distanceToSqr)).ifPresent(target -> {
                    state.arm(target.getId(), player.getInventory().selected, now);
                    data.combat().clearCharge();
                    ZSSNetwork.syncCombatState(player, data.combat());
                });
    }

    private static boolean eligiblePlayer(ServerPlayer player, ZSSPlayerData data) {
        State state = data.combat().iaiSlash();
        return data.activeSkillLevel(ZSSContentIds.MORTAL_DRAW) > 0 && player.isAlive() && !player.isSpectator()
                && data.combat().targetId() < 0 && !data.combat().flashAssault().busy()
                && !state.coolingDown(player.level().getGameTime());
    }

    private static void updateDraw(ServerPlayer player, ZSSPlayerData data) {
        State state = data.combat().iaiSlash();
        long now = player.level().getGameTime();
        if (!eligiblePlayer(player, data) || !(player.level().getEntity(state.targetId()) instanceof LivingEntity target)
                || !validEnemy(player, target) || player.distanceToSqr(target) > MAXIMUM_DISTANCE * MAXIMUM_DISTANCE
                || state.expired(now)) {
            cancel(player, data);
            return;
        }
        state.draw(player.getInventory().selected, isAttackWeapon(player), now);
    }

    /** Only the actual attack target can trigger the skill; no custom auto-hit request exists. */
    public static boolean tryStrike(ServerPlayer player, ZSSPlayerData data, LivingEntity target) {
        State state = data.combat().iaiSlash();
        if (!state.active()) return false;
        long now = player.level().getGameTime();
        updateDraw(player, data);
        if (!state.active()) {
            // updateDraw dropped the stance (timeout, lost lock, dead mark); nothing left to spend.
            return false;
        }
        if (target == null) {
            cancel(player, data);
            return false;
        }
        // A stray hit spends the stance and starts the same cooldown as every other cancellation.
        if (target.getId() != state.targetId()) {
            cancel(player, data);
            return false;
        }
        if (!state.ready(now) || !isAttackWeapon(player) || !validEnemy(player, target)
                || !player.canReach(target, 0.0D) || !player.hasLineOfSight(target)) {
            cancel(player, data);
            return false;
        }
        float damage = weaponDamage(player, state) + 2.5F * data.activeSkillLevel(ZSSContentIds.MORTAL_DRAW);
        DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE), player);
        player.resetAttackStrengthTicker();
        if (target.hurt(source, damage)) {
            // A successful draw consumes the marker and starts the same cancellation cooldown as
            // every other way the preparation can end.
            state.cancel(now);
            data.combat().finishCombo();
            if (data.activeSkillLevel(ZSSContentIds.SWORD_BASIC) > 0) data.combat().setTarget(target.getId());
            BasicSwordSkill.recordComboHit(player, data, target, damage);
            ZSSNetwork.syncCombatState(player, data.combat());
            player.level().playSound(null, target.blockPosition(), ZSSRegistries.MORTAL_DRAW_SOUND.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            SwordSkillParticles.blockBreak(player, target, Blocks.BLUE_ICE.defaultBlockState(), 40);
        }
        // A rejected skill hit may be retried until timeout, but never adds a vanilla hit.
        return true;
    }

    private static float weaponDamage(ServerPlayer player, State state) {
        AttributeInstance attack = new AttributeInstance(Attributes.ATTACK_DAMAGE, ignored -> {});
        attack.replaceFrom(player.getAttribute(Attributes.ATTACK_DAMAGE));
        if (state.attributeWeapon != null) state.attributeWeapon.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE).forEach(modifier -> attack.removeModifier(modifier.getId()));
        for (var modifier : player.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            attack.removeModifier(modifier.getId());
            attack.addTransientModifier(modifier);
        }
        return (float) attack.getValue();
    }

    public static void cancel(ServerPlayer player, ZSSPlayerData data) {
        State state = data.combat().iaiSlash();
        boolean active = state.active();
        state.cancel(player.level().getGameTime());
        if (active) ZSSNetwork.syncCombatState(player, data.combat());
    }
}
