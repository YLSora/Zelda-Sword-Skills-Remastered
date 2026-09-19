package zeldaswordskills_remastered.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.registry.ZSSContentIds;

public final class PlayerCombatState {
    /** The physical dodge movement remains four ticks; immunity/perfect-dodge window is separate. */
    public static final int DODGE_DURATION_TICKS = 4;
    public static final int DODGE_IMMUNITY_TICKS = 20;
    public static final int PARRY_COOLDOWN_TICKS = 60;
    public static final int SPIN_ROUND_TICKS = 8;
    public static final int SWORD_BEAM_COOLDOWN_TICKS = 40;
    /** Ticks between the repeated vanilla sword sweeps of a climbing Rising Cut. */
    public static final long RISING_CUT_SLASH_INTERVAL_TICKS = 2L;
    private int targetId = -1;
    private int comboTargetId = -1;
    private int comboCount;
    private int comboMaximum;
    private float comboDamage;
    private long comboDeadline;
    private boolean comboFinished;
    private long lastTargetIntentTick = Long.MIN_VALUE;
    private ResourceLocation chargingSkill;
    private long chargeStarted;
    private Vec3 chargeDirection = Vec3.ZERO;
    private long dodgeUntil;
    private long dodgeCooldownUntil;
    private long dodgeLastTapTick = Long.MIN_VALUE;
    private Vec3 dodgeLastTapDirection = Vec3.ZERO;
    private long parryUntil;
    private long parryCooldownUntil;
    private long swordBreakUntil;
    private boolean swordBreakUsed;
    private int swordBreakTargetId = -1;
    private boolean groundSlamReady;
    private boolean groundSlamFallImmune;
    private long groundSlamHitTick = -1L;
    private final java.util.Set<Integer> groundSlamHitTargets = new java.util.HashSet<>();
    private long risingCutUntil;
    private long risingCutCooldownUntil;
    private long risingCutImmuneUntil;
    private boolean risingCutAscending;
    private long risingCutTrailStart;
    private long risingCutTrailUntil;
    /** Blocks of fall a landed Rising Cut covers without damage; zero when no waiver is pending. */
    private double risingCutFallGrace;
    /** Whether the lift has left the ground since the waiver was armed. */
    private boolean risingCutGraceAirborne;
    private int dashTargetId = -1;
    private Vec3 dashStart = Vec3.ZERO;
    private int dashLevel;
    private long dashUntil;
    private long dashCooldownUntil;
    private long dashImmuneUntil;
    private long dashLastEffect = Long.MIN_VALUE;
    private long dashLastSound = Long.MIN_VALUE;
    private long dashLastTap = Long.MIN_VALUE;
    private boolean dashContinuationQueued;
    private boolean dashContinuationUsed;
    private int dashContinuationRemaining;
    private boolean continuedDash;
    private final java.util.Set<Integer> dashHitTargets = new java.util.HashSet<>();
    private long spinUntil;
    private long spinCooldownUntil;
    private long swordBeamCooldownUntil;
    private int spinRounds;
    private int superSpinTapCount;
    private long superSpinLastTap;
    private boolean spinContinuationQueued;
    private final IaiSlash.State iaiSlash = new IaiSlash.State();
    public IaiSlash.State iaiSlash() { return iaiSlash; }
    private final FlashAssault.State flashAssault = new FlashAssault.State();
    private final HelmSplitter.State helmSplitter = new HelmSplitter.State();
    public HelmSplitter.State helmSplitter() { return helmSplitter; }
    private long focusUntil;
    private long focusVersion;
    private long fatalStrikeCooldownUntil;
    private long nextFocusRoll = -1L;
    private FatalStrike.Attempt dashAttempt;
    private boolean doubleJumpUsed;

    public int targetId() { return targetId; }
    public int comboCount() { return comboCount; }
    public int comboMaximum() { return comboMaximum; }
    public float comboDamage() { return comboDamage; }
    public long comboDeadline() { return comboDeadline; }
    public boolean comboFinished() { return comboFinished; }
    public int comboTargetId() { return comboTargetId; }

