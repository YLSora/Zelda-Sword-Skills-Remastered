package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import zeldaswordskills_remastered.block.LockedChestBlock;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Arrays;

public final class FairyRootPiece extends StructurePiece {
    public static final int SIZE = 16;
    public static final int MIN_HEIGHT = 40;
    public static final int MAX_HEIGHT = 88;
    public static final int ORB_CENTER_Y = 18;
    private static final int WOOD = 1, GLASS = 2, LIGHT = 3, PLATFORM = 4, CHEST = 5, HOLLOW = 6,
            CORE = 7, FUNGUS = 8, NYLIUM = 9, JAR = 10;
    private final int baseY;
    private final int height;
    private final int[] heights;
    private final long seed;
    private final boolean warped;
    private final Shape shape;

    public FairyRootPiece(BlockPos origin, int[] heights, long seed, boolean warped, int height) {
        super(ZSSRegistries.FAIRY_ROOT_PIECE.get(), 0, new BoundingBox(origin.getX(),
                Arrays.stream(heights).min().orElseThrow() - 1, origin.getZ(), origin.getX() + SIZE - 1,
                origin.getY() + height - 1, origin.getZ() + SIZE - 1));
        this.baseY = origin.getY();
        this.height = height;
        this.heights = heights.clone();
        this.seed = seed;
        this.warped = warped;
        shape = new Shape(seed, height);
    }

