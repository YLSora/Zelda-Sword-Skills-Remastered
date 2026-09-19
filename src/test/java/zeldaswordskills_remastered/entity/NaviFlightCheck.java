package zeldaswordskills_remastered.entity;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Standalone numerical checks of the production flight functions; no game bootstrap required. */
public final class NaviFlightCheck {
    public static void main(String[] args) {
        motionSamples();
        hoverContinuity();
        followAndStop();
        facingPlane();
        lockVoices();
        System.out.println("Navi flight: sampling, continuity, tracking, stopping, facing plane and lock voice transitions passed.");
    }

    private static void motionSamples() {
        NaviFlight.Motion motion = new NaviFlight.Motion();
        near(motion.sample(Vec3.ZERO, 10), Vec3.ZERO, 0, "First sample");
        near(motion.sample(new Vec3(0.28D, 0.42D, 0), 11), new Vec3(0.28D, 0.42D, 0), 1.0E-12D, "Three-dimensional velocity");
        near(motion.sample(new Vec3(1.28D, 0.42D, 0), 13), new Vec3(0.5D, 0, 0), 1.0E-12D, "Elapsed ticks");
        near(motion.sample(new Vec3(100, 50, 0), 14), Vec3.ZERO, 0, "Teleport reset");
        near(motion.sample(new Vec3(100, 50, 0), 15), Vec3.ZERO, 0, "Stationary sample");
        motion.reset();
        near(motion.sample(new Vec3(-100, 5, 0), 16), Vec3.ZERO, 0, "Dimension reset");
    }

    private static void hoverContinuity() {
        for (int seed = 0; seed < 32; seed++) {
            Vec3 phase = new Vec3(seed * 0.73D, seed * 1.13D, seed * 1.89D);
            Vec3 previous = NaviFlight.hover(0, phase);
            Vec3 oldVelocity = Vec3.ZERO;
            for (int tick = 1; tick <= 4000; tick++) {
                Vec3 position = NaviFlight.hover(tick, phase);
                Vec3 velocity = position.subtract(previous);
                check(velocity.length() < 0.05D, "Idle drift must remain slow");
                if (tick > 1) check(velocity.subtract(oldVelocity).length() < 0.003D, "No random velocity discontinuity");
                check(position.x >= 0.82D && position.x <= 1.78D && position.length() < 2.2D, "Hover stays beside the owner");
                oldVelocity = velocity;
                previous = position;
            }
        }
    }

    private static void followAndStop() {
        for (Vec3 ownerVelocity : new Vec3[]{new Vec3(0.2159D, 0, 0), new Vec3(0.2807D, 0, 0),
                new Vec3(1.2D, 0, 0.4D), new Vec3(0.8D, 0.6D, 0), new Vec3(0, -1.2D, 0), new Vec3(2.1D, 0.3D, 0)}) {
            Vec3 phase = new Vec3(1, 2, 3);
            Vec3 position = new Vec3(-5, 1, 0);
            Vec3 velocity = Vec3.ZERO;
            Vec3 owner = Vec3.ZERO;
            Vec3 previousTarget = null;
            double activity = 0;
            double time = 0;
            for (int tick = 0; tick < 900; tick++) {
                Vec3 step = tick < 600 ? ownerVelocity : Vec3.ZERO;
                owner = owner.add(step);
                activity += (Math.min(1, step.length() / 0.28D) - activity) * 0.12D;
                time += 1 + activity * 1.5D;
                Vec3 target = owner.add(NaviFlight.hover(time, phase));
                double maxSpeed = NaviFlight.followSpeed(step.length(), position.distanceTo(target));
                Vec3 feed = previousTarget == null ? Vec3.ZERO : NaviFlight.limit(target.subtract(previousTarget), maxSpeed);
                Vec3 next = NaviFlight.steer(velocity, target.subtract(position), feed, maxSpeed);
                check(Double.isFinite(next.length()) && next.length() <= 3.000001D, "Bounded finite steering");
                check(next.subtract(velocity).length() <= 0.12D + feed.length() * 0.3D + 1.0E-8D, "Acceleration bound");
                velocity = next;
                position = position.add(velocity);
                if (tick > 200 && tick < 600) {
                    check(position.distanceTo(target) < 3.0D, "Real-time movement must not accumulate a following gap");
                }
                if (tick > 720) check(velocity.length() < 0.08D, "Stopping must return to slow hover");
                previousTarget = target;
            }
        }
    }

    private static void facingPlane() {
        Vec3 right = null;
        Vec3 phase = new Vec3(1, 2, 3);
        for (int tick = 0; tick <= 2000; tick++) {
            double angle = tick * Math.PI * 2 / 2000;
            Vec3 eye = new Vec3(Math.sin(angle) * 8, Math.cos(angle) * 8, 0.0001D);
            Vec3 normal = NaviFlight.facingNormal(eye, Vec3.ZERO);
            Vec3 next = NaviFlight.planeRight(normal, right);
            check(Math.abs(next.dot(normal)) < 1.0E-8D && Math.abs(next.length() - 1) < 1.0E-8D, "Orthonormal plane");
            if (right != null) check(right.dot(next) > 0.99D, "No pole flip when crossing above/below the enemy");
            for (double size : new double[]{0.25D, 0.6D, 2, 4}) {
                Vec3 orbit = NaviFlight.orbit(tick * 0.075D, phase, size, size * 2, normal, next);
                check(Double.isFinite(orbit.length()) && Math.abs(orbit.dot(normal)) < 1.0E-8D,
                        "All enemy sizes orbit on the player-facing plane");
            }
            right = next;
        }
        Vec3 normal = NaviFlight.facingNormal(Vec3.ZERO, Vec3.ZERO);
        check(Double.isFinite(NaviFlight.planeRight(normal, normal).length()), "Degenerate coincident view");
    }

    private static void lockVoices() {
        NaviFlight.LockVoice voice = new NaviFlight.LockVoice();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        check(!voice.update(null), "No voice without target");
        check(voice.update(a), "First lock speaks");
        for (int tick = 0; tick < 100; tick++) check(!voice.update(a), "Maintaining a lock must stay quiet");
        check(voice.update(b), "Manual or kill transfer speaks");
        check(!voice.update(null), "Clearing is quiet");
        check(voice.update(b), "Reacquiring speaks");
    }

    private static void near(Vec3 actual, Vec3 expected, double tolerance, String message) {
        check(actual.distanceTo(expected) <= tolerance, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
