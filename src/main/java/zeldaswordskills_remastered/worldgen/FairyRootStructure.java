package zeldaswordskills_remastered.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Optional;

/** Fits the sculpture to a cave floor without loading any neighbouring chunks. */
public final class FairyRootStructure extends Structure {
    public static final Codec<FairyRootStructure> CODEC = simpleCodec(FairyRootStructure::new);

    public FairyRootStructure(StructureSettings settings) { super(settings); }
    @Override public StructureType<?> type() { return ZSSRegistries.FAIRY_ROOT_STRUCTURE.get(); }

    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ZSSConfig.SERVER.generateStructures.get()) return Optional.empty();
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        NoiseColumn center = context.chunkGenerator().getBaseColumn(x + 8, z + 8,
                context.heightAccessor(), context.randomState());
        NoiseColumn[] columns = new NoiseColumn[256];
        long seed = context.random().nextLong();
        FairyRootPiece.Shape footprint = null;
        int ceiling = Math.min(120, context.heightAccessor().getMaxBuildHeight() - 1);
        for (int floor = ceiling - FairyRootPiece.MIN_HEIGHT - 2; floor >= 31; floor--) {
            if (!natural(center.getBlock(floor)) || !center.getBlock(floor + 1).isAir()) continue;
            var biome = context.biomeSource().getNoiseBiome((x + 8) >> 2, (floor + 1) >> 2, (z + 8) >> 2,
                    context.randomState().sampler());
            if ((!biome.is(Biomes.CRIMSON_FOREST) && !biome.is(Biomes.WARPED_FOREST))
                    || !context.validBiome().test(biome)) continue;
            if (footprint == null) footprint = new FairyRootPiece.Shape(seed, FairyRootPiece.MIN_HEIGHT);
            int[] heights = new int[256];
            boolean fits = true;
            for (int dx = 0; dx < 16 && fits; dx++) for (int dz = 0; dz < 16 && fits; dz++) {
                int index = dx * 16 + dz;
                if (columns[index] == null) columns[index] = context.chunkGenerator().getBaseColumn(x + dx, z + dz,
                        context.heightAccessor(), context.randomState());
                int surface = floor + 3;
                while (surface >= floor - 4 && !natural(columns[index].getBlock(surface))) surface--;
                heights[index] = Math.max(floor - 4, surface);
                if (!footprint.needsFoundation(dx, dz)) continue;
                if (surface < floor - 4 || !columns[index].getBlock(surface + 1).isAir()) { fits = false; break; }
                for (int depth = 0; depth < 3; depth++) if (!natural(columns[index].getBlock(surface - depth))) fits = false;
            }
            int base = floor + 1;
            if (!fits) continue;
            int height = ceilingHeight(columns, base, ceiling);
            if (height == 0) continue;
            var shape = new FairyRootPiece.Shape(seed, height);
            for (int dx = 0; dx < 16 && fits; dx++) for (int dz = 0; dz < 16 && fits; dz++) {
                var localBiome = context.biomeSource().getNoiseBiome((x + dx) >> 2, base >> 2, (z + dz) >> 2,
                        context.randomState().sampler());
                if ((!localBiome.is(Biomes.CRIMSON_FOREST) && !localBiome.is(Biomes.WARPED_FOREST))
                        || !context.validBiome().test(localBiome)) { fits = false; break; }
                for (int y = 0; y < height; y++) {
                    BlockState state = columns[dx * 16 + dz].getBlock(base + y);
                    // Blend only the feet and tapered tip into rock; never excavate the orb's cavity.
                    if (shape.needsSpace(dx, y, dz) && !state.isAir() && !((y <= 3 || y >= height - 8) && natural(state))) {
                        fits = false;
                        break;
                    }
                }
            }
            if (!fits) continue;
            BlockPos origin = new BlockPos(x, base, z);
            boolean warped = biome.is(Biomes.CRIMSON_FOREST);
            return Optional.of(new GenerationStub(origin.offset(8, 0, 8), builder ->
                    builder.addPiece(new FairyRootPiece(origin, heights, seed, warped, height))));
        }
        return Optional.empty();
    }

    private static int ceilingHeight(NoiseColumn[] columns, int base, int ceiling) {
        for (int height = FairyRootPiece.MIN_HEIGHT; height <= FairyRootPiece.MAX_HEIGHT
                && base + height + 1 < ceiling; height++) {
            boolean anchored = true;
            for (int x = 0; x < 16 && anchored; x++) for (int z = 0; z < 16 && anchored; z++) {
                if (!FairyRootPiece.Shape.ceilingAnchor(x, z)) continue;
                anchored = natural(columns[x * 16 + z].getBlock(base + height))
                        && natural(columns[x * 16 + z].getBlock(base + height + 1));
            }
            if (anchored) return height;
        }
        return 0;
    }

    static boolean natural(BlockState state) {
        return state.is(Blocks.NETHERRACK) || state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.CRIMSON_NYLIUM) || state.is(Blocks.WARPED_NYLIUM);
    }
}
