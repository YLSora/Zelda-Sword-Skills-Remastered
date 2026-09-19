package zeldaswordskills_remastered.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.client.ZSSClientSongState;
import zeldaswordskills_remastered.network.SongIntentMessage;
import zeldaswordskills_remastered.network.SongStateMessage;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.song.SongNote;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InstrumentHudScreen extends Screen {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "textures/gui/gui_ocarina.png");
    private static final int HUD_WIDTH = 213;
    private static final int HUD_HEIGHT = 90;
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 128;
    private final Set<Integer> pressedKeys = new HashSet<>();
    private long seenRevision;
    private int closeAtTick = -1;
    private boolean cancelOnClose = true;

    public InstrumentHudScreen() {
        super(Component.translatable("hud.zeldaswordskills_remastered.instrument"));
        seenRevision = ZSSClientSongState.revision();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null) return;
        if (seenRevision != ZSSClientSongState.revision()) {
            seenRevision = ZSSClientSongState.revision();
            SongStateMessage.Status status = ZSSClientSongState.status();
            if (status == SongStateMessage.Status.SUCCESS || status == SongStateMessage.Status.RECORDED) {
                closeAtTick = minecraft.player.tickCount + 30;
            } else if (status == SongStateMessage.Status.CLOSED) {
                closeWithoutCancel();
                return;
            }
        }
        if (closeAtTick >= 0 && minecraft.player.tickCount >= closeAtTick) closeWithoutCancel();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Component heading = ZSSClientSongState.song()
                .map(id -> Component.translatable("song." + id.getNamespace() + "." + id.getPath()))
                .orElseGet(() -> Component.translatable(ZSSClientSongState.scarecrowMode()
                        ? "hud.zeldaswordskills_remastered.instrument.scarecrow" : "hud.zeldaswordskills_remastered.instrument"));
        Component status = statusText();
        Component controls = Component.translatable("hud.zeldaswordskills_remastered.instrument.controls");
        int textWidth = Math.max(80, Math.min(280, width - 16));
        List<FormattedCharSequence> headingLines = font.split(heading, textWidth);
        List<FormattedCharSequence> statusLines = font.split(status, textWidth);
        List<FormattedCharSequence> controlLines = font.split(controls, textWidth);
        int lineHeight = font.lineHeight;
        int contentHeight = (headingLines.size() + statusLines.size() + controlLines.size()) * lineHeight
                + HUD_HEIGHT + 12;
        int contentTop = Math.max(8, (height - contentHeight) / 2);
        int left = (width - HUD_WIDTH) / 2;
        int top = contentTop + headingLines.size() * lineHeight + 4;
        drawCenteredLines(graphics, headingLines, contentTop, 0xFFFFFF);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(TEXTURE, left, top, 0, 0, HUD_WIDTH, HUD_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        for (int index = 0; index < ZSSClientSongState.notes().size(); index++) {
            SongNote note = ZSSClientSongState.notes().get(index);
            int x = left + 46 + index * 20;
            int y = top + 6 + note.staffRow() * 5;
            graphics.blit(TEXTURE, x, y, HUD_WIDTH, note.spriteRow() * 12, 12, 12,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        int textY = top + HUD_HEIGHT + 4;
        drawCenteredLines(graphics, statusLines, textY, 0xFFFFFF);
        drawCenteredLines(graphics, controlLines, textY + statusLines.size() * lineHeight + 4, 0xBFBFBF);
    }

    private void drawCenteredLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int y, int color) {
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawCenteredString(font, lines.get(index), width / 2, y + index * font.lineHeight, color);
        }
    }

    private Component statusText() {
        return switch (ZSSClientSongState.status()) {
            case MATCHED -> {
                Minecraft game = Minecraft.getInstance();
                long now = game.level == null ? 0L : game.level.getGameTime();
                long ticks = Math.max(0L, ZSSClientSongState.deadline() - now);
                yield Component.translatable("hud.zeldaswordskills_remastered.instrument.playing", String.format("%.1f", ticks / 20.0D));
            }
            case FAILED -> Component.translatable("hud.zeldaswordskills_remastered.instrument.failed");
            case RECORDED -> Component.translatable("hud.zeldaswordskills_remastered.instrument.recorded");
            case SUCCESS -> Component.translatable("hud.zeldaswordskills_remastered.instrument.success");
            default -> Component.translatable("hud.zeldaswordskills_remastered.instrument.ready");
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        SongNote note = switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> SongNote.D1;
            case GLFW.GLFW_KEY_S -> SongNote.F1;
            case GLFW.GLFW_KEY_D -> SongNote.A2;
            case GLFW.GLFW_KEY_A -> SongNote.B2;
            case GLFW.GLFW_KEY_W -> SongNote.D2;
            default -> null;
        };
        if (note != null) {
            if (pressedKeys.add(keyCode)) play(note);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        pressedKeys.remove(keyCode);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (cancelOnClose) ZSSNetwork.sendSongIntent(new SongIntentMessage(SongIntentMessage.Action.CANCEL));
        ZSSClientSongState.clear();
        super.onClose();
    }

    private void play(SongNote note) {
        if (ZSSClientSongState.status() != SongStateMessage.Status.OPEN
                && ZSSClientSongState.status() != SongStateMessage.Status.FAILED) return;
        ZSSNetwork.sendSongIntent(new SongIntentMessage(SongIntentMessage.Action.NOTE, java.util.Optional.of(note)));
    }

    private void closeWithoutCancel() {
        cancelOnClose = false;
        if (minecraft != null) minecraft.setScreen(null);
        ZSSClientSongState.clear();
    }
}
