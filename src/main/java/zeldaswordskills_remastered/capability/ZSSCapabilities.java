package zeldaswordskills_remastered.capability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSCapabilities {
    public static final Capability<ZSSPlayerData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation PLAYER_DATA_ID = ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "player_data");

    private ZSSCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(ZSSPlayerData.class);
    }

    @SubscribeEvent
    public static void attachPlayerData(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            // Deliberately no invalidating listener. Forge runs those listeners from
            // Entity#invalidateCaps, which vanilla calls on the old player *before* it posts
            // PlayerEvent.Clone; a listener here would therefore destroy the very data the clone
            // handler has to copy, and LazyOptional cannot be revived afterwards. The entity's own
            // capability flag already hides this data once the player is removed, so the listener
            // bought nothing and cost every death the player's magic, skills, songs and quests.
            event.addCapability(PLAYER_DATA_ID, new ZSSPlayerDataProvider());
        }
    }

    public static LazyOptional<ZSSPlayerData> get(Player player) {
        return player.getCapability(PLAYER_DATA);
    }
}
