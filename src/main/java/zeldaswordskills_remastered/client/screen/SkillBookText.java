package zeldaswordskills_remastered.client.screen;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

/** Reads the skill book's own localized resource instead of copying its fields into lang files. */
final class SkillBookText {
    private final Map<String, String> values;

    private SkillBookText(Map<String, String> values) {
        this.values = values;
    }

    static SkillBookText load() {
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft.getLanguageManager().getSelected();
        ResourceLocation selected = ResourceLocation.fromNamespaceAndPath(
                ZeldaSwordSkills_Remastered.MOD_ID, "skill_book/" + language + ".json");
        ResourceLocation resource = minecraft.getResourceManager().getResource(selected).isPresent()
                ? selected
                : ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "skill_book/en_us.json");
        Map<String, String> values = new LinkedHashMap<>();
        try (Reader reader = minecraft.getResourceManager().openAsReader(resource)) {
            GsonHelper.parse(reader).entrySet().forEach(entry ->
                    values.put(entry.getKey(), GsonHelper.convertToString(entry.getValue(), entry.getKey())));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load skill book text: " + resource, exception);
        }
        return new SkillBookText(Map.copyOf(values));
    }

    Component get(String key, Object... arguments) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing skill book field: " + key);
        if (arguments.length == 0) return Component.literal(value);
        String[] plain = new String[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            plain[i] = arguments[i] instanceof Component component
                    ? component.getString() : String.valueOf(arguments[i]);
            value = value.replace("%" + (i + 1) + "$s", plain[i]);
        }
        for (String argument : plain) {
            int placeholder = value.indexOf("%s");
            if (placeholder >= 0) value = value.substring(0, placeholder) + argument + value.substring(placeholder + 2);
        }
        return Component.literal(value);
    }
}
