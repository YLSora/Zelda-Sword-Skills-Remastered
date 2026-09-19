package zeldaswordskills_remastered.combat;

/**
 * The pure arithmetic behind a Flash Assault burst: how many hits a weapon's attack speed earns and
 * how far apart they land. It is deliberately free of any registry or entity reference, the same way
 * {@link FlashAssaultMovement} isolates the approach maths, so the thresholds can be reasoned about
 * and tested on their own.
 */
public final class FlashAssaultBurst {
    private FlashAssaultBurst() {}

    /**
     * Number of hits for an attack speed: at most 1.0 gives 2, then 1.5 gives 3, 2.0 gives 4, and
     * anything faster gives 5. A faster weapon therefore lands more, smaller hits, and the weapon's
     * class no longer decides the count.
     */
    public static int hitCount(double attackSpeed) {
        if (attackSpeed > 2.0D) return 5;
        if (attackSpeed > 1.5D) return 4;
        if (attackSpeed > 1.0D) return 3;
        return 2;
    }

    /** Ticks between hits, chosen so every tier spans a comparable 12-13 tick burst. */
    public static int hitInterval(int hits) {
        return switch (hits) {
            case 5 -> 3;
            case 4 -> 4;
            case 3 -> 5;
            default -> 10;
        };
    }
}
