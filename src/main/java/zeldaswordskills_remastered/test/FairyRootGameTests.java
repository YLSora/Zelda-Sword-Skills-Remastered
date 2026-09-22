package zeldaswordskills_remastered.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.FairyRootPiece;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FairyRootGameTests {
    @GameTest(template="zssgametests.empty", templateNamespace="minecraft", batch="zssRootOrb", timeoutTicks=400)
    public static void geometryPaletteGroundingAndReload(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos a = new BlockPos(4800, 144, 4800), b = a.offset(48, 0, 0);
        var context = StructurePieceSerializationContext.fromLevel(level);
        long previousHash = 0;
        for (long seed : new long[]{0L, 12345L, 987654321L}) {
            boolean warped = seed != 0;
            var first = prepare(level, a, seed, warped);
            var second = prepare(level, b, seed, warped);
            var tag = second.createTag(context);
            second = new FairyRootPiece(context, tag);
            helper.assertTrue(tag.equals(second.createTag(context)), "Root orb lost its terrain, seed or palette on reload");
            place(level, first, first.getBoundingBox());
            // Split clipping in reverse order must produce the same block and loot data.
            var box = second.getBoundingBox();
            int height = box.maxY() - b.getY() + 1;
            place(level, second, new BoundingBox(box.minX() + 8, box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
            place(level, second, new BoundingBox(box.minX(), box.minY(), box.minZ(), box.minX() + 7, box.maxY(), box.maxZ()));
            long hash = 1;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -6; y < height; y++) {
                var sa = level.getBlockState(a.offset(x, y, z));
                var sb = level.getBlockState(b.offset(x, y, z));
                helper.assertTrue(sa.equals(sb), "Reload or clipping changed root orb geometry");
                hash = hash * 31 + sa.hashCode();
            }
            helper.assertTrue(hash != previousHash, "Different seeds produced identical root orbs");
            previousHash = hash;
            verify(helper, level, a, warped, height);
            verify(helper, level, b, warped, height);
            var chestA = (StageNineBlockEntities.Storage) level.getBlockEntity(a.offset(3, 1, 9));
            var chestB = (StageNineBlockEntities.Storage) level.getBlockEntity(b.offset(3, 1, 9));
            helper.assertTrue(chestA.saveWithoutMetadata().equals(chestB.saveWithoutMetadata()), "Clipping changed chest loot");
        }
        var rejected = prepare(level, a, 0, false);
        level.setBlock(a.offset(7, -3, 1), Blocks.AIR.defaultBlockState(), 2);
        place(level, rejected, rejected.getBoundingBox());
        helper.assertTrue(level.isEmptyBlock(a.offset(7, 18, 7)), "Carved-out foundation still generated a floating orb structure");
        rejected = prepare(level, a, 0, false);
        level.setBlock(a.offset(7, 40, 7), Blocks.AIR.defaultBlockState(), 2);
        place(level, rejected, rejected.getBoundingBox());
        helper.assertTrue(level.isEmptyBlock(a.offset(7, 18, 7)), "Missing ceiling still generated a disconnected trunk");
        var overworldOrigin = helper.absolutePos(new BlockPos(0, 3, 0));
        int[] heights = new int[256];
        java.util.Arrays.fill(heights, overworldOrigin.getY() - 1);
        var wrongDimension = new FairyRootPiece(overworldOrigin, heights, 0, false, 40);
        var before = helper.getLevel().getBlockState(overworldOrigin.offset(7, 0, 7));
        place(helper.getLevel(), wrongDimension, wrongDimension.getBoundingBox());
        helper.assertTrue(helper.getLevel().getBlockState(overworldOrigin.offset(7, 0, 7)).equals(before), "Root orb wrote outside the Nether");
        helper.succeed();
    }

    private static FairyRootPiece prepare(ServerLevel level, BlockPos origin, long seed, boolean warped) {
        int[] heights = new int[256];
        int height = seed == 0 ? 40 : seed == 12345 ? 48 : 64;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int top = seed == 987654321L ? (x + z) / 6 - 3 : -1 - (x + z) / 10;
            heights[x * 16 + z] = origin.getY() + top;
            for (int y = -7; y < FairyRootPiece.MAX_HEIGHT + 3; y++) {
                BlockPos pos = origin.offset(x, y, z);
                if (level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) storage.clearContent();
                level.setBlock(pos, (y <= top || y >= height ? Blocks.NETHERRACK : Blocks.AIR).defaultBlockState(), 2);
            }
        }
        return new FairyRootPiece(origin, heights, seed, warped, height);
    }

    private static void place(ServerLevel level, FairyRootPiece piece, BoundingBox clip) {
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(9),
                clip, new ChunkPos(piece.getBoundingBox().minX() >> 4, piece.getBoundingBox().minZ() >> 4), BlockPos.ZERO);
    }

    private static void verify(GameTestHelper helper, ServerLevel level, BlockPos origin, boolean warped, int height) {
        Set<BlockPos> wood = new HashSet<>();
        int glass = 0, lights = 0, chests = 0, fungi = 0, jars = 0, cores = 0;
        int minGlassX = 16, maxGlassX = 0, minGlassY = height, maxGlassY = 0, minGlassZ = 16, maxGlassZ = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < height; y++) {
            BlockPos pos = origin.offset(x, y, z);
            var state = level.getBlockState(pos);
            if (wood(state)) {
                wood.add(pos);
                helper.assertTrue(warped ? state.is(Blocks.WARPED_HYPHAE) || state.is(Blocks.WARPED_WART_BLOCK)
                        : state.is(Blocks.CRIMSON_HYPHAE) || state.is(Blocks.NETHER_WART_BLOCK), "Wrong forest palette");
                if (y == 0) for (int depth = 1; depth <= 4; depth++) {
                    helper.assertTrue(!level.getBlockState(pos.below(depth)).isAir(), "Root foot is floating");
                }
            }
            if (state.getBlock() instanceof StainedGlassBlock) {
                glass++;
                minGlassX = Math.min(minGlassX, x); maxGlassX = Math.max(maxGlassX, x);
                minGlassY = Math.min(minGlassY, y); maxGlassY = Math.max(maxGlassY, y);
                minGlassZ = Math.min(minGlassZ, z); maxGlassZ = Math.max(maxGlassZ, z);
            }
            if (state.is(Blocks.SHROOMLIGHT)) {
                lights++;
                if (y == 8 || y == 24 || y >= 32) wood.add(pos);
            }
            if (state.is(ZSSRegistries.CHEST_LOCKED.get())) chests++;
            if (state.is(ZSSRegistries.FAIRY_ROOT_CORE.get())) cores++;
            if (state.is(Blocks.WARPED_FUNGUS) || state.is(Blocks.CRIMSON_FUNGUS)) {
                fungi++;
                helper.assertTrue(state.is(warped ? Blocks.WARPED_FUNGUS : Blocks.CRIMSON_FUNGUS)
                        && state.canSurvive(level, pos), "Fungus is unsupported or does not match its root");
            }
            if (state.is(ZSSRegistries.CERAMIC_JAR.get())) {
                jars++;
                var jar = (StageNineBlockEntities.Storage) level.getBlockEntity(pos);
                helper.assertTrue(level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                        && jar.saveWithoutMetadata().getString("LootTable").equals(DungeonLootTables.JAR.toString()),
                        "Root jar lost its support or loot");
            }
        }
        helper.assertTrue(fungi == 6 && jars == 6 && cores == 1, "Root needs six fungi, six jars and one fairy core");
        helper.assertTrue(wood.size() > 450 && glass > 180 && lights >= 10 && lights < 20 && chests == 1, "Incomplete root orb sculpture");
        int width = Math.min(maxGlassX - minGlassX + 1, maxGlassZ - minGlassZ + 1);
        helper.assertTrue((maxGlassY - minGlassY + 1) <= width * 1.3, "Orb is too elongated");
        helper.assertTrue(Math.abs((minGlassY + maxGlassY) / 2.0 - 2 - 16) <= .5, "Orb center is not 16 blocks above the platform");
        verifyOrbAttachment(helper, level, origin);
        Set<BlockPos> connected = floodWood(wood, wood.iterator().next());
        helper.assertTrue(connected.size() == wood.size(), "Root branches or upper trunk are disconnected");
        var top = wood.stream().filter(pos -> pos.getY() == origin.getY() + height - 1).toList();
        helper.assertTrue(!top.isEmpty() && top.stream().allMatch(pos -> level.getBlockState(pos.above()).is(Blocks.NETHERRACK)),
                "Trunk is not joined to the natural ceiling");
        Set<BlockPos> feet = new HashSet<>();
        wood.stream().filter(pos -> pos.getY() == origin.getY()).forEach(feet::add);
        int roots = 0;
        var centers = new java.util.ArrayList<net.minecraft.world.phys.Vec3>();
        while (!feet.isEmpty()) {
            var group = floodWood(feet, feet.iterator().next());
            helper.assertTrue(group.size() <= 6, "Lower branch is too thick");
            centers.add(new net.minecraft.world.phys.Vec3(group.stream().mapToInt(BlockPos::getX).average().orElseThrow(), 0,
                    group.stream().mapToInt(BlockPos::getZ).average().orElseThrow()));
            feet.removeAll(group); roots++;
        }
        helper.assertTrue(roots == 3, "Expected three distinct grounded roots");
        double shortest = Double.MAX_VALUE, longest = 0;
        for (int i = 0; i < 3; i++) {
            double side = centers.get(i).distanceTo(centers.get((i + 1) % 3));
            shortest = Math.min(shortest, side); longest = Math.max(longest, side);
        }
        helper.assertTrue(longest - shortest < 1, "Root feet do not form an equilateral triangle within block rounding");
        for (int y : new int[]{8, 24}) {
            final int slice = y;
            double bend = wood.stream().filter(pos -> pos.getY() == origin.getY() + slice && pos.getZ() < origin.getZ() + 4)
                    .mapToInt(BlockPos::getX).average().orElseThrow() - origin.getX() - 7.5;
            helper.assertTrue(y == 8 ? bend > 3.5 : bend < -3.5, "Lower branch needs a pronounced S bend with over seven blocks of lateral swing");
            helper.assertTrue(wood.stream().filter(pos -> pos.getY() == origin.getY() + slice
                    && level.getBlockState(pos).is(Blocks.SHROOMLIGHT)).count() == 3, "Each branch needs sparse shroomlights");
        }
        helper.assertTrue(level.getBlockState(origin.offset(7, 0, 7)).is(ZSSRegistries.FAIRY_ROOT_CORE.get())
                && level.getBlockEntity(origin.offset(7, 0, 7)) instanceof zeldaswordskills_remastered.block.entity.FairyCore
                && level.getBlockState(origin.offset(7, 1, 7)).is(Blocks.POLISHED_BLACKSTONE_BRICKS), "Platform is not two blocks high");
        for (int x = 5; x <= 10; x++) for (int z = 5; z <= 10; z++) for (int y = 2; y <= 10; y++) {
            helper.assertTrue(level.isEmptyBlock(origin.offset(x, y, z)), "Space between platform and orb is obstructed");
        }
        Set<BlockPos> interior = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.offset(7, 18, 7));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            if (!interior.add(pos)) continue;
            helper.assertTrue(interior.size() < 1500 && pos.getY() > origin.getY() + 10, "Glass shell is not sealed");
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                var state = level.getBlockState(next);
                if (state.isAir() || state.is(Blocks.SHROOMLIGHT)) queue.add(next);
                else helper.assertTrue(state.getBlock() instanceof StainedGlassBlock, "Branch penetrates hollow orb");
            }
        }
        helper.assertTrue(interior.size() > 200, "Orb interior is not hollow");
        BlockPos chestPos = origin.offset(3, 1, 9);
        var chest = (StageNineBlockEntities.Storage) level.getBlockEntity(chestPos);
        helper.assertTrue(chest != null && chest.saveWithoutMetadata().getString("LootTable")
                .equals(DungeonLootTables.FAIRY_ROOT.toString()), "Missing Nether chest loot");
        helper.assertTrue(level.isEmptyBlock(chestPos.above())
                && level.getBlockState(chestPos.below()).isFaceSturdy(level, chestPos.below(), Direction.UP), "Chest floats or cannot open");
        helper.assertTrue(level.getBlockState(origin.offset(0, -4, 0)).is(Blocks.NETHERRACK)
                && level.isEmptyBlock(origin), "Generator flattened the entire footprint");
    }

    private static void verifyOrbAttachment(GameTestHelper helper, ServerLevel level, BlockPos origin) {
        for (int x = 7; x <= 8; x++) for (int z = 7; z <= 8; z++) {
            helper.assertTrue(level.getBlockState(origin.offset(x, FairyRootPiece.ORB_CENTER_Y + 5, z))
                    .getBlock() instanceof StainedGlassBlock, "Trunk destroyed the glass crown");
            for (int y = FairyRootPiece.ORB_CENTER_Y + 6; y <= 32; y++) {
                helper.assertTrue(wood(level.getBlockState(origin.offset(x, y, z))), "Orb is separated from the main trunk");
            }
        }
    }

    private static boolean wood(BlockState state) {
        return state.is(Blocks.CRIMSON_HYPHAE) || state.is(Blocks.WARPED_HYPHAE)
                || state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.WARPED_WART_BLOCK);
    }

    private static Set<BlockPos> floodWood(Set<BlockPos> blocks, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            if (!seen.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (blocks.contains(next) && !seen.contains(next)) queue.add(next);
            }
        }
        return seen;
    }

    @GameTest(template="zssgametests.empty", templateNamespace="minecraft", batch="zssRootOrbGeneration", timeoutTicks=400)
    public static void forestBiomesSpacingAndNaturalCaves(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.NETHER);
        var registries = level.registryAccess();
        var structure = registries.registryOrThrow(Registries.STRUCTURE).get(DungeonLootTables.id("fairy_root"));
        var placement = (RandomSpreadStructurePlacement) registries.registryOrThrow(Registries.STRUCTURE_SET)
                .get(DungeonLootTables.id("fairy_roots")).placement();
        helper.assertTrue(placement.spacing() == 30 && placement.separation() == 18, "Wrong root orb spacing");
        var biomes = registries.registryOrThrow(Registries.BIOME);
        helper.assertTrue(structure.biomes().size() == 2 && structure.biomes().contains(biomes.getHolderOrThrow(Biomes.CRIMSON_FOREST))
                && structure.biomes().contains(biomes.getHolderOrThrow(Biomes.WARPED_FOREST)), "Wrong root orb biomes");
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        for (long seed : new long[]{0L, 12345L, 987654321L}) {
            var random = RandomState.create(generator.generatorSettings().value(), registries.registryOrThrow(Registries.NOISE).asLookup(), seed);
            boolean found = false;
            for (int region = 0; region < 1024 && !found; region++) {
                var chunk = placement.getPotentialStructureChunk(seed, (region % 32 - 16) * placement.spacing(), (region / 32 - 16) * placement.spacing());
                var start = structure.generate(registries, generator, generator.getBiomeSource(), random,
                        level.getStructureManager(), seed, chunk, 0, level, structure.biomes()::contains);
                if (!start.isValid()) continue;
                var piece = start.getPieces().get(0);
                var box = piece.getBoundingBox();
                var tag = piece.createTag(StructurePieceSerializationContext.fromLevel(level));
                int base = tag.getInt("BaseY");
                var biome = generator.getBiomeSource().getNoiseBiome((box.minX() + 8) >> 2, base >> 2,
                        (box.minZ() + 8) >> 2, random.sampler());
                helper.assertTrue(tag.getBoolean("Warped") == biome.is(Biomes.CRIMSON_FOREST), "Forest variants were not swapped");
                int height = box.maxY() - base + 1;
                helper.assertTrue(box.getXSpan() == 16 && box.getZSpan() == 16 && box.maxY() == base + height - 1
                        && height >= FairyRootPiece.MIN_HEIGHT && height <= FairyRootPiece.MAX_HEIGHT
                        && box.minY() > 28 && box.maxY() < 120, "Root orb exceeds cave or chunk bounds");
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    var column = generator.getBaseColumn(box.minX() + x, box.minZ() + z, level, random);
                    int ground = tag.getIntArray("Heights")[x * 16 + z];
                    if ((x == 7 && z == 1) || (x == 13 && z == 11) || (x == 2 && z == 11) || (x == 7 && z == 7)) {
                        helper.assertTrue(!column.getBlock(ground).isAir() && column.getBlock(ground + 1).isAir()
                                && base - ground >= -2 && base - ground <= 5, "Root orb is not grounded on the cave floor");
                    }
                    if (Math.hypot(x - 7.5, z - 7.5) <= 2.5) {
                        helper.assertTrue(!column.getBlock(base + height).isAir() && !column.getBlock(base + height + 1).isAir(),
                                "Noise-selected trunk has no ceiling anchor");
                    }
                    if (x >= 5 && x <= 10 && z >= 5 && z <= 10) for (int y = 4; y <= 10; y++) {
                        helper.assertTrue(column.getBlock(base + y).isAir(), "Natural terrain blocks platform clearance");
                    }
                }
                ZeldaSwordSkills_Remastered.LOGGER.info("Root orb generation verified: seed={} candidates={} box={}", seed, region + 1, box);
                found = true;
            }
            helper.assertTrue(found, "No natural forest cave fits a root orb for seed " + seed);
        }
        for (var dimension : List.of(Level.OVERWORLD, Level.END)) {
            var other = level.getServer().getLevel(dimension);
            var otherGenerator = other.getChunkSource().getGenerator();
            var random = other.getChunkSource().randomState();
            helper.assertTrue(!structure.generate(registries, otherGenerator, otherGenerator.getBiomeSource(), random,
                    other.getStructureManager(), 0L, new ChunkPos(0, 0), 0, other, structure.biomes()::contains).isValid(),
                    "Root orb selected another dimension");
        }
        helper.succeed();
    }

    @GameTest(template="zssgametests.empty", templateNamespace="minecraft", batch="zssRootOrbWorld", timeoutTicks=400)
    public static void placementInGeneratedNetherTerrain(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.NETHER);
        var registries = level.registryAccess();
        var structure = registries.registryOrThrow(Registries.STRUCTURE).get(DungeonLootTables.id("fairy_root"));
        var placement = (RandomSpreadStructurePlacement) registries.registryOrThrow(Registries.STRUCTURE_SET)
                .get(DungeonLootTables.id("fairy_roots")).placement();
        var generator = level.getChunkSource().getGenerator();
        long seed = level.getSeed();
        int loaded = 0;
        for (int region = 0; region < 1024 && loaded < 128; region++) {
            var chunk = placement.getPotentialStructureChunk(seed, (region % 32 + 80) * placement.spacing(), (region / 32 + 80) * placement.spacing());
            var start = structure.generate(registries, generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                    level.getStructureManager(), seed, chunk, 0, level, structure.biomes()::contains);
            if (!start.isValid()) continue;
            loaded++;
            var piece = start.getPieces().get(0);
            int base = piece.createTag(StructurePieceSerializationContext.fromLevel(level)).getInt("BaseY");
            BlockPos origin = new BlockPos(piece.getBoundingBox().minX(), base, piece.getBoundingBox().minZ());
            level.getChunk(chunk.x, chunk.z);
            // The shared GameTest world disables natural structures. Use vanilla placement on its real terrain.
            start.placeInChunk(level, level.structureManager(), generator, RandomSource.create(seed),
                    new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                            chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ()), chunk);
            // A cave carver may remove a noise-approved foundation; the piece must then skip it intact.
            if (!level.getBlockState(origin.offset(7, 18, 7)).is(Blocks.SHROOMLIGHT)) continue;
            verifyOrbAttachment(helper, level, origin);
            helper.assertTrue(level.getBlockState(origin.offset(3, 1, 9)).is(ZSSRegistries.CHEST_LOCKED.get()),
                    "Natural structure generated without its chest");
            for (BlockPos foot : List.of(new BlockPos(7, 0, 1), new BlockPos(13, 0, 11), new BlockPos(2, 0, 11))) {
                helper.assertTrue(wood(level.getBlockState(origin.offset(foot))) && !level.isEmptyBlock(origin.offset(foot).below()),
                        "Natural structure has a missing or floating root");
            }
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (Math.hypot(x - 7.5, z - 7.5) > 2.5) continue;
                BlockPos tip = new BlockPos(origin.getX() + x, piece.getBoundingBox().maxY(), origin.getZ() + z);
                helper.assertTrue(wood(level.getBlockState(tip)) && !level.isEmptyBlock(tip.above()), "Natural trunk is disconnected from ceiling");
            }
            for (int y = 2; y <= 10; y++) helper.assertTrue(level.isEmptyBlock(origin.offset(7, y, 7)),
                    "Natural decorations obstructed the platform");
            ZeldaSwordSkills_Remastered.LOGGER.info("Root orb placed in generated Nether terrain: seed={} origin={}", seed, origin);
            helper.succeed();
            return;
        }
        helper.fail("No root orb was actually placed in generated Nether terrain");
    }
}
