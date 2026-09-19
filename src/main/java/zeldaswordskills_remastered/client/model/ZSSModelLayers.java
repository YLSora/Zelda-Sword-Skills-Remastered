package zeldaswordskills_remastered.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

public final class ZSSModelLayers {
    public static final ModelLayerLocation DARKNUT = layer("darknut");
    public static final ModelLayerLocation GORON = layer("goron");
    public static final ModelLayerLocation MASK_TRADER = layer("mask_trader");
    public static final ModelLayerLocation OCTOROK = layer("octorok");
    public static final ModelLayerLocation WIZZROBE = layer("wizzrobe");
    public static final ModelLayerLocation BABA_DEKU = layer("baba_deku");
    public static final ModelLayerLocation BABA_FIRE = layer("baba_fire");
    public static final ModelLayerLocation BABA_WITHERED = layer("baba_withered");
    public static final ModelLayerLocation KEESE = layer("keese");
    public static final ModelLayerLocation HOOKSHOT_HEAD = layer("hookshot_head");

    private ZSSModelLayers() {
    }

    private static ModelLayerLocation layer(String path) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path), "main");
    }
}
