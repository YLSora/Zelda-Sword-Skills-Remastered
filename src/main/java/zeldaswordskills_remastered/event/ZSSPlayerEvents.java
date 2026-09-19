package zeldaswordskills_remastered.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.progression.AcquisitionService;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.entity.NaviService;
import zeldaswordskills_remastered.entity.NaviCreature;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSPlayerEvents {
    private ZSSPlayerEvents() {
    }

    @SubscribeEvent
    public static void itemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getCrafting().getItem() instanceof zeldaswordskills_remastered.item.InstrumentItem) {
            ZSSAdvancementService.instrumentCrafted(player);
        }
    }

    /**
     * Copies the previous player's data onto the respawned one.
     * <p>
     * Vanilla removes the old player before posting this event, which leaves its capabilities
     * invalid; {@code reviveCaps()} is what makes them readable again for this single lookup, and it
     * must bracket the read. It only works because the player data provider never invalidates its
     * own optional - otherwise the source here is empty and a respawn silently loses magic, skills,
     * songs and quests.
     */
    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        NaviService.copyIdentity(event.getOriginal(), event.getEntity());
        event.getOriginal().reviveCaps();
        ZSSCapabilities.get(event.getOriginal()).ifPresent(source -> ZSSCapabilities.get(event.getEntity()).ifPresent(target -> {
            target.copyFrom(source);
            if (event.isWasDeath() && ZSSConfig.SERVER.resetSkillsOnDeath.get()) target.resetLearnedSkills();
        }));
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event);
        if (event.getEntity() instanceof ServerPlayer player) NaviService.onLogin(player);
    }

    @SubscribeEvent
    public static void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        sync(event);
        if (event.getEntity() instanceof ServerPlayer player) NaviService.onRespawn(player);
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event);
        if (event.getEntity() instanceof ServerPlayer player) NaviService.onDimensionChange(player);
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestService.returnEscrowOnLogout(player);
            NaviService.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void tickNavi(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) NaviService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void naviJoined(EntityJoinLevelEvent event) {
        // Companions are reconstructed exclusively from player state, never from chunk NBT.
        if (!event.getLevel().isClientSide && event.loadedFromDisk() && event.getEntity() instanceof NaviCreature) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void serverStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        NaviService.onServerStopping(event.getServer());
    }

    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ZSSCapabilities.get(player).ifPresent(data -> data.combat().reset());
            ZSSCapabilities.get(player).ifPresent(data -> AdvancedSwordSkills.tick(player, data));
            ZSSCapabilities.get(player).ifPresent(data -> QuestService.initialize(player, data));
            ZSSCapabilities.get(player).ifPresent(data -> AcquisitionService.initializePlayer(player, data));
            ZSSCapabilities.get(player).ifPresent(data -> ZSSAdvancementService.initialize(player, data));
            ZSSNetwork.syncPlayerData(player);
            ZSSCapabilities.get(player).ifPresent(data -> ZSSNetwork.syncCombatState(player, data.combat()));
        }
    }
}
