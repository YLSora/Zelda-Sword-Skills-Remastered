package zeldaswordskills_remastered.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.combat.DodgeMovement;
import zeldaswordskills_remastered.combat.PlayerCombatState;
import zeldaswordskills_remastered.network.DodgeOrbitMessage;

/** Reorients one approved impulse around the target, without teleporting or adding speed. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class ZSSClientDodge {
    private static LocalPlayer owner;
    private static LivingEntity target;
    private static boolean right;
    private static double speed;
    private static double remainingDistance;
    private static double appliedChord;
    private static int lastHurtTime;
    private static int movementTicks;

    private ZSSClientDodge() {}

    public static void start(DodgeOrbitMessage message) {
        clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !(minecraft.level.getEntity(message.targetId()) instanceof LivingEntity living)) return;
        owner = minecraft.player;
        target = living;
        right = message.right();
        speed = message.speed();
        remainingDistance = DodgeMovement.DISTANCE;
        lastHurtTime = owner.hurtTime;
    }

    @SubscribeEvent
    public static void movementInput(MovementInputUpdateEvent event) {
        if (owner == null || event.getEntity() != owner) return;
        if (!valid() || !owner.onGround() || owner.isPassenger() || owner.isInWater() || owner.isInLava()
                || event.getInput().jumping || owner.hurtTime > lastHurtTime
                || movementTicks >= PlayerCombatState.DODGE_DURATION_TICKS
                || speed < 0.003D || remainingDistance <= 0.0D) {
            clear();
            return;
        }
        lastHurtTime = owner.hurtTime;
        Vec3 motion = DodgeMovement.orbitVelocity(owner.position(), target.position(),
                Math.min(speed, remainingDistance), right);
        appliedChord = motion.horizontalDistance();
        if (appliedChord < 1.0E-6D) {
            clear();
            return;
        }
        // A/D already selected the orbit direction; walking input must not add radial drift.
        event.getInput().leftImpulse = 0.0F;
        event.getInput().forwardImpulse = 0.0F;
        owner.setDeltaMovement(motion.x, owner.getDeltaMovement().y, motion.z);
        movementTicks++;
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || owner == null || event.player != owner || appliedChord == 0.0D) return;
        if (owner.horizontalCollision || !valid() || owner.hurtTime > lastHurtTime) {
            clear();
            return;
        }
        // Read the decay actually applied by vanilla; never refresh the launch impulse.
        speed = Math.min(appliedChord, owner.getDeltaMovement().horizontalDistance());
        remainingDistance = Math.max(0.0D, remainingDistance - appliedChord);
        appliedChord = 0.0D;
        if (remainingDistance == 0.0D) {
            owner.setDeltaMovement(0.0D, owner.getDeltaMovement().y, 0.0D);
            clear();
        } else if (movementTicks >= PlayerCombatState.DODGE_DURATION_TICKS) {
            // Leave the residual impulse to vanilla, but restore walking on the next input tick.
            clear();
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (owner != null && !valid()) clear();
    }

    private static boolean valid() {
        Minecraft minecraft = Minecraft.getInstance();
        return owner == minecraft.player && minecraft.screen == null && owner.isAlive()
                && target.isAlive() && !target.isRemoved() && target.level() == minecraft.level
                && ZSSClientCombatState.targetId() == target.getId();
    }

    public static void clear() {
        owner = null;
        target = null;
        speed = remainingDistance = appliedChord = 0.0D;
        movementTicks = 0;
    }
}
