package zeldaswordskills_remastered.worldgen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.config.ZSSConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class RoyalCastleWorldData extends SavedData {
    private static final String NAME = "zss_royal_castle";
    private static final Map<RandomState, RoyalCastleSite> SITES = Collections.synchronizedMap(new WeakHashMap<>());
    private final Optional<RoyalCastleSite> site;

    private RoyalCastleWorldData(Optional<RoyalCastleSite> site) { this.site = site; }

    public static RoyalCastleWorldData load(CompoundTag tag) {
        return new RoyalCastleWorldData(tag.contains("Site") ? Optional.of(RoyalCastleSite.load(tag.getCompound("Site"))) : Optional.empty());
    }

    @Override public CompoundTag save(CompoundTag tag) {
        site.ifPresent(value -> tag.put("Site", value.save()));
        return tag;
    }

    public static RoyalCastleSite get(RandomState random) { return SITES.get(random); }

    public static RoyalCastleSite get(ServerLevel level) {
        return level.dimension().equals(Level.OVERWORLD) ? get(level.getChunkSource().randomState()) : null;
    }

    @SubscribeEvent
    public static void loadLevel(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        var random = level.getChunkSource().randomState();
        RoyalCastleWorldData data = level.getDataStorage().get(RoyalCastleWorldData::load, NAME);
        if (data == null) {
            if (!level.structureManager().shouldGenerateStructures() || !ZSSConfig.SERVER.generateStructures.get()) return;
            ZeldaSwordSkills_Remastered.LOGGER.info("Searching for the unique royal castle plains site...");
            Path regions = level.getServer().getWorldPath(LevelResource.ROOT).resolve("region");
            var found = RoyalCastleLocator.find(level.getSeed(), level.getChunkSource().getGenerator(), random,
                    level, candidate -> untouched(candidate, regions));
            data = new RoyalCastleWorldData(found);
            data.setDirty();
            level.getDataStorage().set(NAME, data);
        }
        data.site.ifPresentOrElse(site -> {
            SITES.put(random, site);
            ZeldaSwordSkills_Remastered.LOGGER.info("Royal castle reserved at {}", site.origin());
        }, () -> ZeldaSwordSkills_Remastered.LOGGER.warn("No untouched plains site in the 10000-50000 royal castle ring; castle generation is unavailable in this world."));
        // Persist before any terrain workers start, including a completed search with no site.
        level.getDataStorage().save();
    }

    @SubscribeEvent
    public static void unloadLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) SITES.remove(level.getChunkSource().randomState());
    }

    private static boolean untouched(RoyalCastleSite site, Path regions) {
        // Existing worlds are supported only on untouched land. Include the vanilla
        // eight-chunk structure-reference reach so saved neighboring starts cannot intrude.
        var box = site.bounds();
        int margin = RoyalCastleSite.CLEARANCE + 128;
        for (int x = (box.minX() - margin) >> 9; x <= (box.maxX() + margin) >> 9; x++) {
            for (int z = (box.minZ() - margin) >> 9; z <= (box.maxZ() + margin) >> 9; z++) {
                if (Files.exists(regions.resolve("r." + x + "." + z + ".mca"))) return false;
            }
        }
        return true;
    }
}
