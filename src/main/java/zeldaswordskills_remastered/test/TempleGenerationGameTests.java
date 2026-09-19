package zeldaswordskills_remastered.test;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.data.DungeonStructureDataProvider;
import zeldaswordskills_remastered.worldgen.DungeonType;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class TempleGenerationGameTests {
    private TempleGenerationGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssTempleGeneration", timeoutTicks = 200)
    public static void allTemplesGenerateInTheirDimensions(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var structures = registries.registryOrThrow(Registries.STRUCTURE);
        var sets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        for (DungeonType type : DungeonType.values()) {
            var level = helper.getLevel().getServer().getLevel(type == DungeonType.FIRE ? Level.NETHER : type == DungeonType.END ? Level.END : Level.OVERWORLD);
            var generator = (NoiseBasedChunkGenerator) (type == DungeonType.FIRE || type == DungeonType.END
                    ? level.getChunkSource().getGenerator() : registries.registryOrThrow(Registries.WORLD_PRESET)
                    .get(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL).createWorldDimensions().overworld());
            var structure = structures.get(DungeonStructureDataProvider.structureId(type));
            var setId = ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID,
                    type == DungeonType.FIRE ? "nether_boss_dungeons" : type == DungeonType.END ? "end_boss_dungeons" : "boss_dungeons");
            var placement = (RandomSpreadStructurePlacement) sets.get(setId).placement();
            for (long seed : new long[]{0L, 12345L, 987654321L}) {
                var random = RandomState.create(generator.generatorSettings().value(),
                        registries.registryOrThrow(Registries.NOISE).asLookup(), seed);
                var state = ChunkGeneratorStructureState.createForNormal(random, seed, generator.getBiomeSource(), sets.asLookup());
                boolean generated = false;
                // Run placement, frequency rejection, biome selection and actual Jigsaw generation.
                for (int region = 0; region < 1024 && !generated; region++) {
                    ChunkPos chunk = placement.getPotentialStructureChunk(seed,
                            (region % 32 + 2) * placement.spacing(), (region / 32 - 16) * placement.spacing());
                    if (!placement.isStructureChunk(state, chunk.x, chunk.z)) continue;
                    var start = structure.generate(registries, generator, generator.getBiomeSource(), random,
                            level.getStructureManager(), seed, chunk, 0, level, structure.biomes()::contains);
                    if (!start.isValid()) continue;
                    var box = start.getPieces().get(0).getBoundingBox();
                    if (type == DungeonType.FIRE) {
                        helper.assertTrue(box.minY() == 128 && box.maxY() < level.getMaxBuildHeight(),
                                "Fire template intersects the Nether bedrock roof or exceeds build height");
                    } else if (type == DungeonType.END) {
                        int surface = generator.getFirstFreeHeight((box.minX()+box.maxX())/2, (box.minZ()+box.maxZ())/2,
                                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,random);
                        helper.assertTrue(box.minY() > level.getMinBuildHeight() && box.minY() == surface - 1
                                        && (Math.abs(box.minX()) > 1024 || Math.abs(box.minZ()) > 1024),
                                "Wind template terrain mismatch: " + box + " surface=" + surface);
                    }
                    ZeldaSwordSkills_Remastered.LOGGER.info("Temple generation verified: {} seed={} floor=({}, {}, {})",
                            type, seed, box.minX(), box.minY(), box.minZ());
                    generated = true;
                }
                helper.assertTrue(generated, "No valid rare " + type + " temple generated for seed " + seed);
                if (type == DungeonType.END) {
                    helper.assertTrue(!structure.generate(registries, generator, generator.getBiomeSource(), random,
                                    level.getStructureManager(), seed, new ChunkPos(0, 0), 0, level,
                                    structure.biomes()::contains).isValid(),
                            "Wind temple must reject the central End island");
                }
            }
        }
        helper.succeed();
    }
}
