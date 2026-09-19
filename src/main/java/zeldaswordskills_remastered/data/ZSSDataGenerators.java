package zeldaswordskills_remastered.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.PedestalBlock;
import zeldaswordskills_remastered.block.*;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.registry.MusicDiscCatalog;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ZSSDataGenerators {
    private static final Map<String, String> ITEM_MODEL_ALIASES = Map.ofEntries(
            Map.entry("hero_tunic_helmet", "hero_tunic_helm"), Map.entry("hero_tunic_chestplate", "hero_tunic_chest"), Map.entry("hero_tunic_leggings", "hero_tunic_legs"),
            Map.entry("goron_tunic_helmet", "goron_tunic_helm"), Map.entry("goron_tunic_chestplate", "goron_tunic_chest"), Map.entry("goron_tunic_leggings", "goron_tunic_legs"),
            Map.entry("zora_tunic_helmet", "zora_tunic_helm"), Map.entry("zora_tunic_chestplate", "zora_tunic_chest"), Map.entry("zora_tunic_leggings", "zora_tunic_legs"),
            Map.entry("heavy_boots", "boots_heavy"), Map.entry("hover_boots", "boots_hover"), Map.entry("pegasus_boots", "boots_pegasus"), Map.entry("rubber_boots", "boots_rubber"),
            Map.entry("broken_sword", "broken_sword_giant"), Map.entry("kokiri_sword", "sword_kokiri"), Map.entry("ordon_sword", "sword_ordon"),
            Map.entry("giant_sword", "sword_giant"), Map.entry("biggoron_sword", "sword_biggoron"), Map.entry("master_sword", "sword_master"),
            Map.entry("tempered_sword", "sword_tempered"), Map.entry("golden_sword", "sword_golden"), Map.entry("true_master_sword", "sword_master_true"), Map.entry("darknut_sword", "sword_darknut"),
            Map.entry("wooden_hammer", "hammer"), Map.entry("skull_hammer", "hammer_skull"), Map.entry("megaton_hammer", "hammer_megaton"),
            Map.entry("magic_boomerang", "boomerang_magic"),
            Map.entry("magic_whip", "whip_magic"),
            Map.entry("bomb_arrow", "arrow_bomb"), Map.entry("fire_bomb_arrow", "arrow_bomb_fire"), Map.entry("water_bomb_arrow", "arrow_bomb_water"),
            Map.entry("fire_arrow", "arrow_fire"), Map.entry("ice_arrow", "arrow_ice"), Map.entry("light_arrow", "arrow_light"),
            Map.entry("small_key", "key_small"), Map.entry("skeleton_key", "key_skeleton"),
            Map.entry("door_boss_desert", "door_temple_desert"), Map.entry("door_boss_earth", "door_temple_earth"),
            Map.entry("door_boss_fire", "door_temple_fire"), Map.entry("door_boss_forest", "door_temple_forest"),
            Map.entry("door_boss_ice", "door_temple_ice"), Map.entry("door_boss_water", "door_temple_water"),
            Map.entry("door_boss_end", "door_temple_end"),
            Map.entry("warp_stone_bolero", "warp_stone"), Map.entry("warp_stone_minuet", "warp_stone"),
            Map.entry("warp_stone_prelude", "warp_stone"), Map.entry("warp_stone_oath", "warp_stone"),
            Map.entry("warp_stone_nocturne", "warp_stone"), Map.entry("warp_stone_requiem", "warp_stone"),
            Map.entry("warp_stone_serenade", "warp_stone"),
            Map.entry("hookshot_extender", "hookshot_upgrade_extender"), Map.entry("claw_upgrade", "hookshot_upgrade_claw"), Map.entry("multi_hook_upgrade", "hookshot_upgrade_multi"),
            Map.entry("standard_bomb", "bomb_standard"), Map.entry("fire_bomb", "bomb_fire"), Map.entry("water_bomb", "bomb_water"),
            Map.entry("empty_spirit_crystal", "spirit_crystal_empty"), Map.entry("din_crystal", "spirit_crystal_din"),
            Map.entry("farore_crystal", "spirit_crystal_farore"), Map.entry("nayru_crystal", "spirit_crystal_nayru"),
            Map.entry("silver_gauntlets", "gauntlets_silver"), Map.entry("golden_gauntlets", "gauntlets_golden"),
            Map.entry("red_potion", "potion_red"), Map.entry("green_potion", "potion_green"), Map.entry("blue_potion", "potion_blue"),
            Map.entry("yellow_potion", "potion_yellow"), Map.entry("purple_potion", "potion_purple"),
            Map.entry("small_magic_jar", "magic_jar"), Map.entry("large_magic_jar", "magic_jar_big"),
            Map.entry("bombos_medallion", "medallion_bombos"), Map.entry("ether_medallion", "medallion_ether"), Map.entry("quake_medallion", "medallion_quake"),
            Map.entry("fire_rod", "rod_fire"), Map.entry("ice_rod", "rod_ice"), Map.entry("tornado_rod", "rod_tornado"),
            Map.entry("blast_mask", "mask_blast"), Map.entry("bunny_hood", "mask_bunny"), Map.entry("couples_mask", "mask_couples"),
            Map.entry("gerudo_mask", "mask_gerudo"), Map.entry("giants_mask", "mask_giants"), Map.entry("gibdo_mask", "mask_gibdo"),
            Map.entry("hawkeye_mask", "mask_hawkeye"), Map.entry("keaton_mask", "mask_keaton"), Map.entry("mask_of_scents", "mask_scents"),
            Map.entry("skull_mask", "mask_skull"), Map.entry("spooky_mask", "mask_spooky"), Map.entry("stone_mask", "mask_stone"),
            Map.entry("mask_of_truth", "mask_truth"), Map.entry("deku_mask", "mask_deku"), Map.entry("goron_mask", "mask_goron"),
            Map.entry("zora_mask", "mask_zora"), Map.entry("fierce_deity_mask", "mask_fierce"), Map.entry("majora_mask", "mask_majora"),
            Map.entry("chu_jelly_green", "jelly_chu_green"), Map.entry("chu_jelly_red", "jelly_chu_red"),
            Map.entry("chu_jelly_blue", "jelly_chu_blue"), Map.entry("chu_jelly_yellow", "jelly_chu_yellow"),
            Map.entry("fairy_ocarina", "ocarina_fairy"), Map.entry("ocarina_of_time", "ocarina_time"), Map.entry("goddess_harp", "ocarina_time"),
            Map.entry("bomb_flower_seed", "seed_bomb_flower"), Map.entry("book_of_mudora", "book_mudora"), Map.entry("small_heart", "heart_small")
    );

    private ZSSDataGenerators() {
    }

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        generator.addProvider(event.includeClient(), new ItemModels(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(), new BlockStates(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(), new EnglishLanguage(output));
        generator.addProvider(event.includeClient(), new ChineseLanguage(output));
        generator.addProvider(event.includeClient(), new EnglishLanguage(output, "fr_fr"));
        generator.addProvider(event.includeClient(), new EnglishLanguage(output, "pl_pl"));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(BlockLootTables::new, LootContextParamSets.BLOCK),
                new LootTableProvider.SubProviderEntry(DungeonLootTables::new, LootContextParamSets.CHEST))));
        generator.addProvider(event.includeServer(), new DungeonStructureDataProvider(output));
        generator.addProvider(event.includeServer(), new SecretRoomDataProvider(output));
        generator.addProvider(event.includeServer(), new SmallWorldFeatureDataProvider(output));
        generator.addProvider(event.includeServer(), new ZSSRecipeProvider(output));
        generator.addProvider(event.includeServer(), new ZSSAdvancementProvider(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(), new VanillaLootModifierProvider(output));
    }

    private static final class ItemModels extends ItemModelProvider {
        private ItemModels(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, ZeldaSwordSkills_Remastered.MOD_ID, existingFileHelper);
        }

        @Override
        protected void registerModels() {
            ZSSRegistries.MUSIC_DISC_ITEMS.forEach(item -> basicItem(item.get()));
            basicItem(ZSSRegistries.MASTER_ORE.get());
            basicItem(ZSSRegistries.PENDANT_POWER.get());
            basicItem(ZSSRegistries.PENDANT_WISDOM.get());
            basicItem(ZSSRegistries.PENDANT_COURAGE.get());
            basicItem(ZSSRegistries.THROWING_ROCK.get());
            ITEM_MODEL_ALIASES.forEach((item, model) -> withExistingParent(item, modLoc("item/" + model)));
            skillOrb();
            getBuilder("big_key").parent(existing("key_small"))
                    .override().predicate(modLoc("dungeon"), 1).model(existing("key_temple_desert")).end()
                    .override().predicate(modLoc("dungeon"), 2).model(existing("key_temple_earth")).end()
                    .override().predicate(modLoc("dungeon"), 3).model(existing("key_temple_fire")).end()
                    .override().predicate(modLoc("dungeon"), 4).model(existing("key_temple_forest")).end()
                    .override().predicate(modLoc("dungeon"), 5).model(existing("key_temple_ice")).end()
                    .override().predicate(modLoc("dungeon"), 6).model(existing("key_temple_water")).end()
                    .override().predicate(modLoc("dungeon"), 7).model(existing("key_temple_end")).end();
            shield("deku_shield", "shield_deku");
            shield("hylian_shield", "shield_hylian");
            shield("mirror_shield", "shield_mirror");
            getBuilder("hero_bow").parent(existing("bow_hero_0"))
                    .override().predicate(mcLoc("pulling"), 1.0F).model(existing("bow_hero_1")).end()
                    .override().predicate(mcLoc("pulling"), 1.0F).predicate(mcLoc("pull"), 0.65F).model(existing("bow_hero_2")).end()
                    .override().predicate(mcLoc("pulling"), 1.0F).predicate(mcLoc("pull"), 0.9F).model(existing("bow_hero_3")).end();
            getBuilder("magic_mirror").parent(existing("magic_mirror_0"))
                    .override().predicate(modLoc("using"), 0.25F).model(existing("magic_mirror_1")).end()
                    .override().predicate(modLoc("using"), 0.5F).model(existing("magic_mirror_2")).end()
                    .override().predicate(modLoc("using"), 0.75F).model(existing("magic_mirror_3")).end();
            ZSSRegistries.SPAWN_EGG_ITEMS.forEach(item ->
                    withExistingParent(item.getId().getPath(), mcLoc("item/template_spawn_egg")));
        }

        private ModelFile existing(String path) { return getExistingFile(modLoc("item/" + path)); }
        private void skillOrb() {
            String[] models = {"skillorb_swordbasic", "skillorb_helm_splitter", "skillorb_dodge", "skillorb_leapingblow",
                    "skillorb_parry", "skillorb_dash", "skillorb_spinattack", "skillorb_superspinattack", "skillorb_swordbeam",
                    "skillorb_swordbreak", "skillorb_mortaldraw", "heart_container", "skillorb_risingcut", "skillorb_endingblow",
                    "skillorb_flash_assault", "skillorb_doublejump", "skillorb_continuous_flash"};
            var builder = getBuilder("skill_orb").parent(existing(models[0]));
            for (int i = 0; i < models.length; i++) builder.override().predicate(modLoc("skill"), i + 1).model(existing(models[i])).end();
        }
        private void shield(String item, String model) {
            getBuilder(item).parent(existing(model)).override().predicate(mcLoc("blocking"), 1.0F)
                    .model(existing(model + "_using")).end();
        }
    }

    private static final class BlockStates extends BlockStateProvider {
        private static final Set<String> MANUAL_ITEM_MODELS = Set.of(
                "ancient_tablet_bombos", "ancient_tablet_ether", "ancient_tablet_quake", "beam_wooden",
                "bomb_flower", "ceramic_jar", "chest_invisible", "chest_locked", "door_locked", "gossip_stone",
                "hook_target", "hook_target_all", "inscription", "lever_giant", "peg_rusty", "peg_wooden",
                "quake_stone_mossy", "royal_block", "time_block",
                "door_boss_desert", "door_boss_earth", "door_boss_fire", "door_boss_forest", "door_boss_ice", "door_boss_water", "door_boss_end",
                "sacred_flame_din", "sacred_flame_farore", "sacred_flame_nayru",
                "warp_stone_bolero", "warp_stone_minuet", "warp_stone_prelude", "warp_stone_oath", "warp_stone_nocturne", "warp_stone_requiem", "warp_stone_serenade");
        private BlockStates(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, ZeldaSwordSkills_Remastered.MOD_ID, existingFileHelper);
        }

        @Override
        protected void registerStatesAndModels() {
            simpleBlock(ZSSRegistries.SECRET_ROOM_CORE.get(), models().cubeAll("secret_room_core", mcLoc("block/stone")));
            simpleBlock(ZSSRegistries.NAVI_LIGHT.get(), models().getBuilder("navi_light").texture("particle", mcLoc("block/glass")));
            ModelFile[] models = new ModelFile[8];
            for (int pendants = 0; pendants < models.length; pendants++) {
                models[pendants] = models().slab(
                        "pedestal_" + pendants,
                        modLoc("block/pedestal_side_" + pendants),
                        modLoc("block/pedestal_bottom"),
                        modLoc("block/pedestal_top"));
            }

            getVariantBuilder(ZSSRegistries.PEDESTAL.get()).forAllStates(state -> {
                int model = state.getValue(PedestalBlock.UNLOCKED) ? 7 : state.getValue(PedestalBlock.PENDANTS);
                return ConfiguredModel.builder().modelFile(models[model]).build();
            });
            stageNineBlockItem(ZSSRegistries.PEDESTAL.get(), models[0]);

            ModelFile beam = existing("beam_wooden");
            getVariantBuilder(ZSSRegistries.BEAM_WOODEN.get()).forAllStates(state -> {
                net.minecraft.core.Direction.Axis axis = state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS);
                return ConfiguredModel.builder().modelFile(beam)
                        .rotationX(axis == net.minecraft.core.Direction.Axis.Y ? 0 : 90)
                        .rotationY(axis == net.minecraft.core.Direction.Axis.X ? 90 : 0).build();
            });
            stageNineBlockItem(ZSSRegistries.BEAM_WOODEN.get(), beam);

            getVariantBuilder(ZSSRegistries.BOMB_FLOWER.get()).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(existing("bomb_flower_stage_" + state.getValue(BombFlowerBlock.AGE))).build());
            stageNineBlockItem(ZSSRegistries.BOMB_FLOWER.get(), existing("bomb_flower_stage_3"));
            simpleExisting(ZSSRegistries.CERAMIC_JAR, "ceramic_jar");

            orientedChest(ZSSRegistries.CHEST_LOCKED, false);
            orientedChest(ZSSRegistries.CHEST_INVISIBLE, true);
            lockedDoor(ZSSRegistries.DOOR_LOCKED, null);
            ZSSRegistries.BOSS_DOORS.forEach(block -> lockedDoor(block, block.get().dungeonType()));

            stateIgnored(ZSSRegistries.DUNGEON_CORE_STONE, models().cubeAll("dungeon_core_stone", mcLoc("block/polished_granite")));
            stateIgnored(ZSSRegistries.DUNGEON_CORE_SANDSTONE, models().cubeAll("dungeon_core_sandstone", mcLoc("block/cut_sandstone")));
            stateIgnored(ZSSRegistries.DUNGEON_STONE_STONE, models().cubeAll("dungeon_stone_stone", mcLoc("block/polished_andesite")));
            stateIgnored(ZSSRegistries.DUNGEON_STONE_SANDSTONE, models().cubeAll("dungeon_stone_sandstone", mcLoc("block/sandstone")));
            simpleExisting(ZSSRegistries.GOSSIP_STONE, "gossip_stone");
            simpleExisting(ZSSRegistries.LIGHT_BLOCK, "barrier_light");
            simpleExisting(ZSSRegistries.HEAVY_BLOCK, "barrier_heavy");
            // The orientable model's red front is NORTH, not UP like the other directional models.
            getVariantBuilder(ZSSRegistries.HOOK_TARGET.get()).forAllStates(state -> {
                var direction = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
                return ConfiguredModel.builder().modelFile(existing("hook_target"))
                        .rotationX((xRotation(direction) + 270) % 360).rotationY(yRotation(direction)).build();
            });
            stageNineBlockItem(ZSSRegistries.HOOK_TARGET.get(), existing("hook_target"));
            directional(ZSSRegistries.HOOK_TARGET_ALL, "hook_target_all");
            directional(ZSSRegistries.INSCRIPTION, "inscription");

            getVariantBuilder(ZSSRegistries.LEVER_GIANT.get()).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(existing(state.getValue(MechanismBlocks.GiantLever.POWERED) ? "lever_giant" : "lever_giant_off"))
                    .rotationX(xRotation(state.getValue(MechanismBlocks.GiantLever.FACING)))
                    .rotationY(yRotation(state.getValue(MechanismBlocks.GiantLever.FACING))).build());
            stageNineBlockItem(ZSSRegistries.LEVER_GIANT.get(), existing("lever_giant_off"));

            peg(ZSSRegistries.PEG_WOODEN, "peg_wooden");
            peg(ZSSRegistries.PEG_RUSTY, "peg_rusty");
            simpleExisting(ZSSRegistries.QUAKE_STONE, "quake_stone_cobble");
            simpleExisting(ZSSRegistries.QUAKE_STONE_MOSSY, "quake_stone_mossy");

            ZSSRegistries.SACRED_FLAMES.forEach(this::sacredFlame);
            ZSSRegistries.SECRET_STONES.forEach(this::secretStone);
            timeBlock(ZSSRegistries.TIME_BLOCK, "time_block");
            timeBlock(ZSSRegistries.ROYAL_BLOCK, "royal_block");
            ZSSRegistries.WARP_STONES.forEach(block -> stateIgnored(block, existing("warp_stone")));
            ancientTablet(ZSSRegistries.ANCIENT_TABLET_BOMBOS);
            ancientTablet(ZSSRegistries.ANCIENT_TABLET_ETHER);
            ancientTablet(ZSSRegistries.ANCIENT_TABLET_QUAKE);
        }

        private ModelFile existing(String name) { return models().getExistingFile(modLoc("block/" + name)); }
        private void stageNineBlockItem(Block block, ModelFile model) {
            String path = block.getDescriptionId().substring(block.getDescriptionId().lastIndexOf('.') + 1);
            if (!MANUAL_ITEM_MODELS.contains(path)) simpleBlockItem(block, model);
        }
        private void simpleExisting(RegistryObject<? extends Block> block, String model) {
            simpleBlock(block.get(), existing(model)); stageNineBlockItem(block.get(), existing(model));
        }
        private void stateIgnored(RegistryObject<? extends Block> block, ModelFile model) {
            simpleBlock(block.get(), model); stageNineBlockItem(block.get(), model);
        }
        private void orientedChest(RegistryObject<LockedChestBlock> block, boolean hidden) {
            getVariantBuilder(block.get()).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(hidden && !state.getValue(LockedChestBlock.VISIBLE) ? existing("empty") : existing("chest_locked"))
                    .rotationY(horizontalRotation(state.getValue(LockedChestBlock.FACING))).build());
            stageNineBlockItem(block.get(), existing("chest_locked"));
        }
        private void lockedDoor(RegistryObject<LockedDoorBlock> block, @Nullable DungeonType dungeonType) {
            String texture = dungeonType == null ? "door_locked" : "door_temple_" + dungeonType.getSerializedName();
            getVariantBuilder(block.get()).forAllStates(state -> {
                String half = state.getValue(LockedDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                        ? "bottom" : "top";
                boolean open = state.getValue(LockedDoorBlock.OPEN);
                ModelFile model = models().withExistingParent(block.getId().getPath() + "_" + half + (open ? "_open" : ""),
                                mcLoc("block/door_" + half + "_left" + (open ? "_open" : "")))
                        .texture("bottom", modLoc("block/" + texture + "_lower"))
                        .texture("top", modLoc("block/" + texture + "_upper"))
                        .renderType("cutout");
                int rotation = (horizontalRotation(state.getValue(LockedDoorBlock.FACING)) + 270 + (open ? 90 : 0)) % 360;
                return ConfiguredModel.builder().modelFile(model).rotationY(rotation).build();
            });
        }
        private void sacredFlame(RegistryObject<SacredFlameBlock> block) {
            getVariantBuilder(block.get()).forAllStates(state -> ConfiguredModel.builder().modelFile(existing(
                    state.getValue(SacredFlameBlock.EXTINGUISHED) ? "sacred_flame_extinguished"
                            : "sacred_flame_" + block.get().flameType().getSerializedName())).build());
            stageNineBlockItem(block.get(), existing("sacred_flame_" + block.get().flameType().getSerializedName()));
        }
        private void secretStone(RegistryObject<MechanismBlocks.SecretStone> block) {
            ModelFile model = secretModel(block.get().variant());
            simpleBlock(block.get(), model); stageNineBlockItem(block.get(), model);
        }
        private void directional(RegistryObject<? extends Block> block, String modelName) {
            getVariantBuilder(block.get()).forAllStates(state -> {
                net.minecraft.core.Direction direction = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
                return ConfiguredModel.builder().modelFile(existing(modelName)).rotationX(xRotation(direction)).rotationY(yRotation(direction)).build();
            });
            stageNineBlockItem(block.get(), existing(modelName));
        }
        private void peg(RegistryObject<MechanismBlocks.Peg> block, String prefix) {
            getVariantBuilder(block.get()).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(existing(prefix + state.getValue(MechanismBlocks.Peg.HITS))).build());
            stageNineBlockItem(block.get(), existing(prefix + "0"));
        }
        private void timeBlock(RegistryObject<MechanismBlocks.TimeBlock> block, String modelName) {
            getVariantBuilder(block.get()).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(existing(state.getValue(MechanismBlocks.TimeBlock.ETHEREAL) ? "empty" : modelName)).build());
            stageNineBlockItem(block.get(), existing(modelName));
        }
        private ModelFile secretModel(MechanismBlocks.SecretVariant variant) {
            return models().cubeAll("secret_stone_" + variant.getSerializedName(), switch (variant) {
                case STONE -> mcLoc("block/stone"); case SANDSTONE -> mcLoc("block/sandstone");
                case NETHER_BRICKS -> mcLoc("block/nether_bricks"); case STONE_BRICKS -> mcLoc("block/stone_bricks");
                case MOSSY_COBBLESTONE -> mcLoc("block/mossy_cobblestone"); case ICE -> mcLoc("block/ice");
                case COBBLESTONE -> mcLoc("block/cobblestone"); case END_STONE -> mcLoc("block/end_stone");
                case NETHER_WART_BLOCK -> mcLoc("block/nether_wart_block");
                case PURPUR_BLOCK -> mcLoc("block/purpur_block");
                case END_STONE_BRICKS -> mcLoc("block/end_stone_bricks");
            });
        }
        private void ancientTablet(RegistryObject<MechanismBlocks.AncientTablet> block) {
            getVariantBuilder(block.get()).forAllStates(state -> ConfiguredModel.builder().modelFile(existing("ancient_tablet"))
                    .rotationY(horizontalRotation(state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING))).build());
            stageNineBlockItem(block.get(), existing("ancient_tablet"));
        }
        private static int horizontalRotation(net.minecraft.core.Direction direction) {
            return switch (direction) { case NORTH -> 0; case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
        }
        private static int xRotation(net.minecraft.core.Direction direction) {
            return direction == net.minecraft.core.Direction.UP ? 0 : direction == net.minecraft.core.Direction.DOWN ? 180 : 90;
        }
        private static int yRotation(net.minecraft.core.Direction direction) {
            return direction.getAxis().isHorizontal() ? horizontalRotation(direction) : 0;
        }
    }

    private static final class EnglishLanguage extends LanguageProvider {
        private final String locale;
        private EnglishLanguage(PackOutput output) {
            this(output, "en_us");
        }
        private EnglishLanguage(PackOutput output, String locale) {
            super(output, ZeldaSwordSkills_Remastered.MOD_ID, locale);
            this.locale = locale;
        }

        @Override
        protected void addTranslations() {
            ZSSRegistries.allItems().stream().filter(item -> !ZSSRegistries.BLOCK_ITEMS.contains(item))
                    .filter(item -> !ZSSRegistries.MUSIC_DISC_ITEMS.contains(item))
                    .forEach(item -> add(item.get(), englishName(item.getId().getPath())));
            addMusicDiscTranslations(this, false, switch (locale) {
                case "fr_fr" -> "Disque musical";
                case "pl_pl" -> "Płyta muzyczna";
                default -> "Music Disc";
            });
            ZSSRegistries.BLOCKS.getEntries().forEach(block -> add(block.get(), block.get() == ZSSRegistries.DOOR_BOSS_END.get()
                    ? "End Temple Boss Door" : englishName(block.getId().getPath())));
            add("structure.zeldaswordskills_remastered.end_dungeon", "End Temple");
            add(ZSSRegistries.STUN.get(), "Stunned");
            add("attribute.name.zeldaswordskills_remastered.max_magic", "Maximum Magic");
            addElementalDamageTranslations(this, false);
            addGossipHints(this, false);
            add("container.zeldaswordskills_remastered.pedestal", "Master Sword Pedestal");
            add("container.zeldaswordskills_remastered.locked_chest", "Locked Chest");
            add("tooltip.zeldaswordskills_remastered.hero_bow_level", "Fairy upgrade: %s/3");
            add("message.zeldaswordskills_remastered.hero_bow_upgrade_required", "This arrow requires a level %s Hero's Bow. Visit a Great Fairy to upgrade it.");
            add("message.zeldaswordskills_remastered.arrow_magic_required", "This arrow requires %s magic.");
            add("tooltip.zeldaswordskills_remastered.heart_piece", "Combine four pieces to create a Bonus Heart skill orb");
            add("tooltip.zeldaswordskills_remastered.navi_bottle", "Right-click to release Navi, or bottle her again from an empty bottle");
            add("message.zeldaswordskills_remastered.navi_not_yours", "This Navi belongs to another hero");
            add("message.zeldaswordskills_remastered.navi_already_out", "Your Navi is already flying beside you");
            add("message.zeldaswordskills_remastered.navi_no_space", "There is no room to release Navi here");
            add("subtitles.zeldaswordskills_remastered.navi_voice", "Navi speaks");
            add("subtitles.zeldaswordskills_remastered.navi_float", "Navi: Floating");
            add("tooltip.zeldaswordskills_remastered.skill_orb.skill", "Skill: %s");
            add("tooltip.zeldaswordskills_remastered.skill_orb.id", "Skill ID: %s");
            add("message.zeldaswordskills_remastered.skill_added", "Added a point to %s. Current level: %s");
            add("message.zeldaswordskills_remastered.skill_max", "%s is already at its maximum level.");
            addSkillTranslations(this, Map.ofEntries(
                    Map.entry("sword_basic", "Basic Sword Skill"), Map.entry("helm_splitter", "Helm Splitter"),
                    Map.entry("dodge", "Dodge"), Map.entry("leaping_blow", "Ground Slam"),
                    Map.entry("parry", "Parry"), Map.entry("dash", "Dash"),
                    Map.entry("spin_attack", "Spin Attack"), Map.entry("super_spin_attack", "Super Spin Attack"),
                    Map.entry("sword_beam", "Sword Beam"), Map.entry("sword_break", "Sword Break"),
                    Map.entry("mortal_draw", "Iai Slash"), Map.entry("bonus_heart", "Bonus Heart"),
                    Map.entry("rising_cut", "Rising Cut"), Map.entry("ending_blow", "Fatal Strike"),
                    Map.entry("flash_assault", "Flash Assault"), Map.entry("double_jump", "Double Jump"),
                    Map.entry("continuous_flash", "Continuous Dash")));
            add("message.zeldaswordskills_remastered.bomb_bag_empty", "The Bomb Bag is empty");
            add("message.zeldaswordskills_remastered.bomb_bag_selected", "Selected: %s");
            add("tooltip.zeldaswordskills_remastered.bomb_bag.selected", "Selected type: %s");
            add("tooltip.zeldaswordskills_remastered.bomb_bag.store", "Hold the Bomb Bag and press Shift + Right Click to store bombs from your inventory");
            add("tooltip.zeldaswordskills_remastered.bomb_bag.switch", "Left Click to switch the bomb type to throw");
            add("message.zeldaswordskills_remastered.mirror_no_destination", "The Magic Mirror has no safe destination");
            add("message.zeldaswordskills_remastered.fairy_upgrade_cost", "The fairy requires %s emeralds for this upgrade");
            add("message.zeldaswordskills_remastered.fairy_revive", "The fairy you carried restored your strength");
            add("message.zeldaswordskills_remastered.fairy_upgrade_complete", "The fairy transformed your item into %s");
            add("message.zeldaswordskills_remastered.giant_sword_unworthy", "Biggoron will repair this blade after you gain ten Bonus Hearts");
            add("message.zeldaswordskills_remastered.giant_sword_cost", "Biggoron requires five emeralds to repair this blade");
            addStageNineMessages(this, false);
            add("key.categories.zeldaswordskills_remastered", "ZeldaSwordSkills_Remastered");
            add("key.zeldaswordskills_remastered.target", "Lock / Next Target");
            add("key.zeldaswordskills_remastered.clear_target", "Clear Target");
            add("key.zeldaswordskills_remastered.skill_book", "Sword Skill Book");
            add("hud.zeldaswordskills_remastered.target", "Locked: %s");
            add("hud.zeldaswordskills_remastered.combo", "Combo %s/%s  Damage %s");
            add("hud.zeldaswordskills_remastered.combo_finished", "Combo finished %s/%s  Damage %s");
            add("hud.zeldaswordskills_remastered.fatal_strike", "It says, you can go now");
            add("hud.zeldaswordskills_remastered.magic", "Magic %s / %s");
            addSoundSubtitles(this, false);
            addSongTranslations(this, Map.ofEntries(
                    Map.entry("epona", "Epona's Song"), Map.entry("healing", "Song of Healing"),
                    Map.entry("saria", "Saria's Song"), Map.entry("soaring", "Song of Soaring"),
                    Map.entry("storms", "Song of Storms"), Map.entry("sun", "Sun's Song"),
                    Map.entry("time", "Song of Time"), Map.entry("bolero", "Bolero of Fire"),
                    Map.entry("minuet", "Minuet of Forest"), Map.entry("prelude", "Prelude of Light"),
                    Map.entry("oath", "Oath to Order"), Map.entry("nocturne", "Nocturne of Shadow"),
                    Map.entry("requiem", "Requiem of Spirit"), Map.entry("serenade", "Serenade of Water"),
                    Map.entry("lullaby", "Zelda's Lullaby"), Map.entry("scarecrow", "Scarecrow's Song")));
            addInstrumentTranslations(this, Map.ofEntries(
                    Map.entry("instrument", "Play an instrument"), Map.entry("instrument.scarecrow", "Scarecrow melody"),
                    Map.entry("instrument.controls", "Score = key: A = Space, v = S, > = D, < = A, ^ = W. Esc: close"),
                    Map.entry("instrument.ready", "Play a learned melody"), Map.entry("instrument.playing", "Playing… %s s"),
                    Map.entry("instrument.failed", "That melody was not recognized — try again"),
                    Map.entry("instrument.effect_failed", "Melody recognized, but its effect is unavailable under current conditions"),
                    Map.entry("instrument.too_weak", "Melody recognized. This effect requires the Ocarina of Time or Goddess Harp"),
                    Map.entry("instrument.recorded", "Melody recorded — return in seven days"),
                    Map.entry("instrument.success", "Song completed"),
                    Map.entry("instrument.free_play_on", "Free play: On"), Map.entry("instrument.free_play_off", "Free play: Off")));
            addSongMessages(this, Map.ofEntries(
                    Map.entry("scarecrow_not_unique", "That melody conflicts with an existing song."),
                    Map.entry("scarecrow_mismatch", "Play exactly the melody you recorded."),
                    Map.entry("scarecrow_too_early", "The scarecrow needs seven in-game days to remember the melody."),
                    Map.entry("warp_saved", "Warp destination saved for %s.")));
            add("command.zeldaswordskills_remastered.song.unavailable", "This song cannot be learned by command: %s");
            add("command.zeldaswordskills_remastered.song.unknown", "Unknown song: %s");
            add("command.zeldaswordskills_remastered.song.learned", "Learned song: %s");
            add("command.zeldaswordskills_remastered.song.forgotten", "Forgot song: %s");
            add("command.zeldaswordskills_remastered.navi.reset", "Reset the target's Navi. The next release will create a new fairy.");
            add("command.zeldaswordskills_remastered.navi.call", "Your Navi has returned to your side.");
            add("command.zeldaswordskills_remastered.hearts.maximum", "The configured maximum is %s Heart Containers.");
            add("command.zeldaswordskills_remastered.hearts.set", "Set active Heart Containers to %s.");
            add("command.zeldaswordskills_remastered.quest.unknown", "Unknown quest: %s");
            add("command.zeldaswordskills_remastered.quest.unavailable", "Quest data is unavailable for %s.");
            add("command.zeldaswordskills_remastered.quest.achieve", "Completed quest %s for %s.");
            add("command.zeldaswordskills_remastered.quest.reset", "Reset quest %s for %s.");
            add("command.zeldaswordskills_remastered.quest.resetall", "Reset all quests for %s.");
            addAdvancementTranslations(this, false);
            addCreatureTranslations(this, false);
            addNpcTranslations(this, false);
            addQuestTranslations(this, false);
            addCreativeTabs(this, Map.of(
                    "skills", "Skills", "keys", "Keys", "tools", "Tools", "combat", "Combat",
                    "masks", "Masks", "treasures", "Treasures", "blocks", "Blocks", "spawn_eggs", "Spawn Eggs"));
        }
    }

    private static final class ChineseLanguage extends LanguageProvider {
        private ChineseLanguage(PackOutput output) {
            super(output, ZeldaSwordSkills_Remastered.MOD_ID, "zh_cn");
        }

        @Override
        protected void addTranslations() {
            add("structure.zeldaswordskills_remastered.end_dungeon", "终末神殿");
            ZSSRegistries.allItems().stream()
                    .filter(item -> !ZSSRegistries.BLOCK_ITEMS.contains(item) && !ZSSRegistries.SPAWN_EGG_ITEMS.contains(item))
                    .filter(item -> !ZSSRegistries.MUSIC_DISC_ITEMS.contains(item))
                    .forEach(item -> add(item.get(), chineseName(item.getId().getPath())));
            addMusicDiscTranslations(this, true, "音乐唱片");
            add(ZSSRegistries.PEDESTAL.get(), "大师之剑基座");
            add(ZSSRegistries.SECRET_ROOM_CORE.get(), "秘密房间核心");
            addGossipHints(this, true);
            addAdvancementTranslations(this, true);
            addBlockTranslations(this, Map.ofEntries(
                    Map.entry("navi_light", "娜薇之光"),
                    Map.entry("beam_wooden", "木制横梁"), Map.entry("bomb_flower", "炸弹花"), Map.entry("ceramic_jar", "陶罐"),
                    Map.entry("chest_invisible", "隐形宝箱"), Map.entry("chest_locked", "上锁宝箱"),
                    Map.entry("door_boss_desert", "沙漠首领之门"), Map.entry("door_boss_earth", "大地首领之门"),
                    Map.entry("door_boss_fire", "火焰首领之门"), Map.entry("door_boss_forest", "森林首领之门"),
                    Map.entry("door_boss_ice", "冰雪首领之门"), Map.entry("door_boss_water", "水之首领之门"),
                    Map.entry("door_boss_end", "终末神殿首领之门"),
                    Map.entry("door_locked", "上锁之门"), Map.entry("dungeon_core_stone", "石质地牢核心"),
                    Map.entry("dungeon_core_sandstone", "砂岩地牢核心"), Map.entry("dungeon_stone_stone", "石质地牢石"),
                    Map.entry("dungeon_stone_sandstone", "砂岩地牢石"), Map.entry("gossip_stone", "流言石"),
                    Map.entry("light_block", "轻型机关块"), Map.entry("heavy_block", "重型机关块"), Map.entry("hook_target", "定向钩爪靶"),
                    Map.entry("hook_target_all", "全向钩爪靶"), Map.entry("inscription", "乐曲铭文"), Map.entry("lever_giant", "巨型拉杆"),
                    Map.entry("peg_wooden", "木桩"), Map.entry("peg_rusty", "锈蚀铁桩"),
                    Map.entry("quake_stone", "震地石"),
                    Map.entry("quake_stone_mossy", "苔藓震地石"), Map.entry("sacred_flame_din", "丁之圣火"),
                    Map.entry("sacred_flame_farore", "法罗尔之圣火"), Map.entry("sacred_flame_nayru", "娜茹之圣火"),
                    Map.entry("secret_stone_stone", "石质秘密石"), Map.entry("secret_stone_sandstone", "砂岩秘密石"),
                    Map.entry("secret_stone_nether_bricks", "下界砖秘密石"), Map.entry("secret_stone_stone_bricks", "石砖秘密石"),
                    Map.entry("secret_stone_mossy_cobblestone", "苔石秘密石"), Map.entry("secret_stone_ice", "冰质秘密石"),
                    Map.entry("secret_stone_cobblestone", "圆石秘密石"), Map.entry("secret_stone_end_stone", "末地石秘密石"),
                    Map.entry("secret_stone_nether_wart_block", "下界疣块秘密石"),
                    Map.entry("secret_stone_purpur_block", "紫珀块秘密石"), Map.entry("secret_stone_end_stone_bricks", "末地石砖秘密石"),
                    Map.entry("time_block", "时间方块"), Map.entry("royal_block", "王族方块"),
                    Map.entry("warp_stone_bolero", "炎之传送石"), Map.entry("warp_stone_minuet", "森林传送石"),
                    Map.entry("warp_stone_prelude", "光之传送石"), Map.entry("warp_stone_oath", "誓约传送石"),
                    Map.entry("warp_stone_nocturne", "暗影传送石"), Map.entry("warp_stone_requiem", "魂之传送石"),
                    Map.entry("warp_stone_serenade", "水之传送石"),
                    Map.entry("ancient_tablet_bombos", "爆炎古代石板"), Map.entry("ancient_tablet_ether", "以太古代石板"),
                    Map.entry("ancient_tablet_quake", "震地古代石板")));
            add(ZSSRegistries.STUN.get(), "眩晕");
            add("attribute.name.zeldaswordskills_remastered.max_magic", "魔力上限");
            addElementalDamageTranslations(this, true);
            add("container.zeldaswordskills_remastered.pedestal", "大师之剑基座");
            add("container.zeldaswordskills_remastered.locked_chest", "上锁宝箱");
            add("tooltip.zeldaswordskills_remastered.hero_bow_level", "大妖精升级：%s/3");
            add("message.zeldaswordskills_remastered.hero_bow_upgrade_required", "此箭需要%s级勇者之弓，请找大妖精升级。");
            add("message.zeldaswordskills_remastered.arrow_magic_required", "此箭需要%s点魔力。");
            add("tooltip.zeldaswordskills_remastered.heart_piece", "四个碎片可合成一枚额外心之技能球");
            add("tooltip.zeldaswordskills_remastered.navi_bottle", "右键释放娜薇，也可用空瓶子再次将她收起");
            add("message.zeldaswordskills_remastered.navi_not_yours", "这只娜薇属于别的勇者");
            add("message.zeldaswordskills_remastered.navi_already_out", "你的娜薇已经在身边飞舞了");
            add("message.zeldaswordskills_remastered.navi_no_space", "这里没有足够的空间释放娜薇");
            add("subtitles.zeldaswordskills_remastered.navi_voice", "娜薇发声");
            add("subtitles.zeldaswordskills_remastered.navi_float", "娜薇：飘动");
            add("tooltip.zeldaswordskills_remastered.skill_orb.skill", "技能：%s");
            add("tooltip.zeldaswordskills_remastered.skill_orb.id", "技能键值：%s");
            add("message.zeldaswordskills_remastered.skill_added", "已为 %s 加点，当前等级：%s");
            add("message.zeldaswordskills_remastered.skill_max", "%s 已达到最高等级。");
            addSkillTranslations(this, Map.ofEntries(
                    Map.entry("sword_basic", "基础剑术"), Map.entry("helm_splitter", "兜割"),
                    Map.entry("dodge", "闪避"), Map.entry("leaping_blow", "坠地斩"),
                    Map.entry("parry", "弹反"), Map.entry("dash", "冲刺斩"),
                    Map.entry("spin_attack", "回旋斩"), Map.entry("super_spin_attack", "超级回旋斩"),
                    Map.entry("sword_beam", "剑气"), Map.entry("sword_break", "断剑术"),
                    Map.entry("mortal_draw", "居合斩"), Map.entry("bonus_heart", "额外心之容器"),
                    Map.entry("rising_cut", "升龙斩"), Map.entry("ending_blow", "致命一击"),
                    Map.entry("flash_assault", "闪袭"), Map.entry("double_jump", "二段跳"),
                    Map.entry("continuous_flash", "连续冲刺斩")));
            add("message.zeldaswordskills_remastered.bomb_bag_empty", "炸弹袋是空的");
            add("message.zeldaswordskills_remastered.bomb_bag_selected", "已切换为：%s");
            add("tooltip.zeldaswordskills_remastered.bomb_bag.selected", "当前类型：%s");
            add("tooltip.zeldaswordskills_remastered.bomb_bag.store", "手持炸弹袋按住Shift+右键，可收纳身上炸弹");
            add("tooltip.zeldaswordskills_remastered.bomb_bag.switch", "点击左键，可切换投掷炸弹类型");
            add("message.zeldaswordskills_remastered.mirror_no_destination", "魔镜没有可用的安全目的地");
            add("message.zeldaswordskills_remastered.fairy_upgrade_cost", "妖精需要 %s 枚绿宝石才能完成这次升级");
            add("message.zeldaswordskills_remastered.fairy_revive", "身上的妖精使你恢复了力量");
            add("message.zeldaswordskills_remastered.fairy_upgrade_complete", "妖精将物品转化为了%s");
            add("message.zeldaswordskills_remastered.giant_sword_unworthy", "获得十颗额外心后，大哥隆才会修复这把剑");
            add("message.zeldaswordskills_remastered.giant_sword_cost", "大哥隆需要五枚绿宝石来修复这把剑");
            addStageNineMessages(this, true);
            add("key.categories.zeldaswordskills_remastered", "塞尔达剑技");
            add("key.zeldaswordskills_remastered.target", "锁定／切换目标");
            add("key.zeldaswordskills_remastered.clear_target", "解除目标锁定");
            add("key.zeldaswordskills_remastered.skill_book", "剑技技能书");
            add("hud.zeldaswordskills_remastered.target", "锁定：%s");
            add("hud.zeldaswordskills_remastered.combo", "连击 %s/%s  伤害 %s");
            add("hud.zeldaswordskills_remastered.combo_finished", "连击结束 %s/%s  伤害 %s");
            add("hud.zeldaswordskills_remastered.fatal_strike", "它说，可以上了");
            add("hud.zeldaswordskills_remastered.magic", "魔力 %s / %s");
            addSoundSubtitles(this, true);
            addSongTranslations(this, Map.ofEntries(
                    Map.entry("epona", "艾波娜之歌"), Map.entry("healing", "治愈之歌"),
                    Map.entry("saria", "莎莉亚之歌"), Map.entry("soaring", "翱翔之歌"),
                    Map.entry("storms", "暴风雨之歌"), Map.entry("sun", "太阳之歌"),
                    Map.entry("time", "时间之歌"), Map.entry("bolero", "炎之波莱罗"),
                    Map.entry("minuet", "森林小步舞曲"), Map.entry("prelude", "光之序曲"),
                    Map.entry("oath", "誓约之号令"), Map.entry("nocturne", "暗影夜曲"),
                    Map.entry("requiem", "魂之挽歌"), Map.entry("serenade", "水之小夜曲"),
                    Map.entry("lullaby", "塞尔达摇篮曲"), Map.entry("scarecrow", "稻草人之歌")));
            addInstrumentTranslations(this, Map.ofEntries(
                    Map.entry("instrument", "演奏乐器"), Map.entry("instrument.scarecrow", "稻草人旋律"),
                    Map.entry("instrument.controls", "曲谱=按键：A=空格，v=S，>=D，<=A，^=W。Esc：关闭"),
                    Map.entry("instrument.ready", "演奏一首已学会的旋律"), Map.entry("instrument.playing", "演奏中……%s 秒"),
                    Map.entry("instrument.failed", "未识别该旋律，请重试"),
                    Map.entry("instrument.effect_failed", "旋律已识别，但当前条件不满足，无法生效"),
                    Map.entry("instrument.too_weak", "旋律已识别，此效果需要时之笛或女神竖琴"),
                    Map.entry("instrument.recorded", "旋律已记录，请在七天后回来"),
                    Map.entry("instrument.success", "乐曲演奏完成"),
                    Map.entry("instrument.free_play_on", "自由演奏：开启"), Map.entry("instrument.free_play_off", "自由演奏：关闭")));
            addSongMessages(this, Map.ofEntries(
                    Map.entry("scarecrow_not_unique", "该旋律与现有乐曲冲突。"),
                    Map.entry("scarecrow_mismatch", "请准确演奏先前记录的旋律。"),
                    Map.entry("scarecrow_too_early", "稻草人需要七个游戏日来记住旋律。"),
                    Map.entry("warp_saved", "已保存 %s 的传送目的地。")));
            add("command.zeldaswordskills_remastered.song.unavailable", "无法通过命令学习该乐曲：%s");
            add("command.zeldaswordskills_remastered.song.unknown", "未知乐曲：%s");
            add("command.zeldaswordskills_remastered.song.learned", "已学会乐曲：%s");
            add("command.zeldaswordskills_remastered.song.forgotten", "已遗忘乐曲：%s");
            add("command.zeldaswordskills_remastered.navi.reset", "已重置目标的娜薇状态，下一次释放将召唤新的娜薇。");
            add("command.zeldaswordskills_remastered.navi.call", "娜薇已回到你身边。");
            add("command.zeldaswordskills_remastered.hearts.maximum", "心之容器数量不能超过配置上限：%s。");
            add("command.zeldaswordskills_remastered.hearts.set", "已将生效的心之容器数量设为%s。");
            add("command.zeldaswordskills_remastered.quest.unknown", "未知任务：%s");
            add("command.zeldaswordskills_remastered.quest.unavailable", "无法读取 %s 的任务数据。");
            add("command.zeldaswordskills_remastered.quest.achieve", "已完成任务 %s，玩家：%s。");
            add("command.zeldaswordskills_remastered.quest.reset", "已重置任务 %s，玩家：%s。");
            add("command.zeldaswordskills_remastered.quest.resetall", "已重置 %s 的所有任务。");
            addCreatureTranslations(this, true);
            addNpcTranslations(this, true);
            addQuestTranslations(this, true);
            addCreativeTabs(this, Map.of(
                    "skills", "技能", "keys", "钥匙", "tools", "工具", "combat", "战斗",
                    "masks", "面具", "treasures", "宝物", "blocks", "方块", "spawn_eggs", "刷怪蛋"));
        }
    }

    private static void addAdvancementTranslations(LanguageProvider provider, boolean chinese) {
        provider.add("advancements.zeldaswordskills_remastered.adventure_begins.tab", chinese ? "塞尔达传说" : "The Legend of Zelda");
        provider.add("advancements.zeldaswordskills_remastered.ocarina.song.scarecrow.description",
                chinese ? "目前暂无作用的歌曲" : "This song currently has no effect");
        for (String id : ZSSAdvancementProvider.ids()) {
            if (id.startsWith("ocarina.song.")) continue;
            String[] text = advancementText(id);
            String title = chinese ? text[0] : englishName(id);
            provider.add("advancements.zeldaswordskills_remastered." + id + ".title", title);
            provider.add("advancements.zeldaswordskills_remastered." + id + ".description",
                    text[chinese ? 1 : 2]);
        }
    }

    private static String[] advancementText(String id) {
        return switch (id) {
            case "adventure_begins" -> new String[]{"冒险伊始", "转生成为勇者然后天下无敌", "Reborn as a hero, then invincible across the world"};
            case "bombs_away" -> new String[]{"发现一间秘密房间", "发现一间秘密房间", "Discover a secret room"};
            case "bomb_junkie" -> new String[]{"秘密探索者", "发现五十间秘密房间", "Discover 50 secret rooms"};
            case "boss_battle" -> new String[]{"神殿首胜", "完成一座神殿", "Complete a temple"};
            case "temple.water" -> new String[]{"战胜水之神殿", "完成水之神殿", "Complete the Water Temple"};
            case "temple.desert" -> new String[]{"战胜沙之神殿", "完成沙之神殿", "Complete the Desert Temple"};
            case "temple.ice" -> new String[]{"战胜冰之神殿", "完成冰之神殿", "Complete the Ice Temple"};
            case "temple.forest" -> new String[]{"战胜森林神殿", "完成水、沙、冰三殿与森林神殿", "Complete the Water, Desert, Ice and Forest Temples"};
            case "temple.earth" -> new String[]{"战胜大地神殿", "完成森林神殿进度后，完成大地神殿", "Complete the Earth Temple after the Forest Temple advancement"};
            case "temple.fire" -> new String[]{"战胜火之神殿", "完成大地神殿进度后，完成火之神殿", "Complete the Fire Temple after the Earth Temple advancement"};
            case "temple.end" -> new String[]{"战胜终末神殿", "完成火之神殿进度后，完成终末神殿", "Complete the End Temple after the Fire Temple advancement"};
            case "sword.pendant" -> new String[]{"三枚吊坠", "向塞尔达交付三枚吊坠", "Deliver all three pendants to Zelda"};
            case "sword.master" -> new String[]{"大师剑", "拔出大师剑", "Draw the Master Sword"};
            case "sword.tempered" -> new String[]{"淬炼之路", "以大师矿石开启淬炼交易", "Offer Master Ore to unlock tempering"};
            case "sword.evil" -> new String[]{"斩除邪恶", "以淬炼剑击败三百个敌人", "Defeat 300 monsters with a Tempered Sword"};
            case "sword.golden" -> new String[]{"黄金剑", "在妖精池将淬炼剑升级为黄金剑", "Upgrade a Tempered Sword at a fairy pool"};
            case "sword.flame" -> new String[]{"圣火加护", "让大师剑系武器吸收圣火", "Infuse a Master Sword with sacred flame"};
            case "sword.true" -> new String[]{"真大师剑", "完成基座的真大师剑仪式", "Complete the True Master Sword pedestal ritual"};
            case "shield.mirror" -> new String[]{"镜盾", "持真大师剑请妖精升级海利亚盾", "Receive a Mirror Shield from a fairy"};
            case "fairy.catcher" -> new String[]{"瓶中妖精", "用玻璃瓶捕捉妖精", "Capture a fairy in a glass bottle"};
            case "fairy.emerald" -> new String[]{"妖精的礼物", "支付绿宝石完成妖精升级", "Pay emeralds for a fairy upgrade"};
            case "fairy.bow" -> new String[]{"勇者弓升级", "将勇者弓升级到二级", "Upgrade the Hero Bow to level 2"};
            case "fairy.bow_max" -> new String[]{"光之箭之弓", "将勇者弓升级到三级以使用光之箭", "Upgrade the Hero Bow to level 3 for Light Arrows"};
            case "fairy.enchantment" -> new String[]{"妖精附魔", "在妖精池为弹弓获得力量附魔", "Receive a fairy Power enchantment for a slingshot"};
            case "fairy.slingshot" -> new String[]{"散射弹弓", "请妖精升级弹弓", "Receive the first slingshot upgrade"};
            case "fairy.supershot" -> new String[]{"超级弹弓", "请妖精将散射弹弓升级到最高级", "Receive the Supershot upgrade"};
            case "fairy.boomerang" -> new String[]{"魔法回旋镖", "请妖精升级回旋镖", "Receive a Magic Boomerang from a fairy"};
            case "hammer.wood" -> new String[]{"敲下木桩", "成功敲击一个木桩", "Strike a peg successfully"};
            case "hammer.silver" -> new String[]{"搬山之力", "举起或砸碎重物方块", "Lift or smash a heavy block"};
            case "hammer.skull" -> new String[]{"强力一击", "成功敲击锈蚀木桩", "Strike a rusty peg successfully"};
            case "hammer.golden" -> new String[]{"巨力", "举起或砸碎最重的重物方块", "Lift or smash a very heavy block"};
            case "mask.trader" -> new String[]{"面具商人", "完成面具商人的引荐", "Establish a mask trader"};
            case "mask.sold" -> new String[]{"第一笔销售", "向村民出售借来的面具", "Sell a borrowed mask to a villager"};
            case "mask.shop" -> new String[]{"真实面具商店", "完成六次面具销售并结清货款", "Complete and repay all six mask sales"};
            case "skill.basic" -> new String[]{"基础剑技", "成功学习基础剑技", "Learn Basic Sword Skill"};
            case "skill.gain" -> new String[]{"旅途的收获", "在战利品宝箱中获得额外的技能球", "Obtain an extra skill orb from a loot chest"};
            case "skill.all_types" -> new String[]{"精通万般剑术", "学会所有种类的剑技（心之容器不计入）", "Learn every type of sword skill (Heart Containers excluded)"};
            case "skill.heart" -> new String[]{"额外的心", "获得一颗额外心", "Gain one Bonus Heart"};
            case "skill.heartbar" -> new String[]{"第二排心", "获得十颗额外心", "Gain ten Bonus Hearts"};
            case "skill.hearts_galore" -> new String[]{"充沛生命", "获得二十颗额外心", "Gain twenty Bonus Hearts"};
            case "combo.basic" -> new String[]{"三连击", "在一次连击中命中三次", "Land 3 hits in one combo"};
            case "combo.perfect" -> new String[]{"八连击", "在一次连击中命中八次", "Land 8 hits in one combo"};
            case "combo.legend" -> new String[]{"十二连击", "在一次连击中命中十二次", "Land 12 hits in one combo"};
            case "sword.broken" -> new String[]{"修剑资格", "获得十颗额外心并向大哥隆出示断剑", "Present a broken sword with ten Bonus Hearts"};
            case "treasure.first" -> new String[]{"宝藏交易", "在大哥隆任务中换得触手", "Trade for a Tentacle in the Biggoron quest"};
            case "treasure.second" -> new String[]{"口袋蛋", "在大哥隆任务中换得口袋蛋", "Trade for a Pocket Egg in the Biggoron quest"};
            case "treasure.biggoron" -> new String[]{"大哥隆剑", "完成大哥隆剑交易任务", "Complete the Biggoron Sword quest"};
            case "orca.thief" -> new String[]{"鞭取战利品", "用鞭从敌人身上取得战利品", "Steal loot from an enemy with a whip"};
            case "orca.deknighted" -> new String[]{"骑士纹章", "用鞭取得骑士纹章", "Steal a Knight's Crest with a whip"};
            case "orca.request" -> new String[]{"Orca 的请求", "向 Orca 交付第一枚纹章", "Give Orca your first crest"};
            case "orca.first" -> new String[]{"第一课", "向 Orca 交付十枚纹章", "Give Orca 10 crests"};
            case "orca.canopener" -> new String[]{"破甲", "打破暗黑骑士的护甲", "Break a Darknut's armor"};
            case "orca.second" -> new String[]{"第二课", "向 Orca 交付二十枚纹章", "Give Orca 20 crests"};
            case "orca.master" -> new String[]{"Orca 的大师课", "向 Orca 交付一百枚纹章", "Give Orca 100 crests"};
            case "ocarina.craft" -> new String[]{"乐器制作", "制作一件乐器", "Craft an instrument"};
            case "ocarina.song" -> new String[]{"第一首乐曲", "学会一首乐曲", "Learn a song"};
            case "ocarina.scarecrow" -> new String[]{"稻草人之歌", "七天后确认自创稻草人旋律", "Confirm your Scarecrow Song after seven days"};
            case "ocarina.maestro" -> new String[]{"乐曲大师", "学会全部十六首乐曲", "Learn all sixteen songs"};
            default -> throw new IllegalArgumentException("Missing advancement text: " + id);
        };
    }

    private static String englishName(String path) {
        String key = path.replace('.', '_');
        return switch (key) {
            case "adventure_begins" -> "Adventure Begins";
            case "bombs_away" -> "Discover a Secret Room";
            case "temple_water" -> "Conquer the Water Temple";
            case "temple_desert" -> "Conquer the Desert Temple";
            case "temple_ice" -> "Conquer the Ice Temple";
            case "temple_forest" -> "Conquer the Forest Temple";
            case "temple_earth" -> "Conquer the Earth Temple";
            case "temple_fire" -> "Conquer the Fire Temple";
            case "temple_end" -> "Conquer the End Temple";
            case "skill_gain" -> "Journey's Reward";
            case "skill_all_types" -> "Master of Every Sword";
            case "pendant_power" -> "Pendant of Power";
            case "pendant_wisdom" -> "Pendant of Wisdom";
            case "pendant_courage" -> "Pendant of Courage";
            case "rocs_feather" -> "Roc's Feather";
            case "zeldas_letter" -> "Zelda's Letter";
            case "pedestal" -> "Master Sword Pedestal";
            case "navi_bottle" -> "Navi in a Bottle";
            default -> Arrays.stream(key.split("_")).map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                    .collect(Collectors.joining(" "));
        };
    }

    private static String chineseName(String path) {
        String key = path.replace('.', '_');
        return switch (key) {
            case "magic_whip" -> "魔法鞭子";
            case "stoneshot" -> "石质钩爪";
            case "multishot" -> "多重钩爪";
            case "skill_all_types" -> "精通万般剑术";
            case "skill_orb" -> "技能球"; case "heart_piece" -> "心之碎片"; case "skill_wiper" -> "技能清除器";
            case "big_key" -> "首领钥匙"; case "small_key" -> "小钥匙"; case "skeleton_key" -> "骷髅钥匙";
            case "master_ore" -> "大师矿石"; case "pendant_power" -> "力量吊坠";
            case "pendant_wisdom" -> "智慧吊坠"; case "pendant_courage" -> "勇气吊坠";
            case "throwing_rock" -> "投掷石块"; case "rocs_feather" -> "洛克鸟羽毛";
            case "zeldas_letter" -> "塞尔达的信"; case "links_house" -> "林克之家";
            case "navi_bottle" -> "装有娜薇的瓶子";
            case "lon_lon_milk" -> "隆隆牛奶"; case "lon_lon_special" -> "隆隆特饮"; case "standard_bomb" -> "炸弹";
            case "fairy_ocarina" -> "妖精笛"; case "ocarina_of_time" -> "时之笛"; case "goddess_harp" -> "女神竖琴";
            case "book_of_mudora" -> "穆德拉之书"; case "mask_of_truth" -> "真实之面";
            case "mask_of_scents" -> "香气面具"; case "fierce_deity_mask" -> "鬼神面具"; case "majora_mask" -> "魔吉拉面具";
            case "hero_bow" -> "勇者之弓"; case "deku_leaf" -> "德库叶"; case "deku_nut" -> "德库坚果";
            case "bombos_medallion" -> "爆炎徽章"; case "ether_medallion" -> "以太徽章"; case "quake_medallion" -> "震地徽章";
            case "din_crystal" -> "丁之水晶"; case "farore_crystal" -> "法罗尔水晶"; case "nayru_crystal" -> "娜茹水晶";
            default -> Arrays.stream(key.split("_")).map(ZSSDataGenerators::chineseWord).collect(Collectors.joining());
        };
    }

    private static String chineseWord(String word) {
        return switch (word) {
            case "hero" -> "勇者"; case "tunic" -> "服"; case "helmet" -> "头盔"; case "chestplate" -> "胸甲";
            case "leggings" -> "护腿"; case "boots" -> "靴"; case "goron" -> "鼓隆"; case "zora" -> "卓拉";
            case "heavy" -> "沉重"; case "hover" -> "悬浮"; case "pegasus" -> "天马"; case "rubber" -> "橡胶";
            case "shield" -> "盾"; case "deku" -> "德库"; case "hylian" -> "海利亚"; case "mirror" -> "镜";
            case "sword" -> "剑"; case "broken" -> "断裂"; case "kokiri" -> "克洛格"; case "ordon" -> "奥尔汀";
            case "giant" -> "巨人"; case "biggoron" -> "大哥隆"; case "master" -> "大师"; case "tempered" -> "淬炼";
            case "golden" -> "黄金"; case "true" -> "真"; case "darknut" -> "黑甲武士"; case "wooden" -> "木制";
            case "hammer" -> "锤"; case "skull" -> "骷髅"; case "megaton" -> "百万吨"; case "boomerang" -> "回旋镖";
            case "magic" -> "魔法"; case "bomb" -> "炸弹"; case "fire" -> "火焰"; case "water" -> "水";
            case "arrow" -> "箭"; case "ice" -> "冰霜"; case "light" -> "光"; case "slingshot" -> "弹弓";
            case "scattershot" -> "散射弹弓"; case "supershot" -> "超级弹弓"; case "hookshot" -> "钩爪";
            case "extender" -> "延长器"; case "claw" -> "爪钩"; case "upgrade" -> "升级件"; case "multi" -> "多重";
            case "bag" -> "袋"; case "standard" -> "标准"; case "empty" -> "空"; case "spirit" -> "灵魂";
            case "crystal" -> "水晶"; case "silver" -> "银"; case "gauntlets" -> "手套"; case "fairy" -> "妖精";
            case "bottle" -> "瓶"; case "navi" -> "娜薇"; case "red" -> "红色"; case "green" -> "绿色"; case "blue" -> "蓝色";
            case "yellow" -> "黄色"; case "purple" -> "紫色"; case "potion" -> "药水"; case "small" -> "小型";
            case "large" -> "大型"; case "jar" -> "罐"; case "container" -> "容器"; case "medallion" -> "徽章";
            case "rod" -> "法杖"; case "tornado" -> "龙卷风"; case "whip" -> "鞭"; case "flower" -> "花";
            case "seed" -> "种子"; case "held" -> "举起的"; case "block" -> "方块"; case "blast" -> "爆炸";
            case "mask" -> "面具"; case "bunny" -> "兔子"; case "hood" -> "头巾"; case "couples" -> "情侣";
            case "gerudo" -> "格鲁德"; case "giants" -> "巨人"; case "gibdo" -> "吉波德"; case "hawkeye" -> "鹰眼";
            case "keaton" -> "基顿"; case "scents" -> "香气"; case "spooky" -> "诡异"; case "stone" -> "石头";
            case "truth" -> "真实"; case "fierce" -> "凶猛"; case "deity" -> "鬼神"; case "majora" -> "魔吉拉";
            case "pendant" -> "吊坠"; case "wisdom" -> "智慧"; case "courage" -> "勇气"; case "power" -> "力量";
            case "ore" -> "矿石"; case "chu" -> "丘丘"; case "jelly" -> "果冻"; case "skulltula" -> "骷髅蜘蛛";
            case "token" -> "徽记"; case "piece" -> "碎片"; case "heart" -> "心"; case "claim" -> "领取";
            case "check" -> "凭证"; case "evil" -> "邪恶"; case "eye" -> "眼"; case "drops" -> "药水";
            case "eyeball" -> "眼球"; case "frog" -> "青蛙"; case "jellyblob" -> "黏液团"; case "monster" -> "怪物";
            case "odd" -> "奇怪的"; case "mushroom" -> "蘑菇"; case "poacher" -> "偷猎者"; case "saw" -> "锯";
            case "pocket" -> "口袋"; case "egg" -> "蛋"; case "prescription" -> "处方"; case "tentacle" -> "触手";
            case "knights" -> "骑士"; case "crest" -> "纹章"; case "cojiro" -> "小次郎"; case "links" -> "林克";
            case "house" -> "之家"; case "milk" -> "牛奶"; case "special" -> "特饮"; case "wiper" -> "清除器";
            case "orb" -> "球"; default -> englishName(word);
        };
    }

    private static void addCreativeTabs(LanguageProvider provider, Map<String, String> names) {
        names.forEach((id, name) -> provider.add("itemGroup.zeldaswordskills_remastered." + id, name));
    }

    private static void addMusicDiscTranslations(LanguageProvider provider, boolean chinese, String itemName) {
        provider.add("itemGroup.zeldaswordskills_remastered.zelda_ost", chinese ? "塞尔达传说OST" : "Zelda OST");
        MusicDiscCatalog.DISCS.forEach(disc -> {
            String key = "item.zeldaswordskills_remastered." + disc.id();
            provider.add(key, itemName);
            provider.add(key + ".desc", disc.title());
        });
    }

    private static void addSoundSubtitles(LanguageProvider provider, boolean chinese) {
        Map<String, String> english = Map.ofEntries(
                Map.entry("fatal_strike_hit", "Fatal Strike: Hit"),
                Map.entry("fatal_strike_focus", "Focus"),
                Map.entry("flash_assault_dodge", "Dodge: Success"),
                Map.entry("flash_assault_dash", "Flash Assault"),
                Map.entry("flash_assault_attack", "Flash Assault: Attack"),
                Map.entry("rising_cut_slash", "Rising Cut"),
                Map.entry("parry_success", "Parry: Success"), Map.entry("sword_break", "Sword Break: Release"),
                Map.entry("armor_shatter", "Armor breaks"), Map.entry("helm_splitter_hit", "Helm Splitter: Hit"),
                Map.entry("hookshot", "Hookshot chain rattles"), Map.entry("boomerang", "Boomerang flies"),
                Map.entry("leaping_blow", "Ground slam"), Map.entry("level_up", "Power increases"),
                Map.entry("magic_failure", "Magic fizzles"), Map.entry("magic_fire", "Fire magic bursts"),
                Map.entry("magic_ice", "Ice magic bursts"), Map.entry("mortal_draw", "Iai slash"),
                Map.entry("note_ocarina", "Ocarina note"), Map.entry("rock_fall", "Rocks fall"),
                Map.entry("secret_medley", "Secret discovered"), Map.entry("song", "Song plays"),
                Map.entry("spin_attack", "Spin attack"), Map.entry("success", "Success chime"),
                Map.entry("sword_cut", "Sword slices"), Map.entry("sword_miss", "Sword whooshes"),
                Map.entry("sword_strike", "Sword strikes"), Map.entry("web_splat", "Web splats"),
                Map.entry("whip", "Whip cracks"), Map.entry("whirlwind", "Whirlwind rushes"));
        Map<String, String> chineseNames = Map.ofEntries(
                Map.entry("fatal_strike_hit", "致命一击：命中"),
                Map.entry("fatal_strike_focus", "会心"),
                Map.entry("flash_assault_dodge", "闪避：成功"),
                Map.entry("flash_assault_dash", "突袭"),
                Map.entry("flash_assault_attack", "突袭：攻击"),
                Map.entry("rising_cut_slash", "升龙斩"),
                Map.entry("parry_success", "弹反：成功"), Map.entry("sword_break", "断剑斩：释放"),
                Map.entry("armor_shatter", "护甲碎裂"), Map.entry("helm_splitter_hit", "兜割：命中"),
                Map.entry("hookshot", "钩爪：锁链晃动"), Map.entry("boomerang", "回旋镖：飞行"),
                Map.entry("leaping_blow", "坠地斩"), Map.entry("level_up", "力量提升"),
                Map.entry("magic_failure", "魔力消散"), Map.entry("magic_fire", "火焰魔法爆发"),
                Map.entry("magic_ice", "冰霜魔法爆发"), Map.entry("mortal_draw", "居合斩"),
                Map.entry("note_ocarina", "陶笛音符"), Map.entry("rock_fall", "岩石坠落"),
                Map.entry("secret_medley", "发现秘密"), Map.entry("song", "乐曲奏响"),
                Map.entry("spin_attack", "回旋斩"), Map.entry("success", "成功提示音"),
                Map.entry("sword_cut", "剑刃切过"), Map.entry("sword_miss", "剑刃挥空"),
                Map.entry("sword_strike", "剑刃命中"), Map.entry("web_splat", "蛛网飞溅"),
                Map.entry("whip", "长鞭破空"), Map.entry("whirlwind", "旋风呼啸"));
        (chinese ? chineseNames : english).forEach((id, value) ->
                provider.add("subtitles." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, value));
    }

    private static void addBlockTranslations(LanguageProvider provider, Map<String, String> names) {
        names.forEach((id, name) -> provider.add("block." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, name));
    }

    private static void addElementalDamageTranslations(LanguageProvider provider, boolean chinese) {
        Map<String, String> names = chinese ? Map.of(
                "fire", "%1$s 被魔法火焰吞噬了", "ice", "%1$s 被寒冰冻伤了", "lightning", "%1$s 被雷电击中了",
                "wind", "%1$s 被狂风卷走了", "holy", "%1$s 被圣光击败了", "magic", "%1$s 被魔法击败了",
                "quake", "%1$s 被震地之力击败了") : Map.of(
                "fire", "%1$s was consumed by magical fire", "ice", "%1$s was frozen by magic",
                "lightning", "%1$s was struck by magical lightning", "wind", "%1$s was swept away by magic",
                "holy", "%1$s was defeated by holy power", "magic", "%1$s was defeated by magic",
                "quake", "%1$s was defeated by quake force");
        names.forEach((id, message) -> provider.add("death.attack.zss_" + id, message));
    }

    private static void addSongTranslations(LanguageProvider provider, Map<String, String> names) {
        names.forEach((id, name) -> provider.add("song." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, name));
    }

    private static void addSkillTranslations(LanguageProvider provider, Map<String, String> names) {
        names.forEach((id, name) -> provider.add("skill." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, name));
    }

    private static void addStageNineMessages(LanguageProvider provider, boolean chinese) {
        provider.add("tooltip.zeldaswordskills_remastered.big_key.dungeon", chinese ? "对应门：%s" : "Opens: %s");
        provider.add("tooltip.zeldaswordskills_remastered.big_key.unbound", chinese ? "未绑定地牢，无法开启首领之门" : "Unbound key: cannot open a boss door");
        Map<String, String> values = chinese ? Map.ofEntries(
                Map.entry("gossip.default", "流言石保持着沉默。"), Map.entry("gossip.saved", "流言石的信息已保存。"),
                Map.entry("gossip.denied", "你无权修改这块流言石，或文字超过了 192 字符。"), Map.entry("gossip.silent", "只有真实之面能听见流言石的声音。"),
                Map.entry("inscription.changed", "铭文所教授的乐曲已切换。"), Map.entry("inscription.denied", "你无权修改这块铭文。"),
                Map.entry("inscription.learned", "你从铭文中学会了：%s"), Map.entry("inscription.instrument", "手持乐器即可学习铭文中的乐曲。"),
                Map.entry("flame.extinguished", "圣火已经熄灭，尚未恢复。"), Map.entry("flame.unaffected", "手中的物品无法承载这团圣火。"),
                Map.entry("tablet.dark", "光线太暗，无法辨认石板。"), Map.entry("tablet.unknown", "需要《穆德拉之书》才能解读石板。"),
                Map.entry("tablet.master_sword", "石板回应了文字，但需要大师之剑的力量。"), Map.entry("tablet.confined", "石板上方必须留出五格空间。"))
                : Map.ofEntries(
                Map.entry("gossip.default", "The Gossip Stone remains silent."), Map.entry("gossip.saved", "The Gossip Stone message was saved."),
                Map.entry("gossip.denied", "You may not edit this stone, or the message exceeds 192 characters."), Map.entry("gossip.silent", "Only the Mask of Truth can hear this stone."),
                Map.entry("inscription.changed", "The inscription now teaches another song."), Map.entry("inscription.denied", "You may not edit this inscription."),
                Map.entry("inscription.learned", "You learned %s from the inscription."), Map.entry("inscription.instrument", "Hold an instrument to learn the inscribed song."),
                Map.entry("flame.extinguished", "The sacred flame is extinguished and has not recovered."), Map.entry("flame.unaffected", "The held item cannot receive this sacred flame."),
                Map.entry("tablet.dark", "It is too dark to read the tablet."), Map.entry("tablet.unknown", "The Book of Mudora is required to decipher the tablet."),
                Map.entry("tablet.master_sword", "The tablet answers, but requires the Master Sword's power."), Map.entry("tablet.confined", "Five clear blocks are required above the tablet."));
        values.forEach((id, value) -> provider.add("message." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, value));
    }

    private static void addInstrumentTranslations(LanguageProvider provider, Map<String, String> names) {
        names.forEach((id, name) -> provider.add("hud." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, name));
    }

    private static void addSongMessages(LanguageProvider provider, Map<String, String> names) {
        names.forEach((id, name) -> provider.add("message." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, name));
    }

    private static void addNpcTranslations(LanguageProvider provider, boolean chinese) {
        Map<String, String> names = chinese ? Map.of(
                "goron", "大哥隆", "npc/barnes", "巴恩斯", "npc/mask_trader", "面具商人",
                "npc/orca", "奥卡", "npc/zelda", "塞尔达公主")
                : Map.of("goron", "Biggoron", "npc/barnes", "Barnes", "npc/mask_trader", "Happy Mask Salesman",
                "npc/orca", "Orca", "npc/zelda", "Princess Zelda");
        names.forEach((id, name) -> provider.add("entity." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, name));
        if (chinese) {
            provider.add("item.zeldaswordskills_remastered.goron_spawn_egg", "大哥隆刷怪蛋");
            provider.add("item.zeldaswordskills_remastered.barnes_spawn_egg", "巴恩斯刷怪蛋");
            provider.add("item.zeldaswordskills_remastered.mask_trader_spawn_egg", "面具商人刷怪蛋");
            provider.add("item.zeldaswordskills_remastered.orca_spawn_egg", "奥卡刷怪蛋");
            provider.add("item.zeldaswordskills_remastered.zelda_spawn_egg", "塞尔达公主刷怪蛋");
        }
    }

    private static void addCreatureTranslations(LanguageProvider provider, boolean chinese) {
        Map<String, String> names = chinese ? Map.ofEntries(
                Map.entry("darknut", "黑甲武士"), Map.entry("darknut_mighty", "强力黑甲武士"),
                Map.entry("fairy", "妖精"), Map.entry("navi", "娜薇"),
                Map.entry("chu", "红色丘丘"), Map.entry("chu_green", "绿色丘丘"),
                Map.entry("chu_blue", "蓝色丘丘"), Map.entry("chu_yellow", "黄色丘丘"),
                Map.entry("baba_deku", "德库巴巴"), Map.entry("baba_fire", "火焰德库巴巴"),
                Map.entry("baba_withered", "枯萎德库巴巴"), Map.entry("keese", "蝙蝠怪"),
                Map.entry("keese_fire", "火焰蝙蝠怪"), Map.entry("keese_ice", "冰霜蝙蝠怪"),
                Map.entry("keese_thunder", "雷电蝙蝠怪"), Map.entry("keese_cursed", "诅咒蝙蝠怪"),
                Map.entry("octorok", "八爪投石怪"), Map.entry("octorok_bomb", "炸弹八爪怪"),
                Map.entry("skulltula", "骷髅蜘蛛"), Map.entry("skulltula_gold", "黄金骷髅蜘蛛"),
                Map.entry("wizzrobe", "火焰巫师袍怪"), Map.entry("wizzrobe_ice", "冰霜巫师袍怪"),
                Map.entry("wizzrobe_lightning", "雷电巫师袍怪"), Map.entry("wizzrobe_wind", "疾风巫师袍怪"),
                Map.entry("darknut_boss", "黑甲武士首领"),
                Map.entry("wizzrobe_grand", "大巫师袍怪"), Map.entry("desert_boss", "沙之神殿尸壳"),
                Map.entry("fire_boss", "火之神殿元素巫师"), Map.entry("forest_boss", "森林神殿黄金骷髅蜘蛛"), Map.entry("ice_boss", "冰霜神殿幻术师"), Map.entry("water_boss", "水之神殿炸弹八爪怪"))
                : Map.ofEntries(
                Map.entry("darknut", "Darknut"), Map.entry("darknut_mighty", "Mighty Darknut"),
                Map.entry("fairy", "Fairy"), Map.entry("navi", "Navi"),
                Map.entry("chu", "Red Chu"), Map.entry("chu_green", "Green Chu"),
                Map.entry("chu_blue", "Blue Chu"), Map.entry("chu_yellow", "Yellow Chu"),
                Map.entry("baba_deku", "Deku Baba"), Map.entry("baba_fire", "Fire Deku Baba"),
                Map.entry("baba_withered", "Withered Deku Baba"), Map.entry("keese", "Keese"),
                Map.entry("keese_fire", "Fire Keese"), Map.entry("keese_ice", "Ice Keese"),
                Map.entry("keese_thunder", "Thunder Keese"), Map.entry("keese_cursed", "Cursed Keese"),
                Map.entry("octorok", "Octorok"), Map.entry("octorok_bomb", "Bomb Octorok"),
                Map.entry("skulltula", "Skulltula"), Map.entry("skulltula_gold", "Gold Skulltula"),
                Map.entry("wizzrobe", "Fire Wizzrobe"), Map.entry("wizzrobe_ice", "Ice Wizzrobe"),
                Map.entry("wizzrobe_lightning", "Lightning Wizzrobe"), Map.entry("wizzrobe_wind", "Wind Wizzrobe"),
                Map.entry("darknut_boss", "Darknut Boss"),
                Map.entry("wizzrobe_grand", "Grand Wizzrobe"), Map.entry("desert_boss", "Desert Temple Husk"),
                Map.entry("fire_boss", "Fire Temple Elemental Wizzrobe"), Map.entry("forest_boss", "Forest Temple Golden Skulltula"), Map.entry("ice_boss", "Ice Temple Illusioner"), Map.entry("water_boss", "Water Temple Bomb Octorok"));
        names.forEach((id, name) -> provider.add("entity." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, name));
        if (chinese) {
            names.forEach((id, name) -> {
                if (!id.equals("desert_boss") && !id.equals("fire_boss") && !id.equals("forest_boss") && !id.equals("ice_boss") && !id.equals("water_boss")) {
                    provider.add("item." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id + "_spawn_egg", name + "刷怪蛋");
                }
            });
        }
    }

    private static void addQuestTranslations(LanguageProvider provider, boolean chinese) {
        Map<String, String> messages = chinese ? Map.ofEntries(
                Map.entry("zelda_talk.start", "塞尔达：如果你有妖精笛，请让我看看。"),
                Map.entry("zelda_talk.accepted", "塞尔达接过妖精笛，开始为它注入古老的旋律……"),
                Map.entry("zelda_talk.performing", "塞尔达仍在演奏，请稍候。"),
                Map.entry("zelda_talk.complete", "妖精笛已归还。塞尔达请你依次寻找智慧、勇气与力量吊坠。"),
                Map.entry("zelda_letter.complete", "塞尔达交给你一封信，请把它带给一位成年村民。"),
                Map.entry("pendants.wrong_order", "塞尔达：吊坠的顺序是智慧、勇气、力量。"),
                Map.entry("pendants.delivered", "塞尔达收下了吊坠。"),
                Map.entry("pendants.complete", "三枚吊坠齐聚！你获得了森林神殿首领钥匙。"),
                Map.entry("master_sword.pendants_returned", "塞尔达归还了三枚吊坠，并请你展示大师之剑。"),
                Map.entry("master_sword.complete", "塞尔达认可了大师之剑，并将时之笛交给你。"),
                Map.entry("light_arrows.complete", "你的勇者之弓获得了八支光之箭。"),
                Map.entry("song.time.learned", "塞尔达教会了你时间之歌。"),
                Map.entry("song.time.known", "塞尔达：你已经掌握时间之歌。"),
                Map.entry("zelda.story", "塞尔达：海拉鲁的命运仍需要你的勇气。"),
                Map.entry("mask_shop.start", "面具商人：请从塞尔达那里取得介绍信，再找一位成年村民开设店铺。"),
                Map.entry("mask_shop.letter", "面具商人：先把塞尔达的信交给一位成年村民。"),
                Map.entry("mask_shop.complete", "新的快乐面具店开张了，面具销售任务已经开始。"),
                Map.entry("mask_sales.borrowed", "面具商人借给你下一副面具。把它卖给一位村民。"),
                Map.entry("mask_sales.sold", "村民买下了面具；现在把货款交回面具商人。"),
                Map.entry("mask_sales.payment_needed", "面具商人：这次需要交回 %s 枚绿宝石。"),
                Map.entry("mask_sales.paid", "货款结清。再次交谈即可领取下一副面具。"),
                Map.entry("mask_sales.sell_first", "面具商人：先把借出的面具卖掉。"),
                Map.entry("mask_sales.complete", "六副面具的生意全部完成！你获得了真实之面。"),
                Map.entry("mask_sales.complete_story", "面具商人：愿真实之面让你看清隐藏的心意。"),
                Map.entry("song.healing.learned", "面具商人教会了你治愈之歌。"),
                Map.entry("song.healing.known", "面具商人：你已经懂得治愈之歌。"),
                Map.entry("biggoron.hint", "大哥隆交易需要用主手持有正确物品，并左键点击对应的命名 NPC。"),
                Map.entry("biggoron.wrong_stage", "这项交换还没有轮到。"),
                Map.entry("biggoron.not_ready", "大哥隆之剑还没有锻造完成。"),
                Map.entry("biggoron.trade", "交换完成，交易链进入下一阶段。"),
                Map.entry("biggoron.wait", "收据已签发；等待两个游戏日（48000 tick）后再来。"),
                Map.entry("biggoron.complete", "大哥隆之剑锻造完成！"),
                Map.entry("biggoron.complete_story", "大哥隆：这把剑会证明海拉鲁最伟大的锻造技艺！"),
                Map.entry("zelda.converted", "村民显露出塞尔达公主的真正身份。"),
                Map.entry("barnes.story", "巴恩斯：标准炸弹八枚绿宝石；给我鱼和岩浆膏还能扩充货品。"),
                Map.entry("barnes.open", "巴恩斯的炸弹店开张了。"),
                Map.entry("barnes.water_unlocked", "巴恩斯研究了鱼，解锁了水炸弹交易。"),
                Map.entry("barnes.water_known", "巴恩斯：水炸弹已经在货架上了。"),
                Map.entry("barnes.fire_unlocked", "巴恩斯研究了岩浆膏，解锁了火焰炸弹交易。"),
                Map.entry("barnes.fire_known", "巴恩斯：火焰炸弹已经在货架上了。"),
                Map.entry("orca.story", "奥卡：先掌握回旋斩，再带骑士纹章来接受训练。"),
                Map.entry("orca.unfit", "奥卡：先掌握我上一阶段教授的剑技。"),
                Map.entry("orca.begin", "奥卡收下第一枚骑士纹章，百枚纹章的训练开始了。"),
                Map.entry("orca.redeem", "奥卡收下骑士纹章：%s／100。"),
                Map.entry("orca.flash_assault", "奥卡收下第 %s 枚纹章，并提升了你的闪袭等级。"),
                Map.entry("orca.super_spin", "奥卡收下第 %s 枚纹章，并提升了你的超级回旋斩等级。"),
                Map.entry("orca.continuous_flash", "奥卡收下第 %s 枚纹章，并提升了你的连续冲刺斩等级。"),
                Map.entry("orca.choose_pending", "奥卡：请先在聊天栏选择要学习的技能。"),
                Map.entry("orca.skill_maxed", "奥卡：这个技能已经达到最高等级。"),
                Map.entry("orca.master", "奥卡：百枚骑士纹章的训练已经全部完成。"),
                Map.entry("cursed_man.story", "诅咒之人：黄金骷髅蜘蛛徽记可以解除我们家族的诅咒。"),
                Map.entry("cursed_man.amount", "你已累计收集 %s 枚骷髅徽章，下一份奖励需要累计收集 %s 枚。"),
                Map.entry("cursed_man.reward", "诅咒之人：这是累计收集 %s 枚骷髅徽章的奖励。"),
                Map.entry("darunia.weak", "达鲁尼亚：这首莎莉亚之歌的力量还不够。"),
                Map.entry("darunia.complete", "莎莉亚之歌治愈了达鲁尼亚；他赠予你银手套。"),
                Map.entry("darunia.thanks", "达鲁尼亚：谢谢你再次演奏莎莉亚之歌！"))
                : Map.ofEntries(
                Map.entry("zelda_talk.start", "Zelda: If you have a Fairy Ocarina, please show it to me."),
                Map.entry("zelda_talk.accepted", "Zelda takes the Fairy Ocarina and begins weaving an ancient melody into it…"),
                Map.entry("zelda_talk.performing", "Zelda is still performing. Please wait."),
                Map.entry("zelda_talk.complete", "Your Fairy Ocarina was returned. Seek Wisdom, Courage, then Power."),
                Map.entry("zelda_letter.complete", "Zelda gives you a letter. Deliver it to an adult villager."),
                Map.entry("pendants.wrong_order", "Zelda: Present the pendants in the order Wisdom, Courage, Power."),
                Map.entry("pendants.delivered", "Zelda accepts the pendant."),
                Map.entry("pendants.complete", "All three pendants are gathered! You received the Forest Boss Key."),
                Map.entry("master_sword.pendants_returned", "Zelda returns the pendants and asks to see the Master Sword."),
                Map.entry("master_sword.complete", "Zelda recognizes the Master Sword and entrusts the Ocarina of Time to you."),
                Map.entry("light_arrows.complete", "Your Hero Bow is blessed with eight Light Arrows."),
                Map.entry("song.time.learned", "Zelda taught you the Song of Time."),
                Map.entry("song.time.known", "Zelda: You already know the Song of Time."),
                Map.entry("zelda.story", "Zelda: Hyrule's fate still depends on your courage."),
                Map.entry("mask_shop.start", "Mask Trader: Obtain Zelda's letter, then recruit an adult villager for the shop."),
                Map.entry("mask_shop.letter", "Mask Trader: Give Zelda's letter to an adult villager first."),
                Map.entry("mask_shop.complete", "A new Happy Mask Shop has opened. The mask sales quest begins."),
                Map.entry("mask_sales.borrowed", "The trader lends you the next mask. Sell it to a villager."),
                Map.entry("mask_sales.sold", "The villager bought the mask. Return the payment to the trader."),
                Map.entry("mask_sales.payment_needed", "Mask Trader: This payment is %s emeralds."),
                Map.entry("mask_sales.paid", "Payment settled. Speak again to borrow the next mask."),
                Map.entry("mask_sales.sell_first", "Mask Trader: Sell the borrowed mask first."),
                Map.entry("mask_sales.complete", "All six sales are complete! You received the Mask of Truth."),
                Map.entry("mask_sales.complete_story", "Mask Trader: May the Mask of Truth reveal hidden hearts."),
                Map.entry("song.healing.learned", "The Mask Trader taught you the Song of Healing."),
                Map.entry("song.healing.known", "Mask Trader: You already know the Song of Healing."),
                Map.entry("biggoron.hint", "Hold the correct trade item in your main hand and left-click the matching named NPC."),
                Map.entry("biggoron.wrong_stage", "That exchange is not available yet."),
                Map.entry("biggoron.not_ready", "The Biggoron Sword is not finished yet."),
                Map.entry("biggoron.trade", "Trade complete. The exchange chain advances."),
                Map.entry("biggoron.wait", "Claim Check issued. Return after two game days (48,000 ticks)."),
                Map.entry("biggoron.complete", "The Biggoron Sword is complete!"),
                Map.entry("biggoron.complete_story", "Biggoron: This blade proves Hyrule's finest craftsmanship!"),
                Map.entry("zelda.converted", "The villager reveals her true identity as Princess Zelda."),
                Map.entry("barnes.story", "Barnes: Standard bombs cost eight emeralds. Fish and magma cream expand my stock."),
                Map.entry("barnes.open", "Barnes's Bomb Shop is now open."),
                Map.entry("barnes.water_unlocked", "Barnes studies the fish and unlocks Water Bomb trades."),
                Map.entry("barnes.water_known", "Barnes: Water Bombs are already in stock."),
                Map.entry("barnes.fire_unlocked", "Barnes studies the magma cream and unlocks Fire Bomb trades."),
                Map.entry("barnes.fire_known", "Barnes: Fire Bombs are already in stock."),
                Map.entry("orca.story", "Orca: Learn Spin Attack, then bring Knight's Crests for training."),
                Map.entry("orca.unfit", "Orca: Master the technique from my previous lesson first."),
                Map.entry("orca.begin", "Orca accepts the first Knight's Crest. The hundred-crest training begins."),
                Map.entry("orca.redeem", "Orca accepts Knight's Crest %s/100."),
                Map.entry("orca.flash_assault", "At crest %s, Orca raises your Flash Assault level."),
                Map.entry("orca.super_spin", "At crest %s, Orca raises your Super Spin Attack level."),
                Map.entry("orca.continuous_flash", "At crest %s, Orca raises your Continuous Dash level."),
                Map.entry("orca.choose_pending", "Orca: Choose the skill in chat first."),
                Map.entry("orca.skill_maxed", "Orca: That skill is already at its maximum level."),
                Map.entry("orca.master", "Orca: The hundred-crest training is complete."),
                Map.entry("cursed_man.story", "Cursed Man: Gold Skulltula Tokens can break my family's curse."),
                Map.entry("cursed_man.amount", "You have collected %s Skulltula Tokens. The next reward requires %s collected tokens."),
                Map.entry("cursed_man.reward", "Cursed Man: Here is your reward for collecting %s Skulltula Tokens."),
                Map.entry("darunia.weak", "Darunia: This Song of Saria is not powerful enough."),
                Map.entry("darunia.complete", "The Song of Saria cures Darunia; he rewards you with Silver Gauntlets."),
                Map.entry("darunia.thanks", "Darunia: Thank you for playing Saria's Song again!"));
        messages.forEach((id, message) -> provider.add("quest." + ZeldaSwordSkills_Remastered.MOD_ID + "." + id, message));
    }

    private static void addGossipHints(LanguageProvider provider, boolean chinese) {
        String[] hints = chinese ? new String[]{
                "试着对带电的敌人使用回旋镖或钩爪。", "野生妖精会在阳光下消失。", "铁甲武士背后的护甲最薄弱。",
                "先破坏强大铁甲武士的披风，再击败它。", "用近战武器攻击带电的敌人可不是好主意。", "随身携带妖精瓶，可以在危急时刻救命。",
                "魔法师在施法时最容易受到攻击。", "试着让不同的物品接触圣火。", "许多怪物会忽略戴着恐怖面具的人。",
                "风车里的古鲁古鲁喜欢暴风雨。", "塞尔达摇篮曲能够揭示隐藏的事物。", "留意虚空粒子，那里可能藏着什么！"
        } : new String[]{
                "Try using boomerangs or hookshots on electrified enemies.", "Wild fairies fade away in sunlight.", "Darknuts' armor is weakest in the back.",
                "Mighty Darknuts cannot be defeated until their cape is destroyed.", "Striking an electrified enemy with a melee weapon is a bad idea.", "A Fairy Bottle on one's belt is the best insurance policy.",
                "Wizzrobes are most vulnerable while casting a spell.", "Try swinging different items through the Sacred Flames.", "Many mobs will ignore players wearing a Spooky Mask.",
                "Guru-Guru down at the windmill enjoys a good storm.", "Zelda's Lullaby can reveal hidden things.", "Keep an eye out for void particles - something might be there!"
        };
        for(int index=0;index<hints.length;index++) provider.add("message.zeldaswordskills_remastered.gossip.hint."+index,hints[index]);
    }

    private static final class BlockLootTables extends BlockLootSubProvider {
        private BlockLootTables() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            ZSSRegistries.BLOCKS.getEntries().stream().map(RegistryObject::get)
                    .filter(block -> block != ZSSRegistries.NAVI_LIGHT.get()).forEach(this::dropSelf);
            add(ZSSRegistries.CERAMIC_JAR.get(), noDrop());
            add(ZSSRegistries.SECRET_ROOM_CORE.get(), noDrop());
            ZSSRegistries.WARP_STONES.forEach(block -> add(block.get(), noDrop()));
            ZSSRegistries.SACRED_FLAMES.forEach(block -> add(block.get(), noDrop()));
            add(ZSSRegistries.ANCIENT_TABLET_BOMBOS.get(), noDrop());
            add(ZSSRegistries.ANCIENT_TABLET_ETHER.get(), noDrop());
            add(ZSSRegistries.ANCIENT_TABLET_QUAKE.get(), noDrop());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ZSSRegistries.BLOCKS.getEntries().stream().map(RegistryObject::get).toList();
        }
    }
}
