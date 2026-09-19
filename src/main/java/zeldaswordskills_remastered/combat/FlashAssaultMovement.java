package zeldaswordskills_remastered.combat;

import net.minecraft.world.phys.Vec3;

/** Geometry and initial momentum for a fixed rear approach. */
public final class FlashAssaultMovement {
    private FlashAssaultMovement() {}

    public static Vec3 rearPosition(Vec3 targetPosition, float targetYaw) {
        return targetPosition.subtract(Vec3.directionFromRotation(0.0F, targetYaw).scale(2.0D));
    }

    public static Vec3 impulse(Vec3 start, Vec3 destination, double drag) {
        // Sum vanilla drag over the bounded approach; direction and distance are sampled once.
        double scale = Math.abs(1.0D - drag) < 1.0E-9D ? 1.0D / FlashAssault.FOLLOW_UP_TICKS
                : (1.0D - drag) / (1.0D - Math.pow(drag, FlashAssault.FOLLOW_UP_TICKS));
        return destination.subtract(start).multiply(scale, 0.0D, scale);
    }
}
