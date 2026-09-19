package zeldaswordskills_remastered.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.npc.QuestNpc;
import zeldaswordskills_remastered.client.model.ZSSLegacyModels;
import zeldaswordskills_remastered.client.model.ZSSModelLayers;

public final class QuestNpcRenderer extends MobRenderer<QuestNpc, EntityModel<QuestNpc>> {
    private final EntityModel<QuestNpc> humanoid;
    private final EntityModel<QuestNpc> goron;
    private final EntityModel<QuestNpc> maskTrader;

    public QuestNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        humanoid = model;
        goron = new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.GORON));
        maskTrader = new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.MASK_TRADER));
    }

    @Override
    public void render(QuestNpc npc, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        model = switch (npc.role()) {
            case GORON -> goron;
            case MASK_TRADER -> maskTrader;
            case BARNES, ORCA, ZELDA -> humanoid;
        };
        super.render(npc, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    protected void scale(QuestNpc npc, PoseStack poseStack, float partialTick) {
        if (npc.role() == QuestNpc.Role.GORON) poseStack.scale(1.5F, 1.5F, 1.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(QuestNpc npc) {
        String texture = switch (npc.role()) {
            case ZELDA -> "npc_zelda";
            case MASK_TRADER -> "npc_mask_salesman";
            case GORON -> "goron";
            case BARNES -> "npc_barnes";
            case ORCA -> "npc_orca";
        };
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "textures/entity/" + texture + ".png");
    }
}
