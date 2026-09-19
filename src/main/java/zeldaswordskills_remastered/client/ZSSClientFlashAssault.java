package zeldaswordskills_remastered.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.network.FlashAssaultStateMessage;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;

import java.util.Optional;

/** Mirrors server windows; movement and attack results are never reported by the client. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class ZSSClientFlashAssault {
    private static LocalPlayer owner;
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static FlashAssaultStateMessage state;
    private static boolean attackSent;
    private static boolean consumePressedAttack;
    private static boolean moving;

    private ZSSClientFlashAssault() {}

    public static void apply(FlashAssaultStateMessage message) {
        if (owner != Minecraft.getInstance().player || level != Minecraft.getInstance().level) clear();
        owner = Minecraft.getInstance().player;
        level = Minecraft.getInstance().level;
        if (owner == null || level == null) { clear(); return; }
        state = message;
        attackSent = false;
        if (message.dashing() || message.attacking()) {
            ZSSClientDodge.clear();
            ZSSClientGameplay.clearFlashConflictingInputs();
            lockHotbar();
        }
        if (moving && (!message.dashing() || owner.level().getGameTime() >= message.dashUntil())) stopMotion();
        if (message.dashing() && owner.level().getGameTime() < message.dashUntil()) {
            message.impulse().ifPresent(impulse -> {
                owner.setDeltaMovement(impulse);
                moving = true;
            });
        }
    }

    public static void clear() { owner = null; level = null; state = null; attackSent = consumePressedAttack = moving = false; }

    private static boolean valid() {
        Minecraft minecraft = Minecraft.getInstance();
        if (owner == null || owner != minecraft.player || level != minecraft.level || level == null || !owner.isAlive()) {
            clear();
            return false;
        }
        return state != null;
    }

    public static boolean busy() {
        return valid() && (state.dashing() && owner.level().getGameTime() < state.dashUntil() || state.attacking());
    }

    public static boolean forwardTap() {
        if (busy()) return true;
        if (!valid() || state.targetId() != ZSSClientCombatState.targetId()
                || owner.level().getGameTime() >= state.readyUntil()
                || owner.level().getGameTime() < state.cooldownUntil()
                || ZSSClientData.playerData().activeSkillLevel(ZSSContentIds.FLASH_ASSAULT) <= 0) return false;
        ZSSNetwork.sendSkillIntent(new SkillIntentMessage(ZSSContentIds.FLASH_ASSAULT, SkillIntentMessage.Action.BEGIN, Optional.empty()));
        return true;
    }

    public static void pressAttack() {
        if (valid() && !state.attacking() && !attackSent && state.followUpUntil() > owner.level().getGameTime()) {
            ZSSNetwork.sendSkillIntent(new SkillIntentMessage(ZSSContentIds.FLASH_ASSAULT, SkillIntentMessage.Action.ATTACK, Optional.empty()));
            attackSent = true;
            consumePressedAttack = true;
            ZSSClientGameplay.clearFlashConflictingInputs();
        }
    }

    public static boolean attack() {
        if (!valid()) return false;
        // Even an instant kill ACK must not let the same physical click become a normal attack.
        if (consumePressedAttack) {
            consumePressedAttack = false;
            return true;
        }
        return state.attacking() || attackSent || state.dashing()
                || state.followUpUntil() > owner.level().getGameTime();
    }

    public static void lockHotbar() {
        if (!busy() || state.lockedSlot() < 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        owner.getInventory().selected = state.lockedSlot();
        for (var mapping : minecraft.options.keyHotbarSlots) while (mapping.consumeClick()) { }
        while (minecraft.options.keySwapOffhand.consumeClick()) { }
        while (minecraft.options.keyDrop.consumeClick()) { }
        while (minecraft.options.keyPickItem.consumeClick()) { }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (valid() && moving && owner.level().getGameTime() >= state.dashUntil()) stopMotion();
        lockHotbar();
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (busy() && Minecraft.getInstance().screen == null) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void movement(MovementInputUpdateEvent event) {
        if (!valid() || !moving || event.getEntity() != owner) return;
        event.getInput().leftImpulse = event.getInput().forwardImpulse = 0.0F;
        event.getInput().jumping = false;
    }

    private static void stopMotion() {
        owner.setDeltaMovement(0.0D, owner.getDeltaMovement().y, 0.0D);
        moving = false;
    }
}
