package zeldaswordskills_remastered.entity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.player.Player;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.block.entity.SecretRoomCore;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Selects a short Navi line from the supplied dialogue groups for the current world context. */
public final class NaviDialogueService {
    private static final String LAST_COMBAT_KEY = "zss_navi_last_combat";
    private static final long COMBAT_WINDOW_TICKS = 20L * 15L;

    private static final Map<Integer, List<String>> GROUPS = loadGroups();
    private static final List<String> GENERAL = combine(1, 3, 6);

    private NaviDialogueService() {}

    public static void markCombat(ServerPlayer player) {
        player.getPersistentData().putLong(LAST_COMBAT_KEY, player.level().getGameTime());
    }

    public static boolean interact(ServerPlayer player, NaviCreature navi) {
        if (navi.ownerUuid() == null || !navi.ownerUuid().equals(player.getUUID())) return false;
        List<String> pool = recentlyFought(player) ? group(4)
                : inNaviStructure(player) ? group(5)
                : specialBiome(player) ? group(2) : generalPool(player);
        String line = pool.get(player.getRandom().nextInt(pool.size()));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("娜薇：" + line));
        player.playNotifySound(ZSSRegistries.NAVI_INTERACT.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        return true;
    }

    private static boolean recentlyFought(ServerPlayer player) {
        long last = player.getPersistentData().getLong(LAST_COMBAT_KEY);
        return last > 0 && player.level().getGameTime() - last <= COMBAT_WINDOW_TICKS;
    }

    private static boolean specialBiome(ServerPlayer player) {
        var biome = player.level().getBiome(player.blockPosition());
        return biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE)
                || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_MOUNTAIN)
                || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)
                || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_NETHER)
                || biome.is(BiomeTags.IS_END);
    }

    private static boolean inNaviStructure(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -8, -16), center.offset(16, 8, 16))) {
            var blockEntity = player.level().getBlockEntity(pos);
            if (blockEntity instanceof StageNineBlockEntities.DungeonCore || blockEntity instanceof SecretRoomCore) return true;
        }
        return false;
    }

    private static List<String> generalPool(ServerPlayer player) {
        if (player.level().isRaining() || player.level().isThundering()) return group(3);
        long time = player.level().getDayTime() % 24000L;
        if (time < 1500L || time >= 12000L) return group(1);
        return GENERAL;
    }

    private static List<String> combine(int... groups) {
        List<String> combined = new ArrayList<>();
        for (int group : groups) combined.addAll(group(group));
        return List.copyOf(combined);
    }

    private static List<String> group(int number) {
        List<String> values = GROUPS.get(number);
        if (values == null || values.isEmpty()) throw new IllegalStateException("Missing Navi dialogue group " + number);
        return values;
    }

    private static Map<Integer, List<String>> loadGroups() {
        Map<Integer, List<String>> groups = new HashMap<>();
        String path = "/data/zeldaswordskills_remastered/navi_dialogue.md";
        try (var stream = Objects.requireNonNull(NaviDialogueService.class.getResourceAsStream(path), path);
                var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            int current = 0;
            for (String line; (line = reader.readLine()) != null;) {
                if (line.startsWith("## ")) {
                    int end = line.indexOf('.');
                    current = end > 3 ? Integer.parseInt(line.substring(3, end)) : 0;
                } else if (current > 0) {
                    int separator = line.indexOf(". ");
                    if (separator > 0) groups.computeIfAbsent(current, ignored -> new ArrayList<>())
                            .add(line.substring(separator + 2));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load Navi dialogue: " + path, exception);
        }
        groups.replaceAll((number, lines) -> List.copyOf(lines));
        return Map.copyOf(groups);
    }
}
