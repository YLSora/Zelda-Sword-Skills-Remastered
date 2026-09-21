package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

/** Searches noise data only: candidate evaluation never loads or generates chunks. */
public final class RoyalCastleLocator {
    private static final int MIN_RADIUS = 10_000;
    private static final int MAX_RADIUS = 50_000;
    private static final int DIAMETER = 6250;
    private static final long CANDIDATES = (long) DIAMETER * DIAMETER;

    private RoyalCastleLocator() { }

    public static Optional<RoyalCastleSite> find(long seed, ChunkGenerator generator, RandomState random,
                                                LevelHeightAccessor height, Predicate<RoyalCastleSite> available) {
        if (generator.getBiomeSource().possibleBiomes().stream().noneMatch(b -> b.is(Biomes.PLAINS))) {
            return Optional.empty();
        }
        if (generator instanceof FlatLevelSource) {
            int surface = generator.getBaseHeight(0, 0, Heightmap.Types.OCEAN_FLOOR_WG, height, random) - 1;
            if (surface < generator.getSeaLevel() || surface - 36 <= height.getMinBuildHeight()
                    || surface + 92 >= height.getMaxBuildHeight()) return Optional.empty();
        }
        long first = Math.floorMod(seed ^ 0x524f59414cL, CANDIDATES);
        // Coprime to 6250^2: visit each chunk once, with a seed-dependent starting point.
        for (long i = 0; i < CANDIDATES; i++) {
            long index = (first + i * 1_048_573L) % CANDIDATES;
            int x = ((int) (index % DIAMETER) - DIAMETER / 2) * 16 - 112;
            int z = ((int) (index / DIAMETER) - DIAMETER / 2) * 16 - 112;
            if (!inRing(x, z)) continue;
            if (!plainsPlane(generator, random, x - 16, z - 16, x + 231, z + 231, 64, 32)) continue;
            if (!plainsPlane(generator, random, x - 16, z - 16, x + 231, z + 231, 64, 4)) continue;
            int[] heights = new int[9];
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            int count = 0;
            boolean steep = false;
            for (int dx : new int[]{-16, 108, 231}) {
                if (steep) break;
                for (int dz : new int[]{-16, 108, 231}) {
                    int top = generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, height, random) - 1;
                    low = Math.min(low, top);
                    high = Math.max(high, top);
                    heights[count++] = top;
                    if (low < generator.getSeaLevel() || high - low > 12) { steep = true; break; }
                }
            }
            if (steep) continue;
            Arrays.sort(heights, 0, count);
            int y = heights[count / 2] - 4;
            if (y - 32 <= height.getMinBuildHeight() || y + 96 >= height.getMaxBuildHeight()) continue;
            RoyalCastleSite site = new RoyalCastleSite(x, y, z);
            if (!available.test(site)) continue;
            // Include all eight possible Voronoi biome corners, across the entire
            // height of the castle. This is stricter than testing its center/surface.
            int minX = QuartPos.fromBlock(x - 18);
            int maxX = QuartPos.fromBlock(x + 229) + 1;
            int minZ = QuartPos.fromBlock(z - 18);
            int maxZ = QuartPos.fromBlock(z + 229) + 1;
            boolean valid = true;
            for (int qy = QuartPos.fromBlock(Math.min(y, low) - 2);
                 qy <= QuartPos.fromBlock(y + 93) + 1 && valid; qy++) {
                for (int qx = minX; qx <= maxX && valid; qx++) {
                    for (int qz = minZ; qz <= maxZ; qz++) {
                        if (!generator.getBiomeSource().getNoiseBiome(qx, qy, qz, random.sampler()).is(Biomes.PLAINS)) {
                            valid = false;
                            break;
                        }
                    }
                }
            }
            if (valid) return Optional.of(site);
        }
        return Optional.empty();
    }

    public static boolean inRing(int x, int z) {
        int minX = x - RoyalCastleSite.BLEND;
        int minZ = z - RoyalCastleSite.BLEND;
        int maxX = x + RoyalCastleSite.SIZE + RoyalCastleSite.BLEND - 1;
        int maxZ = z + RoyalCastleSite.SIZE + RoyalCastleSite.BLEND - 1;
        int nearX = minX > 0 ? minX : Math.min(0, maxX);
        int nearZ = minZ > 0 ? minZ : Math.min(0, maxZ);
        int farX = Math.max(Math.abs(minX), Math.abs(maxX));
        int farZ = Math.max(Math.abs(minZ), Math.abs(maxZ));
        return (long) nearX * nearX + (long) nearZ * nearZ >= (long) MIN_RADIUS * MIN_RADIUS
                && (long) farX * farX + (long) farZ * farZ <= (long) MAX_RADIUS * MAX_RADIUS;
    }

    private static boolean plainsPlane(ChunkGenerator generator, RandomState random,
                                       int minX, int minZ, int maxX, int maxZ, int y, int step) {
        for (int x = minX; x <= maxX; x += step) for (int z = minZ; z <= maxZ; z += step) {
            if (!generator.getBiomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2, random.sampler()).is(Biomes.PLAINS)) return false;
        }
        return true;
    }
}
