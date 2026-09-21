package zeldaswordskills_remastered.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/** Local steering around block collision boxes, independent of the orbit timers. */
public final class NaviObstacleAvoidance {
    public static final double CLEARANCE = 0.35D;
    private static final double EPSILON = 1.0E-6D;

    private NaviObstacleAvoidance() {}

    public static double lookAhead(Vec3 velocity) {
        return Math.max(velocity.length(), Mth.clamp(velocity.length() * 5.0D, 0.75D, 2.0D));
    }

    public static Vec3 steer(AABB bounds, Vec3 velocity, List<AABB> obstacles, Predicate<Vec3> allowedMove) {
        double speed = velocity.length();
        if (speed < EPSILON) return Vec3.ZERO;
        Vec3 center = bounds.getCenter();
        List<AABB> expanded = expand(bounds, obstacles);
        Vec3 forward = velocity.scale(1.0D / speed);
        double lookAhead = lookAhead(velocity);
        if (clearance(center, expanded) >= CLEARANCE
                && freeDistance(center, forward, lookAhead, expanded) >= lookAhead && allowedMove.test(velocity)) {
            return velocity;
        }

        // Include the current heading and fixed detours; score longer clear paths before alignment.
        Vec3 best = Vec3.ZERO;
        double bestScore = -Double.MAX_VALUE;
        double detourSpeed = Math.min(speed, 0.2D);
        for (int index = -1; index < 27; index++) {
            Vec3 direction = index < 0 ? forward
                    : new Vec3(index / 9 - 1, index / 3 % 3 - 1, index % 3 - 1).normalize();
            if (direction.lengthSqr() < EPSILON) continue;
            double distance = freeDistance(center, direction, lookAhead, expanded);
            if (distance < EPSILON) continue;
            Vec3 candidate = direction.scale(Math.min(detourSpeed, distance));
            if (!allowedMove.test(candidate)) continue;
            double gap = clearance(center.add(candidate), expanded);
            double score = 1.5D * distance / lookAhead + 0.6D * direction.dot(forward)
                    + 0.6D * Math.min(1.0D, gap / CLEARANCE) + 0.01D * direction.y;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    public static Vec3 limitHover(AABB bounds, Vec3 velocity, List<AABB> obstacles) {
        double speed = velocity.length();
        if (speed < EPSILON) return Vec3.ZERO;
        Vec3 direction = velocity.scale(1.0D / speed);
        return direction.scale(freeDistance(bounds.getCenter(), direction, speed, expand(bounds, obstacles)));
    }

    private static List<AABB> expand(AABB bounds, List<AABB> obstacles) {
        return obstacles.stream().map(box -> box.inflate(bounds.getXsize() * 0.5D,
                bounds.getYsize() * 0.5D, bounds.getZsize() * 0.5D)).toList();
    }

    private static double clearance(Vec3 center, List<AABB> obstacles) {
        double distanceSqr = Double.POSITIVE_INFINITY;
        for (AABB box : obstacles) distanceSqr = Math.min(distanceSqr, box.distanceToSqr(center));
        return Math.sqrt(distanceSqr);
    }

    private static double freeDistance(Vec3 start, Vec3 direction, double maximum, List<AABB> obstacles) {
        double distance = maximum;
        Vec3 end = start.add(direction.scale(maximum));
        for (AABB box : obstacles) {
            if (box.contains(start)) return 0;
            AABB padded = box.inflate(CLEARANCE + EPSILON);
            if (padded.contains(start)) {
                // Already close to a surface: permit escape or sliding, but never approach it further.
                Vec3 away = start.subtract(new Vec3(Mth.clamp(start.x, box.minX, box.maxX),
                        Mth.clamp(start.y, box.minY, box.maxY), Mth.clamp(start.z, box.minZ, box.maxZ)));
                if (direction.dot(away) < -EPSILON) return 0;
            } else {
                var hit = padded.clip(start, end);
                if (hit.isPresent()) distance = Math.min(distance, Math.max(0, start.distanceTo(hit.get()) - EPSILON));
            }
        }
        return distance;
    }
}
