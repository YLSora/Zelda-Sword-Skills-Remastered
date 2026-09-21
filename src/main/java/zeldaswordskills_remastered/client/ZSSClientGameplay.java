package zeldaswordskills_remastered.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.SongIntentMessage;
import zeldaswordskills_remastered.network.TargetIntentMessage;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.network.ParryStateMessage;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.ItemUsePriority;
import zeldaswordskills_remastered.combat.IaiSlash;
import zeldaswordskills_remastered.combat.PlayerCombatState;
import zeldaswordskills_remastered.combat.TargetingService;
import zeldaswordskills_remastered.client.screen.InstrumentHudScreen;
import zeldaswordskills_remastered.item.InstrumentItem;
import zeldaswordskills_remastered.item.BombBagItem;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.song.ScarecrowStructure;
import zeldaswordskills_remastered.entity.npc.QuestNpc;
import zeldaswordskills_remastered.quest.QuestService;

import java.util.Optional;

/**
 * Owns every client-side skill window. All of them are timed against the local player's own
 * tick counter rather than the world's game time, so a death and respawn can never leave a
 * window pointing at a clock the new player entity does not share.
 */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class ZSSClientGameplay {
    /**
     * A tick far enough in the past that no window treats it as recent. It is deliberately not
     * {@code Integer.MIN_VALUE}: every window test subtracts the stored tick from the current one,
     * and a value that far out would overflow that subtraction and silently read as "just now".
     */
    private static final int NEVER_TICK = -1_000_000;
    private static boolean attackWasDown;
    private static boolean leftWasDown;
    private static boolean rightWasDown;
    private static boolean jumpWasDown;
    private static int spinChargeStarted = -1;
    private static int spinActiveUntil;
    private static int spinVisualStart = -1;
    private static float spinVisualYaw;
    private static boolean spinClockwise;
    private static int spinChargeToneTick = -1;
    private static int spinCooldownUntil;
    private static int spinSequenceIndex = -1;
    private static int spinSequenceDirection;
    private static int spinSequenceLength;
    private static int spinSequenceStartTick = NEVER_TICK;
    private static int spinSequenceLastTick = NEVER_TICK;
    private static int spinSequenceReadyUntil = -1;
    private static int firstForwardTap = NEVER_TICK;
    private static int dashReadyUntil;
    private static int dashCooldownUntil;
    private static int risingCutUntil;
    private static int risingCutCooldownUntil;
    private static boolean sneakWasDown;
    /** Absolute tick the client's own Parry window ends at; zero when none is open. */
    private static int parryWindowUntil;
    /** A guard was requested and has not ended or been released, including while its ACK is in flight. */
    private static boolean parryGuardRequested;
    /** Next permitted guard, including the guard duration while an activation is in flight. */
    private static int parryCooldownUntil;
    /**
     * Ticks left of the Sword Break follow-up the server confirmed, and the attacker that parry
     * recorded. The window only exists once a Parry has actually landed, so a missed parry never
     * swallows the attack key.
     */
    private static int swordBreakReadyUntil;
    private static int helmReadyUntil;
    private static double helmParryY;
    private static boolean helmJumped;
    private static boolean helmAttackQueued;
    /** Client mirror of the server rule: one extra mid-air jump, restored on landing. */
    private static boolean doubleJumpUsed;
    /**
     * Ticks spent off the ground. The vanilla jump is applied by the client's own physics before
     * this handler runs, so a jump pressed from the ground already reports {@code onGround() == false}
     * on its own tick; the previous tick's state is what separates a ground jump from a mid-air one.
     */
    private static int airborneTicks;
    /** Grounded at the instant the sneak key went down; the jump and attack are not re-checked. */
    private static boolean sneakPressedOnGround;
    /**
     * The local player entity every window above belongs to. Death, respawn, a dimension change and
     * a new world each build a fresh local player whose tick counter restarts at zero, so the
     * windows must be dropped as soon as the entity they were measured against is gone.
     */
    private static net.minecraft.client.player.LocalPlayer boundPlayer;
    /** Client mirror of the server Rising Cut window, in ticks, covering one full crouch-jump. */
    private static final int RISING_CUT_WINDOW_TICKS = 20;
    private static final int RISING_CUT_COOLDOWN_TICKS = 60;
    /** Ticks the double-tap-forward Dash stays armed while waiting for the attack key. */
    private static final int DASH_WINDOW_TICKS = 6;

    private ZSSClientGameplay() {
    }

    /** The local player's own clock. Every window below is expressed in this counter. */
    private static int clock(net.minecraft.world.entity.player.Player player) {
        return player.tickCount;
    }

    /**
     * Drops every window the moment the local player entity or the world is replaced. Without this
     * the windows keep pointing at the previous entity's clock: after a respawn the new counter
     * starts at zero while a stale deadline still sits far in the future, which closes every skill
     * that compares against it and leaves the spin window driving the camera.
     */
    private static void refreshPlayerBinding(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            ZSSClientCombatState.clear();
            if (boundPlayer == null) return;
            boundPlayer = null;
        } else if (minecraft.player == boundPlayer) {
            return;
        } else {
            boundPlayer = minecraft.player;
        }
        resetWindows();
    }

    /** Returns every transient skill window to its never-set state. */
    private static void resetWindows() {
        ZSSClientCombatState.clear();
        attackWasDown = false;
        leftWasDown = false;
        rightWasDown = false;
        jumpWasDown = false;
        spinChargeStarted = -1;
        spinActiveUntil = 0;
        spinVisualStart = -1;
        spinVisualYaw = 0.0F;
        spinClockwise = false;
        spinChargeToneTick = -1;
        spinCooldownUntil = 0;
        spinSequenceIndex = -1;
        spinSequenceDirection = 0;
        spinSequenceLength = 0;
        spinSequenceStartTick = NEVER_TICK;
        spinSequenceLastTick = NEVER_TICK;
        spinSequenceReadyUntil = -1;
        firstForwardTap = NEVER_TICK;
        ZSSClientFlashAssault.clear();
        dashReadyUntil = 0;
        dashCooldownUntil = 0;
        risingCutUntil = 0;
        risingCutCooldownUntil = 0;
        sneakWasDown = false;
        sneakPressedOnGround = false;
        parryWindowUntil = 0;
        parryGuardRequested = false;
        parryCooldownUntil = 0;
        swordBreakReadyUntil = 0;
        helmReadyUntil = 0;
        helmJumped = false;
        helmAttackQueued = false;
        doubleJumpUsed = false;
        airborneTicks = 0;
    }

    public static void disableSkill(ResourceLocation skill) {
        boolean basic = skill.equals(ZSSContentIds.SWORD_BASIC);
        boolean parry = skill.equals(ZSSContentIds.PARRY);
        if (basic || skill.equals(ZSSContentIds.DODGE)) ZSSClientDodge.clear();
        if (basic || skill.equals(ZSSContentIds.DODGE) || skill.equals(ZSSContentIds.FLASH_ASSAULT)) ZSSClientFlashAssault.clear();
        if (basic || parry) {
            parryWindowUntil = 0;
            parryGuardRequested = false;
        }
        if (basic || parry || skill.equals(ZSSContentIds.SWORD_BREAK)) swordBreakReadyUntil = 0;
        if (basic || parry || skill.equals(ZSSContentIds.HELM_SPLITTER)) {
            helmReadyUntil = 0;
            helmJumped = helmAttackQueued = false;
        }
        if (basic || skill.equals(ZSSContentIds.RISING_CUT)) risingCutUntil = 0;
        if (basic || skill.equals(ZSSContentIds.DASH)) {
            dashReadyUntil = 0;
            firstForwardTap = NEVER_TICK;
        }
        if (skill.equals(ZSSContentIds.SPIN_ATTACK) || skill.equals(ZSSContentIds.SUPER_SPIN_ATTACK)) {
            if (skill.equals(ZSSContentIds.SPIN_ATTACK)) {
                spinChargeStarted = spinChargeToneTick = -1;
                spinSequenceReadyUntil = -1;
                spinSequenceIndex = -1;
                spinSequenceLength = 0;
            }
            spinActiveUntil = 0;
            var player = Minecraft.getInstance().player;
            if (spinVisualStart >= 0 && player != null) player.setYRot(spinVisualYaw);
            spinVisualStart = -1;
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        if (minecraft.player == null || minecraft.level == null) return;
        if (!minecraft.options.keyUse.isDown() || minecraft.screen != null) cancelParryGuard(minecraft);
        if (minecraft.screen != null) {
            // Keep the mid-air bookkeeping current while a screen is open, so landing behind the
            // inventory cannot leave a stale airborne state behind for the next jump press.
            updateAirborne(minecraft.player);
            return;
        }
        while (ZSSKeyMappings.SKILL_BOOK.consumeClick()) minecraft.setScreen(new zeldaswordskills_remastered.client.screen.SkillBookScreen());
        while (ZSSKeyMappings.TARGET.consumeClick()) {
            ZSSNetwork.sendTargetIntent(ZSSClientCombatState.hasTarget() ? TargetIntentMessage.Action.NEXT : TargetIntentMessage.Action.ACQUIRE);
        }
        while (ZSSKeyMappings.CLEAR_TARGET.consumeClick()) {
            ZSSNetwork.sendTargetIntent(TargetIntentMessage.Action.CLEAR);
        }
        processSkillKeys(minecraft);
    }

    @SubscribeEvent
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        if (!event.isAttack() || minecraft.player == null || minecraft.screen != null) return;
        if (ZSSClientFlashAssault.attack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (ZSSClientFlashAssault.busy()) {
            if (ZSSClientCombatState.hasTarget()) {
                sendPlainLockedAttack(minecraft.player);
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            return;
        }
        if (minecraft.player.getMainHandItem().getItem() instanceof BombBagItem) {
            ZSSNetwork.sendBombBagCycleIntent();
            event.setCanceled(true);
            event.setSwingHand(true);
            return;
        }
        if (handleAttack(minecraft)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void emptyAttack(PlayerInteractEvent.LeftClickEmpty event) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        if (event.getEntity() != minecraft.player || minecraft.screen != null
                || ZSSClientCombatState.iaiTargetId() < 0 || !TargetingService.isHoldingWeapon(minecraft.player)) return;
        // Empty swings have no vanilla server attack packet. Send only a cancellation intent.
        send(ZSSContentIds.MORTAL_DRAW, SkillIntentMessage.Action.CANCEL, Optional.empty());
        ZSSClientCombatState.clearIaiTarget();
    }

    @SubscribeEvent
    public static void keyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        if (event.getAction() == GLFW.GLFW_RELEASE
                && minecraft.options.keyUse.matches(event.getKey(), event.getScanCode())) {
            cancelParryGuard(minecraft);
        }
        if (event.getAction() != GLFW.GLFW_PRESS || minecraft.player == null || minecraft.screen != null) return;
        ZSSClientFlashAssault.lockHotbar();
        if (minecraft.options.keyAttack.matches(event.getKey(), event.getScanCode())) {
            ZSSClientFlashAssault.pressAttack();
            pressContinuousDash(minecraft);
        }
        if (minecraft.options.keyUp.matches(event.getKey(), event.getScanCode())) forwardTap(minecraft.player);
        if (ZSSClientFlashAssault.busy()) return;
        int key = switch (event.getKey()) {
            case GLFW.GLFW_KEY_W -> 0;
            case GLFW.GLFW_KEY_A -> 1;
            case GLFW.GLFW_KEY_S -> 2;
            case GLFW.GLFW_KEY_D -> 3;
            default -> -1;
        };
        if (key >= 0) recordSpinSequenceKey(key, clock(minecraft.player));
        // Capture every press, including press/release pairs between client ticks.
        // GLFW_REPEAT is excluded above; holding a key cannot supply a second tap.
        if (event.getKey() == GLFW.GLFW_KEY_A) sendDodgeTap(minecraft.player, false);
        if (event.getKey() == GLFW.GLFW_KEY_D) sendDodgeTap(minecraft.player, true);
    }

    @SubscribeEvent
    public static void releaseParryMouse(InputEvent.MouseButton.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        if (event.getAction() == GLFW.GLFW_RELEASE && minecraft.options.keyUse.matchesMouse(event.getButton())) {
            cancelParryGuard(minecraft);
        }
        if (event.getAction() == GLFW.GLFW_PRESS && minecraft.player != null && minecraft.screen == null
                && minecraft.options.keyUp.matchesMouse(event.getButton())) forwardTap(minecraft.player);
        if (event.getAction() == GLFW.GLFW_PRESS && minecraft.player != null && minecraft.screen == null
                && minecraft.options.keyAttack.matchesMouse(event.getButton())) {
            ZSSClientFlashAssault.pressAttack();
            pressContinuousDash(minecraft);
        }
    }

    private static void cancelParryGuard(Minecraft minecraft) {
        if (!parryGuardRequested || minecraft.player == null) return;
        parryGuardRequested = false;
        parryWindowUntil = 0;
        parryCooldownUntil = clock(minecraft.player) + PlayerCombatState.PARRY_COOLDOWN_TICKS;
        send(ZSSContentIds.PARRY, SkillIntentMessage.Action.CANCEL, Optional.empty());
    }

    @SubscribeEvent
    public static void useInstrument(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        ItemStack main = minecraft.player.getMainHandItem();
        ItemStack offhand = minecraft.player.getOffhandItem();
        if (!(main.getItem() instanceof InstrumentItem) && !(offhand.getItem() instanceof InstrumentItem)) return;
        if (minecraft.hitResult instanceof EntityHitResult hit) {
            if (hit.getEntity() instanceof QuestNpc) return;
            if (hit.getEntity() instanceof Villager villager
                    && (QuestService.isNamedSongTeacher(villager, main)
                    || QuestService.isNamedSongTeacher(villager, offhand))) return;
        }
        if (minecraft.hitResult instanceof BlockHitResult hit
                && minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof zeldaswordskills_remastered.block.WarpStoneBlock) return;
        boolean scarecrow = minecraft.hitResult instanceof BlockHitResult hit
                && ScarecrowStructure.isScarecrowNear(minecraft.level, hit.getBlockPos());
        event.setCanceled(true);
        event.setSwingHand(false);
        ZSSClientSongState.beginLocal(scarecrow);
        minecraft.setScreen(new InstrumentHudScreen());
        ZSSNetwork.sendSongIntent(new SongIntentMessage(scarecrow
                ? SongIntentMessage.Action.BEGIN_SCARECROW : SongIntentMessage.Action.BEGIN));
    }

    @SubscribeEvent
    public static void lockCamera(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        int tick = clock(minecraft.player);
        // A spin window only drives the camera while it also owns a recorded start yaw; a window
        // left over from a replaced player must never rotate the view on its own.
        if (spinActiveUntil > tick && spinVisualStart >= 0) {
            float progress = Math.max(0, tick - spinVisualStart + event.renderTickTime);
            minecraft.player.setYRot(spinVisualYaw + progress * (spinClockwise ? 45.0F : -45.0F));
            minecraft.player.yHeadRot = minecraft.player.getYRot();
            return;
        } else if (spinVisualStart >= 0) {
            minecraft.player.setYRot(spinVisualYaw);
            spinVisualStart = -1;
        }
        if (!ZSSClientCombatState.hasTarget()) return;
        Entity target = minecraft.level.getEntity(ZSSClientCombatState.targetId());
        if (target == null || !target.isAlive()) return;
        Vec3 delta = target.getBoundingBox().getCenter().subtract(minecraft.player.getEyePosition(event.renderTickTime));
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float desiredYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float desiredPitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        minecraft.player.setYRot(Mth.rotLerp(0.4F, minecraft.player.getYRot(), desiredYaw));
        minecraft.player.setXRot(Mth.lerp(0.4F, minecraft.player.getXRot(), Mth.clamp(desiredPitch, -90.0F, 90.0F)));
        minecraft.player.yHeadRot = minecraft.player.getYRot();
    }

    private static void processSkillKeys(Minecraft minecraft) {
        var player = minecraft.player;
        if (ZSSClientFlashAssault.busy()) {
            attackWasDown = minecraft.options.keyAttack.isDown();
            jumpWasDown = minecraft.options.keyJump.isDown();
            updateAirborne(player);
            return;
        }
        boolean attack = minecraft.options.keyAttack.isDown();
        boolean left = minecraft.options.keyLeft.isDown();
        boolean right = minecraft.options.keyRight.isDown();
        boolean jump = minecraft.options.keyJump.isDown();
        boolean use = minecraft.options.keyUse.isDown();
        boolean spinInput = attack;
        boolean sneak = player.isShiftKeyDown();
        boolean itemUsePriority = ItemUsePriority.takesPriority(player);
        if (itemUsePriority) cancelParryGuard(minecraft);
        int tick = clock(player);
        // The ground requirement is evaluated only at the moment the sneak key is pressed;
        // the following jump and attack are deliberately not ground-checked.
        if (sneak && !sneakWasDown) sneakPressedOnGround = player.onGround();
        else if (!sneak) sneakPressedOnGround = false;

        if (tick < helmReadyUntil && !player.onGround() && player.getY() > helmParryY + 0.05D) helmJumped = true;
        if (helmAttackQueued) {
            if (tick >= helmReadyUntil || !TargetingService.isHoldingSword(player)) helmAttackQueued = false;
            else if (helmJumped && !player.onGround()) {
                clearFlashConflictingInputs();
                send(ZSSContentIds.HELM_SPLITTER, SkillIntentMessage.Action.ATTACK, Optional.empty());
            }
        }

        if (spinChargeStarted >= 0) {
            int level = skillLevel(ZSSContentIds.SPIN_ATTACK);
            int elapsed = tick - spinChargeStarted;
            int chargeElapsed = elapsed - 6;
            int chargeRequired = Math.max(1, 20 - level);
            if (chargeElapsed > 0 && chargeElapsed < chargeRequired && chargeElapsed % 2 == 0
                    && chargeElapsed != spinChargeToneTick) {
                float pitch = 0.5F + 1.5F * chargeElapsed / (float) chargeRequired;
                player.playSound(SoundEvents.NOTE_BLOCK_PLING.get(), 0.45F, pitch);
                spinChargeToneTick = chargeElapsed;
            }
            if (left) spinClockwise = false;
            if (right) spinClockwise = true;
            if (elapsed > 6) {
                var motion = player.getDeltaMovement();
                double maxSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 0.3D;
                double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
                if (horizontal > maxSpeed && horizontal > 1.0E-6D) {
                    double scale = maxSpeed / horizontal;
                    player.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
                }
            }
            if (!spinInput) {
                if (elapsed >= 6 + chargeRequired) {
                    send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.RELEASE,
                            Optional.of(sideDirection(player, spinClockwise)));
                    spinActiveUntil = tick + 8;
                    spinVisualStart = tick;
                    spinVisualYaw = player.getYRot();
                    spinCooldownUntil = tick + 20;
                } else {
                    send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.CANCEL, Optional.empty());
                }
                spinChargeStarted = -1;
                spinChargeToneTick = -1;
            } else if (elapsed >= 6 + chargeRequired) {
                // Keep charging until the attack key is released.
            }
        }
        if (ZSSClientCombatState.iaiTargetId() < 0 && attack && TargetingService.isHoldingWeapon(player) && spinSequenceReadyUntil >= tick && spinChargeStarted < 0
                && spinActiveUntil <= tick && learned(ZSSContentIds.SPIN_ATTACK) && tick >= spinCooldownUntil) {
            send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.ATTACK, Optional.empty());
            spinActiveUntil = tick + 8;
            spinVisualStart = tick;
            spinVisualYaw = player.getYRot();
            spinClockwise = spinSequenceDirection < 0;
            spinSequenceReadyUntil = -1;
            spinCooldownUntil = tick + 20;
        }

        // Holding use can begin the next guard only after the previous guard AND cooldown end.
        // No animation is predicted: only a server-confirmed guard may raise the weapon.
        if (!itemUsePriority && ZSSClientCombatState.hasTarget() && learned(ZSSContentIds.PARRY)
                && TargetingService.isHoldingSword(player) && player.isAlive() && !player.isSpectator()
                && tick >= parryCooldownUntil && use) {
            parryGuardRequested = true;
            send(ZSSContentIds.PARRY, SkillIntentMessage.Action.BEGIN, Optional.empty());
            parryCooldownUntil = tick + (int) AdvancedSwordSkills.parryWindow(skillLevel(ZSSContentIds.PARRY))
                    + PlayerCombatState.PARRY_COOLDOWN_TICKS;
        }
        if (jump && !jumpWasDown) {
            // The window is armed by the crouch-jump itself; a second press while it is still
            // open is a mid-air jump, not another activation, so it falls through to Double Jump.
            if (sneak && sneakPressedOnGround && airborneTicks == 0 && risingCutUntil < tick
                    && ZSSClientCombatState.hasTarget() && tick >= risingCutCooldownUntil) {
                send(ZSSContentIds.RISING_CUT, SkillIntentMessage.Action.BEGIN, Optional.empty());
                // Covers the whole crouch-jump: the attack may land at any point before landing.
                risingCutUntil = tick + RISING_CUT_WINDOW_TICKS;
            }
            else if (airborneTicks > 0 && !doubleJumpUsed && learned(ZSSContentIds.DOUBLE_JUMP)
                    && !player.isFallFlying()) {
                // A jump pressed while already airborne is the extra Double Jump; the crouch-jump
                // above still reports zero airborne ticks, so the two never compete for one press.
                // It is refused while gliding for the same reason the server refuses it there:
                // jumpFromGround would overwrite the elytra's own vertical motion.
                send(ZSSContentIds.DOUBLE_JUMP, SkillIntentMessage.Action.BEGIN, Optional.empty());
                doubleJumpUsed = true;
            }
            // Item actions own use; Parry may only claim it when both hands have no use action.
        }
        attackWasDown = attack;
        leftWasDown = left;
        rightWasDown = right;
        jumpWasDown = jump;
        sneakWasDown = sneak;
        updateAirborne(player);
    }

    private static void forwardTap(net.minecraft.world.entity.player.Player player) {
        if (ZSSClientFlashAssault.forwardTap()) {
            firstForwardTap = NEVER_TICK;
            dashReadyUntil = 0;
            return;
        }
        int tick = clock(player);
        if (tick - firstForwardTap <= 6) {
            if (ZSSClientCombatState.hasTarget()) dashReadyUntil = tick + DASH_WINDOW_TICKS;
            firstForwardTap = NEVER_TICK;
        } else firstForwardTap = tick;
    }

    public static void clearFlashConflictingInputs() {
        spinChargeStarted = spinChargeToneTick = -1;
        spinSequenceReadyUntil = -1;
        dashReadyUntil = swordBreakReadyUntil = helmReadyUntil = risingCutUntil = 0;
        helmJumped = false;
        helmAttackQueued = false;
        firstForwardTap = NEVER_TICK;
    }

    /** Starts, rejected requests, damage and timeouts all replace the same client snapshot. */
    public static void applyParryState(ParryStateMessage state) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        if (minecraft.player == null) return;
        if (state.guardTicks() > 0) {
            // A BEGIN confirmation can arrive after release. It must not restore the stance or
            // replace the predicted release cooldown; the following CANCEL ACK reconciles it.
            if (!parryGuardRequested) return;
            if (!minecraft.options.keyUse.isDown() || minecraft.screen != null
                    || ItemUsePriority.takesPriority(minecraft.player)) {
                cancelParryGuard(minecraft);
                return;
            }
        } else {
            parryGuardRequested = false;
        }
        int tick = clock(minecraft.player);
        if (state.helmTicks() > 0 && helmReadyUntil <= tick) {
            helmParryY = minecraft.player.getY();
            helmJumped = false;
        }
        helmReadyUntil = state.helmTicks() > 0 ? tick + state.helmTicks() : 0;
        swordBreakReadyUntil = state.followUpTicks() > 0 ? tick + state.followUpTicks() : 0;
        parryWindowUntil = state.guardTicks() > 0 ? tick + state.guardTicks() : 0;
        parryCooldownUntil = tick + state.cooldownTicks();
    }

    /**
     * Whether the local player is holding the Parry guard up right now, which is what the guard
     * animation is drawn for. The guard is only effective while a target is locked, so losing the
     * lock drops the stance at once rather than leaving the pose up over an empty window.
     *
     * <p>Only the local player is known: another player's guard is server state the client is never
     * told about, so their third-person pose stays vanilla.
     */
    public static boolean isParryGuarding(net.minecraft.world.entity.player.Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        return player != null && player == minecraft.player && ZSSClientCombatState.hasTarget()
                && parryGuardRequested && minecraft.options.keyUse.isDown() && minecraft.screen == null
                && player.isAlive() && !player.isSpectator() && !ItemUsePriority.takesPriority(player)
                && TargetingService.isHoldingSword(player)
                && learned(ZSSContentIds.PARRY) && parryWindowUntil > clock(player);
    }

    /** Landing restores the extra jump; every airborne tick counts towards being mid-air. */
    private static void updateAirborne(net.minecraft.world.entity.player.Player player) {
        if (player.onGround()) {
            airborneTicks = 0;
            doubleJumpUsed = false;
        } else {
            airborneTicks++;
        }
    }

    private static void sendDodgeTap(net.minecraft.world.entity.player.Player player, boolean right) {
        if (spinChargeStarted >= 0 || !learned(ZSSContentIds.DODGE)) return;
        send(ZSSContentIds.DODGE, SkillIntentMessage.Action.BEGIN,
                Optional.of(sideDirection(player, right)));
    }

    private static boolean continuousDashInput(Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null && !ZSSClientFlashAssault.busy()
                && ZSSClientCombatState.dashImmune(minecraft.level.getGameTime())
                && ZSSClientCombatState.hasTarget() && learned(ZSSContentIds.DASH)
                && learned(ZSSContentIds.CONTINUOUS_FLASH) && TargetingService.isHoldingWeapon(minecraft.player);
    }

    private static void pressContinuousDash(Minecraft minecraft) {
        if (continuousDashInput(minecraft)) {
            clearFlashConflictingInputs();
            send(ZSSContentIds.CONTINUOUS_FLASH, SkillIntentMessage.Action.ATTACK, Optional.empty());
        }
    }

    private static boolean handleAttack(Minecraft minecraft) {
        var player = minecraft.player;
        int tick = clock(player);
        if (ZSSClientCombatState.iaiTargetId() >= 0 && !ZSSClientCombatState.hasTarget()) {
            if (spinChargeStarted >= 0) send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.CANCEL, Optional.empty());
            clearFlashConflictingInputs();
            // Vanilla supplies the entity actually under the crosshair, including normal misses.
            return false;
        }
        if (continuousDashInput(minecraft)) {
            clearFlashConflictingInputs();
            return true;
        }
        // Flash Assault handles its confirmed follow-up before reaching this method.
        // Non-weapons must not be swallowed by weapon charges or gestures.
        if (!TargetingService.isHoldingWeapon(player)) {
            if (spinChargeStarted >= 0) send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.CANCEL, Optional.empty());
            clearFlashConflictingInputs();
            if (!ZSSClientCombatState.hasTarget()) return false;
            return sendPlainLockedAttack(player);
        }
        if (spinActiveUntil > tick && learned(ZSSContentIds.SUPER_SPIN_ATTACK)) {
            // Taps only request the next round; its duration and cost belong to the server.
            send(ZSSContentIds.SUPER_SPIN_ATTACK, SkillIntentMessage.Action.ATTACK, Optional.empty());
            return true;
        }
        if (TargetingService.isHoldingSword(player) && tick < helmReadyUntil && learned(ZSSContentIds.HELM_SPLITTER)
                && (minecraft.options.keyJump.isDown() || !player.onGround()
                && (helmJumped || player.getY() > helmParryY + 0.05D))) {
            if (spinChargeStarted >= 0) send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.CANCEL, Optional.empty());
            // Simultaneous jump/attack can precede vanilla's jump and movement packet this tick.
            // Own the click now, then send after vanilla has actually moved the player upward.
            if (player.onGround() || !helmJumped && player.getY() <= helmParryY + 0.05D) {
                spinChargeStarted = spinChargeToneTick = -1;
                spinSequenceReadyUntil = -1;
                helmAttackQueued = true;
                return true;
            }
            clearFlashConflictingInputs();
            send(ZSSContentIds.HELM_SPLITTER, SkillIntentMessage.Action.ATTACK, Optional.empty());
            return true;
        }
        // A confirmed Parry opens a short follow-up, so the attack key releases Sword Break against
        // the enemy that parry recorded. This confirmed follow-up owns the next attack press;
        // a zero deadline is the "no follow-up" sentinel, so it never matches on its own.
        // Always consume this press, including misses and a lost/changed lock. The server alone
        // decides whether the recorded enemy is still a valid target; no ordinary hit accompanies it.
        if (TargetingService.isHoldingSword(player) && swordBreakReadyUntil > 0 && tick < swordBreakReadyUntil
                && learned(ZSSContentIds.SWORD_BREAK)) {
            send(ZSSContentIds.SWORD_BREAK, SkillIntentMessage.Action.ATTACK, Optional.empty());
            swordBreakReadyUntil = 0;
            helmReadyUntil = 0;
            // Discard prior charges so releasing this click cannot also release another attack.
            if (spinChargeStarted >= 0) send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.CANCEL, Optional.empty());
            spinChargeStarted = spinChargeToneTick = -1;
            spinSequenceReadyUntil = -1;
            return true;
        }
        // Rising Cut and Spin Attack are told apart by the key combination, not by check order:
        // sneak + jump + attack is Rising Cut, while attack held on its own (or the WASD
        // sequence) is Spin Attack. The sneak gate keeps the two patterns mutually exclusive.
        boolean risingCutPattern = player.isShiftKeyDown() && risingCutUntil >= tick
                && ZSSClientCombatState.hasTarget();
        if (risingCutPattern && learned(ZSSContentIds.RISING_CUT)) {
            send(ZSSContentIds.RISING_CUT, SkillIntentMessage.Action.ATTACK, Optional.empty());
            risingCutUntil = 0;
            // Mirror the server cooldown so the window cannot be re-armed while it runs.
            risingCutCooldownUntil = tick + RISING_CUT_COOLDOWN_TICKS;
            return true;
        }
        if (spinSequenceReadyUntil >= tick && learned(ZSSContentIds.SPIN_ATTACK)
                && tick >= spinCooldownUntil) {
            send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.ATTACK, Optional.empty());
            spinActiveUntil = tick + 8;
            spinVisualStart = tick;
            spinVisualYaw = player.getYRot();
            spinClockwise = spinSequenceDirection < 0;
            spinSequenceReadyUntil = -1;
            spinCooldownUntil = tick + 20;
            return true;
        }
        if (!ZSSClientCombatState.hasTarget()) {
            beginSpinCharge(tick);
            return false;
        }
        // Outside a confirmed Flash Assault window, the forward gesture belongs to Dash.
        if (player.onGround() && dashReadyUntil >= tick && tick >= dashCooldownUntil && learned(ZSSContentIds.DASH)) {
            int dashLevel = skillLevel(ZSSContentIds.DASH);
            send(ZSSContentIds.DASH, SkillIntentMessage.Action.BEGIN, Optional.empty());
            dashReadyUntil = 0;
            // Mirror the server cooldown so the gesture cannot be re-armed while it runs.
            dashCooldownUntil = tick + 100 - 10 * dashLevel;
            return true;
        }
        // Descending after either second jump uses the actual left-click target. The server
        // confirms the jump and hit; Spin Attack may not delay this click.
        if (ZSSClientCombatState.groundSlamReady() && learned(ZSSContentIds.LEAPING_BLOW) && !player.onGround()
                && player.getDeltaMovement().y < 0.0D && TargetingService.isHoldingWeapon(player)) {
            if (spinChargeStarted >= 0) send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.CANCEL, Optional.empty());
            spinChargeStarted = spinChargeToneTick = -1;
            return false;
        }
        // A focused jump-critical press must reach the server before landing, not wait in a charge.
        if (ZSSClientCombatState.focusActive(minecraft.level.getGameTime())
                && zeldaswordskills_remastered.combat.FatalStrike.isJumpCritical(player)) {
            return sendPlainLockedAttack(player);
        }
        // Crouch + attack must reach Sword Beam before the generic attack-hold charge.
        if (player.isShiftKeyDown() && player.onGround() && learned(ZSSContentIds.SWORD_BEAM)
                && TargetingService.isHoldingSword(player)
                && (player.isCreative() || player.getHealth() >= player.getMaxHealth())) {
            send(ZSSContentIds.SWORD_BEAM, SkillIntentMessage.Action.ATTACK, Optional.empty());
            return true;
        }
        if (spinChargeStarted >= 0) return true;
        // The press attacks immediately; holding it may also charge a later Spin Attack.
        // Send ATTACK before BEGIN because the basic attack clears the server's prior charge.
        sendPlainLockedAttack(player);
        beginSpinCharge(tick);
        return true;
    }

    private static void beginSpinCharge(int tick) {
        if (spinChargeStarted < 0 && learned(ZSSContentIds.SPIN_ATTACK) && tick >= spinCooldownUntil) {
            spinChargeStarted = tick;
            spinChargeToneTick = -1;
            send(ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.BEGIN, Optional.empty());
        }
    }

    /**
     * Sends one plain locked basic attack, paced by the main-hand weapon's own attack speed.
     *
     * <p>Every branch of {@code handleAttack} that reaches a plain hit cancels vanilla's attack
     * input, so the local player's attack-strength ticker is never reset by vanilla and its
     * recovery scale would stay pinned at full. Re-arming it here - exactly the way vanilla's own
     * {@code Player#attack} does after a landed blow - makes the held weapon's {@code ATTACK_SPEED}
     * attribute the thing that paces the next ordinary hit. Sword skills keep their own windows and
     * are unaffected. The server independently refuses a hit whose recovery has not elapsed, so
     * this mirror only stops the client from asking for a cadence the weapon cannot deliver.
     *
     * @return true, because this press has been claimed as a basic attack either way
     */
    private static boolean sendPlainLockedAttack(net.minecraft.world.entity.player.Player player) {
        if (player.getAttackStrengthScale(0.5F) < zeldaswordskills_remastered.combat.BasicSwordSkill.LOCKED_ATTACK_RECOVERY) {
            // The weapon has not swung back yet; swallow the press rather than queue a second hit.
            return true;
        }
        player.resetAttackStrengthTicker();
        send(ZSSContentIds.SWORD_BASIC, SkillIntentMessage.Action.ATTACK, Optional.empty());
        // Predict only the animation. This overload does not send a vanilla swing packet;
        // the server validates the attack and broadcasts its swing to the other players.
        player.swing(InteractionHand.MAIN_HAND, false);
        return true;
    }

    public static void applySpinState(long until) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshPlayerBinding(minecraft);
        if (minecraft.player == null || minecraft.level == null) return;
        int remaining = (int) Math.max(0L, Math.min(PlayerCombatState.SPIN_ROUND_TICKS,
                until - minecraft.level.getGameTime()));
        int tick = clock(minecraft.player);
        spinActiveUntil = tick + remaining;
        if (remaining > 0 && spinVisualStart < 0) {
            spinVisualStart = tick - (PlayerCombatState.SPIN_ROUND_TICKS - remaining);
            spinVisualYaw = minecraft.player.getYRot();
        }
    }

    private static void recordSpinSequenceKey(int key, int tick) {
        if (spinSequenceReadyUntil >= tick) return;
        if (tick - spinSequenceStartTick > 20 || spinSequenceIndex < 0) {
            spinSequenceIndex = key;
            spinSequenceDirection = 0;
            spinSequenceLength = 1;
            spinSequenceStartTick = tick;
            spinSequenceLastTick = tick;
            return;
        }
        int forwardNext = (spinSequenceIndex + 1) & 3;
        int reverseNext = (spinSequenceIndex + 3) & 3;
        if (spinSequenceDirection == 0) {
            if (key == forwardNext) spinSequenceDirection = 1;
            else if (key == reverseNext) spinSequenceDirection = -1;
            else { spinSequenceIndex = key; spinSequenceDirection = 0; spinSequenceLength = 1; spinSequenceStartTick = tick; spinSequenceLastTick = tick; return; }
        } else {
            int expected = (spinSequenceIndex + spinSequenceDirection + 4) & 3;
            if (key != expected) { spinSequenceIndex = key; spinSequenceDirection = 0; spinSequenceLength = 1; spinSequenceStartTick = tick; spinSequenceLastTick = tick; return; }
        }
        spinSequenceIndex = key;
        spinSequenceLength++;
        spinSequenceLastTick = tick;
        if (spinSequenceLength >= 4) {
            spinSequenceReadyUntil = tick + 10;
            spinSequenceIndex = -1;
        }
    }

    private static Vec3 sideDirection(net.minecraft.world.entity.player.Player player, boolean right) {
        Vec3 look = player.getLookAngle();
        Vec3 side = right ? new Vec3(-look.z, 0.0D, look.x) : new Vec3(look.z, 0.0D, -look.x);
        return side.normalize();
    }

    private static boolean learned(ResourceLocation skill) {
        return skillLevel(skill) > 0;
    }

    private static int skillLevel(ResourceLocation skill) {
        return ZSSClientData.playerData().activeSkillLevel(skill);
    }

    private static void send(ResourceLocation skill, SkillIntentMessage.Action action, Optional<Vec3> direction) {
        if (learned(skill)) ZSSNetwork.sendSkillIntent(new SkillIntentMessage(skill, action, direction));
    }
}
