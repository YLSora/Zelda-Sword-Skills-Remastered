package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import zeldaswordskills_remastered.block.LockedChestBlock;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Arrays;

/** A carved basin with a sealed floor and a shore that rises into the original terrain. */
public final class FairyPoolPiece extends StructurePiece {
    public static final int RADIUS = 12;
    public static final int SIZE = RADIUS * 2 + 1;
    private static final Block[] SHORE = {Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
            Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.ANDESITE, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSS_BLOCK};
    private static final Block[] FLOOR = {Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.ANDESITE};
    private static final Block[] CORAL = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL,
            Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL};
    private final int waterY;
    private final int[] heights;
    private final long seed;

    public FairyPoolPiece(BlockPos origin, int[] heights, long seed) {
        super(ZSSRegistries.FAIRY_POOL_PIECE.get(), 0, new BoundingBox(origin.getX(), origin.getY() - 8,
                origin.getZ(), origin.getX() + SIZE - 1, Arrays.stream(heights).max().orElseThrow() + 4,
                origin.getZ() + SIZE - 1));
        this.waterY = origin.getY();
        this.heights = heights.clone();
        this.seed = seed;
    }

    public FairyPoolPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ZSSRegistries.FAIRY_POOL_PIECE.get(), tag);
        waterY = tag.getInt("WaterY");
        heights = tag.getIntArray("Heights");
        seed = tag.getLong("PoolSeed");
        if (heights.length != SIZE * SIZE) throw new IllegalArgumentException("Invalid pool terrain footprint");
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("WaterY", waterY);
        tag.putIntArray("Heights", heights);
        tag.putLong("PoolSeed", seed);
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        if (!level.getLevel().dimension().equals(Level.OVERWORLD)) return;
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            BlockPos surface = new BlockPos(boundingBox.minX() + x, waterY, boundingBox.minZ() + z);
            if (!clip.isInside(surface)) continue;
            double dx = x - RADIUS;
            double dz = z - RADIUS;
            double angle = Math.atan2(dz, dx);
            double phase = (seed & 65535) * (Math.PI * 2 / 65536);
            double radius = 7.6 + .55 * Math.sin(3 * angle + phase) + .35 * Math.cos(5 * angle - phase);
            double shoreDistance = Math.sqrt(dx * dx + dz * dz) - radius;
            if (shoreDistance > 3.5) continue;
            // Each column has its own RNG: chunk order and piece reloads cannot change the design.
            RandomSource detail = RandomSource.create(seed ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L));
            int terrain = heights[x * SIZE + z];
            boolean basin = shoreDistance < 0;
            int top = basin ? waterY - (shoreDistance < -3 ? 3 : 2)
                    : waterY + 1 + (int) Math.round(Math.max(0, terrain - waterY - 1) * Math.min(1, shoreDistance / 3.5));
            BlockState cap = (basin ? FLOOR[detail.nextInt(FLOOR.length)] : SHORE[detail.nextInt(SHORE.length)]).defaultBlockState();
            // The outermost fringe retains the local surface material for a soft terrain transition.
            if (shoreDistance > 2.3 && detail.nextBoolean()) {
                BlockState original = level.getBlockState(surface.atY(terrain));
                if (original.isCollisionShapeFullBlock(level, surface.atY(terrain)) && !original.hasBlockEntity()) cap = original;
            }
            for (int y = waterY - 8; y <= top; y++) {
                put(level, clip, surface.atY(y), y == top ? cap : Blocks.STONE.defaultBlockState());
            }
            for (int y = top + 1; y <= Math.max(terrain + 1, waterY + 3); y++) {
                put(level, clip, surface.atY(y), basin && y <= waterY ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            if (basin) {
                int decoration = detail.nextInt(16);
                BlockPos bed = surface.atY(top + 1);
                if (decoration < 3) put(level, clip, bed, Blocks.SEAGRASS.defaultBlockState());
                else if (decoration == 3) {
                    put(level, clip, bed, Blocks.TALL_SEAGRASS.defaultBlockState());
                    put(level, clip, bed.above(), Blocks.TALL_SEAGRASS.defaultBlockState()
                            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
                } else if (decoration < 6) put(level, clip, bed, CORAL[detail.nextInt(CORAL.length)].defaultBlockState()
                        .setValue(BlockStateProperties.WATERLOGGED, true));
                else if (decoration == 6) put(level, clip, bed, Blocks.SEA_PICKLE.defaultBlockState()
                        .setValue(BlockStateProperties.PICKLES, 2 + detail.nextInt(3)));
                if (decoration == 7 && shoreDistance < -1) put(level, clip, surface.above(), Blocks.LILY_PAD.defaultBlockState());
            } else {
                int ix = x - RADIUS;
                int iz = z - RADIUS;
                BlockPos bank = surface.atY(top + 1);
                if (ix == -9 && iz == 0) container(level, clip, bank, ZSSRegistries.CHEST_LOCKED.get().defaultBlockState()
                        .setValue(LockedChestBlock.FACING, Direction.EAST), DungeonLootTables.FAIRY_POOL, detail.nextLong());
                else if ((ix == -8 && iz == -6) || (ix == 8 && iz == 6)
                        || (ix == 6 && iz == -8) || (ix == -6 && iz == 8)) {
                    container(level, clip, bank, ZSSRegistries.CERAMIC_JAR.get().defaultBlockState(), DungeonLootTables.JAR, detail.nextLong());
                } else if (ix == -9 && iz == 2) put(level, clip, bank, Blocks.LANTERN.defaultBlockState());
                else if ((Math.pow(ix + 6, 2) + Math.pow(iz + 7, 2) < 5
                        || Math.pow(ix - 7, 2) + Math.pow(iz - 6, 2) < 5) && shoreDistance > .6) {
                    int height = 1 + detail.nextInt(2);
                    for (int y = 0; y < height; y++) put(level, clip, bank.above(y), SHORE[detail.nextInt(SHORE.length)].defaultBlockState());
                }
            }
        }
        put(level, clip, new BlockPos(boundingBox.minX() + RADIUS, waterY - 4, boundingBox.minZ() + RADIUS),
                ZSSRegistries.FAIRY_POOL_CORE.get().defaultBlockState());
    }

    private static void put(WorldGenLevel level, BoundingBox clip, BlockPos pos, BlockState state) {
        if (clip.isInside(pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void container(WorldGenLevel level, BoundingBox clip, BlockPos pos, BlockState state,
                                  ResourceLocation loot, long seed) {
        put(level, clip, pos, state);
        if (clip.isInside(pos) && level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) {
            storage.setLootTable(loot, seed);
        }
    }
}
