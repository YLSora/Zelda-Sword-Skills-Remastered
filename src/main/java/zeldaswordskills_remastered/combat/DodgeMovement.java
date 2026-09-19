package zeldaswordskills_remastered.combat;

import net.minecraft.world.phys.Vec3;

/** Preserves horizontal travel when turning an impulse around the target. */
public final class DodgeMovement {
    public static final double DISTANCE = 4.0D;

    private DodgeMovement() {}

    public static Vec3 orbitVelocity(Vec3 position, Vec3 center, double distance, boolean right) {
        Vec3 radial = position.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        double radius = radial.length();
        if (radius < 1.0E-6D) return Vec3.ZERO;
        // Minecraft moves along a chord each tick. Using distance / radius as an angle
        // shortens that actual move, most noticeably when the target is close.
        double angle = (right ? -1.0D : 1.0D) * 2.0D * Math.asin(Math.min(1.0D, distance / (2.0D * radius)));
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return new Vec3(radial.x * (cos - 1.0D) - radial.z * sin, 0.0D,
                radial.x * sin + radial.z * (cos - 1.0D));
    }
}
