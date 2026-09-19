package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.BasicSwordSkill;
import zeldaswordskills_remastered.combat.GroundSlam;
import zeldaswordskills_remastered.combat.PlayerCombatState;
import zeldaswordskills_remastered.event.ZSSCombatEvents;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;

import java.util.Optional;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GroundSlamGameTests {
    private GroundSlamGameTests() { }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void landingAndFallBoundaries(GameTestHelper helper) {
        PlayerCombatState state = new PlayerCombatState();
        state.armGroundSlam();
        helper.assertTrue(!state.groundSlamLandingActive(100) && !state.groundSlamFallImmune(),
                "A second jump alone granted an impact or fall immunity");
        state.groundSlamHit(42, 100);
        helper.assertTrue(state.groundSlamLandingActive(100) && state.groundSlamLandingActive(104)
                        && !state.groundSlamLandingActive(105) && state.groundSlamFallImmune(),
                "The four-tick boundary and fall immunity must be independent");
        state.groundSlamHit(43, 101);
        state.setTarget(99);
        var excluded = state.groundSlamHitTargets();
        helper.assertTrue(excluded.contains(42) && excluded.contains(43) && !excluded.contains(99),
                "Landing exclusions must follow actual direct hits, not the current lock");
        state.reset();
        helper.assertTrue(!state.groundSlamReady() && !state.groundSlamFallImmune()
                        && !state.groundSlamLandingActive(100) && state.groundSlamHitTargets().isEmpty()
                        && excluded.contains(42), "Reset retained airborne state or mutated the landing snapshot");
        helper.assertTrue(GroundSlam.radius(1) == 1.0D && GroundSlam.radius(5) == 3.0D,
                "Ground Slam radius does not follow the level formula");

        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[Ground_Slam]")) {
            @Override public float getAttackStrengthScale(float partialTick) { return 1.0F; }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        data.setSkillLevel(ZSSContentIds.LEAPING_BLOW, 1);
        data.setSkillLevel(ZSSContentIds.DOUBLE_JUMP, 1);
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        player.setOnGround(false);
        player.fallDistance = 25.0F;
        AdvancedSwordSkills.handleIntent(player, data,
                new SkillIntentMessage(ZSSContentIds.DOUBLE_JUMP, SkillIntentMessage.Action.BEGIN, Optional.empty()));
        helper.assertTrue(player.fallDistance == 0.0F && data.combat().groundSlamReady()
                        && !GroundSlam.canStrike(player, data), "Launch retained prior fall distance or allowed an ascending hit");

        var target = EntityType.IRON_GOLEM.create(helper.getLevel());
        target.setPos(player.position().add(0.5D, 0.0D, 0.0D));
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        helper.getLevel().addFreshEntity(target);
        var bystander = EntityType.IRON_GOLEM.create(helper.getLevel());
        bystander.setPos(player.position().add(-0.5D, 0.0D, 0.0D));
        bystander.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        helper.getLevel().addFreshEntity(bystander);
        data.combat().setTarget(target.getId());
        player.fallDistance = 1.0F;
        float exhaustion = player.getFoodData().getExhaustionLevel();
        target.invulnerableTime = 20;
        float health = target.getHealth();
        AttackEntityEvent attack = new AttackEntityEvent(player, target);
        ZSSCombatEvents.jumpAttack(attack);
        float strike = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 2.0F;
        helper.assertTrue(attack.isCanceled() && Math.abs(health - target.getHealth() - strike) < 0.001F,
                "Ground Slam did not replace ordinary melee with one full armor-piercing hit");
        float beforeLanding = target.getHealth();
        float beforeArea = bystander.getHealth();
        LivingFallEvent fall = new LivingFallEvent(player, 80.0F, 1.0F);
        ZSSCombatEvents.playerFall(fall);
        float impact = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 0.75F;
        helper.assertTrue(fall.getDistance() == 0.0F && target.getHealth() == beforeLanding
                        && Math.abs(beforeArea - bystander.getHealth() - impact) < 0.001F,
                "Landing damaged the primary target again or missed the other armored enemy");
        helper.assertTrue(data.combat().comboCount() == 1, "One direct hit counted more than once towards the combo");
        float afterLanding = target.getHealth();
        GroundSlam.land(player, data);
        helper.assertTrue(target.getHealth() == afterLanding && !data.combat().groundSlamReady()
                        && !data.combat().groundSlamFallImmune(), "The landing was replayed or retained immunity");
        helper.assertTrue(player.getFoodData().getExhaustionLevel() == exhaustion, "Ground Slam consumed hunger");

        data.combat().groundSlamHit(target.getId(), helper.getLevel().getGameTime() - 5L);
        LivingFallEvent late = new LivingFallEvent(player, 80.0F, 1.0F);
        ZSSCombatEvents.playerFall(late);
        helper.assertTrue(late.getDistance() == 0.0F && target.getHealth() == afterLanding,
                "An expired impact window removed immunity or dealt area damage");
        LivingFallEvent next = new LivingFallEvent(player, 80.0F, 1.0F);
        ZSSCombatEvents.playerFall(next);
        helper.assertTrue(next.getDistance() > 70.0F, "Immunity leaked into a later fall");
        data.combat().armGroundSlam();
        data.combat().groundSlamHit(target.getId(), helper.getLevel().getGameTime());
        player.getAbilities().flying = true;
        GroundSlam.tick(player, data);
        helper.assertTrue(!data.combat().groundSlamReady() && !data.combat().groundSlamFallImmune(),
                "Flight did not interrupt the pending fall");
        player.getAbilities().flying = false;

        for (int skillLevel = 1; skillLevel <= 5; skillLevel++) {
            double grace = AdvancedSwordSkills.risingCutHeight(skillLevel) + 5.0D;
            data.combat().startRisingCutFallGrace(AdvancedSwordSkills.risingCutFallGrace(skillLevel));
            LivingFallEvent boundary = new LivingFallEvent(player, (float) grace, 1.0F);
            ZSSCombatEvents.playerFall(boundary);
            helper.assertTrue(boundary.getDistance() == 0.0F, "Rising Cut boundary dealt damage");
            data.combat().startRisingCutFallGrace(AdvancedSwordSkills.risingCutFallGrace(skillLevel));
            LivingFallEvent excess = new LivingFallEvent(player, (float) grace + 6.0F, 1.0F);
            ZSSCombatEvents.playerFall(excess);
            helper.assertTrue(excess.getDistance() == 6.0F, "Rising Cut excess stacked the Double Jump allowance");
        }
        data.combat().startRisingCutFallGrace(7.0D);
        data.combat().markRisingCutGraceAirborne();
        player.setOnGround(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 7.0F;
        AdvancedSwordSkills.tick(player, data);
        helper.assertTrue(data.combat().risingCutFallGrace() == 7.0D,
                "A ground flag preceding fall settlement cleared the Rising Cut waiver");
        var holder = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, ZSSContentIds.LEAPING_BLOW));
        helper.assertTrue(holder.is(DamageTypeTags.BYPASSES_ARMOR) && holder.is(DamageTypeTags.BYPASSES_COOLDOWN),
                "Ground Slam damage tags are missing");
        data.combat().reset();
        data.combat().armGroundSlam();
        data.combat().setTarget(target.getId());
        player.setOnGround(false);
        player.fallDistance = 1.0F;
        float beforeBasic = target.getHealth();
        helper.assertTrue(BasicSwordSkill.attack(player, data)
                        && Math.abs(beforeBasic - target.getHealth() - strike) < 0.001F
                        && data.combat().comboCount() == 1,
                "The locked basic-attack route duplicated Ground Slam damage or combo hits");
        data.combat().clearGroundSlam();
        data.combat().armGroundSlam();
        target.setInvulnerable(true);
        float beforeRefusal = target.getHealth();
        AttackEntityEvent refused = new AttackEntityEvent(player, target);
        ZSSCombatEvents.jumpAttack(refused);
        helper.assertTrue(refused.isCanceled() && target.getHealth() == beforeRefusal
                        && !data.combat().groundSlamFallImmune() && data.combat().comboCount() == 1,
                "A refused skill hit fell through to ordinary damage or granted a successful-hit effect");
        target.discard();
        bystander.discard();
        helper.succeed();
    }
}
