package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.IaiSlash;
import zeldaswordskills_remastered.combat.SkillAvailability;
import zeldaswordskills_remastered.combat.TargetingService;
import zeldaswordskills_remastered.event.ZSSCombatEvents;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.TargetIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;

import java.util.Set;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IaiSlashGameTests {
    private IaiSlashGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void drawReplacesOneHitIgnoresArmorAndStartsCombo(GameTestHelper helper) {
        for (int level : new int[] {1, 5}) {
            FakePlayer player = player(helper);
            ZSSPlayerData data = data(player);
            data.setSkillLevel(ZSSContentIds.MORTAL_DRAW, level);
            IronGolem target = target(helper, player.position().add(0, 0, 2));
            arm(player, target);
            helper.assertTrue(data.combat().targetId() == -1 && data.combat().iaiSlash().targetId() == target.getId(),
                    "Preparing forced ordinary lock-on or failed to select the observed enemy");
            ItemStack weapon = new ItemStack(level == 1 ? Items.IRON_SWORD : Items.IRON_AXE);
            player.getInventory().setItem(1, weapon);
            player.getInventory().selected = 1;
            float expected = (float) (player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
                    + weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)
                    .stream().mapToDouble(modifier -> modifier.getAmount()).sum()) + 2.5F * level;
            int food = player.getFoodData().getFoodLevel();
            float exhaustion = player.getFoodData().getExhaustionLevel();
            AttackEntityEvent attack = new AttackEntityEvent(player, target);
            ZSSCombatEvents.jumpAttack(attack);
            helper.assertTrue(attack.isCanceled() && Math.abs(target.getHealth() - (1000 - expected)) < 0.01F,
                    "Same-tick selection used stale damage, armor reduced the hit, or ordinary attack was not canceled");
            helper.assertTrue(!data.combat().iaiSlash().active() && data.combat().targetId() == target.getId()
                            && data.combat().comboCount() == 1 && Math.abs(data.combat().comboDamage() - expected) < 0.01F,
                    "Successful Iai Slash did not convert the marker and record exactly one combo hit");
            helper.assertTrue(player.getFoodData().getFoodLevel() == food && player.getFoodData().getExhaustionLevel() == exhaustion,
                    "Iai Slash consumed hunger");
            helper.assertTrue(!IaiSlash.tryStrike(player, data, target), "Completed draw dealt another special hit");
            helper.assertTrue(data.combat().iaiSlash().coolingDown(player.level().getGameTime())
                            && !data.combat().iaiSlash().coolingDown(player.level().getGameTime() + 200),
                    "Successful Iai Slash did not start exactly the cancellation cooldown");
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void observationRequiresConeRangeAndUnobstructedSight(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(0, 0, 4));
        long now = helper.getLevel().getGameTime();
        data.combat().iaiSlash().observe(Set.of(target.getId()), now - 39);
        IaiSlash.tick(player, data);
        helper.assertTrue(!data.combat().iaiSlash().active(), "Observation armed before 40 ticks");
        target.setPos(player.position().add(4, 0, 0));
        helper.assertTrue(!IaiSlash.visibleCandidate(player, target), "Target outside the 120-degree cone qualified");
        IaiSlash.tick(player, data);
        target.setPos(player.position().add(0, 0, 5.01));
        helper.assertTrue(!IaiSlash.visibleCandidate(player, target), "Target beyond five blocks qualified");
        target.setPos(player.position().add(0, 0, 4));
        IaiSlash.tick(player, data);
        helper.assertTrue(!data.combat().iaiSlash().observed(target.getId(), now + 39), "Leaving the cone retained observation time");
        BlockPos wall = BlockPos.containing(player.position().add(0, 1, 2));
        helper.getLevel().setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        helper.assertTrue(!IaiSlash.visibleCandidate(player, target), "A wall did not interrupt observation");
        helper.getLevel().removeBlock(wall, false);
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void wrongTargetDamageTimeoutAndTargetDeath(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(0, 0, 2));
        IronGolem other = target(helper, player.position().add(1, 0, 2));
        arm(player, target);
        helper.assertTrue(!AdvancedSwordSkills.onAttacked(player, data, player.damageSources().mobAttack(target)),
                "Preparation still grants the old automatic counter or immunity");
        player.getInventory().setItem(1, new ItemStack(Items.IRON_SWORD));
        player.getInventory().selected = 1;
        helper.assertTrue(!IaiSlash.tryStrike(player, data, other) && !data.combat().iaiSlash().active(),
                "A hit on a different enemy did not spend the stance");
        assertCanceled(helper, player);
        helper.assertTrue(data.combat().iaiSlash().coolingDown(helper.getLevel().getGameTime()),
                "Hitting the wrong enemy did not start the failure cooldown");
        // The failure cooldown refuses a new stance until it runs out.
        var state = data.combat().iaiSlash();
        state.observe(Set.of(target.getId()), helper.getLevel().getGameTime() - 40);
        IaiSlash.tick(player, data);
        helper.assertTrue(!data.combat().iaiSlash().active(), "Observation re-armed during the failure cooldown");
        data.combat().iaiSlash().reset();
        arm(player, target);
        long deadline = data.combat().iaiSlash().attackUntil();
        player.getInventory().selected = 0;
        IaiSlash.tick(player, data);
        helper.assertTrue(data.combat().iaiSlash().attackUntil() == deadline, "Putting away the weapon reset the window");
        ZSSCombatEvents.playerDamaged(new LivingDamageEvent(player, player.damageSources().inFire(), 0));
        helper.assertTrue(data.combat().iaiSlash().active(), "Zero damage interrupted preparation");
        ZSSCombatEvents.playerDamaged(new LivingDamageEvent(player, player.damageSources().inFire(), 1));
        helper.assertTrue(!data.combat().iaiSlash().active(), "Actual damage did not interrupt preparation");
        assertCanceled(helper, player);
        helper.assertTrue(data.combat().iaiSlash().coolingDown(helper.getLevel().getGameTime()),
                "Taking damage did not start the failure cooldown");
        data.combat().iaiSlash().reset();
        arm(player, target);
        long now = helper.getLevel().getGameTime();
        data.combat().iaiSlash().draw(1, true, now - 40);
        player.getInventory().selected = 1;
        helper.assertTrue(!IaiSlash.tryStrike(player, data, target) && !data.combat().iaiSlash().active(),
                "Expired window accepted a hit");
        assertCanceled(helper, player);
        data.combat().iaiSlash().reset();
        player.getInventory().selected = 0;
        arm(player, target);
        target.discard();
        IaiSlash.tick(player, data);
        helper.assertTrue(!data.combat().iaiSlash().active(), "A removed enemy left a stale marker");
        assertCanceled(helper, player);
        other.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void emptySwingCancellationAndEmptyHandedLockEndPreparation(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(0, 0, 2));
        arm(player, target);
        player.getInventory().setItem(1, new ItemStack(Items.IRON_SWORD));
        player.getInventory().selected = 1;
        AdvancedSwordSkills.handleIntent(player, data, new SkillIntentMessage(
                ZSSContentIds.MORTAL_DRAW, SkillIntentMessage.Action.CANCEL, Optional.empty()));
        helper.assertTrue(!data.combat().iaiSlash().active() && data.combat().targetId() < 0
                        && target.getHealth() == 1000 && data.combat().comboCount() == 0,
                "Empty-swing cancellation did not clear preparation, or caused a hit/lock");
        helper.assertTrue(!IaiSlash.tryStrike(player, data, target), "Attack after cancellation still released Iai Slash");
        assertCanceled(helper, player);
        data.combat().iaiSlash().reset();
        player.getInventory().selected = 0;
        arm(player, target);
        TargetingService.handle(player, data, TargetIntentMessage.Action.ACQUIRE);
        helper.assertTrue(data.combat().targetId() == target.getId() && !data.combat().iaiSlash().active(),
                "Manual empty-handed lock during preparation failed or retained the special marker");
        assertCanceled(helper, player);
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void distanceAndTotalStanceDeadlineCancelBeforeTickOrAttack(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(0, 0, 2));
        var state = data.combat().iaiSlash();
        for (boolean attack : new boolean[] {false, true}) {
            state.reset();
            target.setPos(player.position().add(0, 0, 2));
            arm(player, target);
            target.setPos(player.position().add(0, 0, 5));
            IaiSlash.tick(player, data);
            helper.assertTrue(state.active(), "Exactly five blocks canceled preparation");
            target.setPos(player.position().add(0, 0, 5.01));
            if (attack) {
                player.getInventory().setItem(1, new ItemStack(Items.IRON_SWORD));
                player.getInventory().selected = 1;
                helper.assertTrue(!IaiSlash.tryStrike(player, data, target), "Out-of-range stance released a strike");
            } else IaiSlash.tick(player, data);
            assertCanceled(helper, player);

            state.reset();
            target.setPos(player.position().add(0, 0, 2));
            arm(player, target);
            long now = player.level().getGameTime();
            state.arm(target.getId(), 0, now - 99);
            IaiSlash.tick(player, data);
            helper.assertTrue(state.active(), "Stance expired before 100 ticks");
            state.arm(target.getId(), 0, now - 100);
            if (attack) {
                player.getInventory().selected = 1;
                helper.assertTrue(!IaiSlash.tryStrike(player, data, target), "Expired stance released a strike");
            } else IaiSlash.tick(player, data);
            assertCanceled(helper, player);
            helper.assertTrue(target.getHealth() == 1000, "Canceled preparation caused damage");
        }
        state.reset();
        arm(player, target);
        SkillAvailability.setEnabled(player, data, ZSSContentIds.MORTAL_DRAW, false);
        assertCanceled(helper, player);
        SkillAvailability.setEnabled(player, data, ZSSContentIds.MORTAL_DRAW, true);
        state.observe(Set.of(target.getId()), player.level().getGameTime() - 40);
        IaiSlash.tick(player, data);
        assertCanceled(helper, player);
        target.discard();
        helper.succeed();
    }

    private static void assertCanceled(GameTestHelper helper, FakePlayer player) {
        var state = data(player).combat().iaiSlash();
        long now = player.level().getGameTime();
        helper.assertTrue(!state.active() && state.coolingDown(now + 199) && !state.coolingDown(now + 200),
                "Canceled preparation must clear its mark and start exactly 200 ticks of cooldown");
        IaiSlash.tick(player, data(player));
        helper.assertTrue(!state.coolingDown(now + 200), "An inactive tick extended the cancellation cooldown");
    }

    private static void arm(FakePlayer player, IronGolem target) {
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, ItemStack.EMPTY);
        var state = data(player).combat().iaiSlash();
        state.observe(Set.of(target.getId()), player.level().getGameTime() - 40);
        IaiSlash.tick(player, data(player));
        if (!state.active() || state.targetId() != target.getId()) throw new AssertionError("Failed to prepare Iai Slash");
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "iai_test"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        player.setYRot(0);
        player.setXRot(0);
        data(player).setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        data(player).setSkillLevel(ZSSContentIds.MORTAL_DRAW, 1);
        return player;
    }

    private static ZSSPlayerData data(FakePlayer player) {
        return ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player capability"));
    }

    private static IronGolem target(GameTestHelper helper, Vec3 position) {
        IronGolem target = EntityType.IRON_GOLEM.create(helper.getLevel());
        target.setPos(position);
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(30);
        target.setHealth(1000);
        helper.getLevel().addFreshEntity(target);
        return target;
    }
}
