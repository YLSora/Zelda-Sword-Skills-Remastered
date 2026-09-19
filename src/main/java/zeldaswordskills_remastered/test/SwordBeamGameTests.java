package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.entity.projectile.SwordBeam;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SwordBeamGameTests {
    private SwordBeamGameTests() { }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void releaseRequirementsAndLevelValues(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        float[] factors = {0.30F, 0.40F, 0.50F, 0.60F, 0.75F};
        for (int level = 1; level <= 5; level++) {
            data.combat().reset();
            data.setSkillLevel(ZSSContentIds.SWORD_BEAM, level);
            fire(player, data);
            List<? extends SwordBeam> beams = beams(helper, player);
            helper.assertTrue(beams.size() == 1 && data.currentMagic() == 0.0F,
                    "Zero-magic release failed or produced multiple beams");
            CompoundTag tag = new CompoundTag();
            beams.get(0).addAdditionalSaveData(tag);
            float expected = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * factors[level - 1];
            helper.assertTrue(Math.abs(tag.getFloat("damage") - expected) < 0.001F
                            && tag.getFloat("maximum_range") == 8 + level && tag.getBoolean("piercing"),
                    "Beam damage, range or Master Sword piercing differs at level " + level);
            beams.get(0).discard();
        }
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(24.0D);
        for (int failure = 0; failure < 4; failure++) {
            data.combat().reset();
            player.setHealth(player.getMaxHealth());
            player.setOnGround(true);
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.MASTER_SWORD.get()));
            switch (failure) {
                case 0 -> player.setHealth(player.getMaxHealth() - 0.1F);
                case 1 -> player.setOnGround(false);
                case 2 -> player.setShiftKeyDown(false);
                case 3 -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                default -> throw new AssertionError();
            }
            fire(player, data);
            helper.assertTrue(beams(helper, player).isEmpty()
                            && !data.combat().swordBeamCoolingDown(helper.getLevel().getGameTime()),
                    "Failed release spawned a beam or started cooldown: " + failure);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void missAndHitKeepReleaseCooldown(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        fire(player, data);
        beams(helper, player).get(0).discard();
        fire(player, data);
        helper.assertTrue(beams(helper, player).isEmpty(), "A missed beam cleared the cooldown");
        helper.runAfterDelay(39, () -> {
            fire(player, data);
            helper.assertTrue(beams(helper, player).isEmpty(), "Cooldown ended before forty ticks");
        });
        helper.runAfterDelay(40, () -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            fire(player, data);
            List<? extends SwordBeam> beams = beams(helper, player);
            helper.assertTrue(beams.size() == 1, "Cooldown did not permit release at tick forty");
            IronGolem target = target(helper, player.position().add(3, 0, 0));
            strike(beams.get(0), target);
            helper.assertTrue(beams.get(0).isRemoved(), "Ordinary beam pierced an enemy");
            fire(player, data);
            helper.assertTrue(beams(helper, player).isEmpty(), "A hit cleared the release cooldown");
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(data.combat().swordBeamCoolingDown(now + 39)
                            && !data.combat().swordBeamCoolingDown(now + 40), "Hit changed the cooldown deadline");
            data.combat().reset();
            helper.assertTrue(!data.combat().swordBeamCoolingDown(now), "Reset retained Sword Beam cooldown");
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void piercingLeavesComboUntouchedAndClipsRange(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        IronGolem first = target(helper, player.position().add(3, 0, 0));
        IronGolem second = target(helper, player.position().add(7, 0, 0));
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        data.combat().setTarget(first.getId());
        long deadline = helper.getLevel().getGameTime() + 30;
        data.combat().recordHit(first.getId(), 2.0F, 3, deadline);
        SwordBeam beam = new SwordBeam(helper.getLevel(), player, 10.0F, 13.0F, true);
        strike(beam, first);
        helper.assertTrue(!beam.isRemoved() && Math.abs(first.getHealth() - 90.0F) < 0.001F,
                "Piercing beam did not damage its first target");
        first.invulnerableTime = 0;
        strike(beam, first);
        helper.assertTrue(Math.abs(first.getHealth() - 90.0F) < 0.001F, "Beam hit the same target twice");
        strike(beam, second);
        helper.assertTrue(Math.abs(second.getHealth() - 92.0F) < 0.001F,
                "Piercing did not retain twenty-percent damage decay");
        helper.assertTrue(data.combat().comboCount() == 1 && data.combat().comboDamage() == 2.0F
                        && data.combat().comboDeadline() == deadline && !data.combat().comboFinished(),
                "Sword Beam modified the ongoing combo");
        beam.discard();
        first.discard();
        second.discard();
        SwordBeam ranged = new SwordBeam(helper.getLevel(), player, 10.0F, 9.0F, true);
        Vec3 start = player.position().add(0, 10, 0);
        ranged.setPos(start);
        ranged.setNoGravity(true);
        ranged.setDeltaMovement(20.0D, 0.0D, 0.0D);
        ranged.tick();
        helper.assertTrue(ranged.isRemoved() && Math.abs(ranged.position().distanceTo(start) - 9.0D) < 0.001D,
                "Final collision sweep exceeded the beam range");
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[Sword_Beam]")) {
            @Override
            public float getAttackStrengthScale(float partialTick) { return 1.0F; }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 20, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.MASTER_SWORD.get()));
        player.setHealth(player.getMaxHealth());
        player.setOnGround(true);
        player.setShiftKeyDown(true);
        data(player).setSkillLevel(ZSSContentIds.SWORD_BEAM, 1);
        data(player).setMagic(0.0F, 50.0F);
        return player;
    }

    private static ZSSPlayerData data(FakePlayer player) {
        return ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
    }

    private static void fire(FakePlayer player, ZSSPlayerData data) {
        AdvancedSwordSkills.handleIntent(player, data, new SkillIntentMessage(
                ZSSContentIds.SWORD_BEAM, SkillIntentMessage.Action.ATTACK, Optional.empty()));
    }

    private static List<? extends SwordBeam> beams(GameTestHelper helper, FakePlayer player) {
        return helper.getLevel().getEntities(ZSSRegistries.SWORD_BEAM.get(), beam -> !beam.isRemoved() && beam.getOwner() == player);
    }

    private static IronGolem target(GameTestHelper helper, Vec3 position) {
        IronGolem target = EntityType.IRON_GOLEM.create(helper.getLevel());
        target.setPos(position);
        target.setNoAi(true);
        target.setNoGravity(true);
        helper.getLevel().addFreshEntity(target);
        return target;
    }

    private static void strike(SwordBeam beam, IronGolem target) {
        beam.setNoGravity(true);
        beam.setPos(target.position().add(-2, 1, 0));
        beam.setDeltaMovement(3.0D, 0.0D, 0.0D);
        beam.tick();
    }
}
