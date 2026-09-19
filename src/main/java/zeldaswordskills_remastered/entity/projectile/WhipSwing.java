package zeldaswordskills_remastered.entity.projectile;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Server-owned swing arc, clipped by vanilla collision rather than client-reported movement. */
final class WhipSwing {
    private Vec3 heading;
    private double height;
    private int phase;

    void tick(Player player, Vec3 anchor, double range) {
        if (player.onGround() || player.getBoundingBox().maxY >= anchor.y) return;
        Vec3 pivot = anchor.subtract(0, player.getEyeHeight(), 0);
        if (heading == null) {
            if (player.getDeltaMovement().y >= 0) return;
            Vec3 delta = pivot.subtract(player.position());
            heading = delta.normalize();
            height = Math.min(range, delta.length()) / 7.0D;
            phase = (int) ((range - Math.min(range, delta.horizontalDistance())) / range * 8);
        }
        double angle = Math.toRadians(10.0D * phase++);
        Vec3 motion = new Vec3(Math.sin(angle) * heading.x * 0.8D,
                -Math.sin(angle * 2) * height, Math.sin(angle) * heading.z * 0.8D);
        var swept = player.getBoundingBox().expandTowards(motion);
        if (!player.level().getWorldBorder().isWithinBounds(swept)) return;
        Vec3 clipped = Entity.collideBoundingBox(player, motion, player.getBoundingBox(), player.level(),
                player.level().getEntityCollisions(player, swept));
        if (clipped.distanceToSqr(motion) > 1.0E-8D) heading = null;
        player.setDeltaMovement(clipped);
        player.fallDistance = 0;
        player.hurtMarked = true;
    }
}
