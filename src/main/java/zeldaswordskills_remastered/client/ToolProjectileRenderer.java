package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;

/** Dedicated animated renderer for boomerangs and tethered stage-nine tools. */
public final class ToolProjectileRenderer extends EntityRenderer<ToolProjectile> {
    private static final int SEGMENTS = 24;
    private final ItemRenderer itemRenderer;
    private final net.minecraft.client.model.geom.ModelPart hookHead;
    private static final ResourceLocation HOOK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID, "textures/entity/hookshot.png");

    public ToolProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        hookHead = context.bakeLayer(zeldaswordskills_remastered.client.model.ZSSModelLayers.HOOKSHOT_HEAD);
    }

    @Override
    public void render(ToolProjectile projectile, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        if (projectile.mode().isBoomerang()) {
            renderBoomerang(projectile, partialTick, poseStack, buffers, packedLight);
        } else if (projectile.mode() == ToolProjectile.Mode.HOOKSHOT || projectile.mode() == ToolProjectile.Mode.WHIP) {
            Vec3 towardOwner = tetherDelta(projectile, partialTick);
            if (projectile.mode() == ToolProjectile.Mode.HOOKSHOT) {
                poseStack.pushPose();
                poseStack.mulPose(hookHeadRotation(towardOwner));
                hookHead.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(HOOK_TEXTURE)),
                        packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
            renderTether(projectile, towardOwner, poseStack, buffers, packedLight);
        }
        super.render(projectile, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private void renderBoomerang(ToolProjectile projectile, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-((projectile.tickCount + partialTick) * 50.0F) % 360.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(0.7F, 0.7F, 0.7F);
        itemRenderer.renderStatic(projectile.renderStack(), ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffers, projectile.level(), projectile.getId());
        poseStack.popPose();
    }

    static Quaternionf hookHeadRotation(Vec3 towardOwner) {
        if (towardOwner.lengthSqr() < 1.0E-12D) return new Quaternionf();
        // The model's tip points along -Z; its +Z tail stays aligned with the tether toward the owner.
        float yaw = (float) Math.atan2(towardOwner.x, towardOwner.z);
        float pitch = (float) -Math.atan2(towardOwner.y, towardOwner.horizontalDistance());
        return Axis.YP.rotation(yaw).mul(Axis.XP.rotation(pitch));
    }

    private static Vec3 tetherDelta(ToolProjectile projectile, float partialTick) {
        Entity owner = projectile.getOwner();
        if (owner == null) return Vec3.ZERO;

        Vec3 origin = projectile.mode() == ToolProjectile.Mode.HOOKSHOT
                ? ToolProjectile.hookOrigin(owner, partialTick)
                : owner.getPosition(partialTick).add(0.0D, owner.getBbHeight() * 0.72D, 0.0D);
        return origin.subtract(projectile.getPosition(partialTick));
    }

    private static void renderTether(ToolProjectile projectile, Vec3 delta, PoseStack poseStack,
                                     MultiBufferSource buffers, int packedLight) {
        if (delta.lengthSqr() < 1.0E-12D) return;
        boolean whip = projectile.mode() == ToolProjectile.Mode.WHIP;
        float width = whip ? 0.035F : 0.025F;
        float sag = whip ? 0.38F : 0.08F;
        boolean magic = projectile.isMagicWhip();
        int red = magic ? 70 : whip ? 139 : 128;
        int green = magic ? 220 : whip ? 90 : 128;
        int blue = magic ? 190 : whip ? 43 : 128;

        VertexConsumer vertices = buffers.getBuffer(RenderType.leash());
        Matrix4f matrix = poseStack.last().pose();
        drawRibbon(vertices, matrix, delta, width, 0.0F, width, sag, red, green, blue, packedLight);
        drawRibbon(vertices, matrix, delta, 0.0F, width, 0.0F, sag, red, green, blue, packedLight);
    }

    private static void drawRibbon(VertexConsumer vertices, Matrix4f matrix, Vec3 delta,
                                   float offsetX, float offsetY, float offsetZ, float sag,
                                   int red, int green, int blue, int packedLight) {
        for (int segment = 0; segment <= SEGMENTS; segment++)
            addVertexPair(vertices, matrix, delta, segment, offsetX, offsetY, offsetZ, sag,
                    red, green, blue, packedLight);
        for (int segment = SEGMENTS; segment >= 0; segment--)
            addVertexPair(vertices, matrix, delta, segment, offsetX, offsetY, offsetZ, sag,
                    red, green, blue, packedLight);
    }

    private static void addVertexPair(VertexConsumer vertices, Matrix4f matrix, Vec3 delta, int segment,
                                      float offsetX, float offsetY, float offsetZ, float sag,
                                      int red, int green, int blue, int packedLight) {
        float progress = segment / (float) SEGMENTS;
        float curve = Mth.sin(progress * Mth.PI) * sag;
        float x = (float) (delta.x * progress);
        float y = (float) (delta.y * progress) - curve;
        float z = (float) (delta.z * progress);
        int shade = (segment & 1) == 0 ? 255 : 205;
        vertex(vertices, matrix, x - offsetX, y - offsetY, z - offsetZ,
                red * shade / 255, green * shade / 255, blue * shade / 255, packedLight);
        vertex(vertices, matrix, x + offsetX, y + offsetY, z + offsetZ,
                red, green, blue, packedLight);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z,
                               int red, int green, int blue, int packedLight) {
        vertices.vertex(matrix, x, y, z).color(red, green, blue, 255).uv2(packedLight).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ToolProjectile projectile) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
