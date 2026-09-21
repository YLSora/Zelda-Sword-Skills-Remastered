package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.client.model.ZSSLegacyModels;
import zeldaswordskills_remastered.client.model.ZSSModelLayers;
import zeldaswordskills_remastered.entity.ChuCreature;
import zeldaswordskills_remastered.entity.LegacyCreature;

public final class LegacyCreatureRenderer extends MobRenderer<LegacyCreature, EntityModel<LegacyCreature>> {
    private final EntityModel<LegacyCreature> chu;
    private final EntityModel<LegacyCreature> babaDeku;
    private final EntityModel<LegacyCreature> babaFire;
    private final EntityModel<LegacyCreature> babaWithered;
    private final EntityModel<LegacyCreature> keese;
    private final EntityModel<LegacyCreature> octorok;
    private final EntityModel<LegacyCreature> skulltula;
    private final EntityModel<LegacyCreature> wizzrobe;

    public LegacyCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), 0.5F);
        chu = model;
        babaDeku = new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.BABA_DEKU));
        babaFire = new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.BABA_FIRE));
        babaWithered = new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.BABA_WITHERED));
        keese = new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.KEESE));
        octorok = new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.OCTOROK));
        skulltula = new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER));
        wizzrobe = new ZSSLegacyModels.PartModel<>(context.bakeLayer(ZSSModelLayers.WIZZROBE));
    }

    @Override
    public void render(LegacyCreature creature, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        shadowRadius = creature instanceof ChuCreature chu ? 0.15F * chu.getSize()
                : switch (creature.kind()) {
                    case KEESE_NORMAL, KEESE_FIRE, KEESE_ICE, KEESE_THUNDER, KEESE_CURSED -> 0.125F;
                    case FOREST_BOSS -> 2.0F;
                    default -> 0.5F;
                };
        model = switch (creature.kind()) {
            case DARKNUT_STANDARD, DARKNUT_MIGHTY, DARKNUT_BOSS -> throw new IllegalStateException("Darknuts require DarknutRenderer");
            case CHU_RED, CHU_GREEN, CHU_BLUE, CHU_YELLOW -> chu;
            case BABA_DEKU -> babaDeku;
            case BABA_FIRE -> babaFire;
            case BABA_WITHERED -> babaWithered;
            case KEESE_NORMAL, KEESE_FIRE, KEESE_ICE, KEESE_THUNDER, KEESE_CURSED -> keese;
            case OCTOROK_NORMAL, OCTOROK_BOMB, WATER_BOSS -> octorok;
            case SKULLTULA_NORMAL, SKULLTULA_GOLD, FOREST_BOSS -> skulltula;
            case WIZZROBE_FIRE, WIZZROBE_ICE, WIZZROBE_LIGHTNING, WIZZROBE_WIND, WIZZROBE_GRAND, FIRE_BOSS -> wizzrobe;
            case FAIRY, NAVI -> throw new IllegalStateException("Fairies require FairyRenderer");
        };
        super.render(creature, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    protected void scale(LegacyCreature creature, PoseStack poseStack, float partialTick) {
        float scale = switch (creature.kind()) {
            case CHU_RED, CHU_GREEN, CHU_BLUE, CHU_YELLOW -> 0.65F;
            case BABA_DEKU, BABA_FIRE, BABA_WITHERED -> 1.25F;
            case KEESE_NORMAL, KEESE_FIRE, KEESE_ICE, KEESE_THUNDER, KEESE_CURSED -> 0.25F;
            case OCTOROK_NORMAL, OCTOROK_BOMB, WATER_BOSS -> 0.7F;
            case WIZZROBE_GRAND -> 1.5F;
            case FOREST_BOSS -> 4.0F;
            default -> 1.0F;
        };
        if (creature instanceof ChuCreature chu) {
            float size = chu.getSize();
            float squish = 1.0F + chu.getSquish(partialTick) * 0.5F / size;
            float horizontal = 1.0F / squish;
            poseStack.scale(0.999F, 0.999F, 0.999F);
            poseStack.translate(0.0F, 0.001F, 0.0F);
            poseStack.scale(scale * size * horizontal, scale * size * squish, scale * size * horizontal);
        } else {
            poseStack.scale(scale, scale, scale);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(LegacyCreature creature) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID,
                "textures/entity/" + (creature instanceof zeldaswordskills_remastered.entity.FireBossCreature boss
                        ? boss.texturePath() : creature.kind().texturePath()) + ".png");
    }
}
