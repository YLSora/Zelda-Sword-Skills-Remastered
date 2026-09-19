package zeldaswordskills_remastered.client;

import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

/** Keeps the Zelda advancement tab title separate from its root advancement title. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class ZSSAdvancementTabs {
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath(
            ZeldaSwordSkills_Remastered.MOD_ID, "adventure_begins");
    private static final Component TITLE = Component.translatable(
            "advancements.zeldaswordskills_remastered.adventure_begins.tab");

    private ZSSAdvancementTabs() { }

    @SubscribeEvent
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof AdvancementsScreen screen) {
            screen.tabs.forEach((root, tab) -> {
                if (root.getId().equals(ROOT)) tab.title = TITLE;
            });
        }
    }
}
