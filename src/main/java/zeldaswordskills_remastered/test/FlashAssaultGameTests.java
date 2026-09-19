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
import zeldaswordskills_remastered.combat.FlashAssaultMovement;
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
    public static void earlyDodgeGraceConfirmsOnceAndExtendsImmunity(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_AXE));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        data.combat().setTarget(target.getId());
        long now = helper.getLevel().getGameTime();
        for (int delay = 4; delay <= 10; delay++) {
            data.combat().flashAssault().reset();
            data.combat().startDodge(now - delay);
            data.combat().flashAssault().startDodge(now - delay, target.getId());
            helper.assertTrue(!AdvancedSwordSkills.onAttacked(player, data, player.damageSources().inFire())
                            && !AdvancedSwordSkills.onAttacked(player, data, player.damageSources().fall())
                            && !AdvancedSwordSkills.onAttacked(player, data, player.damageSources().indirectMagic(target, target)),
                    "Grace accidentally extended immunity to non-melee damage");
            helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, player.damageSources().mobAttack(target)) == (delay < 10),
                    "Early dodge confirmation must accept delays 4 through 9 but reject 10");
            helper.assertTrue(AdvancedSwordSkills.onAttacked(player, data, player.damageSources().mobAttack(target)) == (delay < 10),
                    "Confirmed dodge must protect subsequent attacks until the extended deadline");
            var state = data.combat().flashAssault();
            helper.assertTrue(!state.confirmDodge(target.getId(), now)
                            && state.immune(now + 13 - delay) == (delay < 10)
                            && !state.immune(now + 14 - delay),
                    "Dodge extension must end fourteen ticks after dodge start, without repeat confirmation");
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
            helper.assertTrue(!BasicSwordSkill.attack(player, data), "Basic attack bypassed Flash Assault's action lock");
            helper.assertTrue(!data.combat().flashAssault().immune(helper.getLevel().getGameTime() + 60),
                    "Unprocessed Flash Assault action flags granted unbounded immunity");
            target.discard();
        }
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
        data.combat().startDodge(now);
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
        helper.assertTrue(state.immune(now + 13) && !state.immune(now + 14)
                        && data.combat().dodgeActive(now + 3) && !data.combat().dodgeActive(now + 4),
                "Confirmed dodge must extend immunity by ten ticks without extending the dodge action");
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
        FlashAssault.tick(player, data);
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
        // Hit count now follows the held weapon's attack speed, not its class. Vanilla speeds are
        // 4.0 + the MAINHAND modifier: iron sword 1.6 -> 4 hits, iron axe 0.9 -> 2 hits, a bare
        // stick with no modifier (base 4.0) -> 5 hits, and the ZSS master sword 1.6 -> 4 hits.
        ItemStack[] weapons = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE), new ItemStack(Items.STICK),
                new ItemStack(ZSSRegistries.MASTER_SWORD.get())};
        int[] totals = {4, 2, 5, 4};
        int[] intervals = {4, 10, 3, 4};
        double[] attackScales = {0.5D, 0.75D, 1.0D, 0.5D};
        double[] levelBonuses = {0.1D, 0.15D, 0.2D, 0.1D};
        double[] knockbackPerLevel = {0.5D, 1.5D, 1.0D, 0.5D};
        // The published thresholds, checked directly so a wrong boundary cannot hide behind the
        // weapon table above: <=1.0 -> 2, then >1.5 -> 3, >2.0 -> 4, and anything faster -> 5.
        helper.assertTrue(FlashAssault.hitCount(0.9D) == 2 && FlashAssault.hitCount(1.0D) == 2
                        && FlashAssault.hitCount(1.1D) == 3 && FlashAssault.hitCount(1.5D) == 3
                        && FlashAssault.hitCount(1.6D) == 4 && FlashAssault.hitCount(2.0D) == 4
                        && FlashAssault.hitCount(2.1D) == 5 && FlashAssault.hitCount(4.0D) == 5,
                "Flash Assault hit counts do not follow the published attack-speed thresholds");
        for (int index = 0; index < weapons.length; index++) {
            for (int level : new int[] {1, 5}) {
                FakePlayer player = player(helper, weapons[index]);
                ZSSPlayerData data = data(player);
                data.setSkillLevel(ZSSContentIds.FLASH_ASSAULT, level);
                IronGolem target = target(helper, player.position().add(2, 0, 0));
                arm(player, target);
                FlashAssault.tick(player, data);
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
                                "Burst immunity must cover every hit and the twenty-tick tail");
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
        FlashAssault.tick(player, data);
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        for (int tick = 1; tick <= 12; tick++) {
            helper.runAfterDelay(tick, () -> FlashAssault.tick(player, data));
        }
        helper.runAfterDelay(13, () -> {
            helper.assertTrue(!data.combat().flashAssault().busy(), "Completed assault left an active action behind");
            data.combat().clearTarget();
        });
        for (int tick : new int[] {5, 11, 12, 13, 20, 31, 32, 39, 40, 60}) {
            boolean immune = tick < 32;
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
    public static void interruptedBurstClearsImmunityEvenDuringApproach(GameTestHelper helper) {
        for (boolean arriveFirst : new boolean[] {false, true}) {
            FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
            ZSSPlayerData data = data(player);
            IronGolem target = target(helper, player.position().add(2, 0, 0));
            arm(player, target);
            if (arriveFirst) FlashAssault.tick(player, data);
            FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(data.combat().flashAssault().immune(now), "Burst did not protect its first hit");
            data.combat().clearTarget();
            FlashAssault.tick(player, data);
            FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
            helper.assertTrue(!data.combat().flashAssault().busy() && !data.combat().flashAssault().immune(now),
                    "Interrupted burst retained immunity or renewed the approach protection");
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void rearApproachUsesOneSnapshotAndCooldown(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        Vec3 start = player.position();
        IronGolem target = target(helper, start.add(2, 0, 0));
        target.setYRot(90.0F);
        Vec3 destination = FlashAssaultMovement.rearPosition(target.position(), target.getYRot());
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
        helper.assertTrue(player.position().equals(start) && player.getDeltaMovement().equals(impulse),
                "Target movement or repeated input redirected, teleported or accelerated the approach");
        player.setPos(destination);
        FlashAssault.tick(player, data);
        helper.assertTrue(!data.combat().flashAssault().busy() && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                "Approach did not stop at its original rear destination");
        data.combat().clearTarget();
        FlashAssault.tick(player, data);
        helper.assertTrue(data.combat().flashAssault().coolingDown(now + 59)
                        && !data.combat().flashAssault().coolingDown(now + 60), "Unlock changed the cooldown deadline");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void timedOutApproachRetainsExactlyTwentyTicks(GameTestHelper helper) {
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
            FlashAssault.tick(player, data);
            FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
            helper.assertTrue(!state.immune(started + 40), "Repeated cleanup renewed immunity");
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void killingFirstHitKeepsTwentyTicksAndStopsApproach(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        arm(player, target);
        target.setHealth(1.0F);
        long now = helper.getLevel().getGameTime();
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        var state = data.combat().flashAssault();
        helper.assertTrue(!target.isAlive() && !state.busy() && data.combat().comboCount() == 1
                        && state.immune(now + 19) && !state.immune(now + 20)
                        && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                "An early kill must count once, stop all motion and retain twenty ticks from completion");
        FlashAssault.tick(player, data);
        FlashAssault.handle(player, data, SkillIntentMessage.Action.ATTACK);
        helper.assertTrue(state.immune(now + 19) && !state.immune(now + 20),
                "Post-kill cleanup removed or renewed protection");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void rearApproachStopsWhenBlocked(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = data(player);
        IronGolem target = target(helper, player.position().add(2, 0, 0));
        target.setYRot(90.0F);
        arm(player, target);
        player.horizontalCollision = true;
        FlashAssault.tick(player, data);
        helper.assertTrue(!data.combat().flashAssault().busy() && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                "A blocked approach retained movement or hotbar lock");
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
        data.combat().startDodge(now);
        data.combat().flashAssault().startDodge(now, target.getId());
        AdvancedSwordSkills.onAttacked(player, data, player.damageSources().mobAttack(target));
        FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
        FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
    }
}
