package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.entity.FairyCreature;
import zeldaswordskills_remastered.item.ProgressionItem;
import zeldaswordskills_remastered.progression.AcquisitionService;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.quest.QuestStates;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.List;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AcquisitionGameTests {
    private AcquisitionGameTests() {
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void startingHouseIsGrantedOnce(GameTestHelper helper) {
        FakePlayer player = player(helper, "2897f9f1-8d27-44d2-907c-f3036838d42d", "[ZSS_Acquisition_Start]");
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Starting reward capability missing"));
        AcquisitionService.initializePlayer(player, data);
        AcquisitionService.initializePlayer(player, data);
        helper.assertTrue(data.receivedStartingGear() && count(player, ZSSRegistries.getItem("links_house")) == 1,
                "Link's House was missing or granted more than once");
        ZSSPlayerData deathCopy = new ZSSPlayerData();
        deathCopy.copyFrom(data);
        deathCopy.resetLearnedSkills();
        helper.assertTrue(deathCopy.receivedStartingGear(), "Death-only capability copy lost the starting reward marker");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void startingHouseContainsFurnitureAndSupplies(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.startSequence().thenWaitUntil(() -> {
            // Chunk blocks can be ready before saved paintings have joined the level.
            for (int x = origin.getX() >> 4; x <= (origin.getX() + 6) >> 4; x++) {
                for (int z = origin.getZ() >> 4; z <= (origin.getZ() + 6) >> 4; z++) {
                    helper.assertTrue(helper.getLevel().areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x, z)),
                            "House chunk entities are still loading");
                }
            }
        }).thenExecute(() -> verifyStartingHouse(helper, origin));
    }

    private static void verifyStartingHouse(GameTestHelper helper, BlockPos origin) {
        FakePlayer player = player(helper, "665cdfb1-cd37-4e6f-9359-5717c8e0e7d0", "[ZSS_House_Furniture]");
        var level = helper.getLevel();
        level.getEntitiesOfClass(Painting.class, new AABB(origin, origin.offset(7, 5, 7)))
                .forEach(Painting::discard);
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(6, 4, 6))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        BlockPos ground = origin.offset(3, -1, 3);
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        Item house = ZSSRegistries.getItem("links_house");
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(house));
        var hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, .5D, 0), Direction.UP, ground, false);
        helper.assertTrue(house.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.CONSUME
                && player.getMainHandItem().isEmpty(), "House placement failed or did not consume its item");
        var placedPaintings = level.getEntitiesOfClass(Painting.class, new AABB(origin, origin.offset(7, 5, 7)));
        helper.assertTrue(placedPaintings.size() == 1, "House did not create exactly one painting: " + placedPaintings.size());
        Painting painting = placedPaintings.get(0);
        helper.assertTrue(painting.survives(), "Newly placed painting has no support");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(house));
        helper.assertTrue(house.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.FAIL
                && player.getMainHandItem().getCount() == 1, "Occupied house placement duplicated supplies or consumed the item");

        helper.runAfterDelay(5, () -> {
            var lower = level.getBlockState(origin.offset(3, 1, 0));
            var upper = level.getBlockState(origin.offset(3, 2, 0));
            helper.assertTrue(lower.is(Blocks.OAK_DOOR) && upper.is(Blocks.OAK_DOOR)
                    && lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "Door halves did not survive updates");
            for (int x = 2; x <= 4; x++) for (int y = 2; y <= 3; y++) {
                helper.assertTrue(level.getBlockState(origin.offset(x, y, 6)).is(Blocks.GLASS_PANE), "Rear window is not 3 wide by 2 high");
            }
            var foot = level.getBlockState(origin.offset(2, 1, 5));
            var head = level.getBlockState(origin.offset(3, 1, 5));
            helper.assertTrue(foot.is(Blocks.WHITE_BED) && head.is(Blocks.WHITE_BED)
                    && foot.getValue(BedBlock.PART) == BedPart.FOOT && head.getValue(BedBlock.PART) == BedPart.HEAD
                    && foot.getValue(BedBlock.FACING) == Direction.EAST && head.getValue(BedBlock.FACING) == Direction.EAST,
                    "White bed halves are missing or disconnected");
            String[] supplies = {"hero_tunic_helmet", "hero_tunic_chestplate", "hero_tunic_leggings", "hero_tunic_boots",
                    "kokiri_sword", "deku_shield", "navi_bottle"};
            helper.assertTrue(level.getBlockEntity(origin.offset(5, 1, 4)) instanceof ChestBlockEntity, "Supply chest is missing");
            var chest = (ChestBlockEntity) level.getBlockEntity(origin.offset(5, 1, 4));
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                helper.assertTrue(slot < supplies.length ? stack.is(ZSSRegistries.getItem(supplies[slot])) && stack.getCount() == 1
                        : stack.isEmpty(), "Supply chest contents differ from the starter kit");
            }
            helper.assertTrue(level.getBlockState(origin.offset(1, 1, 3)).is(Blocks.OAK_FENCE)
                    && level.getBlockState(origin.offset(1, 2, 3)).is(Blocks.OAK_PRESSURE_PLATE)
                    && level.getBlockState(origin.offset(2, 1, 3)).is(Blocks.OAK_STAIRS)
                    && level.getBlockState(origin.offset(1, 1, 1)).is(Blocks.CRAFTING_TABLE)
                    && level.getBlockState(origin.offset(1, 1, 2)).is(Blocks.FURNACE), "Basic furniture is missing");
            var paintings = level.getEntitiesOfClass(Painting.class, new AABB(origin, origin.offset(7, 5, 7)));
            helper.assertTrue(paintings.size() == 1 && paintings.get(0).survives(),
                    "Wall painting is missing or unsupported: count=" + paintings.size()
                            + ", removed=" + painting.getRemovalReason() + ", position=" + painting.blockPosition()
                            + ", support=" + level.getBlockState(origin.offset(0, 2, 4)));
            helper.assertTrue(level.getBlockState(origin.offset(3, 1, 1)).isAir()
                    && level.getBlockState(origin.offset(3, 1, 3)).isAir(), "Furniture blocks the entrance or central aisle");
            painting.discard();
            player.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void fairyBottleAndPoolUpgradesHaveRealInputs(GameTestHelper helper) {
        FakePlayer player = player(helper, "46903283-c230-48da-acb6-b263098e8ab2", "[ZSS_Acquisition_Fairy]");
        FairyCreature fairy = (FairyCreature) ZSSRegistries.FAIRY.get().create(helper.getLevel());
        helper.assertTrue(fairy != null, "Fairy factory failed");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
        helper.assertTrue(AcquisitionService.interact(player, fairy, InteractionHand.MAIN_HAND)
                        && fairy.isRemoved() && count(player, ZSSRegistries.getItem("fairy_bottle")) == 1,
                "Glass Bottle did not capture a fairy");

        BlockPos corePos = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setPos(corePos.getX() + 1.5D, corePos.getY(), corePos.getZ() + 1.5D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem("slingshot")));
        player.getInventory().add(new ItemStack(Items.EMERALD, 64));
        player.getInventory().add(new ItemStack(Items.EMERALD, 64));
        helper.assertTrue(AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND)
                        && player.getMainHandItem().is(ZSSRegistries.getItem("scattershot"))
                        && count(player, Items.EMERALD) == 0,
                "Fairy pool did not consume 128 emeralds and upgrade the Slingshot");
        add(player, Items.EMERALD, 320);
        helper.assertTrue(AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND)
                        && player.getMainHandItem().is(ZSSRegistries.getItem("supershot"))
                        && count(player, Items.EMERALD) == 0,
                "Fairy pool did not consume 320 emeralds and upgrade the Scattershot");

        player.getInventory().add(new ItemStack(ZSSRegistries.TRUE_MASTER_SWORD.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.HYLIAN_SHIELD.get()));
        helper.assertTrue(AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND)
                        && player.getMainHandItem().is(ZSSRegistries.MIRROR_SHIELD.get()),
                "True Master Sword did not qualify the Hylian Shield for its fairy upgrade");
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Fairy test capability missing"));
        data.setSkillLevel(ZSSContentIds.BONUS_HEART, 10);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem("boomerang")));
        helper.assertTrue(AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND)
                        && player.getMainHandItem().is(ZSSRegistries.getItem("magic_boomerang")),
                "Ten Bonus Hearts did not qualify the Boomerang for its fairy upgrade");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.TEMPERED_SWORD.get()));
        WitherSkeleton victim = EntityType.WITHER_SKELETON.create(helper.getLevel());
        helper.assertTrue(victim != null, "Hostile test entity factory failed");
        for (int kill = 0; kill < 300; kill++) AcquisitionService.recordTemperedSwordKill(player, victim);
        helper.assertTrue(!AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND),
                "Tempered Sword upgraded before exceeding 300 kills");
        AcquisitionService.recordTemperedSwordKill(player, victim);
        var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        player.getMainHandItem().getItem().appendHoverText(player.getMainHandItem(), helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.stream().anyMatch(line -> line.getString().contains("301")),
                "Tempered Sword tooltip omitted the kill count");
        helper.assertTrue(AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND)
                        && player.getMainHandItem().is(ZSSRegistries.GOLDEN_SWORD.get()),
                "Qualified Tempered Sword did not upgrade to the Golden Sword");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem("hero_bow")));
        add(player, Items.EMERALD, 384);
        helper.assertTrue(AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND)
                        && zeldaswordskills_remastered.item.ZeldaCombatItems.HeroBow.upgradeLevel(player.getMainHandItem()) == 2
                        && count(player, Items.EMERALD) == 0, "First bow upgrade must consume 384 emeralds");
        add(player, Items.EMERALD, 576);
        helper.assertTrue(AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND)
                        && zeldaswordskills_remastered.item.ZeldaCombatItems.HeroBow.upgradeLevel(player.getMainHandItem()) == 3
                        && count(player, Items.EMERALD) == 0, "Final bow upgrade must consume 576 emeralds");
        helper.assertTrue(!AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND), "Max bow upgraded twice");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem("slingshot")));
        player.setShiftKeyDown(true);
        add(player, Items.EMERALD, 20);
        helper.assertTrue(AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND)
                        && player.getMainHandItem().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS) == 2
                        && count(player, Items.EMERALD) == 0, "Fairy enchantment must use Bonus Hearts and emeralds");
        helper.assertTrue(!AcquisitionService.interact(player, poolFairy(helper), InteractionHand.MAIN_HAND), "Unchanged enchantment consumed more resources");
        player.setShiftKeyDown(false);
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void milkJellyAndMobDropsAreObtainable(GameTestHelper helper) {
        FakePlayer player = player(helper, "9285f9ca-89dd-41d2-b340-38345fac5db8", "[ZSS_Acquisition_Loot]");
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Cow factory failed");
        cow.setPos(player.getX() + 1, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(cow);
        AcquisitionService.markLonLonCows(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        helper.assertTrue(AcquisitionService.interact(player, cow, InteractionHand.MAIN_HAND)
                        && player.getMainHandItem().is(ZSSRegistries.getItem("lon_lon_milk"))
                        && cow.getPersistentData().getLong(AcquisitionService.LON_LON_COOLDOWN) > helper.getLevel().getGameTime(),
                "Epona-marked cow did not provide cooldown-bound Lon Lon Milk");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        helper.assertTrue(!AcquisitionService.interact(player, cow, InteractionHand.MAIN_HAND),
                "Lon Lon cow cooldown allowed a second bottle on the same day");

        String[] jellies = {"chu_jelly_red", "chu_jelly_green", "chu_jelly_blue", "chu_jelly_yellow"};
        String[] potions = {"red_potion", "green_potion", "blue_potion", "yellow_potion"};
        for (int index = 0; index < jellies.length; index++) {
            Villager cleric = EntityType.VILLAGER.create(helper.getLevel());
            helper.assertTrue(cleric != null, "Villager factory failed");
            cleric.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.CLERIC, 1));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem(jellies[index])));
            int emeralds = (index + 1) * 8;
            int trade = index;
            helper.assertTrue(AcquisitionService.interact(player, cleric, InteractionHand.MAIN_HAND)
                            && player.getMainHandItem().isEmpty()
                            && cleric.getOffers().stream().anyMatch(offer -> offer.getBaseCostA().getCount() == 4
                            && offer.getCostB().getCount() == emeralds && offer.getResult().is(ZSSRegistries.getItem(potions[trade]))),
                    "Chu Jelly did not unlock its potion trade: " + jellies[index]);
        }

        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Trade test capability missing"));
        data.setSkillLevel(ZSSContentIds.BONUS_HEART, 10);
        zeldaswordskills_remastered.entity.npc.QuestNpc biggoron = ZSSRegistries.GORON.get().create(helper.getLevel());
        helper.assertTrue(biggoron != null, "Biggoron factory failed");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        QuestService.interactNpc(player, biggoron, InteractionHand.MAIN_HAND);
        helper.assertTrue(biggoron.getOffers().stream().anyMatch(offer -> offer.getBaseCostA().is(Items.EMERALD)
                                && offer.getBaseCostA().getCount() == 20
                                && offer.getResult().is(ZSSRegistries.getItem("small_magic_jar")))
                        && biggoron.getOffers().stream().anyMatch(offer -> offer.getBaseCostA().is(Items.EMERALD)
                                && offer.getBaseCostA().getCount() == 40
                                && offer.getResult().is(ZSSRegistries.getItem("large_magic_jar"))),
                "Biggoron did not sell both Magic Jars at their configured prices");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.BROKEN_SWORD.get()));
        player.getInventory().add(new ItemStack(Items.EMERALD, 5));
        helper.assertTrue(AcquisitionService.repairGiantSword(player, biggoron)
                        && count(player, ZSSRegistries.GIANT_SWORD.get()) == 1 && count(player, Items.EMERALD) == 0,
                "Qualified player could not repair a Broken Sword into the Giant Sword");

        zeldaswordskills_remastered.entity.npc.QuestNpc maskTrader = ZSSRegistries.MASK_TRADER.get().create(helper.getLevel());
        helper.assertTrue(maskTrader != null, "Mask Trader factory failed");
        data.setQuestProgress(ZSSContentIds.MASK_SHOP, QuestStates.COMPLETE, 1, helper.getLevel().getGameTime(), java.util.Map.of());
        data.setQuestProgress(ZSSContentIds.MASK_SALES, QuestStates.COMPLETE, 6, helper.getLevel().getGameTime(), java.util.Map.of());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        QuestService.interactNpc(player, maskTrader, InteractionHand.MAIN_HAND);
        helper.assertTrue(maskTrader.getOffers().size() == 11
                        && maskTrader.getOffers().stream().anyMatch(offer -> offer.getResult().is(ZSSRegistries.getItem("deku_mask")))
                        && maskTrader.getOffers().stream().anyMatch(offer -> offer.getResult().is(ZSSRegistries.getItem("fierce_deity_mask"))),
                "Completed mask quest did not expose the full eleven-mask shop");

        Creeper creeper = EntityType.CREEPER.create(helper.getLevel());
        WitherSkeleton hostile = EntityType.WITHER_SKELETON.create(helper.getLevel());
        helper.assertTrue(creeper != null && hostile != null, "Drop test entity factory failed");
        hostile.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(50.0D);
        int bombs = 0;
        int powerPieces = 0;
        int largeJars = 0;
        int legalSkillOrbs = 0;
        int hearts = 0;
        RandomSource random = RandomSource.create(0x5A17C0DEL);
        for (int roll = 0; roll < 10_000; roll++) {
            List<ItemStack> creeperDrops = AcquisitionService.rollMobDrops(creeper, random);
            bombs += amount(creeperDrops, "standard_bomb");
            legalSkillOrbs += legalSkills(creeperDrops);
            List<ItemStack> hostileDrops = AcquisitionService.rollMobDrops(hostile, random);
            powerPieces += amount(hostileDrops, "power_piece");
            largeJars += amount(hostileDrops, "large_magic_jar");
            hearts += amount(hostileDrops, "small_heart");
        }
        helper.assertTrue(bombs == 0 && powerPieces == 0 && largeJars == 0 && legalSkillOrbs == 0,
                "Mob extra drops were not removed");
        helper.assertTrue(hearts > 350 && hearts < 650, "Heart-only mob drop rate changed: " + hearts);
        helper.succeed();
    }

    private static FairyCreature poolFairy(GameTestHelper helper) {
        var fairy = (FairyCreature) ZSSRegistries.FAIRY.get().create(helper.getLevel());
        helper.assertTrue(fairy != null, "Pool fairy factory failed");
        fairy.bindToHabitat(helper.absolutePos(new BlockPos(2, 1, 2)));
        return fairy;
    }

    private static int legalSkills(List<ItemStack> drops) {
        int result = 0;
        for (ItemStack stack : drops) {
            if (!stack.is(ZSSRegistries.getItem("skill_orb"))) continue;
            String value = stack.getOrCreateTag().getString(ProgressionItem.SKILL_TAG);
            helperSkill(value);
            result += stack.getCount();
        }
        return result;
    }

    private static void helperSkill(String value) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(value);
        if (id == null || !ZSSContentIds.SKILLS.contains(id) || id.equals(ZSSContentIds.BONUS_HEART)) {
            throw new AssertionError("Mob drop produced an invalid random skill orb: " + value);
        }
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void fairyAndComponentToolUpgradesShareResults(GameTestHelper helper) {
        FakePlayer player = player(helper, "d151ce1d-e82f-4d12-a24d-0ec224986c22", "ToolUpgradeTest");
        var fairy = (FairyCreature) ZSSRegistries.FAIRY.get().create(helper.getLevel());
        var hook = zeldaswordskills_remastered.item.StageNineToolItem.upgradeHook(
                new ItemStack(ZSSRegistries.getItem("hookshot")), "hookshot_extender");
        hook.setHoverName(net.minecraft.network.chat.Component.literal("Named hook"));
        hook.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 2);
        var originalTag = hook.getTag().copy();
        player.setItemInHand(InteractionHand.OFF_HAND, hook);
        add(player, Items.EMERALD, 127);
        helper.assertTrue(!AcquisitionService.interact(player, fairy, InteractionHand.OFF_HAND)
                && count(player, Items.EMERALD) == 127, "Wild fairy upgraded outside a pool");
        fairy.bindToHabitat(helper.absolutePos(new BlockPos(2, 1, 2)));
        helper.assertTrue(AcquisitionService.interact(player, fairy, InteractionHand.OFF_HAND)
                && player.getOffhandItem() == hook && count(player, Items.EMERALD) == 127 && fairy.isAlive(),
                "Insufficient funds changed the hook or consumed emeralds");
        add(player, Items.EMERALD, 1);
        AcquisitionService.interact(player, fairy, InteractionHand.OFF_HAND);
        helper.assertTrue(fairy.isRemoved(), "Successful upgrade did not consume the fairy");
        helper.assertTrue(player.getOffhandItem().is(ZSSRegistries.getItem("stoneshot"))
                && originalTag.equals(player.getOffhandItem().getTag()) && count(player, Items.EMERALD) == 0,
                "Fairy stone upgrade lost extension/name/enchantments or charged the wrong amount");
        ItemStack component = new ItemStack(ZSSRegistries.getItem("multi_hook_upgrade"));
        player.setItemInHand(InteractionHand.MAIN_HAND, component);
        component.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(component.isEmpty() && player.getOffhandItem().is(ZSSRegistries.getItem("multishot"))
                && originalTag.equals(player.getOffhandItem().getTag()), "Component after fairy upgrade conflicted");
        helper.assertTrue(!AcquisitionService.interact(player, fairy, InteractionHand.OFF_HAND), "Final hook upgraded again");
        component = new ItemStack(ZSSRegistries.getItem("multi_hook_upgrade"));
        player.setItemInHand(InteractionHand.MAIN_HAND, component);
        player.getCooldowns().removeCooldown(component.getItem());
        helper.assertTrue(component.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult()
                == net.minecraft.world.InteractionResult.FAIL && component.getCount() == 1,
                "Redundant handheld component was consumed");

        player.setItemInHand(InteractionHand.OFF_HAND, hook.copy());
        component = new ItemStack(ZSSRegistries.getItem("claw_upgrade"));
        player.setItemInHand(InteractionHand.MAIN_HAND, component);
        component.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        add(player, Items.EMERALD, 320);
        fairy = poolFairy(helper);
        AcquisitionService.interact(player, fairy, InteractionHand.OFF_HAND);
        helper.assertTrue(player.getOffhandItem().is(ZSSRegistries.getItem("multishot"))
                && count(player, Items.EMERALD) == 0 && originalTag.equals(player.getOffhandItem().getTag()),
                "Fairy after component upgrade conflicted");
        ItemStack reloaded = ItemStack.of(player.getOffhandItem().save(new CompoundTag()));
        helper.assertTrue(reloaded.is(ZSSRegistries.getItem("multishot"))
                && originalTag.equals(reloaded.getTag()), "Upgraded hook did not survive saving");

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ZSSRegistries.getItem("whip")));
        add(player, Items.EMERALD, 320);
        fairy = poolFairy(helper);
        AcquisitionService.interact(player, fairy, InteractionHand.OFF_HAND);
        helper.assertTrue(player.getOffhandItem().is(ZSSRegistries.getItem("magic_whip"))
                && count(player, Items.EMERALD) == 0
                && !AcquisitionService.interact(player, fairy, InteractionHand.OFF_HAND),
                "Magic whip cost or terminal state is incorrect");
        player.getAbilities().instabuild = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, hook.copy());
        fairy = poolFairy(helper);
        AcquisitionService.interact(player, fairy, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(ZSSRegistries.getItem("stoneshot")),
                "Creative fairy upgrade incorrectly requires emeralds");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void hookRecipesPreserveStateAndRejectRedundantComponents(GameTestHelper helper) {
        FakePlayer player = player(helper, "409661be-1129-4dc3-9040-6c6b83e330d5", "HookRecipeTest");
        for (String input : List.of("hookshot", "stoneshot", "multishot")) {
            for (String material : List.of("hookshot_extender", "claw_upgrade", "multi_hook_upgrade")) {
                var grid = new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu, 2, 2);
                ItemStack source = new ItemStack(ZSSRegistries.getItem(input));
                source.setHoverName(net.minecraft.network.chat.Component.literal("Recipe hook"));
                grid.setItem(0, source);
                grid.setItem(1, new ItemStack(ZSSRegistries.getItem(material)));
                String recipeId = material.equals("claw_upgrade") ? "hookshot_claw_upgrade"
                        : material.equals("multi_hook_upgrade") ? "hookshot_multi_upgrade" : "hookshot_extender";
                var recipe = (zeldaswordskills_remastered.recipe.HookshotUpgradeRecipe) helper.getLevel().getRecipeManager()
                        .byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, recipeId)).orElseThrow();
                boolean accepted = material.equals("hookshot_extender") || material.equals("claw_upgrade") && input.equals("hookshot")
                        || material.equals("multi_hook_upgrade") && !input.equals("multishot");
                helper.assertTrue(recipe.matches(grid, helper.getLevel()) == accepted, "Wrong upgrade eligibility: " + input + material);
                if (!accepted) continue;
                var result = recipe.assemble(grid, helper.getLevel().registryAccess());
                String output = material.equals("claw_upgrade") ? "stoneshot" : material.equals("multi_hook_upgrade") ? "multishot" : input;
                helper.assertTrue(result.is(ZSSRegistries.getItem(output)) && result.getHoverName().equals(source.getHoverName())
                        && source.is(ZSSRegistries.getItem(input)), "Recipe preview mutated input or lost state");
                grid.setItem(0, result);
                helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Redundant component accepted");
                grid.setItem(0, source);
                grid.setItem(2, new ItemStack(Items.DIRT));
                helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Upgrade accepted unrelated ingredients");
            }
        }
        helper.succeed();
    }

    private static int amount(List<ItemStack> drops, String path) {
        return drops.stream().filter(stack -> stack.is(ZSSRegistries.getItem(path))).mapToInt(ItemStack::getCount).sum();
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static FakePlayer player(GameTestHelper helper, String uuid, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.fromString(uuid), name));
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return player;
    }

    private static void add(ServerPlayer player, Item item, int amount) {
        while (amount > 0) {
            int count = Math.min(amount, item.getMaxStackSize());
            player.getInventory().add(new ItemStack(item, count));
            amount -= count;
        }
    }
}
