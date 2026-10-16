package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;

import java.util.Optional;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DodgeGameTests {
    private DodgeGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void backwardDodgeUsesVanillaTravelAndCollision(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(2, 20, 2));
        for (var surface : new net.minecraft.world.level.block.Block[] {Blocks.STONE, Blocks.BLUE_ICE}) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -3; z <= 1; z++) {
                    helper.getLevel().setBlockAndUpdate(floor.offset(x, 0, z), surface.defaultBlockState());
                }
            }
            for (boolean wall : new boolean[] {false, true}) {
                for (int y = 1; y <= 2; y++) {
                    helper.getLevel().setBlockAndUpdate(floor.offset(0, y, -1),
                            (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                }
                FakePlayer player = player(helper);
                player.setPos(Vec3.atBottomCenterOf(floor.above()));
                // Grounded players retain vanilla's downward gravity velocity between ticks.
                player.setDeltaMovement(0.0D, -0.0784D, 0.0D);
                Vec3 start = player.position();
                tap(player, new Vec3(0, 0, -1));
                tap(player, new Vec3(0, 0, -1));
                helper.assertTrue(player.getDeltaMovement().z < 0, "Double-tap backward did not launch");
                // FakePlayers do not tick; run vanilla travel with no additional walking input.
                for (int tick = 0; tick < 200; tick++) player.travel(Vec3.ZERO);
                double distance = start.z - player.getZ();
                helper.assertTrue(wall ? distance > 0 && distance < 0.3D : Math.abs(distance - 2.0D) < 0.2D,
                        "Backward Dodge distance/collision failed on " + surface + ": " + distance);
                helper.assertTrue(Math.abs(player.getX() - start.x) < 1.0E-5D,
                        "Backward Dodge drifted sideways");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void backwardDodgeSharesUnlockTapImmunityAndCooldownRules(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        Vec3 back = new Vec3(0, 0, -1);
        long now = helper.getLevel().getGameTime();
        data.setSkillLevel(ZSSContentIds.DODGE, 0);
        tap(player, back);
        tap(player, back);
        helper.assertTrue(!data.combat().dodgeActive(now), "Unlearned backward Dodge activated");
        data.setSkillLevel(ZSSContentIds.DODGE, 1);
        data.setSkillEnabled(ZSSContentIds.DODGE, false);
        tap(player, back);
        tap(player, back);
        helper.assertTrue(!data.combat().dodgeActive(now), "Disabled backward Dodge activated");
        data.setSkillEnabled(ZSSContentIds.DODGE, true);
        player.setOnGround(false);
        tap(player, back);
        tap(player, back);
        helper.assertTrue(!data.combat().dodgeActive(now), "Airborne backward Dodge activated");
        player.setOnGround(true);
        tap(player, back.reverse());
        tap(player, back.reverse());
        helper.assertTrue(!data.combat().dodgeActive(now), "Forward taps activated Dodge");
        tap(player, new Vec3(-1, 0, 0));
        tap(player, back);
        helper.assertTrue(!data.combat().dodgeActive(now), "Mixed side/back taps activated Dodge");
        data.combat().reset();
        var target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 20, 4));
        target.setNoAi(true);
        for (int level = 1; level <= 5; level++) {
            data.combat().reset();
            data.setSkillLevel(ZSSContentIds.DODGE, level);
            data.combat().setTarget(target.getId());
            player.setYRot(90.0F);
            player.setXRot(-90.0F);
            player.setDeltaMovement(Vec3.ZERO);
            Vec3 backward = Vec3.directionFromRotation(0, player.getYRot()).reverse();
            tap(player, backward);
            helper.assertTrue(!data.combat().dodgeActive(now), "A single backward tap activated Dodge");
            tap(player, backward);
            helper.assertTrue(data.combat().dodgeActive(now + 19) && !data.combat().dodgeActive(now + 20),
                    "Backward Dodge did not retain twenty ticks of immunity");
            long deadline = now + 90 - 10L * level;
            helper.assertTrue(data.combat().dodgeOnCooldown(deadline - 1) && !data.combat().dodgeOnCooldown(deadline),
                    "Backward Dodge changed the level-based shared cooldown");
            Vec3 motion = player.getDeltaMovement();
            helper.assertTrue(motion.normalize().dot(backward) > 0.999D,
                    "Locked backward Dodge orbited the target or depended on camera pitch");
            helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, player.damageSources().inFire())
                            && AdvancedSwordSkills.onAttacked(player, data, player.damageSources().fall()),
                    "Backward Dodge did not grant complete damage immunity");
            tap(player, new Vec3(0, 0, -1));
            tap(player, new Vec3(0, 0, -1));
            helper.assertTrue(player.getDeltaMovement().equals(motion), "A side dodge bypassed backward Dodge's cooldown");
        }
        target.discard();
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[Dodge_Test]"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 21, 2))));
        player.setOnGround(true);
        data(player).setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        data(player).setSkillLevel(ZSSContentIds.DODGE, 1);
        return player;
    }

    private static ZSSPlayerData data(FakePlayer player) {
        return ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
    }

    private static void tap(FakePlayer player, Vec3 direction) {
        AdvancedSwordSkills.handleIntent(player, data(player),
                new SkillIntentMessage(ZSSContentIds.DODGE, SkillIntentMessage.Action.BEGIN, Optional.of(direction)));
    }
}
