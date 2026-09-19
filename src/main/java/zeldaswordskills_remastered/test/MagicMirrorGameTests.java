package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.MagicMirrorService;

import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class MagicMirrorGameTests {
    private MagicMirrorGameTests() {}

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void mirrorUsesVanillaScaleAndNetherHeight(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "Nether must be available");
        BlockPos target = MagicMirrorService.scaledTarget(overworld, nether, new Vec3(800, 70, -808));
        helper.assertTrue(target.equals(new BlockPos(100, 70, -101)), "Overworld coordinates must divide by eight");
        helper.assertTrue(MagicMirrorService.scaledTarget(nether, overworld, Vec3.atLowerCornerOf(target))
                .equals(new BlockPos(800, 70, -808)), "Nether coordinates must multiply by eight");
        helper.assertTrue(MagicMirrorService.scaledTarget(overworld, nether, new Vec3(0, 300, 0)).getY() <= 126,
                "Landing must remain below the Nether ceiling");
        helper.assertTrue(nether.getWorldBorder().isWithinBounds(MagicMirrorService.scaledTarget(overworld, nether,
                new Vec3(1.0E10, 70, -1.0E10))), "Scaled destination must respect the world border");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void mirrorRejectsUnsafeLandings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(4, 3, 4));
        clear(level, feet.offset(-2, -1, -2), feet.offset(2, 2, 2));
        level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(MagicMirrorService.safe(level, feet), "Clear supported landing must be safe");
        helper.assertTrue(MagicMirrorService.findLanding(level, feet).equals(feet), "Existing safe destination must be used");
        helper.assertTrue(level.getBlockState(feet.below()).is(Blocks.STONE), "Safe destination must not create a platform");
        for (Block hazard : new Block[]{Blocks.LAVA, Blocks.WATER, Blocks.FIRE, Blocks.POWDER_SNOW, Blocks.CACTUS,
                Blocks.SWEET_BERRY_BUSH, Blocks.WITHER_ROSE, Blocks.NETHER_PORTAL}) {
            level.setBlock(feet, hazard.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(!MagicMirrorService.safe(level, feet), "Unsafe destination accepted: " + hazard);
        }
        level.setBlock(feet, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(feet.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(!MagicMirrorService.safe(level, feet), "Low ceiling must be rejected");
        level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(!MagicMirrorService.safe(level, feet), "Magma floor must be rejected");
        level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(feet.east(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(!MagicMirrorService.safe(level, feet), "Adjacent lava must be rejected");
        level.setBlock(feet.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(feet.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(!MagicMirrorService.safe(level, feet), "Unsupported destination must be rejected");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void mirrorCreatesPersistentLeavesWhenNoFloorExists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Keep the entire search volume away from other concurrently running tests.
        BlockPos feet = helper.absolutePos(new BlockPos(0, 0, 0)).offset(0, 0, 2048).atY(80);
        clear(level, new BlockPos(feet.getX() - 18, level.getMinBuildHeight(), feet.getZ() - 18),
                new BlockPos(feet.getX() + 18, level.getMaxBuildHeight() - 1, feet.getZ() + 18));
        helper.assertTrue(feet.equals(MagicMirrorService.findLanding(level, feet)), "Empty destination must receive a platform");
        assertPlatform(helper, level, feet, Blocks.OAK_LEAVES);
        helper.assertTrue(level.getBlockState(feet.below()).getValue(LeavesBlock.PERSISTENT), "Leaf platform must not decay");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void mirrorCreatesObsidianBelowNetherRoof(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos feet = new BlockPos(2048, 80, 2048);
        clear(level, new BlockPos(feet.getX() - 18, 0, feet.getZ() - 18),
                new BlockPos(feet.getX() + 18, 127, feet.getZ() + 18));
        helper.assertTrue(feet.equals(MagicMirrorService.findLanding(level, feet)), "Empty Nether destination must receive a platform");
        assertPlatform(helper, level, feet, Blocks.OBSIDIAN);
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void mirrorChargesOnceAndWaitsForSoundBeforeTeleport(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        ItemStack mirror = new ItemStack(ZSSRegistries.getItem("magic_mirror"));
        player.setItemInHand(InteractionHand.MAIN_HAND, mirror);
        player.setPos(80, 70, 80);
        helper.assertTrue(mirror.getUseDuration() == 60, "Charge must last exactly three seconds");
        mirror.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.releaseUsingItem();
        helper.assertTrue(MagicMirrorService.canUse(player), "Releasing during charge must cancel without a pending warp");
        mirror.getItem().finishUsingItem(mirror, helper.getLevel(), player);
        helper.assertTrue(!MagicMirrorService.canUse(player), "Committed warp must not restart");
        MagicMirrorService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(player.level() == helper.getLevel(), "Warp must not teleport immediately");
        helper.runAfterDelay(MagicMirrorService.TELEPORT_DELAY - 1, () -> {
            MagicMirrorService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(player.level() == helper.getLevel(), "Warp must wait for audio plus twenty ticks");
        });
        helper.runAfterDelay(MagicMirrorService.TELEPORT_DELAY, () -> {
            try {
                MagicMirrorService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                helper.assertTrue(player.level().dimension() == Level.NETHER, "Warp must enter Nether at deadline");
                helper.assertTrue(MagicMirrorService.safe(player.serverLevel(), player.blockPosition()), "Warp must arrive at a safe landing");
                helper.assertTrue(player.fallDistance == 0 && player.getDeltaMovement().equals(Vec3.ZERO), "Warp must clear falling momentum");
                helper.succeed();
            } finally {
                dispose(player);
            }
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void mirrorClearsPendingWarpOnDimensionChangeAndLogout(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        try {
            MagicMirrorService.start(player);
            MagicMirrorService.dimensionChanged(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(MagicMirrorService.canUse(player), "Dimension change must clear pending warp");
            MagicMirrorService.start(player);
            MagicMirrorService.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(MagicMirrorService.canUse(player), "Logout must clear pending warp");
            player.setServerLevel(player.server.getLevel(Level.END));
            helper.assertTrue(!MagicMirrorService.canUse(player), "Mirror must only connect Overworld and Nether");
            helper.succeed();
        } finally {
            dispose(player);
        }
    }

    private static void assertPlatform(GameTestHelper helper, ServerLevel level, BlockPos feet, Block block) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            helper.assertTrue(level.getBlockState(feet.offset(x, -1, z)).is(block), "Platform must be 3x3: " + block);
            helper.assertTrue(level.getBlockState(feet.offset(x, 0, z)).isAir()
                    && level.getBlockState(feet.offset(x, 1, z)).isAir(), "Platform must provide standing room");
        }
        helper.assertTrue(MagicMirrorService.safe(level, feet), "Generated platform must be safe");
    }

    private static void clear(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static ServerPlayer testPlayer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Mirror]"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        return player;
    }

    private static void dispose(ServerPlayer player) {
        MagicMirrorService.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        player.getAdvancements().stopListening();
        player.discard();
    }
}
