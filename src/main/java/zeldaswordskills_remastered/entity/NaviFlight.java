package zeldaswordskills_remastered.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Continuous companion trajectories and steering, independent of entity lifecycle and collisions. */
public final class NaviFlight {
    private NaviFlight() {}

    public static Vec3 hover(double time, Vec3 phase) {
        return new Vec3(1.3D + 0.35D * Math.sin(time * 0.033D + phase.x)
                        + 0.12D * Math.sin(time * 0.057D + phase.z),
                0.15D + 0.22D * Math.sin(time * 0.027D + phase.y)
                        + 0.08D * Math.cos(time * 0.049D + phase.x),
                0.5D + 0.45D * Math.cos(time * 0.029D + phase.z)
                        + 0.18D * Math.sin(time * 0.043D + phase.y));
    }

    public static Vec3 facingNormal(Vec3 eye, Vec3 center) {
        Vec3 direction = eye.subtract(center);
        return direction.lengthSqr() < 1.0E-8D ? new Vec3(0, 0, 1) : direction.normalize();
    }

    public static Vec3 planeRight(Vec3 normal, Vec3 previous) {
        // Project the previous axis so crossing the vertical view has no pole flip.
        Vec3 projected = previous == null ? Vec3.ZERO : previous.subtract(normal.scale(previous.dot(normal)));
        if (projected.lengthSqr() < 1.0E-8D) {
            Vec3 reference = Math.abs(normal.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            projected = reference.cross(normal);
        }
        return projected.normalize();
    }

    public static Vec3 orbit(double angle, Vec3 phase, double width, double height, Vec3 normal, Vec3 right) {
        Vec3 up = normal.cross(right).normalize();
        double horizontal = Math.max(1.5D, width * 0.8D + 0.65D);
        double vertical = Math.max(0.9D, height * 0.55D + 0.3D);
        double radius = 1.0D + 0.055D * Math.sin(angle * 0.63D + phase.x);
        return right.scale(horizontal * radius * Math.cos(angle))
                .add(up.scale(vertical * radius * Math.sin(angle)));
    }

    public static Vec3 limit(Vec3 vector, double maximum) {
        double length = vector.length();
        return length > maximum ? vector.scale(maximum / length) : vector;
    }

    public static Vec3 steer(Vec3 velocity, Vec3 error, Vec3 feedForward, double maxSpeed) {
        Vec3 correction = limit(error.scale(0.22D), 0.9D);
        Vec3 desired = limit(feedForward.add(correction), maxSpeed);
        return velocity.add(limit(desired.subtract(velocity).scale(0.45D), 0.12D + feedForward.length() * 0.3D));
    }

    public static double followSpeed(double ownerSpeed, double gap) {
        return Mth.clamp(ownerSpeed * 1.2D + Math.max(0.0D, gap - 0.5D) * 0.12D + 0.12D, 0.12D, 3.0D);
    }

    /** Samples accepted world positions, including riding and vertical movement. */
    public static final class Motion {
        private Vec3 previous;
        private long previousTick;
        private Vec3 velocity = Vec3.ZERO;

        public Vec3 sample(Vec3 position, long tick) {
            if (previous != null && tick == previousTick) return velocity;
            long elapsed = tick - previousTick;
            velocity = previous == null || elapsed <= 0 || elapsed > 2 || previous.distanceToSqr(position) > 64.0D
                    ? Vec3.ZERO : position.subtract(previous).scale(1.0D / elapsed);
            previous = position;
            previousTick = tick;
            return velocity;
        }

        public void reset() {
            previous = null;
            velocity = Vec3.ZERO;
        }
    }

    public static final class LockVoice {
        private UUID previous;

        public boolean update(UUID target) {
            boolean play = target != null && !target.equals(previous);
            previous = target;
            return play;
        }
    }
}
