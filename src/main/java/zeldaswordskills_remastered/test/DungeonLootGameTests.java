package zeldaswordskills_remastered.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.Storage;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.data.DungeonStructureDataProvider;
import zeldaswordskills_remastered.data.SecretRoomDataProvider;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.DungeonType;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DungeonLootGameTests {
    @GameTest(template="zssgametests.empty",templateNamespace="minecraft",batch="zssLoot")
    public static void templatesReferenceLoadedLootAndRewardsHaveStableIds(GameTestHelper helper) {
        var level=helper.getLevel();
        for(DungeonType type:DungeonType.values()) {
            for(int variant=0;variant<20;variant++) checkTemplate(helper,DungeonStructureDataProvider.templateId(type,variant));
            String pendant=switch(type) { case DESERT->"pendant_courage";case ICE->"pendant_power";case WATER->"pendant_wisdom";default->null; };
            var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
            var table=level.getServer().getLootData().getLootTable(DungeonLootTables.temple(type,true));
            boolean themed=false;
            int goddessHarps = 0;
            for(long seed=1;seed<=100;seed++) {
                var loot=table.getRandomItems(params,seed);
                helper.assertTrue(loot.stream().noneMatch(stack->stack.is(ZSSRegistries.getItem("heart_piece"))),"Temple chest contained a heart piece: "+type);
                if(pendant!=null) helper.assertTrue(loot.stream().anyMatch(stack->stack.is(ZSSRegistries.getItem(pendant))),"Temple lost pendant: "+type);
                int skillCount=loot.stream().filter(stack->stack.is(ZSSRegistries.getItem("skill_orb"))).mapToInt(ItemStack::getCount).sum();
                helper.assertTrue(skillCount>=1&&skillCount<=3,"Temple skill orb count is outside 1-3: "+type+" ("+skillCount+")");
                loot.stream().filter(stack->stack.is(ZSSRegistries.getItem("skill_orb"))).forEach(stack->
                        helper.assertTrue(ZSSContentIds.SKILLS.stream().filter(id->!id.equals(ZSSContentIds.BONUS_HEART))
                                .anyMatch(id->id.toString().equals(stack.getOrCreateTag().getString("skill"))),"Invalid or bonus-heart random skill"));
                themed|=loot.stream().anyMatch(stack->java.util.Arrays.stream(themedItems(type))
                        .anyMatch(name->stack.is(ZSSRegistries.getItem(name))));
            }
            if (type == DungeonType.ICE) {
                var iceSeeds = level.getServer().getLootData().getLootTable(DungeonLootTables.temple(type, true));
                for (long seed = 1; seed <= 10_000; seed++)
                    goddessHarps += (int) iceSeeds.getRandomItems(params, seed).stream()
                            .filter(stack -> stack.is(ZSSRegistries.GODDESS_HARP.get())).count();
                helper.assertTrue(goddessHarps >= 50 && goddessHarps <= 150,
                        "Ice Temple Goddess Harp rate is outside 1% tolerance: " + goddessHarps);
            } else {
                helper.assertTrue(lootHasNoGoddessHarp(level, params, type),
                        "Non-Ice temple unexpectedly contains Goddess Harp: " + type);
            }
            helper.assertTrue(themed,"Missing themed reward branch: "+type);
        }
        for(String environment:SecretRoomDataProvider.ENVIRONMENTS) for(String gate:SecretRoomDataProvider.GATES)
            for(int variant=0;variant<20;variant++) checkTemplate(helper,SecretRoomDataProvider.templateId(environment,gate,variant));
        var secretParams=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
        for(String environment:SecretRoomDataProvider.ENVIRONMENTS) for(boolean locked:new boolean[]{false,true}) {
            assertOneHeartPiece(helper,level.getServer().getLootData().getLootTable(DungeonLootTables.secret(environment,locked)),secretParams);
            for(String gate:SecretRoomDataProvider.GATES) if(!gate.equals("none"))
                assertOneHeartPiece(helper,level.getServer().getLootData().getLootTable(DungeonLootTables.id(
                        "chests/secret/"+environment+"/"+gate+(locked?"_locked":""))),secretParams);
        }
        var keys=level.getServer().getLootData().getLootTable(DungeonLootTables.id("chests/pools/big_keys"));
        var seen=new java.util.HashSet<String>();
        var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
        for(int seed=1;seed<=100;seed++) keys.getRandomItems(params,seed).forEach(stack->seen.add(stack.getOrCreateTag().getString("dungeon")));
        helper.assertTrue(seen.equals(java.util.Arrays.stream(DungeonType.values()).map(type->type.id().toString()).collect(java.util.stream.Collectors.toSet())),"Not all seven keys are obtainable");
        helper.succeed();
    }

    private static boolean lootHasNoGoddessHarp(net.minecraft.server.level.ServerLevel level,
                                                 LootParams params, DungeonType type) {
        var table = level.getServer().getLootData().getLootTable(DungeonLootTables.temple(type, true));
        for (long seed = 1; seed <= 100; seed++)
            if (table.getRandomItems(params, seed).stream().anyMatch(stack -> stack.is(ZSSRegistries.GODDESS_HARP.get()))) return false;
        return true;
    }
    private static void checkTemplate(GameTestHelper helper,net.minecraft.resources.ResourceLocation id) {
        var tag=helper.getLevel().getStructureManager().get(id).orElseThrow().save(new CompoundTag());
        var palette=tag.getList("palette",Tag.TAG_COMPOUND);
        for(Tag entry:tag.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)entry;
            String name=palette.getCompound(block.getInt("state")).getString("Name");
            if(!name.equals("minecraft:chest")&&!name.endsWith(":chest_locked")&&!name.endsWith(":chest_invisible")&&!name.endsWith(":ceramic_jar"))continue;
            var loot=net.minecraft.resources.ResourceLocation.tryParse(block.getCompound("nbt").getString("LootTable"));
            helper.assertTrue(loot!=null&&helper.getLevel().getServer().getLootData().getLootTable(loot)!=LootTable.EMPTY,"Missing container loot: "+id+" "+name);
        }
    }
    private static void assertOneHeartPiece(GameTestHelper helper,LootTable table,LootParams params) {
        for(long seed=1;seed<=10;seed++) helper.assertTrue(table.getRandomItems(params,seed).stream()
                .filter(stack->stack.is(ZSSRegistries.getItem("heart_piece"))).mapToInt(ItemStack::getCount).sum()==1,
                "Secret-room chest did not contain exactly one heart piece");
    }

    @GameTest(template="zssgametests.empty",templateNamespace="minecraft",batch="zssLoot")
    public static void pendingAndOpenedLootSurviveReloadAndJarTransfer(GameTestHelper helper) {
        var level=helper.getLevel();BlockPos pos=helper.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos,ZSSRegistries.CERAMIC_JAR.get().defaultBlockState());
        var jar=(Storage)level.getBlockEntity(pos);jar.setLootTable(DungeonLootTables.JAR,12345L);
        CompoundTag pending=jar.saveWithoutMetadata();
        helper.assertTrue(pending.contains("LootTable")&&!pending.contains("Items"),"Saving rolled unopened jar loot");
        jar.clearContent();level.removeBlock(pos,false);
        level.setBlockAndUpdate(pos,ZSSRegistries.CERAMIC_JAR.get().defaultBlockState());
        jar=(Storage)level.getBlockEntity(pos);jar.load(pending);
        ItemStack contents=jar.getItem(0).copy();
        helper.assertTrue(!contents.isEmpty()&&!jar.saveWithoutMetadata().contains("LootTable"),"Jar did not resolve its loot exactly once");
        CompoundTag opened=jar.saveWithoutMetadata();jar.load(opened);
        helper.assertTrue(ItemStack.matches(contents,jar.getItem(0)),"Jar contents changed on reload");
        level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).forEach(net.minecraft.world.entity.Entity::discard);
        level.destroyBlock(pos,false);
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
        helper.assertTrue(drops.size()==1&&ItemStack.matches(contents,drops.get(0).getItem()),"Breaking generated jar lost or duplicated loot");
        level.destroyBlock(pos,false);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).size()==1,"Repeated break duplicated loot");
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        level.setBlockAndUpdate(pos,ZSSRegistries.CHEST_LOCKED.get().defaultBlockState());
        var chest=(Storage)level.getBlockEntity(pos);chest.setLootTable(DungeonLootTables.temple(DungeonType.DESERT,true),98765L);
        chest.load(chest.saveWithoutMetadata());chest.unpackLootTable(null);
        CompoundTag saved=chest.saveWithoutMetadata();chest.load(saved);
        helper.assertTrue(saved.equals(chest.saveWithoutMetadata())&&!saved.contains("LootTable")&&!chest.isEmpty(),"Locked chest loot was lost or rerolled");
        chest.clearContent();level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());helper.succeed();
    }

    @GameTest(template="zssgametests.empty",templateNamespace="minecraft",batch="zssLoot")
    public static void vanillaChestsReceiveDataDrivenZssLoot(GameTestHelper helper) {
        var level=helper.getLevel();
        var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
        boolean bomb=false,bag=false,heartPiece=false;
        LootTable mineshaft=level.getServer().getLootData().getLootTable(BuiltInLootTables.ABANDONED_MINESHAFT);
        for(long seed=1;seed<=10000;seed++) for(ItemStack stack:mineshaft.getRandomItems(params,seed)) {
            bomb|=stack.is(ZSSRegistries.getItem("standard_bomb"));
            bag|=stack.is(ZSSRegistries.getItem("bomb_bag"));
            heartPiece|=stack.is(ZSSRegistries.getItem("heart_piece"));
            helper.assertTrue(!stack.is(ZSSRegistries.getItem("skill_orb")),"Vanilla chest contained a forbidden heart-container skill orb");
        }
        helper.assertTrue(bomb&&bag&&heartPiece,"Mineshaft did not expose all configured ZSS loot branches");

        boolean book=false;
        LootTable library=level.getServer().getLootData().getLootTable(BuiltInLootTables.STRONGHOLD_LIBRARY);
        for(long seed=1;seed<=100&&!book;seed++) book=library.getRandomItems(params,seed).stream()
                .anyMatch(stack->stack.is(ZSSRegistries.getItem("book_of_mudora")));
        helper.assertTrue(book,"Stronghold library did not receive the Book of Mudora injection");

        boolean bonusBomb=false;
        LootTable bonus=level.getServer().getLootData().getLootTable(BuiltInLootTables.SPAWN_BONUS_CHEST);
        for(long seed=1;seed<=100;seed++) for(ItemStack stack:bonus.getRandomItems(params,seed)) {
            if(stack.is(ZSSRegistries.getItem("standard_bomb"))) {
                bonusBomb=true;
                helper.assertTrue(stack.getCount()>=1&&stack.getCount()<=3,"Bonus chest bomb count is outside 1-3");
            } else helper.assertTrue(!ZSSRegistries.ITEMS.getEntries().stream().anyMatch(item->stack.is(item.get())),
                    "Bonus chest received an unintended ZSS item");
        }
        helper.assertTrue(bonusBomb,"Bonus chest did not receive standard bombs");
        helper.succeed();
    }

    private static String[] themedItems(DungeonType type) {
        return switch(type) {
            case DESERT -> new String[]{"boomerang","hover_boots","hookshot_extender","gibdo_mask","fire_rod"};
            case EARTH -> new String[]{"pegasus_boots","wooden_hammer","blast_mask","claw_upgrade","broken_sword"};
            case FIRE -> new String[]{"skeleton_key","hero_bow","multi_hook_upgrade","majora_mask","goron_tunic_chestplate"};
            case FOREST -> new String[]{"deku_leaf","hero_bow","hookshot","hawkeye_mask","whip"};
            case ICE -> new String[]{"boomerang","hover_boots","silver_gauntlets","giants_mask","ice_rod"};
            case WATER -> new String[]{"heavy_boots","stone_mask","slingshot","zora_tunic_chestplate"};
            case END -> new String[]{"rubber_boots","wooden_hammer","hero_bow","hawkeye_mask","tornado_rod"};
        };
    }
}
