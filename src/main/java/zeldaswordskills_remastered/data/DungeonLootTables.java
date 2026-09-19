package zeldaswordskills_remastered.data;

import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.registry.MusicDiscCatalog;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.function.BiConsumer;

/** Datapack replacement for DungeonLootLists, using its default weights and stable item IDs. */
public final class DungeonLootTables implements LootTableSubProvider {
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
    }
    public static ResourceLocation temple(DungeonType type, boolean reward) {
        return id("chests/temples/" + type.getSerializedName() + (reward ? "_reward" : "_extra"));
    }
    public static ResourceLocation secret(String environment, boolean locked) {
        return id("chests/secret/" + environment + (locked ? "_locked" : ""));
    }
    public static final ResourceLocation JAR = id("chests/ceramic_jar");

    @Override public void generate(BiConsumer<ResourceLocation, LootTable.Builder> out) {
        LootPool.Builder musicDiscs = pool(1, 1);
        MusicDiscCatalog.DISCS.forEach(disc -> musicDiscs.add(item(disc.id())));
        out.accept(id("chests/pools/music_discs"), table(musicDiscs));
        out.accept(id("chests/pools/basic"), table(pool(4, 8,
                vanilla("apple",1,2,3), vanilla("golden_apple",1,1,1), vanilla("bread",1,2,3),
                vanilla("bucket",1,1,1), vanilla("compass",1,1,2), vanilla("map",1,2,2),
                vanilla("experience_bottle",1,5,2), vanilla("name_tag",1,2,2), vanilla("diamond",1,2,1),
                vanilla("gold_ingot",1,2,2), vanilla("iron_ingot",1,2,3), vanilla("leather",1,3,3),
                vanilla("glass_bottle",1,2,3), vanilla("emerald",2,5,5), vanilla("arrow",3,7,5),
                vanilla("melon_seeds",1,3,2), vanilla("pumpkin_seeds",1,3,2), item("deku_nut",1,3,2),
                item("standard_bomb",1,2,5), item("bomb_bag"), item("small_key",1,1,4),
                item("red_potion",1,1,3), item("green_potion"), item("purple_potion"), item("deku_shield"), item("broken_sword"))));
        out.accept(id("chests/pools/boss"), table(pool(1, 1,
                item("fire_arrow",7,15,1), item("ice_arrow",7,15,1), item("light_arrow",3,7,1), item("bomb_bag"),
                item("boomerang",1,1,2), item("heavy_boots"), item("hover_boots"), item("pegasus_boots"), item("rubber_boots"),
                item("empty_spirit_crystal"), item("deku_leaf"), LootItem.lootTableItem(Items.ENCHANTED_GOLDEN_APPLE),
                item("wooden_hammer",1,1,2), item("hero_bow",1,1,2), item("hookshot",1,1,2), item("hookshot_extender"),
                item("claw_upgrade"), item("multi_hook_upgrade"), item("ocarina_of_time"), item("skeleton_key"),
                item("magic_container"), item("magic_mirror"), item("master_ore"), item("blue_potion"), item("rocs_feather"),
                item("hylian_shield",1,1,2), item("slingshot",1,1,2), item("whip",1,1,2))));
        out.accept(id("chests/pools/locked"), table(pool(1,3,
                item("fire_arrow",2,5,3), item("ice_arrow",2,5,3), item("light_arrow",1,3,1),
                vanilla("golden_apple",1,2,1), potion("strong_healing",2), potion("healing",2), item("bomb_bag",1,1,3),
                item("magic_mirror",1,1,3), item("blue_potion"), item("ordon_sword",1,1,3),
                item("hero_tunic_boots"), item("hero_tunic_leggings"), item("hero_tunic_chestplate"), item("hero_tunic_helmet"))));
        out.accept(JAR, table(pool(1, 1, item("small_heart"), vanilla("emerald", 1, 1, 1),
                vanilla("arrow", 1, 1, 1), item("deku_nut"), item("throwing_rock"), item("standard_bomb"))));
        vanillaChestInjections(out);
        environmentPools(out);
        LootPool.Builder skills = pool(1,1);
        ZSSContentIds.SKILL_ORDER.stream().filter(skill -> !skill.equals(ZSSContentIds.BONUS_HEART)
                        && !skill.equals(ZSSContentIds.SUPER_SPIN_ATTACK)
                        && !skill.equals(ZSSContentIds.CONTINUOUS_FLASH))
                .forEach(skill -> skills.add(chestSkill(skill)));
        out.accept(id("chests/pools/skills"), table(skills));
        LootPool.Builder keys = pool(1,1);
        for (DungeonType type : DungeonType.values()) keys.add(tagged("big_key", "dungeon", type.id().toString()));
        out.accept(id("chests/pools/big_keys"), table(keys));
        for (DungeonType type : DungeonType.values()) {
            String environment = switch(type) { case FIRE -> "nether"; case WATER -> "ocean"; case EARTH -> "mountain"; default -> "land"; };
            LootTable.Builder reward = base(environment, true).withPool(reference("boss").setRolls(UniformGenerator.between(1,2)));
            String pendant = switch(type) { case DESERT -> "pendant_courage"; case ICE -> "pendant_power"; case WATER -> "pendant_wisdom"; default -> null; };
            if (pendant != null) reward.withPool(pool(1,1,item(pendant)));
            out.accept(id("chests/pools/" + type.getSerializedName()), table(pool(1,1, themed(type))));
            reward.withPool(reference("skills").setRolls(UniformGenerator.between(1,3)))
                    .withPool(reference(type.getSerializedName()).when(LootItemRandomChanceCondition.randomChance(.20F)));
            if (type == DungeonType.ICE) {
                reward.withPool(pool(1, 1, item("goddess_harp"))
                        .when(LootItemRandomChanceCondition.randomChance(.01F)));
            }
            out.accept(temple(type,true), reward);
            out.accept(temple(type,false), base(environment,true));
        }
        for (String environment : new String[]{"land","mountain","ocean","nether","lava"}) {
            for (boolean locked : new boolean[]{false,true}) {
                LootTable.Builder loot = base(environment,locked)
                        .withPool(pool(1,1,item("heart_piece")));
                if (locked) loot.withPool(reference("boss").when(LootItemRandomChanceCondition.randomChance(.25F)))
                        .withPool(reference("big_keys").when(LootItemRandomChanceCondition.randomChance(.2F)));
                out.accept(secret(environment,locked),loot);
            }
        }
        String[] gates = {"peg_wooden","light_block","peg_rusty","heavy_block","time_block","quake_stone","door_locked"};
        String[] rewards = {"silver_gauntlets","skull_hammer","golden_gauntlets","megaton_hammer","zeldas_letter","ocarina_of_time", "magic_container"};
        for (int i=0;i<gates.length;i++) out.accept(id("chests/secret/gates/"+gates[i]), table(pool(1,1,
                item(rewards[i]).setWeight(1), LootTableReference.lootTableReference(id("chests/pools/boss")).setWeight(3))));
        for(String environment:SecretRoomDataProvider.ENVIRONMENTS) for(String gate:gates) for(boolean locked:new boolean[]{false,true})
            out.accept(id("chests/secret/"+environment+"/"+gate+(locked?"_locked":"")),
                    table(pool(1,1,LootTableReference.lootTableReference(secret(environment,locked))))
                            .withPool(pool(1,1,LootTableReference.lootTableReference(id("chests/secret/gates/"+gate)))));
    }
    private static void vanillaChestInjections(BiConsumer<ResourceLocation,LootTable.Builder> out) {
        LootPool.Builder common = pool(1,1,item("standard_bomb",1,3,1))
                .when(LootItemRandomChanceCondition.randomChance(.25F));
        LootTable.Builder commonTable = table(musicDiscPool()).withPool(common)
                .withPool(pool(1,1,item("bomb_bag")).when(LootItemRandomChanceCondition.randomChance(.12F)))
                .withPool(pool(1,1,item("heart_piece")).when(LootItemRandomChanceCondition.randomChance(.002F)));
        out.accept(id("chests/inject/vanilla_common"), commonTable);
        out.accept(id("chests/inject/stronghold_library"), table(referenceInjection("vanilla_common"))
                .withPool(pool(1,1,item("book_of_mudora")).when(LootItemRandomChanceCondition.randomChance(.20F))));
        out.accept(id("chests/inject/village_blacksmith"), table(musicDiscPool())
                .withPool(pool(1,1,item("book_of_mudora")).when(LootItemRandomChanceCondition.randomChance(.20F)))
                .withPool(pool(1,1,item("standard_bomb",1,3,1)).when(LootItemRandomChanceCondition.randomChance(.25F)))
                .withPool(pool(1,1,item("bomb_bag")).when(LootItemRandomChanceCondition.randomChance(.12F))));
        out.accept(id("chests/inject/bonus_chest"), table(pool(1,1,item("standard_bomb",1,3,1))
                .when(LootItemRandomChanceCondition.randomChance(.25F))));
    }

    private static LootPool.Builder referenceInjection(String path) {
        return pool(1,1,LootTableReference.lootTableReference(id("chests/inject/" + path)));
    }

    private static LootTable.Builder base(String environment, boolean locked) {
        LootTable.Builder table = table(musicDiscPool()).withPool(reference("basic"));
        LootPool.Builder environmentPool = pool(0,2, LootTableReference.lootTableReference(id("chests/pools/"+(environment.equals("nether")?"land":environment))));
        if (!locked) environmentPool.when(LootItemRandomChanceCondition.randomChance(.25F));
        table.withPool(environmentPool);
        if (environment.equals("nether") || environment.equals("lava")) table.withPool(reference("nether").setRolls(UniformGenerator.between(1,2)));
        if (locked) table.withPool(reference("locked"));
        return table;
    }
    private static void environmentPools(BiConsumer<ResourceLocation,LootTable.Builder> out) {
        out.accept(id("chests/pools/land"), table(pool(1,1,vanilla("diamond_horse_armor",1,1,1),vanilla("golden_horse_armor",1,1,1),
                vanilla("iron_horse_armor",1,1,2),vanilla("saddle",1,1,3),item("bomb_arrow",2,5,3),item("standard_bomb",1,3,5),item("broken_sword"),item("kokiri_sword"))));
        out.accept(id("chests/pools/mountain"), table(pool(1,1,potion("strong_strength",1),potion("strength",3),vanilla("diamond",1,3,3),
                item("bomb_arrow",2,5,3),item("standard_bomb",1,2,10),item("rocs_feather"),item("broken_sword"))));
        out.accept(id("chests/pools/nether"), table(pool(1,1,vanilla("ghast_tear",1,2,1),vanilla("nether_wart",1,2,1),potion("fire_resistance",2),
                vanilla("blaze_rod",1,3,2),vanilla("fire_charge",1,3,3),vanilla("magma_cream",1,3,3),item("fire_bomb_arrow",2,5,3),item("fire_bomb",1,2,10))));
        out.accept(id("chests/pools/lava"), table(pool(1,1,vanilla("nether_wart",1,2,1),potion("fire_resistance",2),vanilla("fire_charge",1,2,3),
                item("fire_bomb_arrow",2,5,3),item("fire_bomb",1,2,10),item("goron_tunic_helmet"),item("goron_tunic_chestplate"),item("goron_tunic_leggings"))));
        out.accept(id("chests/pools/ocean"), table(pool(1,1,vanilla("fishing_rod",1,1,1),vanilla("cod",1,2,3),potion("water_breathing",2),
                item("water_bomb_arrow",2,5,3),item("water_bomb",1,2,10),item("zora_tunic_helmet"),item("zora_tunic_chestplate"),item("zora_tunic_leggings"),item("zora_tunic_boots"))));
    }
    private static LootPoolSingletonContainer.Builder<?>[] themed(DungeonType type) {
        String[] items = switch(type) {
            case DESERT -> new String[]{"boomerang","hover_boots","hookshot_extender","gibdo_mask","fire_rod"};
            case EARTH -> new String[]{"pegasus_boots","wooden_hammer","blast_mask","claw_upgrade","broken_sword"};
            case FIRE -> new String[]{"skeleton_key","hero_bow","multi_hook_upgrade","majora_mask","goron_tunic_chestplate"};
            case FOREST -> new String[]{"deku_leaf","hero_bow","hookshot","hawkeye_mask","whip"};
            case ICE -> new String[]{"boomerang","hover_boots","silver_gauntlets","giants_mask","ice_rod"};
            case WATER -> new String[]{"heavy_boots","stone_mask","slingshot","zora_tunic_chestplate"};
            case END -> new String[]{"rubber_boots","wooden_hammer","hero_bow","hawkeye_mask","tornado_rod"};
        };
        return java.util.Arrays.stream(items).map(DungeonLootTables::item).toArray(LootPoolSingletonContainer.Builder<?>[]::new);
    }
    private static LootItem.Builder item(String name) { return item(name,1,1,1); }
    private static LootItem.Builder item(String name,int min,int max,int weight) {
        return LootItem.lootTableItem(ZSSRegistries.getItem(name)).setWeight(weight)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min,max)));
    }
    private static LootItem.Builder vanilla(String name,int min,int max,int weight) {
        return LootItem.lootTableItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("minecraft",name)))
                .setWeight(weight).apply(SetItemCountFunction.setCount(UniformGenerator.between(min,max)));
    }
    private static LootItem.Builder tagged(String item,String key,String value) {
        CompoundTag tag = new CompoundTag(); tag.putString(key,value);
        return item(item).apply(SetNbtFunction.setTag(tag));
    }
    private static LootItem.Builder chestSkill(ResourceLocation skill) {
        CompoundTag tag = new CompoundTag();
        tag.putString(zeldaswordskills_remastered.item.ProgressionItem.SKILL_TAG, skill.toString());
        tag.putBoolean(zeldaswordskills_remastered.item.ProgressionItem.CHEST_SKILL_TAG, true);
        return item("skill_orb").apply(SetNbtFunction.setTag(tag));
    }
    private static LootItem.Builder potion(String potion,int weight) {
        CompoundTag tag = new CompoundTag(); tag.putString("Potion","minecraft:"+potion);
        return LootItem.lootTableItem(Items.POTION).setWeight(weight).apply(SetNbtFunction.setTag(tag));
    }
    private static LootPool.Builder musicDiscPool() {
        return reference("music_discs").when(LootItemRandomChanceCondition.randomChance(.10F));
    }

    private static LootPool.Builder reference(String pool) { return pool(1,1,LootTableReference.lootTableReference(id("chests/pools/"+pool))); }
    private static LootPool.Builder pool(int min,int max,LootPoolSingletonContainer.Builder<?>... entries) {
        LootPool.Builder pool = LootPool.lootPool().setRolls(UniformGenerator.between(min,max));
        for (var entry : entries) pool.add(entry);
        return pool;
    }
    private static LootTable.Builder table(LootPool.Builder pool) { return LootTable.lootTable().withPool(pool); }
}
