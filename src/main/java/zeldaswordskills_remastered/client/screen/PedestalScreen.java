package zeldaswordskills_remastered.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import zeldaswordskills_remastered.menu.PedestalMenu;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

public final class PedestalScreen extends AbstractContainerScreen<PedestalMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "textures/gui/gui_pedestal.png");

    public PedestalScreen(PedestalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int left = (width - imageWidth) / 2;
        int top = (height - imageHeight) / 2;
        graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);
        int pendants = menu.pendants();
        if ((pendants & 1) != 0) graphics.blit(TEXTURE, left + 57, top + 7, 176, 0, 62, 31);
        if ((pendants & 2) != 0) graphics.blit(TEXTURE, left + 26, top + 38, 176, 0, 62, 31);
        if ((pendants & 4) != 0) graphics.blit(TEXTURE, left + 88, top + 38, 176, 0, 62, 31);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component fittedTitle = fit(title, imageWidth - 16);
        graphics.drawCenteredString(font, fittedTitle, imageWidth / 2, titleLabelY, 0x404040);
        graphics.drawString(font, fit(playerInventoryTitle, imageWidth - 16), inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    private Component fit(Component text, int maximumWidth) {
        if (font.width(text) <= maximumWidth) return text;
        String suffix = "...";
        String visible = font.plainSubstrByWidth(text.getString(), Math.max(0, maximumWidth - font.width(suffix)));
        return Component.literal(visible + suffix).withStyle(text.getStyle());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
