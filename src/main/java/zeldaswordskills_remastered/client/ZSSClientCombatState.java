package zeldaswordskills_remastered.client;

public final class ZSSClientCombatState {
    private static int targetId = -1;
    private static int iaiTargetId = -1;
    private static int comboCount;
    private static int comboMaximum;
    private static float comboDamage;
    private static long comboDeadline;
    private static long focusUntil;
    private static long dashImmuneUntil;
    private static boolean comboFinished;
    private static boolean groundSlamReady;
    private static long lastUpdateNanos;

    private ZSSClientCombatState() {
    }

    public static void apply(int newTargetId, int newComboCount, int newComboMaximum, float newComboDamage,
                             long newComboDeadline, boolean newComboFinished, long newFocusUntil, boolean newGroundSlamReady, int newIaiTargetId,
                             long newDashImmuneUntil) {
        boolean comboChanged = comboCount != newComboCount || comboMaximum != newComboMaximum
                || Float.compare(comboDamage, newComboDamage) != 0 || comboDeadline != newComboDeadline
                || comboFinished != newComboFinished;
        targetId = newTargetId;
        iaiTargetId = newIaiTargetId;
        comboCount = newComboCount;
        comboMaximum = newComboMaximum;
        comboDamage = newComboDamage;
        comboDeadline = newComboDeadline;
        comboFinished = newComboFinished;
        focusUntil = newFocusUntil;
        groundSlamReady = newGroundSlamReady;
        dashImmuneUntil = newDashImmuneUntil;
        if (comboChanged) lastUpdateNanos = System.nanoTime();
    }

    public static int targetId() { return targetId; }
    public static boolean hasTarget() { return targetId >= 0; }
    public static int iaiTargetId() { return iaiTargetId; }
    public static void clearIaiTarget() { iaiTargetId = -1; }
    public static int comboCount() { return comboCount; }
    public static int comboMaximum() { return comboMaximum; }
    public static float comboDamage() { return comboDamage; }
    public static boolean comboFinished() { return comboFinished; }
    public static boolean groundSlamReady() { return groundSlamReady; }
    public static boolean dashImmune(long now) { return dashImmuneUntil > 0L && now <= dashImmuneUntil; }
    public static boolean focusActive(long now) { return hasTarget() && focusUntil > 0 && now < focusUntil; }

    public static boolean shouldShowCombo(long gameTime) {
        if (comboCount <= 0) return false;
        return comboFinished ? System.nanoTime() - lastUpdateNanos < 5_000_000_000L : gameTime <= comboDeadline;
    }

    public static void clear() {
        apply(-1, 0, 0, 0.0F, 0L, false, 0L, false, -1, 0L);
    }
}
