package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.LockedDoorBlock;
import zeldaswordskills_remastered.item.BigKeyItem;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class DoorAndDekuGameTests {
    private DoorAndDekuGameTests() { }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void fireBypassesDekuGuard(GameTestHelper helper) {
        for (var type : java.util.List.of(ZSSRegistries.BABA_DEKU, ZSSRegistries.BABA_WITHERED)) {
            var deku = type.get().create(helper.getLevel());
            float health = deku.getHealth();
            helper.assertTrue(deku.hurt(helper.getLevel().damageSources().onFire(), 2), "Standing Deku rejected fire");
            helper.assertTrue(deku.getHealth() < health, "Fire did not damage Deku");
        }
        var fire = ZSSRegistries.BABA_FIRE.get().create(helper.getLevel());
        helper.assertTrue(!fire.hurt(helper.getLevel().damageSources().onFire(), 2), "Fire Baba lost its fire immunity");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void meleeAndRangedDamageStandingDeku(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[Deku_Melee]"));
        for (var type : java.util.List.of(ZSSRegistries.BABA_DEKU, ZSSRegistries.BABA_WITHERED, ZSSRegistries.BABA_FIRE)) {
            for (var weapon : java.util.List.of(Items.IRON_SWORD, Items.IRON_AXE, ZSSRegistries.MASTER_SWORD.get())) {
                var deku = type.get().create(level);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
                float health = deku.getHealth();
                helper.assertTrue(deku.hurt(level.damageSources().playerAttack(player), 3), "Standing Deku rejected melee: " + type.getId());
                helper.assertTrue(deku.getHealth() < health, "Melee did not reduce Deku health");
            }
            var deku = type.get().create(level);
            var arrow = new net.minecraft.world.entity.projectile.Arrow(level, player);
            float health = deku.getHealth();
            helper.assertTrue(deku.hurt(level.damageSources().arrow(arrow, player), 3),
                    "Standing Deku rejected an arrow: " + type.getId());
            helper.assertTrue(deku.getHealth() < health, "Arrow did not reduce Deku health");
            var rockTarget = type.get().create(level);
            var rock = new zeldaswordskills_remastered.entity.projectile.ThrownRock(level, player);
            float rockHealth = rockTarget.getHealth();
            helper.assertTrue(rockTarget.hurt(level.damageSources().thrown(rock, player), 3)
                    && rockTarget.getHealth() < rockHealth, "Standing Deku rejected a thrown rock");
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void dungeonKeysOpenMatchingDoors(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[Door_Test]"));
        player.getAbilities().instabuild = false;
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        for (var entry : ZSSRegistries.BOSS_DOORS) {
            var door = entry.get();
            level.setBlockAndUpdate(pos, door.defaultBlockState());
            level.setBlockAndUpdate(pos.above(), door.defaultBlockState().setValue(LockedDoorBlock.HALF, DoubleBlockHalf.UPPER));
            var hit = new BlockHitResult(Vec3.atCenterOf(pos.above()), Direction.NORTH, pos.above(), false);
            DungeonType wrongType = door.dungeonType() == DungeonType.FOREST ? DungeonType.FIRE : DungeonType.FOREST;
            var wrong = BigKeyItem.forDungeon(ZSSRegistries.getItem("big_key"), wrongType.id());
            player.setItemInHand(InteractionHand.MAIN_HAND, wrong);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            door.use(level.getBlockState(pos.above()), level, pos.above(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(!level.getBlockState(pos).getValue(LockedDoorBlock.UNLOCKED) && wrong.getCount() == 1,
                    "Wrong key unlocked door or was consumed");
            var key = BigKeyItem.forDungeon(ZSSRegistries.getItem("big_key"), door.dungeonType().id());
            var tooltip = new ArrayList<Component>();
            key.getItem().appendHoverText(key, level, tooltip, TooltipFlag.NORMAL);
            helper.assertTrue(tooltip.size() == 1 && !tooltip.get(0).getString().isBlank(), "Bound key tooltip is empty");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.setItemInHand(InteractionHand.OFF_HAND, key);
            door.use(level.getBlockState(pos.above()), level, pos.above(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(key.isEmpty(), "Matching key not consumed");
            door.neighborChanged(level.getBlockState(pos), level, pos, Blocks.STONE, pos.east(), false);
            for (BlockPos half : java.util.List.of(pos, pos.above())) {
                var state = level.getBlockState(half);
                helper.assertTrue(state.getValue(LockedDoorBlock.OPEN) && state.getValue(LockedDoorBlock.UNLOCKED),
                        "Door closed itself during neighbor updates: " + door.dungeonType());
                var shape = state.getShape(level, half).bounds();
                helper.assertTrue(shape.getXsize() == 3.0 / 16.0, "Open north door must lie along west edge");
            }
            door.use(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(!level.getBlockState(pos).getValue(LockedDoorBlock.OPEN), "Unlocked door cannot close manually");
            level.setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.assertTrue(level.getBlockState(pos).getValue(LockedDoorBlock.OPEN), "Powered door did not open");
            level.removeBlock(pos.east(), false);
            helper.assertTrue(!level.getBlockState(pos).getValue(LockedDoorBlock.OPEN), "Unpowered door did not close");
            level.removeBlock(pos, false);
        }
        var empty = new ItemStack(ZSSRegistries.getItem("big_key"));
        var tooltip = new ArrayList<Component>();
        empty.getItem().appendHoverText(empty, level, tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(BigKeyItem.dungeon(empty).isEmpty() && tooltip.size() == 1, "Unbound key must have a warning");
        helper.succeed();
    }
}
