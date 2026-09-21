package zeldaswordskills_remastered.entity;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.RandomSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Standalone numerical checks of the production flight functions; no game bootstrap required. */
public final class NaviFlightCheck {
    public static void main(String[] args) {
        motionSamples();
        hoverContinuity();
        stationaryHover();
        sphereOrbit();
        randomWander();
        followAndStop();
        obstacleAvoidance();
        facingPlane();
        lockVoices();
        dialogueResources();
        System.out.println("Fairy flight, Navi obstacle avoidance and dialogue resource checks passed.");
    }

    private static void obstacleAvoidance() {
        AABB body = AABB.ofSize(Vec3.ZERO, 0.25D, 0.25D, 0.25D);
        Vec3 forward = new Vec3(0.06D, 0, 0);
        near(NaviObstacleAvoidance.steer(body, forward, List.of(), move -> true), forward, 0,
                "Open space must preserve the existing orbit velocity");

        AABB wall = new AABB(0.8D, -1, -1, 1.2D, 1, 1);
        Vec3 detour = NaviObstacleAvoidance.steer(body, forward, List.of(wall), move -> true);
        check(detour.x < forward.x && (Math.abs(detour.y) > 0 || Math.abs(detour.z) > 0),
                "Navi must turn before hitting a wall");
        Vec3 position = Vec3.ZERO;
        Vec3 velocity = Vec3.ZERO;
        Vec3 destination = new Vec3(2.5D, 0, 0);
        for (int tick = 0; tick < 400; tick++) {
            AABB bounds = body.move(position);
            Vec3 requested = NaviFlight.steer(velocity, destination.subtract(position), Vec3.ZERO, 0.1D);
            velocity = NaviObstacleAvoidance.steer(bounds, requested, List.of(wall), move -> true);
            check(!wall.inflate(0.125D).clip(position, position.add(velocity)).isPresent(),
                    "The entire movement must clear the wall, not only its endpoint");
            position = position.add(velocity);
            check(!body.move(position).intersects(wall), "Detour must not enter the solid block");
        }
        check(position.distanceTo(destination) < 0.15D, "Navi must pass the obstacle and return to the intended path");

        AABB floor = new AABB(-4, -1, -4, 4, -0.3D, 4);
        AABB side = new AABB(0.3D, -1, -4, 1, 4, 4);
        Vec3 escape = NaviObstacleAvoidance.steer(body, new Vec3(0.04D, -0.04D, 0),
                List.of(floor, side), move -> true);
        check(escape.x <= 0 && escape.y >= 0 && escape.lengthSqr() > 0,
                "A floor-wall corner must steer away from both surfaces");
        near(NaviObstacleAvoidance.limitHover(body, new Vec3(0, -0.01D, 0), List.of(floor)), Vec3.ZERO, 0,
                "Crouching hover must stop approaching nearby blocks without wandering off");
        near(NaviObstacleAvoidance.limitHover(body, new Vec3(0, 0.01D, 0), List.of(floor)),
                new Vec3(0, 0.01D, 0), 1.0E-12D, "Crouching hover can drift away from a nearby block");
        AABB marginWall = new AABB(-1, -1, -1, -0.475D, 1, 1);
        near(NaviObstacleAvoidance.limitHover(body, new Vec3(-0.01D, 0, 0), List.of(marginWall)), Vec3.ZERO, 0,
                "The upper face of the clearance boundary must not bypass obstacle detection");

        AABB slab = new AABB(0.8D, -1, -1, 1.2D, -0.5D, 1);
        near(NaviObstacleAvoidance.steer(body, forward, List.of(slab), move -> true), forward, 0,
                "A clear path over a slab must not be treated as a full cube");
        Vec3 fast = NaviObstacleAvoidance.steer(body, new Vec3(3, 0, 0), List.of(wall), move -> true);
        check(!body.move(fast).intersects(wall) && !wall.inflate(0.125D).clip(Vec3.ZERO, fast).isPresent(),
                "Fast pursuit must not tunnel through a block");
        Vec3 confined = NaviObstacleAvoidance.steer(body, forward, List.of(wall), move -> false);
        near(confined, Vec3.ZERO, 0, "Navi must stop when every detour violates the follow boundary");
        List<AABB> enclosure = List.of(new AABB(-1, -1, -1, -0.3D, 1, 1),
                new AABB(0.3D, -1, -1, 1, 1, 1), new AABB(-1, -1, -1, 1, -0.3D, 1),
                new AABB(-1, 0.3D, -1, 1, 1, 1), new AABB(-1, -1, -1, 1, 1, -0.3D),
                new AABB(-1, -1, 0.3D, 1, 1, 1));
        near(NaviObstacleAvoidance.steer(body, forward, enclosure, move -> true), Vec3.ZERO, 0,
                "An enclosed Navi must stop without entering any block");
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
                check(position.length() < 1.0D, "Floating motion stays close to the wander offset");
                oldVelocity = velocity;
                previous = position;
            }
        }
    }

    private static void randomWander() {
        for (double radius : new double[]{4.0D, 6.0D}) {
            for (int seed = 0; seed < 32; seed++) {
                var random = RandomSource.create(seed);
                var wander = new NaviFlight.Wander();
                Vec3 phase = new Vec3(seed * 0.73D, seed * 1.13D, seed * 1.89D);
                Vec3 previous = wander.tick(random, 0, phase, radius);
                Vec3 previousVelocity = Vec3.ZERO;
                int quadrants = 0;
                for (int tick = 1; tick <= 12000; tick++) {
                    Vec3 offset = wander.tick(random, tick, phase, radius);
                    Vec3 velocity = offset.subtract(previous);
                    check(velocity.length() < 0.075D, "Random wandering must remain slow");
                    if (tick > 1) check(velocity.subtract(previousVelocity).length() < 0.008D,
                            "Changing random destinations must not snap velocity");
                    Vec3 fromOrigin = radius == 4.0D ? offset.add(0, 1.62D, 0) : offset;
                    check(fromOrigin.length() < radius + 2.0D, "Wandering must stay inside the tether");
                    if (Math.abs(offset.x) > 0.5D && Math.abs(offset.z) > 0.5D)
                        quadrants |= 1 << ((offset.x > 0 ? 1 : 0) + (offset.z > 0 ? 2 : 0));
                    previous = offset;
                    previousVelocity = velocity;
                }
                check(quadrants == 15, "Random wandering must visit every horizontal quadrant");
            }
        }
    }

    private static void stationaryHover() {
        Vec3 position = new Vec3(7, 3, -4);
        for (int seed = 0; seed < 32; seed++) {
            Vec3 phase = new Vec3(seed * 0.73D, seed * 1.13D, seed * 1.89D);
            Vec3 anchor = position.subtract(NaviFlight.smallHover(0, phase));
            Vec3 previous = position;
            for (int tick = 1; tick <= 4000; tick++) {
                Vec3 target = anchor.add(NaviFlight.smallHover(tick, phase));
                check(target.distanceTo(position) < 0.35D, "Crouching hover must stay near its original position");
                check(target.distanceTo(previous) < 0.01D, "Crouching hover must remain smooth");
                previous = target;
            }
        }
    }

    private static void followAndStop() {
        for (Vec3 ownerVelocity : new Vec3[]{new Vec3(0.2159D, 0, 0), new Vec3(0.2807D, 0, 0),
                new Vec3(1.2D, 0, 0.4D), new Vec3(0.8D, 0.6D, 0), new Vec3(0, -1.2D, 0), new Vec3(2.1D, 0.3D, 0)}) {
            Vec3 position = new Vec3(-5, 1, 0);
            Vec3 velocity = Vec3.ZERO;
            Vec3 owner = Vec3.ZERO;
            Vec3 previousTarget = null;
            var orbitRandom = RandomSource.create(42);
            var speedRandom = RandomSource.create(43);
            var orbit = new NaviFlight.SphereOrbit();
            double activity = 0;
            double orbitAngularSpeed = 0;
            int orbitSpeedTicks = 0;
            for (int tick = 0; tick < 900; tick++) {
                Vec3 step = tick < 600 ? ownerVelocity : Vec3.ZERO;
                owner = owner.add(step);
                activity += (Math.min(1, step.length() / 0.28D) - activity) * 0.12D;
                Vec3 center = owner.add(0, 1.95D, 0);
                if (!orbit.isInitialized()) orbit.reset(orbitRandom, position.subtract(center));
                if (--orbitSpeedTicks <= 0) {
                    orbitSpeedTicks = 60 + speedRandom.nextInt(141);
                    orbitAngularSpeed = ((1.0D + speedRandom.nextDouble() * 0.25D) / 20.0D) / 1.5D;
                }
                double angularStep = orbitAngularSpeed + activity * 0.0275D;
                Vec3 target = center.add(orbit.tick(orbitRandom, angularStep, 1.5D));
                check(target.y - owner.y > 0.4D, "Player sphere must remain clear of the floor");
                double anchorSpeed = previousTarget == null ? 0 : target.distanceTo(previousTarget);
                double maxSpeed = Math.min(1.5D, Math.max(0.3D, Math.max(step.length(), anchorSpeed) * 0.6D + 0.2D));
                Vec3 feed = previousTarget == null ? Vec3.ZERO : NaviFlight.limit(target.subtract(previousTarget), maxSpeed);
                Vec3 next = NaviFlight.steer(velocity, target.subtract(position), feed, maxSpeed);
                check(Double.isFinite(next.length()), "Finite steering");
                check(next.subtract(velocity).length() <= 0.12D + feed.length() * 0.3D + 1.0E-8D, "Acceleration bound");
                velocity = owner.add(NaviFlight.limit(position.add(next).subtract(owner), 3.5D)).subtract(position);
                position = position.add(velocity);
                check(position.distanceTo(owner) <= 3.500001D, "Following and stopping must respect the sphere tether");
                if (tick > 200 && tick < 600 && ownerVelocity.length() <= 0.4D) {
                    check(position.distanceTo(target) < 3.0D, "Real-time movement must not accumulate a following gap");
                }
                if (tick > 720) check(velocity.length() < 0.08D, "Stopping must return to slow hover");
                if (tick > 720) check(maxSpeed <= 0.300001D, "Idle orbit speed must be half the locked minimum");
                if (tick > 820) {
                    double blocksPerSecond = orbitAngularSpeed * 1.5D * 20.0D;
                    check(blocksPerSecond >= 1.0D && blocksPerSecond <= 1.25D,
                            "Stationary orbit speed must remain within its configured range");
                }
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

    private static void sphereOrbit() {
        for (int seed = 0; seed < 32; seed++) {
            var random = RandomSource.create(seed);
            var orbit = new NaviFlight.SphereOrbit();
            orbit.reset(random, new Vec3(1, 0, 0));
            Vec3 previous = new Vec3(1.5D, 0, 0);
            Vec3 firstNormal = null;
            boolean changedPlane = false;
            for (int tick = 0; tick < 1000; tick++) {
                Vec3 point = orbit.tick(random, 0.0375D, 1.5D);
                check(Math.abs(point.length() - 1.5D) < 1.0E-9D, "Great-circle path must remain on the sphere");
                check(point.distanceTo(previous) < 0.06D, "Changing the great-circle plane must not jump");
                if (tick == 1) firstNormal = previous.cross(point).normalize();
                if (firstNormal != null && Math.abs(point.dot(firstNormal)) > 0.1D) changedPlane = true;
                previous = point;
            }
            check(changedPlane, "Sphere orbit must choose new great-circle planes");
        }
    }

    private static void dialogueResources() {
        for (String language : new String[]{"", "_en_us"}) {
            String path = "/data/zeldaswordskills_remastered/navi_dialogue" + language + ".md";
            var groups = new HashSet<Integer>();
            try (var stream = Objects.requireNonNull(NaviDialogueService.class.getResourceAsStream(path), path);
                    var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (!line.startsWith("## ")) continue;
                    int end = line.indexOf('.');
                    if (end > 3) groups.add(Integer.parseInt(line.substring(3, end)));
                }
            } catch (java.io.IOException exception) {
                throw new AssertionError("Could not read " + path, exception);
            }
            check(groups.containsAll(java.util.Set.of(1, 2, 3, 4, 5, 6)),
                    "Navi dialogue resource is missing a required group: " + path);
        }
    }

    private static void near(Vec3 actual, Vec3 expected, double tolerance, String message) {
        check(actual.distanceTo(expected) <= tolerance, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
