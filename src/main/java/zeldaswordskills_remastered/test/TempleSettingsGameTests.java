package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.LockedChestBlock;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.entity.npc.QuestNpc;
import zeldaswordskills_remastered.event.ZSSQuestEvents;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.DungeonType;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TempleSettingsGameTests {
    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void warpStonesAreUnbreakableAndDropNothing(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        for (var entry : ZSSRegistries.WARP_STONES) {
            var state = entry.get().defaultBlockState();
            helper.assertTrue(state.getDestroySpeed(level, pos) == -1, "Warp stone hardness changed");
            helper.assertTrue(Block.getDrops(state, level, pos, null).isEmpty(), "Warp stone drops an item");
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void smallKeyIsConsumedOnlyOnFirstChestUnlock(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(java.util.UUID.randomUUID(), "[Key_Test]"));
        player.getAbilities().instabuild = false;
        var key = new ItemStack(ZSSRegistries.getItem("small_key"));
        player.setItemInHand(InteractionHand.MAIN_HAND, key);
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var chest = ZSSRegistries.CHEST_LOCKED.get();
        level.setBlockAndUpdate(pos, chest.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for (String wrongId : new String[]{"skeleton_key", "big_key"}) {
            var wrong = new ItemStack(ZSSRegistries.getItem(wrongId));
            player.setItemInHand(InteractionHand.MAIN_HAND, wrong);
            chest.use(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(!level.getBlockState(pos).getValue(LockedChestBlock.UNLOCKED)
                    && wrong.getCount() == 1, "Wrong key unlocked chest or was consumed");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, key);
        chest.use(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(key.isEmpty() && level.getBlockState(pos).getValue(LockedChestBlock.UNLOCKED),
                "Small key was not consumed when unlocking chest");
        var spare = new ItemStack(ZSSRegistries.getItem("small_key"));
        player.setItemInHand(InteractionHand.MAIN_HAND, spare);
        chest.use(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(spare.getCount() == 1, "Already unlocked chest consumed a second key");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssLoot")
    public static void skeletonKeyRatesMatchTempleSettings(GameTestHelper helper) {
        var level = helper.getLevel();
        var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        for (var type : DungeonType.values()) for (boolean reward : new boolean[]{false, true}) {
            var table = level.getServer().getLootData().getLootTable(DungeonLootTables.temple(type, reward));
            int keys = 0;
            for (long seed = 1; seed <= 2000; seed++) {
                int count = table.getRandomItems(params, seed).stream()
                        .filter(stack -> stack.is(ZSSRegistries.getItem("skeleton_key")))
                        .mapToInt(ItemStack::getCount).sum();
                helper.assertTrue(count <= 1, "Overlapping skeleton key pools");
                keys += count;
            }
            int expected = type == DungeonType.FIRE ? 1500 : 600;
            helper.assertTrue(Math.abs(keys - expected) < 120, "Incorrect key rate: " + type + ": " + keys);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void namedVillagersConvertWithoutBypassingSpecialConditions(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        for (String name : new String[]{"Biggoron", "Zelda", "Barnes", "Orca", "Happy Mask Salesman", "Impa"}) {
            var villager = EntityType.VILLAGER.create(helper.getLevel());
            villager.moveTo(Vec3.atCenterOf(pos));
            villager.setCustomName(Component.literal(name));
            helper.getLevel().addFreshEntity(villager);
            ZSSQuestEvents.namedVillager(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(villager));
            boolean converts = name.equals("Biggoron") || name.equals("Zelda") || name.equals("Barnes");
            helper.assertTrue(villager.isRemoved() == converts, "Unexpected conversion for " + name);
            if (converts) {
                var npcs = helper.getLevel().getEntitiesOfClass(QuestNpc.class, villager.getBoundingBox().inflate(1),
                        npc -> npc.getName().getString().equals(name));
                helper.assertTrue(npcs.size() == 1, "Conversion lost name or duplicated NPC");
                npcs.forEach(QuestNpc::discard);
            } else villager.discard();
        }
        helper.succeed();
    }
}
