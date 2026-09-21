package zeldaswordskills_remastered.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Called by Forge's bundled ASM coremod API at the vanilla integration points. */
public final class RoyalCastleHooks {
    private RoyalCastleHooks() { }

    public static boolean skipFeatures(WorldGenLevel level, BlockPos origin) {
        RoyalCastleSite site = RoyalCastleWorldData.get(level.getLevel());
        if (site == null) return false;
        var bounds = site.bounds();
        // Features such as dirt ores and trees write into adjacent chunks. Reserve
        // their reach too, so later decoration cannot overwrite an earlier tile.
        return bounds.intersects(origin.getX() - 32, origin.getZ() - 32,
                origin.getX() + 47, origin.getZ() + 47);
    }

    public static StructureStart filterStart(StructureStart start, RandomState random) {
        RoyalCastleSite site = RoyalCastleWorldData.get(random);
        return site != null && start.isValid() && !(start.getStructure() instanceof RoyalCastleStructure)
                && site.excludes(start.getBoundingBox()) ? StructureStart.INVALID_START : start;
    }

    public static Pair<BlockPos, Holder<Structure>> locate(Pair<BlockPos, Holder<Structure>> original,
                                                          ServerLevel level, HolderSet<Structure> structures, BlockPos from) {
        RoyalCastleSite site = RoyalCastleWorldData.get(level);
        if (site == null) return original;
        for (Holder<Structure> structure : structures) {
            if (structure.value() instanceof RoyalCastleStructure) {
                BlockPos entrance = site.origin().offset(107, 5, 0);
                if (original == null || entrance.distSqr(from) < original.getFirst().distSqr(from)) {
                    return Pair.of(entrance, structure);
                }
            }
        }
        return original;
    }
}
