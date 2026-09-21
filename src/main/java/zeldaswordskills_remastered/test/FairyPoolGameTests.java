package zeldaswordskills_remastered.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.LockedChestBlock;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.FairyPoolPiece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FairyPoolGameTests {
    @GameTest(template="zssgametests.dungeon_empty", templateNamespace="minecraft", batch="zssPool", timeoutTicks=200)
    public static void poolSurvivesChunkOrderReloadAndFluidTicks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(3, 10, 3));
        BlockPos second = first.offset(40, 0, 0);
        var context = StructurePieceSerializationContext.fromLevel(level);
        for (long seed : new long[]{0L, 1L, 12345L, 987654321L}) {
            FairyPoolPiece a = prepare(helper, first, seed);
            FairyPoolPiece b = prepare(helper, second, seed);
            var tag = b.createTag(context);
            b = new FairyPoolPiece(context, tag);
            helper.assertTrue(tag.equals(b.createTag(context)), "Pool lost terrain or seed during reload");
            placeChunks(helper, a, false);
            placeChunks(helper, b, true);
            verifyPool(helper, first);
            verifyPool(helper, second);
            for (int x = 0; x < FairyPoolPiece.SIZE; x++) for (int z = 0; z < FairyPoolPiece.SIZE; z++) {
                for (int y = -8; y <= 8; y++) {
                    BlockPos pa = first.offset(x, y, z);
                    BlockPos pb = second.offset(x, y, z);
                    helper.assertTrue(level.getBlockState(pa).equals(level.getBlockState(pb)), "Chunk order changed pool at " + pa);
                    if (level.getBlockEntity(pa) instanceof StageNineBlockEntities.Storage sa) {
                        var sb = (StageNineBlockEntities.Storage) level.getBlockEntity(pb);
                        helper.assertTrue(sa.saveWithoutMetadata().equals(sb.saveWithoutMetadata()), "Chunk order changed loot");
                    }
                }
            }
        }
        helper.runAfterDelay(80, () -> {
            verifyPool(helper, first);
            verifyPool(helper, second);
            helper.succeed();
        });
    }

    private static FairyPoolPiece prepare(GameTestHelper helper, BlockPos origin, long seed) {
        var level = helper.getLevel();
        int[] heights = new int[FairyPoolPiece.SIZE * FairyPoolPiece.SIZE];
        for (int x = 0; x < FairyPoolPiece.SIZE; x++) for (int z = 0; z < FairyPoolPiece.SIZE; z++) {
            int top = 1 + (x + z) / 12;
            heights[x * FairyPoolPiece.SIZE + z] = origin.getY() + top;
            for (int y = -9; y <= 9; y++) {
                BlockPos pos = origin.offset(x, y, z);
                if (level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) storage.clearContent();
                level.setBlock(pos, (y < top ? Blocks.STONE : y == top ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(), 2);
            }
        }
        return new FairyPoolPiece(origin, heights, seed);
    }

    private static void placeChunks(GameTestHelper helper, FairyPoolPiece piece, boolean reverse) {
        var level = helper.getLevel();
        var box = piece.getBoundingBox();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) chunks.add(new ChunkPos(x, z));
        }
        if (reverse) Collections.reverse(chunks);
        for (ChunkPos chunk : chunks) {
            var clip = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(91),
                    clip, chunk, BlockPos.ZERO);
        }
    }

    private static void verifyPool(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        int chests = 0, jars = 0, plants = 0, corals = 0, water = 0, cores = 0;
        int minX = 25, maxX = 0, minZ = 25, maxZ = 0;
        for (int x = 0; x < FairyPoolPiece.SIZE; x++) for (int z = 0; z < FairyPoolPiece.SIZE; z++) {
            for (int y = -8; y <= 8; y++) {
                BlockPos pos = origin.offset(x, y, z);
                var state = level.getBlockState(pos);
                if (state.is(ZSSRegistries.FAIRY_POOL_CORE.get())) {
                    cores++;
                    helper.assertTrue(pos.equals(origin.offset(FairyPoolPiece.RADIUS, -4, FairyPoolPiece.RADIUS))
                            && level.getBlockEntity(pos) instanceof zeldaswordskills_remastered.block.entity.FairyCore,
                            "Pool core is missing or outside the sealed floor");
                }
                if (state.is(ZSSRegistries.CHEST_LOCKED.get()) || state.is(ZSSRegistries.CERAMIC_JAR.get())) {
                    boolean chest = state.is(ZSSRegistries.CHEST_LOCKED.get());
                    if (chest) {
                        chests++;
                        helper.assertTrue(!state.getValue(LockedChestBlock.UNLOCKED) && state.getValue(LockedChestBlock.VISIBLE), "Chest must start visible and locked");
                        helper.assertTrue(level.isEmptyBlock(pos.above()), "Chest opening is obstructed");
                    } else jars++;
                    helper.assertTrue(level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP), "Floating container");
                    var storage = (StageNineBlockEntities.Storage) level.getBlockEntity(pos);
                    helper.assertTrue(storage != null && storage.saveWithoutMetadata().getString("LootTable")
                            .equals((chest ? DungeonLootTables.FAIRY_POOL : DungeonLootTables.JAR).toString()), "Missing pool loot table");
                }
                helper.assertTrue(!state.is(Blocks.CHEST), "Pool contains a vanilla chest");
                if (state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)) plants++;
                if (state.is(net.minecraft.tags.BlockTags.CORALS)) corals++;
                if (state.getFluidState().is(FluidTags.WATER)) {
                    helper.assertTrue(y <= 0 && state.getFluidState().isSource(), "Pool leaks or has flowing water");
                    water++;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    for (Direction direction : Direction.values()) {
                        if (direction == Direction.UP) continue;
                        BlockPos adjacent = pos.relative(direction);
                        var neighbour = level.getBlockState(adjacent);
                        helper.assertTrue(neighbour.getFluidState().is(FluidTags.WATER)
                                || neighbour.isFaceSturdy(level, adjacent, direction.getOpposite()), "Unsealed pool at " + adjacent);
                    }
                }
            }
        }
        helper.assertTrue(chests == 1 && jars == 4, "Expected one locked chest and four jars, got " + chests + "/" + jars);
        helper.assertTrue(cores == 1, "Expected exactly one fairy pool core");
        helper.assertTrue(water > 200 && maxX - minX + 1 >= 12 && maxZ - minZ + 1 >= 12, "Pool is too small");
        helper.assertTrue(plants > 0 && corals > 0, "Pool lost its living aquatic decorations");
    }

    @GameTest(template="zssgametests.empty", templateNamespace="minecraft", batch="zssPoolInteractions")
    public static void upgradeEligibilitySurvivesSaveButNotBottling(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new net.minecraftforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "[Pool_Upgrade]"));
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var home = helper.absolutePos(new BlockPos(2, 1, 2));
        var fairy = (zeldaswordskills_remastered.entity.FairyCreature) ZSSRegistries.FAIRY.get().create(level);
        fairy.bindToHabitat(home);
        var saved = new net.minecraft.nbt.CompoundTag();
        fairy.saveWithoutId(saved);
        var reloaded = (zeldaswordskills_remastered.entity.FairyCreature) ZSSRegistries.FAIRY.get().create(level);
        reloaded.load(saved);
        helper.assertTrue(reloaded.canUpgradeEquipment() && reloaded.belongsToHabitat(home), "Pool eligibility lost on entity reload");
        player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(ZSSRegistries.getItem("slingshot")));
        helper.assertTrue(zeldaswordskills_remastered.progression.AcquisitionService.interact(player, reloaded, hand)
                && reloaded.isAlive() && player.getMainHandItem().is(ZSSRegistries.getItem("slingshot")),
                "Failed payment consumed the fairy or upgraded equipment");
        player.getAbilities().instabuild = true;
        helper.assertTrue(zeldaswordskills_remastered.progression.AcquisitionService.interact(player, reloaded, hand)
                && reloaded.isRemoved() && player.getMainHandItem().is(ZSSRegistries.getItem("scattershot")),
                "Successful upgrade did not consume exactly its fairy");
        helper.assertTrue(!zeldaswordskills_remastered.progression.AcquisitionService.interact(player, reloaded, hand)
                && player.getMainHandItem().is(ZSSRegistries.getItem("scattershot")), "Removed fairy upgraded twice");

        player.getAbilities().instabuild = false;
        player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
        helper.assertTrue(zeldaswordskills_remastered.progression.AcquisitionService.interact(player, fairy, hand)
                && fairy.isRemoved() && player.getMainHandItem().is(ZSSRegistries.getItem("fairy_bottle")),
                "Pool fairy could not be bottled");
        var bottle = player.getMainHandItem();
        helper.assertTrue(!bottle.hasTag(), "Bottling retained pool upgrade data");
        helper.assertTrue(!zeldaswordskills_remastered.progression.AcquisitionService.interact(player, fairy, hand),
                "Captured fairy remained interactable");
        var ordinary = (zeldaswordskills_remastered.entity.FairyCreature) ZSSRegistries.FAIRY.get().create(level);
        level.setBlockAndUpdate(home, ZSSRegistries.FAIRY_POOL_CORE.get().defaultBlockState());
        player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(ZSSRegistries.getItem("slingshot")));
        player.getAbilities().instabuild = true;
        helper.assertTrue(!zeldaswordskills_remastered.progression.AcquisitionService.interact(player, ordinary, hand)
                && ordinary.isAlive(), "Ordinary fairy gained upgrade eligibility near a pool");
        player.setItemInHand(hand, bottle);
        player.setHealth(1);
        player.getAbilities().instabuild = false;
        bottle.use(level, player, hand);
        helper.assertTrue(player.getHealth() > 1 && player.getMainHandItem().is(net.minecraft.world.item.Items.GLASS_BOTTLE),
                "Bottled pool fairy lost its healing behavior");
        player.discard();
        ordinary.discard();
        helper.succeed();
    }

    @GameTest(template="zssgametests.empty", templateNamespace="minecraft", batch="zssPoolGeneration", timeoutTicks=400)
    public static void naturalTerrainPlacementAndBiomeSelection(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var structure = registries.registryOrThrow(Registries.STRUCTURE).get(DungeonLootTables.id("fairy_pool"));
        var placement = (RandomSpreadStructurePlacement) registries.registryOrThrow(Registries.STRUCTURE_SET)
                .get(DungeonLootTables.id("fairy_pools")).placement();
        helper.assertTrue(placement.spacing() == 40 && placement.separation() == 24, "Pool spacing changed");
        var biomes = registries.registryOrThrow(Registries.BIOME);
        for (var biome : List.of(net.minecraft.world.level.biome.Biomes.PLAINS, net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS,
                net.minecraft.world.level.biome.Biomes.FLOWER_FOREST, net.minecraft.world.level.biome.Biomes.OLD_GROWTH_BIRCH_FOREST,
                net.minecraft.world.level.biome.Biomes.WINDSWEPT_FOREST,
                net.minecraft.world.level.biome.Biomes.SAVANNA_PLATEAU, net.minecraft.world.level.biome.Biomes.CHERRY_GROVE)) {
            helper.assertTrue(structure.biomes().contains(biomes.getHolderOrThrow(biome)), "Missing eligible pool biome: " + biome);
        }
        for (var biome : List.of(net.minecraft.world.level.biome.Biomes.OCEAN, net.minecraft.world.level.biome.Biomes.DESERT,
                net.minecraft.world.level.biome.Biomes.NETHER_WASTES, net.minecraft.world.level.biome.Biomes.THE_END)) {
            helper.assertTrue(!structure.biomes().contains(biomes.getHolderOrThrow(biome)), "Unexpected pool biome: " + biome);
        }
        var generator = (NoiseBasedChunkGenerator) registries.registryOrThrow(Registries.WORLD_PRESET)
                .get(WorldPresets.NORMAL).createWorldDimensions().overworld();
        for (long seed : new long[]{0L, 12345L, 987654321L}) {
            var random = RandomState.create(generator.generatorSettings().value(), registries.registryOrThrow(Registries.NOISE).asLookup(), seed);
            boolean found = false;
            for (int region = 0; region < 512 && !found; region++) {
                ChunkPos chunk = placement.getPotentialStructureChunk(seed,
                        (region % 32 - 16) * placement.spacing(), (region / 32 - 8) * placement.spacing());
                var start = structure.generate(registries, generator, generator.getBiomeSource(), random,
                        level.getStructureManager(), seed, chunk, 0, level, structure.biomes()::contains);
                if (!start.isValid()) continue;
                var piece = start.getPieces().get(0);
                var box = piece.getBoundingBox();
                int waterY = piece.createTag(StructurePieceSerializationContext.fromLevel(level)).getInt("WaterY");
                for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    int surface = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
                    helper.assertTrue(surface >= waterY + 1 && surface <= waterY + 5, "Pool is floating or buried");
                }
                ZeldaSwordSkills_Remastered.LOGGER.info("Pool generation verified: seed={} box={}", seed, box);
                found = true;
            }
            helper.assertTrue(found, "No surface pool found for seed " + seed);
        }
        for (var dimension : List.of(Level.NETHER, Level.END)) {
            var other = level.getServer().getLevel(dimension);
            var otherGenerator = (NoiseBasedChunkGenerator) other.getChunkSource().getGenerator();
            var random = RandomState.create(otherGenerator.generatorSettings().value(), registries.registryOrThrow(Registries.NOISE).asLookup(), 0L);
            helper.assertTrue(!structure.generate(registries, otherGenerator, otherGenerator.getBiomeSource(), random,
                    other.getStructureManager(), 0L, new ChunkPos(0, 0), 0, other, structure.biomes()::contains).isValid(),
                    "Pool generated outside the Overworld");
        }
        helper.succeed();
    }
}
