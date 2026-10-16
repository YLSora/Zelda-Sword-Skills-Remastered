package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.SkillAvailability;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.entity.projectile.SwordBeam;
import zeldaswordskills_remastered.event.ZSSCombatEvents;
import zeldaswordskills_remastered.registry.ZSSContentIds;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillAvailabilityGameTests {
    private SkillAvailabilityGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssSkillCommands")
    public static void learnAndForgetAllSkillsUseCommandPermissionsAndClearEffects(GameTestHelper helper) throws CommandSyntaxException {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "all_skills_test"));
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        for (String command : new String[]{"zss skills learn all", "zss skills forget all"}) {
            var before = data.skills();
            try {
                dispatcher.execute(command, source.withPermission(0));
                throw new AssertionError("Bulk skill command bypassed operator permissions");
            } catch (CommandSyntaxException expected) {
                helper.assertTrue(data.skills().equals(before), "Unauthorized bulk command changed skills");
            }
        }
        helper.assertTrue(dispatcher.execute("zss skills learn all", source) == 1, "Learn all failed");
        for (var skill : ZSSContentIds.SKILLS) {
            helper.assertTrue(data.activeSkillLevel(skill) == data.skillMaximum(skill),
                    "Learn all did not maximize and enable " + skill);
        }
        data.setSkillEnabled(ZSSContentIds.DODGE, false);
        dispatcher.execute("zss skills learn all", source);
        helper.assertTrue(data.activeSkillLevel(ZSSContentIds.DODGE) == 5, "Learn all retained a disabled skill");
        long now = helper.getLevel().getGameTime();
        data.combat().setTarget(123);
        data.combat().startDodge(now, 1);
        data.combat().startParry(now, now + 22);
        data.combat().startSpin(now + 8, now + 20);
        player.setDeltaMovement(1, 0, 0);
        var beam = new SwordBeam(helper.getLevel(), player, 10, 10, false);
        helper.getLevel().addFreshEntity(beam);
        helper.assertTrue(dispatcher.execute("zss skills forget all", source) == 1 && data.skills().isEmpty(),
                "Forget all retained learned skills");
        for (var skill : ZSSContentIds.SKILLS) {
            helper.assertTrue(data.skillEnabled(skill), "Forget all disabled future skill learning");
        }
        helper.assertTrue(!data.combat().dodgeActive(now) && !data.combat().parryActive(now)
                        && !data.combat().spinPending() && data.combat().targetId() < 0
                        && player.getDeltaMovement().horizontalDistanceSqr() == 0 && beam.isRemoved(),
                "Forget all retained active skill effects");
        helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(AdvancedSwordSkills.BONUS_HEART_MODIFIER_ID) == null
                        && player.getHealth() <= player.getMaxHealth(), "Forget all retained bonus hearts");
        dispatcher.execute("zss skills learn all", source);
        for (var skill : ZSSContentIds.SKILLS) {
            helper.assertTrue(data.activeSkillLevel(skill) == data.skillMaximum(skill), "Relearning all left a skill disabled");
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssSkillCommands")
    public static void singleSkillCommandsUseMaximumLevelsAndFilterSuggestions(GameTestHelper helper) throws CommandSyntaxException {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "single_skill_test"));
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        for (String action : new String[]{"learn", "forget"}) {
            try {
                dispatcher.execute("zss skills " + action + " " + ZSSContentIds.DODGE, source.withPermission(0));
                throw new AssertionError("Single skill command bypassed operator permissions");
            } catch (CommandSyntaxException expected) {
                helper.assertTrue(data.skills().isEmpty(), "Unauthorized command changed a skill");
            }
        }
        var emptyForget = dispatcher.getCompletionSuggestions(dispatcher.parse("zss skills forget ", source)).join();
        helper.assertTrue(emptyForget.getList().size() == 1 && emptyForget.getList().get(0).getText().equals("all"),
                "Forget suggestions included unlearned skills");
        data.setSkillLevel(ZSSContentIds.DODGE, 2);
        data.setSkillEnabled(ZSSContentIds.DODGE, false);
        helper.assertTrue(dispatcher.execute("zss skills learn " + ZSSContentIds.DODGE, source) == 1
                        && data.activeSkillLevel(ZSSContentIds.DODGE) == data.skillMaximum(ZSSContentIds.DODGE),
                "Single learn did not maximize and enable the skill");
        var learn = dispatcher.getCompletionSuggestions(dispatcher.parse("zss skills learn ", source)).join();
        helper.assertTrue(learn.getList().stream().noneMatch(s -> s.getText().equals(ZSSContentIds.DODGE.toString()))
                        && learn.getList().stream().anyMatch(s -> s.getText().equals(ZSSContentIds.SWORD_BASIC.toString())),
                "Learn suggestions included a maxed skill or hid an unlearned skill");
        var forget = dispatcher.getCompletionSuggestions(dispatcher.parse("zss skills forget ", source)).join();
        helper.assertTrue(forget.getList().stream().map(s -> s.getText()).collect(java.util.stream.Collectors.toSet())
                        .equals(java.util.Set.of("all", ZSSContentIds.DODGE.toString())),
                "Forget suggestions did not match learned skills");
        long now = helper.getLevel().getGameTime();
        data.combat().startDodge(now, 1);
        player.setDeltaMovement(1, 0, 0);
        helper.assertTrue(dispatcher.execute("zss skills forget " + ZSSContentIds.DODGE, source) == 1
                        && data.skillLevel(ZSSContentIds.DODGE) == 0 && data.skillEnabled(ZSSContentIds.DODGE)
                        && !data.combat().dodgeActive(now) && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                "Single forget retained Dodge effects or disabled future learning");
        helper.assertTrue(dispatcher.execute("zss skills learn " + ZSSContentIds.SWORD_BASIC, source) == 1
                        && data.skillLevel(ZSSContentIds.SWORD_BASIC) == 10,
                "Single learn left Basic Sword Skill at level five");
        var before = data.skills();
        for (String action : new String[]{"learn", "forget"}) {
            helper.assertTrue(dispatcher.execute("zss skills " + action + " zeldaswordskills_remastered:unknown", source) == 0
                            && data.skills().equals(before), "Unknown skill command changed data");
        }
        dispatcher.execute("zss skills learn all", source);
        var maxed = dispatcher.getCompletionSuggestions(dispatcher.parse("zss skills learn ", source)).join();
        helper.assertTrue(maxed.getList().size() == 1 && maxed.getList().get(0).getText().equals("all"),
                "Maxed skills remained in learn suggestions");
        dispatcher.execute("zss skills forget all", source);
        var cleared = dispatcher.getCompletionSuggestions(dispatcher.parse("zss skills forget ", source)).join();
        helper.assertTrue(cleared.getList().size() == 1 && cleared.getList().get(0).getText().equals("all"),
                "Forgotten skills remained in suggestions");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssSkillCommands")
    public static void skillCommandRejectsOutOfRangeLevelsWithoutChangingData(GameTestHelper helper) throws CommandSyntaxException {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "skill_command_test"));
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        for (var skill : ZSSContentIds.SKILLS) {
            int maximum = skill.equals(ZSSContentIds.BONUS_HEART) ? ZSSConfig.SERVER.maximumHeartContainers.get()
                    : skill.equals(ZSSContentIds.SWORD_BASIC) ? 10 : 5;
            String command = "zss skills set " + skill + " ";
            helper.assertTrue(dispatcher.execute(command + maximum, source) == 1 && data.skillLevel(skill) == maximum,
                    "Command rejected the maximum valid skill level: " + skill);
            helper.assertTrue(dispatcher.execute(command + "0", source) == 1 && data.skillLevel(skill) == 0,
                    "Level zero did not clear the skill: " + skill);
            data.setSkillLevel(skill, Math.min(1, maximum));
            var before = data.skills();
            for (int invalid : new int[]{maximum + 1, Integer.MAX_VALUE}) {
                helper.assertTrue(dispatcher.execute(command + invalid, source) == 0 && data.skills().equals(before),
                        "Out-of-range command changed skills instead of rejecting the level: " + skill);
            }
            try {
                dispatcher.execute(command + "-1", source);
                throw new AssertionError("Negative skill level was accepted");
            } catch (CommandSyntaxException expected) {
                helper.assertTrue(data.skills().equals(before), "Negative level changed skill data");
            }
        }
        var before = data.skills();
        helper.assertTrue(dispatcher.execute("zss skills set zeldaswordskills_remastered:unknown 1", source) == 0
                        && data.skills().equals(before), "Unknown skill changed player data");
        try {
            dispatcher.execute("zss skills set " + ZSSContentIds.SWORD_BASIC + " 1", source.withPermission(0));
            throw new AssertionError("Skill command bypassed operator permissions");
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(data.skills().equals(before), "Rejected unauthorized command changed skills");
        }
        int previousMaximum = ZSSConfig.SERVER.maximumHeartContainers.get();
        try {
            for (int maximum : new int[]{3, 0}) {
                ZSSConfig.SERVER.maximumHeartContainers.set(maximum);
                String command = "zss skills set " + ZSSContentIds.BONUS_HEART + " ";
                helper.assertTrue(dispatcher.execute(command + maximum, source) == 1
                                && data.skillLevel(ZSSContentIds.BONUS_HEART) == maximum,
                        "Skill command did not use the current heart-container limit");
                helper.assertTrue(dispatcher.execute(command + (maximum + 1), source) == 0
                                && data.skillLevel(ZSSContentIds.BONUS_HEART) == maximum,
                        "Skill command ignored a changed heart-container limit");
            }
        } finally {
            ZSSConfig.SERVER.maximumHeartContainers.set(previousMaximum);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void preferencesPreserveLearningAndSurvivePlayerCopies(GameTestHelper helper) {
        var data = new ZSSPlayerData();
        for (var id : ZSSContentIds.SKILLS) data.setSkillLevel(id, data.skillMaximum(id));
        for (var id : ZSSContentIds.SKILLS) {
            if (id.equals(ZSSContentIds.BONUS_HEART)) continue;
            helper.assertTrue(data.setSkillEnabled(id, false) && data.activeSkillLevel(id) == 0
                    && data.skillLevel(id) == data.skillMaximum(id), "Disabling erased learning or retained gameplay: " + id);
        }
        helper.assertTrue(data.allSkillTypesLearned(), "Disabled skills no longer count as learned skill types");
        helper.assertTrue(!data.setSkillEnabled(ZSSContentIds.BONUS_HEART, false)
                && !data.setSkillEnabled(ResourceLocation.fromNamespaceAndPath("test", "unknown"), false),
                "Heart Containers or unknown skills accepted the toggle");
        var restored = new ZSSPlayerData();
        restored.load(data.save());
        var clone = new ZSSPlayerData();
        clone.copyFrom(restored);
        var deathWithoutSkills = new ZSSPlayerData();
        deathWithoutSkills.copyFrom(data);
        deathWithoutSkills.resetLearnedSkills();
        for (var id : ZSSContentIds.SKILLS) {
            if (id.equals(ZSSContentIds.BONUS_HEART)) continue;
            helper.assertTrue(!clone.skillEnabled(id) && clone.skillLevel(id) == data.skillLevel(id), "Clone lost preference: " + id);
            deathWithoutSkills.setSkillLevel(id, 1);
            helper.assertTrue(!deathWithoutSkills.skillEnabled(id), "Death with lost skills reset preference: " + id);
            clone.setSkillEnabled(id, true);
            helper.assertTrue(clone.activeSkillLevel(id) == data.skillLevel(id) && !data.skillEnabled(id),
                    "Re-enabling changed another player's preferences or lost levels: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void disablingCancelsEffectsWithoutRefunds(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "skill_toggle_test"));
        var data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player capability"));
        for (var id : ZSSContentIds.SKILLS) data.setSkillLevel(id, data.skillMaximum(id));
        long now = helper.getLevel().getGameTime();
        var state = data.combat();
        state.startDash(123, Vec3.ZERO, now + 20, 1);
        state.startDashCooldown(now + 100);
        state.startDashImmunity(now + 20);
        player.setDeltaMovement(1, 0.2D, 0);
        SkillAvailability.setEnabled(player, data, ZSSContentIds.DASH, false);
        helper.assertTrue(!state.dashPending() && !state.dashImmune(now) && state.dashCoolingDown(now)
                && player.getDeltaMovement().horizontalDistanceSqr() == 0, "Dash survived disable or refunded its cooldown");
        state.useDoubleJump(now);
        SkillAvailability.setEnabled(player, data, ZSSContentIds.DOUBLE_JUMP, false);
        var fall = new net.minecraftforge.event.entity.living.LivingFallEvent(player, 10, 1);
        ZSSCombatEvents.playerFall(fall);
        helper.assertTrue(fall.getDistance() == 10, "Disabled Double Jump still reduced fall damage");
        SkillAvailability.setEnabled(player, data, ZSSContentIds.DOUBLE_JUMP, true);
        helper.assertTrue(state.doubleJumpUsed(), "Toggling refunded the current airtime's extra jump");
        state.armGroundSlam();
        state.groundSlamHit(123, now);
        SkillAvailability.setEnabled(player, data, ZSSContentIds.LEAPING_BLOW, false);
        helper.assertTrue(!state.groundSlamReady() && !state.groundSlamFallImmune(), "Ground Slam kept landing or fall protection");
        state.beginCharge(ZSSContentIds.SPIN_ATTACK, now, Vec3.ZERO);
        state.startSpin(now + 8, now + 20);
        SkillAvailability.setEnabled(player, data, ZSSContentIds.SPIN_ATTACK, false);
        helper.assertTrue(!state.spinPending() && !state.charging(ZSSContentIds.SPIN_ATTACK) && state.spinCoolingDown(now),
                "Spin charge/rotation survived disable or refunded cooldown");
        var beam = new SwordBeam(helper.getLevel(), player, 10, 10, false);
        helper.getLevel().addFreshEntity(beam);
        SkillAvailability.setEnabled(player, data, ZSSContentIds.SWORD_BEAM, false);
        helper.assertTrue(beam.isRemoved(), "An existing Sword Beam survived disabling its skill");
        state.setTarget(123);
        state.recordHit(123, 10, 5, now + 40);
        state.startParry(now, now + 22);
        state.startSwordBreak(123, now + 22);
        state.helmSplitter().arm(123, now + 22, player.getY());
        state.startFocus(now);
        SkillAvailability.setEnabled(player, data, ZSSContentIds.SWORD_BASIC, false);
        helper.assertTrue(state.targetId() < 0 && state.comboCount() == 0 && !state.focusActive(now)
                && !state.parryActive(now) && !state.swordBreakActive(now) && !state.helmSplitter().ready(now),
                "Disabling Basic Sword Skill retained lock-dependent effects");
        int maximum = ZSSConfig.SERVER.maximumHeartContainers.get();
        data.setSkillLevel(ZSSContentIds.BONUS_HEART, maximum + 1);
        AdvancedSwordSkills.tickBonusHearts(player, data);
        helper.assertTrue(data.skillLevel(ZSSContentIds.BONUS_HEART) == maximum, "Heart count exceeded server configuration");
        data.setSkillLevel(ZSSContentIds.BONUS_HEART, 0);
        AdvancedSwordSkills.tickBonusHearts(player, data);
        helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(AdvancedSwordSkills.BONUS_HEART_MODIFIER_ID) == null
                && player.getHealth() <= player.getMaxHealth(), "Zero hearts retained health modifier or excess health");
        helper.succeed();
    }
}
