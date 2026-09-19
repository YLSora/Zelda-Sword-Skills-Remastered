package zeldaswordskills_remastered.progression;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.capability.ZSSPlayerData;

import zeldaswordskills_remastered.quest.QuestStates;
import zeldaswordskills_remastered.worldgen.DungeonType;


/** Grants semantic advancements from server-owned progression events. */
public final class ZSSAdvancementService {
    private ZSSAdvancementService() { }

    public static void skillLearned(ServerPlayer player, ResourceLocation skill, int level, boolean allTypesLearned) {
        grant(player, "skill.basic", skill.equals(ZSSContentIds.SWORD_BASIC));
        grant(player, "skill.all_types", allTypesLearned);
        grant(player, "skill.heart", skill.equals(ZSSContentIds.BONUS_HEART) && level >= 1);
        grant(player, "skill.heartbar", skill.equals(ZSSContentIds.BONUS_HEART) && level > 9);
        grant(player, "skill.hearts_galore", skill.equals(ZSSContentIds.BONUS_HEART) && level > 19);
    }

    public static void skillOrbFromChest(ServerPlayer player) {
        grant(player, "skill.gain", true);
    }

    public static void songLearned(ServerPlayer player, ResourceLocation song, int total) {
        grant(player, "ocarina.song", true);
        grant(player, "ocarina.song." + song.getPath(), ZSSContentIds.SONGS.contains(song));
        grant(player, "ocarina.scarecrow", song.equals(ZSSContentIds.SCARECROW));
        grant(player, "ocarina.maestro", total >= 16);
    }

    public static void instrumentCrafted(ServerPlayer player) { grant(player, "ocarina.craft", true); }

    public static void comboHit(ServerPlayer player, int count) {
        grant(player, "combo.basic", count >= 3);
        grant(player, "combo.perfect", count >= 8);
        grant(player, "combo.legend", count >= 12);
    }

    public static void bossStarted(ServerPlayer player) {
    }

    public static void bossCompleted(ServerPlayer player, ZSSPlayerData data) {
        grant(player, "boss_battle", data.bossRooms() > 0);
        boolean water = data.hasCompletedDungeon(DungeonType.WATER.id());
        boolean desert = data.hasCompletedDungeon(DungeonType.DESERT.id());
        boolean ice = data.hasCompletedDungeon(DungeonType.ICE.id());
        grant(player, "temple.water", water);
        grant(player, "temple.desert", desert);
        grant(player, "temple.ice", ice);
        // All three parallel temples must be complete before the shared chain can advance.
        boolean forest = water && desert && ice && data.hasCompletedDungeon(DungeonType.FOREST.id());
        boolean earth = forest && data.hasCompletedDungeon(DungeonType.EARTH.id());
        boolean fire = earth && data.hasCompletedDungeon(DungeonType.FIRE.id());
        boolean end = fire && data.hasCompletedDungeon(DungeonType.END.id());
        grant(player, "temple.forest", forest);
        grant(player, "temple.earth", earth);
        grant(player, "temple.fire", fire);
        grant(player, "temple.end", end);
    }

    public static void questCompleted(ServerPlayer player, ResourceLocation quest) {
        grant(player, "mask.trader", quest.equals(ZSSContentIds.MASK_SHOP));
        grant(player, "mask.shop", quest.equals(ZSSContentIds.MASK_SALES));
        grant(player, "treasure.biggoron", quest.getPath().equals("biggoron_sword"));
        grant(player, "sword.pendant", quest.getPath().equals("pendants"));
        grant(player, "sword.master", quest.getPath().equals("master_sword"));
    }

    public static void fairyUpgrade(ServerPlayer player, ItemStack result) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(result.getItem());
        if (id == null) return;
        grant(player, "fairy.slingshot", id.getPath().equals("scattershot"));
        grant(player, "fairy.supershot", id.getPath().equals("supershot"));
        grant(player, "fairy.boomerang", id.getPath().equals("magic_boomerang"));
        grant(player, "shield.mirror", id.getPath().equals("mirror_shield"));
        grant(player, "sword.golden", id.getPath().equals("golden_sword"));
    }

    public static void secretRoomDiscovered(ServerPlayer player, int count) {
        grant(player, "bombs_away", count >= 1);
        grant(player, "bomb_junkie", count >= 50);
    }

    public static void magicChanged(ServerPlayer player, float maximum) {
    }

    public static void maskProgress(ServerPlayer player, boolean trader, boolean sold, boolean shop) {
        grant(player, "mask.trader", trader); grant(player, "mask.sold", sold); grant(player, "mask.shop", shop);
    }

    public static void fairyProgress(ServerPlayer player, boolean captured, boolean emerald, boolean enchantment) {
        grant(player, "fairy.catcher", captured); grant(player, "fairy.emerald", emerald);
        grant(player, "fairy.enchantment", enchantment);
    }

    public static void specialProgress(ServerPlayer player) { }

    public static void bowUpgraded(ServerPlayer player, int level) {
        grant(player, "fairy.bow", level >= 2);
        grant(player, "fairy.bow_max", level >= 3);
    }

    public static void treasureTraded(ServerPlayer player, String output) {
        grant(player, "treasure.first", output.equals("tentacle"));
        grant(player, "treasure.second", output.equals("pocket_egg"));
    }

    public static void lootStolen(ServerPlayer player, boolean crest) {
        grant(player, "orca.thief", true);
        grant(player, "orca.deknighted", crest);
    }

    public static void orcaProgress(ServerPlayer player, int crests, boolean theft, boolean canOpener) {
        grant(player, "orca.request", crests >= 1); grant(player, "orca.first", crests >= 10);
        grant(player, "orca.second", crests >= 20); grant(player, "orca.master", crests >= 100);
        grant(player, "orca.canopener", canOpener);
    }

    public static void hammerProgress(ServerPlayer player, boolean wood, boolean silver, boolean skull, boolean golden) {
        grant(player, "hammer.wood", wood); grant(player, "hammer.silver", silver);
        grant(player, "hammer.skull", skull); grant(player, "hammer.golden", golden);
    }

    public static void swordProgress(ServerPlayer player, String milestone) {
        grant(player, milestone, true);
    }

    public static void initialize(ServerPlayer player, zeldaswordskills_remastered.capability.ZSSPlayerData data) {
        grant(player, "adventure_begins", true);
        bossCompleted(player, data);
    }

    private static void grant(ServerPlayer player, String id, boolean condition) {
        if (!condition || player.getServer() == null) return;
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(
                ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, id));
        if (advancement != null) player.getAdvancements().award(advancement, "event");
    }

}
