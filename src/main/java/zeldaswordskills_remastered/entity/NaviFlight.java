package zeldaswordskills_remastered.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Fairy trajectories and steering, independent of entity lifecycle and collisions. */
public final class NaviFlight {
    private NaviFlight() {}

    public static Vec3 hover(double time, Vec3 phase) {
        return new Vec3(0.35D * Math.sin(time * 0.033D + phase.x)
                        + 0.12D * Math.sin(time * 0.057D + phase.z),
                0.15D + 0.22D * Math.sin(time * 0.027D + phase.y)
                        + 0.08D * Math.cos(time * 0.049D + phase.x),
                0.45D * Math.cos(time * 0.029D + phase.z)
                        + 0.18D * Math.sin(time * 0.043D + phase.y));
    }

    public static Vec3 smallHover(double time, Vec3 phase) {
        return hover(time, phase).scale(0.2D);
    }

    public static final class Wander {
        private Vec3 offset = Vec3.ZERO;
        private Vec3 velocity = Vec3.ZERO;
        private Vec3 target = Vec3.ZERO;
        private int remainingTicks;

        public Vec3 tick(RandomSource random, double time, Vec3 phase, double radius) {
            if (--remainingTicks <= 0) {
                remainingTicks = 120 + random.nextInt(161);
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = Math.sqrt(random.nextDouble()) * radius;
                target = new Vec3(Math.cos(angle) * distance, (random.nextDouble() - 0.5D) * 1.2D,
                        Math.sin(angle) * distance);
            }
            Vec3 desired = limit(target.subtract(offset).scale(0.04D), 0.025D);
            velocity = velocity.lerp(desired, 0.08D);
            offset = offset.add(velocity);
            return offset.add(hover(time, phase));
        }
    }

    public static final class SphereOrbit {
        private Vec3 normal;
        private Vec3 right;
        private double angle;
        private int direction;
        private int remainingTicks;

        public boolean isInitialized() {
            return right != null;
        }

        public void reset(RandomSource random, Vec3 initialDirection) {
            right = initialDirection.lengthSqr() < 1.0E-8D ? randomUnit(random) : initialDirection.normalize();
            normal = randomPlaneNormal(random, right);
            angle = 0;
            direction = random.nextBoolean() ? 1 : -1;
            remainingTicks = 60 + random.nextInt(141);
        }

        public Vec3 tick(RandomSource random, double angularStep, double radius) {
            if (!isInitialized()) reset(random, randomUnit(random));
            if (remainingTicks-- <= 0) {
                right = point();
                normal = randomPlaneNormal(random, right);
                angle = 0;
                direction = random.nextBoolean() ? 1 : -1;
                remainingTicks = 59 + random.nextInt(141);
            }
            angle += angularStep * direction;
            return point().scale(radius);
        }

        private Vec3 point() {
            Vec3 up = normal.cross(right).normalize();
            return right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
        }

        private static Vec3 randomPlaneNormal(RandomSource random, Vec3 radial) {
            Vec3 normal = radial.cross(randomUnit(random));
            if (normal.lengthSqr() < 1.0E-8D) {
                Vec3 fallback = Math.abs(radial.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                normal = radial.cross(fallback);
            }
            return normal.normalize();
        }

        private static Vec3 randomUnit(RandomSource random) {
            double y = random.nextDouble() * 2.0D - 1.0D;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double horizontal = Math.sqrt(1.0D - y * y);
            return new Vec3(horizontal * Math.cos(angle), y, horizontal * Math.sin(angle));
        }
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
