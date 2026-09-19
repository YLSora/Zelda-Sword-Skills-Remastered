package zeldaswordskills_remastered.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.entity.SecretRoomCore;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.data.SecretRoomDataProvider;
import zeldaswordskills_remastered.world.ZSSWorldData;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SecretRoomGameTests {
    @GameTest(template="zssgametests.empty",templateNamespace="minecraft",batch="zssSecretFairies")
    public static void fairyPoolDoesNotRefillBeforePersistedCooldown(GameTestHelper helper) {
        var level=helper.getLevel();
        helper.runAfterDelay(20-level.getGameTime()%20,()->{
            BlockPos origin=helper.absolutePos(new BlockPos(1,1,1));
            level.getStructureManager().get(SecretRoomDataProvider.templateId("land","none",0)).orElseThrow()
                    .placeInWorld(level,origin,origin,new StructurePlaceSettings(),level.random,2);
            var core=(SecretRoomCore)level.getBlockEntity(origin.offset(2,0,2));
            var area=new net.minecraft.world.phys.AABB(core.getBlockPos()).inflate(8);
            var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"[Secret_Fairies]"));
            player.setPos(core.getBlockPos().getX()+.5,core.getBlockPos().getY()+2,core.getBlockPos().getZ()+.5);level.addFreshEntity(player);
            var id=core.instanceId(level);ZSSWorldData.get(level).setDungeonState(id,false,0L);
            try {
                SecretRoomCore.tick(level,core.getBlockPos(),core.getBlockState(),core);
                var fairies=level.getEntitiesOfClass(zeldaswordskills_remastered.entity.FairyCreature.class,area);
                helper.assertTrue(fairies.size()==3,"Fairy pool did not supply three fairies");
                long cooldown=ZSSWorldData.get(level).dungeonState(id).cooldownUntil();
                helper.assertTrue(cooldown>=level.getGameTime()+48000&&cooldown<=level.getGameTime()+168000,"Fairy refill must take two to seven days");
                fairies.forEach(net.minecraft.world.entity.Entity::discard);
                core.load(core.saveWithoutMetadata());
                var saved=ZSSWorldData.load(ZSSWorldData.get(level).save(new CompoundTag()));
                helper.assertTrue(saved.dungeonState(id).cooldownUntil()==cooldown,"Fairy refill deadline lost on save");
                SecretRoomCore.tick(level,core.getBlockPos(),core.getBlockState(),core);
                helper.assertTrue(level.getEntitiesOfClass(zeldaswordskills_remastered.entity.FairyCreature.class,area).isEmpty(),"Room reload bypassed fairy cooldown");
                ZSSWorldData.get(level).setDungeonState(id,true,level.getGameTime());
                SecretRoomCore.tick(level,core.getBlockPos(),core.getBlockState(),core);
                helper.assertTrue(level.getEntitiesOfClass(zeldaswordskills_remastered.entity.FairyCreature.class,area).size()==3,"Expired fairy pool did not refill");
            } finally {player.discard();level.getEntitiesOfClass(zeldaswordskills_remastered.entity.FairyCreature.class,area).forEach(net.minecraft.world.entity.Entity::discard);}
            helper.succeed();
        });
    }
    @GameTest(template="zssgametests.empty",templateNamespace="minecraft",batch="zssSecretGeneration",timeoutTicks=400)
    public static void fixedSeedsGenerateHiddenRoomsAndPersistPieces(GameTestHelper helper) {
        var registries=helper.getLevel().registryAccess();
        var sets=registries.registryOrThrow(Registries.STRUCTURE_SET);
        for(boolean nether:new boolean[]{false,true}) {
            var level=helper.getLevel().getServer().getLevel(nether?Level.NETHER:Level.OVERWORLD);
            var generator=(NoiseBasedChunkGenerator)(nether ? level.getChunkSource().getGenerator()
                    : registries.registryOrThrow(Registries.WORLD_PRESET).get(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL).createWorldDimensions().overworld());
            var id=DungeonLootTables.id(nether?"nether_secret_rooms":"secret_rooms");
            var structure=registries.registryOrThrow(Registries.STRUCTURE).get(id);
            var placement=(RandomSpreadStructurePlacement)sets.get(id).placement();
            var placementJson=net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC
                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE,placement).result().orElseThrow().getAsJsonObject();
            helper.assertTrue(placement.spacing()>placement.separation()&&placement.separation()>=2
                    && placementJson.get("frequency").getAsFloat()==.25F,"Secret-room placement frequency or separation changed");
            for(long seed:new long[]{0L,12345L,987654321L}) {
                var random=RandomState.create(generator.generatorSettings().value(),registries.registryOrThrow(Registries.NOISE).asLookup(),seed);
                var state=ChunkGeneratorStructureState.createForNormal(random,seed,generator.getBiomeSource(),sets.asLookup());
                boolean found=false;
                for(int region=0;region<256&&!found;region++) {
                    var chunk=placement.getPotentialStructureChunk(seed,(region%16-8)*placement.spacing(),(region/16-8)*placement.spacing());
                    if(!placement.isStructureChunk(state,chunk.x,chunk.z))continue;
                    var start=structure.generate(registries,generator,generator.getBiomeSource(),random,level.getStructureManager(),seed,chunk,0,level,structure.biomes()::contains);
                    if(!start.isValid())continue;
                    var box=start.getBoundingBox();
                    helper.assertTrue(box.minY()>level.getMinBuildHeight()&&box.maxY()<(nether?128:level.getMaxBuildHeight()),"Secret room exceeds terrain bounds");
                    var context=net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level);
                    var tag=start.getPieces().get(0).createTag(context);
                    var reloaded=new zeldaswordskills_remastered.worldgen.SecretRoomPiece(context,tag);
                    helper.assertTrue(tag.equals(reloaded.createTag(context)),"Secret piece lost its template, position or rotation on reload");
                    ZeldaSwordSkills_Remastered.LOGGER.info("Secret generation verified: {} seed={} box={}",id,seed,box);found=true;
                }
                helper.assertTrue(found,"No secret room generated: "+id+" seed="+seed);
            }
        }
        helper.succeed();
    }
    @GameTest(template="zssgametests.empty",templateNamespace="minecraft",batch="zssSecretRooms")
    public static void entrancesAndDiscoverySurviveReload(GameTestHelper helper) {
        helper.runAfterDelay(20-helper.getLevel().getGameTime()%20, () -> {
        var level=helper.getLevel();BlockPos origin=helper.absolutePos(new BlockPos(1,1,1));
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"[Secret_Room]"));
        level.addFreshEntity(player);
        try {
            for(String gate:SecretRoomDataProvider.GATES) for(Rotation rotation:Rotation.values()) {
                var template=level.getStructureManager().get(SecretRoomDataProvider.templateId("land",gate,1)).orElseThrow();
                var settings=new StructurePlaceSettings().setRotation(rotation).setRotationPivot(new BlockPos(3,0,3));
                template.placeInWorld(level,origin,origin,settings,level.random,2);
                var core=(SecretRoomCore)level.getBlockEntity(origin.offset(3,0,3));
                helper.assertTrue(core!=null,"Secret room lost its core");
                var entrance=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(new BlockPos(3,1,0),net.minecraft.world.level.block.Mirror.NONE,rotation,new BlockPos(3,0,3)).offset(origin);
                if(!gate.equals("none")) helper.assertTrue(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(entrance).getBlock()).equals(DungeonLootTables.id(gate)),"Gate lost after rotation: "+gate);
                var id=core.instanceId(level);ZSSWorldData.get(level).setDungeonState(id,false,0);
                player.setPos(core.getBlockPos().getX()+.5,core.getBlockPos().getY()+1,core.getBlockPos().getZ()+.5);
                int discoveries=zeldaswordskills_remastered.capability.ZSSCapabilities.get(player).resolve().orElseThrow().secretRooms();
                SecretRoomCore.tick(level,core.getBlockPos(),core.getBlockState(),core);
                helper.assertTrue(ZSSWorldData.get(level).dungeonState(id).completed()
                        && zeldaswordskills_remastered.capability.ZSSCapabilities.get(player).resolve().orElseThrow().secretRooms()==discoveries+1,"Entering did not discover the room");
                SecretRoomCore.tick(level,core.getBlockPos(),core.getBlockState(),core);
                helper.assertTrue(zeldaswordskills_remastered.capability.ZSSCapabilities.get(player).resolve().orElseThrow().secretRooms()==discoveries+1,"Repeated entry duplicated discovery");
                long now=level.getGameTime();
                helper.assertTrue(core.saveWithoutMetadata().contains("fairy_pool"),"Secret settings not saved");
                ZSSWorldData.get(level).setDungeonState(id,true,now+48000);
                core.load(core.saveWithoutMetadata());
                var restored=ZSSWorldData.load(ZSSWorldData.get(level).save(new CompoundTag()));
                helper.assertTrue(restored.dungeonState(id).completed()&&restored.dungeonState(id).cooldownUntil()==now+48000,"Discovery or fairy cooldown lost on reload");
            }
        } finally {
            player.discard();
            for(BlockPos pos:BlockPos.betweenClosed(origin,origin.offset(7,5,7))) {
                if(level.getBlockEntity(pos) instanceof zeldaswordskills_remastered.block.entity.StageNineBlockEntities.Storage storage) storage.clearContent();
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            }
        }
        helper.succeed();
        });
    }
}
