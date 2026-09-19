package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.client.model.ZSSLegacyModels;
import zeldaswordskills_remastered.client.model.ZSSModelLayers;
import zeldaswordskills_remastered.entity.LegacyCreature;

/** The three Darknut forms share their armor model and vanilla equipment rendering. */
public final class DarknutRenderer extends MobRenderer<LegacyCreature, ZSSLegacyModels.PartModel<LegacyCreature>> {
    public DarknutRenderer(EntityRendererProvider.Context context) {
        super(context, new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.DARKNUT)), .5F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override protected void scale(LegacyCreature creature, PoseStack poseStack, float partialTick) {
        float scale = creature.kind() == LegacyCreature.Kind.DARKNUT_BOSS ? 1.8F : 1.5F;
        poseStack.scale(scale, scale, scale);
    }

    @Override public ResourceLocation getTextureLocation(LegacyCreature creature) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID,
                "textures/entity/" + creature.kind().texturePath() + ".png");
    }
}
