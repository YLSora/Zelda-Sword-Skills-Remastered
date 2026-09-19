package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.projectile.SwordBeam;

public final class SwordBeamRenderer extends EntityRenderer<SwordBeam> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "textures/entity/sword_beam.png");

    public SwordBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SwordBeam beam, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(1.5F, 1.5F, 1.5F);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        vertex(vertices, pose, packedLight, 0.0F, 0.0F, 0.0F, 1.0F);
        vertex(vertices, pose, packedLight, 1.0F, 0.0F, 1.0F, 1.0F);
        vertex(vertices, pose, packedLight, 1.0F, 1.0F, 1.0F, 0.0F);
        vertex(vertices, pose, packedLight, 0.0F, 1.0F, 0.0F, 0.0F);
        poseStack.popPose();
        super.render(beam, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, int light,
                               float x, float y, float u, float v) {
        vertices.vertex(pose.pose(), x - 0.5F, y - 0.5F, 0.0F)
                .color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SwordBeam beam) {
        return TEXTURE;
    }
}
