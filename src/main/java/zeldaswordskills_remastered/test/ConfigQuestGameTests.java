package zeldaswordskills_remastered.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.entity.CreatureSpawnRules;
import zeldaswordskills_remastered.event.ZSSPlayerEvents;
import zeldaswordskills_remastered.item.ProgressionItem;
import zeldaswordskills_remastered.progression.AcquisitionService;
import zeldaswordskills_remastered.quest.QuestStates;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.item.BigKeyItem;
import zeldaswordskills_remastered.item.BombBagItem;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigQuestGameTests {
    private ConfigQuestGameTests() {}

    @BeforeBatch(batch = "zssConfigQuest")
    public static void useInMemoryConfig(ServerLevel level) {
        // ConfigValue.set on Forge's file config autosaves and races the reload watcher.
        CommentedConfig config = CommentedConfig.inMemory();
        ZSSConfig.SERVER_SPEC.correct(config);
        ZSSConfig.SERVER_SPEC.setConfig(config);
    }

    @AfterBatch(batch = "zssConfigQuest")
    public static void restoreServerConfig(ServerLevel level) {
        ModConfig config = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
                .filter(candidate -> candidate.getSpec() == ZSSConfig.SERVER_SPEC)
                .findFirst().orElseThrow();
        ZSSConfig.SERVER_SPEC.setConfig(config.getConfigData());
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void deathSkillResetIsIndependentOfInventory(GameTestHelper helper) {
        boolean previousReset = ZSSConfig.SERVER.resetSkillsOnDeath.get();
        var keepInventory = helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        boolean previousKeep = keepInventory.get();
        try {
            for (boolean reset : new boolean[]{false, true}) {
                ZSSConfig.SERVER.resetSkillsOnDeath.set(reset);
                for (boolean keep : new boolean[]{false, true}) {
                    keepInventory.set(keep, helper.getLevel().getServer());
                    for (boolean death : new boolean[]{false, true}) {
                        FakePlayer original = player(helper);
                        ZSSPlayerData source = data(original);
                        source.setSkillLevel(ZSSContentIds.SWORD_BASIC, 3);
                        source.setSkillEnabled(ZSSContentIds.SWORD_BASIC, false);
                        source.setSkillLevel(ZSSContentIds.BONUS_HEART, 2);
                        source.learnSong(ZSSContentIds.TIME);
                        source.setMagic(75, 100);
                        source.markReceivedStartingGear();
                        source.setQuestProgress(ZSSContentIds.PENDANTS, QuestStates.STARTED, 2, 42, Map.of());
                        FakePlayer replacement = player(helper);
                        ZSSPlayerEvents.clonePlayer(new PlayerEvent.Clone(replacement, original, death));
                        ZSSPlayerData copied = data(replacement);
                        helper.assertTrue(copied.skillLevel(ZSSContentIds.SWORD_BASIC) == (reset && death ? 0 : 3),
                                "Death skill policy depended on inventory or applied to a non-death clone");
                        helper.assertTrue(copied.skillLevel(ZSSContentIds.BONUS_HEART) == 2
                                        && !copied.skillEnabled(ZSSContentIds.SWORD_BASIC)
                                        && copied.songs().contains(ZSSContentIds.TIME) && copied.maxMagic() == 100
                                        && copied.currentMagic() == 75 && copied.receivedStartingGear()
                                        && copied.quests().get(ZSSContentIds.PENDANTS).step() == 2,
                                "Death reset erased non-skill progression or preferences");
                    }
                }
            }
        } finally {
            ZSSConfig.SERVER.resetSkillsOnDeath.set(previousReset);
            keepInventory.set(previousKeep, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void masterModeStopsNaturalMagicRegeneration(GameTestHelper helper) {
        boolean previous = ZSSConfig.SERVER.masterMode.get();
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        try {
            data.setMagic(10.0F, 100.0F);
            ZSSConfig.SERVER.masterMode.set(false);
            player.tickCount = 39;
            zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, player));
            helper.assertTrue(data.currentMagic() == 10.0F, "Magic changed before the natural regeneration interval");
            player.tickCount = 40;
            zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, player));
            helper.assertTrue(data.currentMagic() == 11.0F, "Normal mode did not regenerate one magic point");
            data.setMagic(10.0F, 100.0F);
            ZSSConfig.SERVER.masterMode.set(true);
            player.tickCount = 80;
            zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, player));
            helper.assertTrue(data.currentMagic() == 10.0F, "Master Mode regenerated magic naturally");
        } finally {
            ZSSConfig.SERVER.masterMode.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void newGameplayConfigDefaultsAreStable(GameTestHelper helper) {
        boolean previousLoot = ZSSConfig.SERVER.generateZssLoot.get();
        try {
            ZSSConfig.SERVER.generateZssLoot.set(false);
            helper.assertTrue(!ZSSConfig.SERVER.generateZssLoot.get(), "ZSS loot switch could not disable injected loot");
            ZSSConfig.SERVER.generateZssLoot.set(true);
            helper.assertTrue(ZSSConfig.SERVER.generateZssLoot.get(), "ZSS loot switch could not enable injected loot");
        } finally {
            ZSSConfig.SERVER.generateZssLoot.set(previousLoot);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void pickupSoundResourcesArePresent(GameTestHelper helper) {
        helper.assertTrue(ZSSRegistries.GET_ITEM.get() != null && ZSSRegistries.GET_HEART.get() != null,
                "Pickup sound events were not registered");
        helper.assertTrue(ConfigQuestGameTests.class.getClassLoader().getResource("assets/zeldaswordskills_remastered/sounds/get_item.ogg") != null
                        && ConfigQuestGameTests.class.getClassLoader().getResource("assets/zeldaswordskills_remastered/sounds/get_heart.ogg") != null,
                "Pickup sound resources are missing");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void startingKitIsConfiguredAndProcessedOnce(GameTestHelper helper) {
        boolean previous = ZSSConfig.SERVER.giveStartingItems.get();
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                ZSSConfig.SERVER.giveStartingItems.set(enabled);
                FakePlayer player = player(helper);
                ZSSPlayerData data = data(player);
                AcquisitionService.initializePlayer(player, data);
                int expected = enabled ? 1 : 0;
                helper.assertTrue(count(player, "links_house") == expected && count(player, "skill_orb") == expected,
                        "Starting kit did not honor its switch or item counts");
                if (enabled) {
                    ItemStack orb = player.getInventory().items.stream()
                            .filter(stack -> stack.is(ZSSRegistries.getItem("skill_orb"))).findFirst().orElseThrow();
                    helper.assertTrue(orb.hasTag() && ZSSContentIds.SWORD_BASIC.toString().equals(
                            orb.getTag().getString(ProgressionItem.SKILL_TAG)), "Starting orb is not Basic Sword Skill");
                }
                ZSSConfig.SERVER.giveStartingItems.set(true);
                ZSSPlayerData reloaded = new ZSSPlayerData();
                reloaded.load(data.save());
                reloaded.resetLearnedSkills();
                AcquisitionService.initializePlayer(player, reloaded);
                AcquisitionService.initializePlayer(player, reloaded);
                helper.assertTrue(reloaded.receivedStartingGear() && count(player, "links_house") == expected
                                && count(player, "skill_orb") == expected,
                        "Reload, respawn or enabling the switch repeated a processed starting kit");
            }
        } finally {
            ZSSConfig.SERVER.giveStartingItems.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void naturalSpawnsRequireConfigAndGameRule(GameTestHelper helper) {
        boolean previous = ZSSConfig.SERVER.naturalMonsterSpawning.get();
        var mobSpawning = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        boolean previousRule = mobSpawning.get();
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                ZSSConfig.SERVER.naturalMonsterSpawning.set(enabled);
                for (boolean rule : new boolean[]{false, true}) {
                    mobSpawning.set(rule, helper.getLevel().getServer());
                    helper.assertTrue(CreatureSpawnRules.naturalMonstersAllowed(helper.getLevel()) == (enabled && rule),
                            "Custom monster spawning did not require both switches");
                    if (enabled && rule) continue;
                    for (MobSpawnType reason : new MobSpawnType[]{MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION}) {
                        helper.assertTrue(!SpawnPlacements.checkSpawnRules(ZSSRegistries.CHU.get(), helper.getLevel(), reason,
                                        helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)), helper.getLevel().random),
                                "Natural placement bypassed a disabled spawn switch");
                    }
                }
            }
        } finally {
            ZSSConfig.SERVER.naturalMonsterSpawning.set(previous);
            mobSpawning.set(previousRule, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void questCommandsCompleteAndResetConsistently(GameTestHelper helper) throws CommandSyntaxException {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        try {
            dispatcher.execute("zss quest resetall", source.withPermission(0));
            throw new AssertionError("Quest administration allowed permission level zero");
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(data.quests().isEmpty(), "Rejected command mutated quest state");
        }
        helper.assertTrue(dispatcher.execute("zss quest achieve zeldaswordskills_remastered:unknown", source) == 0,
                "Unknown quest was accepted");
        data.setEscrowOcarina(new ItemStack(ZSSRegistries.FAIRY_OCARINA.get()));
        data.setQuestProgress(ZSSContentIds.ZELDA_TALK, QuestStates.OCARINA_HELD, 0, 42, Map.of());
        data.setBorrowedMask(ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "keaton_mask"));
        Map<ResourceLocation, Integer> steps = Map.of(
                ZSSContentIds.ZELDA_TALK, 1, ZSSContentIds.ZELDA_LETTER, 1,
                ZSSContentIds.PENDANTS, 3, ZSSContentIds.MASTER_SWORD_QUEST, 2,
                ZSSContentIds.LIGHT_ARROWS, 1, ZSSContentIds.MASK_SHOP, 1,
                ZSSContentIds.MASK_SALES, 6, ZSSContentIds.BIGGORON_SWORD_QUEST, 11);
        for (var entry : steps.entrySet()) {
            helper.assertTrue(dispatcher.execute("zss quest achieve " + entry.getKey() + " @s", source) == 1,
                    "Targeted completion failed");
            helper.assertTrue(dispatcher.execute("zss quest achieve " + entry.getKey(), source) == 1,
                    "Repeated completion failed");
            var progress = data.quests().get(entry.getKey());
            helper.assertTrue(progress.state().equals(QuestStates.COMPLETE) && progress.step() == entry.getValue(),
                    "Completion used the wrong terminal step");
        }
        helper.assertTrue(data.escrowOcarina().isEmpty() && data.borrowedMask().isEmpty()
                        && count(player, "fairy_ocarina") == 1
                        && count(player, "zeldas_letter") == 1 && count(player, "big_key") == 1
                        && count(player, "ocarina_of_time") == 1 && count(player, "light_arrow") == 8
                        && count(player, "mask_of_truth") == 1 && count(player, "biggoron_sword") == 1
                        && player.getInventory().countItem(ZSSRegistries.PENDANT_WISDOM.get()) == 1
                        && player.getInventory().countItem(ZSSRegistries.PENDANT_COURAGE.get()) == 1
                        && player.getInventory().countItem(ZSSRegistries.PENDANT_POWER.get()) == 1
                        && player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == 17,
                "Completion lost escrow, duplicated rewards or left a borrowed mask marker");
        ItemStack forestKey = player.getInventory().items.stream()
                .filter(stack -> stack.is(ZSSRegistries.getItem("big_key"))).findFirst().orElseThrow();
        helper.assertTrue(forestKey.hasTag() && "zeldaswordskills_remastered:forest".equals(forestKey.getTag().getString("dungeon")),
                "Pendant reward key was not bound to the forest dungeon");
        data.load(data.save());
        dispatcher.execute("zss quest achieve zeldaswordskills_remastered:light_arrows", source);
        helper.assertTrue(count(player, "light_arrow") == 8, "Reload allowed duplicate completion rewards");
        dispatcher.execute("zss quest reset zeldaswordskills_remastered:pendants", source);
        helper.assertTrue(data.quests().get(ZSSContentIds.PENDANTS).state().equals(QuestStates.STARTED)
                        && data.quests().get(ZSSContentIds.PENDANTS).step() == 0
                        && data.quests().get(ZSSContentIds.MASTER_SWORD_QUEST).state().equals(QuestStates.COMPLETE),
                "Single reset became inaccessible or reset a different quest");
        dispatcher.execute("zss quest achieve zeldaswordskills_remastered:pendants", source);
        dispatcher.execute("zss quest achieve zeldaswordskills_remastered:pendants", source);
        helper.assertTrue(count(player, "big_key") == 2, "Reset did not allow exactly one new reward");
        dispatcher.execute("zss quest reset zeldaswordskills_remastered:master_sword", source);
        data.setQuestProgress(ZSSContentIds.MASTER_SWORD_QUEST, QuestStates.STARTED, 1, 42, Map.of());
        dispatcher.execute("zss quest achieve zeldaswordskills_remastered:master_sword", source);
        helper.assertTrue(count(player, "ocarina_of_time") == 2
                        && player.getInventory().countItem(ZSSRegistries.PENDANT_WISDOM.get()) == 1
                        && player.getInventory().countItem(ZSSRegistries.PENDANT_COURAGE.get()) == 1
                        && player.getInventory().countItem(ZSSRegistries.PENDANT_POWER.get()) == 1,
                "Completion duplicated pendants that were already returned at step one");
        player.getInventory().clearContent();
        data.setEscrowOcarina(new ItemStack(ZSSRegistries.FAIRY_OCARINA.get()));
        data.setBorrowedMask(ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "skull_mask"));
        dispatcher.execute("zss quest resetall @s", source);
        dispatcher.execute("zss quest resetall", source);
        ZSSPlayerData reloaded = new ZSSPlayerData();
        reloaded.load(data.save());
        for (var id : ZSSContentIds.QUESTS) {
            var progress = reloaded.quests().get(id);
            var expected = id.equals(ZSSContentIds.BIGGORON_SWORD_QUEST) ? QuestStates.STARTED : QuestStates.NOT_STARTED;
            helper.assertTrue(progress.state().equals(expected) && progress.step() == 0 && progress.counters().isEmpty(),
                    "Reset-all did not persist fresh quest progress");
        }
        helper.assertTrue(reloaded.escrowOcarina().isEmpty() && reloaded.borrowedMask().isEmpty()
                && count(player, "fairy_ocarina") == 1, "Reset-all lost or duplicated quest escrow");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void inventoryHeartsHealInsteadOfRemainingInSlots(GameTestHelper helper) {
        FakePlayer player = player(helper);
        var heart = ZSSRegistries.getItem("small_heart");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.setHealth(player.getMaxHealth() - 4);
            player.getInventory().setItem(slot, new ItemStack(heart, 2));
            zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, player));
            helper.assertTrue(player.getHealth() == player.getMaxHealth()
                            && player.getInventory().getItem(slot).isEmpty(),
                    "Small hearts failed to heal and clear inventory slot " + slot);
        }
        player.setHealth(player.getMaxHealth() - 2);
        player.containerMenu.setCarried(new ItemStack(heart));
        zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        helper.assertTrue(player.containerMenu.getCarried().isEmpty() && player.getHealth() == player.getMaxHealth(),
                "Cursor-held heart was retained instead of healing");
        player.getInventory().add(new ItemStack(heart, 64));
        zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        helper.assertTrue(player.getInventory().countItem(heart) == 0, "Full-health player stored hearts");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getInventory().add(new ItemStack(heart, 3));
        player.setHealth(player.getMaxHealth() - 6);
        zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        helper.assertTrue(player.getInventory().countItem(heart) == 3 && player.getHealth() == player.getMaxHealth() - 6,
                "Creative mode could not store hearts");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        helper.assertTrue(player.getInventory().countItem(heart) == 0 && player.getHealth() == player.getMaxHealth(),
                "Switching to survival retained hearts or lost their healing");
        var dropped = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(heart, 2));
        var pickup = new net.minecraftforge.event.entity.player.EntityItemPickupEvent(player, dropped);
        player.setHealth(player.getMaxHealth() - 4);
        zeldaswordskills_remastered.event.ZSSItemEvents.pickup(pickup);
        helper.assertTrue(pickup.isCanceled() && dropped.isRemoved() && player.getHealth() == player.getMaxHealth(),
                "Ground pickup did not consume the whole stack and heal");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void cursedManMilestonesAndPersistence(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setCustomName(Component.literal("Cursed Man"));
        for (var skill : ZSSContentIds.SKILLS) data.setSkillLevel(skill, data.skillMaximum(skill));
        data.setSkillLevel(ZSSContentIds.DASH, 0);
        data.addSkulltulaTokens(9);
        QuestService.interactVillager(player, villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.skulltulaTrades() == 0 && player.getInventory().isEmpty(), "Nine tokens unlocked a reward");
        data.addSkulltulaTokens(41);
        QuestService.interactVillager(player, villager, InteractionHand.OFF_HAND);
        helper.assertTrue(data.skulltulaTrades() == 0, "Offhand interaction claimed an extra reward");
        for (int rewardIndex = 0; rewardIndex < 5; rewardIndex++) {
            helper.assertTrue(QuestService.interactVillager(player, villager, InteractionHand.MAIN_HAND),
                    "Cursed Man did not offer reward " + rewardIndex);
            String reward = switch (rewardIndex) {
                case 0 -> "whip";
                case 1 -> "zora_tunic_chestplate";
                case 2 -> "bomb_bag";
                case 3 -> "big_key";
                default -> "skill_orb";
            };
            helper.assertTrue(count(player, reward) == 1, "Missing milestone reward " + rewardIndex);
            ItemStack stack = player.getInventory().items.stream()
                    .filter(item -> item.is(ZSSRegistries.getItem(reward))).findFirst().orElseThrow();
            if (rewardIndex == 2) helper.assertTrue(BombBagItem.getCount(stack, "standard_bomb") == 10,
                    "Reward bag did not contain ten bombs");
            if (rewardIndex == 3) helper.assertTrue(BigKeyItem.dungeon(stack).isPresent(), "Reward key was unbound");
            if (rewardIndex == 4) helper.assertTrue(stack.getTag().getString(ProgressionItem.SKILL_TAG).equals(ZSSContentIds.DASH.toString()),
                    "Reward orb selected a maxed skill");
        }
        helper.assertTrue(data.skulltulaTokens() == 50 && data.skulltulaTrades() == 5,
                "Trading changed the lifetime badge balance");
        data.addSkulltulaTokens(50);
        player.getInventory().clearContent();
        QuestService.interactVillager(player, villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(count(player, "whip") == 1, "The reward list did not cycle after fifty badges");
        for (int i = 0; i < 4; i++) QuestService.interactVillager(player, villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 64,
                "The hundred-badge bonus was not granted");
        data.load(data.save());
        helper.assertTrue(data.skulltulaTokens() == 100 && data.skulltulaTrades() == 10,
                "Badge balance or trade list did not survive reload");
        var clone = new ZSSPlayerData();
        clone.copyFrom(data);
        clone.resetLearnedSkills();
        helper.assertTrue(clone.skulltulaTokens() == 100 && clone.skulltulaTrades() == 10,
                "Death lost token collection or reward history");
        Villager another = EntityType.VILLAGER.create(helper.getLevel());
        another.setCustomName(Component.literal("Cursed Man"));
        QuestService.interactVillager(player, another, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.skulltulaTrades() == 10 && player.getInventory().countItem(Items.EMERALD) == 64,
                "Another Cursed Man repeated a claimed reward");
        data.addSkulltulaTokens(100);
        player.getInventory().clearContent();
        for (int i = 0; i < 10; i++) QuestService.interactVillager(player, another, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.skulltulaTokens() == 200 && data.skulltulaTrades() == 20
                        && player.getInventory().countItem(Items.EMERALD) == 64 && count(player, "whip") == 2,
                "Rewards did not continue to cycle through two hundred tokens");
        FakePlayer otherPlayer = player(helper);
        data(otherPlayer).addSkulltulaTokens(10);
        QuestService.interactVillager(otherPlayer, another, InteractionHand.MAIN_HAND);
        helper.assertTrue(count(otherPlayer, "whip") == 1 && data(otherPlayer).skulltulaTrades() == 1,
                "One player's rewards changed another player's trade list");
        helper.assertTrue(!another.getPersistentData().contains("zss_next_skulltula_reward"),
                "Cursed Man retained player reward state");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void cursedManRecurringRewardsAndItemBoundaries(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        ItemStack tokens = new ItemStack(ZSSRegistries.getItem("skulltula_token"), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, tokens);
        helper.assertTrue(!QuestService.attackForTrade(player, villager)
                        && !QuestService.interactVillager(player, villager, InteractionHand.MAIN_HAND),
                "Ordinary villager accepted quest tokens");
        villager.setCustomName(Component.literal("Cursed Man"));
        villager.setAge(-24000);
        helper.assertTrue(!QuestService.attackForTrade(player, villager), "Baby accepted quest tokens");
        villager.setAge(0);
        ProgressionItem item = (ProgressionItem) tokens.getItem();
        helper.assertTrue(item.applyOnPickup(player, tokens) && tokens.isEmpty() && data.skulltulaTokens() == 3,
                "Pickup did not add quest tokens to the persistent balance");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.getItem("skulltula_token"), 3));
        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.skulltulaTokens() == 3, "Manual token use changed the persistent balance");
        for (var skill : ZSSContentIds.SKILLS) data.setSkillLevel(skill, data.skillMaximum(skill));
        data.setSkillLevel(ZSSContentIds.BONUS_HEART, 0);
        data.addSkulltulaTokens(47);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.FAIRY_OCARINA.get()));
        QuestService.convertNamedVillager(villager);
        for (int i = 0; i < 5; i++) QuestService.interactVillager(player, villager, InteractionHand.MAIN_HAND);
        ItemStack heartOrb = player.getInventory().items.stream()
                .filter(stack -> stack.is(ZSSRegistries.getItem("skill_orb"))).findFirst().orElseThrow();
        helper.assertTrue(heartOrb.getTag().getString(ProgressionItem.SKILL_TAG).equals(ZSSContentIds.BONUS_HEART.toString())
                        && count(player, "light_arrow") == 0,
                "An unmaxed Bonus Heart was treated as all skills maxed");
        helper.assertTrue(data.skulltulaTokens() == 50 && data.skulltulaTrades() == 5,
                "The player-owned trade list did not advance");
        helper.assertTrue(!villager.isRemoved() && player.getMainHandItem().is(ZSSRegistries.FAIRY_OCARINA.get()),
                "Holding an ocarina converted Cursed Man into Zelda");
        data.setSkillLevel(ZSSContentIds.BONUS_HEART, data.skillMaximum(ZSSContentIds.BONUS_HEART));
        data.addSkulltulaTokens(50);
        for (int i = 0; i < 5; i++) QuestService.interactVillager(player, villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(count(player, "light_arrow") == 16 && player.getInventory().countItem(Items.EMERALD) == 64,
                "All skills maxed did not yield Light Arrows alongside the hundred-token bonus");
        QuestService.attackForTrade(player, villager);
        helper.assertTrue(count(player, "light_arrow") == 16 && data.skulltulaTrades() == 10,
                "Repeated interaction duplicated a reward");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void skulltulaTokensConvertOnPickupAndInventoryEntry(GameTestHelper helper) {
        FakePlayer player = player(helper);
        var data = data(player);
        var token = ZSSRegistries.getItem("skulltula_token");
        int expected = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(token, 3));
            zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, player));
            expected += 3;
            helper.assertTrue(player.getInventory().getItem(slot).isEmpty() && data.skulltulaTokens() == expected,
                    "Inventory tokens were lost, duplicated or retained at slot " + slot);
        }
        player.containerMenu.setCarried(new ItemStack(token, 5));
        zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        expected += 5;
        helper.assertTrue(player.containerMenu.getCarried().isEmpty() && data.skulltulaTokens() == expected,
                "Cursor tokens were not converted");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(token, 7));
        var dropped = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(token, 11));
        var creativePickup = new net.minecraftforge.event.entity.player.EntityItemPickupEvent(player, dropped);
        zeldaswordskills_remastered.event.ZSSItemEvents.pickup(creativePickup);
        zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        helper.assertTrue(count(player, "skulltula_token") == 7 && data.skulltulaTokens() == expected
                        && !creativePickup.isCanceled() && dropped.getItem().getCount() == 11,
                "Creative tokens were consumed or counted");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        expected += 7;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        var pickup = new net.minecraftforge.event.entity.player.EntityItemPickupEvent(player, dropped);
        zeldaswordskills_remastered.event.ZSSItemEvents.pickup(pickup);
        expected += 11;
        helper.assertTrue(pickup.isCanceled() && dropped.isRemoved() && data.skulltulaTokens() == expected,
                "A full inventory prevented token collection");
        zeldaswordskills_remastered.event.ZSSItemEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        helper.assertTrue(data.skulltulaTokens() == expected && data.skulltulaTrades() == 0,
                "Collection repeated or silently claimed rewards");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void skulltulaDeathLootRates(GameTestHelper helper) {
        var level = helper.getLevel();
        var creature = ZSSRegistries.SKULLTULA.get().create(level);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, creature.position())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, creature)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
        var normal = level.getServer().getLootData().getLootTable(ResourceLocation.fromNamespaceAndPath(
                ZeldaSwordSkills_Remastered.MOD_ID, "entities/skulltula"));
        var gold = level.getServer().getLootData().getLootTable(ResourceLocation.fromNamespaceAndPath(
                ZeldaSwordSkills_Remastered.MOD_ID, "entities/skulltula_gold"));
        int tokens = 0;
        int hearts = 0;
        // Sequential small seeds bias the first draws of Minecraft's legacy random source.
        var seeds = net.minecraft.util.RandomSource.create(0x5A17C0DEL);
        for (int roll = 0; roll < 10000; roll++) {
            long seed = seeds.nextLong();
            for (ItemStack loot : normal.getRandomItems(params, seed)) {
                if (loot.is(ZSSRegistries.getItem("skulltula_token"))) tokens += loot.getCount();
                if (loot.is(ZSSRegistries.getItem("small_heart"))) hearts += loot.getCount();
            }
        }
        helper.assertTrue(tokens >= 150 && tokens <= 250, "Normal Skulltula token rate differs from 2%: " + tokens);
        helper.assertTrue(hearts >= 4700 && hearts <= 5300, "Token pool changed the existing heart drops: " + hearts);
        for (long seed = 1; seed <= 100; seed++) {
            var drops = gold.getRandomItems(params, seed);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(ZSSRegistries.getItem("skulltula_token"))
                    && drops.get(0).getCount() == 1, "Gold Skulltula no longer drops exactly one token");
        }
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "config_quest"));
    }

    private static ZSSPlayerData data(FakePlayer player) {
        return ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
    }

    private static int count(FakePlayer player, String item) {
        return player.getInventory().countItem(ZSSRegistries.getItem(item));
    }
}