    public void beginCharge(ResourceLocation skill, long gameTime, Vec3 direction) {
        chargingSkill = skill;
        chargeStarted = gameTime;
        chargeDirection = direction == null ? Vec3.ZERO : direction;
    }

    public boolean charged(ResourceLocation skill, long gameTime, int requiredTicks) {
        return skill.equals(chargingSkill) && gameTime - chargeStarted >= requiredTicks;
    }

    public Vec3 chargeDirection() { return chargeDirection; }
    public boolean charging(ResourceLocation skill) { return skill.equals(chargingSkill); }

    public void clearCharge() {
        chargingSkill = null;
        chargeStarted = 0L;
        chargeDirection = Vec3.ZERO;
    }

    /** Disabling cancels effects without refunding cooldowns or the current airtime's jump. */
    public void disableSkill(ResourceLocation skill, long now) {
        if (charging(skill)) clearCharge();
        if (skill.equals(ZSSContentIds.SWORD_BASIC)) {
            endFocusOnTargetChange(now);
            clearTarget();
            clearCombo();
            finishParry(now);
            useSwordBreak();
            helmSplitter.clearOpening();
            clearRisingCut();
            finishDash();
            dashImmuneUntil = -1L;
            clearDashContinuation();
        } else if (skill.equals(ZSSContentIds.DODGE)) {
            dodgeUntil = 0L;
            dodgeLastTapTick = Long.MIN_VALUE;
            dodgeLastTapDirection = Vec3.ZERO;
        } else if (skill.equals(ZSSContentIds.PARRY)) {
            finishParry(now);
            useSwordBreak();
            helmSplitter.clearOpening();
        } else if (skill.equals(ZSSContentIds.SWORD_BREAK)) {
            useSwordBreak();
        } else if (skill.equals(ZSSContentIds.HELM_SPLITTER)) {
            helmSplitter.reset();
        } else if (skill.equals(ZSSContentIds.LEAPING_BLOW)) {
            clearGroundSlam();
        } else if (skill.equals(ZSSContentIds.RISING_CUT)) {
            clearRisingCut();
            risingCutTrailUntil = -1L;
            clearRisingCutFallGrace();
        } else if (skill.equals(ZSSContentIds.DASH)) {
            finishDash();
            dashImmuneUntil = -1L;
            clearDashContinuation();
        } else if (skill.equals(ZSSContentIds.CONTINUOUS_FLASH)) {
            clearDashContinuation();
        } else if (skill.equals(ZSSContentIds.SPIN_ATTACK)
                || skill.equals(ZSSContentIds.SUPER_SPIN_ATTACK)) {
            finishSpin();
        } else if (skill.equals(ZSSContentIds.MORTAL_DRAW)) {
            iaiSlash.cancel(now);
        } else if (skill.equals(ZSSContentIds.ENDING_BLOW)) {
            if (focusActive(now)) finishFocus(now, 300);
            else clearFocus();
            resetFocusRoll();
        }
    }

    public void startDodge(long now, int level) {
        dodgeUntil = now + DODGE_IMMUNITY_TICKS;
        dodgeCooldownUntil = now + 90 - 10 * net.minecraft.util.Mth.clamp(level, 1, 5);
    }
    public boolean dodgeActive(long now) { return now < dodgeUntil; }
    public boolean dodgeOnCooldown(long now) { return now < dodgeCooldownUntil; }
    public boolean acceptDodgeTap(long now, Vec3 direction) {
        boolean secondTap = dodgeLastTapTick != Long.MIN_VALUE
                && now - dodgeLastTapTick <= 10L
                && dodgeLastTapDirection.dot(direction) > 0.5D;
        if (secondTap) {
            dodgeLastTapTick = Long.MIN_VALUE;
            dodgeLastTapDirection = Vec3.ZERO;
            return true;
        }
        dodgeLastTapTick = now;
        dodgeLastTapDirection = direction;
        return false;
    }

