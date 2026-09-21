package zeldaswordskills_remastered.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Optional;

public final class FairyPoolStructure extends Structure {
    public static final Codec<FairyPoolStructure> CODEC = simpleCodec(FairyPoolStructure::new);

    public FairyPoolStructure(StructureSettings settings) { super(settings); }
    @Override public StructureType<?> type() { return ZSSRegistries.FAIRY_POOL_STRUCTURE.get(); }

    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ZSSConfig.SERVER.generateStructures.get()) return Optional.empty();
        int x = context.chunkPos().getMiddleBlockX() - FairyPoolPiece.RADIUS;
        int z = context.chunkPos().getMiddleBlockZ() - FairyPoolPiece.RADIUS;
        int size = FairyPoolPiece.SIZE;
        int[] heights = new int[size * size];
        NoiseColumn[] columns = new NoiseColumn[heights.length];
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int dx = 0; dx < size; dx++) for (int dz = 0; dz < size; dz++) {
            int index = dx * size + dz;
            int y = context.chunkGenerator().getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState()) - 1;
            heights[index] = y;
            low = Math.min(low, y);
            high = Math.max(high, y);
            if (high - low > 4) return Optional.empty();
            var biome = context.biomeSource().getNoiseBiome((x + dx) >> 2, y >> 2, (z + dz) >> 2,
                    context.randomState().sampler());
            if (!biome.is(BiomeTags.IS_OVERWORLD) || !context.validBiome().test(biome)) return Optional.empty();
            columns[index] = context.chunkGenerator().getBaseColumn(x + dx, z + dz,
                    context.heightAccessor(), context.randomState());
            if (!natural(columns[index].getBlock(y)) || !columns[index].getBlock(y + 1).isAir()) return Optional.empty();
        }
        int waterY = low - 1;
        if (waterY - 8 <= context.heightAccessor().getMinBuildHeight()
                || high + 4 >= context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        // Inspect every foundation column, including caves beneath otherwise level terrain.
        for (NoiseColumn column : columns) for (int y = waterY - 8; y <= waterY - 3; y++) {
            if (!natural(column.getBlock(y))) return Optional.empty();
        }
        BlockPos origin = new BlockPos(x, waterY, z);
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(origin.offset(FairyPoolPiece.RADIUS, 0, FairyPoolPiece.RADIUS),
                builder -> builder.addPiece(new FairyPoolPiece(origin, heights, seed))));
    }

    private static boolean natural(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.TERRACOTTA)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.SNOW_BLOCK);
    }
}
