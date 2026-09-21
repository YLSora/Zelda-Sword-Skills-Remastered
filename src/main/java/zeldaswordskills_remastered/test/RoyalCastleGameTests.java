package zeldaswordskills_remastered.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.event.RoyalCastleSpawnEvents;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.*;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@GameTestHolder("zss_royal_castle_tests")
@PrefixGameTestTemplate(false)
public final class RoyalCastleGameTests {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "royal_castle");

    @GameTest(template = "zssgametests.empty", batch = "royalCastleNoise", timeoutTicks = 400)
    public static void plainsSelectionAcrossSeeds(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var generator = (NoiseBasedChunkGenerator) registries.registryOrThrow(Registries.WORLD_PRESET)
                .get(WorldPresets.NORMAL).createWorldDimensions().overworld();
        for (long seed : new long[]{0, 12345, 987654321}) {
            long started = System.nanoTime();
            var random = RandomState.create(generator.generatorSettings().value(), registries.registryOrThrow(Registries.NOISE).asLookup(), seed);
            var site = RoyalCastleLocator.find(seed, generator, random, level, s -> true).orElseThrow();
            helper.assertTrue(RoyalCastleLocator.inRing(site.x(), site.z()), "Estate extends outside the allowed ring");
            var biomes = new BiomeManager((x, y, z) -> generator.getBiomeSource().getNoiseBiome(x, y, z, random.sampler()), BiomeManager.obfuscateSeed(seed));
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = site.x() - 16; x <= site.x() + 231; x++) for (int z = site.z() - 16; z <= site.z() + 231; z++) {
                for (int y : new int[]{site.y(), site.y() + 4, site.y() + 48, site.y() + 95}) {
                    helper.assertTrue(biomes.getBiome(pos.set(x, y, z)).is(Biomes.PLAINS), "Biome border crosses estate: " + pos);
                }
            }
            helper.assertTrue(site.equals(RoyalCastleSite.load(site.save())), "Site failed NBT round trip");
            ZeldaSwordSkills_Remastered.LOGGER.info("Royal castle noise verified: seed={} site={} elapsedMs={}", seed, site, (System.nanoTime() - started) / 1_000_000);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", batch = "royalCastleWorld", timeoutTicks = 600)
    public static void naturalGenerationPersistenceAndProtection(GameTestHelper helper) {
        var level = helper.getLevel();
        var site = RoyalCastleWorldData.get(level);
        helper.assertTrue(site != null, "World load did not reserve a castle");
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var holder = registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, ID));
        var generator = level.getChunkSource().getGenerator();
        var located = generator.findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 100, false);
        helper.assertTrue(located != null && located.getFirst().equals(site.origin().offset(107, 5, 0)), "Vanilla /locate hook is inactive");
        var placement = ZSSRegistries.ROYAL_CASTLE_PLACEMENT.get();
        helper.assertTrue(placement != null, "Placement is not registered");
        var structurePlacement = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ID).placement();
        var state = level.getChunkSource().getGeneratorState();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            helper.assertTrue(structurePlacement.isStructureChunk(state, site.startChunk().x + dx, site.startChunk().z + dz)
                    == (dx == 0 && dz == 0), "More than one castle start is possible");
        }
        var serialized = new CompoundTag();
        serialized.put("Site", site.save());
        helper.assertTrue(RoyalCastleWorldData.load(serialized).save(new CompoundTag()).equals(serialized), "Saved location changed during reload");
        verifyExclusionHook(helper, site);
        verifySpawns(helper, site);

        // Load the boundary first, in a shuffled order, exercising structure references
        // in all eight-chunk directions before the center chunk is populated.
        List<ChunkPos> chunks = new ArrayList<>();
        var bounds = site.bounds();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) chunks.add(new ChunkPos(x, z));
        }
        Collections.shuffle(chunks, new java.util.Random(1977));
        for (ChunkPos chunk : chunks) level.getChunk(chunk.x, chunk.z);
        var start = level.getChunk(site.startChunk().x, site.startChunk().z).getStartForStructure(holder.value());
        helper.assertTrue(start != null && start.isValid(), "Natural chunk generation did not create the castle");
        var context = StructurePieceSerializationContext.fromLevel(level);
        var reloaded = StructureStart.loadStaticStart(context, start.createTag(context, site.startChunk()), level.getSeed());
        helper.assertTrue(reloaded != null && reloaded.getBoundingBox().equals(start.getBoundingBox()), "Structure start failed reload");
        int starts = 0;
        for (ChunkPos chunk : chunks) {
            for (var entry : level.getChunk(chunk.x, chunk.z).getAllStarts().entrySet()) {
                if (!entry.getValue().isValid()) continue;
                if (entry.getKey() instanceof RoyalCastleStructure) starts++;
                else helper.assertTrue(!site.excludes(entry.getValue().getBoundingBox()), "A neighboring structure intruded");
            }
        }
        helper.assertTrue(starts == 1, "Expected exactly one persisted castle start");
        int checked = 0;
        int blockEntities = 0;
        int clearedAir = 0;
        for (int tx = 0; tx < 14; tx++) for (int tz = 0; tz < 14; tz++) {
            var template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID,
                    "royal_castle/tile_" + tx + "_" + tz)).orElseThrow();
            CompoundTag tag = template.save(new CompoundTag());
            var palette = tag.getList("palette", 10);
            var entries = tag.getList("blocks", 10);
            var occupied = new java.util.BitSet(16 * 16 * 96);
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.getCompound(i);
                var p = entry.getList("pos", 3);
                occupied.set((p.getInt(1) * 16 + p.getInt(2)) * 16 + p.getInt(0));
                BlockPos pos = site.origin().offset(tx * 16 + p.getInt(0), p.getInt(1), tz * 16 + p.getInt(2));
                var expected = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), palette.getCompound(entry.getInt("state")));
                helper.assertTrue(level.getBlockState(pos).equals(expected), "Template mismatch at " + pos + ": " + level.getBlockState(pos) + " expected " + expected);
                if (entry.contains("nbt")) {
                    helper.assertTrue(level.getBlockEntity(pos) != null, "Missing block entity at " + pos);
                    blockEntities++;
                }
                checked++;
            }
            for (int y = 0; y < 96; y++) for (int z = 0; z < template.getSize().getZ(); z++) {
                for (int x = 0; x < template.getSize().getX(); x++) {
                    if (occupied.get((y * 16 + z) * 16 + x)) continue;
                    BlockPos pos = site.origin().offset(tx * 16 + x, y, tz * 16 + z);
                    helper.assertTrue(level.getBlockState(pos).isAir(), "Template air was not cleared at " + pos);
                    clearedAir++;
                }
            }
        }
        helper.assertTrue(checked == 338596 && blockEntities == 30, "Template block/entity counts changed");
        helper.assertTrue(clearedAir == 4140380, "Template air volume changed");
        helper.assertTrue(RoyalCastleWorldData.get(level.getServer().getLevel(Level.NETHER)) == null, "Castle leaked into Nether");
        level.getDataStorage().save();
        ZeldaSwordSkills_Remastered.LOGGER.info("Royal castle world verified: site={} chunks={} blocks={} blockEntities={}", site, chunks.size(), checked, blockEntities);
        helper.succeed();
    }

    private static void verifyExclusionHook(GameTestHelper helper, RoyalCastleSite site) {
        var level = helper.getLevel();
        // A real call to Structure.generate verifies transformed bytecode, not just the helper.
        var settings = new Structure.StructureSettings(HolderSet.direct(level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS)),
                java.util.Map.of(), net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES,
                net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE);
        for (int distance : new int[]{0, 100, 101}) {
            RoyalCastleSite other = new RoyalCastleSite(site.x() + 216 + 32 + distance - 1, site.y(), site.z());
            Structure probe = new Structure(settings) {
                @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
                    return Optional.of(new GenerationStub(other.origin(), builder -> builder.addPiece(new RoyalCastlePiece(other))));
                }
                @Override public StructureType<?> type() { return ZSSRegistries.ROYAL_CASTLE_STRUCTURE.get(); }
            };
            // Account for the probe piece's own 16-block blend band.
            var probeBox = new RoyalCastlePiece(other).getBoundingBox();
            var result = probe.generate(level.registryAccess(), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), other.startChunk(), 0, level, biome -> true);
            helper.assertTrue(result.isValid() == !site.excludes(probeBox), "Structure exclusion transformer failed at distance " + distance);
        }
        int right = site.bounds().maxX();
        helper.assertTrue(site.excludes(new BoundingBox(right + 100, -60, site.z(), right + 100, -50, site.z())), "Underground structure at 100 blocks escaped");
        helper.assertTrue(!site.excludes(new BoundingBox(right + 101, -60, site.z(), right + 101, -50, site.z())), "Structure beyond 100 blocks was canceled");
    }

    private static void verifySpawns(GameTestHelper helper, RoyalCastleSite site) {
        var level = helper.getLevel();
        for (var type : List.of(EntityType.ZOMBIE, EntityType.WOLF, EntityType.BEE, EntityType.PANDA, EntityType.LLAMA,
                EntityType.IRON_GOLEM, EntityType.GOAT, EntityType.DOLPHIN, EntityType.PIG, EntityType.VILLAGER)) {
            Mob mob = (Mob) type.create(level);
            boolean blocked = type != EntityType.PIG && type != EntityType.VILLAGER;
            mob.moveTo(site.x() + 100.5, site.y() + 6, site.z() + 100.5);
            var join = new EntityJoinLevelEvent(mob, level);
            RoyalCastleSpawnEvents.join(join);
            helper.assertTrue(join.isCanceled() == blocked, "Wrong castle spawn rule for " + type);
            var spawn = new MobSpawnEvent.FinalizeSpawn(mob, level, mob.getX(), mob.getY(), mob.getZ(),
                    level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWNER, null, null, null);
            RoyalCastleSpawnEvents.finalizeSpawn(spawn);
            helper.assertTrue(spawn.isSpawnCancelled() == blocked, "Spawner escaped protection for " + type);
            var reload = new EntityJoinLevelEvent(mob, level, true);
            RoyalCastleSpawnEvents.join(reload);
            helper.assertTrue(!reload.isCanceled(), "Reload destroyed an existing entity");
            mob.moveTo(site.x() - 1, site.y(), site.z());
            var outside = new EntityJoinLevelEvent(mob, level);
            RoyalCastleSpawnEvents.join(outside);
            helper.assertTrue(!outside.isCanceled(), "Spawn protection extends outside the castle");
        }
    }
}