    /** One guard cannot be refreshed; both a hit and a timeout are followed by a cooldown. */
    public boolean startParry(long now, long until) {
        if (parryActive(now) || parryCoolingDown(now)) return false;
        parryUntil = until;
        parryCooldownUntil = until + PARRY_COOLDOWN_TICKS;
        return true;
    }
    public boolean parryActive(long now) { return now < parryUntil; }
    public boolean parryPending() { return parryUntil > 0L; }
    public long parryUntil() { return parryUntil; }
    public long parryCooldownUntil() { return parryCooldownUntil; }
    public boolean parryCoolingDown(long now) { return now < parryCooldownUntil; }

    /** Ends once; late tick processing never extends the cooldown past timeout + 60. */
    public boolean finishParry(long now) {
        if (!parryPending()) return false;
        parryCooldownUntil = Math.min(now, parryUntil) + PARRY_COOLDOWN_TICKS;
        parryUntil = 0L;
        return true;
    }

    /**
     * A successful Parry opens one Sword Break follow-up against the attacker it just stopped, so
     * the follow-up can never be aimed at a bystander.
     */
    public void startSwordBreak(int targetId, long until) {
        swordBreakTargetId = targetId;
        swordBreakUntil = until;
        swordBreakUsed = false;
    }
    public boolean swordBreakActive(long now) { return !swordBreakUsed && now < swordBreakUntil; }
    public int swordBreakTargetId() { return swordBreakTargetId; }
    public long swordBreakUntil() { return swordBreakUntil; }
    public void useSwordBreak() { swordBreakUsed = true; }

    public void armGroundSlam() { groundSlamReady = true; }
    public boolean groundSlamReady() { return groundSlamReady; }
    public void groundSlamHit(int targetId, long now) {
        groundSlamHitTick = now;
        groundSlamFallImmune = true;
        groundSlamHitTargets.add(targetId);
    }
    public java.util.Set<Integer> groundSlamHitTargets() { return java.util.Set.copyOf(groundSlamHitTargets); }
    public boolean groundSlamFallImmune() { return groundSlamFallImmune; }
    public boolean groundSlamLandingActive(long now) {
        return groundSlamHitTick >= 0L && now >= groundSlamHitTick && now - groundSlamHitTick <= 4L;
    }
    public void clearGroundSlam() {
        groundSlamReady = groundSlamFallImmune = false;
        groundSlamHitTick = -1L;
        groundSlamHitTargets.clear();
    }

    public void armRisingCut(long until) { risingCutUntil = until; }
    public boolean risingCutActive(long now) { return now <= risingCutUntil; }
    public void clearRisingCut() { risingCutUntil = 0L; }

    public void startRisingCutCooldown(long until) { risingCutCooldownUntil = until; }
    public boolean risingCutCoolingDown(long now) { return now < risingCutCooldownUntil; }
    public void startRisingCutImmunity(long until) {
        risingCutImmuneUntil = until;
        risingCutAscending = true;
    }
    public boolean risingCutImmune(long now) { return now < risingCutImmuneUntil; }
    public void finishRisingCutAscent(long now) {
        if (risingCutAscending) {
            risingCutAscending = false;
            risingCutImmuneUntil = now + 30L;
        }
    }

    /**
     * A landed Rising Cut waives the fall damage of the climb it just started. The waiver covers
     * exactly one climb: it starts counting once the lift leaves the ground, and ends on the next
     * landing, so it can never reach an unrelated fall.
     */
    public void startRisingCutFallGrace(double blocks) {
        risingCutFallGrace = Math.max(0.0D, blocks);
        risingCutGraceAirborne = false;
    }
    public double risingCutFallGrace() { return risingCutFallGrace; }
    /** Records that the lift has actually left the ground, so a landing can end the waiver. */
    public void markRisingCutGraceAirborne() { risingCutGraceAirborne = true; }
    public boolean risingCutGraceAirborne() { return risingCutGraceAirborne; }
    public void clearRisingCutFallGrace() { risingCutFallGrace = 0.0D; risingCutGraceAirborne = false; }

