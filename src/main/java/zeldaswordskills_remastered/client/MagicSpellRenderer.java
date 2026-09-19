package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;

/** The two rotating, full-bright elemental cubes from the original spell renderer. */
public final class MagicSpellRenderer extends EntityRenderer<ToolProjectile> {
    private final BlockRenderDispatcher blocks;

    public MagicSpellRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(ToolProjectile spell, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        Block block = switch (spell.mode()) {
            case FIRE -> Blocks.MAGMA_BLOCK;
            case ICE -> Blocks.ICE;
            case LIGHTNING -> Blocks.GOLD_BLOCK;
            case WIND -> Blocks.EMERALD_BLOCK;
            default -> throw new IllegalStateException("Not a magic spell: " + spell.mode());
        };
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.of(new org.joml.Vector3f(0.8F, 0.0F, -0.6F))
                .rotationDegrees((spell.tickCount + partialTick) * 40.0F));
        poseStack.scale(0.25F, 0.25F, 0.25F);
        for (int cube = 0; cube < 2; cube++) {
            poseStack.pushPose();
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            blocks.renderSingleBlock(block.defaultBlockState(), poseStack, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            poseStack.mulPose(Axis.of(new org.joml.Vector3f(1, 0, 1).normalize()).rotationDegrees(45));
        }
        poseStack.popPose();
        super.render(spell, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ToolProjectile spell) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
