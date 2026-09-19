package zeldaswordskills_remastered.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.config.ZSSConfig;

import java.util.Optional;

/** End biome selection alone does not guarantee terrain beneath a Jigsaw start. */
public final class EndTempleStructure extends Structure {
    public static final Codec<EndTempleStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance), StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.pool)
    ).apply(instance,EndTempleStructure::new));
    private final Holder<StructureTemplatePool> pool;
    public EndTempleStructure(StructureSettings settings,Holder<StructureTemplatePool> pool) { super(settings);this.pool=pool; }
    @Override public StructureType<?> type() { return ZSSRegistries.END_TEMPLE_STRUCTURE.get(); }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ZSSConfig.SERVER.generateStructures.get()) return Optional.empty();
        BlockPos origin=new BlockPos(context.chunkPos().getMinBlockX(),0,context.chunkPos().getMinBlockZ());
        return JigsawPlacement.addPieces(context,pool,Optional.empty(),1,origin,false,
                Optional.of(Heightmap.Types.WORLD_SURFACE_WG),32).filter(stub -> {
            BlockPos surface=stub.position();
            if(surface.getY()<=context.heightAccessor().getMinBuildHeight()+1
                    || (long)surface.getX()*surface.getX()+(long)surface.getZ()*surface.getZ()<=1024L*1024L) return false;
            return context.chunkGenerator().getBaseColumn(surface.getX(),surface.getZ(),context.heightAccessor(),context.randomState())
                    .getBlock(surface.getY()-1).is(net.minecraft.world.level.block.Blocks.END_STONE);
        });
    }
}
