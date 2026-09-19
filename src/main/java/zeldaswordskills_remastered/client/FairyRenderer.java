package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.LegacyCreature;

public final class FairyRenderer extends EntityRenderer<LegacyCreature> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "textures/entity/fairy.png");

    public FairyRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(LegacyCreature fairy, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.25D, 0.0D);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.3F, 0.3F, 0.3F);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f position = pose.pose();
        Matrix3f normal = pose.normal();
        // Navi keeps her signature pale blue; wild fairies cycle through the pink/green shimmer.
        boolean navi = fairy.kind() == zeldaswordskills_remastered.entity.LegacyCreature.Kind.NAVI;
        VertexConsumer vertices = buffers.getBuffer(navi
                ? RenderType.entityTranslucentEmissive(TEXTURE) : RenderType.entityTranslucent(TEXTURE));
        if (navi) packedLight = LightTexture.FULL_BRIGHT;
        int red = navi ? 173 : (int)((Mth.sin((fairy.tickCount + partialTick) * 0.1F) + 1.0F) * 127.5F);
        int blue = navi ? 230 : (int)((Mth.sin((fairy.tickCount + partialTick) * 0.1F + 4.1887903F) + 1.0F) * 12.75F);
        int green = navi ? 216 : 255;
        vertex(vertices, position, normal, -0.5F, -0.25F, 0.5F, 0.75F, red, green, blue, packedLight);
        vertex(vertices, position, normal, 0.5F, -0.25F, 0.75F, 0.75F, red, green, blue, packedLight);
        vertex(vertices, position, normal, 0.5F, 0.75F, 0.75F, 0.5F, red, green, blue, packedLight);
        vertex(vertices, position, normal, -0.5F, 0.75F, 0.5F, 0.5F, red, green, blue, packedLight);
        poseStack.popPose();
        super.render(fairy, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f position, Matrix3f normal,
                               float x, float y, float u, float v, int red, int green, int blue, int packedLight) {
        consumer.vertex(position, x, y, 0.0F).color(red, green, blue, 128).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(LegacyCreature entity) {
        return TEXTURE;
    }
}
