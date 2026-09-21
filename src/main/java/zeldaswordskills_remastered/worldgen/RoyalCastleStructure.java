package zeldaswordskills_remastered.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Optional;

public final class RoyalCastleStructure extends Structure {
    public static final Codec<RoyalCastleStructure> CODEC = simpleCodec(RoyalCastleStructure::new);

    public RoyalCastleStructure(StructureSettings settings) { super(settings); }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!zeldaswordskills_remastered.config.ZSSConfig.SERVER.generateStructures.get()) return Optional.empty();
        RoyalCastleSite site = RoyalCastleWorldData.get(context.randomState());
        if (site == null || !site.startChunk().equals(context.chunkPos())) return Optional.empty();
        return Optional.of(new GenerationStub(site.origin().offset(108, 4, 108),
                builder -> builder.addPiece(new RoyalCastlePiece(site))));
    }

    @Override public StructureType<?> type() { return ZSSRegistries.ROYAL_CASTLE_STRUCTURE.get(); }
}
