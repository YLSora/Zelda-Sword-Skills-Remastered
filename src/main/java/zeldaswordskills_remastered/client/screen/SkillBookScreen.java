package zeldaswordskills_remastered.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.client.ZSSClientData;
import zeldaswordskills_remastered.client.ZSSKeyMappings;
import zeldaswordskills_remastered.item.ProgressionItem;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.network.ZSSNetwork;

/** The original player-surrounding skill slots with a scrollable description page. */
public final class SkillBookScreen extends Screen {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "textures/gui/gui_skills.png");
    private static final String TEXT = "screen.zeldaswordskills_remastered.skill_book.";
    private static final int BOOK_WIDTH = 281;
    private static final int BOOK_HEIGHT = 180;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int BODY_X = 155;
    private static final int BODY_WIDTH = 101;
    private static final int BODY_BOTTOM = 153;
    private static final int SLOT_SIZE = 18;
    private final SkillBookText text;
    private final List<SkillSlot> slots = new ArrayList<>();
    private final DisabledSkillIcons disabledIcons = new DisabledSkillIcons();
    private ResourceLocation selected;
    private ResourceLocation lastClicked;
    private long lastClickTime;
    private int scroll;
    private int maxScroll;
    private int bodyTop;
    private int thumbHeight;
    private double dragOffset;
    private boolean draggingScroll;

    public SkillBookScreen() {
        this(SkillBookText.load());
    }

    private SkillBookScreen(SkillBookText text) {
        super(text.get("screen.zeldaswordskills_remastered.skill_book"));
        this.text = text;
        int index = 0;
        for (ResourceLocation id : ZSSContentIds.SKILL_ORDER) {
            int x;
            int y;
            if (id.equals(ZSSContentIds.CONTINUOUS_FLASH)) {
                x = 22; y = 35;
            } else if (id.equals(ZSSContentIds.SUPER_SPIN_ATTACK)) {
                x = 108; y = 35;
            } else if (id.equals(ZSSContentIds.BONUS_HEART)) {
                x = 22; y = 140;
            } else if (id.equals(ZSSContentIds.DODGE)) {
                x = 44; y = 140;
            } else if (id.equals(ZSSContentIds.SWORD_BASIC)) {
                x = 65; y = 140;
            } else if (id.equals(ZSSContentIds.DOUBLE_JUMP)) {
                x = 86; y = 140;
            } else if (id.equals(ZSSContentIds.LEAPING_BLOW)) {
                // Ground Slam keeps its original left-column slot below the upper corner skill.
                x = 22; y = 56;
            } else {
                int[][] positions = {
                        {108, 56}, {22, 77}, {108, 77}, {22, 98}, {108, 98},
                        {22, 119}, {108, 119}, {44, 119}, {65, 119}, {86, 119}
                };
                int[] position = positions[index++];
                x = position[0];
                y = position[1];
            }
            ItemStack orb = new ItemStack(ZSSRegistries.getItem("skill_orb"));
            orb.getOrCreateTag().putString(ProgressionItem.SKILL_TAG, id.toString());
            slots.add(new SkillSlot(id, orb, x, y));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private int left() { return (width - BOOK_WIDTH) / 2; }

    private int top() { return (height - BOOK_HEIGHT) / 2; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        graphics.blit(BOOK, left, top, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, 300, 180);
        renderPlayer(graphics, left, top);
        ZSSPlayerData data = ZSSClientData.playerData();
        SkillSlot hovered = slotAt(mouseX, mouseY);
        for (SkillSlot slot : slots) {
            int x = left + slot.x();
            int y = top + slot.y();
            graphics.blit(BOOK, x, y, 281, 0, SLOT_SIZE, SLOT_SIZE, 300, 180);
            boolean disabled = !data.skillEnabled(slot.id());
            if (disabled) {
                disabledIcons.render(graphics, slot.orb(), x + 1, y + 1);
            } else graphics.renderItem(slot.orb(), x + 1, y + 1);
            // Flush the item layer before drawing the unknown-skill tint and selection outline.
            graphics.flush();
            if (data.skillLevel(slot.id()) == 0) {
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x70938171);
            }
            if (slot.id().equals(selected) || slot == hovered) {
                graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE,
                        slot.id().equals(selected) ? 0xFFFFE080 : 0xFFFFFFFF);
            }
        }
        String tokens = Integer.toString(minecraft.player.getInventory().countItem(ZSSRegistries.getItem("skulltula_token")));
        graphics.drawString(font, tokens, left + 128 - font.width(tokens), top + 159, TEXT_COLOR, false);
        renderDescription(graphics, data, left, top);
        if (hovered != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(skillName(hovered.id()));
            tooltip.add(text.get("screen.zeldaswordskills_remastered.skill_level",
                    data.skillLevel(hovered.id()), data.skillMaximum(hovered.id())));
            if (hovered.id().equals(ZSSContentIds.BONUS_HEART)) tooltip.add(text.get(TEXT + "heart_always_enabled"));
            else {
                tooltip.add(text.get(TEXT + (data.skillEnabled(hovered.id()) ? "double_click_disable" : "double_click_enable")));
                if (data.skillLevel(hovered.id()) == 0) tooltip.add(text.get(TEXT + "unknown"));
            }
            graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderPlayer(GuiGraphics graphics, int left, int top) {
        graphics.fill(left + 47, top + 40, left + 101, top + 112, 0xFF503020);
        graphics.fill(left + 48, top + 41, left + 100, top + 111, 0xFFCBB694);
        if (minecraft != null && minecraft.player != null) {
            graphics.enableScissor(left + 48, top + 41, left + 100, top + 111);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    left + 74, top + 105, 30, 0.0F, 0.0F, minecraft.player);
            graphics.disableScissor();
        }
    }

    private void renderDescription(GuiGraphics graphics, ZSSPlayerData data, int left, int top) {
        Component heading = selected == null ? text.get(TEXT + "description") : skillName(selected);
        List<FormattedCharSequence> titleLines = font.split(heading, BODY_WIDTH);
        int y = 38;
        for (FormattedCharSequence line : titleLines) {
            graphics.drawString(font, line, left + BODY_X, top + y, TEXT_COLOR, false);
            y += font.lineHeight;
        }
        graphics.fill(left + BODY_X, top + y + 1, left + 259, top + y + 2, 0xFF94816A);
        bodyTop = y + 5;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (selected == null) {
            addParagraph(lines, text.get(TEXT + "introduction"));
        } else {
            addParagraph(lines, text.get(TEXT + "summary"));
            addParagraph(lines, text.get("screen.zeldaswordskills_remastered.skill_level",
                    data.skillLevel(selected), data.skillMaximum(selected)));
            if (selected.equals(ZSSContentIds.SWORD_BASIC)) {
                int level = Math.max(1, data.skillLevel(selected));
                addParagraph(lines, text.get(TEXT + "basic_values",
                        zeldaswordskills_remastered.combat.BasicSwordSkill.comboMaximum(level),
                        zeldaswordskills_remastered.combat.BasicSwordSkill.comboDuration(level),
                        zeldaswordskills_remastered.combat.BasicSwordSkill.damageBreakThreshold(level)));
            }
            if (data.skillLevel(selected) == 0) {
                addParagraph(lines, text.get(TEXT + "unknown"));
            }
            if (!data.skillEnabled(selected)) addParagraph(lines, text.get(TEXT + "disabled"));
            lines.add(FormattedCharSequence.EMPTY);
            addParagraph(lines, text.get(TEXT + "activation"));
            addParagraph(lines, text.get(TEXT + "activation." + selected.getPath(),
                    ZSSKeyMappings.TARGET.getTranslatedKeyMessage()));
            lines.add(FormattedCharSequence.EMPTY);
            addParagraph(lines, text.get(TEXT + "description"));
            addParagraph(lines, text.get(TEXT + "description." + selected.getPath()));
        }
        int bodyHeight = BODY_BOTTOM - bodyTop;
        maxScroll = Math.max(0, lines.size() * font.lineHeight - bodyHeight);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        graphics.enableScissor(left + BODY_X, top + bodyTop, left + BODY_X + BODY_WIDTH, top + BODY_BOTTOM);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = bodyTop + i * font.lineHeight - scroll;
            if (lineY + font.lineHeight > bodyTop && lineY < BODY_BOTTOM) {
                graphics.drawString(font, lines.get(i), left + BODY_X, top + lineY, TEXT_COLOR, false);
            }
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            thumbHeight = Math.max(7, bodyHeight * bodyHeight / (bodyHeight + maxScroll));
            int thumbY = top + bodyTop + Math.round((float) scroll / maxScroll * (bodyHeight - thumbHeight));
            graphics.fill(left + 260, top + bodyTop, left + 261, top + BODY_BOTTOM, 0xFF94816A);
            graphics.fill(left + 259, thumbY, left + 262, thumbY + thumbHeight, 0xFF79583F);
        }
    }

    /** Resolve the translation first so explicit resource newlines become layout lines. */
    private void addParagraph(List<FormattedCharSequence> lines, Component text) {
        String[] paragraphs = text.getString().replace("\r\n", "\n").split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(font.split(Component.literal(paragraph), BODY_WIDTH));
            }
        }
    }

    private Component skillName(ResourceLocation id) {
        return text.get(TEXT + "skill_name." + id.getPath());
    }

    private SkillSlot slotAt(double mouseX, double mouseY) {
        double x = mouseX - left();
        double y = mouseY - top();
        for (SkillSlot slot : slots) {
            if (x >= slot.x() && x < slot.x() + SLOT_SIZE && y >= slot.y() && y < slot.y() + SLOT_SIZE) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) lastClicked = null;
        if (button == 0) {
            SkillSlot slot = slotAt(mouseX, mouseY);
            if (slot != null) {
                long now = Util.getMillis();
                boolean doubleClick = slot.id().equals(lastClicked) && now - lastClickTime < 250L;
                if (doubleClick) {
                    selected = slot.id();
                    if (!slot.id().equals(ZSSContentIds.BONUS_HEART) && ZSSClientData.playerData().skillLevel(slot.id()) > 0) {
                        ZSSNetwork.toggleSkill(slot.id());
                    }
                    lastClicked = null;
                } else {
                    selected = slot.id().equals(selected) ? null : slot.id();
                    lastClicked = slot.id();
                    lastClickTime = now;
                }
                scroll = 0;
                draggingScroll = false;
                return true;
            }
            lastClicked = null;
            if (maxScroll > 0 && mouseX >= left() + 258 && mouseX < left() + 264
                    && mouseY >= top() + bodyTop && mouseY < top() + BODY_BOTTOM) {
                int trackHeight = BODY_BOTTOM - bodyTop - thumbHeight;
                double thumbY = top() + bodyTop + (double) scroll / maxScroll * trackHeight;
                dragOffset = mouseY >= thumbY && mouseY < thumbY + thumbHeight ? mouseY - thumbY : thumbHeight / 2.0;
                draggingScroll = true;
                dragScroll(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void dragScroll(double mouseY) {
        int trackHeight = BODY_BOTTOM - bodyTop - thumbHeight;
        if (trackHeight > 0) {
            scroll = Mth.clamp((int) Math.round((mouseY - top() - bodyTop - dragOffset) / trackHeight * maxScroll), 0, maxScroll);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScroll) {
            dragScroll(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left() + BODY_X && mouseX < left() + 264
                && mouseY >= top() + bodyTop && mouseY < top() + BODY_BOTTOM) {
            scroll = Mth.clamp(scroll - (int) (delta * font.lineHeight * 3), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ZSSKeyMappings.SKILL_BOOK.matches(keyCode, scanCode)
                || minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Mth.clamp(scroll + (keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1)
                    * (BODY_BOTTOM - bodyTop), 0, maxScroll);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
            scroll = keyCode == GLFW.GLFW_KEY_HOME ? 0 : maxScroll;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        disabledIcons.close();
        super.removed();
    }

    private record SkillSlot(ResourceLocation id, ItemStack orb, int x, int y) { }
}
