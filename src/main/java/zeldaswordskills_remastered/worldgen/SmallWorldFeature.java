package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import zeldaswordskills_remastered.block.BombFlowerBlock;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Small decorations; placement frequency, height and biome selection live in the datapack. */
public final class SmallWorldFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { JARS, BOMB_FLOWERS, GOSSIP_STONE, SONG_PILLAR }
    private final Kind kind;
    public SmallWorldFeature(Kind kind) { super(NoneFeatureConfiguration.CODEC);this.kind=kind; }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if(kind==Kind.SONG_PILLAR)return pillar(context);
        var world=context.level();var random=context.random();
        int remaining=kind==Kind.GOSSIP_STONE?1:kind==Kind.BOMB_FLOWERS?6:1+random.nextInt(4);
        int placed=0;
        for(int attempt=0;attempt<(kind==Kind.GOSSIP_STONE?1:64)&&remaining>0;attempt++) {
            int spread=kind==Kind.BOMB_FLOWERS?8:4;
            BlockPos pos=kind==Kind.GOSSIP_STONE?context.origin():context.origin().offset(random.nextInt(spread)-random.nextInt(spread),
                    random.nextInt(4)-random.nextInt(4),random.nextInt(spread)-random.nextInt(spread));
            if(!world.ensureCanWrite(pos)||!world.isEmptyBlock(pos)||!world.getBlockState(pos.below()).isFaceSturdy(world,pos.below(),Direction.UP))continue;
            if(kind==Kind.BOMB_FLOWERS) {
                var state=ZSSRegistries.BOMB_FLOWER.get().defaultBlockState().setValue(BombFlowerBlock.AGE,random.nextInt(4));
                if(world.getBlockState(pos.below()).is(Blocks.COBBLESTONE)||!state.canSurvive(world,pos))continue;
                world.setBlock(pos,state,2);
            } else if(kind==Kind.JARS) {
                world.setBlock(pos,ZSSRegistries.CERAMIC_JAR.get().defaultBlockState(),2);
                if(world.getBlockEntity(pos) instanceof StageNineBlockEntities.Storage storage) storage.setLootTable(DungeonLootTables.JAR,random.nextLong());
            } else {
                if(!world.canSeeSky(pos))continue;
                world.setBlock(pos,ZSSRegistries.GOSSIP_STONE.get().defaultBlockState(),2);
                if(world.getBlockEntity(pos) instanceof StageNineBlockEntities.GossipStone stone) {
                    CompoundTag tag=new CompoundTag();tag.putString("message","message.zeldaswordskills_remastered.gossip.hint."+random.nextInt(12));
                    stone.load(tag);stone.setChanged();
                }
            }
            placed++;remaining--;
        }
        return placed>0;
    }
    private boolean pillar(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var world=context.level();var pos=context.origin();var random=context.random();
        // Check the whole footprint before writing so generation never cuts into another structure.
        for(BlockPos check:BlockPos.betweenClosed(pos.offset(-2,0,-2),pos.offset(2,5,2))) {
            if(!world.ensureCanWrite(check)||!world.getBlockState(check).canBeReplaced()||world.getBlockEntity(check)!=null)return false;
            if(check.getY()==pos.getY()&&!world.getBlockState(check.below()).isFaceSturdy(world,check.below(),Direction.UP))return false;
        }
        var biome=world.getBiome(pos);
        var song=biome.is(net.minecraft.world.level.biome.Biomes.SWAMP)||biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)
                ?ZSSContentIds.SOARING:biome.is(BiomeTags.IS_SAVANNA)?ZSSContentIds.SUN:null;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            if(Math.abs(dx)+Math.abs(dz)>2)continue;
            var block=random.nextFloat()<.2F?Blocks.MOSSY_STONE_BRICKS:random.nextFloat()<.2F?Blocks.CRACKED_STONE_BRICKS:Blocks.STONE_BRICKS;
            world.setBlock(pos.offset(dx,0,dz),block.defaultBlockState(),2);
        }
        int height=song!=null||random.nextBoolean()?4:2;
        for(int y=1;y<=height;y++)world.setBlock(pos.above(y),(y==1?Blocks.CRACKED_STONE_BRICKS:Blocks.MOSSY_STONE_BRICKS).defaultBlockState(),2);
        if(height==4)for(Direction direction:Direction.Plane.HORIZONTAL) {
            if(song==null&&random.nextBoolean())continue;
            world.setBlock(pos.above(4).relative(direction),Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING,direction.getOpposite()).setValue(StairBlock.HALF,Half.TOP),2);
        }
        if(song!=null) {
            world.setBlock(pos.above(4),ZSSRegistries.SECRET_STONE_STONE_BRICKS.get().defaultBlockState(),2);
            world.setBlock(pos.above(5),ZSSRegistries.INSCRIPTION.get().defaultBlockState(),2);
            if(world.getBlockEntity(pos.above(5)) instanceof StageNineBlockEntities.Inscription inscription) {
                CompoundTag tag=new CompoundTag();tag.putString("song",song.toString());inscription.load(tag);inscription.setChanged();
            }
        }
        return true;
    }
}
