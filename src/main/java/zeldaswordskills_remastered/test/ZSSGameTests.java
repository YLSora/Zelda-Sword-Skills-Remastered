package zeldaswordskills_remastered.test;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import zeldaswordskills_remastered.item.ProgressionItem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.*;
import zeldaswordskills_remastered.block.entity.PedestalBlockEntity;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.BasicSwordSkill;
import zeldaswordskills_remastered.combat.PlayerCombatState;
import zeldaswordskills_remastered.combat.TargetingService;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.event.ZSSCombatEvents;
import zeldaswordskills_remastered.network.CombatStateMessage;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.SongIntentMessage;
import zeldaswordskills_remastered.network.SongStateMessage;
import zeldaswordskills_remastered.network.TargetIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.entity.projectile.SwordBeam;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.entity.projectile.ZeldaArrow;
import zeldaswordskills_remastered.item.InstrumentItem;
import zeldaswordskills_remastered.item.EquipmentItem;
import zeldaswordskills_remastered.item.StageNineToolItem;
import zeldaswordskills_remastered.item.ZeldaCombatItems;
import zeldaswordskills_remastered.item.SpiritCrystalItem;
import zeldaswordskills_remastered.item.MasterOreItem;
import zeldaswordskills_remastered.item.PedestalItem;
import zeldaswordskills_remastered.entity.npc.QuestNpc;
import zeldaswordskills_remastered.entity.LegacyCreature;
import zeldaswordskills_remastered.entity.ChuCreature;
import zeldaswordskills_remastered.entity.CreatureSpawnRules;
import zeldaswordskills_remastered.entity.DarknutCreature;
import zeldaswordskills_remastered.entity.DekuCreature;
import zeldaswordskills_remastered.entity.ElementalDamage;
import zeldaswordskills_remastered.entity.FairyCreature;
import zeldaswordskills_remastered.entity.KeeseCreature;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.entity.OctorokCreature;
import zeldaswordskills_remastered.entity.SkulltulaCreature;
import zeldaswordskills_remastered.entity.WizzrobeCreature;
import zeldaswordskills_remastered.entity.ZSSDamageSources;
import zeldaswordskills_remastered.entity.ZssNaturalSpawner;
import zeldaswordskills_remastered.data.DungeonStructureDataProvider;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.quest.QuestStates;
import zeldaswordskills_remastered.menu.PedestalMenu;
import zeldaswordskills_remastered.song.PlayerSongState;
import zeldaswordskills_remastered.song.ScarecrowStructure;
import zeldaswordskills_remastered.song.SongCatalog;
import zeldaswordskills_remastered.song.SongDefinition;
import zeldaswordskills_remastered.song.SongNote;
import zeldaswordskills_remastered.song.SongService;
import zeldaswordskills_remastered.world.ZSSWorldData;
import zeldaswordskills_remastered.worldgen.DungeonController;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSGameTests {
    private ZSSGameTests() {
    }

    @GameTest(template = "dungeon_empty", templateNamespace = "minecraft")
    public static void bootstrap(GameTestHelper helper) {
        ResourceLocation masterOreId = ForgeRegistries.ITEMS.getKey(ZSSRegistries.MASTER_ORE.get());
        ResourceLocation swordCutId = ForgeRegistries.SOUND_EVENTS.getKey(ZSSRegistries.SWORD_CUT.get());
        Registry<CreativeModeTab> creativeTabs = helper.getLevel().registryAccess().registryOrThrow(Registries.CREATIVE_MODE_TAB);
        List<ResourceLocation> expectedTabs = List.of("skills", "keys", "tools", "combat", "masks", "treasures", "blocks", "spawn_eggs")
                .stream().map(ZSSGameTests::id).toList();
        List<ResourceLocation> actualTabs = List.of(
                ZSSRegistries.SKILLS_TAB, ZSSRegistries.KEYS_TAB, ZSSRegistries.TOOLS_TAB, ZSSRegistries.COMBAT_TAB,
                ZSSRegistries.MASKS_TAB, ZSSRegistries.TREASURES_TAB, ZSSRegistries.BLOCKS_TAB, ZSSRegistries.SPAWN_EGGS_TAB)
                .stream().map(tab -> creativeTabs.getKey(tab.get())).toList();

        helper.assertTrue(id("master_ore").equals(masterOreId), "Master Ore registry ID changed");
        helper.assertTrue(ZSSRegistries.MASTER_ORE.get() instanceof MasterOreItem,
                "Master Ore lost its blacksmith tempering interaction");
        helper.assertTrue(id("sword_cut").equals(swordCutId), "Sword Cut sound registry ID changed");
        helper.assertTrue(id("secret_medley").equals(ForgeRegistries.SOUND_EVENTS.getKey(ZSSRegistries.SECRET_MEDLEY.get())),
                "Song recognition sound registry ID changed");
        Map<String, net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent>> presentationSounds = Map.of(
                "hookshot", ZSSRegistries.HOOKSHOT_SOUND, "boomerang", ZSSRegistries.BOOMERANG_SOUND,
                "magic_failure", ZSSRegistries.MAGIC_FAILURE,
                "magic_fire", ZSSRegistries.MAGIC_FIRE, "magic_ice", ZSSRegistries.MAGIC_ICE,
                "whip", ZSSRegistries.WHIP_SOUND, "whirlwind", ZSSRegistries.WHIRLWIND);
        presentationSounds.forEach((path, sound) -> helper.assertTrue(
                id(path).equals(ForgeRegistries.SOUND_EVENTS.getKey(sound.get())),
                "Presentation sound registry ID changed: " + path));
        helper.assertTrue(expectedTabs.equals(actualTabs), "Creative tab registry IDs changed");
        helper.assertTrue(ZSSRegistries.allItems().size() == 255, "Expected all 255 item registry entries, found " + ZSSRegistries.allItems().size());
        helper.assertTrue(ZSSRegistries.ITEMS.getEntries().size() == 255, "Deferred item registry is incomplete: " + ZSSRegistries.ITEMS.getEntries().size());
        List<RegistryObject<net.minecraft.world.item.Item>> categorizedItems = List.of(
                ZSSRegistries.SKILL_ITEMS, ZSSRegistries.KEY_ITEMS, ZSSRegistries.TOOL_ITEMS, ZSSRegistries.COMBAT_ITEMS,
                ZSSRegistries.MASK_ITEMS, ZSSRegistries.TREASURE_ITEMS, ZSSRegistries.BLOCK_ITEMS, ZSSRegistries.SPAWN_EGG_ITEMS,
                ZSSRegistries.MUSIC_DISC_ITEMS)
                .stream().flatMap(List::stream).toList();
        helper.assertTrue(new HashSet<>(categorizedItems).equals(new HashSet<>(ZSSRegistries.allItems())), "Creative categories are incomplete");
        helper.assertTrue(new HashSet<>(categorizedItems).size() == categorizedItems.size(), "An item appears in more than one creative category");
        helper.assertTrue(ZSSRegistries.BLOCKS.getEntries().size() == 57, "Missing modern blocks");
        helper.assertTrue(ZSSRegistries.BLOCK_ITEMS.size() == 55, "Every stage-nine block needs exactly one block item");
        List<String> expectedBlockIds = List.of("secret_room_core", "navi_light", "beam_wooden", "bomb_flower", "ceramic_jar", "chest_invisible", "chest_locked",
                "door_boss_desert", "door_boss_earth", "door_boss_fire", "door_boss_forest", "door_boss_ice",
                "door_boss_water", "door_boss_end", "door_locked", "dungeon_core_stone", "dungeon_core_sandstone", "dungeon_stone_stone",
                "dungeon_stone_sandstone", "gossip_stone", "light_block", "heavy_block", "hook_target", "hook_target_all",
                "inscription", "lever_giant", "peg_wooden", "peg_rusty", "pedestal", "quake_stone", "quake_stone_mossy",
                "sacred_flame_din", "sacred_flame_farore", "sacred_flame_nayru",
                "secret_stone_stone", "secret_stone_sandstone", "secret_stone_nether_bricks", "secret_stone_stone_bricks",
                "secret_stone_mossy_cobblestone", "secret_stone_ice", "secret_stone_cobblestone", "secret_stone_end_stone", "secret_stone_nether_wart_block",
                "time_block", "royal_block", "warp_stone_bolero", "warp_stone_minuet", "warp_stone_prelude",
                "warp_stone_oath", "warp_stone_nocturne", "warp_stone_requiem", "warp_stone_serenade", "ancient_tablet_bombos",
                "ancient_tablet_ether", "ancient_tablet_quake", "secret_stone_purpur_block", "secret_stone_end_stone_bricks");
        List<String> actualBlockIds = ZSSRegistries.BLOCKS.getEntries().stream().map(block -> block.getId().getPath()).toList();
        helper.assertTrue(expectedBlockIds.equals(actualBlockIds), "A stage-nine block registry ID changed");
        helper.assertTrue(id("pedestal").equals(ForgeRegistries.BLOCKS.getKey(ZSSRegistries.PEDESTAL.get())), "Pedestal block registry ID changed");
        helper.assertTrue(ZSSRegistries.PEDESTAL_ITEM.get() instanceof PedestalItem
                        && ((net.minecraft.world.item.BlockItem) ZSSRegistries.PEDESTAL_ITEM.get()).getBlock() == ZSSRegistries.PEDESTAL.get(),
                "Pedestal item does not place the existing pedestal block");
        BlockPos insertedPedestalBase = new BlockPos(16, 1, 16);
        helper.setBlock(insertedPedestalBase, Blocks.STONE);
        helper.setBlock(insertedPedestalBase.above(), Blocks.AIR);
        BlockPos insertedPedestalPos = helper.absolutePos(insertedPedestalBase.above());
        Player pedestalPlayer = helper.makeMockSurvivalPlayer();
        pedestalPlayer.setPos(insertedPedestalPos.getX() + 0.5D, insertedPedestalPos.getY() + 1.0D,
                insertedPedestalPos.getZ() + 3.0D);
        ItemStack insertedStack = PedestalItem.withInsertedSword(ZSSRegistries.PEDESTAL_ITEM.get(),
                new ItemStack(ZSSRegistries.TEMPERED_SWORD.get()));
        pedestalPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, insertedStack);
        net.minecraft.world.InteractionResult placement = ((net.minecraft.world.item.BlockItem)
                ZSSRegistries.PEDESTAL_ITEM.get()).place(new net.minecraft.world.item.context.BlockPlaceContext(
                pedestalPlayer, net.minecraft.world.InteractionHand.MAIN_HAND,
                pedestalPlayer.getMainHandItem(), new net.minecraft.world.phys.BlockHitResult(
                        Vec3.atCenterOf(insertedPedestalPos.below()), net.minecraft.core.Direction.UP,
                        insertedPedestalPos.below(), false)));
        helper.assertTrue(placement.consumesAction(), "Inserted sword pedestal placement returned " + placement);
        BlockState insertedState = helper.getLevel().getBlockState(insertedPedestalPos);
        helper.assertTrue(insertedState.is(ZSSRegistries.PEDESTAL.get())
                        && insertedState.getValue(PedestalBlock.PENDANTS) == 0
                        && !insertedState.getValue(PedestalBlock.UNLOCKED)
                        && insertedState.getValue(PedestalBlock.HAS_SWORD),
                "Inserted pedestal did not synchronize its block properties: " + insertedState);
        CompoundTag insertedItemTag = insertedStack.getTag().getCompound("BlockEntityTag");
        helper.assertTrue(insertedItemTag.getBoolean(PedestalBlockEntity.HAS_SWORD_TAG)
                        && insertedItemTag.getInt("pendants") == 0
                        && !insertedItemTag.getBoolean("unlocked"),
                "Creative inserted pedestal has incorrect initial BlockEntityTag state");
        net.minecraft.world.level.block.entity.BlockEntity placedPedestal = helper.getLevel().getBlockEntity(insertedPedestalPos);
        helper.assertTrue(placedPedestal instanceof PedestalBlockEntity pedestal
                        && pedestal.sword().getItem() == ZSSRegistries.TEMPERED_SWORD.get()
                        && placedPedestal.saveWithoutMetadata().getBoolean(PedestalBlockEntity.HAS_SWORD_TAG)
                        && ItemStack.of(placedPedestal.saveWithoutMetadata().getCompound(PedestalBlockEntity.SWORD_TAG))
                        .is(ZSSRegistries.TEMPERED_SWORD.get()),
                "Inserted pedestal did not persist the exact sword item stack");
        ItemStack clone = ZSSRegistries.PEDESTAL.get().getCloneItemStack(insertedState,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(insertedPedestalPos), net.minecraft.core.Direction.UP,
                        insertedPedestalPos, false), helper.getLevel(), insertedPedestalPos, pedestalPlayer);
        helper.assertTrue(clone.is(ZSSRegistries.PEDESTAL_ITEM.get())
                        && clone.getTag() != null
                        && clone.getTag().getCompound("BlockEntityTag").getBoolean(PedestalBlockEntity.HAS_SWORD_TAG),
                "Middle-click did not copy the ordinary pedestal with its state");
        helper.assertTrue(ZSSGameTests.class.getClassLoader().getResource("assets/zeldaswordskills_remastered/models/block/pedestal_0.json") != null,
                "Pedestal base model is missing");
        helper.assertTrue(id("pedestal").equals(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(ZSSRegistries.PEDESTAL_BLOCK_ENTITY.get())), "Pedestal block entity registry ID changed");
        helper.assertTrue(id("pedestal").equals(ForgeRegistries.MENU_TYPES.getKey(ZSSRegistries.PEDESTAL_MENU.get())), "Pedestal menu registry ID changed");
        helper.assertTrue(id("stun").equals(ForgeRegistries.MOB_EFFECTS.getKey(ZSSRegistries.STUN.get())), "Stun effect registry ID changed");
        helper.assertTrue(ZSSGameTests.class.getClassLoader().getResource("assets/zeldaswordskills_remastered/textures/mob_effect/stun.png") != null,
                "Stun effect texture is missing");
        helper.assertTrue(id("max_magic").equals(ForgeRegistries.ATTRIBUTES.getKey(ZSSRegistries.MAX_MAGIC.get())), "Max Magic attribute registry ID changed");
        helper.assertTrue(id("rock").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.ROCK.get())), "Rock entity registry ID changed");
        helper.assertTrue(id("sword_beam").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.SWORD_BEAM.get())), "Sword Beam entity registry ID changed");
        helper.assertTrue(id("bomb").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.BOMB.get())), "Bomb entity registry ID changed");
        helper.assertTrue(id("boomerang").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.BOOMERANG.get()))
                        && id("seedshot").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.SEEDSHOT.get()))
                        && id("magic_spell").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.MAGIC_SPELL.get()))
                        && id("cyclone").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.CYCLONE_PROJECTILE.get()))
                        && id("hookshot").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.HOOKSHOT.get()))
                        && id("whip").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.WHIP.get()))
                        && id("arrow_custom").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.ARROW_CUSTOM.get()))
                        && id("arrow_bomb").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.ARROW_BOMB.get()))
                        && id("arrow_elemental").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.ARROW_ELEMENTAL.get())),
                "A stage-nine tool projectile registry ID changed");
        LivingEntity toolOwner = mockLiving(helper);
        ToolProjectile boomerang = new ToolProjectile(ZSSRegistries.BOOMERANG.get(), helper.getLevel(), toolOwner,
                ToolProjectile.Mode.BOOMERANG);
        ToolProjectile hookshot = new ToolProjectile(ZSSRegistries.HOOKSHOT.get(), helper.getLevel(), toolOwner,
                ToolProjectile.Mode.HOOKSHOT);
        ToolProjectile whip = new ToolProjectile(ZSSRegistries.WHIP.get(), helper.getLevel(), toolOwner,
                ToolProjectile.Mode.WHIP);
        helper.assertTrue(boomerang.isNoGravity() && boomerang.renderStack().is(ZSSRegistries.getItem("boomerang"))
                        && hookshot.isNoGravity() && ToolProjectile.Mode.HOOKSHOT.isTethered()
                        && whip.isNoGravity() && ToolProjectile.Mode.WHIP.isTethered(),
                "Animated boomerang or tether projectile semantics changed");
        helper.assertTrue(id("goron").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.GORON.get()))
                        && id("npc/zelda").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.ZELDA.get()))
                        && id("npc/mask_trader").equals(ForgeRegistries.ENTITY_TYPES.getKey(ZSSRegistries.MASK_TRADER.get())),
                "Stage-eight quest NPC registry IDs changed");
        helper.assertTrue(ZSSRegistries.LEGACY_CREATURE_TYPES.size() == 29, "The non-NPC living entity variants were not registered");
        helper.assertTrue(ZSSRegistries.SPAWN_EGG_ITEMS.size() == 31, "All thirty-one living entity spawn eggs were not registered");
        helper.assertTrue(ZSSRegistries.SPAWN_EGGS_TAB.get().getIconItem().is(ZSSRegistries.ZELDA_SPAWN_EGG.get()),
                "The spawn egg creative tab does not use Zelda's spawn egg icon");
        List<String> expectedCreatureIds = List.of("darknut", "darknut_mighty", "fairy", "navi", "chu", "chu_green",
                "chu_blue", "chu_yellow", "baba_deku", "baba_fire", "baba_withered", "keese", "keese_fire",
                "keese_ice", "keese_thunder", "keese_cursed", "octorok", "octorok_bomb", "skulltula",
                "skulltula_gold", "wizzrobe", "wizzrobe_ice", "wizzrobe_lightning", "wizzrobe_wind",
                "darknut_boss", "wizzrobe_grand", "fire_boss", "forest_boss", "water_boss");
        List<String> actualCreatureIds = ZSSRegistries.LEGACY_CREATURE_TYPES.stream()
                .map(type -> ForgeRegistries.ENTITY_TYPES.getKey(type.get()).getPath()).toList();
        helper.assertTrue(expectedCreatureIds.equals(actualCreatureIds), "A non-NPC living entity registry ID changed");
        for (int index = 0; index < ZSSRegistries.LEGACY_CREATURE_TYPES.size(); index++) {
            LegacyCreature creature = ZSSRegistries.LEGACY_CREATURE_TYPES.get(index).get().create(helper.getLevel());
            helper.assertTrue(creature != null && creature.kind().registryPath().equals(expectedCreatureIds.get(index)),
                    "A spawn egg entity factory created the wrong creature kind");
            helper.assertTrue(specializedCreature(creature),
                    "A creature registry entry still uses the inert LegacyCreature implementation: " + creature.kind());
            if (creature instanceof KeeseCreature) {
                helper.assertTrue(Math.abs(creature.getBbWidth() - 0.5F) < 0.001F
                                && Math.abs(creature.getBbHeight() - 0.9F) < 0.001F
                                && creature.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED) >= 0.5D,
                        "Keese collision dimensions or flying speed are incorrect");
            }
            if (creature instanceof ChuCreature chu) {
                float baseWidth = chu.getBbWidth();
                float baseHeight = chu.getBbHeight();
                double smallAttackDamage = chu.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                helper.assertTrue(Math.abs(baseWidth - 0.3F) < 0.001F && Math.abs(baseHeight - 0.3F) < 0.001F,
                        "Chu collision dimensions are not half the material base size");
                chu.setSize(2, false);
                helper.assertTrue(chu.getSize() == 2
                                && Math.abs(chu.getBbWidth() - baseWidth * 2.0F) < 0.001F
                                && Math.abs(chu.getBbHeight() - baseHeight * 2.0F) < 0.001F
                                && smallAttackDamage > 0.0D
                                && Math.abs(smallAttackDamage * 2.0D
                                - chu.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) < 0.001D,
                        "Chu size changes did not refresh its collision dimensions or attack damage");
                chu.setSize(1, false);
                if (chu.kind() == LegacyCreature.Kind.CHU_RED) {
                    Player player = helper.makeMockSurvivalPlayer();
                    float healthBeforeTouch = player.getHealth();
                    chu.playerTouch(player);
                    helper.assertTrue(player.getHealth() < healthBeforeTouch,
                            "A small Chu did not damage a player on contact");
                }
            }
            helper.assertTrue(creature.kind() == LegacyCreature.Kind.FIRE_BOSS
                            || creature.kind() == LegacyCreature.Kind.FOREST_BOSS
                            || ZSSGameTests.class.getClassLoader().getResource("assets/zeldaswordskills_remastered/textures/entity/"
                            + creature.kind().texturePath() + ".png") != null,
                    "A creature variant references a missing texture: " + creature.kind().texturePath());
            if (creature.kind() != LegacyCreature.Kind.FIRE_BOSS
                    && creature.kind() != LegacyCreature.Kind.FOREST_BOSS && creature.kind() != LegacyCreature.Kind.WATER_BOSS) {
                helper.assertTrue(ZSSRegistries.SPAWN_EGG_ITEMS.get(index).get() instanceof ForgeSpawnEggItem egg
                                && egg.getType(null) == ZSSRegistries.LEGACY_CREATURE_TYPES.get(index).get(),
                        "A creature variant spawn egg points at the wrong entity type");
            }
        }
        helper.assertTrue(id("cyclone").equals(ForgeRegistries.PARTICLE_TYPES.getKey(ZSSRegistries.CYCLONE.get())), "Cyclone particle registry ID changed");
        helper.assertTrue(ZSSGameTests.class.getClassLoader().getResource("assets/zeldaswordskills_remastered/particles/cyclone.json") != null,
                "Cyclone particle description is missing");
        helper.assertTrue(id("warp_stone_minuet").equals(ForgeRegistries.BLOCKS.getKey(ZSSRegistries.WARP_STONE_MINUET.get())), "Warp Stone registry ID changed");
        helper.assertTrue(ZSSRegistries.BOSS_DOORS.size() == 7 && ZSSRegistries.SACRED_FLAMES.size() == 3
                        && ZSSRegistries.SECRET_STONES.size() == 11 && ZSSRegistries.WARP_STONES.size() == 7,
                "A metadata-era block family was not fully split into semantic IDs");
        helper.assertTrue(ZSSGameTests.class.getClassLoader().getResource("assets/zeldaswordskills_remastered/textures/gui/gui_pedestal.png") != null,
                "The Master Sword Pedestal GUI texture is missing");
        helper.assertTrue(ZSSGameTests.class.getClassLoader().getResource("assets/zeldaswordskills_remastered/textures/gui/magic_meter_horizontal.png") != null,
                "The magic meter texture is missing");
        helper.assertTrue(id("throwing_rock").equals(ForgeRegistries.RECIPE_SERIALIZERS.getKey(ZSSRegistries.THROWING_ROCK_RECIPE.get())), "Throwing Rock recipe serializer ID changed");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id("throwing_rock")).isPresent(), "Throwing Rock recipe was not loaded");
        helper.assertTrue(id("heart_piece").equals(ForgeRegistries.RECIPE_SERIALIZERS.getKey(ZSSRegistries.HEART_PIECE_RECIPE.get()))
                        && helper.getLevel().getRecipeManager().byKey(id("heart_piece")).isPresent(),
                "Four-piece Bonus Heart recipe was not loaded");
        helper.assertTrue(id("hookshot_upgrade").equals(ForgeRegistries.RECIPE_SERIALIZERS.getKey(ZSSRegistries.HOOKSHOT_UPGRADE_RECIPE.get()))
                        && helper.getLevel().getRecipeManager().byKey(id("hookshot_extender")).isPresent()
                        && helper.getLevel().getRecipeManager().byKey(id("hookshot_claw_upgrade")).isPresent()
                        && helper.getLevel().getRecipeManager().byKey(id("hookshot_multi_upgrade")).isPresent(),
                "Hookshot upgrade recipes were not loaded");
        helper.assertTrue(ZSSRegistries.MASTER_SWORD.get() instanceof SwordItem, "Master Sword is not usable as a sword");
        helper.assertTrue(ZSSRegistries.HYLIAN_SHIELD.get() instanceof ShieldItem, "Hylian Shield is not usable for blocking");
        helper.assertTrue(ZSSRegistries.MASTER_SWORD.get() instanceof ZeldaCombatItems.Sword sword && sword.masterSword()
                        && ZSSRegistries.BIGGORON_SWORD.get() instanceof ZeldaCombatItems.Sword twoHanded && twoHanded.twoHanded(),
                "Sword master/two-handed semantics were not registered");
        helper.assertTrue(ZSSRegistries.getItem("hookshot") instanceof StageNineToolItem
                        && ZSSRegistries.getItem("majora_mask") instanceof EquipmentItem
                        && ZSSRegistries.getItem("hero_tunic_chestplate") instanceof EquipmentItem,
                "Stage-nine tool, mask, or armor behavior is still a base Item");
        helper.assertTrue(!ZSSRegistries.getItem("skeleton_key").canBeDepleted(),
                "Skeleton Key is still breakable");
        helper.assertTrue(ZSSRegistries.getItem("empty_spirit_crystal") instanceof SpiritCrystalItem empty
                        && empty.kind() == SpiritCrystalItem.Kind.EMPTY
                        && ZSSRegistries.getItem("din_crystal") instanceof SpiritCrystalItem din
                        && din.kind() == SpiritCrystalItem.Kind.DIN && !din.canBeDepleted(),
                "Spirit Crystal charge type or unbreakable behavior changed");
        helper.assertTrue(ZSSRegistries.getItem("hero_bow") instanceof ZeldaCombatItems.HeroBow heroBow
                        && heroBow.getAllSupportedProjectiles().test(new ItemStack(ZSSRegistries.getItem("bomb_arrow")))
                        && heroBow.getAllSupportedProjectiles().test(new ItemStack(ZSSRegistries.getItem("fire_arrow")))
                        && ZSSRegistries.getItem("water_bomb_arrow") instanceof ZeldaCombatItems.ElementArrow waterArrow
                        && waterArrow.kind() == ZeldaCombatItems.ArrowKind.WATER_BOMB,
                "Hero Bow no longer auto-selects Zelda arrow ammunition");
        helper.assertTrue(!ZSSRegistries.getItem("master_sword").canBeDepleted()
                        && !ZSSRegistries.getItem("deku_shield").canBeDepleted()
                        && !ZSSRegistries.getItem("hero_tunic_chestplate").canBeDepleted()
                        && !ZSSRegistries.getItem("hookshot").canBeDepleted()
                        && !ZSSRegistries.getItem("magic_mirror").canBeDepleted(),
                "A ZSS weapon, shield, armor, tool, or utility item remains breakable");
        helper.assertTrue(((ZeldaCombatItems.ElementArrow) ZSSRegistries.getItem("fire_bomb_arrow"))
                        .createArrow(helper.getLevel(), new ItemStack(ZSSRegistries.getItem("fire_bomb_arrow")), mockLiving(helper))
                        .getType() == ZSSRegistries.ARROW_BOMB.get()
                        && ((ZeldaCombatItems.ElementArrow) ZSSRegistries.getItem("ice_arrow"))
                        .createArrow(helper.getLevel(), new ItemStack(ZSSRegistries.getItem("ice_arrow")), mockLiving(helper))
                        .getType() == ZSSRegistries.ARROW_ELEMENTAL.get(),
                "Zelda ammunition created the wrong persistent arrow entity family");
        helper.assertTrue(((InstrumentItem) ZSSRegistries.FAIRY_OCARINA.get()).songStrength() == 1,
                "Fairy Ocarina song strength changed");
        helper.assertTrue(((InstrumentItem) ZSSRegistries.OCARINA_OF_TIME.get()).songStrength() == 5
                        && ((InstrumentItem) ZSSRegistries.GODDESS_HARP.get()).songStrength() == 5,
                "Strong instrument song strength changed");

        ToolProjectile fireSpell = new ToolProjectile(ZSSRegistries.MAGIC_SPELL.get(), helper.getLevel(), toolOwner,
                ToolProjectile.Mode.FIRE);
        ToolProjectile iceSpell = new ToolProjectile(ZSSRegistries.MAGIC_SPELL.get(), helper.getLevel(), toolOwner,
                ToolProjectile.Mode.ICE);
        helper.assertTrue(ElementalDamage.from(ZSSDamageSources.toolProjectile(helper.getLevel(), fireSpell.mode(), fireSpell, toolOwner))
                        == ElementalDamage.Element.FIRE
                        && ElementalDamage.from(ZSSDamageSources.toolProjectile(helper.getLevel(), iceSpell.mode(), iceSpell, toolOwner))
                        == ElementalDamage.Element.ICE,
                "Elemental projectiles did not use their data-driven damage types");

        helper.assertTrue(SongCatalog.all().size() == 15, "The fixed song catalog is incomplete");
        SongDefinition healing = SongCatalog.get(ZSSContentIds.HEALING).orElseThrow();
        helper.assertTrue(healing.minimumDuration() == 78 && healing.notes().equals(List.of(
                SongNote.B2, SongNote.A2, SongNote.F1, SongNote.B2, SongNote.A2, SongNote.F1)),
                "Song of Healing definition changed");
        helper.assertTrue(SongCatalog.isUniqueScarecrowMelody(List.of(SongNote.D1, SongNote.D2, SongNote.D1,
                SongNote.D2, SongNote.F1, SongNote.A2, SongNote.B2, SongNote.F1)),
                "A valid custom scarecrow melody was rejected");
        helper.assertTrue(!SongCatalog.isUniqueScarecrowMelody(List.of(SongNote.B2, SongNote.A2, SongNote.F1,
                SongNote.B2, SongNote.A2, SongNote.F1, SongNote.D1, SongNote.D2)),
                "A scarecrow melody conflicting with a fixed song was accepted");
        PlayerSongState songState = new PlayerSongState();
        songState.open(id("ocarina_of_time"), false);
        helper.assertTrue(songState.addNote(SongNote.D1) && songState.addNote(SongNote.F1)
                        && songState.addNote(SongNote.F1)
                        && songState.notes().equals(List.of(SongNote.D1, SongNote.F1, SongNote.F1)),
                "Song input lost ordered or repeated notes");
        songState.match(ZSSContentIds.TIME, 111L);
        helper.assertTrue(!songState.addNote(SongNote.A2) && songState.completionDeadline() == 111L,
                "Song input continued after a completed melody matched");
        PlayerSongState pagedHud = new PlayerSongState();
        pagedHud.open(id("ocarina_of_time"), false);
        for (int index = 0; index < 8; index++) pagedHud.addNote(SongNote.D1);
        helper.assertTrue(pagedHud.hudNotes().size() == 8, "Song HUD did not fill all eight note slots");
        pagedHud.addNote(SongNote.D2);
        helper.assertTrue(pagedHud.hudNotes().equals(List.of(SongNote.D2)),
                "The ninth note did not clear the previous HUD page and return to slot one");
        pagedHud.waitForRecognition(98L);
        helper.assertTrue(pagedHud.recognitionDeadline() == 98L,
                "Song recognition did not retain its three-second deadline");
        pagedHud.match(ZSSContentIds.TIME, 200L);
        helper.assertTrue(pagedHud.recognitionDeadline() == 0L,
                "A matched song retained its not-recognized deadline");

        ZSSPlayerData playerData = new ZSSPlayerData();
        playerData.setMagic(5000.0F, 5000.0F);
        playerData.setSkillLevel(id("sword_basic"), 5);
        ZSSPlayerData loadedPlayerData = new ZSSPlayerData();
        loadedPlayerData.load(playerData.save());
        helper.assertTrue(loadedPlayerData.currentMagic() == 200.0F && loadedPlayerData.maxMagic() == 200.0F,
                "Player magic was not clamped to the configured hard maximum");
        helper.assertTrue(loadedPlayerData.skills().getOrDefault(id("sword_basic"), 0) == 5, "Player skill levels did not survive serialization");
        ZSSPlayerData itemData = new ZSSPlayerData();
        itemData.setMagic(10.0F, 100.0F);
        helper.assertTrue(itemData.restoreMagic(20.0F) && itemData.currentMagic() == 30.0F
                        && itemData.increaseMaxMagic(10.0F) && itemData.currentMagic() == 110.0F && itemData.maxMagic() == 110.0F,
                "Magic pickup/container arithmetic changed");
        itemData.setSkillLevel(ZSSContentIds.DASH, 1);
        helper.assertTrue(itemData.clearSkills() && itemData.skills().isEmpty() && itemData.addSkulltulaToken()
                        && itemData.skulltulaTokens() == 1,
                "Skill Wiper or Skulltula Token progression mutation failed");
        playerData.setSkillLevel(id("helm_splitter"), 10);
        helper.assertTrue(playerData.skills().getOrDefault(id("helm_splitter"), 0) == 5, "Ordinary phase-six skills exceeded level five");
        playerData.combat().setTarget(123);
        playerData.combat().recordHit(123, 4.0F, 3, 99L);
        playerData.load(playerData.save());
        helper.assertTrue(playerData.combat().targetId() == -1 && playerData.combat().comboCount() == 0,
                "Transient combat state was persisted");
        UUID horseId = UUID.fromString("be36a513-6005-456c-921a-0959def3bc97");
        List<SongNote> scarecrowMelody = List.of(SongNote.D1, SongNote.D2, SongNote.D1, SongNote.D2,
                SongNote.F1, SongNote.A2, SongNote.B2, SongNote.F1);
        playerData.learnSong(ZSSContentIds.HEALING);
        playerData.learnSong(ZSSContentIds.SCARECROW);
        playerData.setScarecrowMelody(scarecrowMelody, 168000L);
        playerData.setWarpPoint(ZSSContentIds.BOLERO, Level.OVERWORLD.location(), new BlockPos(5, 64, 7));
        playerData.setHealingCooldownUntil(24000L);
        playerData.setHorse(horseId, new ChunkPos(3, -2).toLong());
        ZSSPlayerData loadedSongData = new ZSSPlayerData();
        loadedSongData.load(playerData.save());
        helper.assertTrue(loadedSongData.songs().containsAll(Set.of(ZSSContentIds.HEALING, ZSSContentIds.SCARECROW))
                        && loadedSongData.scarecrowNotes().equals(scarecrowMelody.stream().map(Enum::name).toList())
                        && loadedSongData.scarecrowConfirmedAt() == 168000L,
                "Learned and custom songs did not survive serialization");
        helper.assertTrue(loadedSongData.warpPoints().get(ZSSContentIds.BOLERO).pos().equals(new BlockPos(5, 64, 7))
                        && loadedSongData.healingCooldownUntil() == 24000L
                        && loadedSongData.horseUuid().orElseThrow().equals(horseId),
                "Song destinations and cooldown state did not survive serialization");
        playerData.setQuestProgress(ZSSContentIds.ZELDA_TALK,
                zeldaswordskills_remastered.quest.QuestStates.OCARINA_HELD, 0, 42L, java.util.Map.of("performance", 1));
        playerData.setEscrowOcarina(new ItemStack(ZSSRegistries.FAIRY_OCARINA.get()));
        playerData.setBorrowedMask(id("keaton_mask"));
        ZSSPlayerData loadedQuestData = new ZSSPlayerData();
        loadedQuestData.load(playerData.save());
        helper.assertTrue(loadedQuestData.quests().get(ZSSContentIds.ZELDA_TALK).state()
                        .equals(zeldaswordskills_remastered.quest.QuestStates.OCARINA_HELD)
                        && loadedQuestData.escrowOcarina().is(ZSSRegistries.FAIRY_OCARINA.get())
                        && loadedQuestData.borrowedMask().orElseThrow().equals(id("keaton_mask")),
                "Quest state, borrowed mask, or Fairy Ocarina escrow did not survive serialization");
        net.minecraft.world.entity.player.Player mockPlayer = helper.makeMockPlayer();
        helper.assertTrue(ZSSCapabilities.get(mockPlayer).isPresent(), "Player data capability was not attached");
        helper.assertTrue(mockPlayer.getAttribute(ZSSRegistries.MAX_MAGIC.get()) != null, "Maximum magic attribute was not attached to players");

        ZSSWorldData worldData = ZSSWorldData.get(helper.getLevel());
        worldData.setWeatherSongCooldownUntil(1234L);
        worldData.setDungeonState(id("test_dungeon"), true, 5678L);
        ZSSWorldData loadedWorldData = ZSSWorldData.load(worldData.save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(loadedWorldData.weatherSongCooldownUntil() == 1234L, "World song cooldown did not survive serialization");
        helper.assertTrue(loadedWorldData.dungeons().get(id("test_dungeon")).completed(), "Dungeon completion did not survive serialization");

        FriendlyByteBuf validIntentBuffer = new FriendlyByteBuf(Unpooled.buffer());
        SkillIntentMessage validIntent = new SkillIntentMessage(id("sword_basic"), SkillIntentMessage.Action.BEGIN,
                Optional.of(new Vec3(1.0D, 0.0D, 0.0D)));
        SkillIntentMessage.encode(validIntent, validIntentBuffer);
        helper.assertTrue(validIntent.equals(SkillIntentMessage.decode(validIntentBuffer)), "Valid skill intent did not round-trip");
        FriendlyByteBuf invalidIntentBuffer = new FriendlyByteBuf(Unpooled.buffer());
        invalidIntentBuffer.writeUtf(id("sword_basic").toString(), 128);
        invalidIntentBuffer.writeByte(99);
        invalidIntentBuffer.writeBoolean(false);
        boolean invalidRejected = false;
        try {
            SkillIntentMessage.decode(invalidIntentBuffer);
        } catch (DecoderException expected) {
            invalidRejected = true;
        }
        helper.assertTrue(invalidRejected, "Invalid skill action was accepted by the network decoder");

        FriendlyByteBuf songIntentBuffer = new FriendlyByteBuf(Unpooled.buffer());
        SongIntentMessage songIntent = new SongIntentMessage(SongIntentMessage.Action.NOTE, Optional.of(SongNote.D2), 0);
        SongIntentMessage.encode(songIntent, songIntentBuffer);
        helper.assertTrue(songIntent.equals(SongIntentMessage.decode(songIntentBuffer)), "Valid song intent did not round-trip");
        FriendlyByteBuf invalidSongIntent = new FriendlyByteBuf(Unpooled.buffer());
        invalidSongIntent.writeByte(SongIntentMessage.Action.NOTE.ordinal());
        invalidSongIntent.writeByte(99);
        helper.assertTrue(rejectsInvalidSongIntent(invalidSongIntent), "Invalid song note was accepted by the network decoder");
        FriendlyByteBuf songStateBuffer = new FriendlyByteBuf(Unpooled.buffer());
        SongStateMessage songMessage = new SongStateMessage(SongStateMessage.Status.MATCHED,
                healing.notes(), Optional.of(ZSSContentIds.HEALING), 200L, false);
        SongStateMessage.encode(songMessage, songStateBuffer);
        helper.assertTrue(songMessage.equals(SongStateMessage.decode(songStateBuffer)), "Song HUD state did not round-trip");

        PlayerCombatState combat = new PlayerCombatState();
        combat.recordHit(42, 4.0F, 3, 100L);
        helper.assertTrue(combat.comboCount() == 1 && combat.nextDamageBonus() == 1 && !combat.comboFinished(),
                "The first combo hit produced invalid state");
        combat.recordHit(42, 5.0F, 3, 110L);
        combat.recordHit(42, 6.0F, 3, 120L);
        helper.assertTrue(combat.comboCount() == 3 && combat.comboDamage() == 15.0F && combat.comboFinished(),
                "The combo maximum did not finish the chain");
        combat.recordHit(42, 4.0F, 3, 130L);
        helper.assertTrue(combat.comboCount() == 1 && combat.comboDamage() == 4.0F,
                "A completed combo did not start a fresh chain");
        combat.recordHit(43, 5.0F, 3, 140L);
        helper.assertTrue(combat.comboCount() == 1 && combat.comboDamage() == 5.0F,
                "Changing targets carried the previous target's combo forward");
        combat.reset();
        helper.assertTrue(combat.targetId() == -1 && combat.comboCount() == 0, "Combat reset left transient state behind");
        combat.beginCharge(id("spin_attack"), 100L, Vec3.ZERO);
        helper.assertTrue(!combat.charged(id("spin_attack"), 113L, 14) && combat.charged(id("spin_attack"), 114L, 14),
                "Spin charge accepted an early release or rejected its exact deadline");
        combat.startParry(100L, 122L);
        helper.assertTrue(combat.parryActive(100L) && combat.parryActive(121L) && !combat.parryActive(122L),
                "Parry active window boundaries changed");
        helper.assertTrue(!combat.startParry(110L, 140L) && combat.parryUntil() == 122L,
                "Repeated Parry requests refreshed a live guard");
        combat.finishParry(125L);
        helper.assertTrue(combat.parryCoolingDown(181L) && !combat.parryCoolingDown(182L),
                "Timeout must start sixty ticks of cooldown at the original deadline");
        helper.assertTrue(!combat.startParry(181L, 203L) && combat.startParry(182L, 204L),
                "Parry cooldown did not gate the next guard at its exact deadline");
        combat.finishParry(185L);
        helper.assertTrue(!combat.parryActive(185L) && combat.parryCoolingDown(244L) && !combat.parryCoolingDown(245L),
                "Damage must end the guard and start sixty ticks of cooldown immediately");
        helper.assertTrue(!combat.finishParry(190L) && combat.parryCooldownUntil() == 245L,
                "Repeated damage extended the cooldown after the guard ended");
        combat.startSwordBreak(42, 215L);
        combat.reset();
        helper.assertTrue(!combat.parryPending() && !combat.parryCoolingDown(0L) && !combat.swordBreakActive(0L),
                "Combat reset retained Parry or Sword Break state");
        Vec3 dodgeRight = new Vec3(-1.0D, 0.0D, 0.0D);
        helper.assertTrue(!combat.acceptDodgeTap(100L, dodgeRight)
                        && combat.acceptDodgeTap(110L, dodgeRight),
                "Dodge must accept a second same-direction tap at the ten-tick boundary");
        helper.assertTrue(!combat.acceptDodgeTap(120L, dodgeRight)
                        && !combat.acceptDodgeTap(131L, dodgeRight)
                        && !combat.acceptDodgeTap(132L, dodgeRight.reverse()),
                "Dodge accepted a late tap or a change of direction");
        combat.setTarget(42);
        combat.recordHit(42, 4.0F, 5, 150L);
        int[] dodgeCooldowns = {80, 70, 60, 50, 40};
        for (int dodgeLevel = 1; dodgeLevel <= 5; dodgeLevel++) {
            combat.startDodge(140L, dodgeLevel);
            helper.assertTrue(combat.dodgeActive(140L) && combat.dodgeActive(159L) && !combat.dodgeActive(160L),
                    "Dodge immunity must last exactly twenty ticks at every level");
            long deadline = 140L + dodgeCooldowns[dodgeLevel - 1];
            helper.assertTrue(combat.dodgeOnCooldown(deadline - 1) && !combat.dodgeOnCooldown(deadline),
                    "Dodge cooldown must follow the level table from activation");
        }
        helper.assertTrue(combat.targetId() == 42 && combat.comboCount() == 1 && combat.comboDeadline() == 150L,
                "Dodge must preserve the existing combo without adding hits or extending its deadline");
        for (float friction : new float[]{0.6F, 0.98F}) {
            double speed = AdvancedSwordSkills.dodgeImpulseSpeed(friction);
            double drag = friction * 0.91F;
            helper.assertTrue(Math.abs(speed / (1.0D - drag) - 4.0D) < 1.0E-6D,
                    "Dodge impulse must target four blocks of unassisted ground travel");
            // Include vanilla velocity-packet quantization and the per-axis stop threshold.
            double velocity = (int) (speed * 8000.0D) / 8000.0D;
            double distance = 0.0D;
            for (int tick = 0; tick < 100 && Math.abs(velocity) >= 0.003D; tick++) {
                distance += velocity;
                velocity *= drag;
            }
            helper.assertTrue(Math.abs(distance - 4.0D) < 0.03D,
                    "Dodge impulse drifted away from its four-block distance under vanilla friction");
        }
        combat.reset();
        helper.assertTrue(!combat.dodgeActive(141L) && !combat.dodgeOnCooldown(141L)
                        && !combat.acceptDodgeTap(141L, dodgeRight.reverse()),
                "Combat reset left Dodge immunity, cooldown, or a pending tap behind");
        for (int dashLevel = 1; dashLevel <= 5; dashLevel++) {
            helper.assertTrue(AdvancedSwordSkills.dashCooldown(dashLevel) == 100L - 10L * dashLevel,
                    "Dash cooldown must be 100 - 10 x skill level ticks");
        }
        combat.startDash(88, Vec3.ZERO, 100L, 1);
        combat.startDashCooldown(190L);
        helper.assertTrue(combat.dashCoolingDown(100L) && !combat.dashCoolingDown(190L),
                "Dash cooldown window changed");
        helper.assertTrue(combat.registerDashHit(88) && !combat.registerDashHit(88) && combat.registerDashHit(89),
                "A swept Dash must strike each enemy on its path at most once");
        combat.reset();
        helper.assertTrue(!combat.dashCoolingDown(100L) && combat.registerDashHit(88),
                "Combat reset left the Dash cooldown or its hit history behind");
        for (int dashLevel = 1; dashLevel <= 5; dashLevel++) {
            double dashTarget = 3.0D + dashLevel;
            ZSSPlayerData continuousData = new ZSSPlayerData();
            continuousData.setSkillLevel(id("continuous_flash"), dashLevel);
            combat.startDash(88, Vec3.ZERO, 100L, dashLevel, dashLevel);
            helper.assertTrue(combat.dashContinuationRemaining() == dashLevel,
                    "Continuous Dash level " + dashLevel + " must provide one follow-up per level");
            combat.finishDash();
            helper.assertTrue(continuousData.skillMaximum(id("continuous_flash")) == 5
                            && Math.abs(AdvancedSwordSkills.dashTargetDamage(7.0F, dashLevel)
                            - (7.0F + 1.5F * dashLevel)) < 1.0E-6F,
                    "Continuous Dash level " + dashLevel + " must use the same target damage formula as Dash");
            helper.assertTrue(Math.abs(AdvancedSwordSkills.dashDistance(dashLevel) - dashTarget) < 1.0E-9D,
                    "Dash distance must be three blocks plus one per skill level");
            helper.assertTrue(AdvancedSwordSkills.dashDuration(dashLevel) == 8L + 2L * dashLevel,
                    "Dash immunity duration must be 8 + 2 x active skill level ticks");
            for (float friction : new float[]{0.6F, 0.98F}) {
                double dashSpeed = AdvancedSwordSkills.dashImpulseSpeed(dashLevel, friction);
                helper.assertTrue(Math.abs(dashSpeed / (1.0D - friction * 0.91F) - dashTarget) < 1.0E-6D,
                        "Dash impulse must target its own level's ground distance");
                // Include vanilla velocity-packet quantization and the per-axis stop threshold.
                double dashVelocity = (int) (dashSpeed * 8000.0D) / 8000.0D;
                double dashTravel = 0.0D;
                for (int dashTick = 0; dashTick < 400 && Math.abs(dashVelocity) >= 0.003D; dashTick++) {
                    dashTravel += dashVelocity;
                    dashVelocity *= friction * 0.91F;
                }
                helper.assertTrue(Math.abs(dashTravel - dashTarget) < 0.05D,
                        "Dash impulse drifted away from its level's distance under vanilla friction");
            }
        }
        combat.startDash(77, Vec3.ZERO, 100L, 1);
        helper.assertTrue(combat.dashActive(100L) && combat.dashTargetId() == 77 && !combat.dashActive(101L),
                "Dash stayed active past its timeout");
        combat.finishDash();
        helper.assertTrue(!combat.dashActive(100L) && combat.dashTargetId() == -1,
                "Dash remained active after impact");

        for (double radius : new double[]{1.0D, 3.0D, 7.0D}) {
            Vec3 center = new Vec3(12.0D, 0.0D, -9.0D);
            for (boolean right : new boolean[]{false, true}) {
                Vec3 position = center.add(0.0D, 0.0D, -radius);
                Vec3 firstStep = zeldaswordskills_remastered.combat.DodgeMovement.orbitVelocity(position, center, 0.5D, right);
                helper.assertTrue(right ? firstStep.x < 0.0D : firstStep.x > 0.0D,
                        "Orbit Dodge reversed the player's left/right direction");
                double speed = AdvancedSwordSkills.dodgeImpulseSpeed(0.6F);
                double distance = 0.0D;
                while (speed >= 0.003D) {
                    Vec3 step = zeldaswordskills_remastered.combat.DodgeMovement.orbitVelocity(position, center, speed, right);
                    helper.assertTrue(Math.abs(step.horizontalDistance() - speed) < 1.0E-6D,
                            "Orbit Dodge shortened the actual step relative to an unlocked dodge");
                    position = position.add(step);
                    helper.assertTrue(Math.abs(position.distanceTo(center) - radius) < 1.0E-6D,
                            "Orbit Dodge increased the distance from its locked target");
                    distance += step.horizontalDistance();
                    speed *= 0.6F * 0.91F;
                }
                helper.assertTrue(Math.abs(distance - 4.0D) < 0.01D, "Orbit Dodge did not cover four blocks of actual travel");
            }
        }
        FriendlyByteBuf orbitBuffer = new FriendlyByteBuf(Unpooled.buffer());
        var orbitMessage = new zeldaswordskills_remastered.network.DodgeOrbitMessage(42, true, 1.362D);
        zeldaswordskills_remastered.network.DodgeOrbitMessage.encode(orbitMessage, orbitBuffer);
        helper.assertTrue(orbitMessage.equals(zeldaswordskills_remastered.network.DodgeOrbitMessage.decode(orbitBuffer)),
                "Dodge orbit launch did not round-trip");

        // The Parry confirmation carries the attacker it stopped, and a follow-up of zero means the
        // parry landed without Sword Break, which is still a valid state the client must accept.
        FriendlyByteBuf parryBuffer = new FriendlyByteBuf(Unpooled.buffer());
        var parryMessage = new zeldaswordskills_remastered.network.ParryStateMessage(0, 60, 42, 30, 22);
        zeldaswordskills_remastered.network.ParryStateMessage.encode(parryMessage, parryBuffer);
        helper.assertTrue(parryMessage.equals(zeldaswordskills_remastered.network.ParryStateMessage.decode(parryBuffer)),
                "Parry confirmation did not round-trip");
        FriendlyByteBuf parryNoFollowUpBuffer = new FriendlyByteBuf(Unpooled.buffer());
        var parryNoFollowUp = new zeldaswordskills_remastered.network.ParryStateMessage(0, 60, -1, 0, 0);
        zeldaswordskills_remastered.network.ParryStateMessage.encode(parryNoFollowUp, parryNoFollowUpBuffer);
        helper.assertTrue(parryNoFollowUp.equals(zeldaswordskills_remastered.network.ParryStateMessage.decode(parryNoFollowUpBuffer)),
                "A landed Parry without Sword Break did not round-trip");
        FriendlyByteBuf overlongParryBuffer = new FriendlyByteBuf(Unpooled.buffer());
        overlongParryBuffer.writeVarInt(0);
        overlongParryBuffer.writeVarInt(60);
        overlongParryBuffer.writeVarInt(42);
        overlongParryBuffer.writeVarInt(zeldaswordskills_remastered.network.ParryStateMessage.MAXIMUM_FOLLOW_UP_TICKS + 1);
        overlongParryBuffer.writeVarInt(0);
        boolean overlongParryRejected = false;
        try {
            zeldaswordskills_remastered.network.ParryStateMessage.decode(overlongParryBuffer);
        } catch (DecoderException expected) {
            overlongParryRejected = true;
        }
        helper.assertTrue(overlongParryRejected, "A Parry follow-up longer than its maximum was accepted");
        FriendlyByteBuf parryWithoutAttackerBuffer = new FriendlyByteBuf(Unpooled.buffer());
        parryWithoutAttackerBuffer.writeVarInt(0);
        parryWithoutAttackerBuffer.writeVarInt(60);
        parryWithoutAttackerBuffer.writeVarInt(-1);
        parryWithoutAttackerBuffer.writeVarInt(20);
        parryWithoutAttackerBuffer.writeVarInt(0);
        boolean parryWithoutAttackerRejected = false;
        try {
            zeldaswordskills_remastered.network.ParryStateMessage.decode(parryWithoutAttackerBuffer);
        } catch (DecoderException expected) {
            parryWithoutAttackerRejected = true;
        }
        helper.assertTrue(parryWithoutAttackerRejected, "A Parry follow-up without an attacker was accepted");
        FriendlyByteBuf guardBuffer = new FriendlyByteBuf(Unpooled.buffer());
        var guardMessage = new zeldaswordskills_remastered.network.ParryStateMessage(30, 90, -1, 0, 0);
        zeldaswordskills_remastered.network.ParryStateMessage.encode(guardMessage, guardBuffer);
        helper.assertTrue(guardMessage.equals(zeldaswordskills_remastered.network.ParryStateMessage.decode(guardBuffer)),
                "The longest defensive stance did not round-trip");
        FriendlyByteBuf invalidGuardBuffer = new FriendlyByteBuf(Unpooled.buffer());
        zeldaswordskills_remastered.network.ParryStateMessage.encode(
                new zeldaswordskills_remastered.network.ParryStateMessage(31, 91, -1, 0, 0), invalidGuardBuffer);
        boolean invalidGuardRejected = false;
        try {
            zeldaswordskills_remastered.network.ParryStateMessage.decode(invalidGuardBuffer);
        } catch (DecoderException expected) {
            invalidGuardRejected = true;
        }
        helper.assertTrue(invalidGuardRejected, "An overlong defensive stance was accepted");

        FriendlyByteBuf combatBuffer = new FriendlyByteBuf(Unpooled.buffer());
        CombatStateMessage combatMessage = new CombatStateMessage(42, 2, 3, 9.0F, 123L, false, 150L, true, -1, 160L);
        CombatStateMessage.encode(combatMessage, combatBuffer);
        helper.assertTrue(combatMessage.equals(CombatStateMessage.decode(combatBuffer)), "Combat state did not round-trip");
        FriendlyByteBuf invalidCombatBuffer = new FriendlyByteBuf(Unpooled.buffer());
        invalidCombatBuffer.writeVarInt(1);
        invalidCombatBuffer.writeByte(4);
        invalidCombatBuffer.writeByte(3);
        invalidCombatBuffer.writeFloat(1.0F);
        invalidCombatBuffer.writeVarLong(1L);
        invalidCombatBuffer.writeBoolean(false);
        invalidCombatBuffer.writeVarLong(0L);
        invalidCombatBuffer.writeBoolean(false);
        invalidCombatBuffer.writeVarInt(0);
        invalidCombatBuffer.writeVarLong(0L);
        helper.assertTrue(rejectsInvalidCombatState(invalidCombatBuffer), "Invalid combo bounds were accepted");
        FriendlyByteBuf invalidTargetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        invalidTargetBuffer.writeByte(99);
        boolean invalidTargetRejected = false;
        try {
            TargetIntentMessage.decode(invalidTargetBuffer);
        } catch (DecoderException expected) {
            invalidTargetRejected = true;
        }
        helper.assertTrue(invalidTargetRejected, "Invalid target action was accepted by the network decoder");

        // A real shield only counts as blocking after five ticks of use, and a FakePlayer never
        // ticks that wind-up, so the blocking flag is driven directly to create the state the
        // server's guard clause reads.
        final boolean[] parryBlocking = {false};
        FakePlayer combatPlayer = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString("b071f6f5-cb42-48b2-b9fa-f673610b96fa"), "[ZSS_Test]")) {
            @Override
            public float getAttackStrengthScale(float adjustTicks) {
                return 1.0F;
            }

            @Override
            public boolean isBlocking() {
                return parryBlocking[0];
            }
        };
        BlockPos combatPlayerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        combatPlayer.setPos(combatPlayerPos.getX() + 0.5D, combatPlayerPos.getY(), combatPlayerPos.getZ() + 0.5D);
        combatPlayer.setYRot(0.0F);
        combatPlayer.setXRot(0.0F);
        ZSSPlayerData dodgeData = new ZSSPlayerData();
        dodgeData.setSkillLevel(id("dodge"), 1);
        long dodgeTime = helper.getLevel().getGameTime();
        dodgeData.combat().startDodge(dodgeTime, 1);
        helper.assertTrue(AdvancedSwordSkills.onAttacked(combatPlayer, dodgeData, combatPlayer.damageSources().inFire())
                        && AdvancedSwordSkills.onAttacked(combatPlayer, dodgeData, combatPlayer.damageSources().fall()),
                "Dodge must block environmental damage even without a living attacker");
        dodgeData.combat().startDodge(dodgeTime - 20L, 1);
        helper.assertTrue(!AdvancedSwordSkills.onAttacked(combatPlayer, dodgeData, combatPlayer.damageSources().inFire()),
                "Dodge immunity continued after its twenty-tick window");
        ZSSPlayerData dashData = new ZSSPlayerData();
        dashData.setSkillLevel(id("dash"), 1);
        long dashTime = helper.getLevel().getGameTime();
        // Level-one immunity is 10 + 2 * 1 ticks, counted from the moment the dash is released.
        dashData.combat().startDashImmunity(dashTime + 12L);
        helper.assertTrue(dashData.combat().dashImmune(dashTime) && dashData.combat().dashImmune(dashTime + 12L)
                        && !dashData.combat().dashImmune(dashTime + 13L),
                "Dash immunity must last exactly (10 + 2 x skill level) ticks from release");
        helper.assertTrue(AdvancedSwordSkills.onAttacked(combatPlayer, dashData, combatPlayer.damageSources().inFire())
                        && AdvancedSwordSkills.onAttacked(combatPlayer, dashData, combatPlayer.damageSources().fall()),
                "Dash must grant immunity to every damage source, not just the struck target");
        dashData.combat().startDashImmunity(dashTime - 1L);
        helper.assertTrue(!AdvancedSwordSkills.onAttacked(combatPlayer, dashData, combatPlayer.damageSources().inFire()),
                "Dash immunity continued after its window expired");
        combatPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData combatData = new ZSSPlayerData();
        combatData.setSkillLevel(id("sword_basic"), 1);
        AABB combatArea = combatPlayer.getBoundingBox().inflate(8.0D);
        helper.getLevel().getEntitiesOfClass(LivingEntity.class, combatArea,
                entity -> entity != combatPlayer && !(entity instanceof Player)).forEach(LivingEntity::discard);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 3));
        target.setNoAi(true);
        combatPlayer.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        Vec3 targetDirection = target.getBoundingBox().getCenter().subtract(combatPlayer.getEyePosition()).normalize();
        helper.assertTrue(TargetingService.isHoldingSword(combatPlayer), "The test player was not holding a tagged sword");
        helper.assertTrue(combatPlayer.distanceToSqr(target) <= 49.0D, "The test target was outside the level-one lock range");
        var sightHit = helper.getLevel().clip(new net.minecraft.world.level.ClipContext(combatPlayer.getEyePosition(),
                target.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, combatPlayer));
        helper.assertTrue(combatPlayer.hasLineOfSight(target), "The test player had no line of sight to the target: "
                + sightHit.getBlockPos() + " " + helper.getLevel().getBlockState(sightHit.getBlockPos())
                + " from " + combatPlayer.getEyePosition() + " to " + target.getEyePosition());
        helper.assertTrue(combatPlayer.getLookAngle().dot(targetDirection) >= 0.25D, "The test target was outside the view cone");
        TargetingService.handle(combatPlayer, combatData, TargetIntentMessage.Action.ACQUIRE);
        helper.assertTrue(combatData.combat().targetId() == target.getId(), "Server target acquisition expected entity "
                + target.getId() + " but selected " + combatData.combat().targetId());
        helper.assertTrue(TargetingService.isHoldingSword(combatPlayer), "The test player lost its sword");
        helper.assertTrue(TargetingService.getValidTarget(combatPlayer, combatData).isPresent(), "The locked target became invalid before attacking");
        helper.assertTrue(combatPlayer.distanceToSqr(target) <= 16.0D, "The locked target was outside melee range");
        helper.assertTrue(combatPlayer.getAttackStrengthScale(0.5F) >= 0.9F, "The test player's attack cooldown did not recover");
        float healthBefore = target.getHealth();
        helper.assertTrue(BasicSwordSkill.attack(combatPlayer, combatData), "The server rejected a valid basic sword attack");
        helper.assertTrue(target.getHealth() < healthBefore && combatData.combat().comboCount() == 1,
                "The basic sword attack did not apply damage and advance the combo");
        // The break threshold is per level (1 / 1 / 3 / 3 / 5 / 5 / 7 / 7 / 9 / 10), so a level-one
        // chain only breaks once a single hit exceeds one point.
        float breakThreshold = BasicSwordSkill.damageBreakThreshold(1);
        BasicSwordSkill.onPlayerDamaged(combatPlayer, combatData, combatPlayer.damageSources().magic(), breakThreshold);
        helper.assertTrue(!combatData.combat().comboFinished(),
                "A hit equal to the level-one break threshold finished the combo");
        BasicSwordSkill.onPlayerDamaged(combatPlayer, combatData, combatPlayer.damageSources().magic(), breakThreshold + 0.01F);
        helper.assertTrue(combatData.combat().comboFinished(), "Heavy incoming damage did not finish the combo");
        long combatTime = helper.getLevel().getGameTime();
        combatData.setSkillLevel(id("helm_splitter"), 1);
        target.setHealth(target.getMaxHealth());
        target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        float armoredHealth = target.getHealth();
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("helm_splitter"), SkillIntentMessage.Action.ATTACK, Optional.empty()));
        helper.assertTrue(target.getHealth() == armoredHealth, "Helm Splitter accepted an attack without a parry");
        target.invulnerableTime = 0;
        Vec3 beforeHelm = combatPlayer.position();
        combatData.combat().helmSplitter().arm(target.getId(), combatTime + 22L, beforeHelm.y);
        combatPlayer.setPos(beforeHelm.add(0, 0.5D, 0));
        combatPlayer.setOnGround(false);
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("helm_splitter"), SkillIntentMessage.Action.ATTACK, Optional.empty()));
        float helmDamage = armoredHealth - target.getHealth();
        helper.assertTrue(Math.abs(helmDamage - (float) combatPlayer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - 1.5F) < 0.0001F,
                "Armor reduced Helm Splitter damage");
        helper.assertTrue(combatData.combat().comboCount() == 1,
                "Helm Splitter did not count as a combo hit on the locked target");
        helper.assertTrue(combatPlayer.getDeltaMovement().horizontalDistanceSqr() > 0 && combatPlayer.getDeltaMovement().y > 0,
                "Helm Splitter did not apply both horizontal travel and its second jump");
        combatPlayer.setPos(beforeHelm);
        combatPlayer.setOnGround(true);
        combatPlayer.setDeltaMovement(Vec3.ZERO);
        combatData.combat().helmSplitter().reset();

        combatData.setSkillLevel(id("spin_attack"), 1);
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        float spinHealth = target.getHealth();
        combatData.combat().beginCharge(id("spin_attack"), combatTime - 25L, Vec3.ZERO);
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("spin_attack"), SkillIntentMessage.Action.RELEASE, Optional.empty()));
        helper.assertTrue(target.getHealth() < spinHealth, "Charged Spin Attack did not damage a nearby target");

        combatData.setSkillLevel(id("rising_cut"), 1);
        combatData.setSkillLevel(id("leaping_blow"), 1);
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        combatData.combat().clearCombo();
        float risingHealth = target.getHealth();
        float exhaustionBefore = combatPlayer.getFoodData().getExhaustionLevel();
        long risingTime = helper.getLevel().getGameTime();
        combatPlayer.fallDistance = 20.0F;
        combatData.combat().armRisingCut(risingTime + 3L);
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("rising_cut"), SkillIntentMessage.Action.ATTACK, Optional.empty()));
        helper.assertTrue(target.getHealth() < risingHealth && combatData.combat().comboCount() == 1,
                "Rising Cut did not damage the locked target and advance the combo");
        helper.assertTrue(Math.abs(combatPlayer.getFoodData().getExhaustionLevel() - exhaustionBefore) < 1.0E-4F,
                "Rising Cut consumed hunger");
        helper.assertTrue(combatData.combat().comboMaximum() == BasicSwordSkill.comboMaximum(1),
                "Rising Cut did not use the Basic Sword Skill combo bounds");
        helper.assertTrue(combatPlayer.getDeltaMovement().y > 0.3D,
                "A landed Rising Cut hit did not lift the player a second time");
        helper.assertTrue(combatPlayer.fallDistance == 0.0F && combatData.combat().groundSlamReady(),
                "Rising Cut did not reset the old descent and arm Ground Slam");
        helper.assertTrue(Math.abs(AdvancedSwordSkills.risingCutLift(1) - 0.544852D) < 1.0E-9D
                        && Math.abs(AdvancedSwordSkills.risingCutLift(5) - 0.803191D) < 1.0E-9D,
                "Rising Cut double jump did not follow its per-level height table");
        // Integrate vanilla jump physics to confirm the table really clears the stated heights.
        for (int level = 1; level <= 5; level++) {
            double velocity = AdvancedSwordSkills.risingCutLift(level);
            double height = 0.0D;
            for (int tick = 0; tick < 400 && velocity > 0.0D; tick++) {
                height += velocity;
                velocity = (velocity - 0.08D) * 0.98D;
            }
            helper.assertTrue(Math.abs(height - (1.5D + 0.5D * level)) < 0.01D,
                    "Rising Cut level " + level + " double jump rose " + height + " blocks");
        }
        helper.assertTrue(combatData.combat().risingCutCoolingDown(risingTime),
                "Rising Cut did not start its cooldown");
        helper.assertTrue(!combatData.combat().risingCutCoolingDown(risingTime + 60L),
                "Rising Cut cooldown did not end after sixty ticks");
        helper.assertTrue(combatData.combat().risingCutTrailActive(risingTime),
                "Rising Cut did not start its upward slash trail");

        // A landed Rising Cut waives the fall of the climb it started. The waiver is the lift
        // height plus five blocks; a longer drop keeps only its excess.
        helper.assertTrue(Math.abs(combatData.combat().risingCutFallGrace() - 7.0D) < 1.0E-9D,
                "A level-one Rising Cut must waive seven blocks of fall");
        for (int level = 1; level <= 5; level++) {
            double waiver = AdvancedSwordSkills.risingCutFallGrace(level);
            helper.assertTrue(Math.abs(waiver - (6.5D + 0.5D * level)) < 1.0E-9D,
                    "Rising Cut level " + level + " must waive lift height + five blocks of fall");
            helper.assertTrue(waiver - AdvancedSwordSkills.risingCutHeight(level) == 5.0D,
                    "The Rising Cut waiver must cover exactly five blocks beyond its own lift");
        }
        // Drive the fall event directly: it reads the capability instance, not the local data.
        ZSSPlayerData fallData = ZSSCapabilities.get(combatPlayer)
                .orElseThrow(() -> new AssertionError("Test player capability missing"));
        fallData.combat().startRisingCutFallGrace(AdvancedSwordSkills.risingCutFallGrace(1));
        net.minecraftforge.event.entity.living.LivingFallEvent shortFall =
                new net.minecraftforge.event.entity.living.LivingFallEvent(combatPlayer, 7.0F, 1.0F);
        ZSSCombatEvents.playerFall(shortFall);
        helper.assertTrue(shortFall.getDistance() == 0.0F,
                "A level-one Rising Cut fall of seven blocks still dealt fall damage");
        helper.assertTrue(fallData.combat().risingCutFallGrace() == 0.0D,
                "The Rising Cut waiver was not spent on the fall it covered");
        // Past the waiver only the excess stays as fall distance, so vanilla rules apply to it.
        fallData.combat().startRisingCutFallGrace(AdvancedSwordSkills.risingCutFallGrace(1));
        net.minecraftforge.event.entity.living.LivingFallEvent longFall =
                new net.minecraftforge.event.entity.living.LivingFallEvent(combatPlayer, 10.0F, 1.0F);
        ZSSCombatEvents.playerFall(longFall);
        helper.assertTrue(Math.abs(longFall.getDistance() - 3.0F) < 0.01F,
                "A ten-block Rising Cut fall did not keep its three-block excess");
        // The waiver covers one climb, so landing, flight and a reset all end it.
        combatData.combat().startRisingCutFallGrace(AdvancedSwordSkills.risingCutFallGrace(5));
        combatPlayer.setOnGround(true);
        AdvancedSwordSkills.tick(combatPlayer, combatData);
        helper.assertTrue(combatData.combat().risingCutFallGrace() > 0.0D,
                "A hit landed on the ground lost its waiver before the lift had left the ground");
        combatPlayer.setOnGround(false);
        AdvancedSwordSkills.tick(combatPlayer, combatData);
        helper.assertTrue(combatData.combat().risingCutGraceAirborne(),
                "The Rising Cut waiver did not start counting once the lift left the ground");
        combatPlayer.setOnGround(true);
        combatPlayer.setDeltaMovement(Vec3.ZERO);
        combatPlayer.fallDistance = 0.0F;
        AdvancedSwordSkills.tick(combatPlayer, combatData);
        helper.assertTrue(combatData.combat().risingCutFallGrace() == 0.0D,
                "Landing did not end the Rising Cut fall waiver");
        combatData.combat().startRisingCutFallGrace(AdvancedSwordSkills.risingCutFallGrace(5));
        combatPlayer.setOnGround(false);
        combatPlayer.getAbilities().flying = true;
        AdvancedSwordSkills.tick(combatPlayer, combatData);
        helper.assertTrue(combatData.combat().risingCutFallGrace() == 0.0D,
                "Breaking the fall with flight did not end the Rising Cut waiver");
        combatPlayer.getAbilities().flying = false;
        combatData.combat().startRisingCutFallGrace(AdvancedSwordSkills.risingCutFallGrace(5));
        combatData.combat().reset();
        helper.assertTrue(combatData.combat().risingCutFallGrace() == 0.0D,
                "Combat reset left the Rising Cut fall waiver armed");

        // Double Jump: a mid-air skill with its own orb slot and one extra jump per airtime.
        helper.assertTrue(ZSSContentIds.SKILLS.contains(id("double_jump"))
                        && ZSSContentIds.SKILL_ORDER.indexOf(id("double_jump")) == 15,
                "Double Jump was not registered after the existing sword skills");
        helper.assertTrue(combatData.skillMaximum(id("double_jump")) == 5, "Double Jump must cap at level five");
        for (int level = 1; level <= 5; level++) {
            double jumpHeight = 0.75D + 0.25D * level;
            helper.assertTrue(Math.abs(AdvancedSwordSkills.doubleJumpHeight(level) - jumpHeight) < 1.0E-9D,
                    "Double Jump height must be 0.75 + 0.25 x skill level");
            helper.assertTrue(Math.abs(AdvancedSwordSkills.doubleJumpFallHeight(level) - jumpHeight) < 1.0E-9D,
                    "Double Jump must raise the safe fall height by its own jump height");
            double jumpVelocity = AdvancedSwordSkills.doubleJumpVelocity(level);
            double reached = 0.0D;
            for (int tick = 0; tick < 400 && jumpVelocity > 0.0D; tick++) {
                reached += jumpVelocity;
                jumpVelocity = (jumpVelocity - 0.08D) * 0.98D;
            }
            helper.assertTrue(Math.abs(reached - jumpHeight) < 0.01D,
                    "Double Jump level " + level + " rose " + reached + " blocks");
        }
        combatData.combat().resetDoubleJump();
        helper.assertTrue(combatData.combat().useDoubleJump() && !combatData.combat().useDoubleJump(),
                "Double Jump must be usable only once per airtime");
        combatData.combat().resetDoubleJump();
        helper.assertTrue(combatData.combat().useDoubleJump(), "Landing did not restore the Double Jump");
        combatData.combat().reset();
        helper.assertTrue(combatData.combat().useDoubleJump(), "Combat reset left the Double Jump spent");
        // The assertions above spend the charge on purpose; give the skill a fresh one before
        // checking that a real activation replaces the falling velocity.
        combatData.combat().resetDoubleJump();
        combatData.setSkillLevel(id("double_jump"), 1);
        combatPlayer.setOnGround(false);
        combatPlayer.setDeltaMovement(0.0D, -0.3D, 0.0D);
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("double_jump"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(Math.abs(combatPlayer.getDeltaMovement().y - AdvancedSwordSkills.doubleJumpVelocity(1)) < 1.0E-9D,
                "An airborne Double Jump did not replace the falling velocity");
        combatPlayer.setDeltaMovement(0.0D, -0.3D, 0.0D);
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("double_jump"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(combatPlayer.getDeltaMovement().y < 0.0D,
                "A second Double Jump was accepted in the same airtime");
        combatPlayer.setOnGround(true);
        AdvancedSwordSkills.tick(combatPlayer, combatData);
        helper.assertTrue(!combatData.combat().doubleJumpUsed(), "Touching the ground did not restore the Double Jump");

        // Parry: one (20 + 2 x level) tick guard, followed by a sixty-tick cooldown.
        combatData.setSkillLevel(id("parry"), 1);
        combatData.setSkillLevel(id("sword_break"), 1);
        ItemStack attackerSword = new ItemStack(Items.IRON_SWORD);
        target.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, attackerSword);
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        TargetingService.handle(combatPlayer, combatData, TargetIntentMessage.Action.ACQUIRE);
        helper.assertTrue(combatData.combat().targetId() == target.getId(),
                "The test target could not be re-locked for the Parry checks");
        long parryTime = helper.getLevel().getGameTime();
        // Non-parried damage goes through the real damage-event capability, including fire.
        fallData.combat().startParry(parryTime, parryTime + 22L);
        var zeroGuardDamage = new net.minecraftforge.event.entity.living.LivingDamageEvent(
                combatPlayer, combatPlayer.damageSources().inFire(), 0.0F);
        ZSSCombatEvents.playerDamaged(zeroGuardDamage);
        helper.assertTrue(fallData.combat().parryActive(parryTime), "Zero damage ended the guard");
        var actualGuardDamage = new net.minecraftforge.event.entity.living.LivingDamageEvent(
                combatPlayer, combatPlayer.damageSources().inFire(), 1.0F);
        ZSSCombatEvents.playerDamaged(actualGuardDamage);
        helper.assertTrue(!fallData.combat().parryPending()
                        && fallData.combat().parryCooldownUntil() == parryTime + 60L
                        && !fallData.combat().swordBreakActive(parryTime),
                "Actual environmental damage must close the guard without granting Sword Break");
        fallData.combat().reset();
        // The guard is a locked-on stance: with the lock dropped it must not arm at all.
        TargetingService.handle(combatPlayer, combatData, TargetIntentMessage.Action.CLEAR);
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(!combatData.combat().parryActive(parryTime + 1L),
                "Parry armed a guard with no locked target");
        TargetingService.handle(combatPlayer, combatData, TargetIntentMessage.Action.ACQUIRE);
        helper.assertTrue(combatData.combat().targetId() == target.getId(),
                "The test target could not be re-locked for the Parry checks");
        // A raised shield outranks the guard, so the key that would open it belongs to the shield.
        combatPlayer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        parryBlocking[0] = true;
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(!combatData.combat().parryActive(parryTime + 1L),
                "Parry opened a guard while a shield was blocking");
        parryBlocking[0] = false;
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(!combatData.combat().parryPending() && !combatData.combat().parryCoolingDown(parryTime),
                "A held shield must prevent Parry before blocking starts without spending its cooldown");
        combatPlayer.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(!combatData.combat().parryPending(), "Parry bypassed the shield's five-tick wind-up");
        combatPlayer.stopUsingItem();
        combatPlayer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        combatPlayer.swinging = false;
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(combatData.combat().parryActive(parryTime) && !combatPlayer.swinging,
                "Opening a Parry guard must not swing the weapon");
        for (int level = 1; level <= 5; level++) {
            helper.assertTrue(AdvancedSwordSkills.parryWindow(level) == 20 + 2 * level
                            && AdvancedSwordSkills.swordBreakWindow(level) == 20 + 2 * level,
                    "Parry and Sword Break must each use twenty ticks plus two per own skill level");
        }
        helper.assertTrue(combatData.combat().parryActive(parryTime + 21L)
                        && !combatData.combat().parryActive(parryTime + 22L),
                "A level-one Parry must stay armed for exactly twenty-two ticks");
        float exhaustionBeforeParry = combatPlayer.getFoodData().getExhaustionLevel();
        helper.assertTrue(AdvancedSwordSkills.onAttacked(combatPlayer, combatData,
                        combatPlayer.damageSources().mobAttack(target)),
                "An armed Parry did not stop an incoming weapon attack");
        helper.assertTrue(target.hasEffect(ZSSRegistries.STUN.get()) && AdvancedSwordSkills.parryLocked(target),
                "A parried attacker was not stunned out of attacking and moving");
        helper.assertTrue(target.getMainHandItem().getCount() == 1, "Parry disarmed its attacker");
        helper.assertTrue(Math.abs(combatPlayer.getFoodData().getExhaustionLevel() - exhaustionBeforeParry) < 1.0E-4F,
                "Parry consumed hunger");
        helper.assertTrue(!AdvancedSwordSkills.onAttacked(combatPlayer, combatData,
                        combatPlayer.damageSources().mobAttack(target)),
                "One Parry window stopped more attacks than its level allows");
        // Landing a parry spends the window and starts the sixty-tick cooldown from that moment.
        helper.assertTrue(!combatData.combat().parryActive(parryTime + 1L),
                "A landed Parry left its window open for a second attack");
        helper.assertTrue(combatData.combat().parryCoolingDown(parryTime + 59L)
                        && !combatData.combat().parryCoolingDown(parryTime + 60L),
                "A landed Parry did not start a sixty-tick cooldown from the moment it landed");
        // The stun is what stops the parried attacker from fighting back, and it covers every blow
        // that enemy produces rather than only the ones aimed at the player.
        net.minecraftforge.event.entity.living.LivingAttackEvent lockedAttack =
                new net.minecraftforge.event.entity.living.LivingAttackEvent(combatPlayer,
                        target.damageSources().mobAttack(target), 1.0F);
        ZSSCombatEvents.attackerStunned(lockedAttack);
        helper.assertTrue(lockedAttack.isCanceled(), "A stunned attacker could still damage a player");
        net.minecraftforge.event.entity.living.LivingAttackEvent bystanderAttack =
                new net.minecraftforge.event.entity.living.LivingAttackEvent(target,
                        target.damageSources().mobAttack(target), 1.0F);
        ZSSCombatEvents.attackerStunned(bystanderAttack);
        helper.assertTrue(bystanderAttack.isCanceled(),
                "A stunned attacker could still damage something other than the player");
        // The counter-attack the parry opens comes from the player, so it is not refused.
        net.minecraftforge.event.entity.living.LivingAttackEvent counterAttack =
                new net.minecraftforge.event.entity.living.LivingAttackEvent(target,
                        combatPlayer.damageSources().playerAttack(combatPlayer), 1.0F);
        ZSSCombatEvents.attackerStunned(counterAttack);
        helper.assertTrue(!counterAttack.isCanceled(),
                "The parry stun also blocked the player's own counter-attack");
        // The hold pins the parried enemy to where the parry caught it, horizontally only, so
        // gravity still applies; a later Sword Break releases the hold so its knockback can read.
        double parryX = target.getX();
        double parryZ = target.getZ();
        target.setPos(target.getX() + 3.0D, target.getY(), target.getZ() + 3.0D);
        target.setDeltaMovement(0.4D, -0.3D, 0.4D);
        AdvancedSwordSkills.holdParriedAttackers();
        helper.assertTrue(Math.abs(target.getX() - parryX) < 1.0E-6D && Math.abs(target.getZ() - parryZ) < 1.0E-6D,
                "The parry hold did not pin the parried enemy back in place");
        helper.assertTrue(Math.abs(target.getDeltaMovement().y + 0.3D) < 1.0E-6D,
                "The parry hold also cancelled the parried enemy's fall");
        helper.assertTrue(target.getDeltaMovement().horizontalDistanceSqr() == 0.0D,
                "The parry hold left the parried enemy with horizontal motion");

        helper.assertTrue(combatData.combat().swordBreakActive(parryTime + 21L)
                        && !combatData.combat().swordBreakActive(parryTime + 22L),
                "A level-one Sword Break follow-up must stay open for exactly twenty-two ticks");
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        // Armor is worn on purpose: an armor-piercing blow must not be reduced by it.
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        int comboBeforeBreak = combatData.combat().comboCount();
        float breakHealth = target.getHealth();
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("sword_break"), SkillIntentMessage.Action.ATTACK, Optional.empty()));
        float breakDamage = breakHealth - target.getHealth();
        float expectedBreak = (float) combatPlayer.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) + 1.2F;
        helper.assertTrue(Math.abs(breakDamage - expectedBreak) < 0.0001F,
                "Sword Break must deal weapon attack power + 1.2 x skill level as armor-piercing damage");
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, id("sword_break")))
                        .is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR),
                "Sword Break damage is not registered as armor-piercing");
        target.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        helper.assertTrue(target.getDeltaMovement().horizontalDistanceSqr() > 0.0D,
                "Sword Break did not knock its target back");
        helper.assertTrue(attackerSword.getDamageValue() == 0, "Sword Break still damaged the attacker's weapon");
        helper.assertTrue(!combatData.combat().swordBreakActive(parryTime + 4L),
                "Sword Break could be released twice for a single Parry");
        helper.assertTrue(combatData.combat().comboCount() == comboBeforeBreak + 1,
                "Sword Break did not count as a combo hit on the locked target");
        helper.assertTrue(Math.abs(combatPlayer.getFoodData().getExhaustionLevel() - exhaustionBeforeParry) < 1.0E-4F,
                "Sword Break consumed hunger");
        // A landed Sword Break keeps the enemy unable to attack or move for its own, longer window:
        // the stun effect is refreshed past the parry's twenty ticks and the lock outlives it, while
        // the position hold is re-armed after a short grace so the knockback above still carried.
        long breakLockUntil = AdvancedSwordSkills.parryLockUntil(target);
        helper.assertTrue(AdvancedSwordSkills.parryLocked(target)
                        && breakLockUntil >= helper.getLevel().getGameTime() + 38L,
                "Sword Break did not extend the attack lock and immobility to its own window");
        var swordBreakStun = target.getEffect(ZSSRegistries.STUN.get());
        helper.assertTrue(swordBreakStun != null && swordBreakStun.getDuration() >= 38,
                "A landed Sword Break must refresh the forty-tick stun window");
        helper.assertTrue(AdvancedSwordSkills.parryHoldFromTick(target) > helper.getLevel().getGameTime(),
                "Sword Break must give the knockback a grace before pinning its target again");

        // The follow-up reaches nobody but the locked target: with the lock dropped it must neither
        // deal damage nor fall back to an ordinary attack.
        long orphanTime = helper.getLevel().getGameTime();
        combatData.combat().startSwordBreak(target.getId(), orphanTime + 20L);
        TargetingService.handle(combatPlayer, combatData, TargetIntentMessage.Action.CLEAR);
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        float orphanHealth = target.getHealth();
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("sword_break"), SkillIntentMessage.Action.ATTACK, Optional.empty()));
        helper.assertTrue(target.getHealth() == orphanHealth,
                "Sword Break damaged an enemy that was no longer the locked target");
        helper.assertTrue(!combatData.combat().swordBreakActive(orphanTime + 1L),
                "A Sword Break attempt against a non-target did not spend the window");

        // Releasing the key is a server intent, including BEGIN/CANCEL arriving in one tick.
        ZSSPlayerData releaseData = new ZSSPlayerData();
        releaseData.setSkillLevel(id("sword_basic"), 1);
        releaseData.setSkillLevel(id("parry"), 1);
        releaseData.combat().setTarget(target.getId());
        AdvancedSwordSkills.handleIntent(combatPlayer, releaseData,
                new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(releaseData.combat().parryActive(parryTime), "Release test did not open a guard");
        AdvancedSwordSkills.handleIntent(combatPlayer, releaseData,
                new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.CANCEL, Optional.empty()));
        helper.assertTrue(!releaseData.combat().parryPending()
                        && releaseData.combat().parryCooldownUntil() == parryTime + 60L
                        && !releaseData.combat().swordBreakActive(parryTime),
                "Releasing use must end the guard, start sixty ticks of cooldown and grant no follow-up");
        AdvancedSwordSkills.handleIntent(combatPlayer, releaseData,
                new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(!releaseData.combat().parryActive(parryTime), "Release cooldown allowed an immediate new guard");

        // Exercise the damage-cancellation event with both natural attackers from the reported case.
        // Use the actual capability, since playerAttacked reads it rather than combatData above.
        int previousBasicLevel = fallData.skillLevel(id("sword_basic"));
        int previousParryLevel = fallData.skillLevel(id("parry"));
        int previousBreakLevel = fallData.skillLevel(id("sword_break"));
        for (var type : java.util.List.of(EntityType.IRON_GOLEM, EntityType.ZOMBIE)) {
            LivingEntity unarmed = helper.spawn(type, new BlockPos(2, 1, 3));
            unarmed.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fallData.combat().reset();
            fallData.setSkillLevel(id("sword_basic"), 1);
            fallData.setSkillLevel(id("parry"), 1);
            fallData.setSkillLevel(id("sword_break"), 1);
            fallData.combat().setTarget(unarmed.getId());
            AdvancedSwordSkills.handleIntent(combatPlayer, fallData,
                    new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.BEGIN, Optional.empty()));
            var incoming = new net.minecraftforge.event.entity.living.LivingAttackEvent(combatPlayer,
                    combatPlayer.damageSources().mobAttack(unarmed), 8.0F);
            ZSSCombatEvents.playerAttacked(incoming);
            helper.assertTrue(incoming.isCanceled(), "Natural attack was not parried: " + type);
            helper.assertTrue(unarmed.hasEffect(ZSSRegistries.STUN.get())
                            && unarmed.getEffect(ZSSRegistries.STUN.get()).getDuration() == 20
                            && AdvancedSwordSkills.parryLocked(unarmed),
                    "Natural attacker did not receive the twenty-tick stun and attack lock: " + type);
            var response = new net.minecraftforge.event.entity.living.LivingAttackEvent(combatPlayer,
                    combatPlayer.damageSources().mobAttack(unarmed), 8.0F);
            ZSSCombatEvents.attackerStunned(response);
            helper.assertTrue(response.isCanceled(), "Parried natural attacker could still attack: " + type);
            double heldX = unarmed.getX();
            unarmed.setPos(heldX + 1.0D, unarmed.getY(), unarmed.getZ());
            AdvancedSwordSkills.holdParriedAttackers();
            helper.assertTrue(unarmed.getX() == heldX, "Parried natural attacker could still move: " + type);
            long confirmedFollowUp = fallData.combat().swordBreakUntil();
            AdvancedSwordSkills.handleIntent(combatPlayer, fallData,
                    new SkillIntentMessage(id("parry"), SkillIntentMessage.Action.CANCEL, Optional.empty()));
            helper.assertTrue(fallData.combat().swordBreakActive(parryTime)
                            && fallData.combat().swordBreakUntil() == confirmedFollowUp
                            && fallData.combat().parryCooldownUntil() == parryTime + 60L,
                    "Release after successful Parry changed its cooldown or consumed Sword Break");
            unarmed.discard();
        }
        fallData.combat().reset();
        fallData.setSkillLevel(id("sword_basic"), previousBasicLevel);
        fallData.setSkillLevel(id("parry"), previousParryLevel);
        fallData.setSkillLevel(id("sword_break"), previousBreakLevel);
        AdvancedSwordSkills.holdParriedAttackers();

        combatData.setSkillLevel(id("sword_beam"), 1);
        combatData.setMagic(50.0F, 50.0F);
        combatPlayer.setHealth(combatPlayer.getMaxHealth());
        combatPlayer.setShiftKeyDown(true);
        combatPlayer.setOnGround(true);
        int beamsBefore = helper.getLevel().getEntities(ZSSRegistries.SWORD_BEAM.get(), entity -> true).size();
        AdvancedSwordSkills.handleIntent(combatPlayer, combatData,
                new SkillIntentMessage(id("sword_beam"), SkillIntentMessage.Action.ATTACK, Optional.empty()));
        int beamsAfter = helper.getLevel().getEntities(ZSSRegistries.SWORD_BEAM.get(), entity -> true).size();
        helper.assertTrue(beamsAfter == beamsBefore + 1 && combatData.currentMagic() == 50.0F,
                "Sword Beam did not spawn exactly once without consuming magic");
        combatPlayer.setShiftKeyDown(false);

        float maximumHealthBefore = combatPlayer.getMaxHealth();
        combatData.setSkillLevel(id("bonus_heart"), 3);
        AdvancedSwordSkills.tick(combatPlayer, combatData);
        helper.assertTrue(combatPlayer.getMaxHealth() == maximumHealthBefore + 6.0F,
                "Bonus Heart did not add exactly two maximum health per level");
        AdvancedSwordSkills.tick(combatPlayer, combatData);
        helper.assertTrue(combatPlayer.getMaxHealth() == maximumHealthBefore + 6.0F,
                "Bonus Heart modifier stacked during recalculation");

        BlockPos pedestalPos = BlockPos.ZERO;
        helper.setBlock(pedestalPos, ZSSRegistries.PEDESTAL.get());
        helper.assertTrue(helper.getBlockEntity(pedestalPos) instanceof PedestalBlockEntity, "Pedestal block entity was not created");
        PedestalBlockEntity pedestal = (PedestalBlockEntity) helper.getBlockEntity(pedestalPos);
        ItemStack rejected = pedestal.inventory().insertItem(PedestalBlockEntity.SLOT_POWER, new ItemStack(ZSSRegistries.MASTER_ORE.get()), false);
        helper.assertTrue(rejected.is(ZSSRegistries.MASTER_ORE.get()), "Pedestal accepted an invalid item");
        helper.assertTrue(!pedestal.setSword(new ItemStack(ZSSRegistries.MASTER_SWORD.get()), combatPlayer)
                        && !pedestal.setSword(new ItemStack(ZSSRegistries.KOKIRI_SWORD.get()), combatPlayer)
                        && !pedestal.hasSword(),
                "Inactive pedestal accepted a sword or accepted a non-Master-Sword weapon");
        pedestal.inventory().insertItem(PedestalBlockEntity.SLOT_POWER, new ItemStack(ZSSRegistries.PENDANT_POWER.get()), false);
        pedestal.inventory().insertItem(PedestalBlockEntity.SLOT_WISDOM, new ItemStack(ZSSRegistries.PENDANT_WISDOM.get()), false);
        pedestal.inventory().insertItem(PedestalBlockEntity.SLOT_COURAGE, new ItemStack(ZSSRegistries.PENDANT_COURAGE.get()), false);
        helper.assertTrue(helper.getBlockState(pedestalPos).getValue(PedestalBlock.PENDANTS) == 7, "Pedestal pendant flags were not updated");
        helper.assertTrue(helper.getBlockState(pedestalPos).getValue(PedestalBlock.UNLOCKED), "Pedestal did not unlock with all pendants");
        PedestalMenu pedestalMenu = (PedestalMenu) pedestal.createMenu(0, combatPlayer.getInventory(), combatPlayer);
        helper.assertTrue(pedestalMenu != null && !pedestalMenu.getSlot(0).mayPlace(new ItemStack(ZSSRegistries.PENDANT_POWER.get()))
                        && pedestalMenu.getSlot(0).mayPickup(combatPlayer),
                "Unlocked pedestal did not allow pendant removal while preventing insertion");
        ItemStack extractedPendant = pedestal.inventory().extractItem(PedestalBlockEntity.SLOT_POWER, 1, false);
        helper.assertTrue(extractedPendant.is(ZSSRegistries.PENDANT_POWER.get())
                        && helper.getBlockState(pedestalPos).getValue(PedestalBlock.PENDANTS) == 6
                        && !helper.getBlockState(pedestalPos).getValue(PedestalBlock.UNLOCKED),
                "Unlocked pedestal did not allow pendant removal");
        pedestal.inventory().insertItem(PedestalBlockEntity.SLOT_POWER, new ItemStack(ZSSRegistries.PENDANT_POWER.get()), false);
        helper.assertTrue(helper.getBlockState(pedestalPos).getValue(PedestalBlock.PENDANTS) == 7
                        && helper.getBlockState(pedestalPos).getValue(PedestalBlock.UNLOCKED),
                "Pedestal did not reactivate after replacing a removed pendant");
        for (RegistryObject<Item> sword : List.of(ZSSRegistries.MASTER_SWORD, ZSSRegistries.TEMPERED_SWORD,
                ZSSRegistries.GOLDEN_SWORD, ZSSRegistries.TRUE_MASTER_SWORD)) {
            helper.assertTrue(pedestal.setSword(new ItemStack(sword.get()), combatPlayer)
                            && pedestal.sword().is(sword.get())
                            && helper.getBlockState(pedestalPos).getValue(PedestalBlock.HAS_SWORD),
                    "Pedestal did not preserve sword item ID " + sword.getId());
            helper.assertTrue(pedestal.retrieveSword() && !helper.getBlockState(pedestalPos).getValue(PedestalBlock.HAS_SWORD),
                    "Unlocked pedestal did not retrieve sword item ID " + sword.getId());
        }

        helper.assertTrue(ZSSRegistries.PEDESTAL.get().getDestroyProgress(helper.getBlockState(pedestalPos), combatPlayer,
                        helper.getLevel(), helper.absolutePos(pedestalPos)) == 0.0F,
                "Pedestal is still breakable by a player");

        BlockState hookNorth = ZSSRegistries.HOOK_TARGET.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, net.minecraft.core.Direction.NORTH);
        helper.assertTrue(ZSSBlockInteractions.canHook(hookNorth, net.minecraft.core.Direction.NORTH)
                        && !ZSSBlockInteractions.canHook(hookNorth, net.minecraft.core.Direction.SOUTH),
                "Directional hook target accepted the wrong face");
        helper.assertTrue(java.util.Arrays.stream(net.minecraft.core.Direction.values()).allMatch(face ->
                        ZSSBlockInteractions.canHook(ZSSRegistries.HOOK_TARGET_ALL.get().defaultBlockState(), face)),
                "All-face hook target rejected a face");
        helper.assertTrue(ZSSRegistries.LIGHT_BLOCK.get().liftWeight(ZSSRegistries.LIGHT_BLOCK.get().defaultBlockState())
                        == ZSSBlockInteractions.Weight.MEDIUM
                        && ZSSRegistries.HEAVY_BLOCK.get().liftWeight(ZSSRegistries.HEAVY_BLOCK.get().defaultBlockState())
                        == ZSSBlockInteractions.Weight.VERY_HEAVY,
                "Split weight block registrations lost their weight semantics");
        helper.assertTrue(ZSSRegistries.CERAMIC_JAR.get().liftWeight(ZSSRegistries.CERAMIC_JAR.get().defaultBlockState())
                        == ZSSBlockInteractions.Weight.VERY_LIGHT
                        && ZSSRegistries.WARP_STONE_BOLERO.get().liftWeight(ZSSRegistries.WARP_STONE_BOLERO.get().defaultBlockState())
                        == ZSSBlockInteractions.Weight.IMPOSSIBLE
                        && ZSSRegistries.WARP_STONE_BOLERO.get().smashWeight(ZSSRegistries.WARP_STONE_BOLERO.get().defaultBlockState())
                        == ZSSBlockInteractions.Weight.IMPOSSIBLE,
                "Lift and smash contracts changed for ceramic jars or Warp Stones");
        BlockState grownBombFlower = ZSSRegistries.BOMB_FLOWER.get().defaultBlockState().setValue(BombFlowerBlock.AGE, 3);
        helper.assertTrue(!grownBombFlower.getCollisionShape(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)).isEmpty(),
                "A grown Bomb Flower lost its age-dependent collision");

        ItemStack forestBigKey = new ItemStack(ZSSRegistries.getItem("big_key"));
        forestBigKey.getOrCreateTag().putString("dungeon", DungeonType.FOREST.id().toString());
        combatPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, forestBigKey);
        BlockPos desertDoorRelative = new BlockPos(12, 1, 1);
        BlockPos desertDoorAbsolute = helper.absolutePos(desertDoorRelative);
        helper.setBlock(desertDoorRelative, ZSSRegistries.DOOR_BOSS_DESERT.get());
        BlockState desertDoor = helper.getLevel().getBlockState(desertDoorAbsolute);
        ZSSRegistries.DOOR_BOSS_DESERT.get().use(desertDoor, helper.getLevel(), desertDoorAbsolute, combatPlayer,
                net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                        Vec3.atCenterOf(desertDoorAbsolute), net.minecraft.core.Direction.NORTH, desertDoorAbsolute, false));
        helper.assertTrue(!helper.getLevel().getBlockState(desertDoorAbsolute).getValue(LockedDoorBlock.UNLOCKED)
                        && forestBigKey.getCount() == 1,
                "A Forest Boss Key opened the Desert Boss Door");
        BlockPos forestDoorRelative = new BlockPos(13, 1, 1);
        BlockPos forestDoorAbsolute = helper.absolutePos(forestDoorRelative);
        helper.setBlock(forestDoorRelative, ZSSRegistries.DOOR_BOSS_FOREST.get());
        BlockState forestDoor = helper.getLevel().getBlockState(forestDoorAbsolute);
        ZSSRegistries.DOOR_BOSS_FOREST.get().use(forestDoor, helper.getLevel(), forestDoorAbsolute, combatPlayer,
                net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                        Vec3.atCenterOf(forestDoorAbsolute), net.minecraft.core.Direction.NORTH, forestDoorAbsolute, false));
        helper.assertTrue(helper.getLevel().getBlockState(forestDoorAbsolute).getValue(LockedDoorBlock.UNLOCKED),
                "The matching Forest Boss Key did not open the Forest Boss Door");

        StageNineBlockEntities.Storage jarStorage = new StageNineBlockEntities.Storage(BlockPos.ZERO,
                ZSSRegistries.CERAMIC_JAR.get().defaultBlockState());
        jarStorage.setJarContents(new ItemStack(ZSSRegistries.MASTER_ORE.get(), 3));
        net.minecraft.nbt.CompoundTag jarTag = jarStorage.saveWithoutMetadata();
        StageNineBlockEntities.Storage loadedJar = new StageNineBlockEntities.Storage(BlockPos.ZERO,
                ZSSRegistries.CERAMIC_JAR.get().defaultBlockState());
        loadedJar.load(jarTag);
        helper.assertTrue(loadedJar.getContainerSize() == 1 && loadedJar.getItem(0).is(ZSSRegistries.MASTER_ORE.get())
                        && loadedJar.getItem(0).getCount() == 3,
                "Ceramic jar contents did not survive BlockEntity serialization");
        StageNineBlockEntities.DungeonCore dungeonCore = new StageNineBlockEntities.DungeonCore(BlockPos.ZERO,
                ZSSRegistries.DUNGEON_CORE_STONE.get().defaultBlockState());
        helper.assertTrue(dungeonCore.setDungeonType(DungeonType.FOREST) && !dungeonCore.setDungeonType(DungeonType.END),
                "Dungeon core identity was mutable after assignment");
        StageNineBlockEntities.DungeonCore loadedCore = new StageNineBlockEntities.DungeonCore(BlockPos.ZERO,
                ZSSRegistries.DUNGEON_CORE_STONE.get().defaultBlockState());
        loadedCore.load(dungeonCore.saveWithoutMetadata());
        helper.assertTrue(loadedCore.dungeonType().orElseThrow() == DungeonType.FOREST,
                "Dungeon core ID did not survive serialization");

        BlockPos timeRelative = new BlockPos(9, 1, 1);
        BlockPos timeAbsolute = helper.absolutePos(timeRelative);
        helper.setBlock(timeRelative, ZSSRegistries.TIME_BLOCK.get());
        SongDefinition timeSong = SongCatalog.get(ZSSContentIds.TIME).orElseThrow();
        helper.assertTrue(ZSSRegistries.TIME_BLOCK.get().onSongPlayed(helper.getLevel(), timeAbsolute,
                        helper.getLevel().getBlockState(timeAbsolute), combatPlayer, timeSong, 5, 0)
                        && helper.getLevel().getBlockState(timeAbsolute).getValue(MechanismBlocks.TimeBlock.ETHEREAL),
                "Song of Time did not toggle the time block's ethereal state");
        BlockPos pegRelative = new BlockPos(10, 1, 1);
        BlockPos pegAbsolute = helper.absolutePos(pegRelative);
        helper.setBlock(pegRelative, ZSSRegistries.PEG_WOODEN.get());
        helper.assertTrue(ZSSBlockInteractions.smash(helper.getLevel(), pegAbsolute, combatPlayer,
                        new ItemStack(ZSSRegistries.getItem("wooden_hammer")), ZSSBlockInteractions.Weight.LIGHT,
                        net.minecraft.core.Direction.UP).consumesAction()
                        && helper.getLevel().getBlockState(pegAbsolute).getValue(MechanismBlocks.Peg.HITS) > 0,
                "Wooden peg did not accept a sufficient downward smash");
        BlockPos secretRelative = new BlockPos(11, 1, 1);
        BlockPos secretAbsolute = helper.absolutePos(secretRelative);
        helper.setBlock(secretRelative, ZSSRegistries.SECRET_STONE_STONE.get());
        helper.assertTrue(ZSSBlockInteractions.explode(helper.getLevel(), secretAbsolute,
                        ZSSBlockInteractions.ExplosionKind.BOMB, combatPlayer)
                        && helper.getLevel().getBlockState(secretAbsolute).is(Blocks.STONE),
                "Bomb interop did not reveal breakable secret stone");
        BlockPos scarecrowBase = new BlockPos(4, 1, 4);
        helper.setBlock(scarecrowBase, Blocks.HAY_BLOCK);
        helper.setBlock(scarecrowBase.above(), Blocks.HAY_BLOCK);
        helper.setBlock(scarecrowBase.above(2), Blocks.CARVED_PUMPKIN);
        helper.setBlock(scarecrowBase.above().east(), Blocks.HAY_BLOCK);
        helper.setBlock(scarecrowBase.above().west(), Blocks.HAY_BLOCK);
        helper.assertTrue(ScarecrowStructure.isScarecrowNear(helper.getLevel(), helper.absolutePos(scarecrowBase.above())),
                "The documented scarecrow structure was not recognized");
        BlockPos warpRelative = new BlockPos(7, 1, 1);
        BlockPos warpAbsolute = helper.absolutePos(warpRelative);
        var warpState = ZSSRegistries.WARP_STONE_MINUET.get().defaultBlockState();
        helper.getLevel().setBlock(warpAbsolute, warpState, 3);
        combatPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(ZSSRegistries.OCARINA_OF_TIME.get()));
        ZSSRegistries.WARP_STONE_MINUET.get().use(warpState, helper.getLevel(), warpAbsolute, combatPlayer,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(warpAbsolute),
                        net.minecraft.core.Direction.UP, warpAbsolute, false));
        ZSSPlayerData activatedWarpData = ZSSCapabilities.get(combatPlayer)
                .orElseThrow(() -> new AssertionError("Test player capability missing"));
        helper.assertTrue(activatedWarpData.warpPoints().get(ZSSContentIds.MINUET).pos().equals(warpAbsolute),
                "Warp Stone activation did not persist the matching song destination");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void creatureSpawnHabitatsAreDisjointAndDungeonReady(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var desert = biomes.getHolderOrThrow(Biomes.DESERT);
        var snowyPlains = biomes.getHolderOrThrow(Biomes.SNOWY_PLAINS);
        var swamp = biomes.getHolderOrThrow(Biomes.SWAMP);
        var plains = biomes.getHolderOrThrow(Biomes.PLAINS);
        var ocean = biomes.getHolderOrThrow(Biomes.OCEAN);
        var river = biomes.getHolderOrThrow(Biomes.RIVER);
        var deepDark = biomes.getHolderOrThrow(Biomes.DEEP_DARK);
        var netherWastes = biomes.getHolderOrThrow(Biomes.NETHER_WASTES);
        var crimsonForest = biomes.getHolderOrThrow(Biomes.CRIMSON_FOREST);
        var warpedForest = biomes.getHolderOrThrow(Biomes.WARPED_FOREST);
        var soulSandValley = biomes.getHolderOrThrow(Biomes.SOUL_SAND_VALLEY);
        var basaltDeltas = biomes.getHolderOrThrow(Biomes.BASALT_DELTAS);

        helper.assertTrue(desert.is(CreatureSpawnRules.HOT) && desert.is(CreatureSpawnRules.DRY)
                        && !desert.is(CreatureSpawnRules.COLD) && !desert.is(CreatureSpawnRules.WET)
                        && !desert.is(CreatureSpawnRules.GENERAL),
                "Hot/dry biome classification overlaps another Chu habitat");
        helper.assertTrue(snowyPlains.is(CreatureSpawnRules.COLD) && !snowyPlains.is(CreatureSpawnRules.HOT)
                        && !snowyPlains.is(CreatureSpawnRules.WET) && !snowyPlains.is(CreatureSpawnRules.GENERAL),
                "Cold biome classification overlaps another Chu habitat");
        helper.assertTrue(swamp.is(CreatureSpawnRules.WET) && !swamp.is(CreatureSpawnRules.HOT)
                        && !swamp.is(CreatureSpawnRules.COLD) && !swamp.is(CreatureSpawnRules.GENERAL),
                "Wet biome classification overlaps another Chu habitat");
        helper.assertTrue(plains.is(CreatureSpawnRules.GENERAL) && !plains.is(CreatureSpawnRules.HOT)
                        && !plains.is(CreatureSpawnRules.COLD) && !plains.is(CreatureSpawnRules.WET),
                "General biome classification overlaps a specialized Chu habitat");

        helper.assertTrue(CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.CHU.get(), desert)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.CHU_BLUE.get(), snowyPlains)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.CHU_YELLOW.get(), swamp)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.CHU_GREEN.get(), plains)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.KEESE_FIRE.get(), desert)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.KEESE_ICE.get(), snowyPlains)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.KEESE_THUNDER.get(), swamp)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.KEESE.get(), plains),
                "A Chu variant is not assigned to its exclusive biome class");
        helper.assertTrue(CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.BABA_DEKU.get(), plains)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.BABA_DEKU.get(), swamp)
                        && !CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.BABA_DEKU.get(), snowyPlains)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.BABA_WITHERED.get(), desert),
                "Deku Baba habitats overlap or include cold biomes");
        helper.assertTrue(CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.DARKNUT.get(), desert)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.DARKNUT.get(), snowyPlains)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.DARKNUT_MIGHTY.get(), swamp)
                        && !CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.DARKNUT.get(), deepDark)
                        && !CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.DARKNUT_MIGHTY.get(), deepDark)
                        && !CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.SKULLTULA.get(), deepDark),
                "Darknut all-biome or Deep Dark exclusion policy changed");
        helper.assertTrue(ocean.is(CreatureSpawnRules.WATER_RICH)
                        && CreatureSpawnRules.matchesOverworldBiome(ZSSRegistries.OCTOROK.get(), ocean),
                "Octorok is not assigned to water-rich Overworld biomes");
        helper.assertTrue(vanillaSpawnWeight(desert, ZSSRegistries.CHU.get()) == 0
                        && vanillaSpawnWeight(plains, ZSSRegistries.DARKNUT.get()) == 0
                        && vanillaSpawnWeight(ocean, ZSSRegistries.OCTOROK.get()) == 0
                        && vanillaSpawnWeight(netherWastes, ZSSRegistries.WIZZROBE.get()) == 0,
                "A hostile ZSS creature still competes in a vanilla biome spawn list");
        helper.assertTrue(ZssNaturalSpawner.SPAWN_INTERVAL_TICKS == 80
                        && ZssNaturalSpawner.LOCAL_CAP == 18
                        && ZssNaturalSpawner.LOCAL_CAP_RADIUS == 96
                        && ZssNaturalSpawner.independentlySpawns(ZSSRegistries.CHU.get())
                        && ZssNaturalSpawner.independentlySpawns(ZSSRegistries.WIZZROBE_WIND.get())
                        && ZssNaturalSpawner.independentlySpawns(ZSSRegistries.DARKNUT_MIGHTY.get()),
                "Independent ZSS distribution bounds or membership changed");
        helper.assertTrue(ZssNaturalSpawner.spawnWeight(ZSSRegistries.CHU.get(), netherWastes) == 16
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.CHU_GREEN.get(), plains) == 10
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.DARKNUT.get(), netherWastes) == 1
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.DARKNUT_MIGHTY.get(), plains) == 1
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.OCTOROK.get(), ocean) == 4
                        && vanillaSpawnWeight(ocean, EntityType.DROWNED) == 5
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.OCTOROK.get(), river) == 90
                        && vanillaSpawnWeight(river, EntityType.DROWNED) == 100,
                "Independent relative weights or Octorok/Drowned balance changed");
        helper.assertTrue(ZssNaturalSpawner.spawnWeight(ZSSRegistries.WIZZROBE.get(), netherWastes) == 1
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.WIZZROBE.get(), crimsonForest) == 2
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.WIZZROBE_ICE.get(), warpedForest) == 2
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.WIZZROBE_LIGHTNING.get(), soulSandValley) == 2
                        && ZssNaturalSpawner.spawnWeight(ZSSRegistries.WIZZROBE_WIND.get(), basaltDeltas) == 2,
                "Wizzrobe weights no longer match the requested low-frequency distribution");
        helper.assertTrue(CreatureSpawnRules.matchesBiome(ZSSRegistries.OCTOROK_BOMB.get(), Level.NETHER, basaltDeltas)
                        && !CreatureSpawnRules.matchesBiome(ZSSRegistries.OCTOROK_BOMB.get(), Level.NETHER, netherWastes)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.BABA_FIRE.get(), Level.NETHER, crimsonForest)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.BABA_FIRE.get(), Level.NETHER, warpedForest)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.KEESE_CURSED.get(), Level.NETHER, soulSandValley)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.WIZZROBE.get(), Level.NETHER, netherWastes)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.WIZZROBE.get(), Level.NETHER, crimsonForest)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.WIZZROBE_ICE.get(), Level.NETHER, warpedForest)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.WIZZROBE_LIGHTNING.get(), Level.NETHER, soulSandValley)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.WIZZROBE_WIND.get(), Level.NETHER, basaltDeltas)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.DARKNUT.get(), Level.NETHER, warpedForest)
                        && CreatureSpawnRules.matchesBiome(ZSSRegistries.DARKNUT_MIGHTY.get(), Level.NETHER, soulSandValley),
                "A Nether creature is assigned to the wrong biome");
        helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.getPlacementType(ZSSRegistries.OCTOROK.get())
                        == net.minecraft.world.entity.SpawnPlacements.Type.IN_WATER
                        && net.minecraft.world.entity.SpawnPlacements.getPlacementType(ZSSRegistries.OCTOROK_BOMB.get())
                        == net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                "Octorok placement does not match water and Basalt Deltas spawning");
        LegacyCreature normalOctorok = ZSSRegistries.OCTOROK.get().create(helper.getLevel());
        List<RegistryObject<EntityType<LegacyCreature>>> fireproofNetherTypes = List.of(
                ZSSRegistries.CHU, ZSSRegistries.DARKNUT, ZSSRegistries.DARKNUT_MIGHTY,
                ZSSRegistries.BABA_FIRE, ZSSRegistries.KEESE_CURSED,
                ZSSRegistries.OCTOROK_BOMB, ZSSRegistries.WIZZROBE, ZSSRegistries.WIZZROBE_ICE,
                ZSSRegistries.WIZZROBE_LIGHTNING, ZSSRegistries.WIZZROBE_WIND);
        helper.assertTrue(normalOctorok != null && normalOctorok.canBreatheUnderwater()
                        && fireproofNetherTypes.stream().allMatch(type -> type.get().create(helper.getLevel()).fireImmune()),
                "Octorok drowning immunity or Nether creature fire immunity is missing");
        helper.assertTrue(ZssNaturalSpawner.maxGroupSize(ZSSRegistries.CHU_GREEN.get()) == 3
                        && ZssNaturalSpawner.maxGroupSize(ZSSRegistries.KEESE.get()) == 5
                        && ZssNaturalSpawner.maxGroupSize(ZSSRegistries.DARKNUT.get()) == 2
                        && ZssNaturalSpawner.maxGroupSize(ZSSRegistries.DARKNUT_MIGHTY.get()) == 2
                        && ZssNaturalSpawner.maxGroupSize(ZSSRegistries.SKULLTULA_GOLD.get()) == 2,
                "Independent group sizes changed unexpectedly");

        helper.assertTrue(!ZSSRegistries.DARKNUT_MIGHTY.get().is(CreatureSpawnRules.DUNGEON_ONLY)
                        && !ZSSRegistries.WIZZROBE.get().is(CreatureSpawnRules.DUNGEON_ONLY)
                        && ZSSRegistries.DARKNUT_BOSS.get().is(CreatureSpawnRules.DUNGEON_ONLY)
                        && ZSSRegistries.WIZZROBE_GRAND.get().is(CreatureSpawnRules.DUNGEON_ONLY)
                        && ZSSRegistries.DESERT_BOSS.get().is(CreatureSpawnRules.DUNGEON_ONLY)
                        && !ZSSRegistries.DARKNUT.get().is(CreatureSpawnRules.DUNGEON_ONLY),
                "Dungeon-only creature tag does not expose the intended later-stage spawn boundary");
        helper.assertTrue(vanillaSpawnWeight(plains, ZSSRegistries.DARKNUT.get()) == 0
                        && vanillaSpawnWeight(plains, ZSSRegistries.DARKNUT_MIGHTY.get()) == 0
                        && vanillaSpawnWeight(plains, ZSSRegistries.WIZZROBE.get()) == 0
                        && vanillaSpawnWeight(plains, ZSSRegistries.DARKNUT_BOSS.get()) == 0
                        && vanillaSpawnWeight(plains, ZSSRegistries.WIZZROBE_GRAND.get()) == 0,
                "A ZSS creature leaked back into vanilla Overworld spawning");
        helper.assertTrue(CreatureSpawnRules.matchesNaturalHabitat(ZSSRegistries.SKULLTULA.get(), helper.getLevel(),
                        new BlockPos(0, CreatureSpawnRules.DEEP_CAVE_MAX_Y, 0))
                        && !CreatureSpawnRules.matchesNaturalHabitat(ZSSRegistries.SKULLTULA.get(), helper.getLevel(),
                        new BlockPos(0, CreatureSpawnRules.DEEP_CAVE_MAX_Y + 1, 0))
                        && CreatureSpawnRules.matchesNaturalHabitat(ZSSRegistries.DARKNUT.get(), helper.getLevel(),
                        new BlockPos(0, CreatureSpawnRules.DEEP_CAVE_MAX_Y, 0))
                        && CreatureSpawnRules.matchesNaturalHabitat(ZSSRegistries.DARKNUT_MIGHTY.get(), helper.getLevel(),
                        new BlockPos(0, CreatureSpawnRules.DEEP_CAVE_MAX_Y, 0))
                        && !CreatureSpawnRules.matchesNaturalHabitat(ZSSRegistries.CHU_GREEN.get(), helper.getLevel(),
                        new BlockPos(0, CreatureSpawnRules.DEEP_CAVE_MAX_Y, 0))
                        && CreatureSpawnRules.GOLD_SKULLTULA_CHANCE == 100
                        && CreatureSpawnRules.MIGHTY_DARKNUT_CHANCE == 100,
                "Deep-cave whitelist or rare-variant boundary changed");
        helper.succeed();
    }

    @GameTest(template = "dungeon_empty", templateNamespace = "minecraft")
    public static void bossDungeonStructuresRecreateLegacyRooms(GameTestHelper helper) {
        List<ResourceLocation> expectedIds = List.of("desert", "mountain", "hell", "forest", "taiga", "ocean", "end")
                .stream().map(ZSSGameTests::id).toList();
        helper.assertTrue(java.util.Arrays.stream(DungeonType.values()).map(DungeonType::id).toList().equals(expectedIds),
                "The seven stable dungeon IDs changed");

        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        helper.assertTrue(biomes.getHolderOrThrow(Biomes.DESERT).is(DungeonType.DESERT.biomeTag())
                        && biomes.getHolderOrThrow(Biomes.MEADOW).is(DungeonType.EARTH.biomeTag())
                        && biomes.getHolderOrThrow(Biomes.CRIMSON_FOREST).is(DungeonType.FIRE.biomeTag())
                        && biomes.getHolderOrThrow(Biomes.FOREST).is(DungeonType.FOREST.biomeTag())
                        && biomes.getHolderOrThrow(Biomes.TAIGA).is(DungeonType.ICE.biomeTag())
                        && biomes.getHolderOrThrow(Biomes.OCEAN).is(DungeonType.WATER.biomeTag())
                        && biomes.getHolderOrThrow(Biomes.END_HIGHLANDS).is(DungeonType.END.biomeTag())
                        && biomes.getHolderOrThrow(Biomes.END_MIDLANDS).is(DungeonType.END.biomeTag())
                        && biomes.holders().filter(biome -> biome.is(DungeonType.END.biomeTag()))
                        .allMatch(biome -> biome.is(Biomes.END_HIGHLANDS) || biome.is(Biomes.END_MIDLANDS)),
                "A stable dungeon type lost its modern biome tag");

        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Registry<StructureSet> structureSets = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        helper.assertTrue(biomes.holders().filter(biome -> biome.is(DungeonType.FIRE.biomeTag()))
                        .allMatch(biome -> biome.is(Biomes.CRIMSON_FOREST)), "Fire Dungeon allows biomes outside Crimson Forest");
        var firePlacement = net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                        structureSets.get(id("nether_boss_dungeons")).placement()).result().orElseThrow().getAsJsonObject();
        helper.assertTrue(firePlacement.get("frequency").getAsFloat() == .125F
                        && firePlacement.get("spacing").getAsInt() == 48
                        && firePlacement.get("separation").getAsInt() == 16,
                "Fire Dungeon must use sparse placement above the Nether roof");
        var windPlacement = net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                        structureSets.get(id("end_boss_dungeons")).placement()).result().orElseThrow().getAsJsonObject();
        helper.assertTrue(windPlacement.get("frequency").getAsFloat() == .125F
                        && windPlacement.get("spacing").getAsInt() == 48
                        && windPlacement.get("separation").getAsInt() == 16
                        && structureSets.get(id("end_boss_dungeons")).structures().size() == 1
                        && structureSets.get(id("end_boss_dungeons")).structures().get(0).structure().value()
                        == structures.get(DungeonStructureDataProvider.structureId(DungeonType.END))
                        && structureSets.get(id("boss_dungeons")).structures().size() == 5
                        && structureSets.get(id("boss_dungeons")).structures().stream().noneMatch(entry ->
                        entry.structure().value() == structures.get(DungeonStructureDataProvider.structureId(DungeonType.END))),
                "Wind must have its own rare End structure set, separate from the five Overworld temples");
        var fireStructure = Structure.DIRECT_CODEC.encodeStart(
                net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                structures.get(DungeonStructureDataProvider.structureId(DungeonType.FIRE))).result().orElseThrow().getAsJsonObject();
        helper.assertTrue(fireStructure.getAsJsonObject("start_height").get("absolute").getAsInt() == 129
                        && !fireStructure.has("project_start_to_heightmap")
                        && structures.get(DungeonStructureDataProvider.structureId(DungeonType.FIRE)).terrainAdaptation()
                        == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE,
                "Fire floor must land at Y=128 without terrain deformation or roof heightmap projection");
        helper.assertTrue(java.util.Arrays.stream(DungeonType.values())
                        .allMatch(type -> structures.containsKey(DungeonStructureDataProvider.structureId(type)))
                        && structureSets.containsKey(id("boss_dungeons"))
                        && structureSets.containsKey(id("nether_boss_dungeons")),
                "The seven Boss Dungeon world-generation entries or their shared structure sets were not loaded");

        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        List<Block> doors = List.of(ZSSRegistries.DOOR_BOSS_DESERT.get(), ZSSRegistries.DOOR_BOSS_EARTH.get(),
                ZSSRegistries.DOOR_BOSS_FIRE.get(), ZSSRegistries.DOOR_BOSS_FOREST.get(),
                ZSSRegistries.DOOR_BOSS_ICE.get(), ZSSRegistries.DOOR_BOSS_WATER.get(),
                ZSSRegistries.DOOR_BOSS_END.get());
        List<Block> centerPieces = List.of(Blocks.CHEST, Blocks.CHEST, ZSSRegistries.SACRED_FLAME_DIN.get(),
                ZSSRegistries.PEDESTAL.get(), Blocks.AIR, Blocks.CHEST,
                ZSSRegistries.SACRED_FLAME_NAYRU.get());
        StageNineBlockEntities.DungeonCore windCore = null;
        BlockPos windCorePos = null;
        for (int index = 0; index < DungeonType.values().length; index++) {
            DungeonType type = DungeonType.values()[index];
            var template = helper.getLevel().getStructureManager().get(DungeonStructureDataProvider.templateId(type, 0));
            helper.assertTrue(template.isPresent()
                            && template.orElseThrow().getSize().equals(new BlockPos(33, 18, 33)),
                    type + " did not load the 3x3x2 enlarged minimum template");
            helper.assertTrue(template.orElseThrow().placeInWorld(helper.getLevel(), origin, origin,
                            new StructurePlaceSettings(), helper.getLevel().random, Block.UPDATE_ALL),
                    type + " Boss Dungeon structure template could not be placed");

            BlockPos corePos = origin.offset(16, 0, 16);
            Block expectedCore = type == DungeonType.DESERT || type == DungeonType.WATER
                    ? ZSSRegistries.DUNGEON_CORE_SANDSTONE.get() : ZSSRegistries.DUNGEON_CORE_STONE.get();
            BlockState generatedCoreState = helper.getLevel().getBlockState(corePos);
            helper.assertTrue(generatedCoreState.is(expectedCore)
                            && generatedCoreState.getValue(DungeonBlocks.Core.SEALED),
                    type + " did not place its sealed center core: " + generatedCoreState);
            helper.assertTrue(helper.getLevel().getBlockEntity(corePos) instanceof StageNineBlockEntities.DungeonCore,
                    type + " generated a Dungeon Core without its BlockEntity");
            StageNineBlockEntities.DungeonCore core = (StageNineBlockEntities.DungeonCore) helper.getLevel().getBlockEntity(corePos);
            helper.assertTrue(core.dungeonType().orElse(null) == type && !core.completed(),
                    type + " core was not initialized with stable ID " + type.id());

            int doorY = type == DungeonType.WATER || type == DungeonType.END ? 2 : 1;
            BlockState lowerDoor = helper.getLevel().getBlockState(origin.offset(16, doorY, 1));
            BlockState upperDoor = helper.getLevel().getBlockState(origin.offset(16, doorY + 1, 1));
            helper.assertTrue(lowerDoor.is(doors.get(index))
                            && lowerDoor.getValue(LockedDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                            && upperDoor.is(doors.get(index))
                            && upperDoor.getValue(LockedDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER,
                    type + " did not place its paired Boss Door");

            int rewardY = type == DungeonType.END ? 3 : 2;
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(16, rewardY, 16)).is(centerPieces.get(index)),
                    type + " lost its legacy center chest, pedestal, or Sacred Flame");
            helper.assertTrue(!helper.getLevel().getBlockState(origin.offset(0, 16, 0)).isAir()
                            && !helper.getLevel().getBlockState(origin.offset(0, 17, 0)).isAir(),
                    type + " lost the original stair eave or crenellated roof parapet");

            if (type == DungeonType.END) {
                windCore = core;
                windCorePos = corePos;
                ZSSWorldData.get(helper.getLevel()).setDungeonState(
                        DungeonController.instanceId(helper.getLevel(), corePos), false, 0L);
            }
        }

        StageNineBlockEntities.DungeonCore completedCore = windCore;
        BlockPos completedCorePos = windCorePos;
        LegacyCreature boss = (LegacyCreature) DungeonController.spawnBoss(helper.getLevel(), completedCore)
                .orElseThrow(() -> new AssertionError("The End Temple controller did not spawn its boss"));
        helper.assertTrue(boss.getType() == ZSSRegistries.WIZZROBE_GRAND.get()
                        && boss.getType().is(CreatureSpawnRules.DUNGEON_ONLY)
                        && boss.dungeonType().orElse(null) == DungeonType.END
                        && boss.dungeonCorePos().orElseThrow().equals(completedCorePos),
                "The controller did not explicitly link the tagged Grand Wizzrobe to the End Temple core");
        boss.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        ResourceLocation windInstance = DungeonController.instanceId(helper.getLevel(), completedCorePos);
        helper.assertTrue(completedCore.completed()
                        && ZSSWorldData.get(helper.getLevel()).dungeons().get(windInstance).completed()
                        && !helper.getLevel().getBlockState(completedCorePos).getValue(DungeonBlocks.Core.SEALED),
                "Killing the linked End Temple boss did not complete and unseal its exact core");

        BlockPos earthOrigin = origin.offset(48, 0, 0);
        var earthTemplate = helper.getLevel().getStructureManager().get(
                DungeonStructureDataProvider.templateId(DungeonType.EARTH, 0)).orElseThrow();
        helper.assertTrue(earthTemplate.placeInWorld(helper.getLevel(), earthOrigin, earthOrigin,
                        new StructurePlaceSettings(), helper.getLevel().random, Block.UPDATE_ALL),
                "The Earth Dungeon structure could not be placed for its encounter test");
        BlockPos earthCorePos = earthOrigin.offset(16, 0, 16);
        StageNineBlockEntities.DungeonCore earthCore = (StageNineBlockEntities.DungeonCore)
                helper.getLevel().getBlockEntity(earthCorePos);
        ResourceLocation earthInstance = DungeonController.instanceId(helper.getLevel(), earthCorePos);
        ZSSWorldData.get(helper.getLevel()).setDungeonState(earthInstance, false, 0L);
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        LegacyCreature earthBoss = (LegacyCreature) DungeonController.spawnBoss(helper.getLevel(), earthCore)
                .orElseThrow(() -> new AssertionError("The Earth Dungeon controller did not spawn its boss"));
        helper.assertTrue(earthBoss.getType() == ZSSRegistries.DARKNUT_BOSS.get()
                        && earthBoss.dungeonType().orElse(null) == DungeonType.EARTH
                        && earthBoss.dungeonCorePos().orElseThrow().equals(earthCorePos)
                        && zeldaswordskills_remastered.worldgen.DungeonArena.contains(earthCore, earthBoss.getBoundingBox())
                        && helper.getLevel().noCollision(earthBoss),
                "Earth did not spawn its linked Black Knight safely inside the room");
        helper.assertTrue(earthCore.arenaRadius() == 15 && earthCore.arenaHeight() == 16
                        && earthCore.doorSide().isPresent() && earthCore.nextHazardTick() > helper.getLevel().getGameTime(),
                "Earth encounter geometry or its first falling-bomb timer was not initialized");
        long persistedHazard = earthCore.nextHazardTick();
        StageNineBlockEntities.DungeonCore reloadedEarth = new StageNineBlockEntities.DungeonCore(earthCorePos,
                earthCore.getBlockState());
        reloadedEarth.load(earthCore.saveWithoutMetadata());
        helper.assertTrue(reloadedEarth.nextHazardTick() == persistedHazard
                        && reloadedEarth.arenaRadius() == earthCore.arenaRadius()
                        && reloadedEarth.doorSide().equals(earthCore.doorSide()),
                "An active Earth encounter did not survive Dungeon Core serialization");
        earthCore.setNextHazardTick(helper.getLevel().getGameTime());
        DungeonController.tick(helper.getLevel(), earthCore);
        for (int wave = 0; wave < 20; wave++) {
            earthCore.setNextHazardTick(helper.getLevel().getGameTime());
            DungeonController.tick(helper.getLevel(), earthCore);
            earthBoss.aiStep();
        }
        helper.assertTrue(earthCore.bossUuids().size() == 1
                        && helper.getLevel().getEntitiesOfClass(LegacyCreature.class, new AABB(earthCorePos).inflate(12),
                        entity -> entity.kind() == LegacyCreature.Kind.DARKNUT_MIGHTY).isEmpty(),
                "Earth must not spawn Mighty Darknut reinforcements during repeated hazards");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(zeldaswordskills_remastered.entity.projectile.ThrownBomb.class,
                        new AABB(earthCorePos).inflate(24.0D)).isEmpty(),
                "Earth did not produce its difficulty-scaled falling-bomb hazard");
        earthBoss.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(earthCore.completed()
                        && ZSSWorldData.get(helper.getLevel()).dungeons().get(earthInstance).completed()
                        && ZSSWorldData.get(helper.getLevel()).dungeons().get(windInstance).completed()
                        && !earthInstance.equals(windInstance),
                "Killing the Earth boss did not complete its own persistent dungeon instance");

        helper.getLevel().getServer().setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @GameTest(template = "dungeon_empty", templateNamespace = "minecraft", batch = "zssFire")
    public static void fireDungeonLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos center = origin.offset(16, 0, 16);
        var template = level.getStructureManager().get(DungeonStructureDataProvider.templateId(DungeonType.FIRE, 0)).orElseThrow();
        ResourceLocation instance = DungeonController.instanceId(level, center);
        try {
            for (var difficulty : List.of(net.minecraft.world.Difficulty.EASY, net.minecraft.world.Difficulty.NORMAL,
                    net.minecraft.world.Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                helper.assertTrue(template.placeInWorld(level, origin, origin,
                        new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), level.random, 2),
                        "Fire template placement failed");
                var core = (StageNineBlockEntities.DungeonCore) level.getBlockEntity(center);
                ZSSWorldData.get(level).setDungeonState(instance, false, 0L);
                LegacyCreature first = (LegacyCreature) DungeonController.spawnBoss(level, core).orElseThrow();
                var bosses = core.bossUuids().stream().map(level::getEntity).map(entity -> (LegacyCreature) entity).toList();
                helper.assertTrue(bosses.size() == 4 && bosses.stream().allMatch(entity ->
                                entity instanceof zeldaswordskills_remastered.entity.FireBossCreature && entity.fireImmune()
                                && entity.getMaxHealth() == 150.0F
                                && entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                                == 10 + 2 * difficulty.getId()
                                && entity.dungeonCorePos().orElseThrow().equals(center)
                                && entity.getType().is(CreatureSpawnRules.DUNGEON_ONLY)),
                        "Fire boss identity, count, attributes or instance binding changed");
                helper.assertTrue(bosses.stream().map(entity -> ((zeldaswordskills_remastered.entity.FireBossCreature) entity).element())
                                .collect(java.util.stream.Collectors.toSet()).equals(java.util.Set.of(
                                        ToolProjectile.Mode.FIRE, ToolProjectile.Mode.ICE, ToolProjectile.Mode.LIGHTNING, ToolProjectile.Mode.WIND)),
                        "Fire must spawn exactly one caster of each element");
                var beforeRepeatedActivation = core.bossUuids();
                DungeonController.spawnBoss(level, core).orElseThrow();
                helper.assertTrue(core.bossUuids().equals(beforeRepeatedActivation), "Repeated activation duplicated Fire bosses");
                helper.assertTrue(first.getItemBySlot(EquipmentSlot.CHEST).is(switch (difficulty) {
                    case EASY -> Items.CHAINMAIL_CHESTPLATE;
                    case NORMAL -> Items.IRON_CHESTPLATE;
                    default -> Items.DIAMOND_CHESTPLATE;
                }), "Fire boss difficulty-scaled armor was missing");
                CompoundTag entityTag = first.saveWithoutId(new CompoundTag());
                LegacyCreature reloadedBoss = ZSSRegistries.FIRE_BOSS.get().create(level);
                reloadedBoss.load(entityTag);
                helper.assertTrue(reloadedBoss.getMaxHealth() == first.getMaxHealth()
                                && reloadedBoss.dungeonCorePos().equals(first.dungeonCorePos())
                                && ((zeldaswordskills_remastered.entity.FireBossCreature) reloadedBoss).element()
                                == ((zeldaswordskills_remastered.entity.FireBossCreature) first).element(),
                        "Fire boss attributes or binding lost on reload");

                level.getEntitiesOfClass(net.minecraft.world.entity.projectile.SmallFireball.class,
                        new AABB(center).inflate(10)).forEach(Entity::discard);
                // Run the real controller at the original first hazard boundary.
                while (core.fireBattleTicks() < 150) DungeonController.tick(level, core);
                helper.assertTrue(core.fireReinforcements().size() == (difficulty == net.minecraft.world.Difficulty.HARD ? 1 : 0),
                        "Fire first reinforcement phase changed");
                if (difficulty != net.minecraft.world.Difficulty.EASY) {
                    if (difficulty == net.minecraft.world.Difficulty.HARD) {
                        setFireBattleTick(core, 399);
                        DungeonController.tick(level, core);
                    }
                    var fireballs = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.SmallFireball.class,
                            new AABB(center).inflate(10));
                    helper.assertTrue(fireballs.isEmpty() && !hasFireLavaAboveFloor(level, center),
                            "Fire must not drop ceiling fireballs or place lava at the former hazard phase");
                }
                if (difficulty == net.minecraft.world.Difficulty.NORMAL) {
                    setFireBattleTick(core, 399);
                    DungeonController.tick(level, core);
                }
                if (difficulty != net.minecraft.world.Difficulty.EASY) {
                    helper.assertTrue(!core.fireReinforcements().isEmpty(), "Fire did not spawn a scheduled reinforcement");
                    var reinforcement = (net.minecraft.world.entity.Mob) level.getEntity(core.fireReinforcements().iterator().next());
                    boolean hard = difficulty == net.minecraft.world.Difficulty.HARD;
                    helper.assertTrue(reinforcement.getType() == EntityType.WITHER_SKELETON
                                    && reinforcement.getMaxHealth() == (hard ? 300 : 20)
                                    && reinforcement.getMainHandItem().is(hard ? Items.DIAMOND_SWORD : Items.STONE_SWORD),
                            "Fire reinforcement type, health or weapon changed");
                    if (hard) helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                                    net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, reinforcement.getMainHandItem()) == 4
                                    && reinforcement.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE),
                            "Hard Fire reinforcement lost its enchanted boss equipment");
                    // Opening difficulty, not a later server change, determines subsequent waves.
                    level.getServer().setDifficulty(net.minecraft.world.Difficulty.EASY, true);
                    int count = core.fireReinforcements().size();
                    setFireBattleTick(core, 6000L + (800 - 50 * difficulty.getId()) - 1);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.fireReinforcements().size() == count + 1
                                    && core.fireBattleDifficulty() == difficulty.getId(),
                            "Fire difficulty snapshot or long-battle phase was lost");
                }
                for (int dx : new int[]{-12, 12}) for (int dz : new int[]{-12, 12})
                    for (int y = 1; y <= 4; y++) level.setBlock(center.offset(dx, y, dz), Blocks.END_STONE.defaultBlockState(), 2);
                setFireBattleTick(core, 499);
                DungeonController.tick(level, core);
                long remainingPillars = BlockPos.betweenClosedStream(center.offset(-12, 1, -12), center.offset(12, 4, 12))
                        .filter(pos -> Math.abs(pos.getX() - center.getX()) == 12 && Math.abs(pos.getZ() - center.getZ()) == 12)
                        .filter(pos -> level.getBlockState(pos).is(Blocks.END_STONE)).count();
                helper.assertTrue(remainingPillars < 16 && level.getBlockEntity(center) == core
                                && level.getBlockState(center.above(2)).is(ZSSRegistries.SACRED_FLAME_DIN.get())
                                && level.getBlockState(center.offset(-15, 3, 0)).is(ZSSRegistries.SECRET_STONE_NETHER_WART_BLOCK.get()),
                        "Fire pillar collapse failed or destroyed its shell/core/reward");
                if (difficulty == net.minecraft.world.Difficulty.EASY) helper.assertTrue(core.fireReinforcements().isEmpty()
                                && level.getEntitiesOfClass(net.minecraft.world.entity.projectile.SmallFireball.class,
                                new AABB(center).inflate(10)).isEmpty()
                                && !hasFireLavaAboveFloor(level, center), "Easy Fire enabled fireballs, lava or reinforcements");
                var reloaded = new StageNineBlockEntities.DungeonCore(center, core.getBlockState());
                reloaded.load(core.saveWithoutMetadata());
                helper.assertTrue(reloaded.bossUuids().equals(core.bossUuids())
                                && reloaded.fireReinforcements().equals(core.fireReinforcements())
                                && reloaded.fireBattleTicks() == core.fireBattleTicks()
                                && reloaded.fireBattleDifficulty() == difficulty.getId(),
                        "Fire encounter did not survive core serialization");
                // A temporarily unloaded participant must block completion and replacement.
                java.util.UUID missing = java.util.UUID.randomUUID();
                core.addFireReinforcement(missing);
                long paused = core.fireBattleTicks();
                DungeonController.tick(level, core);
                helper.assertTrue(core.fireBattleTicks() == paused, "Missing Fire participant did not pause encounter");
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(center).inflate(10))
                        .forEach(Entity::discard);
                bosses.forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
                helper.assertTrue(!core.completed() && DungeonController.spawnBoss(level, core).isEmpty(),
                        "Fire completed or respawned while a participant was unloaded");
                core.removeFireReinforcement(missing);
                for (java.util.UUID uuid : core.fireReinforcements()) {
                    var mob = (net.minecraft.world.entity.Mob) level.getEntity(uuid);
                    mob.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                }
                DungeonController.tick(level, core);
                helper.assertTrue(core.completed() && ZSSWorldData.get(level).dungeons().get(instance).completed()
                                && !level.getBlockState(center).getValue(DungeonBlocks.Core.SEALED)
                                && !hasFireLavaAboveFloor(level, center)
                                && BlockPos.betweenClosedStream(center.offset(-3, 1, -3), center.offset(3, 1, 3))
                                .noneMatch(pos -> level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)),
                        "Fire victory did not persist, unseal and remove lava");
                long rewards = countFireRewards(level, center);
                DungeonController.tick(level, core);
                helper.assertTrue(rewards == 1 && countFireRewards(level, center) == 1
                                && DungeonController.spawnBoss(level, core).isEmpty(), "Fire victory reward was missing or duplicated");
                BlockPos secondPos = center.offset(16, 0, 0);
                helper.assertTrue(!ZSSWorldData.get(level).dungeons().containsKey(DungeonController.instanceId(level, secondPos)),
                        "Fire completion leaked to another instance");
                level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(center).inflate(32)).forEach(Entity::discard);
            }
            template.placeInWorld(level, origin, origin,
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), level.random, 2);
            var core = (StageNineBlockEntities.DungeonCore) level.getBlockEntity(center);
            ZSSWorldData.get(level).setDungeonState(instance, false, 0L);
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.HARD, true);
            DungeonController.spawnBoss(level, core).orElseThrow();
            setFireBattleTick(core, 149);
            DungeonController.tick(level, core);
            long rewards = countFireRewards(level, center);
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            DungeonController.tick(level, core);
            helper.assertTrue(!core.completed() && core.bossUuids().isEmpty() && core.fireReinforcements().isEmpty()
                            && core.fireBattleDifficulty() == 0 && countFireRewards(level, center) == rewards,
                    "Peaceful cancellation completed or rewarded Fire encounter");
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            DungeonController.spawnBoss(level, core).orElseThrow();
            helper.assertTrue(core.bossUuids().size() == 4 && core.fireBattleTicks() == 0
                            && core.fireBattleDifficulty() == 2, "Cancelled Fire battle could not restart");
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(center).inflate(32)).forEach(Entity::discard);
        } finally {
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "dungeon_empty", templateNamespace = "minecraft", batch = "zssFire")
    public static void templeWizzrobesUseElementalSpells(GameTestHelper helper) {
        var level = helper.getLevel();
        var previous = level.getDifficulty();
        var boss = (zeldaswordskills_remastered.entity.FireBossCreature) ZSSRegistries.FIRE_BOSS.get().create(level);
        var wind = (zeldaswordskills_remastered.entity.WizzrobeCreature) ZSSRegistries.WIZZROBE_GRAND.get().create(level);
        var ordinary = (zeldaswordskills_remastered.entity.WizzrobeCreature) ZSSRegistries.WIZZROBE.get().create(level);
        var target = EntityType.PIG.create(level);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 15, 5));
        boss.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        target.moveTo(pos.getX() + 10.5, pos.getY(), pos.getZ() + .5, 0, 0);
        target.setNoAi(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        level.addFreshEntity(boss);
        level.addFreshEntity(target);
        wind.setPos(boss.position());
        ordinary.setPos(boss.position());
        level.addFreshEntity(wind);
        level.addFreshEntity(ordinary);
        var area = new AABB(pos).inflate(20);
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                        net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
                for (ToolProjectile.Mode element : List.of(ToolProjectile.Mode.FIRE, ToolProjectile.Mode.ICE,
                        ToolProjectile.Mode.LIGHTNING, ToolProjectile.Mode.WIND)) {
                    boss.setElement(element);
                    var reloaded = (zeldaswordskills_remastered.entity.FireBossCreature) ZSSRegistries.FIRE_BOSS.get().create(level);
                    reloaded.load(boss.saveWithoutId(new CompoundTag()));
                    helper.assertTrue(reloaded.element() == element && reloaded.getMaxHealth() == 150
                                    && ZSSGameTests.class.getClassLoader().getResource(
                                    "assets/zeldaswordskills_remastered/textures/entity/" + reloaded.texturePath() + ".png") != null,
                            "Fire elemental identity, health or texture lost on reload");
                    assertCasterHit(helper, boss, target, 10 + 2 * difficulty.getId(), element, area);
                }
                assertCasterHit(helper, wind, target, 20 + 4 * difficulty.getId(), null, area);
                assertCasterHit(helper, ordinary, target, 4 * difficulty.getId(), ToolProjectile.Mode.FIRE, area);
                level.getServer().setDifficulty(Difficulty.EASY, true);
                assertCasterHit(helper, boss, target, 10 + 2 * difficulty.getId(), ToolProjectile.Mode.WIND, area);
            }
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.projectile.SmallFireball.class, area).isEmpty(),
                    "Elemental casters must not use Blaze fireballs");
        } finally {
            level.getEntitiesOfClass(ToolProjectile.class, area).forEach(Entity::discard);
            boss.discard(); wind.discard(); ordinary.discard(); target.discard();
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    private static void assertCasterHit(GameTestHelper helper, zeldaswordskills_remastered.entity.WizzrobeCreature caster,
            LivingEntity target, float damage, ToolProjectile.Mode element, AABB area) {
        target.setHealth(200);
        target.invulnerableTime = 0;
        target.clearFire();
        target.removeAllEffects();
        caster.performRangedAttack(target, 1);
        var spells = helper.getLevel().getEntitiesOfClass(ToolProjectile.class, area, spell -> spell.getOwner() == caster);
        helper.assertTrue(spells.size() == 1, "Caster did not fire exactly one owned spell");
        var spell = spells.get(0);
        CompoundTag saved = spell.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.getFloat("configured_damage") == damage && (element == null || spell.mode() == element),
                "Spell base damage or element changed");
        // Exercise the actual projectile collision and server damage path with deterministic aim.
        spell.setPos(target.getX() - 2, target.getY() + .5, target.getZ());
        spell.setDeltaMovement(.5, 0, 0);
        spell.setNoGravity(true);
        for (int tick = 0; tick < 8 && spell.isAlive(); tick++) spell.tick();
        helper.assertTrue(!spell.isAlive() && Math.abs(target.getHealth() - (200 - damage)) < .001F,
                "Actual elemental projectile damage differs from its base value: " + damage);
        if (element == ToolProjectile.Mode.FIRE) helper.assertTrue(target.isOnFire(), "Fire spell lost ignition");
        if (element == ToolProjectile.Mode.ICE) helper.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "Ice spell lost slowing");
        if (element == ToolProjectile.Mode.LIGHTNING) helper.assertTrue(target.hasEffect(ZSSRegistries.STUN.get()), "Lightning spell lost stun");
        if (element == ToolProjectile.Mode.WIND) helper.assertTrue(target.getDeltaMovement().y > 0, "Wind spell lost knockback");
    }

    private static void setFireBattleTick(StageNineBlockEntities.DungeonCore core, long tick) {
        CompoundTag tag = core.saveWithoutMetadata();
        tag.putLong("fire_battle_ticks", tick);
        core.load(tag);
    }

    @GameTest(template = "dungeon_empty", templateNamespace = "minecraft", batch = "zssFire")
    public static void allFireVariantsActivateWithoutLava(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.runAfterDelay(20 - level.getGameTime() % 20, () -> {
            var previous = level.getDifficulty();
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[Fire_Variants]"));
            BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            level.addFreshEntity(player);
            try {
                for (int variant = 0; variant < DungeonStructureDataProvider.VARIANT_COUNT; variant++) {
                    var template = level.getStructureManager().get(DungeonStructureDataProvider.templateId(DungeonType.FIRE, variant))
                            .orElseThrow();
                    helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(),
                            level.random, Block.UPDATE_ALL), "Fire variant placement failed: " + variant);
                    int radius = DungeonStructureDataProvider.roomSize(variant) / 2;
                    BlockPos center = origin.offset(1 + radius, 0, 1 + radius);
                    var core = (StageNineBlockEntities.DungeonCore) level.getBlockEntity(center);
                    helper.assertTrue(core != null && core.dungeonType().orElse(null) == DungeonType.FIRE,
                            "Fire variant lost its core: " + variant);
                    helper.assertTrue(BlockPos.betweenClosedStream(origin, origin.offset(template.getSize()).offset(-1, -1, -1))
                                    .noneMatch(pos -> level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)),
                            "Fire variant still contains lava: " + variant);
                    ZSSWorldData.get(level).setDungeonState(DungeonController.instanceId(level, center), false, 0L);
                    BlockPos door = center.relative(core.doorSide().orElseThrow(), core.doorDistance()).above(core.doorOffsetY());
                    helper.assertTrue(core.doorDistance() > 0 && level.getBlockState(door).is(ZSSRegistries.DOOR_BOSS_FIRE.get()),
                            "Fire doorway metadata does not point to its real door: " + variant);
                    core.load(core.saveWithoutMetadata());
                    helper.assertTrue(door.equals(center.relative(core.doorSide().orElseThrow(), core.doorDistance())
                            .above(core.doorOffsetY())), "Fire doorway changed after reload");
                    java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>();
                    BlockPos.betweenClosedStream(origin, origin.offset(template.getSize()).offset(-1, -1, -1))
                            .forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
                    // Raised platforms and the far interior edge both missed the old core-centered trigger.
                    player.setPos(center.getX() + radius - 2.5, center.getY() + 3, center.getZ() + .5);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().size() == 4, "Fire variant did not auto-activate: " + variant);
                    BlockPos hinder = center.relative(core.doorSide().orElseThrow()).above(core.doorOffsetY() + 1);
                    before.forEach((pos, state) -> {
                        if (!pos.equals(door) && !pos.equals(door.above()) && !pos.equals(hinder))
                            helper.assertTrue(level.getBlockState(pos).equals(state), "Fire activation changed wall at " + pos);
                    });
                    helper.assertTrue(level.getBlockState(door).is(ZSSRegistries.SECRET_STONE_NETHER_WART_BLOCK.get()),
                            "Fire battle failed to close its real doorway");
                    var ids = core.bossUuids();
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().equals(ids), "Fire variant duplicated its bosses: " + variant);
                    ids.stream().map(level::getEntity).forEach(Entity::discard);
                    level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                    ids.forEach(core::removeBoss);
                    zeldaswordskills_remastered.worldgen.FireEncounter.tick(level, core);
                    helper.assertTrue(level.getBlockState(door).is(ZSSRegistries.DOOR_BOSS_FIRE.get())
                            && level.getBlockState(door).getValue(LockedDoorBlock.OPEN), "Fire doorway did not reopen");
                    level.getServer().setDifficulty(Difficulty.NORMAL, true);
                }
            } finally {
                player.discard();
                level.getServer().setDifficulty(previous, true);
            }
            helper.succeed();
        });
    }

    private static boolean hasFireLavaAboveFloor(net.minecraft.server.level.ServerLevel level, BlockPos center) {
        return BlockPos.betweenClosedStream(center.offset(-3, 3, -3), center.offset(3, 6, 3))
                .anyMatch(pos -> level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA));
    }

    private static long countFireRewards(net.minecraft.server.level.ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(center).inflate(10),
                entity -> entity.getItem().is(ZSSRegistries.getItem("skill_orb"))
                        && entity.getItem().hasTag() && ZSSContentIds.BONUS_HEART.toString().equals(
                        entity.getItem().getTag().getString(ProgressionItem.SKILL_TAG))).size();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void creatureLootTablesAreLoaded(GameTestHelper helper) {
        var lootData = helper.getLevel().getServer().getLootData();
        for (RegistryObject<EntityType<LegacyCreature>> type : ZSSRegistries.LEGACY_CREATURE_TYPES) {
            ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(type.get());
            ResourceLocation lootId = ResourceLocation.fromNamespaceAndPath(entityId.getNamespace(),
                    "entities/" + entityId.getPath());
            helper.assertTrue(lootData.getLootTable(lootId) != LootTable.EMPTY,
                    "Missing entity loot table: " + lootId);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void creatureAttacksRespectVanillaDefenses(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var type : ZSSRegistries.LEGACY_CREATURE_TYPES) {
            LegacyCreature creature = type.get().create(level);
            if (creature.kind().family() == LegacyCreature.Family.FAIRY) continue;
            float previous = Float.MAX_VALUE;
            for (int defense = 0; defense < 4; defense++) {
                float damage = damageAgainstDefenses(helper, defense, target -> {
                    helper.assertTrue(creature.doHurtTarget(target), "Creature melee missed: " + type.getId());
                    var source = target.getLastDamageSource();
                    helper.assertTrue(source != null && source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
                            && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR),
                            "Creature melee did not use ordinary mob damage: " + type.getId());
                });
                helper.assertTrue(damage > 0 && damage < previous,
                        "Creature melee ignored defense tier " + defense + ": " + type.getId());
                previous = damage;
            }
        }
        var caster = ZSSRegistries.WIZZROBE.get().create(level);
        for (ToolProjectile.Mode mode : List.of(ToolProjectile.Mode.FIRE, ToolProjectile.Mode.ICE,
                ToolProjectile.Mode.LIGHTNING, ToolProjectile.Mode.WIND, ToolProjectile.Mode.SEED)) {
            var spell = new ToolProjectile(ZSSRegistries.MAGIC_SPELL.get(), level, caster, mode);
            var source = ZSSDamageSources.toolProjectile(level, mode, spell, caster);
            helper.assertTrue(source.is(net.minecraft.world.damagesource.DamageTypes.THROWN)
                            && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR),
                    "Creature projectile bypassed vanilla damage: " + mode);
            var expected = switch (mode) {
                case FIRE -> ElementalDamage.Element.FIRE;
                case ICE -> ElementalDamage.Element.ICE;
                case LIGHTNING -> ElementalDamage.Element.LIGHTNING;
                case WIND -> ElementalDamage.Element.WIND;
                default -> ElementalDamage.Element.NONE;
            };
            helper.assertTrue(ElementalDamage.from(source) == expected, "Creature projectile lost its element: " + mode);
            float previous = Float.MAX_VALUE;
            for (int defense = 0; defense < 4; defense++) {
                float damage = damageAgainstDefenses(helper, defense, target -> target.hurt(source, 10));
                helper.assertTrue(damage > 0 && damage < previous, "Projectile ignored defense tier " + defense + ": " + mode);
                previous = damage;
            }
        }
        helper.succeed();
    }

    private static float damageAgainstDefenses(GameTestHelper helper, int defense,
                                               java.util.function.Consumer<LivingEntity> attack) {
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        target.setNoAi(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        target.setHealth(target.getMaxHealth());
        if (defense >= 1) {
            target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            target.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            target.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        }
        if (defense >= 2) target.getArmorSlots().forEach(stack ->
                stack.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 4));
        target.tick();
        if (defense >= 3) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 0));
        target.invulnerableTime = 0;
        float before = target.getHealth();
        attack.accept(target);
        return before - target.getHealth();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void projectileParryReflectsWithoutStunningShooter(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "parry_projectile"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        data.setSkillLevel(ZSSContentIds.PARRY, 1);
        var shooter = EntityType.SKELETON.create(helper.getLevel());
        shooter.setPos(player.position().add(0, 0, 4));
        shooter.setNoAi(true);
        helper.getLevel().addFreshEntity(shooter);
        var stranger = EntityType.SKELETON.create(helper.getLevel());
        stranger.setPos(player.position().add(3, 0, 0));
        helper.getLevel().addFreshEntity(stranger);
        long now = helper.getLevel().getGameTime();
        try {
            data.combat().setTarget(shooter.getId());
            data.combat().startParry(now, now + 22);
            Arrow arrow = new Arrow(helper.getLevel(), shooter);
            arrow.setPos(player.getEyePosition().add(0.2D, 0, 0.5D));
            arrow.setDeltaMovement(0.1D, -0.1D, -1.8D);
            arrow.getPersistentData().putString("zss_arrow", "fire_bomb");
            double speed = arrow.getDeltaMovement().length();
            Vec3 expected = shooter.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
            var impact = new net.minecraftforge.event.entity.ProjectileImpactEvent(arrow, new net.minecraft.world.phys.EntityHitResult(player));
            ZSSCombatEvents.projectileParried(impact);
            zeldaswordskills_remastered.event.ZSSItemEvents.arrowImpact(impact);
            helper.assertTrue(impact.getImpactResult() == net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY
                            && arrow.isAlive() && arrow.getOwner() == player
                            && arrow.getDeltaMovement().normalize().dot(expected) > 0.999D
                            && Math.abs(arrow.getDeltaMovement().length() - speed) < 1.0E-5D,
                    "Arrow parry did not suppress impact and reflect at its shooter at the original speed");
            helper.assertTrue(!arrow.getPersistentData().getBoolean("zss_arrow_used") && !player.isOnFire(),
                    "Reflected elemental arrow triggered its impact effects on the defender");
            helper.assertTrue(!shooter.hasEffect(ZSSRegistries.STUN.get()) && !AdvancedSwordSkills.parryLocked(shooter),
                    "Projectile parry immobilized or stunned its shooter");
            var attack = new net.minecraftforge.event.entity.living.LivingAttackEvent(player, shooter.damageSources().mobAttack(shooter), 1);
            ZSSCombatEvents.attackerStunned(attack);
            helper.assertTrue(!attack.isCanceled(), "Reflected shooter could no longer attack");
            helper.assertTrue(!data.combat().parryPending() && data.combat().parryCooldownUntil() == now + 60,
                    "Projectile parry did not spend its guard and start the normal cooldown");
            arrow.setOwner(shooter);
            helper.assertTrue(!AdvancedSwordSkills.parryProjectile(player, data, arrow), "Spent guard reflected a second projectile");

            data.combat().reset();
            data.combat().setTarget(shooter.getId());
            data.combat().startParry(now, now + 22);
            arrow.setOwner(stranger);
            helper.assertTrue(!AdvancedSwordSkills.parryProjectile(player, data, arrow) && data.combat().parryActive(now),
                    "Parry reflected an unlocked shooter or spent the guard on it");
            arrow.setOwner(null);
            helper.assertTrue(!AdvancedSwordSkills.parryProjectile(player, data, arrow), "Ownerless projectile was parried");

            var fireball = new net.minecraft.world.entity.projectile.SmallFireball(helper.getLevel(), shooter, 0, 0, -1);
            fireball.setPos(player.getEyePosition().add(0, 0, 0.5D));
            fireball.setDeltaMovement(0, 0, -1);
            var fireballImpact = new net.minecraftforge.event.entity.ProjectileImpactEvent(fireball,
                    new net.minecraft.world.phys.EntityHitResult(player));
            ZSSCombatEvents.projectileParried(fireballImpact);
            Vec3 power = new Vec3(fireball.xPower, fireball.yPower, fireball.zPower);
            helper.assertTrue(fireball.getOwner() == player && fireball.getDeltaMovement().z > 0
                            && power.normalize().dot(fireball.getDeltaMovement().normalize()) > 0.999D
                            && !AdvancedSwordSkills.parryLocked(shooter) && !shooter.hasEffect(ZSSRegistries.STUN.get()),
                    "Fireball acceleration did not reflect or shooter received a stun");
            fireball.tick();
            helper.assertTrue(fireball.isAlive() && fireball.getDeltaMovement().z > 0,
                    "Reflected fireball was destroyed or reverted direction on its next tick");
            fireball.discard();
            arrow.discard();

            data.combat().reset();
            data.combat().setTarget(shooter.getId());
            data.combat().startParry(now, now + 22);
            helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, shooter.damageSources().mobAttack(shooter))
                            && AdvancedSwordSkills.parryLocked(shooter) && shooter.hasEffect(ZSSRegistries.STUN.get()),
                    "Melee parry lost its existing stun and movement lock");
        } finally {
            AdvancedSwordSkills.clearParryLocks(player);
            shooter.discard();
            stranger.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void darknutVariantsUseStrengthenedCombatProfile(GameTestHelper helper) {
        List<RegistryObject<EntityType<LegacyCreature>>> types = List.of(
                ZSSRegistries.DARKNUT, ZSSRegistries.DARKNUT_MIGHTY, ZSSRegistries.DARKNUT_BOSS);
        double[] health = {100.0D, 200.0D, 500.0D};
        double[] attack = {10.0D, 12.0D, 15.0D};
        double[] armor = {10.0D, 15.0D, 20.0D};
        for (int index = 0; index < types.size(); index++) {
            LegacyCreature creature = types.get(index).get().create(helper.getLevel());
            helper.assertTrue(creature instanceof DarknutCreature,
                    "Darknut registry entry did not create a DarknutCreature: " + types.get(index).getId());
            helper.assertTrue(creature.getMaxHealth() == health[index]
                            && creature.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == attack[index]
                            && creature.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) == armor[index]
                            && creature.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) == .8D,
                    "Darknut combat attributes do not match the strengthened profile: " + types.get(index).getId());
            helper.assertTrue(creature.getMainHandItem().is(ZSSRegistries.DARKNUT_SWORD.get()),
                    "Darknut spawned without a Darknut Sword: " + types.get(index).getId());
            CompoundTag saved = new CompoundTag();
            creature.saveWithoutId(saved);
            creature.load(saved);
            helper.assertTrue(creature.getMainHandItem().is(ZSSRegistries.DARKNUT_SWORD.get()), "Darknut lost sword after reload");
            var striker = EntityType.ZOMBIE.create(helper.getLevel());
            Vec3 direction = creature.getLookAngle().multiply(1, 0, 1).normalize();
            striker.setPos(creature.position().add(direction.scale(2)));
            creature.getRandom().setSeed(731L);
            int blocked = 0;
            for (int attempt = 0; attempt < 100; attempt++) {
                creature.setHealth(creature.getMaxHealth());
                creature.invulnerableTime = 0;
                if (!creature.hurt(creature.damageSources().mobAttack(striker), 1)) blocked++;
            }
            helper.assertTrue(blocked > 25 && blocked < 75, "Darknut front melee block is not probabilistic: " + blocked);
            striker.setPos(creature.position().subtract(direction.scale(2)));
            creature.invulnerableTime = 0;
            helper.assertTrue(creature.hurt(creature.damageSources().mobAttack(striker), 1), "Darknut blocked rear melee");
            creature.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            striker.setPos(creature.position().add(direction.scale(2)));
            for (int attempt = 0; attempt < 20; attempt++) {
                creature.setHealth(creature.getMaxHealth());
                creature.invulnerableTime = 0;
                helper.assertTrue(creature.hurt(creature.damageSources().mobAttack(striker), 1), "Unarmed Darknut blocked melee");
            }
            creature.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ZSSRegistries.DARKNUT_SWORD.get()));
            var armorBreak = new net.minecraft.world.damagesource.DamageSource(helper.getLevel().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                            Registries.DAMAGE_TYPE, id("helm_splitter"))), striker);
            for (int attempt = 0; attempt < 20; attempt++) {
                creature.setHealth(creature.getMaxHealth());
                creature.invulnerableTime = 0;
                helper.assertTrue(creature.hurt(armorBreak, 1), "Darknut blocked armor-break damage");
            }
        }

        FakePlayer attacker = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString("81a7e708-3f67-4c68-a50c-f5552997b3ad"), "[ZSS_Darknut_Test]"));
        DarknutCreature darknut = (DarknutCreature) ZSSRegistries.DARKNUT.get().create(helper.getLevel());
        helper.assertTrue(darknut != null, "Could not create a Darknut for resistance tests");
        darknut.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0.0D);
        darknut.setPos(helper.absolutePos(new BlockPos(4, 1, 4)).getCenter());
        Vec3 facing = darknut.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        attacker.setPos(darknut.position().subtract(facing.scale(2.0D)));

        helper.assertTrue(darknut.hurt(darknut.damageSources().playerAttack(attacker), 50.0F),
                "Rear melee strike did not break the Darknut's directional armor");
        darknut.setHealth(darknut.getMaxHealth());
        darknut.invulnerableTime = 0;
        float before = darknut.getHealth();
        darknut.hurt(darknut.damageSources().playerAttack(attacker), 10.0F);
        helper.assertTrue(Math.abs((before - darknut.getHealth()) - 8.0F) < .001F,
                "Darknut melee resistance is not 20 percent");

        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        helper.assertTrue(arrow != null, "Could not create an arrow for Darknut ranged resistance tests");
        arrow.setOwner(attacker);
        arrow.setPos(darknut.position().subtract(facing.scale(2.0D)).add(0.0D, darknut.getBbHeight() * .5D, 0.0D));
        arrow.setDeltaMovement(facing.scale(4.0D));
        darknut.setHealth(darknut.getMaxHealth());
        darknut.invulnerableTime = 0;
        before = darknut.getHealth();
        helper.assertTrue(!darknut.hurt(darknut.damageSources().arrow(arrow, attacker), 10.0F)
                        && darknut.getHealth() == before,
                "A rear body shot was not immune to projectile damage");

        arrow.setPos(darknut.position().subtract(facing.scale(2.0D)).add(0.0D, darknut.getEyeHeight(), 0.0D));
        arrow.setDeltaMovement(facing.scale(4.0D));
        darknut.invulnerableTime = 0;
        before = darknut.getHealth();
        darknut.hurt(darknut.damageSources().arrow(arrow, attacker), 10.0F);
        helper.assertTrue(Math.abs((before - darknut.getHealth()) - 8.0F) < .001F,
                "A rear head hit did not use the Darknut's 20 percent ranged resistance");

        arrow.setPos(darknut.position().add(facing.scale(2.0D)).add(0.0D, darknut.getEyeHeight(), 0.0D));
        arrow.setDeltaMovement(facing.scale(-4.0D));
        darknut.setHealth(darknut.getMaxHealth());
        darknut.invulnerableTime = 0;
        before = darknut.getHealth();
        darknut.hurt(darknut.damageSources().arrow(arrow, attacker), 10.0F);
        helper.assertTrue(Math.abs((before - darknut.getHealth()) - 16.0F) < .001F,
                "A ranged hit on the front of the Darknut's face did not deal 100 percent bonus damage");

        DarknutCreature armoredDarknut = (DarknutCreature) ZSSRegistries.DARKNUT.get().create(helper.getLevel());
        helper.assertTrue(armoredDarknut != null, "Could not create an armored Darknut for hit-location tests");
        armoredDarknut.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0.0D);
        armoredDarknut.setPos(darknut.position());
        attacker.setPos(armoredDarknut.position().add(facing.scale(2.0D)));
        arrow.setPos(armoredDarknut.position().add(facing.scale(2.0D))
                .add(0.0D, armoredDarknut.getBbHeight() * .5D, 0.0D));
        arrow.setDeltaMovement(facing.scale(-4.0D));
        helper.assertTrue(!armoredDarknut.hurt(armoredDarknut.damageSources().arrow(arrow, attacker), 10.0F)
                        && armoredDarknut.getHealth() == armoredDarknut.getMaxHealth(),
                "A front body shot was not immune to projectile damage");

        ToolProjectile seed = new ToolProjectile(ZSSRegistries.SEEDSHOT.get(), helper.getLevel(), attacker,
                ToolProjectile.Mode.SEED);
        seed.setPos(armoredDarknut.position().add(facing.scale(2.0D))
                .add(0.0D, armoredDarknut.getBbHeight() * .5D, 0.0D));
        seed.setDeltaMovement(facing.scale(-4.0D));
        armoredDarknut.hurt(ZSSDamageSources.toolProjectile(helper.getLevel(), ToolProjectile.Mode.SEED, seed, attacker), 4.0F);
        helper.assertTrue(seed.getDeltaMovement().dot(facing) > 0.0D,
                "A custom projectile did not rebound from the Darknut's body");
        arrow.setPos(armoredDarknut.position().add(facing.scale(2.0D))
                .add(0.0D, armoredDarknut.getEyeHeight(), 0.0D));
        arrow.setDeltaMovement(facing.scale(-4.0D));
        helper.assertTrue(armoredDarknut.hurt(armoredDarknut.damageSources().arrow(arrow, attacker), 10.0F)
                        && armoredDarknut.getHealth() < armoredDarknut.getMaxHealth(),
                "A true front face shot was blocked by the Darknut's directional armor");

        darknut.setHealth(darknut.getMaxHealth());
        darknut.invulnerableTime = 0;
        helper.assertTrue(!darknut.hurt(darknut.damageSources().explosion(attacker, attacker), 20.0F)
                        && darknut.getHealth() == darknut.getMaxHealth(),
                "Darknut took explosion damage despite 100 percent resistance");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void hostileLegacyCreaturesUseNaturalSpawnLifecycle(GameTestHelper helper) {
        helper.getLevel().setDayTime(1000L);
        for (RegistryObject<EntityType<LegacyCreature>> type : ZSSRegistries.LEGACY_CREATURE_TYPES) {
            LegacyCreature creature = type.get().create(helper.getLevel());
            helper.assertTrue(creature != null, "Could not create creature for spawn lifecycle test: " + type.getId());
            if (creature.kind().family() != LegacyCreature.Family.FAIRY) {
                helper.assertTrue(type.get().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER,
                        "Hostile ZSS creature does not use hostile mob lifecycle rules: " + type.getId());
            }
        }

        ChuCreature chu = (ChuCreature) ZSSRegistries.CHU.get().create(helper.getLevel());
        helper.assertTrue(chu != null && !chu.isPersistenceRequired(),
                "Natural ZSS creatures must not be persistent by default");
        chu.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        chu.setNoAi(true);
        FakePlayer observer = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[Spawn_Observer]"));
        observer.setPos(chu.position().add(2, 0, 0));
        helper.getLevel().addFreshEntity(observer);
        helper.assertTrue(chu.removeWhenFarAway(160.0D * 160.0D),
                "Natural ZSS creatures lost their distance-based despawn policy");
        helper.assertTrue(!net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(ZSSRegistries.CHU.get(),
                        helper.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, chu.blockPosition(), helper.getLevel().random),
                "Hostile ZSS creatures can naturally spawn during daytime");
        LegacyCreature dungeonMob = ZSSRegistries.CHU.get().create(helper.getLevel());
        dungeonMob.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(chu.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
        CompoundTag dungeonSave = new CompoundTag();
        dungeonMob.saveWithoutId(dungeonSave);
        dungeonMob.load(dungeonSave);
        helper.assertTrue(dungeonMob.isPersistenceRequired(), "Dungeon creature persistence did not survive reload");
        helper.assertTrue(helper.getLevel().addFreshEntity(chu), "Could not add a Chu for daytime survival test");
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(!chu.isRemoved() && !chu.isPersistenceRequired(),
                        "Daytime removed an existing ZSS creature or made it permanently persistent");
                helper.succeed();
            } finally {
                chu.discard();
                observer.discard();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void darknutPursuitMatchesPlayerSprintMovement(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString("53c5b290-e438-4ccc-baa9-117ae5845170"), "[ZSS_Sprint_Test]"));
        player.setSprinting(true);
        double sprint = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        for (var type : List.of(ZSSRegistries.DARKNUT, ZSSRegistries.DARKNUT_MIGHTY, ZSSRegistries.DARKNUT_BOSS)) {
            LegacyCreature creature = type.get().create(helper.getLevel());
            creature.setNoAi(true);
            for (float remainingArmor : new float[]{20.0F, 0.0F}) {
                CompoundTag saved = new CompoundTag();
                creature.saveWithoutId(saved);
                saved.putFloat("armor_remaining", remainingArmor);
                creature.load(saved);
                creature.setTarget(player);
                creature.aiStep();
                // Exercise the real controller and ground movement. Equal player/mob
                // attributes are not equal speeds: Mob.setSpeed also scales forward input.
                creature.setPos(start);
                creature.setOnGround(true);
                creature.setDeltaMovement(Vec3.ZERO);
                creature.getMoveControl().setWantedPosition(start.x, start.y, start.z + 10.0D, 1.0D);
                creature.getMoveControl().tick();
                creature.handleRelativeFrictionAndCalculateMovement(new Vec3(0.0D, 0.0D, creature.zza * 0.98F), 0.6F);
                double traveled = creature.position().subtract(start).horizontalDistance();
                helper.assertTrue(Math.abs(traveled - sprint * 0.98F) < 1.0E-5D,
                        "Darknut pursuit ground movement differs from vanilla sprint: " + type.getId() + ", traveled=" + traveled);
                creature.setTarget(null);
                creature.aiStep();
                double wandering = creature.kind().speed() + (remainingArmor == 0.0F ? .35D : 0.0D);
                helper.assertTrue(Math.abs(creature.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                                - wandering) < 1.0E-6D,
                        "Pursuit changed the existing Darknut wandering speed");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "zssPeaceful", timeoutTicks = 20)
    public static void hostileLegacyCreaturesDespawnInPeaceful(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        ChuCreature chu = (ChuCreature) ZSSRegistries.CHU.get().create(helper.getLevel());
        helper.assertTrue(chu != null, "Could not create a Chu for peaceful despawn test");
        chu.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        helper.assertTrue(helper.getLevel().addFreshEntity(chu), "Could not add a Chu for peaceful despawn test");
        helper.runAfterDelay(2, () -> {
            boolean removed = chu.isRemoved();
            server.setDifficulty(previousDifficulty, true);
            helper.assertTrue(removed, "Hostile LegacyCreature survived a peaceful difficulty switch");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void strongInstrumentCompletesHealingSong(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString("383d52a0-bc53-498e-b9e9-7150507df5cc"), "[ZSS_Song_Test]"));
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.OCARINA_OF_TIME.get()));
        player.setHealth(player.getMaxHealth() - 5.0F);
        ZSSPlayerData data = new ZSSPlayerData();
        data.learnSong(ZSSContentIds.HEALING);
        SongService.begin(player, data, false);
        FakePlayer weakPlayer = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString("0dd2ac6b-9433-4925-a298-66174f667738"), "[ZSS_Weak_Song_Test]"));
        weakPlayer.setPos(position.getX() + 2.5D, position.getY(), position.getZ() + 0.5D);
        weakPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.FAIRY_OCARINA.get()));
        weakPlayer.setHealth(weakPlayer.getMaxHealth() - 5.0F);
        ZSSPlayerData weakData = new ZSSPlayerData();
        weakData.learnSong(ZSSContentIds.HEALING);
        SongService.begin(weakPlayer, weakData, false);
        List<SongNote> notes = SongCatalog.get(ZSSContentIds.HEALING).orElseThrow().notes();
        for (int index = 0; index < notes.size(); index++) {
            SongNote note = notes.get(index);
            helper.runAtTickTime(index + 1L, () -> {
                SongService.note(player, data, note, 0);
                SongService.note(weakPlayer, weakData, note, 0);
            });
        }
        helper.runAtTickTime(90L, () -> {
            SongService.tick(player, data);
            SongService.tick(weakPlayer, weakData);
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Strong instrument did not complete Song of Healing");
            helper.assertTrue(data.healingCooldownUntil() > helper.getLevel().getGameTime(),
                    "Song of Healing did not start its server cooldown");
            helper.assertTrue(!data.songState().open(), "Completed song session remained open");
            helper.assertTrue(weakPlayer.getHealth() < weakPlayer.getMaxHealth() && weakData.healingCooldownUntil() == 0L,
                    "Weak instrument executed a strength-five song effect");
            helper.succeed();
        });
    }

    // The backdated quest timestamp below must remain nonnegative even in a new world.
    @GameTest(template = "empty", templateNamespace = "minecraft", setupTicks = 45)
    public static void questChainsAreTransactional(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString("57848859-b88c-4f9f-85bd-00db2d1f2688"), "[ZSS_Quest_Test]"));
        ZSSPlayerData data = ZSSCapabilities.get(player)
                .orElseThrow(() -> new AssertionError("Quest test player capability missing"));
        QuestNpc zelda = ZSSRegistries.ZELDA.get().create(helper.getLevel());
        QuestNpc trader = ZSSRegistries.MASK_TRADER.get().create(helper.getLevel());
        QuestNpc biggoron = ZSSRegistries.GORON.get().create(helper.getLevel());
        helper.assertTrue(zelda != null && trader != null && biggoron != null, "Quest NPC entity factories failed");

        QuestService.initialize(player, data);
        helper.assertTrue(data.quests().get(ZSSContentIds.BIGGORON_SWORD_QUEST).state().equals(QuestStates.STARTED),
                "Biggoron quest did not auto-start");

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.FAIRY_OCARINA.get()));
        QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(data.escrowOcarina().is(ZSSRegistries.FAIRY_OCARINA.get())
                        && data.quests().get(ZSSContentIds.ZELDA_TALK).state().equals(QuestStates.OCARINA_HELD),
                "Zelda did not escrow the Fairy Ocarina");
        data.setQuestProgress(ZSSContentIds.ZELDA_TALK, QuestStates.OCARINA_HELD, 0,
                helper.getLevel().getGameTime() - 45L, java.util.Map.of());
        QuestService.tick(player, data);
        helper.assertTrue(data.escrowOcarina().isEmpty()
                        && data.quests().get(ZSSContentIds.ZELDA_TALK).state().equals(QuestStates.COMPLETE)
                        && data.quests().get(ZSSContentIds.PENDANTS).state().equals(QuestStates.STARTED),
                "Zelda Talk did not return escrow and begin Pendants");

        for (ItemStack pendant : List.of(new ItemStack(ZSSRegistries.PENDANT_WISDOM.get()),
                new ItemStack(ZSSRegistries.PENDANT_COURAGE.get()), new ItemStack(ZSSRegistries.PENDANT_POWER.get()))) {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pendant);
            QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(data.quests().get(ZSSContentIds.PENDANTS).state().equals(QuestStates.COMPLETE)
                        && countItem(player, ZSSRegistries.getItem("big_key")) == 1,
                "Ordered pendant delivery did not complete with exactly one Boss Key");
        QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.MASTER_SWORD.get()));
        QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        int ocarinas = countItem(player, ZSSRegistries.OCARINA_OF_TIME.get());
        QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(ocarinas == 1 && countItem(player, ZSSRegistries.OCARINA_OF_TIME.get()) == 1,
                "Master Sword reward duplicated after repeat interaction");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem("hero_bow")));
        QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        int arrows = countItem(player, ZSSRegistries.getItem("light_arrow"));
        QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(arrows == 8 && countItem(player, ZSSRegistries.getItem("light_arrow")) == 8,
                "Light Arrow reward duplicated after repeat interaction");

        QuestService.interactNpc(player, trader, net.minecraft.world.InteractionHand.MAIN_HAND);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        equipFromInventory(player, ZSSRegistries.getItem("zeldas_letter"));
        net.minecraft.world.entity.npc.Villager shopkeeper = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(shopkeeper != null && QuestService.interactVillager(player, shopkeeper,
                net.minecraft.world.InteractionHand.MAIN_HAND), "Zelda's Letter did not convert an adult villager");
        net.minecraft.world.entity.npc.Villager buyer = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(buyer != null, "Mask buyer could not be created");
        String[] masks = {"keaton_mask", "skull_mask", "spooky_mask", "mask_of_scents", "couples_mask", "bunny_hood"};
        for (String mask : masks) {
            QuestService.interactNpc(player, trader, net.minecraft.world.InteractionHand.MAIN_HAND);
            equipFromInventory(player, ZSSRegistries.getItem(mask));
            helper.assertTrue(QuestService.interactVillager(player, buyer, net.minecraft.world.InteractionHand.MAIN_HAND),
                    "Borrowed mask could not be sold: " + mask);
            player.getInventory().add(new ItemStack(Items.EMERALD, 64));
            QuestService.interactNpc(player, trader, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(data.quests().get(ZSSContentIds.MASK_SALES).state().equals(QuestStates.COMPLETE)
                        && countItem(player, ZSSRegistries.getItem("mask_of_truth")) == 1,
                "Six mask sales did not award exactly one Mask of Truth");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.OCARINA_OF_TIME.get()));
        QuestService.interactNpc(player, trader, net.minecraft.world.InteractionHand.MAIN_HAND);
        QuestService.interactNpc(player, zelda, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(data.songs().containsAll(Set.of(ZSSContentIds.HEALING, ZSSContentIds.TIME)),
                "Quest NPCs did not teach Song of Healing and Song of Time through normal play");

        data.setQuestProgress(ZSSContentIds.BIGGORON_SWORD_QUEST, QuestStates.STARTED, 9,
                helper.getLevel().getGameTime(), java.util.Map.of());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem("eye_drops")));
        helper.assertTrue(QuestService.attackForTrade(player, biggoron)
                        && data.quests().get(ZSSContentIds.BIGGORON_SWORD_QUEST).state().equals(QuestStates.CLAIM_WAIT),
                "Eye Drops did not issue a timed Claim Check");
        equipFromInventory(player, ZSSRegistries.getItem("claim_check"));
        QuestService.attackForTrade(player, biggoron);
        helper.assertTrue(!data.quests().get(ZSSContentIds.BIGGORON_SWORD_QUEST).state().equals(QuestStates.COMPLETE),
                "Claim Check completed before its deadline");
        data.setQuestProgress(ZSSContentIds.BIGGORON_SWORD_QUEST, QuestStates.CLAIM_WAIT, 10,
                helper.getLevel().getGameTime(), java.util.Map.of());
        QuestService.attackForTrade(player, biggoron);
        int swords = countItem(player, ZSSRegistries.BIGGORON_SWORD.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new ItemStack(ZSSRegistries.BIGGORON_SWORD.get()));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem("claim_check")));
        QuestService.attackForTrade(player, biggoron);
        helper.assertTrue(data.quests().get(ZSSContentIds.BIGGORON_SWORD_QUEST).state().equals(QuestStates.COMPLETE)
                        && swords == 1 && countItem(player, ZSSRegistries.BIGGORON_SWORD.get()) == 1,
                "Biggoron final reward was missing or repeatable");

        QuestNpc orca = ZSSRegistries.ORCA.get().create(helper.getLevel());
        QuestNpc barnes = ZSSRegistries.BARNES.get().create(helper.getLevel());
        helper.assertTrue(orca != null && barnes != null, "Barnes or Orca factory failed");
        data.setSkillLevel(ZSSContentIds.SPIN_ATTACK, 1);
        for (int crest = 1; crest <= 10; crest++) {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(ZSSRegistries.getItem("knights_crest")));
            QuestService.interactNpc(player, orca, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(data.orcaChoiceTier() == 1 && data.skills().getOrDefault(ZSSContentIds.CONTINUOUS_FLASH, 0) == 0,
                "Orca must offer a skill choice after ten crests without granting one automatically");
        helper.assertTrue(QuestService.chooseOrcaSkill(player, ZSSContentIds.CONTINUOUS_FLASH)
                        && data.orcaChoiceTier() == 0
                        && data.skills().getOrDefault(ZSSContentIds.CONTINUOUS_FLASH, 0) == 1,
                "Orca's ten-crest choice did not grant Continuous Dash");
        helper.assertTrue(data.knightsCrestsGiven() == 10
                        && data.skills().getOrDefault(ZSSContentIds.SUPER_SPIN_ATTACK, 0) == 0,
                "Orca did not persist ten crests and defer the Super Spin Attack choice");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.COD));
        QuestService.interactNpc(player, barnes, net.minecraft.world.InteractionHand.MAIN_HAND);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        QuestService.interactNpc(player, barnes, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(barnes.waterBombUnlocked() && barnes.getOffers().size() == 3,
                "Barnes did not persist the Water Bomb unlock or expose its trade");

        QuestNpc darunia = ZSSRegistries.GORON.get().create(helper.getLevel());
        helper.assertTrue(darunia != null, "Darunia factory failed");
        darunia.setCustomName(net.minecraft.network.chat.Component.literal("Darunia"));
        SongDefinition saria = SongCatalog.get(ZSSContentIds.SARIA).orElseThrow();
        QuestService.onSongPlayed(player, darunia, saria, 5);
        int gauntlets = countItem(player, ZSSRegistries.getItem("silver_gauntlets"));
        QuestService.onSongPlayed(player, darunia, saria, 5);
        helper.assertTrue(gauntlets == 1 && countItem(player, ZSSRegistries.getItem("silver_gauntlets")) == 1,
                "Darunia's Silver Gauntlets reward was missing or repeatable");
        helper.succeed();
    }

    /**
     * A respawn removes the old player before posting {@code PlayerEvent.Clone}, and removing an
     * entity invalidates its capabilities. The clone handler reads the dying player through that
     * same lookup after {@code reviveCaps()}, so this pins down that the data is still reachable
     * then - otherwise every death silently drops magic, skills and songs.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void playerDataSurvivesRespawnClone(GameTestHelper helper) {
        FakePlayer dying = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Death]"));
        ZSSPlayerData source = ZSSCapabilities.get(dying).orElseThrow(IllegalStateException::new);
        source.setMagic(37.0F, 200.0F);
        source.setSkillLevel(ZSSContentIds.SWORD_BASIC, 6);
        source.setSkillLevel(ZSSContentIds.SPIN_ATTACK, 3);
        source.learnSong(ZSSContentIds.HEALING);
        source.combat().setTarget(4242);

        dying.invalidateCaps();
        helper.assertTrue(!ZSSCapabilities.get(dying).isPresent(),
                "A removed player must stop offering the Zelda capability");
        dying.reviveCaps();
        ZSSPlayerData revived = ZSSCapabilities.get(dying).orElseThrow(() ->
                new IllegalStateException("Player data was destroyed by invalidation, so a respawn would lose it"));
        helper.assertTrue(revived == source, "reviveCaps returned a different player data instance");

        FakePlayer respawned = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Respawn]"));
        ZSSPlayerData target = ZSSCapabilities.get(respawned).orElseThrow(IllegalStateException::new);
        target.copyFrom(revived);
        helper.assertTrue(target.currentMagic() == 37.0F && target.skillLevel(ZSSContentIds.SWORD_BASIC) == 6
                        && target.skillLevel(ZSSContentIds.SPIN_ATTACK) == 3 && target.songs().contains(ZSSContentIds.HEALING),
                "Respawn did not carry magic, skills and songs onto the new player");
        helper.assertTrue(target.combat().targetId() == -1,
                "Transient combat state must not survive a respawn");
        helper.succeed();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
    }

    private static int countItem(Player player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int vanillaSpawnWeight(net.minecraft.core.Holder<Biome> biome, EntityType<?> type) {
        return biome.value().getMobSettings().getMobs(type.getCategory()).unwrap().stream()
                .filter(spawner -> spawner.type == type)
                .mapToInt(spawner -> spawner.getWeight().asInt())
                .findFirst().orElse(0);
    }

    private static LivingEntity mockLiving(GameTestHelper helper) {
        LivingEntity entity = EntityType.ZOMBIE.create(helper.getLevel());
        if (entity == null) throw new AssertionError("Could not create arrow test shooter");
        return entity;
    }

    private static boolean specializedCreature(LegacyCreature creature) {
        return switch (creature.kind().family()) {
            case CHU -> creature instanceof ChuCreature;
            case DARKNUT -> creature instanceof DarknutCreature;
            case BABA -> creature instanceof DekuCreature;
            case KEESE -> creature instanceof KeeseCreature;
            case OCTOROK -> creature instanceof OctorokCreature;
            case SKULLTULA -> creature instanceof SkulltulaCreature;
            case WIZZROBE -> creature instanceof WizzrobeCreature;
            case FIRE_BOSS -> creature instanceof zeldaswordskills_remastered.entity.FireBossCreature;
            case FOREST_BOSS -> creature instanceof zeldaswordskills_remastered.entity.ForestBossCreature;
            case FAIRY -> creature.kind() == LegacyCreature.Kind.FAIRY
                    ? creature instanceof FairyCreature : creature instanceof NaviCreature;
        };
    }

    private static void equipFromInventory(Player player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            stack.shrink(1);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(item));
            return;
        }
        throw new AssertionError("Missing expected quest item: " + ForgeRegistries.ITEMS.getKey(item));
    }

    private static boolean rejectsInvalidCombatState(FriendlyByteBuf buffer) {
        try {
            CombatStateMessage.decode(buffer);
            return false;
        } catch (DecoderException expected) {
            return true;
        }
    }

    private static boolean rejectsInvalidSongIntent(FriendlyByteBuf buffer) {
        try {
            SongIntentMessage.decode(buffer);
            return false;
        } catch (DecoderException expected) {
            return true;
        }
    }
}
