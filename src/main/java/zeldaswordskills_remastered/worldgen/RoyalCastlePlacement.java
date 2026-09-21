package zeldaswordskills_remastered.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Optional;

public final class RoyalCastlePlacement extends StructurePlacement {
    public static final Codec<RoyalCastlePlacement> CODEC = Codec.unit(RoyalCastlePlacement::new);

    public RoyalCastlePlacement() {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, 0, Optional.empty());
    }

    @Override protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        RoyalCastleSite site = RoyalCastleWorldData.get(state.randomState());
        return site != null && site.startChunk().x == x && site.startChunk().z == z;
    }

    @Override public StructurePlacementType<?> type() { return ZSSRegistries.ROYAL_CASTLE_PLACEMENT.get(); }
}