    public FairyRootPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ZSSRegistries.FAIRY_ROOT_PIECE.get(), tag);
        baseY = tag.getInt("BaseY");
        height = boundingBox.maxY() - baseY + 1;
        heights = tag.getIntArray("Heights");
        seed = tag.getLong("RootSeed");
        warped = tag.getBoolean("Warped");
        if (heights.length != SIZE * SIZE) throw new IllegalArgumentException("Invalid root orb terrain footprint");
        shape = new Shape(seed, height);
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("BaseY", baseY);
        tag.putIntArray("Heights", heights);
        tag.putLong("RootSeed", seed);
        tag.putBoolean("Warped", warped);
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        if (!level.getLevel().dimension().equals(Level.NETHER)) return;
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            if (!Shape.ceilingAnchor(x, z)) continue;
            BlockPos ceiling = new BlockPos(boundingBox.minX() + x, baseY + height, boundingBox.minZ() + z);
            if (!FairyRootStructure.natural(level.getBlockState(ceiling))
                    || !FairyRootStructure.natural(level.getBlockState(ceiling.above()))) return;
        }
        // Noise columns precede carvers. Recheck the actual substrate before writing anything.
        // Natural starts occupy exactly one chunk, so this never requests a neighbouring chunk.
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            if (!shape.needsFoundation(x, z)) continue;
            BlockPos ground = new BlockPos(boundingBox.minX() + x,
                    Math.min(heights[x * SIZE + z] - 1, baseY - 2), boundingBox.minZ() + z);
            if (!FairyRootStructure.natural(level.getBlockState(ground))
                    || !FairyRootStructure.natural(level.getBlockState(ground.below()))) return;
        }
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            BlockPos base = new BlockPos(boundingBox.minX() + x, baseY, boundingBox.minZ() + z);
            if (!clip.isInside(base)) continue;
            RandomSource detail = RandomSource.create(seed ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L));
            int ground = heights[x * SIZE + z];
            int foot = shape.at(x, 0, z);
            if (shape.needsFoundation(x, z)) {
                for (int y = ground - 1; y < baseY; y++) {
                    BlockState support = (foot == WOOD && y > ground ? wood(detail) :
                            (y == baseY - 1 && (foot != 0 || detail.nextBoolean()) ? Blocks.BLACKSTONE : Blocks.NETHERRACK).defaultBlockState());
                    put(level, clip, base.atY(y), support);
                }
            }
            for (int y = 0; y < height; y++) {
                int kind = shape.at(x, y, z);
                if (kind == 0) continue;
                BlockState state = switch (kind) {
                    case WOOD -> wood(detail);
                    case GLASS -> (warped ? (detail.nextInt(5) == 0 ? Blocks.BLUE_STAINED_GLASS : Blocks.CYAN_STAINED_GLASS)
                            : (detail.nextInt(5) == 0 ? Blocks.RED_STAINED_GLASS : Blocks.ORANGE_STAINED_GLASS)).defaultBlockState();
                    case LIGHT -> Blocks.SHROOMLIGHT.defaultBlockState();
                    case PLATFORM -> (y == 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.BLACKSTONE).defaultBlockState();
                    case CHEST -> ZSSRegistries.CHEST_LOCKED.get().defaultBlockState().setValue(LockedChestBlock.FACING, Direction.WEST);
                    case CORE -> ZSSRegistries.FAIRY_ROOT_CORE.get().defaultBlockState();
                    case FUNGUS -> (warped ? Blocks.WARPED_FUNGUS : Blocks.CRIMSON_FUNGUS).defaultBlockState();
                    case NYLIUM -> (warped ? Blocks.WARPED_NYLIUM : Blocks.CRIMSON_NYLIUM).defaultBlockState();
                    case JAR -> ZSSRegistries.CERAMIC_JAR.get().defaultBlockState();
                    default -> Blocks.AIR.defaultBlockState();
                };
                BlockPos pos = base.above(y);
                put(level, clip, pos, state);
                if ((kind == CHEST || kind == JAR) && clip.isInside(pos)
                        && level.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) {
                    storage.setLootTable(kind == CHEST ? DungeonLootTables.FAIRY_ROOT : DungeonLootTables.JAR, detail.nextLong());
                }
            }
        }
    }

    private BlockState wood(RandomSource random) {
        return (random.nextInt(7) == 0 ? (warped ? Blocks.WARPED_WART_BLOCK : Blocks.NETHER_WART_BLOCK)
                : (warped ? Blocks.WARPED_HYPHAE : Blocks.CRIMSON_HYPHAE)).defaultBlockState();
    }

    private static void put(WorldGenLevel level, BoundingBox clip, BlockPos pos, BlockState state) {
        if (clip.isInside(pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** The same seeded volume is used for cave clearance and placement. No template is loaded. */
    static final class Shape {
        final double phase;
        private final int height;
        private final byte[] blocks;

        Shape(long seed, int height) {
            if (height < MIN_HEIGHT || height > MAX_HEIGHT) throw new IllegalArgumentException("Invalid root orb height");
            this.height = height;
            blocks = new byte[SIZE * height * SIZE];
            phase = (seed & 65535) * (Math.PI * 2 / 65536);
            for (int y = 0; y < height; y++) {
                double t = Math.min(1, y / 32.0);
                double merge = smooth(Math.max(0, (y - 25) / 7.0));
                double upper = Math.max(0, (y - 32.0) / (height - 33));
                double trunkX = 7.5 + Math.sin(upper * Math.PI * 2) * .8;
                double trunkZ = 7.5 + Math.sin(upper * Math.PI) * .6;
                double radius = y < 32 ? 1.05 + .08 * Math.cos(t * Math.PI * 2)
                        : 2.1 + .4 * smooth(Math.max(0, (upper - .75) * 4));
                for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
                    int kind = y >= 2 && y <= 10 && Math.hypot(x - 7.5, z - 7.5) < 5 ? HOLLOW : 0;
                    for (int root = 0; root < 3; root++) {
                        double angle = branchAngle(root, t);
                        double reach = (6.2 - .25 * Math.sin(t * Math.PI)) * (1 - merge);
                        double cx = y < 32 ? 7.5 + Math.cos(angle) * reach : trunkX;
                        double cz = y < 32 ? 7.5 + Math.sin(angle) * reach : trunkZ;
                        double thickness = y < 32 ? radius + 1.05 * merge : radius;
                        if (Math.hypot(x - cx, z - cz) <= thickness) kind = WOOD;
                    }
                    // Extend the trunk down to the glass crown; the shell below still takes precedence.
                    if (y >= ORB_CENTER_Y + 5 && y < 32) {
                        double neck = (y - ORB_CENTER_Y - 5) / 9.0;
                        double neckX = 7.5 + .25 * Math.sin(neck * Math.PI);
                        if (Math.hypot(x - neckX, z - 7.5) <= 1.5 + .6 * smooth(neck)) kind = WOOD;
                    }
                    if (insideOrb(x, y, z)) {
                        kind = HOLLOW;
                        for (Direction direction : Direction.values()) {
                            if (!insideOrb(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ())) {
                                kind = GLASS;
                                break;
                            }
                        }
                        if ((x == 7 && z == 7 && y == ORB_CENTER_Y) || (x == 8 && z == 7 && y == ORB_CENTER_Y + 1)
                                || (x == 7 && z == 8 && y == ORB_CENTER_Y - 1)) kind = LIGHT;
                    }
                    if (y <= 1 && Math.hypot(x - 7.5, z - 7.5) <= (y == 0 ? 3.1 : 2.6)) kind = PLATFORM;
                    if (x == 3 && z == 9 && y <= 2) kind = y == 0 ? PLATFORM : y == 1 ? CHEST : HOLLOW;
                    blocks[(x * height + y) * SIZE + z] = (byte) kind;
                }
            }
            // One exposed light per branch at each of two heights, plus sparse lights up the trunk.
            for (int root = 0; root < 3; root++) for (int y : new int[]{8, 24}) {
                double t = y / 32.0;
                double angle = branchAngle(root, t);
                lightNearest(7.5 + Math.cos(angle) * 7, y, 7.5 + Math.sin(angle) * 7);
            }
            for (int y = 35; y < height - 2; y += 9) lightNearest(12, y, 7.5);
            blocks[(7 * height) * SIZE + 7] = CORE;
            // Small supported patches beside each foot, leaving the platform and branches intact.
            for (int[] spot : new int[][]{{5, 1}, {11, 1}, {14, 9}, {12, 14}, {0, 13}, {3, 13}})
                decorate(spot[0], spot[1], FUNGUS);
            for (int[] spot : new int[][]{{5, 3}, {9, 0}, {14, 12}, {11, 11}, {1, 12}, {4, 11}})
                decorate(spot[0], spot[1], JAR);
        }

        private void decorate(int x, int z, int kind) {
            blocks[(x * height) * SIZE + z] = (byte) (kind == FUNGUS ? NYLIUM : PLATFORM);
            blocks[(x * height + 1) * SIZE + z] = (byte) kind;
            blocks[(x * height + 2) * SIZE + z] = HOLLOW;
        }

        int at(int x, int y, int z) { return blocks[(x * height + y) * SIZE + z]; }

        private double branchAngle(int root, double t) {
            return -Math.PI / 2 + root * Math.PI * 2 / 3
                    + (.85 + .025 * Math.sin(phase)) * Math.sin(t * Math.PI * 2);
        }

        private void lightNearest(double targetX, int y, double targetZ) {
            double best = Double.MAX_VALUE;
            int index = -1;
            for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
                double distance = Math.hypot(x - targetX, z - targetZ);
                if (at(x, y, z) == WOOD && distance < best) {
                    best = distance;
                    index = (x * height + y) * SIZE + z;
                }
            }
            if (index >= 0) blocks[index] = LIGHT;
        }

        static boolean ceilingAnchor(int x, int z) {
            return Math.hypot(x - 7.5, z - 7.5) <= 2.5;
        }

        boolean needsSpace(int x, int y, int z) {
            return at(x, y, z) != 0;
        }

        boolean needsFoundation(int x, int z) {
            return at(x, 0, z) != 0 || Math.hypot(x - 7.5, z - 7.5) < 3.8 + .3 * Math.sin(x + z + phase);
        }

        private boolean insideOrb(int x, int y, int z) {
            double dy = (y - ORB_CENTER_Y) / 5.2;
            double dx = (x - 7.5 - .15 * Math.sin(y * .23 + phase)) / 4.7;
            double dz = (z - 7.5 - .15 * Math.cos(y * .19 + phase)) / 4.6;
            double ripple = .02 * Math.sin(x * .8 + y * .35 + phase) * Math.cos(z * .6 - y * .2);
            return dx * dx + dy * dy + dz * dz < 1 + ripple;
        }

        private static double smooth(double t) {
            t = Math.min(1, t);
            return t * t * (3 - 2 * t);
        }
    }
}
