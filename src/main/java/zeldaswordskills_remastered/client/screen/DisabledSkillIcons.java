package zeldaswordskills_remastered.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Screen-owned copies keep the shared item atlas and inventory rendering untouched. */
final class DisabledSkillIcons implements AutoCloseable {
    private final Map<TextureAtlasSprite, ResourceLocation> textures = new HashMap<>();

    void render(GuiGraphics graphics, ItemStack orb, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureAtlasSprite sprite = minecraft.getItemRenderer().getModel(orb, minecraft.level,
                minecraft.player, 0).getParticleIcon();
        ResourceLocation texture = textures.computeIfAbsent(sprite, DisabledSkillIcons::create);
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, 16, 16, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.disableBlend();
    }

    private static ResourceLocation create(TextureAtlasSprite sprite) {
        NativeImage image = new NativeImage(sprite.contents().width(), sprite.contents().height(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = sprite.getPixelRGBA(0, x, y);
                int red = pixel & 255;
                int green = pixel >>> 8 & 255;
                int blue = pixel >>> 16 & 255;
                float luminance = red * 0.2126F + green * 0.7152F + blue * 0.0722F;
                int r = Math.round((luminance + (red - luminance) * 0.35F) * 0.6F);
                int g = Math.round((luminance + (green - luminance) * 0.35F) * 0.6F);
                int b = Math.round((luminance + (blue - luminance) * 0.35F) * 0.6F);
                image.setPixelRGBA(x, y, pixel & 0xFF000000 | b << 16 | g << 8 | r);
            }
        }
        String key = "disabled_skill/" + Integer.toUnsignedString(sprite.contents().name().hashCode(), 16);
        return Minecraft.getInstance().getTextureManager().register(key, new DynamicTexture(image));
    }

    @Override
    public void close() {
        textures.values().forEach(Minecraft.getInstance().getTextureManager()::release);
        textures.clear();
    }
}
