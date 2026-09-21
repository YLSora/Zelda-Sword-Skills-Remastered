package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Places only the current chunk's sparse template; no neighboring chunks are loaded. */
public final class RoyalCastlePiece extends StructurePiece {
    private final RoyalCastleSite site;

    public RoyalCastlePiece(RoyalCastleSite site) {
        super(ZSSRegistries.ROYAL_CASTLE_PIECE.get(), 0, site.bounds());
        this.site = site;
    }

    public RoyalCastlePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ZSSRegistries.ROYAL_CASTLE_PIECE.get(), tag);
        site = RoyalCastleSite.load(tag.getCompound("Site"));
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.put("Site", site.save());
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        int minX = Math.max(clip.minX(), boundingBox.minX());
        int maxX = Math.min(clip.maxX(), boundingBox.maxX());
        int minZ = Math.max(clip.minZ(), boundingBox.minZ());
        int maxZ = Math.min(clip.maxZ(), boundingBox.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            int natural = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            boolean inside = site.contains(x, z);
            double weight = blendWeight(x - site.x(), z - site.z());
            if (weight <= 0) continue;
            int top = inside ? site.y() - 1 : (int) Math.round(natural + (site.y() + 4 - natural) * weight);
            // Seal the foundation, including shallow caves, with native stone/dirt.
            int bottom = Math.max(level.getMinBuildHeight() + 1, Math.min(natural - 5, site.y() - 24));
            for (int y = bottom; y <= top; y++) {
                level.setBlock(pos.set(x, y, z), (!inside && y == top ? Blocks.GRASS_BLOCK
                        : y >= top - 3 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            int clearTop = Math.max(surface, inside ? site.y() + RoyalCastleSite.HEIGHT - 1 : natural + 2);
            for (int y = top + 1; y <= clearTop; y++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        int tileX = (chunk.getMinBlockX() - site.x()) >> 4;
        int tileZ = (chunk.getMinBlockZ() - site.z()) >> 4;
        if (tileX < 0 || tileX >= 14 || tileZ < 0 || tileZ >= 14) return;
        var id = ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "royal_castle/tile_" + tileX + "_" + tileZ);
        var template = level.getLevel().getStructureManager().get(id)
                .orElseThrow(() -> new IllegalStateException("Missing royal castle template " + id));
        var settings = new StructurePlaceSettings().setBoundingBox(clip).setIgnoreEntities(true)
                .setKeepLiquids(false).setKnownShape(true);
        template.placeInWorld(level, site.origin().offset(tileX * 16, 0, tileZ * 16), BlockPos.ZERO,
                settings, random, Block.UPDATE_CLIENTS);
    }

    public static double blendWeight(int x, int z) {
        double dx = Math.max(0, Math.max(-x, x - (RoyalCastleSite.SIZE - 1)));
        double dz = Math.max(0, Math.max(-z, z - (RoyalCastleSite.SIZE - 1)));
        double distance = Math.sqrt(dx * dx + dz * dz);
        // Rounded, subtly irregular contours avoid a square cliff around the template.
        double width = 13 + 2 * Math.sin(x * .07) * Math.cos(z * .09);
        double t = Math.min(1, distance / width);
        return 1 - t * t * (3 - 2 * t);
    }
}
