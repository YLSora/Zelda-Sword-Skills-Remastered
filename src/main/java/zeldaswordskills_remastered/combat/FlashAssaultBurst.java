package zeldaswordskills_remastered.combat;

/**
 * The pure arithmetic behind a Flash Assault burst: how many hits a weapon's attack speed earns and
 * how far apart they land. It is deliberately free of any registry or entity reference, the same way
 * {@link FlashAssaultMovement} isolates the approach maths, so the thresholds can be reasoned about
 * and tested on their own.
 */
public final class FlashAssaultBurst {
    private FlashAssaultBurst() {}

    /** Both hit count and damage tier follow the weapon's attack speed. */
    public static int hitCount(double attackSpeed) {
        if (attackSpeed > 1.5D) return 6;
        if (attackSpeed > 0.9D) return 4;
        return 2;
    }

    public static float damage(double attackDamage, double attackSpeed, int level) {
        return switch (hitCount(attackSpeed)) {
            case 2 -> (float) (attackDamage * 1.5D + 2.5D * level);
            case 4 -> (float) (attackDamage * 0.8D + 1.2D * level);
            default -> (float) (attackDamage * 0.65D + 0.8D * level);
        };
    }

    /** Keeps the existing two/four-hit cadence; six hits span ten ticks. */
    public static int hitInterval(int hits) {
        return switch (hits) {
            case 6 -> 2;
            case 4 -> 4;
            default -> 10;
        };
    }
}
