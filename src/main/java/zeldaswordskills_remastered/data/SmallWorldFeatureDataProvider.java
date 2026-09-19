package zeldaswordskills_remastered.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SmallWorldFeatureDataProvider implements DataProvider {
    private final PackOutput output;
    public SmallWorldFeatureDataProvider(PackOutput output) {this.output=output;}
    @Override public String getName(){return "ZeldaSwordSkills_Remastered small world features";}
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes=new ArrayList<>();
        for(String feature:List.of("jars","bomb_flowers","gossip_stone","song_pillar")) {
            JsonObject configured=new JsonObject();configured.addProperty("type",DungeonLootTables.id(feature).toString());configured.add("config",new JsonObject());
            writes.add(save(cache,configured,"worldgen/configured_feature",feature));
        }
        placed(cache,writes,"surface_jars","jars",16,false,false);
        placed(cache,writes,"underground_jars","jars",2,true,false);
        placed(cache,writes,"nether_jars","jars",2,true,true);
        placed(cache,writes,"surface_bomb_flowers","bomb_flowers",1,false,false);
        placed(cache,writes,"underground_bomb_flowers","bomb_flowers",1,true,false);
        placed(cache,writes,"gossip_stone","gossip_stone",64,false,false);
        placed(cache,writes,"song_pillar","song_pillar",32,false,false);
        JsonObject pillarBiomes=new JsonObject();JsonArray values=new JsonArray();
        for(String biome:List.of("#minecraft:is_forest","#minecraft:is_jungle","#minecraft:is_taiga","#minecraft:is_mountain",
                "#minecraft:is_savanna","minecraft:plains","minecraft:sunflower_plains","minecraft:snowy_plains","minecraft:swamp","minecraft:mangrove_swamp"))values.add(biome);
        pillarBiomes.add("values",values);writes.add(save(cache,pillarBiomes,"tags/worldgen/biome","song_pillars"));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    private void placed(CachedOutput cache,List<CompletableFuture<?>> writes,String name,String feature,int rarity,boolean underground,boolean nether) {
        JsonObject placed=new JsonObject();placed.addProperty("feature",DungeonLootTables.id(feature).toString());
        JsonArray placements=new JsonArray();
        JsonObject frequency=new JsonObject();frequency.addProperty("type","minecraft:rarity_filter");frequency.addProperty("chance",rarity);placements.add(frequency);
        JsonObject square=new JsonObject();square.addProperty("type","minecraft:in_square");placements.add(square);
        JsonObject height=new JsonObject();
        if(underground) {
            height.addProperty("type","minecraft:height_range");JsonObject range=new JsonObject();range.addProperty("type","minecraft:uniform");
            JsonObject min=new JsonObject(),max=new JsonObject();min.addProperty("absolute",nether?8:-48);max.addProperty("absolute",nether?120:64);
            range.add("min_inclusive",min);range.add("max_inclusive",max);height.add("height",range);
        } else {height.addProperty("type","minecraft:heightmap");height.addProperty("heightmap","WORLD_SURFACE_WG");}
        placements.add(height);JsonObject biome=new JsonObject();biome.addProperty("type","minecraft:biome");placements.add(biome);
        placed.add("placement",placements);writes.add(save(cache,placed,"worldgen/placed_feature",name));
        JsonObject modifier=new JsonObject();modifier.addProperty("type","forge:add_features");modifier.addProperty("biomes",feature.equals("song_pillar")?"#zeldaswordskills_remastered:song_pillars":nether?"#minecraft:is_nether":"#minecraft:is_overworld");
        modifier.addProperty("features",DungeonLootTables.id(name).toString());modifier.addProperty("step","vegetal_decoration");
        writes.add(save(cache,modifier,"forge/biome_modifier",name));
    }
    private CompletableFuture<?> save(CachedOutput cache,JsonObject value,String folder,String name) {
        return DataProvider.saveStable(cache,value,output.createPathProvider(PackOutput.Target.DATA_PACK,folder).json(DungeonLootTables.id(name)));
    }
}
