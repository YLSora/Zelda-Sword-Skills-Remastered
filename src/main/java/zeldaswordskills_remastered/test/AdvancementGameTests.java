package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.advancements.Advancement;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.data.ZSSAdvancementProvider;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.quest.QuestStates;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class AdvancementGameTests {
    private AdvancementGameTests() {}

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void treeLoadsWithSemanticCriteria(GameTestHelper helper) {
        helper.assertTrue(ZSSAdvancementProvider.ids().size() == 58, "Expected 58 advancement nodes");
        for (String id : ZSSAdvancementProvider.ids()) {
            var node = advancement(helper, id);
            helper.assertTrue(node.getCriteria().size() == 1 && node.getCriteria().containsKey("event"),
                    "Inventory proxy or missing event: " + id);
            helper.assertTrue(node.getDisplay() != null, "Node has no display: " + id);
        }
        Map<String, String> parents = Map.of("sword.pendant", "adventure_begins", "combo.basic", "skill.basic",
                "shield.mirror", "sword.true", "fairy.boomerang", "skill.heartbar", "orca.canopener", "orca.first",
                "skill.all_types", "skill.gain", "bombs_away", "adventure_begins", "boss_battle", "adventure_begins");
        parents.forEach((child, parent) -> helper.assertTrue(advancement(helper, child).getParent() == advancement(helper, parent),
                "Wrong parent: " + child));
        for (String root : new String[]{"adventure_begins", "skill.basic", "orca.thief", "ocarina.craft"})
            helper.assertTrue(advancement(helper, root).getParent() == null, "Root acquired parent: " + root);
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void skillSongAndComboThresholds(GameTestHelper helper) {
        withPlayer(helper, player -> {
            var data = ZSSCapabilities.get(player).orElseThrow(IllegalStateException::new);
            ZSSAdvancementService.skillLearned(player, ZSSContentIds.BONUS_HEART, 9, false);
            expect(helper, player, "skill.heart", true);
            expect(helper, player, "skill.heartbar", false);
            ZSSAdvancementService.skillLearned(player, ZSSContentIds.BONUS_HEART, 10, false);
            expect(helper, player, "skill.heartbar", true);
            ZSSAdvancementService.skillLearned(player, ZSSContentIds.BONUS_HEART, 20, false);
            expect(helper, player, "skill.hearts_galore", true);
            for (var skill : ZSSContentIds.SKILLS) if (!skill.equals(ZSSContentIds.BONUS_HEART)) data.setSkillLevel(skill, data.skillMaximum(skill));
            helper.assertTrue(data.allSkillTypesLearned(), "Bonus hearts must not block learning every sword skill type");
            ZSSAdvancementService.skillLearned(player, ZSSContentIds.SWORD_BASIC, 10, data.allSkillTypesLearned());
            expect(helper, player, "skill.all_types", true);
            expect(helper, player, "skill.basic", true);
            ZSSAdvancementService.comboHit(player, 2);
            expect(helper, player, "combo.basic", false);
            for (int threshold : new int[]{3, 8, 12}) {
                ZSSAdvancementService.comboHit(player, threshold);
                expect(helper, player, threshold == 3 ? "combo.basic" : threshold == 8 ? "combo.perfect" : "combo.legend", true);
            }
            ZSSAdvancementService.songLearned(player, ZSSContentIds.TIME, 15);
            expect(helper, player, "ocarina.maestro", false);
            ZSSAdvancementService.songLearned(player, ZSSContentIds.SCARECROW, 16);
            expect(helper, player, "ocarina.maestro", true);
            expect(helper, player, "ocarina.scarecrow", true);
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void worldAndMagicPersistence(GameTestHelper helper) {
        withPlayer(helper, player -> {
            var data = ZSSCapabilities.get(player).orElseThrow(IllegalStateException::new);
            QuestService.initialize(player, data);
            ZSSAdvancementService.initialize(player, data);
            expect(helper, player, "adventure_begins", true);
            expect(helper, player, "bombs_away", false);
            ZSSAdvancementService.bossStarted(player);
            expect(helper, player, "boss_battle", false);
            ZSSAdvancementService.magicChanged(player, 199);
            ZSSAdvancementService.magicChanged(player, 200);
            ZSSAdvancementService.secretRoomDiscovered(player, 49);
            expect(helper, player, "bombs_away", true);
            expect(helper, player, "bomb_junkie", false);
            ZSSAdvancementService.secretRoomDiscovered(player, 50);
            expect(helper, player, "bomb_junkie", true);
            for (int i = 0; i < 10; i++) data.recordBossRoom(DungeonType.FOREST.id());
            helper.assertTrue(data.bossRooms() == 1, "Duplicate temple types counted");
            ZSSAdvancementService.bossCompleted(player, data);
            expect(helper, player, "boss_battle", true);
            expect(helper, player, "temple.forest", false);
            for (DungeonType type : new DungeonType[]{DungeonType.WATER, DungeonType.DESERT}) data.recordBossRoom(type.id());
            ZSSAdvancementService.bossCompleted(player, data);
            expect(helper, player, "temple.water", true);
            expect(helper, player, "temple.desert", true);
            expect(helper, player, "temple.ice", false);
            expect(helper, player, "temple.forest", false);
            data.recordBossRoom(DungeonType.ICE.id());
            ZSSAdvancementService.bossCompleted(player, data);
            expect(helper, player, "temple.forest", true);
            expect(helper, player, "temple.earth", false);
            data.recordBossRoom(DungeonType.END.id());
            data.recordBossRoom(DungeonType.FIRE.id());
            ZSSAdvancementService.bossCompleted(player, data);
            expect(helper, player, "temple.fire", false);
            expect(helper, player, "temple.end", false);
            for (DungeonType type : DungeonType.values()) data.recordBossRoom(type.id());
            var loaded = new ZSSPlayerData();
            loaded.load(data.save());
            helper.assertTrue(loaded.bossRooms() == 7 && !loaded.save().getCompound("magic_stats").contains("boss_rooms"),
                    "Stable temple IDs were not persisted");
            ZSSAdvancementService.initialize(player, loaded);
            expect(helper, player, "temple.earth", true);
            expect(helper, player, "temple.fire", true);
            expect(helper, player, "temple.end", true);
            var clone = new ZSSPlayerData();
            clone.copyFrom(loaded);
            clone.resetLearnedSkills();
            helper.assertTrue(clone.bossRooms() == 7, "Death lost world milestone progress");
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void questAndAcquisitionSemantics(GameTestHelper helper) {
        withPlayer(helper, player -> {
            ZSSAdvancementService.questCompleted(player, ZSSContentIds.MASK_SHOP);
            expect(helper, player, "mask.trader", true);
            expect(helper, player, "mask.shop", false);
            ZSSAdvancementService.questCompleted(player, ZSSContentIds.MASK_SALES);
            expect(helper, player, "mask.shop", true);
            ZSSAdvancementService.lootStolen(player, false);
            expect(helper, player, "orca.thief", true);
            expect(helper, player, "orca.deknighted", false);
            ZSSAdvancementService.lootStolen(player, true);
            expect(helper, player, "orca.deknighted", true);
            for (int crests : new int[]{1, 10, 20, 100}) {
                ZSSAdvancementService.orcaProgress(player, crests, false, false);
                expect(helper, player, crests == 1 ? "orca.request" : crests == 10 ? "orca.first" : crests == 20 ? "orca.second" : "orca.master", true);
            }
            ZSSAdvancementService.bowUpgraded(player, 2);
            expect(helper, player, "fairy.bow", true);
            expect(helper, player, "fairy.bow_max", false);
            ZSSAdvancementService.bowUpgraded(player, 3);
            expect(helper, player, "fairy.bow_max", true);
            ZSSAdvancementService.fairyUpgrade(player, new ItemStack(ZSSRegistries.getItem("supershot")));
            expect(helper, player, "fairy.supershot", true);
            ZSSAdvancementService.treasureTraded(player, "tentacle");
            ZSSAdvancementService.treasureTraded(player, "pocket_egg");
            expect(helper, player, "treasure.first", true);
            expect(helper, player, "treasure.second", true);
            ZSSAdvancementService.instrumentCrafted(player);
            expect(helper, player, "ocarina.craft", true);
            helper.succeed();
        });
    }

    private static void withPlayer(GameTestHelper helper, java.util.function.Consumer<ServerPlayer> test) {
        // Forge intentionally refuses advancement awards for FakePlayer.
        var server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Adv]"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        try {
            test.accept(player);
        } finally {
            player.getAdvancements().stopListening();
            player.discard();
        }
    }

    private static Advancement advancement(GameTestHelper helper, String path) {
        var advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(
                ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path));
        helper.assertTrue(advancement != null, "Missing advancement: " + path);
        return advancement;
    }

    private static void expect(GameTestHelper helper, ServerPlayer player, String path, boolean done) {
        Advancement value = advancement(helper, path);
        helper.assertTrue(value.getCriteria().containsKey("event"), "Semantic advancement lost event criterion: " + path);
        helper.assertTrue(player.getAdvancements().getOrStartProgress(value).isDone() == done,
                "Unexpected advancement completion: " + path + " (expected " + done + ")");
    }
}