    /** The upward slash trail plays only while the player is still climbing. */
    public void startRisingCutTrail(long now, long until) {
        risingCutTrailStart = now;
        risingCutTrailUntil = until;
    }
    public boolean risingCutTrailActive(long now) { return now <= risingCutTrailUntil; }
    public double risingCutTrailProgress(long now) {
        double span = Math.max(1L, risingCutTrailUntil - risingCutTrailStart);
        double elapsed = Math.max(0L, now - risingCutTrailStart);
        return Math.min(1.0D, elapsed / span);
    }
    /**
     * True on every other tick of the climb, counted from the trail's first tick, so the Rising Cut
     * sweeps its sword once every two ticks while the slash is playing.
     */
    public boolean risingCutSlashDue(long now) {
        return risingCutTrailActive(now) && (now - risingCutTrailStart) % RISING_CUT_SLASH_INTERVAL_TICKS == 0L;
    }

    public void startDash(int targetId, Vec3 start, long until, int level) {
        startDash(targetId, start, until, level, 0);
    }
    public void startDash(int targetId, Vec3 start, long until, int level, int continuationRemaining) {
        dashTargetId = targetId;
        dashStart = start;
        dashUntil = until;
        dashLevel = Math.max(1, level);
        dashHitTargets.clear();
        dashLastEffect = dashLastSound = Long.MIN_VALUE;
        dashLastTap = Long.MIN_VALUE;
        dashContinuationQueued = dashContinuationUsed = continuedDash = false;
        dashContinuationRemaining = Math.max(0, continuationRemaining);
    }
    public void markDashRelease(boolean continued) {
        continuedDash = continued;
    }
    public boolean continuedDash() { return continuedDash; }
    public boolean pollDashTrail(long now) {
        if (dashLastEffect == now) return false;
        dashLastEffect = now;
        return true;
    }
    public boolean pollDashSound(long now) {
        if (dashLastSound != Long.MIN_VALUE && now - dashLastSound < 2L) return false;
        dashLastSound = now;
        return true;
    }
    public boolean queueDashTap(long now) {
        if (!dashImmune(now) || dashContinuationUsed || dashContinuationRemaining <= 0) return false;
        if (dashLastTap == now) return false;
        if (dashLastTap != Long.MIN_VALUE && now > dashLastTap && now - dashLastTap <= 10L) {
            dashContinuationQueued = dashContinuationUsed = true;
            dashLastTap = Long.MIN_VALUE;
            return true;
        }
        dashLastTap = now;
        return false;
    }
    public boolean takeDashContinuation() {
        boolean queued = dashContinuationQueued;
        dashContinuationQueued = false;
        return queued;
    }
    public void clearDashContinuation() {
        dashLastTap = Long.MIN_VALUE;
        dashContinuationQueued = false;
        dashContinuationUsed = true;
        dashContinuationRemaining = 0;
    }
    public int dashTargetId() { return dashTargetId; }
    public int dashLevel() { return dashLevel; }
    public int dashContinuationRemaining() { return dashContinuationRemaining; }
    public Vec3 dashStart() { return dashStart; }
    public boolean dashActive(long now) { return dashTargetId >= 0 && now <= dashUntil; }
    public void finishDash() {
        if (dashAttempt != null) dashAttempt.close();
        dashAttempt = null;
        dashTargetId = -1;
        dashUntil = 0L;
        dashLevel = 0;
    }
    public boolean dashPending() { return dashTargetId >= 0; }
    public void watchDash(FatalStrike.Attempt attempt) { dashAttempt = attempt; }
    public void recordDashDamage(boolean hit) { if (dashAttempt != null) dashAttempt.hit(hit); }

    public void startDashCooldown(long until) { dashCooldownUntil = until; }
    public boolean dashCoolingDown(long now) { return now < dashCooldownUntil; }

