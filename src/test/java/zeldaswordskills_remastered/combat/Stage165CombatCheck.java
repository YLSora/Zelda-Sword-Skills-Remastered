package zeldaswordskills_remastered.combat;

/**
 * Standalone numerical checks of the Stage 16.5 combat rules. Only the pure production logic is
 * exercised - burst thresholds, combo damage and Iai Slash deadlines - so no game
 * bootstrap is needed and no registry is touched.
 */
public final class Stage165CombatCheck {
    public static void main(String[] args) {
        flashAssaultHitCounts();
        comboDamageAccumulator();
        iaiSlashDeadlines();
        System.out.println("Stage 16.5 combat: Flash Assault attack-speed hit counts and True Master "
                + "Sword combo-damage accumulation and Iai Slash deadlines passed.");
    }

    /** The speed bands share boundaries for hit count and per-hit damage. */
    private static void flashAssaultHitCounts() {
        require(FlashAssaultBurst.hitCount(0.45D) == 2, "Megaton hammer speed must give 2 hits");
        require(FlashAssaultBurst.hitCount(0.6D) == 2, "Skull hammer speed must give 2 hits");
        require(FlashAssaultBurst.hitCount(0.8D) == 2, "Wooden hammer speed must give 2 hits");
        require(FlashAssaultBurst.hitCount(0.9D) == 2, "Biggoron sword speed must give 2 hits");
        require(FlashAssaultBurst.hitCount(0.9001D) == 4, "Just above 0.9 must give 4 hits");
        require(FlashAssaultBurst.hitCount(1.0D) == 4, "Diamond axe speed must give 4 hits");
        require(FlashAssaultBurst.hitCount(1.2D) == 4, "Darknut sword speed must give 4 hits");
        require(FlashAssaultBurst.hitCount(1.5D) == 4, "The 1.5 boundary belongs to the four-hit band");
        require(FlashAssaultBurst.hitCount(1.5001D) == 6, "Just above 1.5 must give 6 hits");
        require(FlashAssaultBurst.hitCount(1.6D) == 6, "Ordinary sword speed must give 6 hits");
        require(FlashAssaultBurst.hitCount(4.0D) == 6, "A bare hand must give 6 hits");
        for (int level = 1; level <= 5; level++) {
            require(Math.abs(FlashAssaultBurst.damage(8, 0.9D, level) - (12 + 2.5D * level)) < 0.0001D,
                    "Slow tier damage differs");
            require(Math.abs(FlashAssaultBurst.damage(8, 1.5D, level) - (6.4D + 1.2D * level)) < 0.0001D,
                    "Medium tier damage differs");
            require(Math.abs(FlashAssaultBurst.damage(8, 1.5001D, level) - (5.2D + 0.8D * level)) < 0.0001D,
                    "Fast tier damage differs");
        }
        // Intervals keep every tier inside a comparable burst and stay ordered by hit count.
        require(FlashAssaultBurst.hitInterval(6) == 2, "Six hits use a two-tick interval");
        require(FlashAssaultBurst.hitInterval(4) == 4, "Four hits use a four-tick interval");
        require(FlashAssaultBurst.hitInterval(2) == 10, "Two hits use a ten-tick interval");
    }

    /**
     * The True Master Sword folds its bonus into the recorded combo damage without adding a hit.
     * The accumulator is checked directly because the bonus itself needs a live target to hurt.
     */
    private static void comboDamageAccumulator() {
        PlayerCombatState state = new PlayerCombatState();
        state.setTarget(7);
        state.recordHit(7, 5.0F, 20, 100);
        require(state.comboCount() == 1 && state.comboDamage() == 5.0F, "First hit did not start the combo");
        for (int hit = 2; hit <= 10; hit++) state.recordHit(7, 5.0F, 20, 100 + hit);
        require(state.comboCount() == 10 && state.comboDamage() == 50.0F, "Ten hits did not total 50 damage");
        // The bonus rides along with the tenth hit: damage rises, the count does not.
        state.addComboDamage(5.0F);
        require(state.comboCount() == 10 && state.comboDamage() == 55.0F,
                "The tenth-hit bonus changed the combo count or failed to add damage");
        state.recordHit(7, 5.0F, 20, 111);
        require(state.comboCount() == 11 && state.comboDamage() == 60.0F, "The eleventh hit was not ordinary");
        // A finished combo must not silently absorb a late bonus.
        state.finishCombo();
        state.addComboDamage(5.0F);
        require(state.comboDamage() == 60.0F, "A finished combo absorbed bonus damage");
        // A negative amount can never reduce the recorded damage.
        state.clearCombo();
        state.recordHit(7, 5.0F, 20, 130);
        state.addComboDamage(-3.0F);
        require(state.comboDamage() == 5.0F, "Negative bonus damage reduced the combo total");
    }

    private static void iaiSlashDeadlines() {
        IaiSlash.State state = new IaiSlash.State();
        state.cancel(1000);
        require(!state.coolingDown(1000), "Canceling an inactive stance started a cooldown");
        state.arm(7, 0, 1000);
        require(!state.expired(1099) && state.expired(1100), "Stance must expire at 100 ticks");
        state.draw(1, true, 1099);
        require(state.ready(1099) && !state.ready(1100), "Drawing late extended the total stance deadline");
        state.cancel(1100);
        require(!state.active() && state.coolingDown(1299) && !state.coolingDown(1300),
                "Cancellation must clear the mark and impose exactly 200 ticks of cooldown");
        state.cancel(1200);
        require(!state.coolingDown(1300), "Repeated cancellation extended the cooldown");

        state.reset();
        state.arm(7, 0, 2000);
        state.draw(1, true, 2010);
        require(state.ready(2049) && state.expired(2050), "The 40-tick draw window was lost");
        state.draw(2, true, 2040);
        require(state.expired(2050), "Switching weapons extended the draw window");
        state.clear();
        require(!state.coolingDown(2050), "Successful completion added a cancellation cooldown");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
