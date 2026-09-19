package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

/**
 * Draws the Parry guard while its window is open, using the holding-weapon block animation from
 * CelestialOverhaul (Minecraft-Celestial/CelestialOverhaul, `WeaponBlockHandler.renderBlock` and
 * `COverhaulClient.renderArm`): the blade is brought up across the view in first person, and the
 * weapon arm is raised in third person.
 *
 * <p>That mod drives the two sides from mixins. Neither is needed here, because Forge exposes the
 * same two points: {@link RenderHandEvent} fires before the vanilla arm transform, and a custom
 * {@link HumanoidModel.ArmPose} may carry an {@code IArmPoseTransformer}, which the model invokes
 * for any pose it does not recognise. The one thing their mixin does that a transformer cannot is
 * cancel {@code setupAttackAnimation}; that is reproduced instead by zeroing
 * {@code EntityModel.attackTime}, which {@code setupAttackAnimation} is skipped on and which the
 * renderer recomputes every frame before the model is posed.
 *
 * <p>Both sides only read {@link ZSSClientGameplay#isParryGuarding}. The skill itself stays
 * server-authoritative, so nothing here can open, extend or shorten the window; when it closes the
 * vanilla animation comes straight back.
 */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class ZSSParryAnimation {
    /*
     * First person, in two steps: the vanilla item arm transform, then CelestialOverhaul's block
     * offsets. RenderHandEvent fires before the vanilla transform would be applied, so the first
     * step is laid down here by hand; it is ItemInHandRenderer.applyItemArmTransform verbatim.
     */
    private static final float ARM_ANCHOR_X = 0.56F;
    private static final float ARM_ANCHOR_Y = -0.52F;
    private static final float ARM_ANCHOR_Z = -0.72F;
    private static final float ARM_EQUIP_DROP = -0.6F;
    /** WeaponBlockHandler.renderBlock, with its own left/right sign folded in by the caller. */
    private static final float BLOCK_TRANSLATE_X = -0.14142136F;
    private static final float BLOCK_TRANSLATE_Y = 0.08F;
    private static final float BLOCK_TRANSLATE_Z = 0.14142136F;
    private static final float BLOCK_PITCH = -102.25F;
    private static final float BLOCK_YAW = 13.365F;
    private static final float BLOCK_ROLL = 78.05F;

    /** COverhaulClient.renderArm: fold the arm's existing pitch towards a raised guard stance. */
    private static final float ARM_POSE_PITCH_SCALE = 0.5F;
    private static final float ARM_POSE_PITCH_OFFSET = -0.9424778F;
    private static final float ARM_POSE_YAW = -0.5235988F;

    /**
     * The guard pose. Created once during client setup, which is before anything is rendered: a
     * custom enum constant has to exist before the model's pose switch is first built, or the
     * switch would not know about it.
     */
    private static HumanoidModel.ArmPose swordBlockPose;

    private ZSSParryAnimation() {
    }

    /** Builds the guard pose. Called from client setup so it exists before the first render. */
    public static void bootstrap() {
        if (swordBlockPose != null) return;
        swordBlockPose = HumanoidModel.ArmPose.create("sword_block", true, ZSSParryAnimation::poseGuardArm);
    }

    /**
     * Takes over the main hand while the local player guards, drawing the blade in the block pose
     * instead of the ordinary held-item animation. The event only fires while the window is open, so
     * the vanilla animation returns on its own the moment it closes.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderGuardingHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ZSSClientGameplay.isParryGuarding(player)) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        event.setCanceled(true);
        boolean rightHand = player.getMainArm() == HumanoidArm.RIGHT;
        int sign = rightHand ? 1 : -1;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // applyItemArmTransform: stand where the vanilla hand would be.
        poseStack.translate(sign * ARM_ANCHOR_X,
                ARM_ANCHOR_Y + event.getEquipProgress() * ARM_EQUIP_DROP, ARM_ANCHOR_Z);
        // renderBlock: lift the blade into the guard and turn it to face outwards.
        poseStack.translate(sign * BLOCK_TRANSLATE_X, BLOCK_TRANSLATE_Y, BLOCK_TRANSLATE_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(BLOCK_PITCH));
        poseStack.mulPose(Axis.YP.rotationDegrees(sign * BLOCK_YAW));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sign * BLOCK_ROLL));
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                player, stack,
                rightHand ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !rightHand, poseStack, event.getMultiBufferSource(), event.getPackedLight());
        poseStack.popPose();
    }

    /**
     * Raises the weapon arm in third person. This fires before the renderer runs {@code setupAnim},
     * which is what turns a pose into arm rotations, so the pose is set here and consumed there.
     * Only the local player is animated: another player's guard is server state this client is never
     * told about, so their pose stays vanilla.
     */
    @SubscribeEvent
    public static void raiseGuardingArm(RenderPlayerEvent.Pre event) {
        if (swordBlockPose == null) return;
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        if (!ZSSClientGameplay.isParryGuarding(player)) return;
        HumanoidModel<?> model = event.getRenderer().getModel();
        if (player.getMainArm() == HumanoidArm.RIGHT) model.rightArmPose = swordBlockPose;
        else model.leftArmPose = swordBlockPose;
    }

    /**
     * CelestialOverhaul's arm rotation, applied where the vanilla {@code BLOCK} pose would be. The
     * yaw sign follows the entity's main arm, exactly as their render helper does.
     */
    private static void poseGuardArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        part.xRot = part.xRot * ARM_POSE_PITCH_SCALE + ARM_POSE_PITCH_OFFSET;
        part.yRot = (entity.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F) * ARM_POSE_YAW;
        // Stands in for that mod cancelling setupAttackAnimation: it is skipped when attackTime is
        // zero, and the renderer recomputes attackTime from the swing every frame before posing, so
        // this only ever clears the swing influence on the guard — it cannot leak into another frame
        // or another entity.
        model.attackTime = 0.0F;
    }
}
