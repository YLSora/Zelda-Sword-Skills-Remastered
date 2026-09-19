package zeldaswordskills_remastered.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import zeldaswordskills_remastered.data.SecretRoomDataProvider;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Optional;

/** Bounded terrain-only search: never loads neighbouring chunks during world generation. */
public final class SecretRoomStructure extends Structure {
    public static final Codec<SecretRoomStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance), Codec.BOOL.fieldOf("nether").forGetter(room -> room.nether)
    ).apply(instance, SecretRoomStructure::new));
    private final boolean nether;
    public SecretRoomStructure(StructureSettings settings, boolean nether) { super(settings); this.nether = nether; }
    @Override public StructureType<?> type() { return ZSSRegistries.SECRET_ROOM_STRUCTURE.get(); }

    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ZSSConfig.SERVER.generateStructures.get()) return Optional.empty();
        var random = context.random();
        int variant = random.nextInt(20);
        int size = nether ? 5 + variant % 4 : 5 + variant % 2;
        int x = context.chunkPos().getMinBlockX() + 4;
        int z = context.chunkPos().getMinBlockZ() + 4;
        var biome = context.biomeSource().getNoiseBiome((x+size/2)>>2, 16, (z+size/2)>>2, context.randomState().sampler());
        boolean ocean = !nether && biome.is(BiomeTags.IS_OCEAN);
        boolean mountain = !nether && biome.is(BiomeTags.IS_MOUNTAIN);
        int ceiling = nether ? 120 : context.chunkGenerator().getBaseHeight(x, z,
                Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState()) - 1;
        int bottom = Math.max(context.heightAccessor().getMinBuildHeight()+8, nether ? 8 : -48);
        if (ceiling - 6 <= bottom) return Optional.empty();
        // Cache just the room footprint's noise columns for all vertical attempts.
        var columns = new net.minecraft.world.level.NoiseColumn[size][size];
        for (int dx=0;dx<size;dx++) for(int dz=0;dz<size;dz++) columns[dx][dz] = context.chunkGenerator()
                .getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
        for (int attempt=0;attempt<12;attempt++) {
            int y = ocean ? ceiling - 4 : bottom + random.nextInt(ceiling - 5 - bottom);
            int invalid = 0;
            for(int dx=0;dx<size;dx++) for(int dz=0;dz<size;dz++) for(int dy=0;dy<5;dy++) {
                var state = columns[dx][dz].getBlock(y+dy);
                boolean natural = nether ? state.is(Blocks.NETHERRACK) || state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE)
                        : state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                        || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY);
                if (!(natural || ocean && !state.getFluidState().isEmpty())) invalid++;
            }
            if(invalid > size*size/2) continue;
            String environment = nether ? (random.nextFloat()<.25F ? "lava" : "nether")
                    : ocean ? "ocean" : mountain ? "mountain" : "land";
            String gate = "none";
            if (size>5 && random.nextFloat()<.25F) {
                if(random.nextInt(16)==0) gate="time_block";
                else if(random.nextInt(16)==0) gate="quake_stone";
                else if(!ocean && !environment.equals("lava")) {
                    if(random.nextInt(3)==0) gate=random.nextInt(3)==0 ? "heavy_block" : "peg_rusty";
                    else if(random.nextInt(3)==0) gate="door_locked";
                    else if(random.nextInt(3)==0) gate="light_block";
                    else gate="peg_wooden";
                } else gate="door_locked";
            }
            BlockPos origin = new BlockPos(x,y,z);
            var template = SecretRoomDataProvider.templateId(environment,gate,variant);
            Rotation rotation = Rotation.getRandom(random);
            BlockPos placement = switch(rotation) {
                case NONE -> origin;
                case CLOCKWISE_90 -> origin.offset(size-1,0,0);
                case CLOCKWISE_180 -> origin.offset(size-1,0,size-1);
                case COUNTERCLOCKWISE_90 -> origin.offset(0,0,size-1);
            };
            return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(
                    new SecretRoomPiece(context.structureTemplateManager(),template,placement,rotation))));
        }
        return Optional.empty();
    }
}
