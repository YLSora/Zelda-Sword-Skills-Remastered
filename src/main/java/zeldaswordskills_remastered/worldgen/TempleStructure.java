package zeldaswordskills_remastered.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Optional;

/** A vanilla Jigsaw structure whose generation can be disabled by the server config. */
public final class TempleStructure extends Structure {
    public static final Codec<TempleStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
            ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(structure -> structure.startJigsawName),
            Codec.intRange(0, 7).fieldOf("size").forGetter(structure -> structure.maxDepth),
            HeightProvider.CODEC.fieldOf("start_height").forGetter(structure -> structure.startHeight),
            Codec.BOOL.fieldOf("use_expansion_hack").forGetter(structure -> structure.useExpansionHack),
            Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(structure -> structure.projectStartToHeightmap),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(structure -> structure.maxDistanceFromCenter)
    ).apply(instance, TempleStructure::new));

    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final JigsawStructure delegate;

    public TempleStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool,
                           Optional<ResourceLocation> startJigsawName, int maxDepth, HeightProvider startHeight,
                           boolean useExpansionHack, Optional<Heightmap.Types> projectStartToHeightmap,
                           int maxDistanceFromCenter) {
        super(settings);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.delegate = new JigsawStructure(settings, startPool, startJigsawName, maxDepth, startHeight,
                useExpansionHack, projectStartToHeightmap, maxDistanceFromCenter);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return ZSSConfig.SERVER.generateStructures.get()
                ? delegate.findGenerationPoint(context) : Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return ZSSRegistries.TEMPLE_STRUCTURE.get();
    }
}
