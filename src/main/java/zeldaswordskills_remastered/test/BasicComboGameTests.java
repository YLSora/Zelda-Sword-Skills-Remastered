package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.combat.BasicSwordSkill;
import zeldaswordskills_remastered.combat.PlayerCombatState;
import zeldaswordskills_remastered.combat.TargetingService;
import zeldaswordskills_remastered.entity.ZSSDamageSources;
import zeldaswordskills_remastered.event.ZSSCombatEvents;
import zeldaswordskills_remastered.network.TargetIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.List;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BasicComboGameTests {
    private BasicComboGameTests() { }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void sharedTargetDamageAndLocksRemainIndependent(GameTestHelper helper) {
        FakePlayer first = player(helper);
        FakePlayer second = player(helper);
        var firstData = ZSSCapabilities.get(first).orElseThrow(() -> new AssertionError("Missing player data"));
        var secondData = ZSSCapabilities.get(second).orElseThrow(() -> new AssertionError("Missing player data"));
        var target = mob(helper, EntityType.ZOMBIE, first.position().add(0, 0, 3));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(100);
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        TargetingService.handle(first, firstData, TargetIntentMessage.Action.ACQUIRE);
        TargetingService.handle(second, secondData, TargetIntentMessage.Action.ACQUIRE);
        TargetingService.handle(first, firstData, TargetIntentMessage.Action.NEXT);
        helper.assertTrue(firstData.combat().targetId() == target.getId()
                && secondData.combat().targetId() == target.getId(), "Held item or another player's lock prevented targeting");
        helper.assertTrue(BasicSwordSkill.hurtLockedTarget(first, firstData, target, first.damageSources().playerAttack(first), 5), "First hit failed");
        float health = target.getHealth();
        helper.assertTrue(BasicSwordSkill.hurtLockedTarget(second, secondData, target, second.damageSources().playerAttack(second), 5)
                && target.getHealth() == health - 5, "Shared target rejected or reduced second player's hit");
        helper.assertTrue(!BasicSwordSkill.hurtLockedTarget(second, secondData, target, second.damageSources().playerAttack(second), 5),
                "Same-player cooldown was bypassed");
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        firstData.setSkillLevel(ZSSContentIds.RISING_CUT, 1);
        firstData.combat().armRisingCut(helper.getLevel().getGameTime() + 20);
        zeldaswordskills_remastered.combat.AdvancedSwordSkills.handleIntent(first, firstData,
                new zeldaswordskills_remastered.network.SkillIntentMessage(ZSSContentIds.RISING_CUT,
                        zeldaswordskills_remastered.network.SkillIntentMessage.Action.ATTACK, java.util.Optional.empty()));
        helper.assertTrue(first.getDeltaMovement().y > 0 && firstData.combat().comboCount() == 1,
                "Another player's hit suppressed Rising Cut or its combo");
        TargetingService.handle(first, firstData, TargetIntentMessage.Action.CLEAR);
        helper.assertTrue(secondData.combat().targetId() == target.getId(), "Clearing one lock cleared another player's lock");
        helper.assertTrue(!BasicSwordSkill.hurtLockedTarget(first, firstData, target, first.damageSources().playerAttack(first), 1),
                "Unlocked attack bypassed cooldown");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void incomingDamageKeepsPhysicalCombos(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        var attacker = EntityType.ZOMBIE.create(helper.getLevel());
        var arrow = EntityType.ARROW.create(helper.getLevel());
        var fireball = EntityType.FIREBALL.create(helper.getLevel());
        var skull = EntityType.WITHER_SKULL.create(helper.getLevel());
        var fireworks = EntityType.FIREWORK_ROCKET.create(helper.getLevel());
        var fireCreature = ZSSRegistries.CHU.get().create(helper.getLevel());
        var cursedCreature = ZSSRegistries.KEESE_CURSED.get().create(helper.getLevel());
        var sources = player.damageSources();
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        var swordBreak = ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "sword_break"));
        List<DamageSource> physical = List.of(sources.mobAttack(attacker), sources.noAggroMobAttack(attacker),
                sources.playerAttack(player), sources.sting(attacker), sources.arrow(arrow, attacker),
                sources.thrown(arrow, attacker), sources.trident(arrow, attacker), sources.fall(), sources.stalagmite(),
                new DamageSource(registry.getHolderOrThrow(swordBreak), player));
        List<DamageSource> interrupting = List.of(sources.onFire(), sources.lava(), sources.magic(),
                sources.indirectMagic(arrow, attacker), sources.explosion(arrow, attacker), sources.fireball(fireball, attacker),
                sources.witherSkull(skull, attacker), sources.fireworks(fireworks, attacker), sources.thorns(attacker),
                sources.sonicBoom(attacker), sources.mobAttack(fireCreature), sources.mobAttack(cursedCreature),
                new DamageSource(registry.getHolderOrThrow(ZSSDamageSources.MAGIC), arrow, attacker), sources.cactus());
        for (int level : new int[] {1, 5, 10}) {
            data.setSkillLevel(ZSSContentIds.SWORD_BASIC, level);
            for (DamageSource source : physical) {
                seedCombo(data.combat(), 42, helper.getLevel().getGameTime() + 20);
                long deadline = data.combat().comboDeadline();
                ZSSCombatEvents.playerDamaged(new LivingDamageEvent(player, source, 100.0F));
                helper.assertTrue(!data.combat().comboFinished() && data.combat().comboCount() == 1
                                && data.combat().comboDeadline() == deadline,
                        "Physical damage interrupted or refreshed a combo: " + source);
            }
            for (DamageSource source : interrupting) {
                seedCombo(data.combat(), 42, helper.getLevel().getGameTime() + 20);
                float threshold = BasicSwordSkill.damageBreakThreshold(level);
                ZSSCombatEvents.playerDamaged(new LivingDamageEvent(player, source, 0.0F));
                ZSSCombatEvents.playerDamaged(new LivingDamageEvent(player, source, threshold));
                helper.assertTrue(!data.combat().comboFinished(), "Threshold equality interrupted a combo: " + source);
                ZSSCombatEvents.playerDamaged(new LivingDamageEvent(player, source, threshold + 0.01F));
                helper.assertTrue(data.combat().comboFinished(), "Interrupting damage was ignored: " + source);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void defeatedTargetTransfersComboToNearestHostileEntity(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        var defeated = mob(helper, EntityType.ZOMBIE, player.position().add(0, 0, 3));
        var nearer = mob(helper, ZSSRegistries.DARKNUT.get(), player.position().add(1, 0, 3));
        var centered = mob(helper, EntityType.ZOMBIE, player.position().add(0, 0, 5));
        // Friends are never lock targets now, so a cow standing closer than every hostile must be
        // skipped during the transfer. A dropped item and an armor stand sit closer still and are
        // excluded too, because neither has behaviour of its own.
        var passive = mob(helper, EntityType.COW, player.position().add(0, 0, 1));
        var droppedItem = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), player.getX() + 0.5D,
                player.getY(), player.getZ(), new ItemStack(Items.STONE));
        helper.getLevel().addFreshEntity(droppedItem);
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(helper.getLevel(), player.getX() - 0.5D,
                player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(stand);
        var behind = mob(helper, EntityType.ZOMBIE, player.position().add(0, 0, -1));
        var invisible = mob(helper, EntityType.ZOMBIE, player.position().add(0.5, 0, 1));
        invisible.setInvisible(true);
        long now = helper.getLevel().getGameTime();
        seedCombo(data.combat(), defeated.getId(), now + 20);
        data.combat().startFocus(now);
        defeated.setHealth(0.0F);
        BasicSwordSkill.recordComboHit(player, data, defeated, 5.0F);
        long deadline = data.combat().comboDeadline();
        TargetingService.tick(player, data);
        helper.assertTrue(data.combat().targetId() == nearer.getId() && data.combat().comboTargetId() == nearer.getId(),
                "Transfer did not select the nearest hostile entity, or selected a friendly one");
        helper.assertTrue(!data.combat().comboFinished() && data.combat().comboCount() == 2
                        && data.combat().comboDamage() == 9.0F && data.combat().comboDeadline() == deadline,
                "Transfer lost the killing hit, damage total, or remaining time");
        helper.assertTrue(!data.combat().focusActive(now) && data.combat().fatalStrikeCoolingDown(now),
                "Fatal Strike focus carried over to the next enemy");
        BasicSwordSkill.recordComboHit(player, data, nearer, 6.0F);
        helper.assertTrue(data.combat().comboCount() == 3 && data.combat().comboDamage() == 15.0F,
                "The first hit on the next entity restarted the combo");
        TargetingService.handle(player, data, TargetIntentMessage.Action.NEXT);
        helper.assertTrue(data.combat().comboFinished(), "Manual switching unexpectedly preserved the combo");
        droppedItem.discard();
        stand.discard();
        for (Mob mob : List.of(defeated, nearer, centered, passive, behind, invisible)) mob.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void friendlyCreaturesAreNeverLocked(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 5);
        // Only friends are nearby: a passive animal, a villager NPC and an ambient creature. None
        // may be acquired, and none may enter the combo chain.
        var cow = mob(helper, EntityType.COW, player.position().add(0, 0, 1));
        var villager = mob(helper, EntityType.VILLAGER, player.position().add(0, 0, 2));
        var bat = mob(helper, EntityType.BAT, player.position().add(1, 0, 0));
        helper.assertTrue(TargetingService.isFriendly(cow) && TargetingService.isFriendly(villager)
                        && TargetingService.isFriendly(bat),
                "Friendly detection missed a passive, NPC or ambient creature");
        TargetingService.handle(player, data, TargetIntentMessage.Action.ACQUIRE);
        helper.assertTrue(data.combat().targetId() == -1, "A friendly creature was locked onto");
        long now = helper.getLevel().getGameTime();
        seedCombo(data.combat(), cow.getId(), now + 20);
        int countBefore = data.combat().comboCount();
        float damageBefore = data.combat().comboDamage();
        BasicSwordSkill.recordComboHit(player, data, cow, 5.0F);
        helper.assertTrue(data.combat().comboCount() == countBefore && data.combat().comboDamage() == damageBefore,
                "A friendly creature advanced the combo chain");
        cow.discard();
        villager.discard();
        bat.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void lockAndComboIgnoreBehaviourlessEntities(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 5);
        // Only a dropped item and an armor stand stand nearby: neither may be locked or counted.
        var droppedItem = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), player.getX(),
                player.getY(), player.getZ() + 1.0D, new ItemStack(Items.STONE));
        helper.getLevel().addFreshEntity(droppedItem);
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(helper.getLevel(), player.getX() + 1.0D,
                player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(stand);
        long now = helper.getLevel().getGameTime();
        TargetingService.handle(player, data, TargetIntentMessage.Action.ACQUIRE);
        helper.assertTrue(data.combat().targetId() == -1,
                "A behaviourless entity was locked onto");
        // A skill hit on such an entity must not advance the combo chain even when the transient
        // lock somehow points at it: the behaviour guard is what rejects it, not the id mismatch.
        seedCombo(data.combat(), stand.getId(), now + 20);
        int countBefore = data.combat().comboCount();
        float damageBefore = data.combat().comboDamage();
        BasicSwordSkill.recordComboHit(player, data, stand, 5.0F);
        helper.assertTrue(data.combat().comboCount() == countBefore
                        && data.combat().comboDamage() == damageBefore,
                "A behaviourless entity advanced the combo chain");
        droppedItem.discard();
        stand.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void transferCannotReviveFinishedCombosOrFollowLostTargets(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        var first = mob(helper, EntityType.ZOMBIE, player.position().add(0, 0, 3));
        var next = mob(helper, EntityType.ZOMBIE, player.position().add(1, 0, 3));
        long now = helper.getLevel().getGameTime();
        seedCombo(data.combat(), first.getId(), now - 1);
        first.setHealth(0.0F);
        TargetingService.tick(player, data);
        helper.assertTrue(data.combat().targetId() == next.getId() && data.combat().comboFinished(),
                "Transferring revived an expired combo");
        seedCombo(data.combat(), first.getId(), now + 20);
        data.combat().finishCombo();
        TargetingService.tick(player, data);
        helper.assertTrue(data.combat().targetId() == next.getId() && data.combat().comboFinished(),
                "Transferring revived an already finished combo");
        first.setHealth(first.getMaxHealth());
        seedCombo(data.combat(), first.getId(), now + 20);
        first.setPos(player.position().add(0, 0, 30));
        TargetingService.tick(player, data);
        helper.assertTrue(data.combat().targetId() == -1 && data.combat().comboFinished(),
                "A living target leaving range triggered automatic transfer");
        first.setPos(player.position().add(0, 0, 3));
        first.setHealth(0.0F);
        next.setInvisible(true);
        seedCombo(data.combat(), first.getId(), now + 20);
        TargetingService.tick(player, data);
        helper.assertTrue(data.combat().targetId() == -1 && data.combat().comboFinished(),
                "Death without an eligible replacement retained lock or combo");
        first.discard();
        next.discard();
        helper.succeed();
    }

    /**
     * The True Master Sword adds 5 true damage on every tenth combo hit. The bonus rides along with
     * the hit that earned it, so it must not advance the combo count, it must land through any
     * invulnerability window the hit just opened, and it must be the weapon - not the combo - that
     * decides whether it fires.
     *
     * <p>{@code recordComboHit} only records a hit; it never deals the base damage itself, so the
     * health change observed here is exactly the bonus and nothing else.
     */
    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void trueMasterSwordAddsTrueDamageEveryTenthHit(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 10);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.TRUE_MASTER_SWORD.get()));
        var target = mob(helper, EntityType.IRON_GOLEM, player.position().add(0, 0, 3));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        target.setHealth(1000.0F);
        data.combat().setTarget(target.getId());
        // Nine recorded hits build the chain; none of them may fire the bonus.
        for (int hit = 1; hit <= 9; hit++) {
            float before = target.getHealth();
            BasicSwordSkill.recordComboHit(player, data, target, 5.0F);
            helper.assertTrue(data.combat().comboCount() == hit,
                    "Combo hit " + hit + " was not recorded");
            helper.assertTrue(before == target.getHealth(),
                    "Combo hit " + hit + " dealt bonus true damage");
        }
        // The tenth recorded hit is the one that earns the bonus.
        float beforeTenth = target.getHealth();
        BasicSwordSkill.recordComboHit(player, data, target, 5.0F);
        helper.assertTrue(data.combat().comboCount() == 10, "The tenth hit was not counted");
        helper.assertTrue(Math.abs((beforeTenth - target.getHealth()) - 5.0F) < 0.01F,
                "The tenth hit must add exactly five points of true damage");
        helper.assertTrue(Math.abs(data.combat().comboDamage() - 55.0F) < 0.01F,
                "The bonus true damage was not folded into the recorded combo damage");
        // The eleventh hit is ordinary: the bonus belongs to the tenth hit alone.
        float beforeEleventh = target.getHealth();
        BasicSwordSkill.recordComboHit(player, data, target, 5.0F);
        helper.assertTrue(data.combat().comboCount() == 11 && beforeEleventh == target.getHealth(),
                "A hit between bonuses dealt extra true damage");
        // Another sword never fires the bonus, so the effect is the weapon's, not the combo's.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        data.combat().clearCombo();
        for (int hit = 1; hit <= 9; hit++) BasicSwordSkill.recordComboHit(player, data, target, 5.0F);
        float beforePlainTenth = target.getHealth();
        BasicSwordSkill.recordComboHit(player, data, target, 5.0F);
        helper.assertTrue(data.combat().comboCount() == 10 && beforePlainTenth == target.getHealth(),
                "A non-True-Master sword triggered the tenth-hit bonus");
        target.discard();
        helper.succeed();
    }

    private static void seedCombo(PlayerCombatState state, int targetId, long deadline) {
        state.clearCombo();
        state.setTarget(targetId);
        state.recordHit(targetId, 4.0F, 5, deadline);
    }
    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "combo_test"));
        player.setPos(helper.absoluteVec(new Vec3(4.5D, 10.0D, 4.5D)));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"))
                .setSkillLevel(ZSSContentIds.SWORD_BASIC, 1);
        return player;
    }

    private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, Vec3 position) {
        T mob = type.create(helper.getLevel());
        mob.setPos(position);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }
}
