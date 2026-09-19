package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.HelmSplitter;
import zeldaswordskills_remastered.event.ZSSCombatEvents;
import zeldaswordskills_remastered.registry.ZSSContentIds;

import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HelmSplitterGameTests {
    private HelmSplitterGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void onlySuccessfulHitsGrantThirtyTicksOfFullProtection(GameTestHelper helper) {
        for (boolean hit : new boolean[] {false, true}) {
            FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "helm_immunity"));
            player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            var data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player capability"));
            data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 5);
            data.setSkillLevel(ZSSContentIds.HELM_SPLITTER, 1);
            var target = EntityType.IRON_GOLEM.create(helper.getLevel());
            target.setPos(player.position().add(0, 0, 2));
            target.setNoAi(true);
            target.setInvulnerable(!hit);
            helper.getLevel().addFreshEntity(target);
            data.combat().setTarget(target.getId());
            long now = helper.getLevel().getGameTime();
            var state = data.combat().helmSplitter();
            state.arm(target.getId(), now + 20, player.getY());
            player.setPos(player.position().add(0, 0.5D, 0));
            player.setOnGround(false);
            helper.assertTrue(HelmSplitter.tryStrike(player, data) && state.hitImmune(now) == hit,
                    "Only a successful damage application may grant hit immunity");
            FakePlayer other = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "other_attacker"));
            var arrow = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), other);
            for (int delay : new int[] {0, 19, 20}) {
                helper.runAfterDelay(delay, () -> {
                    for (var source : new net.minecraft.world.damagesource.DamageSource[] {
                            player.damageSources().generic(), player.damageSources().inFire(),
                            player.damageSources().playerAttack(other), player.damageSources().arrow(arrow, other)}) {
                        var event = new net.minecraftforge.event.entity.living.LivingAttackEvent(player, source, 1);
                        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
                        helper.assertTrue(event.isCanceled() == (hit && state.hitImmune(helper.getLevel().getGameTime())),
                                "Helm Splitter damage-event immunity differs at tick " + delay);
                    }
                });
            }
            helper.runAfterDelay(21, () -> {
                state.land(helper.getLevel().getGameTime());
                helper.assertTrue(state.hitImmune(helper.getLevel().getGameTime()) == hit,
                        "Landing changed the existing full-protection deadline");
                state.hit(helper.getLevel().getGameTime());
                data.combat().reset();
                helper.assertTrue(!state.hitImmune(helper.getLevel().getGameTime()),
                        "Lifecycle reset retained hit immunity");
                target.discard();
            });
        }
        helper.runAfterDelay(22, helper::succeed);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void parryJumpDealsOneArmorPiercingComboAndPreservesDoubleJump(GameTestHelper helper) {
        for (int level : new int[] {1, 5}) {
            FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "helm_test"));
            player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
            player.setOnGround(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            var data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player capability"));
            data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 5);
            data.setSkillLevel(ZSSContentIds.PARRY, 1);
            data.setSkillLevel(ZSSContentIds.HELM_SPLITTER, level);
            var target = EntityType.IRON_GOLEM.create(helper.getLevel());
            target.setPos(player.position().add(0, 0, 2));
            target.setYRot(180);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            target.getAttribute(Attributes.ARMOR).setBaseValue(30);
            target.setHealth(1000);
            helper.getLevel().addFreshEntity(target);
            data.combat().setTarget(target.getId());
            long now = helper.getLevel().getGameTime();
            data.combat().startParry(now, now + 22);
            helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, player.damageSources().mobAttack(target)),
                    "Parry did not open Helm Splitter without Sword Break");
            helper.assertTrue(!HelmSplitter.tryStrike(player, data), "A grounded attack used Helm Splitter");
            player.setPos(player.position().add(0, 0.5D, 0));
            player.setOnGround(false);
            if (level == 5) data.combat().useDoubleJump();
            int food = player.getFoodData().getFoodLevel();
            float exhaustion = player.getFoodData().getExhaustionLevel();
            float expected = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 1.5F * level;
            helper.assertTrue(HelmSplitter.tryStrike(player, data) && Math.abs(target.getHealth() - (1000 - expected)) < 0.01F,
                    "Helm Splitter damage was blocked by armor or had the wrong level scaling");
            helper.assertTrue(data.combat().comboCount() == 1 && !HelmSplitter.tryStrike(player, data),
                    "One parry granted repeated damage or failed to count one combo hit");
            helper.assertTrue(data.combat().doubleJumpUsed() == (level == 5)
                            && player.getFoodData().getFoodLevel() == food
                            && player.getFoodData().getExhaustionLevel() == exhaustion,
                    "Helm Splitter consumed Double Jump or hunger");
            helper.assertTrue(player.getDeltaMovement().y > 0 && player.getDeltaMovement().horizontalDistanceSqr() > 0,
                    "Helm Splitter omitted its upward or rearward movement");
            helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, player.damageSources().mobAttack(target))
                            && AdvancedSwordSkills.onAttacked(player, data, player.damageSources().inFire())
                            && AdvancedSwordSkills.onAttacked(player, data, player.damageSources().generic()),
                    "A successful Helm Splitter must protect against all damage sources");
            helper.assertTrue(data.combat().helmSplitter().hitImmune(now + 29)
                            && !data.combat().helmSplitter().hitImmune(now + 30),
                    "Hit immunity must expire exactly thirty ticks after the hit");
            double waiver = HelmSplitter.fallGrace(level);
            var fall = new net.minecraftforge.event.entity.living.LivingFallEvent(player, (float) waiver + 7, 1);
            ZSSCombatEvents.playerFall(fall);
            helper.assertTrue(Math.abs(fall.getDistance() - 7) < 0.001F && data.combat().helmSplitter().fallGrace() == 0,
                    "Landing did not pass only the excess fall distance to vanilla");
            helper.assertTrue(data.combat().helmSplitter().immune(target.getUUID(), now + 19)
                            && !data.combat().helmSplitter().immune(target.getUUID(), now + 20),
                    "Landing immunity differs from twenty ticks");
            target.discard();
        }
        helper.succeed();
    }
}
