package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.*;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.TargetIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WeaponSkillsGameTests {
    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void lockRangeAndViewBoundaries(GameTestHelper helper) {
        var player = player(helper);
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        var target = target(helper, player);
        int[] ranges = {8, 8, 10, 12, 14, 16, 18, 20, 22, 24};
        for (int level = 1; level <= 10; level++) {
            data.setSkillLevel(ZSSContentIds.SWORD_BASIC, level);
            target.setPos(player.position().add(0, 0, ranges[level - 1]));
            data.combat().setTarget(target.getId());
            helper.assertTrue(TargetingService.getLockedTarget(player, data).orElse(null) == target,
                    "Lock rejected range boundary at level " + level);
            target.setPos(player.position().add(0, 0, ranges[level - 1] + 0.01));
            helper.assertTrue(TargetingService.getLockedTarget(player, data).isEmpty(),
                    "Lock exceeded range at level " + level);
        }
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        for (double dot : new double[]{0.34, 0.36}) {
            data.combat().clearTarget();
            target.setPos(player.getX() + 5 * Math.sqrt(1 - dot * dot),
                    player.getEyeY() - target.getBbHeight() / 2, player.getZ() + 5 * dot);
            TargetingService.handle(player, data, TargetIntentMessage.Action.ACQUIRE);
            helper.assertTrue((data.combat().targetId() == target.getId()) == (dot > 0.35),
                    "Incorrect view cone boundary: " + dot);
        }
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void lockedAttackUsesEquippedEntityReach(GameTestHelper helper) {
        var player = player(helper);
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        var target = target(helper, player);
        target.setPos(player.position().add(0, 0, 5));
        data.combat().setTarget(target.getId());
        helper.assertTrue(!BasicSwordSkill.attack(player, data), "Default weapon reached five blocks");
        var weapon = new ItemStack(Items.IRON_AXE);
        var reach = new AttributeModifier(UUID.randomUUID(), "Test weapon reach", 3, AttributeModifier.Operation.ADDITION);
        weapon.addAttributeModifier(ForgeMod.ENTITY_REACH.get(), reach, EquipmentSlot.MAINHAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        // FakePlayer does not tick equipment changes; apply the equipped stack as LivingEntity does.
        player.getAttributes().addTransientAttributeModifiers(weapon.getAttributeModifiers(EquipmentSlot.MAINHAND));
        helper.assertTrue(BasicSwordSkill.attack(player, data), "Weapon reach was ignored while locked");
        player.getAttributes().removeAttributeModifiers(weapon.getAttributeModifiers(EquipmentSlot.MAINHAND));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        target.invulnerableTime = 0;
        helper.assertTrue(!BasicSwordSkill.attack(player, data), "Previous weapon's reach survived switching");
        target.setPos(player.position().add(0, 0, 2));
        helper.assertTrue(BasicSwordSkill.attack(player, data), "Normal melee stopped working");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void nonSwordWeaponsUseOffensiveSkillsButNotSwordOnlySkills(GameTestHelper helper) {
        for (var item : new net.minecraft.world.item.Item[]{Items.IRON_AXE, Items.TRIDENT}) {
            var player = player(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
            for (var skill : ZSSContentIds.SKILLS) data.setSkillLevel(skill, 1);
            var target = target(helper, player);
            data.combat().setTarget(target.getId());
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(TargetingService.getWeaponTarget(player, data).isPresent()
                    && TargetingService.getValidTarget(player, data).isEmpty(), "Weapon and sword eligibility overlap");
            intent(player, data, ZSSContentIds.PARRY, SkillIntentMessage.Action.BEGIN);
            helper.assertTrue(!data.combat().parryActive(now), "Non-sword parried");
            data.combat().startSwordBreak(target.getId(), now + 20);
            float health = target.getHealth();
            intent(player, data, ZSSContentIds.SWORD_BREAK, SkillIntentMessage.Action.ATTACK);
            helper.assertTrue(target.getHealth() == health, "Non-sword used Sword Break");
            data.combat().helmSplitter().arm(target.getId(), now + 20, player.getY() - 1);
            player.setOnGround(false);
            helper.assertTrue(!HelmSplitter.tryStrike(player, data), "Non-sword consumed a Helm Splitter attack");
            player.setOnGround(true);
            player.setShiftKeyDown(true);
            intent(player, data, ZSSContentIds.SWORD_BEAM, SkillIntentMessage.Action.ATTACK);
            helper.assertTrue(!data.combat().swordBeamCoolingDown(now), "Non-sword fired Sword Beam");
            intent(player, data, ZSSContentIds.RISING_CUT, SkillIntentMessage.Action.BEGIN);
            intent(player, data, ZSSContentIds.RISING_CUT, SkillIntentMessage.Action.ATTACK);
            helper.assertTrue(player.getDeltaMovement().y > 0 && target.getHealth() < health, "Weapon could not use Rising Cut");
            player.setShiftKeyDown(false);
            intent(player, data, ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.ATTACK);
            helper.assertTrue(data.combat().spinActive(now), "Weapon could not spin");
            data.combat().finishSpin();
            intent(player, data, ZSSContentIds.DASH, SkillIntentMessage.Action.BEGIN);
            helper.assertTrue(data.combat().dashPending(), "Weapon could not dash");
            data.combat().finishDash();
            player.setOnGround(false);
            player.fallDistance = 1;
            data.combat().armGroundSlam();
            helper.assertTrue(GroundSlam.canStrike(player, data), "Weapon could not use Ground Slam");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            helper.assertTrue(!TargetingService.isHoldingWeapon(player) && !GroundSlam.canStrike(player, data),
                    "Ordinary item gained weapon skills");
            target.discard();
        }
        helper.succeed();
    }

    private static void intent(FakePlayer player, zeldaswordskills_remastered.capability.ZSSPlayerData data,
                               net.minecraft.resources.ResourceLocation skill, SkillIntentMessage.Action action) {
        AdvancedSwordSkills.handleIntent(player, data, new SkillIntentMessage(skill, action, Optional.empty()));
    }

    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "weapon_test")) {
            @Override public float getAttackStrengthScale(float partialTick) { return 1; }
        };
        player.setPos(helper.absoluteVec(new Vec3(4, 12, 4)));
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        ZSSCapabilities.get(player).orElseThrow(AssertionError::new).setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        return player;
    }

    private static net.minecraft.world.entity.monster.Zombie target(GameTestHelper helper, FakePlayer player) {
        var target = EntityType.ZOMBIE.create(helper.getLevel());
        target.setPos(player.position().add(0, 0, 2));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        helper.getLevel().addFreshEntity(target);
        return target;
    }
}
