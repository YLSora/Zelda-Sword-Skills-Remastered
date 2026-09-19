package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ZSSKeyMappings {
    public static final String CATEGORY = "key.categories.zeldaswordskills_remastered";
    public static final KeyMapping TARGET = new KeyMapping("key.zeldaswordskills_remastered.target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping CLEAR_TARGET = new KeyMapping("key.zeldaswordskills_remastered.clear_target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping SKILL_BOOK = new KeyMapping("key.zeldaswordskills_remastered.skill_book", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);

    private ZSSKeyMappings() {
    }
}
