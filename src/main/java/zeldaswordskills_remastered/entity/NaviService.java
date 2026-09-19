package zeldaswordskills_remastered.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.UUID;

/** The player is the sole persistent authority; world entities are disposable representations. */
public final class NaviService {
    public static final String ACTIVE_KEY = "zss_navi_active";
    private NaviService() {}

    public static boolean isActive(ServerPlayer player) {
        return NaviCreature.savedNaviId(player).isPresent() && player.getPersistentData().getBoolean(ACTIVE_KEY);
    }

    public static void copyIdentity(Player original, Player replacement) {
        NaviCreature.clearStoredNaviId(replacement);
        replacement.getPersistentData().remove(ACTIVE_KEY);
        NaviCreature.savedNaviId(original).ifPresent(id -> {
            replacement.getPersistentData().putUUID(NaviCreature.OWNER_NAVI_KEY, id);
            replacement.getPersistentData().putBoolean(ACTIVE_KEY, original.getPersistentData().getBoolean(ACTIVE_KEY));
        });
    }

    public static boolean hasLoadedEntity(ServerPlayer player) {
        UUID id = NaviCreature.savedNaviId(player).orElse(null);
        return id != null && NaviCreature.findOwned(player.serverLevel(), id).isPresent();
    }

    @Nullable
    public static NaviCreature summon(ServerPlayer player) {
        if (!player.isAlive()) return null;
        UUID id = NaviCreature.storedNaviId(player);
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof NaviCreature navi && (level != player.serverLevel() || !navi.isAlive())) navi.discard();
        }
        NaviCreature navi = NaviCreature.spawnFor(player, player.serverLevel());
        if (navi != null) markActive(player, true);
        return navi;
    }

    public static void markActive(ServerPlayer player, boolean active) { player.getPersistentData().putBoolean(ACTIVE_KEY, active); }

    public static boolean call(ServerPlayer player) {
        NaviCreature navi = summon(player);
        return navi != null && navi.relocateNear(player);
    }

    public static void despawn(ServerPlayer player) {
        UUID id = NaviCreature.savedNaviId(player).orElse(null);
        // Snapshot before removal: entity callbacks mutate the level's entity index immediately.
        for (ServerLevel level : player.getServer().getAllLevels()) {
            ArrayList<NaviCreature> owned = new ArrayList<>();
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof NaviCreature navi
                        && (player.getUUID().equals(navi.ownerUuid()) || navi.getUUID().equals(id))) owned.add(navi);
            }
            owned.forEach(Entity::discard);
        }
    }

    public static void restore(ServerPlayer player) {
        if (isActive(player) && player.isAlive()) summon(player);
        else despawn(player);
    }

    /** Rebuilds companions lost to chunk unloading or removal, without saving them into chunks. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isActive(player)) restore(player);
        }
    }

    public static void onLogin(ServerPlayer player) { restore(player); }
    public static void onLogout(ServerPlayer player) { despawn(player); }
    public static void onDimensionChange(ServerPlayer player) { restore(player); }
    public static void onRespawn(ServerPlayer player) { restore(player); }
    public static void onServerStopping(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) despawn(player);
    }

    public static void forget(ServerPlayer player) {
        despawn(player);
        NaviCreature.clearStoredNaviId(player);
        player.getPersistentData().remove(ACTIVE_KEY);
    }
}
