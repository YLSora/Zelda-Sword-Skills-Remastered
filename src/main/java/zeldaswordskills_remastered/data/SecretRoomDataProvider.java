package zeldaswordskills_remastered.data;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import zeldaswordskills_remastered.block.LockedChestBlock;
import zeldaswordskills_remastered.block.LockedDoorBlock;
import zeldaswordskills_remastered.block.MechanismBlocks;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class SecretRoomDataProvider implements DataProvider {
    public static final List<String> ENVIRONMENTS = List.of("land","mountain","ocean","nether","lava");
    public static final List<String> GATES = List.of("none","peg_wooden","peg_rusty","light_block","heavy_block","time_block","quake_stone","door_locked");
    private final PackOutput output;
    public SecretRoomDataProvider(PackOutput output) { this.output=output; }
    public static ResourceLocation templateId(String environment,String gate,int variant) {
        return DungeonLootTables.id("secret_room/"+environment+"/"+gate+"/room_"+String.format(Locale.ROOT,"%02d",variant));
    }
    @Override public String getName() { return "ZeldaSwordSkills_Remastered secret rooms"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes=new ArrayList<CompletableFuture<?>>();
        var templates=output.createPathProvider(PackOutput.Target.DATA_PACK,"structures");
        try {
            for(String environment:ENVIRONMENTS) for(String gate:GATES) for(int variant=0;variant<20;variant++) {
                var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(room(environment,gate,variant),bytes);
                byte[] data=bytes.toByteArray();
                cache.writeIfNeeded(templates.file(templateId(environment,gate,variant),"nbt"),data,Hashing.sha256().hashBytes(data));
            }
        } catch(IOException exception) { return CompletableFuture.failedFuture(exception); }
        for(boolean nether:new boolean[]{false,true}) {
            String name=nether?"nether_secret_rooms":"secret_rooms";
            JsonObject structure=new JsonObject();
            structure.addProperty("type",DungeonLootTables.id("secret_room").toString());
            structure.addProperty("nether",nether);
            structure.addProperty("biomes",nether?"#minecraft:is_nether":"#minecraft:is_overworld");
            structure.addProperty("step","underground_structures");
            structure.addProperty("terrain_adaptation","none"); structure.add("spawn_overrides",new JsonObject());
            writes.add(DataProvider.saveStable(cache,structure,output.createPathProvider(PackOutput.Target.DATA_PACK,"worldgen/structure").json(DungeonLootTables.id(name))));
            JsonObject set=new JsonObject(),entry=new JsonObject(),placement=new JsonObject();
            entry.addProperty("structure",DungeonLootTables.id(name).toString());entry.addProperty("weight",1);
            JsonArray entries=new JsonArray();entries.add(entry);set.add("structures",entries);
            placement.addProperty("type","minecraft:random_spread");placement.addProperty("salt",nether?27182819:31415927);
            placement.addProperty("spacing",nether?4:6);placement.addProperty("separation",nether?2:3);
            placement.addProperty("frequency",.25F);set.add("placement",placement);
            writes.add(DataProvider.saveStable(cache,set,output.createPathProvider(PackOutput.Target.DATA_PACK,"worldgen/structure_set").json(DungeonLootTables.id(name))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    private record Placed(BlockState state,CompoundTag tag) {}
    private static CompoundTag room(String environment,String gate,int variant) {
        boolean nether=environment.equals("nether")||environment.equals("lava"), lava=environment.equals("lava"), ocean=environment.equals("ocean");
        int size=nether?5+variant%4:5+variant%2, center=size/2;
        boolean fairy=environment.equals("mountain") || environment.equals("land") && variant%10==0;
        boolean water=ocean||fairy;
        Block shell=nether?ZSSRegistries.SECRET_STONE_NETHER_BRICKS.get():water?ZSSRegistries.SECRET_STONE_COBBLESTONE.get():ZSSRegistries.SECRET_STONE_STONE.get();
        BlockState wall=shell.defaultBlockState().setValue(MechanismBlocks.SecretStone.UNBREAKABLE,!gate.equals("none"));
        var blocks=new LinkedHashMap<BlockPos,Placed>();
        for(int y=0;y<5;y++) for(int z=0;z<size;z++) for(int x=0;x<size;x++) {
            boolean edge=y==0||y==4||x==0||z==0||x==size-1||z==size-1;
            BlockState inside=y==1 && (water||lava)?(lava?Blocks.LAVA:Blocks.WATER).defaultBlockState():Blocks.AIR.defaultBlockState();
            blocks.put(new BlockPos(x,y,z),new Placed(edge?wall:inside,null));
        }
        int floor=water||lava?2:1;
        if(!gate.equals("none")) {
            Block entrance=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(DungeonLootTables.id(gate));
            BlockState lower=entrance.defaultBlockState(),upper=lower;
            if(entrance instanceof LockedDoorBlock) {
                lower=lower.setValue(LockedDoorBlock.FACING,Direction.NORTH).setValue(LockedDoorBlock.HALF,DoubleBlockHalf.LOWER);
                upper=lower.setValue(LockedDoorBlock.HALF,DoubleBlockHalf.UPPER);
            } else if(entrance instanceof MechanismBlocks.Peg) upper=Blocks.AIR.defaultBlockState();
            blocks.put(new BlockPos(center,floor,0),new Placed(lower,null));
            blocks.put(new BlockPos(center,floor+1,0),new Placed(upper,null));
        }
        CompoundTag core=entity("secret_room_core");core.putBoolean("fairy_pool",fairy);
        core.putInt("room_radius",size/2);
        blocks.put(new BlockPos(center,0,center),new Placed(ZSSRegistries.SECRET_ROOM_CORE.get().defaultBlockState(),core));
        placeTerrain(blocks, environment, variant, size, center, floor, shell);
        boolean locked=variant%3==0;
        ResourceLocation loot=gate.equals("none")?DungeonLootTables.secret(environment,locked)
                :DungeonLootTables.id("chests/secret/"+environment+"/"+gate+(locked?"_locked":""));
        chest(blocks,new BlockPos(size-2,floor,size-2),locked,false,loot);
        if(variant%10==1 && size>5) {
            boolean invisible=!gate.equals("time_block") && variant%3==1;
            boolean secondLocked=invisible || variant%3==0;
            chest(blocks,new BlockPos(1,floor,size-2),secondLocked,invisible,DungeonLootTables.secret(environment,secondLocked));
        }
        if(lava) {
            blocks.put(new BlockPos(size-2,1,size-2),new Placed(wall,null));
            blocks.put(new BlockPos(1,1,size-2),new Placed(wall,null));
        }
        for(int i=0;i<1+variant%3;i++) {
            BlockPos pos=new BlockPos(1+i%Math.max(1,size-3),floor,1);
            CompoundTag jar=entity("storage");jar.putString("LootTable",DungeonLootTables.JAR.toString());
            blocks.put(pos,new Placed(ZSSRegistries.CERAMIC_JAR.get().defaultBlockState(),jar));
        }
        CompoundTag root=new CompoundTag();root.put("size",ints(size,5,size));root.put("entities",new ListTag());
        Map<BlockState,Integer> palette=new LinkedHashMap<>();ListTag list=new ListTag();
        blocks.forEach((pos,placed)->{
            CompoundTag block=new CompoundTag();block.put("pos",ints(pos.getX(),pos.getY(),pos.getZ()));
            block.putInt("state",palette.computeIfAbsent(placed.state(),key->palette.size()));
            if(placed.tag()!=null) block.put("nbt",placed.tag());list.add(block);
        });
        ListTag states=new ListTag();palette.keySet().forEach(state->states.add(NbtUtils.writeBlockState(state)));
        root.put("palette",states);root.put("blocks",list);return NbtUtils.addCurrentDataVersion(root);
    }

    private static void placeTerrain(Map<BlockPos,Placed> blocks, String environment, int variant,
                                     int size, int center, int floor, Block shell) {
        int[][] anchors = {{2, 1}, {size - 3, 1}, {1, size - 3}, {size - 2, size - 2}};
        int count = 1 + Math.floorMod(variant, 2);
        for (int index = 0; index < count; index++) {
            int[] anchor = anchors[Math.floorMod(variant + index * 3, anchors.length)];
            int radius = 1 + terrainNoise(environment, variant, anchor[0], anchor[1], index) % 2;
            int height = 1 + terrainNoise(environment, variant, anchor[1], anchor[0], index + 11) % 2;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                int x = anchor[0] + dx;
                int z = anchor[1] + dz;
                if (x <= 0 || x >= size - 1 || z <= 0 || z >= size - 1
                        || x == center && z == center
                        || x == size - 2 && z == size - 2
                        || x == 1 && z == size - 2
                        || Math.max(Math.abs(dx), Math.abs(dz)) == radius
                        && terrainNoise(environment, variant, x, z, index + 23) % 3 == 0) continue;
                int columnHeight = Math.max(1, height - Math.max(Math.abs(dx), Math.abs(dz)) / 2);
                for (int dy = 0; dy < columnHeight; dy++) {
                    BlockPos pos = new BlockPos(x, floor + dy, z);
                    Placed existing = blocks.get(pos);
                    if (existing == null || !existing.state().isAir() || existing.tag() != null) continue;
                    boolean glowstone = dy == columnHeight - 1
                            && terrainNoise(environment, variant, x, z, index + 37) % 24 == 0;
                    blocks.put(pos, new Placed((glowstone ? Blocks.GLOWSTONE : shell).defaultBlockState(), null));
                }
            }
        }
    }

    private static int terrainNoise(String environment, int variant, int x, int z, int salt) {
        long value = 0x9E3779B97F4A7C15L ^ (long) environment.hashCode() * 0x632BE59BD9B4E019L
                ^ (long) variant * 0x85157AF5L ^ (long) (x * 73428767) ^ (long) (z * 912931L)
                ^ (long) salt * 0x27D4EB2DL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return (int) (value ^ (value >>> 31)) & 0x7FFFFFFF;
    }

    private static void chest(Map<BlockPos,Placed> blocks,BlockPos pos,boolean locked,boolean invisible,ResourceLocation loot) {
        BlockState state=locked?(invisible?ZSSRegistries.CHEST_INVISIBLE.get():ZSSRegistries.CHEST_LOCKED.get()).defaultBlockState()
                .setValue(LockedChestBlock.FACING,Direction.NORTH).setValue(LockedChestBlock.VISIBLE,!invisible)
                :Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH);
        CompoundTag tag=entity("storage");if(!locked) tag.putString("id","minecraft:chest");
        tag.putString("LootTable",loot.toString());blocks.put(pos,new Placed(state,tag));
    }
    private static CompoundTag entity(String name) { CompoundTag tag=new CompoundTag();tag.putString("id",DungeonLootTables.id(name).toString());return tag; }
    private static ListTag ints(int... values) { ListTag list=new ListTag();for(int value:values)list.add(IntTag.valueOf(value));return list; }
}
