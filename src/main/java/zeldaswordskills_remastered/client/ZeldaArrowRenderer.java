package zeldaswordskills_remastered.client;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.entity.projectile.ZeldaArrow;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

public final class ZeldaArrowRenderer extends ArrowRenderer<ZeldaArrow> {
    public ZeldaArrowRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(ZeldaArrow arrow) {
        String texture = switch (arrow.kind()) {
            case BOMB, FIRE_BOMB, WATER_BOMB -> "arrow_bomb";
            case FIRE -> "arrow_fire";
            case ICE -> "arrow_ice";
            case LIGHT -> "arrow_light";
        };
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "textures/entity/" + texture + ".png");
    }
}
