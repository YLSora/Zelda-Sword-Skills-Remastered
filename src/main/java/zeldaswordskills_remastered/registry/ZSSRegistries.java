package zeldaswordskills_remastered.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.*;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.block.entity.PedestalBlockEntity;
import zeldaswordskills_remastered.effect.StunEffect;
import zeldaswordskills_remastered.entity.projectile.ThrownRock;
import zeldaswordskills_remastered.entity.projectile.ThrownBomb;
import zeldaswordskills_remastered.entity.projectile.SwordBeam;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.entity.projectile.ZeldaArrow;
import zeldaswordskills_remastered.entity.npc.QuestNpc;
import zeldaswordskills_remastered.entity.LegacyCreature;
import zeldaswordskills_remastered.entity.ChuCreature;
import zeldaswordskills_remastered.entity.DarknutCreature;
import zeldaswordskills_remastered.entity.DesertBossCreature;
import zeldaswordskills_remastered.entity.DekuCreature;
import zeldaswordskills_remastered.entity.FairyCreature;
import zeldaswordskills_remastered.entity.KeeseCreature;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.entity.OctorokCreature;
import zeldaswordskills_remastered.entity.SkulltulaCreature;
import zeldaswordskills_remastered.entity.WizzrobeCreature;
import zeldaswordskills_remastered.item.*;
import zeldaswordskills_remastered.menu.PedestalMenu;
import zeldaswordskills_remastered.recipe.ThrowingRockRecipe;
import zeldaswordskills_remastered.recipe.HeartPieceRecipe;
import zeldaswordskills_remastered.recipe.HookshotUpgradeRecipe;
import zeldaswordskills_remastered.recipe.CombineBombBagRecipe;
import zeldaswordskills_remastered.recipe.BagToBombArrowRecipe;
import zeldaswordskills_remastered.recipe.TunicDyeRecipe;
import zeldaswordskills_remastered.loot.AddTableLootModifier;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ZSSRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZeldaSwordSkills_Remastered.MOD_ID);

    private static final Map<String, RegistryObject<Item>> ITEM_BY_ID = new LinkedHashMap<>();
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, ZeldaSwordSkills_Remastered.MOD_ID);
    public static final RegistryObject<zeldaswordskills_remastered.worldgen.SmallWorldFeature> JAR_FEATURE = FEATURES.register("jars", () -> new zeldaswordskills_remastered.worldgen.SmallWorldFeature(zeldaswordskills_remastered.worldgen.SmallWorldFeature.Kind.JARS));
    public static final RegistryObject<zeldaswordskills_remastered.worldgen.SmallWorldFeature> BOMB_FLOWER_FEATURE = FEATURES.register("bomb_flowers", () -> new zeldaswordskills_remastered.worldgen.SmallWorldFeature(zeldaswordskills_remastered.worldgen.SmallWorldFeature.Kind.BOMB_FLOWERS));
    public static final RegistryObject<zeldaswordskills_remastered.worldgen.SmallWorldFeature> GOSSIP_FEATURE = FEATURES.register("gossip_stone", () -> new zeldaswordskills_remastered.worldgen.SmallWorldFeature(zeldaswordskills_remastered.worldgen.SmallWorldFeature.Kind.GOSSIP_STONE));
    public static final RegistryObject<zeldaswordskills_remastered.worldgen.SmallWorldFeature> PILLAR_FEATURE = FEATURES.register("song_pillar", () -> new zeldaswordskills_remastered.worldgen.SmallWorldFeature(zeldaswordskills_remastered.worldgen.SmallWorldFeature.Kind.SONG_PILLAR));
    public static final RegistryObject<net.minecraft.world.level.levelgen.structure.StructureType<zeldaswordskills_remastered.worldgen.SecretRoomStructure>> SECRET_ROOM_STRUCTURE = STRUCTURE_TYPES.register("secret_room", () -> () -> zeldaswordskills_remastered.worldgen.SecretRoomStructure.CODEC);
    public static final RegistryObject<net.minecraft.world.level.levelgen.structure.StructureType<zeldaswordskills_remastered.worldgen.TempleStructure>> TEMPLE_STRUCTURE = STRUCTURE_TYPES.register("temple", () -> () -> zeldaswordskills_remastered.worldgen.TempleStructure.CODEC);
    public static final RegistryObject<net.minecraft.world.level.levelgen.structure.StructureType<zeldaswordskills_remastered.worldgen.EndTempleStructure>> END_TEMPLE_STRUCTURE = STRUCTURE_TYPES.register("end_temple", () -> () -> zeldaswordskills_remastered.worldgen.EndTempleStructure.CODEC);
    public static final RegistryObject<net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType> SECRET_ROOM_PIECE = STRUCTURE_PIECES.register("secret_room", () -> zeldaswordskills_remastered.worldgen.SecretRoomPiece::new);
    public static final RegistryObject<SecretRoomCoreBlock> SECRET_ROOM_CORE = BLOCKS.register("secret_room_core", () -> new SecretRoomCoreBlock(BlockBehaviour.Properties.of().strength(-1,3600000)));
    public static final RegistryObject<NaviLightBlock> NAVI_LIGHT = BLOCKS.register("navi_light", NaviLightBlock::new);
    public static final RegistryObject<BlockEntityType<zeldaswordskills_remastered.block.entity.SecretRoomCore>> SECRET_ROOM_CORE_ENTITY = BLOCK_ENTITY_TYPES.register("secret_room_core",
            () -> BlockEntityType.Builder.of(zeldaswordskills_remastered.block.entity.SecretRoomCore::new,SECRET_ROOM_CORE.get()).build(null));

    public static final RegistryObject<Item> MASTER_ORE = item("master_ore", () -> new MasterOreItem(new Item.Properties()));
    public static final RegistryObject<Item> PENDANT_POWER = item("pendant_power", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PENDANT_WISDOM = item("pendant_wisdom", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PENDANT_COURAGE = item("pendant_courage", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> THROWING_ROCK = item("throwing_rock", () -> new ThrowingRockItem(new Item.Properties().stacksTo(18)));
    public static final RegistryObject<Item> DEKU_SHIELD = item("deku_shield", () -> new ZeldaCombatItems.Shield(false, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HYLIAN_SHIELD = item("hylian_shield", () -> new ZeldaCombatItems.Shield(false, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MIRROR_SHIELD = item("mirror_shield", () -> new ZeldaCombatItems.Shield(true, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BROKEN_SWORD = sword("broken_sword", Tiers.WOOD, 0, -2.4F, false, false);
    public static final RegistryObject<Item> KOKIRI_SWORD = sword("kokiri_sword", Tiers.WOOD, 3, -2.4F, false, false);
    // Stage 16.5 weapon retune. Vanilla totals are "1 + tier bonus + modifier" for attack damage
    // and "4.0 + modifier" for attack speed, with tier bonuses WOOD 0 / STONE 1 / IRON 2 /
    // DIAMOND 3 / NETHERITE 4. Every modifier below is chosen to land on the published totals;
    // weapons the task did not name keep their previous values, including attack speed.
    // Ordon 10, Giant 20, Biggoron 32 @ 0.9, Master 15, Tempered 20, Golden 25, True Master 30,
    // Darknut 10.
    public static final RegistryObject<Item> ORDON_SWORD = sword("ordon_sword", Tiers.STONE, 8, -2.4F, false, false);
    public static final RegistryObject<Item> GIANT_SWORD = sword("giant_sword", Tiers.IRON, 17, -3.0F, true, false);
    public static final RegistryObject<Item> BIGGORON_SWORD = sword("biggoron_sword", Tiers.DIAMOND, 28, -3.1F, true, false);
    public static final RegistryObject<Item> MASTER_SWORD = sword("master_sword", Tiers.DIAMOND, 11, -2.4F, false, true);
    public static final RegistryObject<Item> TEMPERED_SWORD = sword("tempered_sword", Tiers.DIAMOND, 16, -2.4F, false, true);
    public static final RegistryObject<Item> GOLDEN_SWORD = sword("golden_sword", Tiers.NETHERITE, 20, -2.4F, false, true);
    public static final RegistryObject<Item> TRUE_MASTER_SWORD = sword("true_master_sword", Tiers.NETHERITE, 25, -2.4F, false, true);
    public static final RegistryObject<Item> DARKNUT_SWORD = sword("darknut_sword", Tiers.IRON, 7, -2.8F, true, false);
    public static final RegistryObject<Item> FAIRY_OCARINA = item("fairy_ocarina", () -> new InstrumentItem(1, new Item.Properties()));
    public static final RegistryObject<Item> OCARINA_OF_TIME = item("ocarina_of_time", () -> new InstrumentItem(5, new Item.Properties()));
    public static final RegistryObject<Item> GODDESS_HARP = item("goddess_harp", () -> new InstrumentItem(5, new Item.Properties()));
    public static final RegistryObject<Item> NAVI_BOTTLE = item("navi_bottle", () -> new SpecialItems.NaviBottle(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<MechanismBlocks.Beam> BEAM_WOODEN = BLOCKS.register("beam_wooden", () -> new MechanismBlocks.Beam(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(2, 5).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<BombFlowerBlock> BOMB_FLOWER = BLOCKS.register("bomb_flower", () -> new BombFlowerBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT).randomTicks().instabreak().sound(SoundType.GRASS).noOcclusion()));
    public static final RegistryObject<CeramicJarBlock> CERAMIC_JAR = BLOCKS.register("ceramic_jar", () -> new CeramicJarBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_ORANGE).strength(0, 1200).sound(SoundType.DECORATED_POT).noOcclusion()));
    public static final RegistryObject<LockedChestBlock> CHEST_INVISIBLE = BLOCKS.register("chest_invisible", () -> new LockedChestBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(-1, 3600000).sound(SoundType.WOOD).noOcclusion(), false));
    public static final RegistryObject<LockedChestBlock> CHEST_LOCKED = BLOCKS.register("chest_locked", () -> new LockedChestBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(-1, 3600000).sound(SoundType.WOOD).noOcclusion(), true));
    public static final RegistryObject<LockedDoorBlock> DOOR_BOSS_DESERT = bossDoor("door_boss_desert", DungeonType.DESERT);
    public static final RegistryObject<LockedDoorBlock> DOOR_BOSS_EARTH = bossDoor("door_boss_earth", DungeonType.EARTH);
    public static final RegistryObject<LockedDoorBlock> DOOR_BOSS_FIRE = bossDoor("door_boss_fire", DungeonType.FIRE);
    public static final RegistryObject<LockedDoorBlock> DOOR_BOSS_FOREST = bossDoor("door_boss_forest", DungeonType.FOREST);
    public static final RegistryObject<LockedDoorBlock> DOOR_BOSS_ICE = bossDoor("door_boss_ice", DungeonType.ICE);
    public static final RegistryObject<LockedDoorBlock> DOOR_BOSS_WATER = bossDoor("door_boss_water", DungeonType.WATER);
    public static final RegistryObject<LockedDoorBlock> DOOR_BOSS_END = bossDoor("door_boss_end", DungeonType.END);
    public static final RegistryObject<LockedDoorBlock> DOOR_LOCKED = BLOCKS.register("door_locked", () -> new LockedDoorBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(-1, 3600000).sound(SoundType.METAL).noOcclusion(), null));
    public static final RegistryObject<DungeonBlocks.Core> DUNGEON_CORE_STONE = BLOCKS.register("dungeon_core_stone", () -> new DungeonBlocks.Core(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(1.5F, 3600000).sound(SoundType.STONE)));
    public static final RegistryObject<DungeonBlocks.Core> DUNGEON_CORE_SANDSTONE = BLOCKS.register("dungeon_core_sandstone", () -> new DungeonBlocks.Core(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND).strength(1.5F, 3600000).sound(SoundType.STONE)));
    public static final RegistryObject<DungeonBlocks.Stone> DUNGEON_STONE_STONE = BLOCKS.register("dungeon_stone_stone", () -> new DungeonBlocks.Stone(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE)));
    public static final RegistryObject<DungeonBlocks.Stone> DUNGEON_STONE_SANDSTONE = BLOCKS.register("dungeon_stone_sandstone", () -> new DungeonBlocks.Stone(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND).strength(-1, 3600000).sound(SoundType.STONE)));
    public static final RegistryObject<GossipStoneBlock> GOSSIP_STONE = BLOCKS.register("gossip_stone", () -> new GossipStoneBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(10, 3600000).sound(SoundType.STONE).noOcclusion()));
    public static final RegistryObject<MechanismBlocks.Heavy> LIGHT_BLOCK = BLOCKS.register("light_block", () -> new MechanismBlocks.Heavy(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE), ZSSBlockInteractions.Weight.MEDIUM));
    public static final RegistryObject<MechanismBlocks.Heavy> HEAVY_BLOCK = BLOCKS.register("heavy_block", () -> new MechanismBlocks.Heavy(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE), ZSSBlockInteractions.Weight.VERY_HEAVY));
    public static final RegistryObject<MechanismBlocks.HookTarget> HOOK_TARGET = BLOCKS.register("hook_target", () -> new MechanismBlocks.HookTarget(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(5, 15).sound(SoundType.METAL), false));
    public static final RegistryObject<MechanismBlocks.HookTarget> HOOK_TARGET_ALL = BLOCKS.register("hook_target_all", () -> new MechanismBlocks.HookTarget(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(5, 15).sound(SoundType.METAL), true));
    public static final RegistryObject<InscriptionBlock> INSCRIPTION = BLOCKS.register("inscription", () -> new InscriptionBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(50, 2000).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<MechanismBlocks.GiantLever> LEVER_GIANT = BLOCKS.register("lever_giant", () -> new MechanismBlocks.GiantLever(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(1, 5).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<MechanismBlocks.Peg> PEG_WOODEN = BLOCKS.register("peg_wooden", () -> new MechanismBlocks.Peg(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(-1, 3600000).sound(SoundType.WOOD).noOcclusion(), ZSSBlockInteractions.Weight.VERY_LIGHT));
    public static final RegistryObject<MechanismBlocks.Peg> PEG_RUSTY = BLOCKS.register("peg_rusty", () -> new MechanismBlocks.Peg(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(-1, 3600000).sound(SoundType.METAL).noOcclusion(), ZSSBlockInteractions.Weight.MEDIUM));
    public static final RegistryObject<PedestalBlock> PEDESTAL = BLOCKS.register("pedestal", () -> new PedestalBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).lightLevel(state -> 8).sound(SoundType.STONE).noOcclusion()));
    public static final RegistryObject<MechanismBlocks.QuakeStone> QUAKE_STONE = BLOCKS.register("quake_stone", () -> new MechanismBlocks.QuakeStone(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE)));
    public static final RegistryObject<MechanismBlocks.QuakeStone> QUAKE_STONE_MOSSY = BLOCKS.register("quake_stone_mossy", () -> new MechanismBlocks.QuakeStone(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN).strength(-1, 3600000).sound(SoundType.STONE)));
    public static final RegistryObject<SacredFlameBlock> SACRED_FLAME_DIN = sacredFlame("sacred_flame_din", SacredFlameBlock.FlameType.DIN);
    public static final RegistryObject<SacredFlameBlock> SACRED_FLAME_FARORE = sacredFlame("sacred_flame_farore", SacredFlameBlock.FlameType.FARORE);
    public static final RegistryObject<SacredFlameBlock> SACRED_FLAME_NAYRU = sacredFlame("sacred_flame_nayru", SacredFlameBlock.FlameType.NAYRU);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_STONE = secretStone("secret_stone_stone", MechanismBlocks.SecretVariant.STONE);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_SANDSTONE = secretStone("secret_stone_sandstone", MechanismBlocks.SecretVariant.SANDSTONE);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_NETHER_BRICKS = secretStone("secret_stone_nether_bricks", MechanismBlocks.SecretVariant.NETHER_BRICKS);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_STONE_BRICKS = secretStone("secret_stone_stone_bricks", MechanismBlocks.SecretVariant.STONE_BRICKS);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_MOSSY_COBBLESTONE = secretStone("secret_stone_mossy_cobblestone", MechanismBlocks.SecretVariant.MOSSY_COBBLESTONE);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_ICE = secretStone("secret_stone_ice", MechanismBlocks.SecretVariant.ICE);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_COBBLESTONE = secretStone("secret_stone_cobblestone", MechanismBlocks.SecretVariant.COBBLESTONE);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_END_STONE = secretStone("secret_stone_end_stone", MechanismBlocks.SecretVariant.END_STONE);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_NETHER_WART_BLOCK = secretStone("secret_stone_nether_wart_block", MechanismBlocks.SecretVariant.NETHER_WART_BLOCK);
    public static final RegistryObject<MechanismBlocks.TimeBlock> TIME_BLOCK = BLOCKS.register("time_block", () -> new MechanismBlocks.TimeBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE).noOcclusion(), ZSSContentIds.TIME));
    public static final RegistryObject<MechanismBlocks.TimeBlock> ROYAL_BLOCK = BLOCKS.register("royal_block", () -> new MechanismBlocks.TimeBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).strength(-1, 3600000).sound(SoundType.STONE).noOcclusion(), ZSSContentIds.LULLABY));
    public static final RegistryObject<WarpStoneBlock> WARP_STONE_BOLERO = warpStone("warp_stone_bolero", WarpStoneBlock.WarpSong.BOLERO);
    public static final RegistryObject<WarpStoneBlock> WARP_STONE_MINUET = warpStone("warp_stone_minuet", WarpStoneBlock.WarpSong.MINUET);
    public static final RegistryObject<WarpStoneBlock> WARP_STONE_PRELUDE = warpStone("warp_stone_prelude", WarpStoneBlock.WarpSong.PRELUDE);
    public static final RegistryObject<WarpStoneBlock> WARP_STONE_OATH = warpStone("warp_stone_oath", WarpStoneBlock.WarpSong.OATH);
    public static final RegistryObject<WarpStoneBlock> WARP_STONE_NOCTURNE = warpStone("warp_stone_nocturne", WarpStoneBlock.WarpSong.NOCTURNE);
    public static final RegistryObject<WarpStoneBlock> WARP_STONE_REQUIEM = warpStone("warp_stone_requiem", WarpStoneBlock.WarpSong.REQUIEM);
    public static final RegistryObject<WarpStoneBlock> WARP_STONE_SERENADE = warpStone("warp_stone_serenade", WarpStoneBlock.WarpSong.SERENADE);
    public static final RegistryObject<MechanismBlocks.AncientTablet> ANCIENT_TABLET_BOMBOS = BLOCKS.register("ancient_tablet_bombos", () -> new MechanismBlocks.AncientTablet(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE).noOcclusion(), MechanismBlocks.AncientTablet.Type.BOMBOS));
    public static final RegistryObject<MechanismBlocks.AncientTablet> ANCIENT_TABLET_ETHER = BLOCKS.register("ancient_tablet_ether", () -> new MechanismBlocks.AncientTablet(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE).noOcclusion(), MechanismBlocks.AncientTablet.Type.ETHER));
    public static final RegistryObject<MechanismBlocks.AncientTablet> ANCIENT_TABLET_QUAKE = BLOCKS.register("ancient_tablet_quake", () -> new MechanismBlocks.AncientTablet(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE).noOcclusion(), MechanismBlocks.AncientTablet.Type.QUAKE));

    public static final RegistryObject<Item> BEAM_WOODEN_ITEM = blockItem("beam_wooden", BEAM_WOODEN);
    public static final RegistryObject<Item> BOMB_FLOWER_ITEM = blockItem("bomb_flower", BOMB_FLOWER);
    public static final RegistryObject<Item> CERAMIC_JAR_ITEM = blockItem("ceramic_jar", CERAMIC_JAR);
    public static final RegistryObject<Item> CHEST_INVISIBLE_ITEM = blockItem("chest_invisible", CHEST_INVISIBLE);
    public static final RegistryObject<Item> CHEST_LOCKED_ITEM = blockItem("chest_locked", CHEST_LOCKED);
    public static final RegistryObject<Item> DOOR_BOSS_DESERT_ITEM = blockItem("door_boss_desert", DOOR_BOSS_DESERT);
    public static final RegistryObject<Item> DOOR_BOSS_EARTH_ITEM = blockItem("door_boss_earth", DOOR_BOSS_EARTH);
    public static final RegistryObject<Item> DOOR_BOSS_FIRE_ITEM = blockItem("door_boss_fire", DOOR_BOSS_FIRE);
    public static final RegistryObject<Item> DOOR_BOSS_FOREST_ITEM = blockItem("door_boss_forest", DOOR_BOSS_FOREST);
    public static final RegistryObject<Item> DOOR_BOSS_ICE_ITEM = blockItem("door_boss_ice", DOOR_BOSS_ICE);
    public static final RegistryObject<Item> DOOR_BOSS_WATER_ITEM = blockItem("door_boss_water", DOOR_BOSS_WATER);
    public static final RegistryObject<Item> DOOR_BOSS_END_ITEM = blockItem("door_boss_end", DOOR_BOSS_END);
    public static final RegistryObject<Item> DOOR_LOCKED_ITEM = blockItem("door_locked", DOOR_LOCKED);
    public static final RegistryObject<Item> DUNGEON_CORE_STONE_ITEM = blockItem("dungeon_core_stone", DUNGEON_CORE_STONE);
    public static final RegistryObject<Item> DUNGEON_CORE_SANDSTONE_ITEM = blockItem("dungeon_core_sandstone", DUNGEON_CORE_SANDSTONE);
    public static final RegistryObject<Item> DUNGEON_STONE_STONE_ITEM = blockItem("dungeon_stone_stone", DUNGEON_STONE_STONE);
    public static final RegistryObject<Item> DUNGEON_STONE_SANDSTONE_ITEM = blockItem("dungeon_stone_sandstone", DUNGEON_STONE_SANDSTONE);
    public static final RegistryObject<Item> GOSSIP_STONE_ITEM = blockItem("gossip_stone", GOSSIP_STONE);
    public static final RegistryObject<Item> LIGHT_BLOCK_ITEM = blockItem("light_block", LIGHT_BLOCK);
    public static final RegistryObject<Item> HEAVY_BLOCK_ITEM = blockItem("heavy_block", HEAVY_BLOCK);
    public static final RegistryObject<Item> HOOK_TARGET_ITEM = blockItem("hook_target", HOOK_TARGET);
    public static final RegistryObject<Item> HOOK_TARGET_ALL_ITEM = blockItem("hook_target_all", HOOK_TARGET_ALL);
    public static final RegistryObject<Item> INSCRIPTION_ITEM = blockItem("inscription", INSCRIPTION);
    public static final RegistryObject<Item> LEVER_GIANT_ITEM = blockItem("lever_giant", LEVER_GIANT);
    public static final RegistryObject<Item> PEG_WOODEN_ITEM = blockItem("peg_wooden", PEG_WOODEN);
    public static final RegistryObject<Item> PEG_RUSTY_ITEM = blockItem("peg_rusty", PEG_RUSTY);
    public static final RegistryObject<Item> PEDESTAL_ITEM = item("pedestal",
            () -> new PedestalItem(PEDESTAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> QUAKE_STONE_ITEM = blockItem("quake_stone", QUAKE_STONE);
    public static final RegistryObject<Item> QUAKE_STONE_MOSSY_ITEM = blockItem("quake_stone_mossy", QUAKE_STONE_MOSSY);
    public static final RegistryObject<Item> SACRED_FLAME_DIN_ITEM = blockItem("sacred_flame_din", SACRED_FLAME_DIN);
    public static final RegistryObject<Item> SACRED_FLAME_FARORE_ITEM = blockItem("sacred_flame_farore", SACRED_FLAME_FARORE);
    public static final RegistryObject<Item> SACRED_FLAME_NAYRU_ITEM = blockItem("sacred_flame_nayru", SACRED_FLAME_NAYRU);
    public static final RegistryObject<Item> SECRET_STONE_STONE_ITEM = blockItem("secret_stone_stone", SECRET_STONE_STONE);
    public static final RegistryObject<Item> SECRET_STONE_SANDSTONE_ITEM = blockItem("secret_stone_sandstone", SECRET_STONE_SANDSTONE);
    public static final RegistryObject<Item> SECRET_STONE_NETHER_BRICKS_ITEM = blockItem("secret_stone_nether_bricks", SECRET_STONE_NETHER_BRICKS);
    public static final RegistryObject<Item> SECRET_STONE_STONE_BRICKS_ITEM = blockItem("secret_stone_stone_bricks", SECRET_STONE_STONE_BRICKS);
    public static final RegistryObject<Item> SECRET_STONE_MOSSY_COBBLESTONE_ITEM = blockItem("secret_stone_mossy_cobblestone", SECRET_STONE_MOSSY_COBBLESTONE);
    public static final RegistryObject<Item> SECRET_STONE_ICE_ITEM = blockItem("secret_stone_ice", SECRET_STONE_ICE);
    public static final RegistryObject<Item> SECRET_STONE_COBBLESTONE_ITEM = blockItem("secret_stone_cobblestone", SECRET_STONE_COBBLESTONE);
    public static final RegistryObject<Item> SECRET_STONE_END_STONE_ITEM = blockItem("secret_stone_end_stone", SECRET_STONE_END_STONE);
    public static final RegistryObject<Item> SECRET_STONE_NETHER_WART_BLOCK_ITEM = blockItem("secret_stone_nether_wart_block", SECRET_STONE_NETHER_WART_BLOCK);
    public static final RegistryObject<Item> TIME_BLOCK_ITEM = blockItem("time_block", TIME_BLOCK);
    public static final RegistryObject<Item> ROYAL_BLOCK_ITEM = blockItem("royal_block", ROYAL_BLOCK);
    public static final RegistryObject<Item> WARP_STONE_BOLERO_ITEM = blockItem("warp_stone_bolero", WARP_STONE_BOLERO);
    public static final RegistryObject<Item> WARP_STONE_MINUET_ITEM = blockItem("warp_stone_minuet", WARP_STONE_MINUET);
    public static final RegistryObject<Item> WARP_STONE_PRELUDE_ITEM = blockItem("warp_stone_prelude", WARP_STONE_PRELUDE);
    public static final RegistryObject<Item> WARP_STONE_OATH_ITEM = blockItem("warp_stone_oath", WARP_STONE_OATH);
    public static final RegistryObject<Item> WARP_STONE_NOCTURNE_ITEM = blockItem("warp_stone_nocturne", WARP_STONE_NOCTURNE);
    public static final RegistryObject<Item> WARP_STONE_REQUIEM_ITEM = blockItem("warp_stone_requiem", WARP_STONE_REQUIEM);
    public static final RegistryObject<Item> WARP_STONE_SERENADE_ITEM = blockItem("warp_stone_serenade", WARP_STONE_SERENADE);
    public static final RegistryObject<Item> ANCIENT_TABLET_BOMBOS_ITEM = blockItem("ancient_tablet_bombos", ANCIENT_TABLET_BOMBOS);
    public static final RegistryObject<Item> ANCIENT_TABLET_ETHER_ITEM = blockItem("ancient_tablet_ether", ANCIENT_TABLET_ETHER);
    public static final RegistryObject<Item> ANCIENT_TABLET_QUAKE_ITEM = blockItem("ancient_tablet_quake", ANCIENT_TABLET_QUAKE);

    public static final List<RegistryObject<LockedDoorBlock>> BOSS_DOORS = List.of(
            DOOR_BOSS_DESERT, DOOR_BOSS_EARTH, DOOR_BOSS_FIRE, DOOR_BOSS_FOREST, DOOR_BOSS_ICE, DOOR_BOSS_WATER, DOOR_BOSS_END);
    public static final List<RegistryObject<SacredFlameBlock>> SACRED_FLAMES = List.of(
            SACRED_FLAME_DIN, SACRED_FLAME_FARORE, SACRED_FLAME_NAYRU);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_PURPUR_BLOCK = secretStone("secret_stone_purpur_block", MechanismBlocks.SecretVariant.PURPUR_BLOCK);
    public static final RegistryObject<MechanismBlocks.SecretStone> SECRET_STONE_END_STONE_BRICKS = secretStone("secret_stone_end_stone_bricks", MechanismBlocks.SecretVariant.END_STONE_BRICKS);
    public static final RegistryObject<Item> SECRET_STONE_PURPUR_BLOCK_ITEM = blockItem("secret_stone_purpur_block", SECRET_STONE_PURPUR_BLOCK);
    public static final RegistryObject<Item> SECRET_STONE_END_STONE_BRICKS_ITEM = blockItem("secret_stone_end_stone_bricks", SECRET_STONE_END_STONE_BRICKS);
    public static final List<RegistryObject<MechanismBlocks.SecretStone>> SECRET_STONES = List.of(
            SECRET_STONE_STONE, SECRET_STONE_SANDSTONE, SECRET_STONE_NETHER_BRICKS, SECRET_STONE_STONE_BRICKS,
            SECRET_STONE_MOSSY_COBBLESTONE, SECRET_STONE_ICE, SECRET_STONE_COBBLESTONE, SECRET_STONE_END_STONE, SECRET_STONE_NETHER_WART_BLOCK,
            SECRET_STONE_PURPUR_BLOCK, SECRET_STONE_END_STONE_BRICKS);
    public static final List<RegistryObject<WarpStoneBlock>> WARP_STONES = List.of(
            WARP_STONE_BOLERO, WARP_STONE_MINUET, WARP_STONE_PRELUDE, WARP_STONE_OATH,
            WARP_STONE_NOCTURNE, WARP_STONE_REQUIEM, WARP_STONE_SERENADE);

    public static final RegistryObject<BlockEntityType<PedestalBlockEntity>> PEDESTAL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("pedestal",
            () -> BlockEntityType.Builder.of(PedestalBlockEntity::new, PEDESTAL.get()).build(null));
    public static final RegistryObject<BlockEntityType<StageNineBlockEntities.Storage>> STORAGE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("storage",
            () -> BlockEntityType.Builder.of(StageNineBlockEntities.Storage::new, CERAMIC_JAR.get(), CHEST_INVISIBLE.get(), CHEST_LOCKED.get()).build(null));
    public static final RegistryObject<BlockEntityType<StageNineBlockEntities.DungeonCore>> DUNGEON_CORE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("dungeon_core",
            () -> BlockEntityType.Builder.of(StageNineBlockEntities.DungeonCore::new, DUNGEON_CORE_STONE.get(), DUNGEON_CORE_SANDSTONE.get()).build(null));
    public static final RegistryObject<BlockEntityType<StageNineBlockEntities.GossipStone>> GOSSIP_STONE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("gossip_stone",
            () -> BlockEntityType.Builder.of(StageNineBlockEntities.GossipStone::new, GOSSIP_STONE.get()).build(null));
    public static final RegistryObject<BlockEntityType<StageNineBlockEntities.Inscription>> INSCRIPTION_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("inscription",
            () -> BlockEntityType.Builder.of(StageNineBlockEntities.Inscription::new, INSCRIPTION.get()).build(null));
    public static final RegistryObject<BlockEntityType<StageNineBlockEntities.SacredFlame>> SACRED_FLAME_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("sacred_flame",
            () -> BlockEntityType.Builder.of(StageNineBlockEntities.SacredFlame::new,
                    SACRED_FLAME_DIN.get(), SACRED_FLAME_FARORE.get(), SACRED_FLAME_NAYRU.get()).build(null));
    public static final RegistryObject<MenuType<PedestalMenu>> PEDESTAL_MENU = MENU_TYPES.register("pedestal", () -> IForgeMenuType.create(PedestalMenu::new));
    public static final RegistryObject<MobEffect> STUN = MOB_EFFECTS.register("stun", StunEffect::new);
    public static final RegistryObject<Attribute> MAX_MAGIC = ATTRIBUTES.register("max_magic",
            () -> new RangedAttribute("attribute.name.zeldaswordskills_remastered.max_magic", 100.0D, 0.0D, 1000.0D).setSyncable(true));
    public static final RegistryObject<EntityType<ThrownRock>> ROCK = ENTITY_TYPES.register("rock", () -> EntityType.Builder.<ThrownRock>of(ThrownRock::new, MobCategory.MISC)
            .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("rock"));
    public static final RegistryObject<EntityType<SwordBeam>> SWORD_BEAM = ENTITY_TYPES.register("sword_beam", () -> EntityType.Builder.<SwordBeam>of(SwordBeam::new, MobCategory.MISC)
            .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1).build("sword_beam"));
    public static final RegistryObject<EntityType<ThrownBomb>> BOMB = ENTITY_TYPES.register("bomb", () -> EntityType.Builder.<ThrownBomb>of(ThrownBomb::new, MobCategory.MISC)
            .sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(1).build("bomb"));
    public static final RegistryObject<EntityType<ToolProjectile>> BOOMERANG = toolProjectile("boomerang", ToolProjectile.Mode.BOOMERANG, 0.35F);
    public static final RegistryObject<EntityType<ToolProjectile>> SEEDSHOT = toolProjectile("seedshot", ToolProjectile.Mode.SEED, 0.2F);
    public static final RegistryObject<EntityType<ToolProjectile>> MAGIC_SPELL = toolProjectile("magic_spell", ToolProjectile.Mode.FIRE, 0.3F);
    public static final RegistryObject<EntityType<ToolProjectile>> CYCLONE_PROJECTILE = toolProjectile("cyclone", ToolProjectile.Mode.WIND, 0.7F);
    public static final RegistryObject<EntityType<ToolProjectile>> HOOKSHOT = toolProjectile("hookshot", ToolProjectile.Mode.HOOKSHOT, 0.2F);
    public static final RegistryObject<EntityType<ToolProjectile>> WHIP = toolProjectile("whip", ToolProjectile.Mode.WHIP, 0.2F);
    public static final RegistryObject<EntityType<ZeldaArrow>> ARROW_CUSTOM = arrowEntity("arrow_custom");
    public static final RegistryObject<EntityType<ZeldaArrow>> ARROW_BOMB = arrowEntity("arrow_bomb");
    public static final RegistryObject<EntityType<ZeldaArrow>> ARROW_ELEMENTAL = arrowEntity("arrow_elemental");
    public static final RegistryObject<EntityType<LegacyCreature>> DARKNUT = creature("darknut", LegacyCreature.Kind.DARKNUT_STANDARD, MobCategory.MONSTER, 0.8F, 2.4F);
    public static final RegistryObject<EntityType<LegacyCreature>> DARKNUT_MIGHTY = creature("darknut_mighty", LegacyCreature.Kind.DARKNUT_MIGHTY, MobCategory.MONSTER, 0.8F, 2.4F);
    public static final RegistryObject<EntityType<LegacyCreature>> FAIRY = creature("fairy", LegacyCreature.Kind.FAIRY, MobCategory.AMBIENT, 0.35F, 0.35F);
    public static final RegistryObject<EntityType<LegacyCreature>> NAVI = ENTITY_TYPES.register("navi", () ->
            EntityType.Builder.<LegacyCreature>of((type, level) -> new NaviCreature(type, level, LegacyCreature.Kind.NAVI), MobCategory.AMBIENT)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1).noSave().build("navi"));
    public static final RegistryObject<EntityType<LegacyCreature>> CHU = creature("chu", LegacyCreature.Kind.CHU_RED, MobCategory.MONSTER, 0.6F, 0.6F);
    public static final RegistryObject<EntityType<LegacyCreature>> CHU_GREEN = creature("chu_green", LegacyCreature.Kind.CHU_GREEN, MobCategory.MONSTER, 0.6F, 0.6F);
    public static final RegistryObject<EntityType<LegacyCreature>> CHU_BLUE = creature("chu_blue", LegacyCreature.Kind.CHU_BLUE, MobCategory.MONSTER, 0.6F, 0.6F);
    public static final RegistryObject<EntityType<LegacyCreature>> CHU_YELLOW = creature("chu_yellow", LegacyCreature.Kind.CHU_YELLOW, MobCategory.MONSTER, 0.6F, 0.6F);
    public static final RegistryObject<EntityType<LegacyCreature>> BABA_DEKU = creature("baba_deku", LegacyCreature.Kind.BABA_DEKU, MobCategory.MONSTER, 0.8F, 1.8F);
    public static final RegistryObject<EntityType<LegacyCreature>> BABA_FIRE = creature("baba_fire", LegacyCreature.Kind.BABA_FIRE, MobCategory.MONSTER, 0.8F, 1.8F);
    public static final RegistryObject<EntityType<LegacyCreature>> BABA_WITHERED = creature("baba_withered", LegacyCreature.Kind.BABA_WITHERED, MobCategory.MONSTER, 0.8F, 1.4F);
    public static final RegistryObject<EntityType<LegacyCreature>> KEESE = creature("keese", LegacyCreature.Kind.KEESE_NORMAL, MobCategory.MONSTER, 0.5F, 0.9F);
    public static final RegistryObject<EntityType<LegacyCreature>> KEESE_FIRE = creature("keese_fire", LegacyCreature.Kind.KEESE_FIRE, MobCategory.MONSTER, 0.5F, 0.9F);
    public static final RegistryObject<EntityType<LegacyCreature>> KEESE_ICE = creature("keese_ice", LegacyCreature.Kind.KEESE_ICE, MobCategory.MONSTER, 0.5F, 0.9F);
    public static final RegistryObject<EntityType<LegacyCreature>> KEESE_THUNDER = creature("keese_thunder", LegacyCreature.Kind.KEESE_THUNDER, MobCategory.MONSTER, 0.5F, 0.9F);
    public static final RegistryObject<EntityType<LegacyCreature>> KEESE_CURSED = creature("keese_cursed", LegacyCreature.Kind.KEESE_CURSED, MobCategory.MONSTER, 0.5F, 0.9F);
    public static final RegistryObject<EntityType<LegacyCreature>> OCTOROK = creature("octorok", LegacyCreature.Kind.OCTOROK_NORMAL, MobCategory.MONSTER, 0.9F, 1.2F);
    public static final RegistryObject<EntityType<LegacyCreature>> OCTOROK_BOMB = creature("octorok_bomb", LegacyCreature.Kind.OCTOROK_BOMB, MobCategory.MONSTER, 0.9F, 1.2F);
    public static final RegistryObject<EntityType<LegacyCreature>> SKULLTULA = creature("skulltula", LegacyCreature.Kind.SKULLTULA_NORMAL, MobCategory.MONSTER, 1.4F, 0.9F);
    public static final RegistryObject<EntityType<LegacyCreature>> SKULLTULA_GOLD = creature("skulltula_gold", LegacyCreature.Kind.SKULLTULA_GOLD, MobCategory.MONSTER, 1.4F, 0.9F);
    public static final RegistryObject<EntityType<LegacyCreature>> WIZZROBE = creature("wizzrobe", LegacyCreature.Kind.WIZZROBE_FIRE, MobCategory.MONSTER, 0.7F, 2.2F);
    public static final RegistryObject<EntityType<LegacyCreature>> WIZZROBE_ICE = creature("wizzrobe_ice", LegacyCreature.Kind.WIZZROBE_ICE, MobCategory.MONSTER, 0.7F, 2.2F);
    public static final RegistryObject<EntityType<LegacyCreature>> WIZZROBE_LIGHTNING = creature("wizzrobe_lightning", LegacyCreature.Kind.WIZZROBE_LIGHTNING, MobCategory.MONSTER, 0.7F, 2.2F);
    public static final RegistryObject<EntityType<LegacyCreature>> WIZZROBE_WIND = creature("wizzrobe_wind", LegacyCreature.Kind.WIZZROBE_WIND, MobCategory.MONSTER, 0.7F, 2.2F);
    public static final RegistryObject<EntityType<LegacyCreature>> DARKNUT_BOSS = creature("darknut_boss", LegacyCreature.Kind.DARKNUT_BOSS, MobCategory.MONSTER, 1.0F, 2.8F);
    public static final RegistryObject<EntityType<LegacyCreature>> WIZZROBE_GRAND = creature("wizzrobe_grand", LegacyCreature.Kind.WIZZROBE_GRAND, MobCategory.MONSTER, 1.0F, 3.3F);
    public static final RegistryObject<EntityType<DesertBossCreature>> DESERT_BOSS = ENTITY_TYPES.register("desert_boss",
            () -> EntityType.Builder.of(DesertBossCreature::new, MobCategory.MONSTER)
                    .sized(.6F, 1.95F).clientTrackingRange(8).build("desert_boss"));
    public static final RegistryObject<EntityType<LegacyCreature>> FIRE_BOSS = creature("fire_boss", LegacyCreature.Kind.FIRE_BOSS, MobCategory.MONSTER, 0.7F, 2.2F);
    public static final RegistryObject<EntityType<zeldaswordskills_remastered.entity.IceBossCreature>> ICE_BOSS = ENTITY_TYPES.register("ice_boss",
            () -> EntityType.Builder.of(zeldaswordskills_remastered.entity.IceBossCreature::new, MobCategory.MONSTER)
                    .sized(.6F, 1.95F).clientTrackingRange(8).build("ice_boss"));
    public static final RegistryObject<EntityType<LegacyCreature>> FOREST_BOSS = creature("forest_boss", LegacyCreature.Kind.FOREST_BOSS, MobCategory.MONSTER, 1.4F, 0.9F);
    public static final RegistryObject<EntityType<LegacyCreature>> WATER_BOSS = creature("water_boss", LegacyCreature.Kind.WATER_BOSS, MobCategory.MONSTER, .9F, 1.2F);
    public static final RegistryObject<EntityType<QuestNpc>> GORON = questNpc("goron", QuestNpc.Role.GORON);
    public static final RegistryObject<EntityType<QuestNpc>> BARNES = questNpc("npc/barnes", QuestNpc.Role.BARNES);
    public static final RegistryObject<EntityType<QuestNpc>> MASK_TRADER = questNpc("npc/mask_trader", QuestNpc.Role.MASK_TRADER);
    public static final RegistryObject<EntityType<QuestNpc>> ORCA = questNpc("npc/orca", QuestNpc.Role.ORCA);
    public static final RegistryObject<EntityType<QuestNpc>> ZELDA = questNpc("npc/zelda", QuestNpc.Role.ZELDA);
    public static final RegistryObject<Item> GORON_SPAWN_EGG = spawnEgg("goron_spawn_egg", GORON, 0x8E5B3E, 0xD8A773);
    public static final RegistryObject<Item> BARNES_SPAWN_EGG = spawnEgg("barnes_spawn_egg", BARNES, 0x745133, 0xE2C89C);
    public static final RegistryObject<Item> MASK_TRADER_SPAWN_EGG = spawnEgg("mask_trader_spawn_egg", MASK_TRADER, 0x6A4228, 0xF3B943);
    public static final RegistryObject<Item> ORCA_SPAWN_EGG = spawnEgg("orca_spawn_egg", ORCA, 0xD9CBB0, 0x5A4633);
    public static final RegistryObject<Item> ZELDA_SPAWN_EGG = spawnEgg("zelda_spawn_egg", ZELDA, 0xEFCF70, 0xA25791);
    public static final RegistryObject<Item> DARKNUT_SPAWN_EGG = spawnEgg("darknut_spawn_egg", DARKNUT, 0x1E1E1E, 0x8B2500);
    public static final RegistryObject<Item> DARKNUT_MIGHTY_SPAWN_EGG = spawnEgg("darknut_mighty_spawn_egg", DARKNUT_MIGHTY, 0x1E1E1E, 0xFB2500);
    public static final RegistryObject<Item> FAIRY_SPAWN_EGG = spawnEgg("fairy_spawn_egg", FAIRY, 0xADFF2F, 0xFFFF00);
    public static final RegistryObject<Item> NAVI_SPAWN_EGG = spawnEgg("navi_spawn_egg", NAVI, 0x80E5FF, 0xFFFFFF);
    public static final RegistryObject<Item> CHU_SPAWN_EGG = spawnEgg("chu_spawn_egg", CHU, 0x008000, 0xDC143C);
    public static final RegistryObject<Item> CHU_GREEN_SPAWN_EGG = spawnEgg("chu_green_spawn_egg", CHU_GREEN, 0x008000, 0x00EE00);
    public static final RegistryObject<Item> CHU_BLUE_SPAWN_EGG = spawnEgg("chu_blue_spawn_egg", CHU_BLUE, 0x008000, 0x3A5FCD);
    public static final RegistryObject<Item> CHU_YELLOW_SPAWN_EGG = spawnEgg("chu_yellow_spawn_egg", CHU_YELLOW, 0x008000, 0xFFFF00);
    public static final RegistryObject<Item> BABA_DEKU_SPAWN_EGG = spawnEgg("baba_deku_spawn_egg", BABA_DEKU, 0x33CC33, 0x0000FF);
    public static final RegistryObject<Item> BABA_FIRE_SPAWN_EGG = spawnEgg("baba_fire_spawn_egg", BABA_FIRE, 0xFF0000, 0x0000FF);
    public static final RegistryObject<Item> BABA_WITHERED_SPAWN_EGG = spawnEgg("baba_withered_spawn_egg", BABA_WITHERED, 0x8B5A00, 0x0000FF);
    public static final RegistryObject<Item> KEESE_SPAWN_EGG = spawnEgg("keese_spawn_egg", KEESE, 0x000000, 0x555555);
    public static final RegistryObject<Item> KEESE_FIRE_SPAWN_EGG = spawnEgg("keese_fire_spawn_egg", KEESE_FIRE, 0x000000, 0xFF4500);
    public static final RegistryObject<Item> KEESE_ICE_SPAWN_EGG = spawnEgg("keese_ice_spawn_egg", KEESE_ICE, 0x000000, 0x40E0D0);
    public static final RegistryObject<Item> KEESE_THUNDER_SPAWN_EGG = spawnEgg("keese_thunder_spawn_egg", KEESE_THUNDER, 0x000000, 0xFFD700);
    public static final RegistryObject<Item> KEESE_CURSED_SPAWN_EGG = spawnEgg("keese_cursed_spawn_egg", KEESE_CURSED, 0x000000, 0x800080);
    public static final RegistryObject<Item> OCTOROK_SPAWN_EGG = spawnEgg("octorok_spawn_egg", OCTOROK, 0x68228B, 0xBA55D3);
    public static final RegistryObject<Item> OCTOROK_BOMB_SPAWN_EGG = spawnEgg("octorok_bomb_spawn_egg", OCTOROK_BOMB, 0x68228B, 0xFF00FF);
    public static final RegistryObject<Item> SKULLTULA_SPAWN_EGG = spawnEgg("skulltula_spawn_egg", SKULLTULA, 0x080808, 0xFFFF00);
    public static final RegistryObject<Item> SKULLTULA_GOLD_SPAWN_EGG = spawnEgg("skulltula_gold_spawn_egg", SKULLTULA_GOLD, 0x080808, 0xE68A00);
    public static final RegistryObject<Item> WIZZROBE_SPAWN_EGG = spawnEgg("wizzrobe_spawn_egg", WIZZROBE, 0x8B2500, 0xFF0000);
    public static final RegistryObject<Item> WIZZROBE_ICE_SPAWN_EGG = spawnEgg("wizzrobe_ice_spawn_egg", WIZZROBE_ICE, 0x8B2500, 0x00B2EE);
    public static final RegistryObject<Item> WIZZROBE_LIGHTNING_SPAWN_EGG = spawnEgg("wizzrobe_lightning_spawn_egg", WIZZROBE_LIGHTNING, 0x8B2500, 0xEEEE00);
    public static final RegistryObject<Item> WIZZROBE_WIND_SPAWN_EGG = spawnEgg("wizzrobe_wind_spawn_egg", WIZZROBE_WIND, 0x8B2500, 0x00EE76);
    public static final RegistryObject<Item> DARKNUT_BOSS_SPAWN_EGG = spawnEgg("darknut_boss_spawn_egg", DARKNUT_BOSS, 0x1E1E1E, 0x000000);
    public static final RegistryObject<Item> WIZZROBE_GRAND_SPAWN_EGG = spawnEgg("wizzrobe_grand_spawn_egg", WIZZROBE_GRAND, 0x8B2500, 0x1E1E1E);
    public static final List<RegistryObject<EntityType<LegacyCreature>>> LEGACY_CREATURE_TYPES = List.of(
            DARKNUT, DARKNUT_MIGHTY, FAIRY, NAVI, CHU, CHU_GREEN, CHU_BLUE, CHU_YELLOW,
            BABA_DEKU, BABA_FIRE, BABA_WITHERED, KEESE, KEESE_FIRE, KEESE_ICE, KEESE_THUNDER, KEESE_CURSED,
            OCTOROK, OCTOROK_BOMB, SKULLTULA, SKULLTULA_GOLD, WIZZROBE, WIZZROBE_ICE,
            WIZZROBE_LIGHTNING, WIZZROBE_WIND, DARKNUT_BOSS, WIZZROBE_GRAND, FIRE_BOSS, FOREST_BOSS, WATER_BOSS);
    public static final RegistryObject<SimpleParticleType> CYCLONE = PARTICLE_TYPES.register("cyclone", () -> new SimpleParticleType(false));
    public static final RegistryObject<RecipeSerializer<ThrowingRockRecipe>> THROWING_ROCK_RECIPE = RECIPE_SERIALIZERS.register("throwing_rock",
            () -> new SimpleCraftingRecipeSerializer<>(ThrowingRockRecipe::new));
    public static final RegistryObject<RecipeSerializer<HeartPieceRecipe>> HEART_PIECE_RECIPE = RECIPE_SERIALIZERS.register("heart_piece",
            () -> new SimpleCraftingRecipeSerializer<>(HeartPieceRecipe::new));
    public static final RegistryObject<RecipeSerializer<HookshotUpgradeRecipe>> HOOKSHOT_UPGRADE_RECIPE = RECIPE_SERIALIZERS.register("hookshot_upgrade",
            () -> new SimpleCraftingRecipeSerializer<>(HookshotUpgradeRecipe::new));
    public static final RegistryObject<RecipeSerializer<CombineBombBagRecipe>> COMBINE_BOMB_BAG_RECIPE = RECIPE_SERIALIZERS.register("combine_bomb_bag",
            () -> new SimpleCraftingRecipeSerializer<>(CombineBombBagRecipe::new));
    public static final RegistryObject<RecipeSerializer<BagToBombArrowRecipe>> BAG_TO_BOMB_ARROW_RECIPE = RECIPE_SERIALIZERS.register("bag_to_bomb_arrow",
            () -> new SimpleCraftingRecipeSerializer<>(BagToBombArrowRecipe::new));
    public static final RegistryObject<RecipeSerializer<TunicDyeRecipe>> TUNIC_DYE_RECIPE = RECIPE_SERIALIZERS.register("tunic_dye",
            () -> new SimpleCraftingRecipeSerializer<>(TunicDyeRecipe::new));
    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_TABLE_LOOT_MODIFIER =
            LOOT_MODIFIERS.register("add_table", () -> AddTableLootModifier.CODEC);
    public static final RegistryObject<SoundEvent> SWORD_CUT = sound("sword_cut");
    public static final RegistryObject<SoundEvent> SWORD_MISS = sound("sword_miss");
    public static final RegistryObject<SoundEvent> SWORD_STRIKE = sound("sword_strike");
    public static final RegistryObject<SoundEvent> LEVEL_UP = sound("level_up");
    public static final RegistryObject<SoundEvent> ARMOR_SHATTER = sound("armor_shatter");
    public static final RegistryObject<SoundEvent> HELM_SPLITTER_HIT = sound("helm_splitter_hit");
    public static final RegistryObject<SoundEvent> FATAL_STRIKE_HIT = sound("fatal_strike_hit");
    public static final RegistryObject<SoundEvent> FATAL_STRIKE_FOCUS = sound("fatal_strike_focus");
    public static final RegistryObject<SoundEvent> FLASH_ASSAULT_DODGE = sound("flash_assault_dodge");
    public static final RegistryObject<SoundEvent> FLASH_ASSAULT_DASH = sound("flash_assault_dash");
    public static final RegistryObject<SoundEvent> FLASH_ASSAULT_ATTACK = sound("flash_assault_attack");
    public static final RegistryObject<SoundEvent> RISING_CUT_SLASH = sound("rising_cut_slash");
    public static final RegistryObject<SoundEvent> LEAPING_BLOW_SOUND = sound("leaping_blow");
    public static final RegistryObject<SoundEvent> MORTAL_DRAW_SOUND = sound("mortal_draw");
    public static final RegistryObject<SoundEvent> SPIN_ATTACK_SOUND = sound("spin_attack");
    public static final RegistryObject<SoundEvent> PARRY_SUCCESS_SOUND = sound("parry_success");
    public static final RegistryObject<SoundEvent> SWORD_BREAK_SOUND = sound("sword_break");
    public static final RegistryObject<SoundEvent> NOTE_OCARINA = sound("note.ocarina");
    public static final RegistryObject<SoundEvent> SUCCESS = sound("success");
    public static final RegistryObject<SoundEvent> SECRET_MEDLEY = sound("secret_medley");
    public static final RegistryObject<SoundEvent> WEB_SPLAT = sound("web_splat");
    public static final RegistryObject<SoundEvent> ROCK_FALL = sound("rock_fall");
    public static final RegistryObject<SoundEvent> HOOKSHOT_SOUND = sound("hookshot");
    public static final RegistryObject<SoundEvent> BOOMERANG_SOUND = sound("boomerang");
    public static final RegistryObject<SoundEvent> MAGIC_FAILURE = sound("magic_failure");
    public static final RegistryObject<SoundEvent> MAGIC_FIRE = sound("magic_fire");
    public static final RegistryObject<SoundEvent> MAGIC_ICE = sound("magic_ice");
    public static final RegistryObject<SoundEvent> WHIP_SOUND = sound("whip");
    public static final RegistryObject<SoundEvent> WHIRLWIND = sound("whirlwind");
    public static final RegistryObject<SoundEvent> NAVI_VOICE = sound("navi_voice");
    public static final RegistryObject<SoundEvent> NAVI_FLOAT = sound("navi_float");
    public static final RegistryObject<SoundEvent> NAVI_INTERACT = sound("navi_interact");
    public static final RegistryObject<SoundEvent> GET_ITEM = sound("get_item");
    public static final RegistryObject<SoundEvent> GET_HEART = sound("get_heart");
    public static final Map<ResourceLocation, RegistryObject<SoundEvent>> SONG_SOUNDS = Map.ofEntries(
            songSound("epona"), songSound("healing"), songSound("saria"), songSound("soaring"), songSound("storms"),
            songSound("sun"), songSound("time"), songSound("bolero"), songSound("minuet"), songSound("prelude"),
            songSound("oath"), songSound("nocturne"), songSound("requiem"), songSound("serenade"), songSound("lullaby"));

    private static final boolean STAGE_NINE_ITEMS_REGISTERED = registerStageNineItems();

    public static final List<RegistryObject<Item>> SKILL_ITEMS = category(false, "skill_orb", "heart_piece", "skill_wiper");
    public static final List<RegistryObject<Item>> KEY_ITEMS = category(true, "big_key", "small_key", "skeleton_key");
    public static final List<RegistryObject<Item>> TOOL_ITEMS = category(true,
            "hookshot", "stoneshot", "multishot", "hookshot_extender", "claw_upgrade", "multi_hook_upgrade", "bomb_bag", "standard_bomb", "fire_bomb", "water_bomb",
            "empty_spirit_crystal", "din_crystal", "farore_crystal", "nayru_crystal", "deku_leaf", "deku_nut", "silver_gauntlets", "golden_gauntlets",
            "magic_mirror", "fairy_bottle", "navi_bottle", "rocs_feather", "red_potion", "green_potion", "blue_potion", "yellow_potion", "purple_potion",
            "lon_lon_milk", "lon_lon_special", "small_magic_jar", "large_magic_jar", "magic_container", "bombos_medallion", "ether_medallion",
            "quake_medallion", "fire_rod", "ice_rod", "tornado_rod", "whip", "magic_whip", "bomb_flower_seed", "held_block", "throwing_rock");
    public static final List<RegistryObject<Item>> COMBAT_ITEMS = category(true,
            "hero_tunic_helmet", "hero_tunic_chestplate", "hero_tunic_leggings", "hero_tunic_boots", "goron_tunic_helmet", "goron_tunic_chestplate",
            "goron_tunic_leggings", "zora_tunic_helmet", "zora_tunic_chestplate", "zora_tunic_leggings", "zora_tunic_boots", "heavy_boots",
            "hover_boots", "pegasus_boots", "rubber_boots", "deku_shield", "hylian_shield", "mirror_shield", "broken_sword", "kokiri_sword",
            "ordon_sword", "giant_sword", "biggoron_sword", "master_sword", "tempered_sword", "golden_sword", "true_master_sword", "darknut_sword",
            "wooden_hammer", "skull_hammer", "megaton_hammer", "boomerang", "magic_boomerang", "hero_bow", "bomb_arrow", "fire_bomb_arrow",
            "water_bomb_arrow", "fire_arrow", "ice_arrow", "light_arrow", "slingshot", "scattershot", "supershot");
    public static final List<RegistryObject<Item>> MASK_ITEMS = category(true,
            "blast_mask", "bunny_hood", "couples_mask", "gerudo_mask", "giants_mask", "gibdo_mask", "hawkeye_mask", "keaton_mask", "mask_of_scents",
            "skull_mask", "spooky_mask", "stone_mask", "mask_of_truth", "deku_mask", "goron_mask", "zora_mask", "fierce_deity_mask", "majora_mask");
    public static final List<RegistryObject<Item>> TREASURE_ITEMS = category(false,
            "pendant_wisdom", "pendant_courage", "pendant_power", "master_ore", "chu_jelly_green", "chu_jelly_red", "chu_jelly_blue", "chu_jelly_yellow",
            "skulltula_token", "links_house", "fairy_ocarina", "ocarina_of_time", "goddess_harp", "book_of_mudora", "power_piece", "small_heart",
            "claim_check", "cojiro", "evil_crystal", "eye_drops", "eyeball_frog", "goron_sword", "jelly_blob", "monster_claw", "odd_mushroom",
            "odd_potion", "poacher_saw", "pocket_egg", "prescription", "tentacle", "zeldas_letter", "knights_crest");
    public static final List<RegistryObject<Item>> BLOCK_ITEMS = List.of(
            BEAM_WOODEN_ITEM, BOMB_FLOWER_ITEM, CERAMIC_JAR_ITEM, CHEST_INVISIBLE_ITEM, CHEST_LOCKED_ITEM,
            DOOR_BOSS_DESERT_ITEM, DOOR_BOSS_EARTH_ITEM, DOOR_BOSS_FIRE_ITEM, DOOR_BOSS_FOREST_ITEM,
            DOOR_BOSS_ICE_ITEM, DOOR_BOSS_WATER_ITEM, DOOR_BOSS_END_ITEM, DOOR_LOCKED_ITEM,
            DUNGEON_CORE_STONE_ITEM, DUNGEON_CORE_SANDSTONE_ITEM,
            DUNGEON_STONE_STONE_ITEM, DUNGEON_STONE_SANDSTONE_ITEM, GOSSIP_STONE_ITEM, LIGHT_BLOCK_ITEM,
            HEAVY_BLOCK_ITEM, HOOK_TARGET_ITEM, HOOK_TARGET_ALL_ITEM, INSCRIPTION_ITEM, LEVER_GIANT_ITEM,
            PEG_WOODEN_ITEM, PEG_RUSTY_ITEM, PEDESTAL_ITEM, QUAKE_STONE_ITEM, QUAKE_STONE_MOSSY_ITEM,
            SACRED_FLAME_DIN_ITEM, SACRED_FLAME_FARORE_ITEM, SACRED_FLAME_NAYRU_ITEM,
            SECRET_STONE_STONE_ITEM, SECRET_STONE_SANDSTONE_ITEM, SECRET_STONE_NETHER_BRICKS_ITEM,
            SECRET_STONE_STONE_BRICKS_ITEM, SECRET_STONE_MOSSY_COBBLESTONE_ITEM, SECRET_STONE_ICE_ITEM,
            SECRET_STONE_COBBLESTONE_ITEM, SECRET_STONE_END_STONE_ITEM, SECRET_STONE_NETHER_WART_BLOCK_ITEM, TIME_BLOCK_ITEM, ROYAL_BLOCK_ITEM,
            WARP_STONE_BOLERO_ITEM, WARP_STONE_MINUET_ITEM, WARP_STONE_PRELUDE_ITEM, WARP_STONE_OATH_ITEM,
            WARP_STONE_NOCTURNE_ITEM, WARP_STONE_REQUIEM_ITEM, WARP_STONE_SERENADE_ITEM,
            ANCIENT_TABLET_BOMBOS_ITEM, ANCIENT_TABLET_ETHER_ITEM, ANCIENT_TABLET_QUAKE_ITEM,
            SECRET_STONE_PURPUR_BLOCK_ITEM, SECRET_STONE_END_STONE_BRICKS_ITEM);
    public static final List<RegistryObject<Item>> SPAWN_EGG_ITEMS = List.of(
            DARKNUT_SPAWN_EGG, DARKNUT_MIGHTY_SPAWN_EGG, FAIRY_SPAWN_EGG, NAVI_SPAWN_EGG,
            CHU_SPAWN_EGG, CHU_GREEN_SPAWN_EGG, CHU_BLUE_SPAWN_EGG, CHU_YELLOW_SPAWN_EGG,
            BABA_DEKU_SPAWN_EGG, BABA_FIRE_SPAWN_EGG, BABA_WITHERED_SPAWN_EGG,
            KEESE_SPAWN_EGG, KEESE_FIRE_SPAWN_EGG, KEESE_ICE_SPAWN_EGG, KEESE_THUNDER_SPAWN_EGG, KEESE_CURSED_SPAWN_EGG,
            OCTOROK_SPAWN_EGG, OCTOROK_BOMB_SPAWN_EGG, SKULLTULA_SPAWN_EGG, SKULLTULA_GOLD_SPAWN_EGG,
            WIZZROBE_SPAWN_EGG, WIZZROBE_ICE_SPAWN_EGG, WIZZROBE_LIGHTNING_SPAWN_EGG, WIZZROBE_WIND_SPAWN_EGG,
            DARKNUT_BOSS_SPAWN_EGG, WIZZROBE_GRAND_SPAWN_EGG,
            GORON_SPAWN_EGG, BARNES_SPAWN_EGG, MASK_TRADER_SPAWN_EGG, ORCA_SPAWN_EGG, ZELDA_SPAWN_EGG);

    public static final List<RegistryObject<Item>> MUSIC_DISC_ITEMS = MusicDiscCatalog.DISCS.stream().map(disc -> {
        RegistryObject<SoundEvent> music = sound(disc.id());
        return item(disc.id(), () -> new net.minecraft.world.item.RecordItem(1, music,
                new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), disc.seconds()));
    }).toList();
    public static final RegistryObject<CreativeModeTab> ZELDA_OST_TAB =
            tab("zelda_ost", "music_disc_a_link_to_the_past", MUSIC_DISC_ITEMS);

    public static final RegistryObject<CreativeModeTab> SKILLS_TAB = tab("skills", "skill_orb", SKILL_ITEMS);
    public static final RegistryObject<CreativeModeTab> KEYS_TAB = tab("keys", "big_key", KEY_ITEMS);
    public static final RegistryObject<CreativeModeTab> TOOLS_TAB = tab("tools", "hookshot", TOOL_ITEMS);
    public static final RegistryObject<CreativeModeTab> COMBAT_TAB = tab("combat", "master_sword", COMBAT_ITEMS);
    public static final RegistryObject<CreativeModeTab> MASKS_TAB = tab("masks", "majora_mask", MASK_ITEMS);
    public static final RegistryObject<CreativeModeTab> TREASURES_TAB = tab("treasures", "master_ore", TREASURE_ITEMS);
    public static final RegistryObject<CreativeModeTab> BLOCKS_TAB = tab("blocks", "pedestal", BLOCK_ITEMS);
    public static final RegistryObject<CreativeModeTab> SPAWN_EGGS_TAB = CREATIVE_MODE_TABS.register("spawn_eggs", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.zeldaswordskills_remastered.spawn_eggs")).icon(() -> new ItemStack(ZELDA_SPAWN_EGG.get()))
            .displayItems((parameters, output) -> SPAWN_EGG_ITEMS.forEach(item -> output.accept(item.get()))).build());

    private ZSSRegistries() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITY_TYPES.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        MOB_EFFECTS.register(modBus);
        ATTRIBUTES.register(modBus);
        SOUND_EVENTS.register(modBus);
        PARTICLE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        FEATURES.register(modBus);
    }

    public static List<RegistryObject<Item>> allItems() {
        return List.copyOf(ITEM_BY_ID.values());
    }

    public static Item getItem(String path) {
        RegistryObject<Item> item = ITEM_BY_ID.get(path);
        if (item == null) throw new IllegalArgumentException("Unknown ZeldaSwordSkills_Remastered item: " + path);
        return item.get();
    }

    private static RegistryObject<Item> item(String path, Supplier<? extends Item> factory) {
        if (ITEM_BY_ID.containsKey(path)) {
            throw new IllegalStateException("Duplicate item registration: " + path);
        }
        RegistryObject<Item> item = ITEMS.register(path, factory);
        ITEM_BY_ID.put(path, item);
        return item;
    }

    private static RegistryObject<Item> blockItem(String path, RegistryObject<? extends Block> block) {
        return item(path, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static RegistryObject<LockedDoorBlock> bossDoor(String path, DungeonType type) {
        return BLOCKS.register(path, () -> new LockedDoorBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(-1, 3600000).sound(SoundType.METAL).noOcclusion(), type));
    }

    private static RegistryObject<SacredFlameBlock> sacredFlame(String path, SacredFlameBlock.FlameType type) {
        return BLOCKS.register(path, () -> new SacredFlameBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.FIRE).strength(-1, 3600000)
                .lightLevel(state -> state.getValue(SacredFlameBlock.EXTINGUISHED) ? 0 : 15)
                .sound(SoundType.WOOL).noCollission().noOcclusion(), type));
    }

    private static RegistryObject<MechanismBlocks.SecretStone> secretStone(String path, MechanismBlocks.SecretVariant variant) {
        return BLOCKS.register(path, () -> new MechanismBlocks.SecretStone(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(-1, 3600000).sound(SoundType.STONE), variant));
    }

    private static RegistryObject<WarpStoneBlock> warpStone(String path, WarpStoneBlock.WarpSong song) {
        return BLOCKS.register(path, () -> new WarpStoneBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(2.0F, 12.0F).lightLevel(state -> 7).sound(SoundType.STONE), song));
    }

    private static RegistryObject<Item> sword(String path, net.minecraft.world.item.Tier tier, int damage, float speed,
                                               boolean twoHanded, boolean masterSword) {
        return item(path, () -> new ZeldaCombatItems.Sword(tier, damage, speed, twoHanded, masterSword,
                path.equals("golden_sword"), new Item.Properties()));
    }

    private static boolean registerStageNineItems() {
        item("skill_orb", () -> new ProgressionItem(ProgressionItem.Kind.SKILL_ORB, new Item.Properties().stacksTo(16)));
        item("heart_piece", () -> new ProgressionItem(ProgressionItem.Kind.HEART_PIECE, new Item.Properties()));
        item("skill_wiper", () -> new ProgressionItem(ProgressionItem.Kind.SKILL_WIPER, new Item.Properties().stacksTo(1)));
        item("big_key", () -> new BigKeyItem(new Item.Properties().stacksTo(1)));
        item("small_key", () -> new Item(new Item.Properties()));
        item("skeleton_key", () -> new Item(new Item.Properties().stacksTo(1)));

        item("hookshot", () -> new StageNineToolItem(StageNineToolItem.Kind.HOOKSHOT, 1, new Item.Properties().stacksTo(1)));
        tool("stoneshot", StageNineToolItem.Kind.HOOKSHOT, 2);
        tool("multishot", StageNineToolItem.Kind.HOOKSHOT, 3);
        item("hookshot_extender", () -> new StageNineToolItem(StageNineToolItem.Kind.HOOKSHOT_UPGRADE, 1, new Item.Properties().stacksTo(1)));
        item("claw_upgrade", () -> new StageNineToolItem(StageNineToolItem.Kind.HOOKSHOT_UPGRADE, 2, new Item.Properties().stacksTo(1)));
        item("multi_hook_upgrade", () -> new StageNineToolItem(StageNineToolItem.Kind.HOOKSHOT_UPGRADE, 3, new Item.Properties().stacksTo(1)));
        item("bomb_bag", () -> new BombBagItem(new Item.Properties().stacksTo(1)));
        item("standard_bomb", () -> new BombItem(ThrownBomb.BombKind.STANDARD, new Item.Properties().stacksTo(16)));
        item("fire_bomb", () -> new BombItem(ThrownBomb.BombKind.FIRE, new Item.Properties().stacksTo(16)));
        item("water_bomb", () -> new BombItem(ThrownBomb.BombKind.WATER, new Item.Properties().stacksTo(16)));
        item("empty_spirit_crystal", () -> new SpiritCrystalItem(SpiritCrystalItem.Kind.EMPTY, new Item.Properties().stacksTo(1)));
        item("din_crystal", () -> new SpiritCrystalItem(SpiritCrystalItem.Kind.DIN, new Item.Properties().stacksTo(1)));
        item("farore_crystal", () -> new SpiritCrystalItem(SpiritCrystalItem.Kind.FARORE, new Item.Properties().stacksTo(1)));
        item("nayru_crystal", () -> new SpiritCrystalItem(SpiritCrystalItem.Kind.NAYRU, new Item.Properties().stacksTo(1)));
        item("deku_leaf", () -> new StageNineToolItem(StageNineToolItem.Kind.DEKU_LEAF, 1, new Item.Properties().stacksTo(1)));
        item("deku_nut", () -> new StageNineToolItem(StageNineToolItem.Kind.DEKU_NUT, 1, new Item.Properties()));
        item("silver_gauntlets", () -> new LiftItems.Gauntlet(ZSSBlockInteractions.Weight.MEDIUM, new Item.Properties().stacksTo(1)));
        item("golden_gauntlets", () -> new LiftItems.Gauntlet(ZSSBlockInteractions.Weight.VERY_HEAVY, new Item.Properties().stacksTo(1)));
        item("magic_mirror", () -> new SpecialItems.MagicMirror(new Item.Properties().stacksTo(1)));
        item("fairy_bottle", () -> new SpecialItems.FairyBottle(new Item.Properties().stacksTo(8)));
        item("rocs_feather", () -> new StageNineToolItem(StageNineToolItem.Kind.ROCS_FEATHER, 1, new Item.Properties().stacksTo(1)));
        drink("red_potion", DrinkableItem.Kind.RED); drink("green_potion", DrinkableItem.Kind.GREEN);
        drink("blue_potion", DrinkableItem.Kind.BLUE); drink("yellow_potion", DrinkableItem.Kind.YELLOW);
        drink("purple_potion", DrinkableItem.Kind.PURPLE); drink("lon_lon_milk", DrinkableItem.Kind.MILK);
        drink("lon_lon_special", DrinkableItem.Kind.SPECIAL);
        item("small_magic_jar", () -> new ProgressionItem(ProgressionItem.Kind.SMALL_MAGIC, new Item.Properties()));
        item("large_magic_jar", () -> new ProgressionItem(ProgressionItem.Kind.LARGE_MAGIC, new Item.Properties()));
        drink("magic_container", DrinkableItem.Kind.MAGIC_CONTAINER);
        tool("bombos_medallion", StageNineToolItem.Kind.MEDALLION, 1);
        tool("ether_medallion", StageNineToolItem.Kind.MEDALLION, 2);
        tool("quake_medallion", StageNineToolItem.Kind.MEDALLION, 3);
        tool("fire_rod", StageNineToolItem.Kind.ROD, 1); tool("ice_rod", StageNineToolItem.Kind.ROD, 2);
        tool("tornado_rod", StageNineToolItem.Kind.ROD, 3); tool("whip", StageNineToolItem.Kind.WHIP, 1);
        tool("magic_whip", StageNineToolItem.Kind.WHIP, 2);
        item("bomb_flower_seed", () -> new BombFlowerSeedItem(new Item.Properties()));
        item("held_block", () -> new LiftItems.HeldBlock(new Item.Properties().stacksTo(1)));

        armorSet("hero_tunic", EquipmentItem.Gear.HERO, ArmorMaterials.LEATHER, true);
        armorSet("goron_tunic", EquipmentItem.Gear.GORON, ArmorMaterials.IRON, false);
        armorSet("zora_tunic", EquipmentItem.Gear.ZORA, ArmorMaterials.CHAIN, true);
        boots("heavy_boots", EquipmentItem.Gear.HEAVY_BOOTS, ArmorMaterials.IRON);
        boots("hover_boots", EquipmentItem.Gear.HOVER_BOOTS, ArmorMaterials.GOLD);
        boots("pegasus_boots", EquipmentItem.Gear.PEGASUS_BOOTS, ArmorMaterials.LEATHER);
        boots("rubber_boots", EquipmentItem.Gear.RUBBER_BOOTS, ArmorMaterials.LEATHER);
        // Stage 16.5 hammer retune: wooden 15 @ 0.8, skull 25 @ 0.6, megaton 40 @ 0.45, expressed
        // as the vanilla damage modifier over the tier base and speed modifier over the 4.0 base.
        item("wooden_hammer", () -> new ZeldaCombatItems.Hammer(Tiers.WOOD, 14, -3.2F, ZSSBlockInteractions.Weight.VERY_LIGHT, false, new Item.Properties()));
        item("skull_hammer", () -> new ZeldaCombatItems.Hammer(Tiers.IRON, 22, -3.4F, ZSSBlockInteractions.Weight.MEDIUM, true, new Item.Properties()));
        item("megaton_hammer", () -> new ZeldaCombatItems.Hammer(Tiers.DIAMOND, 36, -3.55F, ZSSBlockInteractions.Weight.VERY_HEAVY, true, new Item.Properties()));
        tool("boomerang", StageNineToolItem.Kind.BOOMERANG, 1); tool("magic_boomerang", StageNineToolItem.Kind.BOOMERANG, 2);
        item("hero_bow", () -> new ZeldaCombatItems.HeroBow(new Item.Properties().stacksTo(1)));
        arrow("bomb_arrow", ZeldaCombatItems.ArrowKind.BOMB); arrow("fire_bomb_arrow", ZeldaCombatItems.ArrowKind.FIRE_BOMB);
        arrow("water_bomb_arrow", ZeldaCombatItems.ArrowKind.WATER_BOMB); arrow("fire_arrow", ZeldaCombatItems.ArrowKind.FIRE);
        arrow("ice_arrow", ZeldaCombatItems.ArrowKind.ICE); arrow("light_arrow", ZeldaCombatItems.ArrowKind.LIGHT);
        tool("slingshot", StageNineToolItem.Kind.SLINGSHOT, 1); tool("scattershot", StageNineToolItem.Kind.SLINGSHOT, 2);
        tool("supershot", StageNineToolItem.Kind.SLINGSHOT, 3);

        for (EquipmentItem.Gear mask : List.of(EquipmentItem.Gear.BLAST_MASK, EquipmentItem.Gear.BUNNY_HOOD, EquipmentItem.Gear.COUPLES_MASK,
                EquipmentItem.Gear.GERUDO_MASK, EquipmentItem.Gear.GIANTS_MASK, EquipmentItem.Gear.GIBDO_MASK, EquipmentItem.Gear.HAWKEYE_MASK,
                EquipmentItem.Gear.KEATON_MASK, EquipmentItem.Gear.MASK_OF_SCENTS, EquipmentItem.Gear.SKULL_MASK, EquipmentItem.Gear.SPOOKY_MASK,
                EquipmentItem.Gear.STONE_MASK, EquipmentItem.Gear.MASK_OF_TRUTH, EquipmentItem.Gear.DEKU_MASK, EquipmentItem.Gear.GORON_MASK,
                EquipmentItem.Gear.ZORA_MASK, EquipmentItem.Gear.FIERCE_DEITY_MASK, EquipmentItem.Gear.MAJORA_MASK)) {
            String path = mask.name().toLowerCase(java.util.Locale.ROOT);
            item(path, () -> new EquipmentItem(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, mask, new Item.Properties()));
        }
        item("skulltula_token", () -> new ProgressionItem(ProgressionItem.Kind.SKULLTULA_TOKEN, new Item.Properties()));
        item("links_house", () -> new SpecialItems.LinksHouse(new Item.Properties().stacksTo(1)));
        item("power_piece", () -> new ProgressionItem(ProgressionItem.Kind.POWER_PIECE, new Item.Properties()));
        item("small_heart", () -> new ProgressionItem(ProgressionItem.Kind.SMALL_HEART, new Item.Properties()));
        return true;
    }

    private static void drink(String path, DrinkableItem.Kind kind) { item(path, () -> new DrinkableItem(kind, new Item.Properties().stacksTo(1))); }
    private static void tool(String path, StageNineToolItem.Kind kind, int level) {
        item(path, () -> new StageNineToolItem(kind, level, new Item.Properties().stacksTo(1)));
    }
    private static void arrow(String path, ZeldaCombatItems.ArrowKind kind) { item(path, () -> new ZeldaCombatItems.ElementArrow(kind, new Item.Properties())); }
    private static void boots(String path, EquipmentItem.Gear gear, net.minecraft.world.item.ArmorMaterial material) {
        item(path, () -> new EquipmentItem(material, ArmorItem.Type.BOOTS, gear, new Item.Properties()));
    }
    private static void armorSet(String prefix, EquipmentItem.Gear gear, net.minecraft.world.item.ArmorMaterial material, boolean boots) {
        item(prefix + "_helmet", () -> new EquipmentItem(material, ArmorItem.Type.HELMET, gear, new Item.Properties()));
        item(prefix + "_chestplate", () -> new EquipmentItem(material, ArmorItem.Type.CHESTPLATE, gear, new Item.Properties()));
        item(prefix + "_leggings", () -> new EquipmentItem(material, ArmorItem.Type.LEGGINGS, gear, new Item.Properties()));
        if (boots) item(prefix + "_boots", () -> new EquipmentItem(material, ArmorItem.Type.BOOTS, gear, new Item.Properties()));
    }

    private static RegistryObject<EntityType<QuestNpc>> questNpc(String path, QuestNpc.Role role) {
        return ENTITY_TYPES.register(path, () -> EntityType.Builder.<QuestNpc>of(
                        (type, level) -> new QuestNpc(type, level, role), MobCategory.CREATURE)
                .sized(0.6F, 1.8F).clientTrackingRange(8).build(path));
    }

    private static RegistryObject<EntityType<LegacyCreature>> creature(String path, LegacyCreature.Kind kind,
                                                                       MobCategory category, float width, float height) {
        return ENTITY_TYPES.register(path, () -> EntityType.Builder.<LegacyCreature>of(
                        (type, level) -> createCreature(type, level, kind), category)
                .sized(width, height).clientTrackingRange(8).build(path));
    }

    private static LegacyCreature createCreature(EntityType<LegacyCreature> type, net.minecraft.world.level.Level level,
                                                 LegacyCreature.Kind kind) {
        return switch (kind.family()) {
            case CHU -> new ChuCreature(type, level, kind);
            case DARKNUT -> new DarknutCreature(type, level, kind);
            case BABA -> new DekuCreature(type, level, kind);
            case KEESE -> new KeeseCreature(type, level, kind);
            case OCTOROK -> kind == LegacyCreature.Kind.WATER_BOSS
                    ? new zeldaswordskills_remastered.entity.WaterBossCreature(type, level) : new OctorokCreature(type, level, kind);
            case SKULLTULA -> new SkulltulaCreature(type, level, kind);
            case WIZZROBE -> new WizzrobeCreature(type, level, kind);
            case FIRE_BOSS -> new zeldaswordskills_remastered.entity.FireBossCreature(type, level);
            case FOREST_BOSS -> new zeldaswordskills_remastered.entity.ForestBossCreature(type, level);
            case FAIRY -> new FairyCreature(type, level, kind);
        };
    }

    private static RegistryObject<EntityType<ToolProjectile>> toolProjectile(String path, ToolProjectile.Mode mode, float size) {
        return ENTITY_TYPES.register(path, () -> {
            var builder = EntityType.Builder.<ToolProjectile>of(
                            (type, level) -> new ToolProjectile(type, level, mode), MobCategory.MISC)
                    .sized(size, size).clientTrackingRange(8).updateInterval(1);
            if (mode.isTethered()) builder.noSave();
            return builder.build(path);
        });
    }

    private static RegistryObject<EntityType<ZeldaArrow>> arrowEntity(String path) {
        return ENTITY_TYPES.register(path, () -> EntityType.Builder.<ZeldaArrow>of(ZeldaArrow::new, MobCategory.MISC)
                .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1).build(path));
    }

    private static RegistryObject<Item> spawnEgg(String path, RegistryObject<? extends EntityType<? extends Mob>> type,
                                                  int background, int highlight) {
        return item(path, () -> new ForgeSpawnEggItem(type, background, highlight, new Item.Properties()));
    }

    private static List<RegistryObject<Item>> category(boolean singleStack, String... paths) {
        return Arrays.stream(paths).map(path -> ITEM_BY_ID.computeIfAbsent(path, id -> ITEMS.register(id,
                () -> new Item(singleStack ? new Item.Properties().stacksTo(1) : new Item.Properties())))).toList();
    }

    private static RegistryObject<CreativeModeTab> tab(String path, String iconPath, List<RegistryObject<Item>> contents) {
        return CREATIVE_MODE_TABS.register(path, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.zeldaswordskills_remastered." + path)).icon(() -> new ItemStack(ITEM_BY_ID.get(iconPath).get()))
                .displayItems((parameters, output) -> {
                    contents.forEach(item -> {
                        if (item.getId().getPath().equals("skill_orb")) ZSSContentIds.SKILLS.forEach(skill -> {
                            ItemStack stack = new ItemStack(item.get()); stack.getOrCreateTag().putString(ProgressionItem.SKILL_TAG, skill.toString()); output.accept(stack);
                        });
                        else if (item.getId().getPath().equals("big_key")) { }
                        else output.accept(item.get());
                    });
                    if (path.equals("keys")) for (DungeonType type : DungeonType.values())
                        output.accept(BigKeyItem.forDungeon(ITEM_BY_ID.get("big_key").get(), type.id()));
                    if (path.equals("blocks")) output.accept(PedestalItem.withInsertedSword(
                            PEDESTAL_ITEM.get(), new ItemStack(MASTER_SWORD.get())));
                }).build());
    }

    private static RegistryObject<SoundEvent> sound(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private static Map.Entry<ResourceLocation, RegistryObject<SoundEvent>> songSound(String path) {
        return Map.entry(ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path), sound("song." + path));
    }
}
