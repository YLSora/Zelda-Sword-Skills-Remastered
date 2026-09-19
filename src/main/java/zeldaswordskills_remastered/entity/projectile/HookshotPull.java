package zeldaswordskills_remastered.entity.projectile;

import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.entity.DekuCreature;
import zeldaswordskills_remastered.worldgen.DungeonBoss;

/** One bounded, collision-aware pull. Owns and restores only its temporary gravity change. */
final class HookshotPull {
    private static final double SPEED = 0.8D;
    private static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("zeldaswordskills_remastered", "hookshot_immune"));
    private final Entity target;
    private final boolean hadNoGravity;
    private Vec3 previousPosition;
    private int stalledTicks;

    HookshotPull(Entity target) {
        this.target = target;
        hadNoGravity = target.isNoGravity();
    }

    static boolean canGrab(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger() || entity.isVehicle()) return false;
        if (entity instanceof ItemEntity item) return canCollect(item);
        if (!(entity instanceof Mob mob) || mob.isNoAi()) return false;
        if (entity instanceof DungeonBoss boss && boss.isBoss() || entity.getType().is(IMMUNE)) return false;
        return !(entity instanceof DekuCreature)
                && mob.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) > 0.0D;
    }

    static boolean canCollect(ItemEntity item) {
        return item.isAlive() && !item.getItem().isEmpty();
    }

    static Vec3 destination(Player owner, Entity target, float launchYaw) {
        if (target instanceof ItemEntity) return owner.position();
        Vec3 forward = Vec3.directionFromRotation(0.0F, launchYaw);
        return owner.position().add(forward.scale(3.0D));
    }

    static Vec3 velocity(Vec3 from, Vec3 destination) {
        Vec3 delta = destination.subtract(from);
        double distance = delta.length();
        return distance < 1.0E-6D ? Vec3.ZERO : delta.scale(Math.min(SPEED, distance) / distance);
    }

    Entity target() { return target; }

    boolean tick(Vec3 destination, double arrivalRadius) {
        if (!target.isAlive() || target.isPassenger() || target.isVehicle()) return false;
        Vec3 delta = destination.subtract(target.position());
        if (delta.lengthSqr() <= arrivalRadius * arrivalRadius) return false;
        if (previousPosition != null && previousPosition.distanceToSqr(target.position()) < 0.0004D) stalledTicks++;
        else stalledTicks = 0;
        if (stalledTicks >= 6) return false;
        previousPosition = target.position();
        Vec3 motion = velocity(target.position(), destination);
        var swept = target.getBoundingBox().expandTowards(motion);
        if (!target.level().hasChunkAt(net.minecraft.core.BlockPos.containing(destination))
                || !target.level().getWorldBorder().isWithinBounds(swept)) return false;
        target.fallDistance = 0.0F;
        if (target instanceof Mob mob) mob.getNavigation().stop();
        if (target instanceof ServerPlayer) {
            // A floor may block the downward component without blocking horizontal travel.
            motion = Entity.collideBoundingBox(target, motion, target.getBoundingBox(), target.level(),
                    target.level().getEntityCollisions(target, swept));
            if (motion.lengthSqr() < 1.0E-8D) return false;
            target.setNoGravity(true);
            target.setDeltaMovement(motion);
        } else {
            if (!target.level().noCollision(target, swept)) return false;
            // Mob AI and item drag must not turn a constant-speed pull into a one-off knockback.
            target.move(MoverType.SELF, motion);
            target.setDeltaMovement(Vec3.ZERO);
            if (target.position().distanceToSqr(previousPosition) < motion.lengthSqr() * 0.25D) return false;
        }
        target.hurtMarked = true;
        return true;
    }

    void finish() {
        if (target instanceof ServerPlayer) target.setNoGravity(hadNoGravity);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
        target.hurtMarked = true;
    }
}
