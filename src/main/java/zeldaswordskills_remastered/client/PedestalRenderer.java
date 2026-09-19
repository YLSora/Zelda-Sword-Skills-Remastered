package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import zeldaswordskills_remastered.block.entity.PedestalBlockEntity;

/** Renders the exact sword stack stored by the ordinary pedestal block entity. */
public final class PedestalRenderer implements BlockEntityRenderer<PedestalBlockEntity> {
    private final ItemRenderer itemRenderer;

    public PedestalRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(PedestalBlockEntity pedestal, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!pedestal.hasSword() || pedestal.sword().isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.9D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(pedestal.orientation() == 0 ? 0.0F : 90.0F));
        // ItemDisplayContext.FIXED applies the generated-item Y flip; 135 degrees
        // compensates for it so the sword tip points down in the pedestal.
        poseStack.mulPose(Axis.ZP.rotationDegrees(135.0F));
        itemRenderer.renderStatic(pedestal.sword(), ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffers, pedestal.getLevel(), pedestal.getBlockPos().hashCode());
        poseStack.popPose();
    }
}
