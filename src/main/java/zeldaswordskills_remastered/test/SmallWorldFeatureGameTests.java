package zeldaswordskills_remastered.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.Storage;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallWorldFeatureGameTests {
    @GameTest(template="zssgametests.empty",templateNamespace="minecraft",batch="zssSmallFeatures")
    public static void placedFeaturesProduceSupportedLootAndFlowers(GameTestHelper helper) {
        var level=helper.getLevel();
        var placed=level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for(String name:java.util.List.of("surface_jars","underground_jars","nether_jars","surface_bomb_flowers",
                "underground_bomb_flowers","gossip_stone","song_pillar"))
            helper.assertTrue(placed.containsKey(DungeonLootTables.id(name)),"Missing placed feature: "+name);
        var configured=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        BlockPos origin=helper.absolutePos(new BlockPos(4,2,4));
        for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-3,-1,-3),origin.offset(3,-1,3)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        helper.assertTrue(configured.get(DungeonLootTables.id("jars")).place(level,level.getChunkSource().getGenerator(),RandomSource.create(12345L),origin),"Jar feature did not generate a cluster");
        int jars=0;
        for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-3,0,-3),origin.offset(3,0,3))) {
            if(level.getBlockEntity(pos) instanceof Storage storage) {
                helper.assertTrue(storage.saveWithoutMetadata().getString("LootTable").equals(DungeonLootTables.JAR.toString()),"World jar has no deferred loot");
                storage.clearContent();level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);jars++;
            }
        }
        helper.assertTrue(jars>0&&jars<=4,"Jar cluster size out of bounds");
        helper.assertTrue(!configured.get(DungeonLootTables.id("bomb_flowers")).place(level,level.getChunkSource().getGenerator(),RandomSource.create(12345L),origin),"Flowers generated without nearby lava");
        for(int dz=-3;dz<=3;dz++)level.setBlock(origin.offset(0,-1,dz),Blocks.LAVA.defaultBlockState(),2);
        helper.assertTrue(configured.get(DungeonLootTables.id("bomb_flowers")).place(level,level.getChunkSource().getGenerator(),RandomSource.create(12345L),origin),"Flowers did not generate by lava");
        helper.succeed();
    }
}
