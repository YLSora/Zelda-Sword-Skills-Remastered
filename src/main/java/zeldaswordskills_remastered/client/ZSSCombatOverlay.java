package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector4f;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.config.ZSSConfig;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class ZSSCombatOverlay {
    private static final ResourceLocation MAGIC_METER = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "textures/gui/magic_meter_horizontal.png");
    private static final ResourceLocation TARGET = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "textures/gui/target.png");
    private static final ResourceLocation IAI_TARGET = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "textures/gui/iai_lock.png");
    private static final int MAGIC_INNER_WIDTH = 100;
    private static final int MAGIC_WIDTH = MAGIC_INNER_WIDTH + 6;
    private static final int MAGIC_HEIGHT = 9;
    private static final int EDGE_PADDING = 10;
    private static Entity projectedTarget;
    private static float targetScreenX;
    private static float targetScreenY;

    private ZSSCombatOverlay() {
    }

    @SubscribeEvent
    public static void projectTarget(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        projectedTarget = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                || !minecraft.player.isAlive()) return;
        boolean iai = !ZSSClientCombatState.hasTarget() && ZSSClientCombatState.iaiTargetId() >= 0;
        if (!iai && (!ZSSClientCombatState.hasTarget() || !minecraft.options.getCameraType().isFirstPerson())) return;
        Entity target = minecraft.level.getEntity(iai ? ZSSClientCombatState.iaiTargetId() : ZSSClientCombatState.targetId());
        if (target == null || !target.isAlive()) return;

        // Lock-on aims at the bounding-box center; interpolate it with the rendered entity.
        Vec3 anchor = target.getBoundingBox().getCenter().subtract(target.position())
                .add(target.getPosition(event.getPartialTick())).subtract(event.getCamera().getPosition());
        Vector4f clip = new Vector4f((float) anchor.x, (float) anchor.y, (float) anchor.z, 1.0F);
        clip.mul(event.getPoseStack().last().pose()).mul(event.getProjectionMatrix());
        if (clip.w() <= 0.0F || Math.abs(clip.x()) > clip.w() || Math.abs(clip.y()) > clip.w()
                || Math.abs(clip.z()) > clip.w()) return;
        targetScreenX = (clip.x() / clip.w() + 1.0F) * 0.5F;
        targetScreenY = (1.0F - clip.y() / clip.w()) * 0.5F;
        projectedTarget = target;
    }

    public static void render(net.minecraftforge.client.gui.overlay.ForgeGui forgeGui, GuiGraphics graphics,
                              float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity markerTarget = projectedTarget;
        projectedTarget = null;
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) return;

        boolean magicVisible = renderMagic(graphics, minecraft);
        int panelWidth = Math.max(80, Math.min(240, width - EDGE_PADDING * 2));
        int panelLeft = (width - panelWidth) / 2;
        int y = magicVisible && panelLeft < EDGE_PADDING + MAGIC_WIDTH + 8 ? 34 : EDGE_PADDING;

        if (ZSSClientCombatState.hasTarget()) {
            net.minecraft.world.entity.Entity target = minecraft.level.getEntity(ZSSClientCombatState.targetId());
            if (target != null) {
                if (target == markerTarget && target.isAlive() && minecraft.player.isAlive()
                        && minecraft.options.getCameraType().isFirstPerson()) {
                    renderTarget(graphics, minecraft.level.getGameTime(), partialTick,
                            targetScreenX * width, targetScreenY * height);
                }
                Component text = Component.translatable("hud.zeldaswordskills_remastered.target", target.getDisplayName());
                y += drawPanel(graphics, minecraft.font, text, width / 2, y, panelWidth, 0xFFE66D);
            }
        } else if (markerTarget != null && markerTarget.getId() == ZSSClientCombatState.iaiTargetId()
                && markerTarget.isAlive() && minecraft.player.isAlive()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(IAI_TARGET, Math.round(targetScreenX * width) - 12, Math.round(targetScreenY * height) - 12,
                    24, 24, 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();
        }
        if (ZSSClientCombatState.shouldShowCombo(minecraft.level.getGameTime())) {
            Component text = Component.translatable(
                    ZSSClientCombatState.comboFinished() ? "hud.zeldaswordskills_remastered.combo_finished" : "hud.zeldaswordskills_remastered.combo",
                    ZSSClientCombatState.comboCount(), ZSSClientCombatState.comboMaximum(),
                    String.format(Locale.ROOT, "%.1f", ZSSClientCombatState.comboDamage()));
            y += drawPanel(graphics, minecraft.font, text, width / 2, y, panelWidth,
                    ZSSClientCombatState.comboFinished() ? 0xD580FF : 0xFFFFFF);
        }
        if (ZSSClientCombatState.focusActive(minecraft.level.getGameTime())) {
            drawPanel(graphics, minecraft.font, Component.translatable("hud.zeldaswordskills_remastered.fatal_strike"),
                    width / 2, y, panelWidth, 0xFF3333);
        }
    }

    private static void renderTarget(GuiGraphics graphics, long gameTime, float partialTick, float x, float y) {
        double phase = ((gameTime % 40L) + (double) partialTick) * (Math.PI * 2.0D / 40.0D);
        float pulse = (float) (0.5D + 0.5D * Math.sin(phase - Math.PI / 2.0D));
        float scale = 1.5F * (1.0F + 0.75F * pulse);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(TARGET, -8, -8, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    private static boolean renderMagic(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player.getAbilities().instabuild) return false;
        if (ZSSConfig.CLIENT.hideMagicBar.get() && !minecraft.player.isShiftKeyDown()) return false;
        float maximum = ZSSClientData.playerData().maxMagic();
        if (maximum <= 0.0F) return false;

        float current = Mth.clamp(ZSSClientData.playerData().currentMagic(), 0.0F, maximum);
        int fill = Mth.clamp(Mth.floor(current / maximum * MAGIC_INNER_WIDTH), 0, MAGIC_INNER_WIDTH);
        int x = EDGE_PADDING;
        int y = EDGE_PADDING;
        graphics.blit(MAGIC_METER, x, y, 0, 0, 103, MAGIC_HEIGHT, 106, 12);
        graphics.blit(MAGIC_METER, x + 103, y, 103, 0, 3, MAGIC_HEIGHT, 106, 12);
        if (fill > 0) graphics.blit(MAGIC_METER, x + 3, y + 3, 0, 9, fill, 3, 106, 12);

        Component text = Component.translatable("hud.zeldaswordskills_remastered.magic", formatMagic(current), formatMagic(maximum));
        graphics.drawCenteredString(minecraft.font, text, x + MAGIC_WIDTH / 2, y + MAGIC_HEIGHT + 2, 0x7CFF7C);
        return true;
    }

    private static int drawPanel(GuiGraphics graphics, Font font, Component text, int centerX, int y,
                                 int maximumWidth, int color) {
        List<FormattedCharSequence> lines = font.split(text, maximumWidth - 8);
        int contentHeight = Math.max(font.lineHeight, lines.size() * font.lineHeight);
        int actualWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int left = centerX - actualWidth / 2 - 4;
        graphics.fill(left, y - 2, left + actualWidth + 8, y + contentHeight + 1, 0x88000000);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawCenteredString(font, lines.get(index), centerX, y + index * font.lineHeight, color);
        }
        return contentHeight + 5;
    }

    private static String formatMagic(float value) {
        return value == Mth.floor(value) ? Integer.toString(Mth.floor(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
