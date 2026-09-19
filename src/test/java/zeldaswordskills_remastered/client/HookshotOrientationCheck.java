package zeldaswordskills_remastered.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Checks the actual renderer rotation without starting a client or creating a render context. */
public final class HookshotOrientationCheck {
    public static void main(String[] args) {
        for (Vec3 direction : new Vec3[]{new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1),
                new Vec3(3, 5, -7), new Vec3(-4, -8, 2)}) {
            Vector3f outbound = tip(direction.scale(12));
            for (double distance : new double[]{24, 12, 6, 3, 1, 0.01}) {
                Vec3 towardOwner = direction.normalize().scale(distance);
                assertOutward(towardOwner);
                require(outbound.distance(tip(towardOwner)) < 1.0E-5F, "Return distance reversed the head");
            }
        }
        Vector3f previous = null;
        for (int step = 0; step <= 720; step++) {
            double angle = Math.toRadians(step);
            Vec3 towardOwner = new Vec3(Math.sin(angle) * 8, Math.sin(angle * 2) * 5, Math.cos(angle) * 8);
            assertOutward(towardOwner);
            Vector3f current = tip(towardOwner);
            if (previous != null) require(previous.dot(current) > 0.99F, "Moving owner caused a head flip");
            previous = current;
        }
        require(tip(Vec3.ZERO).equals(new Vector3f(0, 0, -1)), "Coincident positions must have a finite pose");
        System.out.println("Hookshot orientation: axes, diagonals, return distance, moving owner and zero vector passed.");
    }

    private static void assertOutward(Vec3 towardOwner) {
        Vec3 expected = towardOwner.normalize().scale(-1);
        require(tip(towardOwner).distance(new Vector3f((float) expected.x, (float) expected.y, (float) expected.z))
                < 1.0E-5F, "Head must point away from the owner: " + towardOwner);
    }

    private static Vector3f tip(Vec3 towardOwner) {
        return ToolProjectileRenderer.hookHeadRotation(towardOwner).transform(new Vector3f(0, 0, -1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
