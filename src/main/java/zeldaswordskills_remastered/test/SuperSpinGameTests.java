package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
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
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.PlayerCombatState;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.network.SpinStateMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;

import java.util.Optional;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuperSpinGameTests {
    private SuperSpinGameTests() { }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void continuationIsOneRoundOnly(GameTestHelper helper) {
        PlayerCombatState state = new PlayerCombatState();
        state.startSpin(108, 120);
        helper.assertTrue(!state.registerSuperSpinTap(101) && state.registerSuperSpinTap(102),
                "Continuation must require two taps, including after sentinel initialization");
        for (int i = 0; i < 20; i++) state.registerSuperSpinTap(103);
        helper.assertTrue(state.spinUntil() == 108 && state.spinRounds() == 1 && state.spinContinuationQueued(),
                "Taps changed the current deadline or queued multiple rounds");
        helper.assertTrue(state.spinActive(107) && !state.spinActive(108), "A circle must last exactly eight ticks");
        state.addSpinRound(108);
        helper.assertTrue(state.spinUntil() == 116 && state.spinRounds() == 2 && !state.spinContinuationQueued()
                        && !state.registerSuperSpinTap(109), "Unused taps carried over to the next circle");
        state.finishSpin();
        helper.assertTrue(!state.spinPending() && state.spinCoolingDown(110) && !state.registerSuperSpinTap(110),
                "Finishing spin retained a queue or removed the existing cooldown");
        state.startSpin(208, 220);
        state.registerSuperSpinTap(201);
        state.registerSuperSpinTap(202);
        state.reset();
        helper.assertTrue(!state.spinPending() && !state.spinContinuationQueued(), "Reset retained spin state");

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (long until : new long[] {0L, 108L, Long.MAX_VALUE}) {
                SpinStateMessage.encode(new SpinStateMessage(until), buffer);
                helper.assertTrue(SpinStateMessage.decode(buffer).until() == until, "Spin deadline codec changed the value");
            }
            buffer.writeVarLong(-1L);
            boolean rejected = false;
            try { SpinStateMessage.decode(buffer); } catch (DecoderException expected) { rejected = true; }
            helper.assertTrue(rejected, "Negative spin deadline was accepted");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void damageAndMagicSettleEveryEightTicks(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        var target = EntityType.IRON_GOLEM.create(helper.getLevel());
        target.setPos(player.position().add(1.0D, 0.0D, 0.0D));
        target.setNoAi(true);
        target.setNoGravity(true);
        helper.getLevel().addFreshEntity(target);
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        data.combat().setTarget(target.getId());
        float health = target.getHealth();
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 1.5F;
        AdvancedSwordSkills.handleIntent(player, data, new SkillIntentMessage(
                ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.ATTACK, Optional.empty()));
        long deadline = data.combat().spinUntil();
        for (int i = 0; i < 12; i++) tap(player, data);
        helper.assertTrue(data.currentMagic() == 30.0F && Math.abs(target.getHealth() - (health - damage)) < 0.001F
                        && data.combat().spinUntil() == deadline, "Tapping spent magic, dealt damage, or extended the current circle");
        helper.runAfterDelay(7, () -> {
            AdvancedSwordSkills.tick(player, data);
            helper.assertTrue(data.currentMagic() == 30.0F && data.combat().spinRounds() == 1,
                    "Continuation settled before the eighth tick");
        });
        helper.runAfterDelay(8, () -> {
            target.invulnerableTime = 20;
            AdvancedSwordSkills.tick(player, data);
            AdvancedSwordSkills.tick(player, data);
            helper.assertTrue(data.currentMagic() == 25.0F && data.combat().spinRounds() == 2
                            && Math.abs(target.getHealth() - (health - 2 * damage)) < 0.001F
                            && data.combat().comboCount() == 2,
                    "Boundary must settle one cost and one hit, bypass hurt cooldown, and count one combo hit");
        });
        helper.runAfterDelay(16, () -> {
            AdvancedSwordSkills.tick(player, data);
            helper.assertTrue(!data.combat().spinPending() && data.currentMagic() == 25.0F
                            && Math.abs(target.getHealth() - (health - 2 * damage)) < 0.001F,
                    "Extra taps in the first circle prepaid a third circle");
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void continuationMagicCostIncreasesByTwo(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        long now = helper.getLevel().getGameTime();

        data.combat().startSpin(now + PlayerCombatState.SPIN_ROUND_TICKS, now + 20L);
        tap(player, data);
        tap(player, data);
        helper.runAfterDelay(PlayerCombatState.SPIN_ROUND_TICKS, () -> {
            AdvancedSwordSkills.tick(player, data);
            helper.assertTrue(data.currentMagic() == 25.0F && data.combat().spinRounds() == 2,
                    "The first Super Spin circle did not consume its level-scaled base cost");
            tap(player, data);
            tap(player, data);
            helper.runAfterDelay(PlayerCombatState.SPIN_ROUND_TICKS, () -> {
                AdvancedSwordSkills.tick(player, data);
                helper.assertTrue(data.currentMagic() == 18.0F && data.combat().spinRounds() == 3,
                        "The second Super Spin circle did not cost two more magic than the first");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void continuationRechecksRequirements(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        long now = helper.getLevel().getGameTime();
        for (int failure = 0; failure < 5; failure++) {
            data.setMagic(30.0F, 30.0F);
            data.setSkillLevel(ZSSContentIds.SPIN_ATTACK, 1);
            data.setSkillLevel(ZSSContentIds.SUPER_SPIN_ATTACK, 1);
            player.setHealth(player.getMaxHealth());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            data.combat().startSpin(now, now + 20);
            data.combat().registerSuperSpinTap(now - 2);
            data.combat().registerSuperSpinTap(now - 1);
            switch (failure) {
                case 0 -> player.setHealth(player.getMaxHealth() - 1.0F);
                case 1 -> data.setMagic(4.0F, 30.0F);
                case 2 -> data.setSkillLevel(ZSSContentIds.SUPER_SPIN_ATTACK, 0);
                case 3 -> data.setSkillLevel(ZSSContentIds.SPIN_ATTACK, 0);
                case 4 -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                default -> throw new AssertionError();
            }
            float magic = data.currentMagic();
            AdvancedSwordSkills.tick(player, data);
            helper.assertTrue(!data.combat().spinPending() && data.currentMagic() == magic,
                    "Invalid continuation consumed magic or retained spin: " + failure);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        data.combat().startSpin(now - 5, now - 1);
        data.combat().registerSuperSpinTap(now - 7);
        data.combat().registerSuperSpinTap(now - 6);
        AdvancedSwordSkills.tick(player, data);
        AdvancedSwordSkills.tick(player, data);
        helper.assertTrue(data.currentMagic() == 25.0F && data.combat().spinUntil() == now + 8,
                "Late processing caused a catch-up burst or shortened the next circle");
        AdvancedSwordSkills.handleIntent(player, data, new SkillIntentMessage(
                ZSSContentIds.SPIN_ATTACK, SkillIntentMessage.Action.ATTACK, Optional.empty()));
        helper.assertTrue(data.combat().spinRounds() == 2, "Expired initial cooldown allowed restarting an active chain");
        data.combat().addSpinRound(now - 8);
        data.combat().registerSuperSpinTap(now - 2);
        data.combat().registerSuperSpinTap(now - 1);
        AdvancedSwordSkills.tick(player, data);
        helper.assertTrue(!data.combat().spinPending() && data.currentMagic() == 25.0F,
                "Maximum circle count was exceeded or charged");
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[Super_Spin]"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        data.setSkillLevel(ZSSContentIds.SPIN_ATTACK, 1);
        data.setSkillLevel(ZSSContentIds.SUPER_SPIN_ATTACK, 1);
        data.setMagic(30.0F, 30.0F);
        return player;
    }

    private static void tap(FakePlayer player, ZSSPlayerData data) {
        AdvancedSwordSkills.handleIntent(player, data, new SkillIntentMessage(
                ZSSContentIds.SUPER_SPIN_ATTACK, SkillIntentMessage.Action.ATTACK, Optional.empty()));
    }
}