    /** Records that this dash already struck the entity, so a swept path cannot hit it twice. */
    public boolean registerDashHit(int entityId) { return dashHitTargets.add(entityId); }

    /** Dash grants full damage immunity from the moment it is released, from any source. */
    public void startDashImmunity(long until) { dashImmuneUntil = until; }
    public long dashImmuneUntil() { return dashImmuneUntil; }
    public boolean dashImmune(long now) { return dashImmuneUntil > 0L && now <= dashImmuneUntil; }

    public void startSpin(long until, long cooldownUntil) {
        spinUntil = until;
        spinCooldownUntil = cooldownUntil;
        spinRounds = 1;
        superSpinTapCount = 0;
        superSpinLastTap = Long.MIN_VALUE;
        spinContinuationQueued = false;
    }
    public boolean spinActive(long now) { return spinUntil > 0L && now < spinUntil; }
    public boolean spinPending() { return spinUntil > 0L; }
    public boolean spinCoolingDown(long now) { return now < spinCooldownUntil; }
    public int spinRounds() { return spinRounds; }
    public boolean spinContinuationQueued() { return spinContinuationQueued; }
    public void addSpinRound(long now) {
        spinRounds++;
        spinUntil = now + SPIN_ROUND_TICKS;
        spinContinuationQueued = false;
        superSpinTapCount = 0;
        superSpinLastTap = Long.MIN_VALUE;
    }
    public void finishSpin() {
        spinUntil = 0L;
        spinContinuationQueued = false;
        superSpinTapCount = 0;
        superSpinLastTap = Long.MIN_VALUE;
    }
    public long spinUntil() { return spinUntil; }

    public boolean registerSuperSpinTap(long gameTime) {
        if (!spinActive(gameTime) || spinContinuationQueued) return false;
        if (superSpinLastTap == Long.MIN_VALUE || gameTime - superSpinLastTap > 10L) superSpinTapCount = 0;
        superSpinLastTap = gameTime;
        superSpinTapCount++;
        if (superSpinTapCount < 2) return false;
        superSpinTapCount = 0;
        spinContinuationQueued = true;
        return true;
    }

    public void startSwordBeamCooldown(long now) { swordBeamCooldownUntil = now + SWORD_BEAM_COOLDOWN_TICKS; }
    public boolean swordBeamCoolingDown(long now) { return now < swordBeamCooldownUntil; }

    public FlashAssault.State flashAssault() { return flashAssault; }

    /**
     * Consumes the one extra mid-air jump this airtime allows. Returns false once it is spent,
     * which keeps a single jump chain from being extended indefinitely; landing restores it.
     */
    public boolean useDoubleJump() {
        if (doubleJumpUsed) return false;
        doubleJumpUsed = true;
        return true;
    }
    public boolean doubleJumpUsed() { return doubleJumpUsed; }
    public void resetDoubleJump() { doubleJumpUsed = false; }

    public long focusUntil() { return focusUntil; }
    public long focusVersion() { return focusVersion; }
    public boolean focusActive(long now) { return focusUntil > 0 && now < focusUntil; }
    public boolean fatalStrikeCoolingDown(long now) { return now < fatalStrikeCooldownUntil; }
    public void startFocus(long now) { focusVersion++; focusUntil = now + 60L; }
    public boolean clearFocus() {
        boolean active = focusUntil > 0L;
        focusUntil = 0L;
        return active;
    }
    public void finishFocus(long now, int cooldown) {
        clearFocus();
        fatalStrikeCooldownUntil = now + cooldown;
        nextFocusRoll = fatalStrikeCooldownUntil;
    }

    public void endFocusOnTargetChange(long now) {
        if (focusActive(now)) finishFocus(now, 300);
        else clearFocus();
    }
    public void resetFocusRoll() { nextFocusRoll = -1L; }
    public boolean pollFocusRoll(long now) {
        if (nextFocusRoll < 0L) nextFocusRoll = now + 20L;
        if (now < nextFocusRoll) return false;
        nextFocusRoll = now + 20L;
        return true;
    }

