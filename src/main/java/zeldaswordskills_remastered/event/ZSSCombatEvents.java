package zeldaswordskills_remastered.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.BasicSwordSkill;
import zeldaswordskills_remastered.combat.TargetingService;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.FatalStrike;
import zeldaswordskills_remastered.combat.GroundSlam;
import zeldaswordskills_remastered.combat.IaiSlash;
import zeldaswordskills_remastered.combat.MasterMode;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.registry.ZSSContentIds;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSCombatEvents {
    private ZSSCombatEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void jumpAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ZSSCapabilities.get(player).ifPresent(data -> {
            if (data.combat().flashAssault().busy()) return;
            if (IaiSlash.tryStrike(player, data, event.getTarget() instanceof LivingEntity living ? living : null)) {
                event.setCanceled(true);
            } else if (zeldaswordskills_remastered.combat.HelmSplitter.tryStrike(player, data)) {
                event.setCanceled(true);
            } else if (GroundSlam.tryStrike(player, data, event.getTarget() instanceof LivingEntity living ? living : null)) {
                event.setCanceled(true);
            } else if (FatalStrike.tryStrike(player, data, event.getTarget() instanceof LivingEntity living ? living : null)) {
                player.resetAttackStrengthTicker();
                event.setCanceled(true);
            }
        });
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            ZSSCapabilities.get(player).ifPresent(data ->
                    zeldaswordskills_remastered.combat.FlashAssault.enforceSlot(player, data.combat().flashAssault()));
        }
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            MasterMode.updatePlayerHealth(player);
            ZSSCapabilities.get(player).ifPresent(data -> {
                TargetingService.tick(player, data);
                AdvancedSwordSkills.tick(player, data);
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void masterModeDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        event.setAmount(MasterMode.enemyDamage(event.getAmount(), event.getSource().getEntity()));
    }

    @SubscribeEvent
    public static void masterModeEntityJoined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof ItemEntity item && ZSSConfig.SERVER.masterMode.get()
                && item.getItem().is(ZSSRegistries.getItem("small_heart"))) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof LivingEntity living) MasterMode.updateEnemyHealth(living);
    }

    @SubscribeEvent
    public static void masterModeEnemyTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (!living.level().isClientSide && living.tickCount % 20 == 0) MasterMode.updateEnemyHealth(living);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerDamaged(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ZSSCapabilities.get(player).ifPresent(data -> {
                if (event.getAmount() > 0.0F) {
                    AdvancedSwordSkills.endParryGuard(player, data);
                    // Taking a hit while the Iai Slash stance is armed spends it and starts its
                    // failure cooldown, so the wound cannot be shrugged off and immediately re-armed.
                    IaiSlash.cancel(player, data);
                }
                BasicSwordSkill.onPlayerDamaged(player, data, event.getSource(), event.getAmount());
            });
        }
    }

    @SubscribeEvent
    public static void playerFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ZSSCapabilities.get(player).ifPresent(data -> {
            data.combat().helmSplitter().land(player.level().getGameTime());
            if (GroundSlam.land(player, data)) {
                data.combat().clearRisingCutFallGrace();
                data.combat().helmSplitter().clearFallGrace();
                event.setDistance(0.0F);
                return;
            }
            // A landed Rising Cut waives the fall of the climb it started. It governs that fall on
            // its own, so the Double Jump allowance does not stack on top of it and quietly widen
            // the boundary the skill states; every other fall uses the Double Jump allowance.
            double waiver = Math.max(data.combat().risingCutFallGrace(), data.combat().helmSplitter().fallGrace());
            if (waiver > 0.0D) {
                // Spend it on this fall: the skill covers exactly one climb, never a later one.
                data.combat().clearRisingCutFallGrace();
                data.combat().helmSplitter().clearFallGrace();
            } else {
                int level = data.activeSkillLevel(ZSSContentIds.DOUBLE_JUMP);
                if (level <= 0) return;
                // Double Jump raises the three-block safe fall height by its own jump height, so
                // the skill can never cause the fall damage of the extra distance it grants.
                waiver = AdvancedSwordSkills.doubleJumpFallHeight(level);
            }
            // Anything past the waiver stays as fall distance, so vanilla still applies its own
            // free height and its own damage formula to the remainder.
            event.setDistance((float) Math.max(0.0D, event.getDistance() - waiver));
        });
    }

    /**
     * A parried attacker is held in place while its twenty-tick stun runs. The Stun effect alone is
     * not enough: it lowers the movement attribute, which a mob's own AI, a knockback or a
     * projectile can still push past. Pinning the entity back to where it was parried (horizontal
     * only, so gravity still applies) makes the immobilise absolute.
     *
     * <p>This runs at the end of the level tick, after every entity has already moved, because the
     * per-entity tick event fires before the entity's own AI and would simply be overwritten.
     */
    @SubscribeEvent
    public static void holdParriedAttackers(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        AdvancedSwordSkills.holdParriedAttackers();
        GroundSlam.tickImmobilized();
    }

    /**
     * A parried attacker cannot attack while its stun runs: every blow it produces is refused. The
     * Stun effect's attack-speed modifier cannot do this on its own, because the vanilla goals that
     * call {@code doHurtTarget} directly and the ZSS creature AI both bypass it.
     *
     * <p>Only blows this enemy produces are refused, so the counter-attack the parry opens still
     * lands: that blow's source is the player, not the enemy held here.
     */
    @SubscribeEvent
    public static void attackerStunned(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && (AdvancedSwordSkills.parryLocked(attacker) || GroundSlam.isImmobilized(attacker))) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void projectileParried(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (event.getImpactResult() != net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.DEFAULT
                || !(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer player)) return;
        ZSSCapabilities.get(player).ifPresent(data -> {
            if (AdvancedSwordSkills.parryProjectile(player, data, event.getProjectile())) {
                event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerAttacked(LivingAttackEvent event) {
        if (event.getAmount() <= 0.0F) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            ZSSCapabilities.get(player).ifPresent(data -> {
                if (AdvancedSwordSkills.onAttacked(player, data, event.getSource())) event.setCanceled(true);
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectSkillUserFromNegativeEffects(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getEffectInstance().getEffect().isBeneficial()) return;
        ZSSCapabilities.get(player).ifPresent(data -> {
            long now = player.level().getGameTime();
            if (data.combat().dodgeActive(now) || data.combat().dashImmune(now)
                    || data.combat().flashAssault().dashing(now)
                    || data.combat().risingCutImmune(now)
                    || data.combat().helmSplitter().airborne()) event.setCanceled(true);
        });
    }
}
