package zeldaswordskills_remastered.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.common.MinecraftForge;
import zeldaswordskills_remastered.event.ZSSQuestEvents;
import zeldaswordskills_remastered.event.ZSSPlayerEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.combat.TargetingService;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.entity.NaviService;
import zeldaswordskills_remastered.item.SpecialItems;
import zeldaswordskills_remastered.network.TargetIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.UUID;

/**
 * Covers the Navi companion rules: one stable fairy per player, only one in the world at a time,
 * bottling and release restore the same identity, and a locked enemy becomes the orbit anchor.
 */
@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NaviGameTests {
    private NaviGameTests() { }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void naviIdentityIsStableAcrossCapture(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature first = NaviService.summon(player);
        UUID identity = first.getUUID();
        helper.assertTrue(identity.equals(NaviCreature.storedNaviId(player)),
                "A summoned Navi must record its own UUID as the player's Navi identity");
        helper.assertTrue(NaviService.isActive(player), "A summoned Navi must mark the player as active");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        captureThroughEvent(player, first, InteractionHand.MAIN_HAND);
        helper.assertTrue(first.isRemoved(), "Capturing Navi must remove the entity from the world");
        helper.assertTrue(!NaviService.isActive(player), "A bottled Navi must not count as active");
        helper.assertTrue(heldNaviBottle(player), "Capturing Navi must leave a Navi bottle in the player's hand");

        var result = player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction() && player.getMainHandItem().is(Items.GLASS_BOTTLE),
                "Using a Navi bottle must release her and return an empty bottle");
        NaviCreature released = NaviCreature.findOwned(helper.getLevel(), identity).orElseThrow();
        helper.assertTrue(released.getUUID().equals(identity),
                "Releasing the Navi must restore the same fairy UUID, not spawn a new one");
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void onlyOneNaviExistsForTheOwner(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature first = NaviService.summon(player);
        NaviCreature second = NaviService.summon(player);
        helper.assertTrue(first == second, "Summoning twice must reuse the single existing Navi");
        long owned = helper.getLevel().getEntities().getAll().spliterator().getExactSizeIfKnown();
        int matching = 0;
        for (net.minecraft.world.entity.Entity entity : helper.getLevel().getEntities().getAll()) {
            if (entity instanceof NaviCreature navi && player.getUUID().equals(navi.ownerUuid())) matching++;
        }
        helper.assertTrue(matching <= 1,
                "A player must never own more than one Navi at a time (found " + matching + ", size hint " + owned + ")");
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void offlineNaviIsRemovedAndRestored(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviService.summon(player);
        UUID identity = NaviCreature.storedNaviId(player);

        NaviService.onLogout(player);
        helper.assertTrue(helper.getLevel().getEntity(identity) == null,
                "Logging out must remove the player's Navi from the world");
        helper.assertTrue(NaviService.isActive(player),
                "Logging out must keep the Navi identity so the fairy can return");

        NaviService.onLogin(player);
        helper.assertTrue(helper.getLevel().getEntity(identity) instanceof NaviCreature,
                "Logging back in must restore the exact same Navi");
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void lockedEnemyBecomesTheOrbitAnchor(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature navi = NaviService.summon(player);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 5);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(zeldaswordskills_remastered.registry.ZSSRegistries.MASTER_SWORD.get()));

        Mob enemy = EntityType.ZOMBIE.create(helper.getLevel());
        if (enemy == null) throw new AssertionError("Could not create the orbit target");
        enemy.setPos(player.getX() + 3.0D, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(enemy);
        data.combat().setTarget(enemy.getId());

        helper.assertTrue(navi.lockedEnemy().map(target -> target == enemy).orElse(false),
                "Navi must recognise the owner's locked enemy as her anchor");
        helper.assertTrue(TargetingService.getLockedTarget(player, data).isPresent(),
                "The lock used by the orbit check must resolve through the shared targeting service");
        enemy.discard();
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void naviBottleRequiresTheRightOwner(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        NaviCreature navi = NaviService.summon(owner);
        UUID identity = navi.getUUID();

        FakePlayer stranger = new FakePlayer(helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "navi_stranger"));
        stranger.setPos(owner.position());
        helper.assertTrue(!identity.equals(NaviCreature.savedNaviId(stranger).orElse(null)),
                "A player without a Navi must not own somebody else's identity");

        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        PlayerInteractEvent.EntityInteract event = captureThroughEvent(stranger, navi, InteractionHand.MAIN_HAND);
        helper.assertTrue(event.getCancellationResult() == InteractionResult.FAIL
                        && stranger.getMainHandItem().is(Items.GLASS_BOTTLE),
                "Another player's empty bottle must be rejected without consuming it");
        helper.assertTrue(!navi.isRemoved(), "Another player must not be able to bottle your Navi");
        finish(helper, owner);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void naviTakesNoDamageAndIsNeverATarget(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature navi = NaviService.summon(player);
        navi.setHealth(navi.getMaxHealth());
        Mob zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) throw new AssertionError("Could not create the attacker");
        zombie.setPos(navi.position());
        helper.getLevel().addFreshEntity(zombie);

        // Ordinary damage from every common source must be refused outright.
        float before = navi.getHealth();
        navi.hurt(navi.damageSources().mobAttack(zombie), 100.0F);
        navi.hurt(navi.damageSources().inFire(), 100.0F);
        navi.hurt(navi.damageSources().fall(), 100.0F);
        navi.hurt(player.damageSources().playerAttack(player), 100.0F);
        helper.assertTrue(navi.getHealth() == before && navi.isAlive(),
                "Navi lost health to a damage source that must be refused");
        helper.assertTrue(navi.isInvulnerableTo(navi.damageSources().inFire())
                        && navi.isInvulnerableTo(zombie.damageSources().mobAttack(zombie)),
                "Navi must report immunity to ordinary damage");
        helper.assertTrue(navi.isInvulnerableTo(navi.damageSources().genericKill()),
                "Navi must also reject damage sources that bypass normal invulnerability");

        // Hostile AI must never be able to select her: the combat target predicate asks this.
        helper.assertTrue(!navi.canBeSeenAsEnemy() && !zombie.canAttack(navi),
                "A hostile mob was allowed to treat Navi as an attack target");
        var warden = EntityType.WARDEN.create(helper.getLevel());
        helper.assertTrue(warden != null && !warden.canTargetEntity(navi) && warden.canTargetEntity(zombie),
                "Warden sniffing must ignore Navi without ignoring ordinary living targets");
        warden.increaseAngerAt(navi);
        helper.assertTrue(warden.getAngerManagement().getActiveAnger(navi) == 0,
                "Warden must not gain anger at Navi");
        warden.discard();
        helper.assertTrue(!TargetingService.isFriendly(zombie)
                        && TargetingService.isFriendly(navi),
                "Navi must be classified as friendly, and the zombie as hostile");
        zombie.discard();
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void naviInteractionOnlyAllowsBottleAndEmptyHandWindow(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature navi = NaviService.summon(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        PlayerInteractEvent.EntityInteract emptyHand = new PlayerInteractEvent.EntityInteract(player,
                InteractionHand.MAIN_HAND, navi);
        ZSSQuestEvents.interactEntity(emptyHand);
        helper.assertTrue(!emptyHand.isCanceled(), "Empty-hand interaction window must remain available");

        player.setShiftKeyDown(true);
        PlayerInteractEvent.EntityInteract dialogue = new PlayerInteractEvent.EntityInteract(player,
                InteractionHand.MAIN_HAND, navi);
        ZSSQuestEvents.interactEntity(dialogue);
        PlayerInteractEvent.EntityInteract duplicateOffhand = new PlayerInteractEvent.EntityInteract(player,
                InteractionHand.OFF_HAND, navi);
        ZSSQuestEvents.interactEntity(duplicateOffhand);
        helper.assertTrue(dialogue.isCanceled() && !duplicateOffhand.isCanceled(),
                "One crouching empty-hand click must route Navi dialogue through the main hand only");
        player.setShiftKeyDown(false);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        PlayerInteractEvent.EntityInteract stick = new PlayerInteractEvent.EntityInteract(player,
                InteractionHand.MAIN_HAND, navi);
        ZSSQuestEvents.interactEntity(stick);
        helper.assertTrue(stick.isCanceled() && navi.isAlive(), "Held items must not interact with Navi");
        helper.assertTrue(!navi.canBeLeashed(player) && !navi.canBeHitByProjectile() && navi.isPickable(),
                "Navi must not be leashed or hit by projectiles, while remaining selectable for bottling");
        navi.fallDistance = 20.0F;
        navi.aiStep();
        helper.assertTrue(navi.fallDistance == 0.0F, "Navi fall distance must remain zero");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        PlayerInteractEvent.EntityInteract bottle = captureThroughEvent(player, navi, InteractionHand.MAIN_HAND);
        helper.assertTrue(bottle.getCancellationResult() == InteractionResult.CONSUME && navi.isRemoved(),
                "An empty glass bottle must remain the dedicated Navi interaction");
        finish(helper, player);
    }

    /** Navi must never be lockable by a sword skill, even when she is the closest entity. */
    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void naviIsNeverLockedBySwordSkills(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviService.summon(player);
        ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        data.setSkillLevel(ZSSContentIds.SWORD_BASIC, 5);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(ZSSRegistries.MASTER_SWORD.get()));
        // Navi is summoned right next to the owner, so she is the nearest candidate.
        TargetingService.handle(player, data, TargetIntentMessage.Action.ACQUIRE);
        helper.assertTrue(data.combat().targetId() == -1, "A sword skill locked onto Navi");
        finish(helper, player);
    }

    /** Resetting a player's Navi forgets the identity so the next summon makes a new fairy. */
    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void naviResetForgetsTheIdentity(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature navi = NaviService.summon(player);
        UUID first = navi.getUUID();
        NaviService.forget(player);
        helper.assertTrue(navi.isRemoved(), "Resetting must remove the current Navi from the world");
        helper.assertTrue(NaviCreature.savedNaviId(player).isEmpty() && !NaviService.isActive(player),
                "Resetting must clear both the identity and the active flag");
        NaviCreature replacement = NaviService.summon(player);
        helper.assertTrue(!replacement.getUUID().equals(first),
                "After a reset the next Navi must be a brand-new fairy");
        finish(helper, player);
    }

    private static boolean heldNaviBottle(Player player) {
        return player.getMainHandItem().is(ZSSRegistries.NAVI_BOTTLE.get())
                || player.getOffhandItem().is(ZSSRegistries.NAVI_BOTTLE.get());
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "navi_test"));
        player.setPos(helper.absoluteVec(new Vec3(4.5D, 10.0D, 4.5D)));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void identitySurvivesDeathAndEndClone(GameTestHelper helper) {
        FakePlayer original = player(helper);
        UUID identity = NaviCreature.storedNaviId(original);
        FakePlayer replacement = new FakePlayer(helper.getLevel(), original.getGameProfile());
        for (boolean death : new boolean[]{true, false}) {
            for (boolean active : new boolean[]{true, false}) {
                NaviService.markActive(original, active);
                ZSSPlayerEvents.clonePlayer(new PlayerEvent.Clone(replacement, original, death));
                helper.assertTrue(NaviCreature.savedNaviId(replacement).orElseThrow().equals(identity)
                                && NaviService.isActive(replacement) == active,
                        "Player cloning must retain the same Navi and bottled/active state");
            }
        }
        finish(helper, original);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void transientCompanionRestoresAfterUnload(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature first = NaviService.summon(player);
        UUID identity = first.getUUID();
        helper.assertTrue(!first.save(new CompoundTag()), "Navi must not have a second persistent copy in chunk NBT");
        first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        NaviService.restore(player);
        NaviCreature restored = NaviCreature.findOwned(helper.getLevel(), identity).orElseThrow();
        helper.assertTrue(restored != first && restored.getUUID().equals(identity),
                "Chunk unload must restore the same identity beside the owner");
        EntityJoinLevelEvent diskLoad = new EntityJoinLevelEvent(first, helper.getLevel(), true);
        ZSSPlayerEvents.naviJoined(diskLoad);
        helper.assertTrue(diskLoad.isCanceled(), "Chunk NBT must never restore an independent companion");
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void missingEntityCanBeReleasedWithoutReset(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature first = NaviService.summon(player);
        UUID identity = first.getUUID();
        first.discard();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.NAVI_BOTTLE.get()));
        var result = player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction() && NaviCreature.findOwned(helper.getLevel(), identity).isPresent(),
                "A stale active flag must not block restoring a missing Navi");
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void captureRequiresEmptyBottleAndHandlesOffhandStack(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature navi = NaviService.summon(player);
        for (ItemStack invalid : new ItemStack[]{ItemStack.EMPTY, new ItemStack(ZSSRegistries.NAVI_BOTTLE.get()), new ItemStack(Items.STICK)}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, invalid);
            helper.assertTrue(SpecialItems.NaviBottle.capture(player, navi, InteractionHand.MAIN_HAND) == InteractionResult.PASS
                            && navi.isAlive(), "Only an empty glass bottle may capture Navi");
        }
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
        captureThroughEvent(player, navi, InteractionHand.OFF_HAND);
        helper.assertTrue(player.getOffhandItem().is(Items.GLASS_BOTTLE) && player.getOffhandItem().getCount() == 1
                        && player.getInventory().countItem(ZSSRegistries.NAVI_BOTTLE.get()) == 1 && navi.isRemoved(),
                "Offhand capture must consume one empty bottle and grant one full bottle");
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void cancelledSpawnDoesNotConsumeBottle(GameTestHelper helper) {
        FakePlayer player = player(helper);
        UUID identity = NaviCreature.storedNaviId(player);
        java.util.function.Consumer<EntityJoinLevelEvent> reject = event -> {
            if (event.getEntity() instanceof NaviCreature && event.getEntity().getUUID().equals(identity)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(reject);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.NAVI_BOTTLE.get()));
            var result = player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult() == InteractionResult.FAIL && heldNaviBottle(player)
                            && !NaviService.isActive(player) && !NaviService.hasLoadedEntity(player),
                    "A rejected spawn must retain the bottle and must not claim a successful summon");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(reject);
        }
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void creativeBottlesKeepContainersWithoutDuplicateNavi(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getAbilities().instabuild = true;
        NaviCreature navi = NaviService.summon(player);
        UUID identity = navi.getUUID();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        captureThroughEvent(player, navi, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE)
                        && player.getInventory().countItem(ZSSRegistries.NAVI_BOTTLE.get()) == 1,
                "Creative capture keeps the empty bottle and grants one Navi bottle");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.NAVI_BOTTLE.get()));
        player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        NaviCreature released = NaviCreature.findOwned(helper.getLevel(), identity).orElseThrow();
        helper.assertTrue(heldNaviBottle(player), "Creative release retains the full bottle");
        var again = player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(again.getResult() == InteractionResult.FAIL
                        && NaviCreature.findOwned(helper.getLevel(), identity).orElseThrow() == released,
                "Repeated creative release must not duplicate Navi");
        finish(helper, player);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", timeoutTicks = 80)
    public static void flightFollowsPositionWithoutPlayerMomentum(GameTestHelper helper) {
        FakePlayer player = player(helper);
        NaviCreature navi = NaviService.summon(player);
        Vec3 start = player.position();
        for (int tick = 1; tick <= 40; tick++) {
            final int step = tick;
            helper.runAtTickTime(tick, () -> {
                player.setPos(start.add(step * 0.28D, step * 0.05D, 0));
                player.setDeltaMovement(Vec3.ZERO);
            });
        }
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(navi.isAlive() && navi.getX() > start.x + 8
                            && navi.position().distanceTo(player.getEyePosition()) < 3.0D,
                    "Flight must follow actual player positions even when server momentum is zero");
            finish(helper, player);
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void callCommandRecallsAndRecreatesOnlyCallerNavi(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        FakePlayer player = player(helper);
        FakePlayer other = player(helper);
        try {
            NaviCreature otherNavi = NaviService.summon(other);
            var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
            var source = player.createCommandSourceStack().withPermission(0);
            helper.assertTrue(dispatcher.execute("zss navi call", source) == 1, "Call must create a missing companion");
            UUID identity = NaviCreature.savedNaviId(player).orElseThrow();
            NaviCreature navi = NaviCreature.findOwned(helper.getLevel(), identity).orElseThrow();
            navi.setPos(player.position().add(12, 0, 0));
            helper.assertTrue(dispatcher.execute("zss navi call", source) == 1 && navi.distanceToSqr(player) < 9,
                    "Call must recall a loaded companion even within the normal teleport distance");
            navi.discard();
            helper.assertTrue(dispatcher.execute("zss navi call", source) == 1
                            && NaviCreature.findOwned(helper.getLevel(), identity).isPresent(),
                    "Call must recreate the same identity after removal");
            helper.assertTrue(NaviCreature.findOwned(helper.getLevel(), otherNavi.getUUID()).orElseThrow() == otherNavi,
                    "Call must not affect another player's companion");
        } finally {
            NaviService.forget(player);
            NaviService.forget(other);
            player.discard();
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void naviDoesNotChangeServerLighting(GameTestHelper helper) {
        FakePlayer player = player(helper);
        try {
            NaviCreature navi = NaviService.summon(player);
            var level = helper.getLevel();
            for (var block : new net.minecraft.world.level.block.Block[]{
                    net.minecraft.world.level.block.Blocks.AIR,
                    net.minecraft.world.level.block.Blocks.WATER,
                    net.minecraft.world.level.block.Blocks.STONE}) {
                var pos = navi.blockPosition();
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                navi.aiStep();
                helper.assertTrue(level.getBlockState(pos).is(block),
                        "Navi's visual light must not replace server blocks");
                helper.assertTrue(!level.getBlockState(navi.blockPosition()).is(ZSSRegistries.NAVI_LIGHT.get()),
                        "Navi must not place a light at her new server position");
            }
        } finally {
            NaviService.forget(player);
            player.discard();
        }
        helper.succeed();
    }

    private static PlayerInteractEvent.EntityInteract captureThroughEvent(FakePlayer player, NaviCreature navi, InteractionHand hand) {
        PlayerInteractEvent.EntityInteract event = new PlayerInteractEvent.EntityInteract(player, hand, navi);
        ZSSQuestEvents.interactEntity(event);
        if (!event.isCanceled()) throw new AssertionError("The empty bottle interaction was not handled");
        return event;
    }

    private static void finish(GameTestHelper helper, FakePlayer player) {
        NaviService.forget(player);
        player.discard();
        helper.succeed();
    }
}
