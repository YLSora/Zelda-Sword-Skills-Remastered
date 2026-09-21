package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.Arrow;
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
import zeldaswordskills_remastered.combat.BasicSwordSkill;
import zeldaswordskills_remastered.combat.FlashAssault;
import zeldaswordskills_remastered.network.FlashAssaultStateMessage;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FlashAssaultGameTests {
    private FlashAssaultGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void dodgeConfirmsOnlyWithinTwelveTicksWithoutExtendingImmunity(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_AXE));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        data.combat().setTarget(target.getId());
        long now = helper.getLevel().getGameTime();
        for (int delay = 0; delay <= 24; delay++) {
            data.combat().flashAssault().reset();
            data.combat().startDodge(now - delay, 1);
            data.combat().flashAssault().startDodge(now - delay, target.getId());
            for (var source : new net.minecraft.world.damagesource.DamageSource[] {
                    player.damageSources().inFire(), player.damageSources().fall(),
                    player.damageSources().indirectMagic(target, target), player.damageSources().mobAttack(target)}) {
                helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, source) == (delay < 20),
                        "Dodge immunity must stop at twenty ticks for every damage source");
            }
            var state = data.combat().flashAssault();
            helper.assertTrue(state.ready(now) == (delay < 12)
                            && !state.confirmDodge(target.getId(), now) && !state.immune(now),
                    "Dodge success was confirmed late, repeated, or granted extra immunity");
        }
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void lockedAxesAndOtherItemsCanAttackAndCombo(GameTestHelper helper) {
        for (var item : new net.minecraft.world.item.Item[] {Items.IRON_AXE, Items.STICK, Items.IRON_SWORD}) {
            FakePlayer player = player(helper, new ItemStack(item));
            ZSSPlayerData data = data(player);
            IronGolem target = target(helper, player.position().add(2, 0, 0));
            data.combat().setTarget(target.getId());
            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(BasicSwordSkill.attack(player, data) && data.combat().comboCount() == 1
                            && Math.abs(target.getHealth() - (1000.0F - damage)) < 0.01F,
                    "Locked basic attack rejected the held item or lost its combo hit");
            arm(player, target);
            target.invulnerableTime = 0;
            helper.assertTrue(BasicSwordSkill.attack(player, data)
                            && data.combat().flashAssault().dashing(helper.getLevel().getGameTime()),
                    "Ordinary attack was blocked during Flash Assault or stopped its approach");
            helper.assertTrue(!data.combat().flashAssault().immune(helper.getLevel().getGameTime() + 60),
                    "Unprocessed Flash Assault action flags granted unbounded immunity");
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void ordinaryAttacksPreserveQueuedAssaultAndBurst(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        arm(player, target);
        long now = helper.getLevel().getGameTime();
        var state = data.combat().flashAssault();
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        helper.assertTrue(target.getHealth() == 1000.0F && state.dashing(now),
                "Buffered attack began its burst before arrival");
        helper.assertTrue(BasicSwordSkill.attack(player, data) && data.combat().comboCount() == 1,
                "Ordinary attack could not land while the burst was queued");
        var attack = new net.minecraftforge.event.entity.player.AttackEntityEvent(player, target);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(attack);
        helper.assertTrue(!attack.isCanceled(), "Flash Assault canceled a vanilla attack event");
        helper.assertTrue(state.dashing(now) && state.immune(now + 39) && !state.immune(now + 40)
                        && data.combat().targetId() == target.getId(),
                "Ordinary attack changed approach, immunity or lock-on state");

        double gap = (target.getBbWidth() + player.getBbWidth()) * 0.5D + 0.1D;
        player.setPos(target.getBoundingBox().getCenter()
                .subtract(Vec3.directionFromRotation(0.0F, target.getYRot()).scale(gap)));
        FlashAssault.tick(player, data);
        helper.assertTrue(state.busy() && !state.dashing(now) && data.combat().comboCount() == 2,
                "Arrival lost the queued burst or applied more than its first hit");
        float health = target.getHealth();
        float ordinaryDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + data.combat().nextDamageBonus();
        helper.assertTrue(target.invulnerableTime > 10 && BasicSwordSkill.attack(player, data)
                        && Math.abs(health - target.getHealth() - ordinaryDamage) < 0.01F
                        && data.combat().comboCount() == 3,
                "Burst damage blocked or reduced an ordinary attack between hits");
        helper.assertTrue(!BasicSwordSkill.hurtLockedTarget(player, data, target,
                        player.damageSources().playerAttack(player), 1.0F),
                "Ordinary attacks bypassed another ordinary attack's damage cooldown");
        health = target.getHealth();
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        helper.assertTrue(target.getHealth() == health && state.busy(),
                "An extra attack request restarted or interrupted the burst");
        for (int tick = 1; tick <= 10; tick++) {
            helper.runAfterDelay(tick, () -> FlashAssault.tick(player, data));
        }
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(!state.busy() && data.combat().comboCount() == 8
                            && state.immune(now + 59) && !state.immune(now + 60),
                    "Ordinary attacks changed burst hit count or completion immunity");
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void ordinaryKillingBlowRetainsSuccessfulBurstImmunity(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        arm(player, target);
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        double gap = (target.getBbWidth() + player.getBbWidth()) * 0.5D + 0.1D;
        player.setPos(target.getBoundingBox().getCenter()
                .subtract(Vec3.directionFromRotation(0.0F, target.getYRot()).scale(gap)));
        FlashAssault.tick(player, data);
        target.setHealth(1.0F);
        helper.assertTrue(BasicSwordSkill.attack(player, data) && !target.isAlive(),
                "Ordinary finishing blow did not land between burst hits");
        zeldaswordskills_remastered.combat.TargetingService.tick(player, data);
        FlashAssault.tick(player, data);
        long now = helper.getLevel().getGameTime();
        var state = data.combat().flashAssault();
        helper.assertTrue(!state.busy() && state.immune(now + 49) && !state.immune(now + 50),
                "Ordinary finishing blow removed or extended successful assault immunity");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void onlyLockedMeleeQualifiesAndWindowsExpire(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        IronGolem other = target(helper, player.position().add(-2, 0, 0));
        long now = helper.getLevel().getGameTime();
        data.combat().setTarget(target.getId());
        data.combat().startDodge(now, 1);
        var state = data.combat().flashAssault();
        state.startDodge(now, target.getId());
        Arrow arrow = new Arrow(helper.getLevel(), target);
        for (var source : new net.minecraft.world.damagesource.DamageSource[] {
                player.damageSources().arrow(arrow, target), player.damageSources().indirectMagic(target, target),
                player.damageSources().thorns(target), player.damageSources().sonicBoom(target),
                player.damageSources().inFire(), player.damageSources().fall(), player.damageSources().mobAttack(other)}) {
            helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, source) && !state.ready(now),
                    "Non-melee/unlocked damage must remain immune without granting Flash Assault");
        }
        helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, player.damageSources().mobAttack(target))
                && state.ready(now) && state.ready(now + 59) && !state.ready(now + 60), "Melee success window differs from sixty ticks");
        helper.assertTrue(!state.confirmDodge(target.getId(), now + 10) && !state.ready(now + 60),
                "Repeated hits during one dodge refreshed the success window");
        helper.assertTrue(!state.immune(now)
                        && data.combat().dodgeActive(now + 19) && !data.combat().dodgeActive(now + 20),
                "Confirmed dodge must retain exactly twenty ticks of immunity");
        helper.assertTrue(!state.acceptForwardTap(now + 54) && !state.acceptForwardTap(now + 60),
                "The second tap at the sixty-tick deadline started an expired approach");
        data.combat().clearTarget();
        data.combat().setTarget(target.getId());
        helper.assertTrue(!state.ready(now) && !state.confirmDodge(target.getId(), now),
                "Clearing and reacquiring the target restored a discarded dodge success");
        data.combat().reset();
        helper.assertTrue(!state.ready(now) && !state.immune(now) && !state.busy(), "Reset retained Flash Assault state");
        target.discard();
        other.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void approachAndFollowUpHaveIndependentLocks(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_AXE));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        arm(player, target);
        long now = helper.getLevel().getGameTime();
        var state = data.combat().flashAssault();
        helper.assertTrue(state.busy() && state.followUp(now + 19) && !state.followUp(now + 20),
                "Approach failed or twenty-tick follow-up boundary is incorrect");
        player.getInventory().selected = 1;
        FlashAssault.enforceSlot(player, state);
        helper.assertTrue(player.getInventory().selected == 0, "Server failed to restore the locked hotbar slot");
        arrive(player, target);
        helper.assertTrue(!state.busy() && state.followUp(now) && state.immune(now + 19) && !state.immune(now + 20),
                "Arrival must release the slot but retain the follow-up and twenty ticks of immunity");
        player.getInventory().selected = 1;
        FlashAssault.enforceSlot(player, state);
        helper.assertTrue(player.getInventory().selected == 1, "Immunity alone kept the hotbar locked");
        helper.runAfterDelay(20, () -> {
            float health = target.getHealth();
            FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
            helper.assertTrue(target.getHealth() == health && !state.busy(), "Attack at the exact deadline was accepted");
            data.combat().clearTarget();
            FlashAssault.tick(player, data);
            helper.assertTrue(!state.followUp(helper.getLevel().getGameTime()) && state.immune(now + 19)
                            && !state.immune(now + 20),
                    "Unlock retained follow-up or shortened immunity");
            data.combat().reset();
            helper.assertTrue(!state.immune(now), "Lifecycle reset retained immunity");
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void weaponBurstsDamageTimingComboAndSingleConsumption(GameTestHelper helper) {
        // Include both axe tiers and a six-hit weapon to exercise float boundaries and all sounds.
        ItemStack[] weapons = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE), new ItemStack(Items.STICK),
                new ItemStack(ZSSRegistries.MASTER_SWORD.get()), new ItemStack(Items.DIAMOND_AXE)};
        int[] totals = {6, 2, 6, 6, 4};
        int[] intervals = {2, 10, 2, 2, 4};
        double[] attackScales = {0.65D, 1.5D, 0.65D, 0.65D, 0.8D};
        double[] levelBonuses = {0.8D, 2.5D, 0.8D, 0.8D, 1.2D};
        double[] knockbackPerLevel = {0.5D, 1.5D, 1.0D, 0.5D, 1.5D};
        helper.assertTrue(FlashAssault.hitCount(0.9D) == 2 && FlashAssault.hitCount(0.9001D) == 4
                        && FlashAssault.hitCount(1.0D) == 4 && FlashAssault.hitCount(1.5D) == 4
                        && FlashAssault.hitCount(1.5001D) == 6 && FlashAssault.hitCount(4.0D) == 6,
                "Flash Assault hit counts do not follow the published attack-speed thresholds");
        for (int index = 0; index < weapons.length; index++) {
            for (int level : new int[] {1, 5}) {
                FakePlayer player = player(helper, weapons[index]);
                ZSSPlayerData data = data(player);
                data.setSkillLevel(ZSSContentIds.FLASH_ASSAULT, level);
                IronGolem target = target(helper, player.position().add(2, 0, 0));
                arm(player, target);
                arrive(player, target);
                float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * attackScales[index]
                        + levelBonuses[index] * level);
                helper.assertTrue(Math.abs(FlashAssault.knockbackDistance(weapons[index], level)
                        - (3.0D + knockbackPerLevel[index] * level)) < 0.0001D,
                        "Weapon-specific final knockback distance differs from the specified formula");
                int total = totals[index], interval = intervals[index];
                FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
                FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
                helper.assertTrue(Math.abs(target.getHealth() - (1000.0F - damage)) < 0.01F && data.combat().comboCount() == 1,
                        "First hit must be instant, once only, with the specified weapon multiplier");
                for (int tick = 1; tick <= 13; tick++) {
                    int elapsed = tick;
                    helper.runAfterDelay(tick, () -> {
                        FlashAssault.tick(player, data);
                        int expected = Math.min(total, 1 + elapsed / interval);
                        helper.assertTrue(Math.abs(target.getHealth() - (1000.0F - damage * expected)) < 0.01F
                                        && data.combat().comboCount() == expected,
                                "Multi-hit timing/damage/combo differs at tick " + elapsed);
                        helper.assertTrue(data.combat().flashAssault().busy() == (elapsed < (total - 1) * interval),
                                "Hotbar/action lock does not end with the last hit");
                        helper.assertTrue(data.combat().flashAssault().immune(helper.getLevel().getGameTime()),
                                "Burst immunity must cover every hit and the successful-hit tail");
                        helper.assertTrue(player.getFoodData().getFoodLevel() == 20, "Flash Assault consumed hunger");
                        if (elapsed == 13) target.discard();
                    });
                }
            }
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var message = new FlashAssaultStateMessage(17, 0, 120, 8, true, false, 120, 160,
                    java.util.Optional.of(new Vec3(5.5D, -0.08D, 0.25D)));
            FlashAssaultStateMessage.encode(message, buffer);
            helper.assertTrue(message.equals(FlashAssaultStateMessage.decode(buffer)), "Flash Assault state packet did not round-trip");
        } finally { buffer.release(); }
        helper.runAfterDelay(14, helper::succeed);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void completedAssaultStopsBlockingDamageAtItsDeadline(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        arm(player, target);
        arrive(player, target);
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        for (int tick = 1; tick <= 10; tick++) {
            helper.runAfterDelay(tick, () -> FlashAssault.tick(player, data));
        }
        helper.runAfterDelay(13, () -> {
            helper.assertTrue(!data.combat().flashAssault().busy(), "Completed assault left an active action behind");
            data.combat().clearTarget();
        });
        for (int tick : new int[] {5, 11, 12, 13, 20, 29, 30, 39, 40, 49, 50, 59, 60}) {
            boolean immune = tick < 60;
            helper.runAfterDelay(tick, () -> {
                // Process late/repeated attack requests as well as normal ticks: neither may renew immunity.
                FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
                FlashAssault.tick(player, data);
                for (var source : new net.minecraft.world.damagesource.DamageSource[] {
                        player.damageSources().mobAttack(target), player.damageSources().generic(),
                        player.damageSources().inFire(), player.damageSources().magic()}) {
                    var event = new net.minecraftforge.event.entity.living.LivingAttackEvent(player, source, 1.0F);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
                    helper.assertTrue(event.isCanceled() == immune,
                            "Actual attack event immunity differs at tick " + tick + ": " + source);
                }
            });
        }
        helper.runAfterDelay(61, () -> {
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void interruptionDistinguishesQueuedAttackFromStartedBurst(GameTestHelper helper) {
        for (boolean arriveFirst : new boolean[] {false, true}) {
            FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
            ZSSPlayerData data = data(player);
            IronGolem target = target(helper, player.position().add(2, 0, 0));
            arm(player, target);
            if (arriveFirst) arrive(player, target);
            FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(data.combat().flashAssault().immune(now), "Burst did not protect its first hit");
            data.combat().clearTarget();
            FlashAssault.tick(player, data);
            FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
            var state = data.combat().flashAssault();
            helper.assertTrue(!state.busy() && !player.noPhysics && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                    "Interruption retained motion, collision bypass or action locks");
            helper.assertTrue(state.immune(now) == !arriveFirst && state.immune(now + 19) == !arriveFirst
                            && !state.immune(now + 20),
                    "A canceled approach must keep twenty ticks; an interrupted burst must clear immunity");
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void rearApproachTracksMovingTargetWithoutRestartingCooldown(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        Vec3 start = player.position();
        IronGolem target = target(helper, start.add(2, 0, 0));
        target.setYRot(90.0F);
        arm(player, target);
        long now = helper.getLevel().getGameTime();
        Vec3 impulse = player.getDeltaMovement();
        helper.assertTrue(impulse.x > 0 && Math.abs(impulse.z) < 0.001D, "Approach did not launch toward the rear");
        helper.assertTrue(data.combat().flashAssault().coolingDown(now + 59)
                        && !data.combat().flashAssault().coolingDown(now + 60), "Cooldown must last sixty ticks from launch");
        target.setYRot(-90.0F);
        target.setPos(target.position().add(0, 0, 1));
        FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
        FlashAssault.tick(player, data);
        Vec3 destination = rearPosition(player, target);
        Vec3 remaining = destination.subtract(start).multiply(1, 0, 1);
        Vec3 expectedMotion = remaining.scale(1.0D / 20.0D);
        helper.assertTrue(player.position().equals(start) && player.noPhysics
                        && player.getDeltaMovement().distanceToSqr(expectedMotion) < 1.0E-10D,
                "Approach did not steer toward the moving target's current rear position");
        helper.runAfterDelay(5, () -> {
            FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
            FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
            arrive(player, target);
            helper.assertTrue(!data.combat().flashAssault().busy() && !player.noPhysics
                            && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                    "Arrival did not release movement and collision bypass");
            data.combat().clearTarget();
            FlashAssault.tick(player, data);
            helper.assertTrue(data.combat().flashAssault().coolingDown(now + 59)
                            && !data.combat().flashAssault().coolingDown(now + 60), "Unlock changed the cooldown deadline");
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void completedApproachWindowsExpireWithoutRenewal(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        target.setYRot(90.0F);
        arm(player, target);
        long started = helper.getLevel().getGameTime();
        helper.runAfterDelay(20, () -> {
            FlashAssault.tick(player, data);
            var state = data.combat().flashAssault();
            helper.assertTrue(!state.busy() && state.immune(started + 39) && !state.immune(started + 40),
                    "Approach timeout must keep exactly twenty ticks of protection and release action locks");
            helper.assertTrue(state.followUp(started + 39) && !state.followUp(started + 40) && !player.noPhysics,
                    "Arrival must open one twenty-tick follow-up window and restore collision");
            FlashAssault.tick(player, data);
            helper.assertTrue(!state.immune(started + 40), "Repeated cleanup renewed immunity");
        });
        helper.runAfterDelay(40, () -> {
            FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
            FlashAssault.tick(player, data);
            helper.assertTrue(!data.combat().flashAssault().busy() && !data.combat().flashAssault().immune(started + 40)
                            && target.getHealth() == 1000.0F,
                    "Attack at the arrival window's deadline restarted the assault or immunity");
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void queuedKillingHitWaitsForArrivalAndKeepsFiftyTicks(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        arm(player, target);
        target.setHealth(1.0F);
        long now = helper.getLevel().getGameTime();
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        var state = data.combat().flashAssault();
        helper.assertTrue(target.isAlive() && state.dashing(now) && data.combat().comboCount() == 0,
                "Queued killing hit must wait for arrival");
        arrive(player, target);
        helper.assertTrue(!target.isAlive() && !state.busy() && data.combat().comboCount() == 1
                        && state.immune(now + 49) && !state.immune(now + 50)
                        && !player.noPhysics && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                "A killing hit must count once, stop motion and retain fifty ticks from completion");
        FlashAssault.tick(player, data);
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        helper.assertTrue(state.immune(now + 49) && !state.immune(now + 50),
                "Post-kill cleanup removed or renewed protection");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void rearApproachBypassesCollisionAndRestoresItOnCancel(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        target.setYRot(90.0F);
        arm(player, target);
        player.horizontalCollision = true;
        FlashAssault.tick(player, data);
        helper.assertTrue(data.combat().flashAssault().busy() && player.noPhysics
                        && player.getDeltaMovement().horizontalDistanceSqr() > 0,
                "Vanilla collision incorrectly interrupted the approach");
        data.combat().clearTarget();
        FlashAssault.tick(player, data);
        helper.assertTrue(!data.combat().flashAssault().busy() && !player.noPhysics
                        && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                "Canceling the approach retained movement, collision bypass or hotbar lock");
        helper.assertTrue(data.combat().flashAssault().coolingDown(helper.getLevel().getGameTime()),
                "Collision refunded the launch cooldown");
        target.discard();
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper, ItemStack weapon) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[Flash_Assault]")) {
            @Override public float getAttackStrengthScale(float partialTick) { return 1.0F; }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 20, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        player.setOnGround(true);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0D);
        data(player).setSkillLevel(ZSSContentIds.SWORD_BASIC, 10);
        data(player).setSkillLevel(ZSSContentIds.FLASH_ASSAULT, 1);
        data(player).setSkillLevel(ZSSContentIds.DODGE, 1);
        return player;
    }

    private static ZSSPlayerData data(FakePlayer player) {
        return ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
    }

    private static IronGolem target(GameTestHelper helper, Vec3 position) {
        IronGolem target = EntityType.IRON_GOLEM.create(helper.getLevel());
        target.setPos(position);
        target.setYRot(-90.0F);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        target.setHealth(1000.0F);
        helper.getLevel().addFreshEntity(target);
        return target;
    }

    private static void arm(FakePlayer player, IronGolem target) {
        ZSSPlayerData data = data(player);
        data.combat().setTarget(target.getId());
        long now = player.level().getGameTime();
        data.combat().startDodge(now, 1);
        data.combat().flashAssault().startDodge(now, target.getId());
        AdvancedSwordSkills.onAttacked(player, data, player.damageSources().mobAttack(target));
        FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
        FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
    }

    private static Vec3 rearPosition(FakePlayer player, IronGolem target) {
        double gap = (target.getBbWidth() + player.getBbWidth()) * 0.5D + 0.1D;
        return target.getBoundingBox().getCenter().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(gap));
    }

    private static void arrive(FakePlayer player, IronGolem target) {
        // FakePlayers are not ticked by the server; explicitly simulate the completed movement.
        player.setPos(rearPosition(player, target));
        FlashAssault.tick(player, data(player));
    }
}