    public void setTarget(int entityId) {
        if (entityId >= 0) iaiSlash.clear();
        if (targetId != entityId) {
            flashAssault.targetChanged();
            helmSplitter.clearOpening();
            clearFocus();
            resetFocusRoll();
        }
        targetId = entityId;
    }

    public void clearTarget() {
        setTarget(-1);
    }

    public void transferTarget(int entityId, long now) {
        if (!comboFinished && comboCount > 0 && comboTargetId == targetId) comboTargetId = entityId;
        endFocusOnTargetChange(now);
        setTarget(entityId);
    }

    public int nextDamageBonus() {
        return comboFinished ? 0 : comboCount;
    }

    public void recordHit(int entityId, float damage, int maximum, long deadline) {
        if (comboFinished || comboCount == 0 || comboTargetId != entityId) {
            comboCount = 0;
            comboDamage = 0.0F;
            comboTargetId = -1;
        }
        comboCount++;
        comboMaximum = maximum;
        comboDamage += Math.max(0.0F, damage);
        comboTargetId = entityId;
        comboDeadline = deadline;
        comboFinished = comboCount >= comboMaximum;
    }

    /**
     * Adds damage to the running combo without recording a new hit. Used by effects that ride along
     * with an already-counted hit, such as the True Master Sword's tenth-hit true damage, so the
     * displayed combo damage stays accurate without inflating the hit count.
     */
    public void addComboDamage(float damage) {
        if (comboCount <= 0 || comboFinished) return;
        comboDamage += Math.max(0.0F, damage);
    }

    public void finishCombo() {
        if (comboCount > 0) comboFinished = true;
        comboDeadline = 0L;
    }

    public void clearCombo() {
        comboTargetId = -1;
        comboCount = 0;
        comboMaximum = 0;
        comboDamage = 0.0F;
        comboDeadline = 0L;
        comboFinished = false;
    }

    public void reset() {
        targetId = -1;
        clearCombo();
        lastTargetIntentTick = Long.MIN_VALUE;
        clearAdvanced();
    }

    private void clearAdvanced() {
        clearCharge();
        dodgeUntil = dodgeCooldownUntil = parryUntil = parryCooldownUntil = swordBreakUntil = risingCutUntil = dashUntil = dashImmuneUntil = spinUntil = spinCooldownUntil = 0L;
        dashCooldownUntil = 0L;
        clearDashContinuation();
        continuedDash = false;
        dashLastEffect = dashLastSound = Long.MIN_VALUE;
        swordBeamCooldownUntil = 0L;
        dashHitTargets.clear();
        risingCutCooldownUntil = risingCutTrailStart = risingCutTrailUntil = risingCutImmuneUntil = 0L;
        risingCutAscending = false;
        clearRisingCutFallGrace();
        iaiSlash.reset();
        flashAssault.reset();
        helmSplitter.reset();
        clearFocus();
        focusVersion++;
        fatalStrikeCooldownUntil = 0L;
        resetFocusRoll();
        dashAttempt = null;
        dashTargetId = swordBreakTargetId = -1;
        dashLevel = 0;
        dodgeLastTapTick = Long.MIN_VALUE;
        dodgeLastTapDirection = Vec3.ZERO;
        spinRounds = 0;
        superSpinTapCount = 0;
        superSpinLastTap = Long.MIN_VALUE;
        spinContinuationQueued = false;
        swordBreakUsed = false;
        doubleJumpUsed = false;
        clearGroundSlam();
    }

    public boolean comboExpired(long gameTime) {
        return comboCount > 0 && !comboFinished && gameTime > comboDeadline;
    }

    public boolean tryAcceptTargetIntent(long gameTime, int intentsPerSecond) {
        int interval = Math.max(1, 20 / Math.max(1, intentsPerSecond));
        if (lastTargetIntentTick != Long.MIN_VALUE && gameTime - lastTargetIntentTick < interval) return false;
        lastTargetIntentTick = gameTime;
        return true;
    }
}
