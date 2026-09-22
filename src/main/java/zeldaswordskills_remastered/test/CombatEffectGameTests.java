package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.FlashAssault;
import zeldaswordskills_remastered.network.SkillIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatEffectGameTests {
    private CombatEffectGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void skillImmunityRejectsEffectsThroughForge(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "effect_immunity"));
        var enemy = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        var state = data.combat();
        long now = helper.getLevel().getGameTime();
        try {
            for (String skill : new String[]{"dodge", "dash", "rising_cut", "flash_assault"}) {
                state.reset();
                var existing = new MobEffectInstance(MobEffects.POISON, 200, 1);
                helper.assertTrue(player.addEffect(existing, enemy), "Could not apply the pre-existing effect");
                switch (skill) {
                    case "dodge" -> state.startDodge(now, 1);
                    case "dash" -> state.startDashImmunity(now + 20);
                    case "rising_cut" -> state.startRisingCutImmunity(now + 20);
                    case "flash_assault" -> {
                        data.setSkillLevel(ZSSContentIds.FLASH_ASSAULT, 1);
                        state.flashAssault().startDodge(now, enemy.getId());
                        helper.assertTrue(state.flashAssault().confirmDodge(enemy.getId(), now), "Dodge was not confirmed");
                        FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
                        FlashAssault.handle(player, data, SkillIntentMessage.Action.BEGIN);
                        helper.assertTrue(state.flashAssault().dashing(now), "Flash Assault did not begin");
                    }
                }
                assertExistingPoisonUnchanged(helper, player, existing, enemy);
                for (var effect : new net.minecraft.world.effect.MobEffect[]{MobEffects.DARKNESS, MobEffects.POISON, MobEffects.WITHER}) {
                    helper.assertTrue(!player.addEffect(new MobEffectInstance(effect, 100), enemy)
                                    && (effect == MobEffects.POISON || !player.hasEffect(effect)),
                            skill + " allowed a harmful effect during immunity");
                }
                helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100))
                                && player.hasEffect(MobEffects.REGENERATION),
                        skill + " blocked a beneficial effect");
                player.removeAllEffects();
                state.reset();
                helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100), enemy),
                        skill + " kept effect immunity after reset");
                player.removeAllEffects();
            }
            helper.assertTrue(enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100), player),
                    "Player immunity handler blocked effects on a mob");
        } finally {
            state.reset();
            enemy.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void successfulParryBlocksNewEffectsWithoutCleansing(GameTestHelper helper) {
        FakePlayer player = parryPlayer(helper);
        var enemy = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        var existing = new MobEffectInstance(MobEffects.POISON, 200, 1);
        try {
            player.addEffect(existing, enemy);
            armParry(player, enemy);
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100), enemy),
                    "An unconfirmed parry guard granted effect immunity");
            var attack = new LivingAttackEvent(player, player.damageSources().mobAttack(enemy), 1);
            MinecraftForge.EVENT_BUS.post(attack);
            helper.assertTrue(attack.isCanceled() && !data.combat().parryPending(), "Attack was not successfully parried");
            helper.assertTrue(!player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100), enemy)
                            && !player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100), enemy),
                    "The parried attack applied a new harmful effect");
            assertExistingPoisonUnchanged(helper, player, existing, enemy);
            helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Parry cleansed an existing negative effect");
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100)),
                    "Parry blocked a beneficial effect");
        } finally {
            AdvancedSwordSkills.clearParryLocks(player);
            enemy.discard();
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100), enemy),
                        "Parry effect immunity survived into a later tick");
                helper.succeed();
            } finally {
                data.combat().reset();
                player.discard();
            }
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void nextAttackCannotInheritParryEffectImmunity(GameTestHelper helper) {
        FakePlayer player = parryPlayer(helper);
        var enemy = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
        var other = helper.spawn(EntityType.ZOMBIE, 3, 1, 2);
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        try {
            armParry(player, enemy);
            var parried = new LivingAttackEvent(player, player.damageSources().mobAttack(enemy), 1);
            MinecraftForge.EVENT_BUS.post(parried);
            helper.assertTrue(parried.isCanceled() && data.combat().parriedAttackEffectsImmune(helper.getLevel().getGameTime(), enemy.getUUID()),
                    "Successful parry did not protect its effect application");
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100), other),
                    "Parry blocked another mob's effect without a damage event");
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100)),
                    "Parry blocked an effect with no attributable source");
            var next = new LivingAttackEvent(player, player.damageSources().mobAttack(other), 1);
            MinecraftForge.EVENT_BUS.post(next);
            helper.assertTrue(!next.isCanceled() && player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100), other),
                    "An unrelated attack in the same tick inherited parry immunity");
            data.combat().recordParriedAttack(helper.getLevel().getGameTime(), enemy.getUUID(), enemy.getUUID());
            data.combat().reset();
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.POISON, 100), enemy),
                    "Lifecycle reset retained parry effect immunity");
        } finally {
            AdvancedSwordSkills.clearParryLocks(player);
            enemy.discard();
            other.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void helmSplitterDoesNotBlockNewEffects(GameTestHelper helper) {
        FakePlayer player = parryPlayer(helper);
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        long now = helper.getLevel().getGameTime();
        try {
            data.combat().helmSplitter().launch(UUID.randomUUID(), 5);
            data.combat().helmSplitter().hit(now);
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.POISON, 100)),
                    "Airborne Helm Splitter still blocked negative effects");
            data.combat().helmSplitter().land(now);
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100)),
                    "Landed Helm Splitter still blocked negative effects");
        } finally {
            data.combat().reset();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void sourceLessEffectsStayWithTheirTickingAttacker(GameTestHelper helper) {
        FakePlayer player = parryPlayer(helper);
        var enemy = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
        var other = helper.spawn(EntityType.ZOMBIE, 3, 1, 2);
        try {
            armParry(player, enemy);
            helper.getLevel().guardEntityTick(attacker -> {
                var attack = new LivingAttackEvent(player, player.damageSources().mobAttack(attacker), 1);
                MinecraftForge.EVENT_BUS.post(attack);
                helper.assertTrue(attack.isCanceled(), "Attack was not parried");
                helper.assertTrue(!player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100)),
                        "Parried mob applied a source-less effect during its tick");
                helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.POISON, 100), other),
                        "Ticking entity overrode an explicitly different effect source");
                helper.getLevel().guardEntityTick(bystander -> {
                    helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100)),
                            "Another mob's source-less effect inherited the parry block");
                }, other);
                helper.assertTrue(!player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100)),
                        "Nested ticking lost the original parried attacker context");
            }, enemy);
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100)),
                    "Effect source leaked out of the entity tick");
        } finally {
            AdvancedSwordSkills.clearParryLocks(player);
            enemy.discard();
            other.discard();
            player.discard();
        }
        helper.succeed();
    }

    private static void assertExistingPoisonUnchanged(GameTestHelper helper, FakePlayer player,
                                                     MobEffectInstance existing, LivingEntity enemy) {
        helper.assertTrue(!player.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 2), enemy),
                "Immunity allowed an existing negative effect to be refreshed or strengthened");
        helper.assertTrue(player.getEffect(MobEffects.POISON) == existing
                        && existing.getDuration() == 200 && existing.getAmplifier() == 1,
                "Immunity cleared or changed the existing negative effect");
    }

    private static FakePlayer parryPlayer(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "parry_effects"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        data.setSkillLevel(ZSSContentIds.PARRY, 1);
        return player;
    }

    private static void armParry(FakePlayer player, LivingEntity enemy) {
        var state = ZSSCapabilities.get(player).orElseThrow(AssertionError::new).combat();
        state.setTarget(enemy.getId());
        long now = player.level().getGameTime();
        state.startParry(now, now + AdvancedSwordSkills.parryWindow(1));
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void effectSourceTickDoesNotCrash(GameTestHelper helper) {
        checkEffectSourceTick(helper, false);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void effectSourceIsNotRemovedByForge(GameTestHelper helper) {
        checkEffectSourceTick(helper, true);
    }

    private static void checkEffectSourceTick(GameTestHelper helper, boolean removeErroringEntities) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "effect_source"));
        var data = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        LivingEntity enemy = helper.spawn(EntityType.WARDEN, 2, 1, 2);
        boolean previous = ForgeConfig.SERVER.removeErroringEntities.get();
        try {
            ForgeConfig.SERVER.removeErroringEntities.set(removeErroringEntities);
            data.combat().startDodge(helper.getLevel().getGameTime(), 1);
            // Exercise Forge's real tick exception boundary with the effect cast by this enemy.
            helper.getLevel().guardEntityTick(source ->
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100), source), enemy);
            helper.assertTrue(enemy.isAlive() && helper.getLevel().getEntity(enemy.getUUID()) == enemy,
                    "Applying an effect to an immune player removed its source entity");
            helper.assertTrue(!player.hasEffect(MobEffects.DARKNESS), "Darkness bypassed skill immunity");
        } finally {
            ForgeConfig.SERVER.removeErroringEntities.set(previous);
            data.combat().reset();
            enemy.discard();
            player.discard();
        }
        helper.succeed();
    }
}
