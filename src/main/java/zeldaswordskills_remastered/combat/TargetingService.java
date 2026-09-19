package zeldaswordskills_remastered.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.entity.FairyCreature;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.network.TargetIntentMessage;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;

import java.util.Comparator;
import java.util.List;

public final class TargetingService {
    private static final double MINIMUM_VIEW_DOT = 0.25D;
    public static final TagKey<net.minecraft.world.item.Item> SWORDS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID, "swords"));

    private TargetingService() {
    }

    public static void handle(ServerPlayer player, ZSSPlayerData data, TargetIntentMessage.Action action) {
        if (action == TargetIntentMessage.Action.CLEAR) {
            clear(player, data);
            return;
        }
        int level = data.activeSkillLevel(ZSSContentIds.SWORD_BASIC);
        if (level <= 0 || !isHoldingSword(player) && data.activeSkillLevel(ZSSContentIds.FLASH_ASSAULT) <= 0
                && !data.combat().iaiSlash().active()) {
            clear(player, data);
            return;
        }
        List<LivingEntity> candidates = candidates(player, level);
        if (candidates.isEmpty()) {
            clear(player, data);
            return;
        }
        LivingEntity selected;
        if (action == TargetIntentMessage.Action.NEXT && data.combat().targetId() >= 0) {
            int currentIndex = -1;
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).getId() == data.combat().targetId()) {
                    currentIndex = i;
                    break;
                }
            }
            selected = candidates.get((currentIndex + 1) % candidates.size());
        } else {
            selected = candidates.get(0);
        }
        if (data.combat().targetId() != selected.getId()) {
            data.combat().finishCombo();
            data.combat().endFocusOnTargetChange(player.level().getGameTime());
        }
        data.combat().iaiSlash().cancel(player.level().getGameTime());
        data.combat().setTarget(selected.getId());
        ZSSNetwork.syncCombatState(player, data.combat());
    }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        if (data.combat().comboExpired(player.level().getGameTime())) {
            data.combat().finishCombo();
            ZSSNetwork.syncCombatState(player, data.combat());
        }
        if (data.combat().targetId() >= 0 && getLockedTarget(player, data).isEmpty()) {
            Entity previous = player.level().getEntity(data.combat().targetId());
            int level = basicSkillLevel(data);
            // Defer transfer until the tick, after the killing skill has recorded its combo hit.
            if (player.isAlive() && level > 0 && previous instanceof LivingEntity defeated && defeated.isDeadOrDying()) {
                // Candidates are filtered by the same validation as acquisition, so the next lock
                // is another hostile inside the existing range and view cone. Friends, non-AI
                // entities and invisible targets are all excluded there.
                LivingEntity next = candidates(player, level).stream()
                        .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                if (next != null) {
                    data.combat().transferTarget(next.getId(), player.level().getGameTime());
                    ZSSNetwork.syncCombatState(player, data.combat());
                    return;
                }
            }
            clear(player, data);
        }
    }

    public static java.util.Optional<LivingEntity> getValidTarget(ServerPlayer player, ZSSPlayerData data) {
        int level = basicSkillLevel(data);
        if (level <= 0 || !isHoldingSword(player)) return java.util.Optional.empty();
        return getLockedTarget(player, data);
    }

    public static java.util.Optional<LivingEntity> getLockedTarget(ServerPlayer player, ZSSPlayerData data) {
        int level = basicSkillLevel(data);
        if (level <= 0) return java.util.Optional.empty();
        Entity entity = player.level().getEntity(data.combat().targetId());
        if (!(entity instanceof LivingEntity target) || !isValid(player, target, level, false)) return java.util.Optional.empty();
        return java.util.Optional.of(target);
    }

    public static int basicSkillLevel(ZSSPlayerData data) {
        return data.activeSkillLevel(ZSSContentIds.SWORD_BASIC);
    }

    public static boolean isHoldingSword(Player player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof SwordItem || stack.is(ItemTags.SWORDS) || stack.is(SWORDS);
    }

    /**
     * Whether an entity has behaviour of its own, so a sword skill may lock onto it. Only
     * self-directed creatures qualify: every mob (vanilla, modded and this mod's PathfinderMob
     * variants) and other players. Items, item frames, armor stands and the transient entities an
     * enemy attack creates (harming areas, projectiles, explosions) have no autonomous AI and are
     * neither lockable nor part of the combo chain.
     */
    public static boolean hasAutonomousBehaviour(Entity entity) {
        return entity instanceof Mob || entity instanceof Player;
    }

    /**
     * Whether an entity is friendly, and therefore never a sword skill target. Passive animals,
     * villagers and other NPCs, ambient creatures, water creatures and this mod's fairies are all
     * friendly; players are handled separately through {@code canTargetPlayers}.
     *
     * <p>Hostility is read in this order, because category alone is not enough:
     * <ol>
     *   <li>the vanilla {@link Enemy} marker, which several hostile mobs implement even though they
     *       are not registered as monsters (hoglins, piglins and their kin);</li>
     *   <li>{@link NeutralMob}, covering creatures that are peaceful until provoked but are proper
     *       combatants once they are (iron golems, wolves, llamas, polar bears, bees);</li>
     *   <li>the monster category, for every remaining hostile mob.</li>
     * </ol>
     * Everything else - farm animals, villagers, bats, squid and fairies - is friendly.
     */
    public static boolean isFriendly(LivingEntity entity) {
        if (entity instanceof NaviCreature || entity instanceof FairyCreature) return true;
        if (entity instanceof Player) return false;
        if (entity instanceof Enemy || entity instanceof NeutralMob) return false;
        if (entity instanceof Mob mob) return mob.getType().getCategory() != MobCategory.MONSTER;
        return true;
    }

    private static List<LivingEntity> candidates(ServerPlayer player, int level) {
        double range = 6.0D + level;
        AABB bounds = player.getBoundingBox().inflate(range);
        return player.level().getEntitiesOfClass(LivingEntity.class, bounds,
                        target -> isValid(player, target, level, true))
                .stream().sorted(Comparator.comparingDouble(target -> targetScore(player, target))).toList();
    }

    private static boolean isValid(ServerPlayer player, LivingEntity target, int level, boolean requireViewCone) {
        if (target == player || !target.isAlive() || target.isInvisible() || target.isSpectator()) return false;
        if (!hasAutonomousBehaviour(target)) return false;
        // Friends are not enemies: a sword skill never locks a passive animal, an NPC or a fairy.
        if (isFriendly(target)) return false;
        if (target instanceof Player && !ZSSConfig.SERVER.canTargetPlayers.get()) return false;
        double range = 6.0D + level;
        if (player.distanceToSqr(target) > range * range || !player.hasLineOfSight(target)) return false;
        if (!requireViewCone) return true;
        Vec3 direction = target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        return player.getLookAngle().dot(direction) >= MINIMUM_VIEW_DOT;
    }

    private static double targetScore(ServerPlayer player, LivingEntity target) {
        Vec3 direction = target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        double angle = 1.0D - Mth.clamp(player.getLookAngle().dot(direction), -1.0D, 1.0D);
        return angle * 1000.0D + player.distanceToSqr(target);
    }

    private static void clear(ServerPlayer player, ZSSPlayerData data) {
        data.combat().endFocusOnTargetChange(player.level().getGameTime());
        data.combat().clearTarget();
        data.combat().finishCombo();
        ZSSNetwork.syncCombatState(player, data.combat());
    }
}
