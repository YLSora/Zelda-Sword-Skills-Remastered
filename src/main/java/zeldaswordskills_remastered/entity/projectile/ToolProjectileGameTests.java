package zeldaswordskills_remastered.entity.projectile;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Same package to exercise impact callbacks and pull state without reflection. */
@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ToolProjectileGameTests {
    private ToolProjectileGameTests() { }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void toolsPullSmallHeartsAndHeal(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        var sequence = helper.startSequence();
        for (ToolProjectile.Mode mode : new ToolProjectile.Mode[]{ToolProjectile.Mode.HOOKSHOT,
                ToolProjectile.Mode.WHIP, ToolProjectile.Mode.BOOMERANG, ToolProjectile.Mode.MAGIC_BOOMERANG}) {
            Vec3 origin = owner.position().add(0, 1, 3);
            ItemEntity heart = new ItemEntity(helper.getLevel(), origin.x, origin.y, origin.z,
                    new ItemStack(ZSSRegistries.getItem("small_heart")));
            heart.setNoGravity(true);
            heart.setDeltaMovement(Vec3.ZERO);
            sequence.thenExecute(() -> helper.assertTrue(helper.getLevel().addFreshEntity(heart),
                    "Small heart could not be spawned: " + mode));
            // Fresh chunks may not expose their entities to the boomerang's proximity query yet.
            sequence.thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, heart.getBoundingBox().inflate(0.6D)).contains(heart),
                    "Small heart is not visible to world entity queries: " + mode));
            sequence.thenExecute(() -> {
                owner.setHealth(1.0F);
                owner.getInventory().clearContent();
                ToolProjectile tool = new ToolProjectile(ZSSRegistries.BOOMERANG.get(), helper.getLevel(), owner, mode);
                try {
                    tool.setPos(heart.position());
                    helper.assertTrue(HookshotPull.canGrab(heart) && (mode.isBoomerang() || tool.canHitEntity(heart)),
                            "Small hearts must be eligible tool targets: " + mode);
                    tool.onHitEntity(new EntityHitResult(heart));
                    for (int tick = 0; tick < 41 && !tool.isRemoved(); tick++) tool.tick();
                    helper.assertTrue(!heart.isAlive(), "Tool did not collect the small heart: " + mode);
                    helper.assertTrue(tool.isRemoved(), "Tool did not finish returning: " + mode);
                    helper.assertTrue(owner.getHealth() == 3.0F,
                            "Tool did not heal exactly two health points: " + mode + ", health=" + owner.getHealth());
                    helper.assertTrue(owner.getInventory().countItem(ZSSRegistries.getItem("small_heart")) == 0,
                            "Tool left a small heart in the inventory: " + mode);
                } finally {
                    tool.discard();
                    heart.discard();
                }
            });
        }
        sequence.thenSucceed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void loweredHookHitsAimedTargetFaceAtObliqueAngles(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 8, 2));
        for (Direction face : Direction.values()) {
            helper.getLevel().setBlockAndUpdate(anchor, ZSSRegistries.HOOK_TARGET.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, face));
            Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 tangent = face.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
            Vec3 otherTangent = normal.cross(tangent);
            for (double offset : new double[]{-3, 0, 3}) {
                Vec3 aimedPoint = Vec3.atCenterOf(anchor).add(normal.scale(0.5D)).add(tangent.scale(0.4D));
                Vec3 eye = aimedPoint.add(normal.scale(4)).add(tangent.scale(offset)).add(otherTangent.scale(offset));
                owner.setPos(eye.subtract(0, owner.getEyeHeight(), 0));
                owner.setDeltaMovement(Vec3.ZERO);
                Vec3 view = aimedPoint.subtract(eye);
                float yaw = (float) Math.toDegrees(Math.atan2(-view.x, view.z));
                float pitch = (float) -Math.toDegrees(Math.atan2(view.y, view.horizontalDistance()));
                ToolProjectile hook = hook(helper, owner);
                hook.shootFromRotation(owner, pitch, yaw, 0, 1.6F, 0);
                owner.setYRot(yaw + 120);
                owner.setXRot(-pitch);
                for (int tick = 0; tick < 8 && !hook.isAnchored() && !hook.isReturning(); tick++) hook.tick();
                helper.assertTrue(hook.isAnchored(), "Crosshair red-face hit failed: " + face + ", offset=" + offset);
                hook.discard();
            }
        }
        helper.getLevel().setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void groundedPlayerIsPulledToSameLevelTarget(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        var level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(6, 3, 6));
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-6, 0, -6), anchor.offset(6, 3, 6)))
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-6, -1, -6), anchor.offset(6, -1, 6)))
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        for (var block : new net.minecraft.world.level.block.Block[]{ZSSRegistries.HOOK_TARGET.get(), ZSSRegistries.HOOK_TARGET_ALL.get()}) {
            for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                level.setBlockAndUpdate(anchor, block.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, face));
                Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
                for (double height : new double[]{0.15D, 0.5D, 0.85D}) {
                    Vec3 start = Vec3.atBottomCenterOf(anchor).add(normal.scale(4.5D));
                    owner.setPos(start);
                    owner.setNoGravity(false);
                    owner.setOnGround(true);
                    owner.setDeltaMovement(Vec3.ZERO);
                    Vec3 aim = Vec3.atBottomCenterOf(anchor).add(normal.scale(0.5D)).add(0, height, 0);
                    Vec3 direction = aim.subtract(owner.getEyePosition());
                    float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                    float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
                    ToolProjectile hook = hook(helper, owner);
                    hook.shootFromRotation(owner, pitch, yaw, 0, 1.6F, 0);
                    for (int tick = 0; tick < 8 && !hook.isAnchored() && !hook.isReturning(); tick++) hook.tick();
                    helper.assertTrue(hook.isAnchored(), "Ground-level shot failed to anchor: " + face + ", height=" + height);
                    int moves = 0;
                    while (hook.isAnchored() && moves++ < 20) {
                        hook.tick();
                        // ServerPlayer movement normally arrives from the client; emulate that step here.
                        owner.move(net.minecraft.world.entity.MoverType.SELF, owner.getDeltaMovement());
                        helper.assertTrue(owner.getY() >= start.y - 1.0E-6D
                                        && level.noCollision(owner, owner.getBoundingBox()),
                                "Hook pulled the grounded player into the floor or target");
                    }
                    helper.assertTrue(owner.position().subtract(start).horizontalDistance() > 3.0D,
                            "Valid grounded hit retracted before pulling the player: " + face + ", height=" + height);
                    helper.assertTrue(!hook.isAnchored() && !owner.isNoGravity()
                                    && owner.getDeltaMovement().lengthSqr() == 0.0D,
                            "Ground pull did not finish and restore gravity at the target");
                    hook.discard();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void playerPullStopsAtSolidObstacle(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        Vec3 start = owner.position();
        BlockPos wall = BlockPos.containing(start.add(0, 0, 2));
        for (BlockPos pos : BlockPos.betweenClosed(wall.offset(-1, -1, 0), wall.offset(1, 2, 0)))
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        HookshotPull pull = new HookshotPull(owner);
        for (int tick = 0; tick < 20; tick++) {
            if (!pull.tick(start.add(0, 0, 5), 0.15D)) break;
            owner.move(net.minecraft.world.entity.MoverType.SELF, owner.getDeltaMovement());
        }
        pull.finish();
        helper.assertTrue(owner.getZ() > start.z && owner.getBoundingBox().maxZ <= wall.getZ() + 1.0E-6D
                        && !owner.isNoGravity() && owner.getDeltaMovement().lengthSqr() == 0.0D,
                "Clipped player pull crossed an obstacle or failed to restore movement state");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void hookLaunchAndReturnUseHeadWithoutFollowingView(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        Vec3 head = new Vec3(owner.getX(), owner.getEyeY() - 0.25D, owner.getZ());
        for (int yaw = -180; yaw <= 180; yaw += 90) {
            for (int pitch : new int[]{-85, 0, 85}) {
                owner.setYRot(yaw);
                owner.setYHeadRot(yaw + 45);
                owner.setXRot(pitch);
                ToolProjectile hook = hook(helper, owner);
                helper.assertTrue(hook.position().distanceToSqr(head) < 1.0E-10D,
                        "Hook must launch 0.25 blocks below the eyes without a view-dependent offset");
                hook.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, 1.6F, 0.0F);
                Vec3 flightMotion = hook.getDeltaMovement();
                for (int tick = 0; tick < 2; tick++) {
                    owner.setYRot(yaw + 90 * (tick + 1));
                    owner.setYHeadRot(owner.getYRot());
                    owner.setXRot(-pitch);
                    Vec3 expectedPosition = hook.position().add(flightMotion);
                    hook.tick();
                    helper.assertTrue(!hook.isReturning() && !hook.isAnchored()
                                    && hook.position().distanceToSqr(expectedPosition) < 1.0E-10D
                                    && hook.getDeltaMovement().distanceToSqr(flightMotion) < 1.0E-10D,
                            "Turning after launch must not change the outgoing trajectory");
                }
                hook.setPos(head.add(3, 1, 4));
                hook.onHitBlock(new BlockHitResult(hook.position(), Direction.NORTH, hook.blockPosition(), false));
                helper.assertTrue(hook.isReturning(), "A miss must start retracting");
                Vec3 expected = head.subtract(hook.position()).normalize();
                hook.tick();
                helper.assertTrue(hook.getDeltaMovement().normalize().distanceToSqr(expected) < 1.0E-10D,
                        "Returning hook must aim 0.25 blocks below the eyes regardless of view direction");
                hook.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void hookTargetsAcceptUnupgradedHooks(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        BlockPos anchor = BlockPos.containing(owner.position().add(0, 1, 4));
        for (var target : new net.minecraft.world.level.block.Block[]{
                ZSSRegistries.HOOK_TARGET.get(), ZSSRegistries.HOOK_TARGET_ALL.get()}) {
            for (Direction facing : Direction.values()) {
                helper.getLevel().setBlockAndUpdate(anchor, target.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, facing));
                for (int upgrade = 0; upgrade < 4; upgrade++) {
                    boolean upgraded = upgrade >= 2;
                    for (Direction face : Direction.values()) {
                        if (upgraded && face != facing) continue;
                        ToolProjectile hook = hook(helper, owner).configureHook(upgrade == 1 ? 24 : 12,
                                upgraded, upgrade == 3);
                        Vec3 hit = Vec3.atCenterOf(anchor).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5D));
                        hook.onHitBlock(new BlockHitResult(hit, face, anchor, false));
                        boolean shouldAnchor = target == ZSSRegistries.HOOK_TARGET_ALL.get() || face == facing;
                        helper.assertTrue(hook.isAnchored() == shouldAnchor && hook.isReturning() != shouldAnchor,
                                "Hook target face rule failed: " + target + ", upgrade=" + upgrade
                                        + ", facing=" + facing + ", face=" + face);
                        hook.discard();
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void projectileVariantsPersistResourceIds(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        var ids = new java.util.HashSet<String>();
        for (ToolProjectile.Mode mode : ToolProjectile.Mode.values()) {
            ToolProjectile original = new ToolProjectile(ZSSRegistries.BOOMERANG.get(), helper.getLevel(), owner, mode);
            original.shootFromRotation(owner, 0, 37, 0, 1.6F, 0);
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.contains("mode", Tag.TAG_STRING)
                    && saved.getString("mode").equals(mode.id().toString()) && ids.add(saved.getString("mode")),
                    "Tool modes must save distinct resource IDs: " + mode);
            ToolProjectile restored = ZSSRegistries.BOOMERANG.get().create(helper.getLevel());
            restored.load(saved);
            helper.assertTrue(!restored.isRemoved() && restored.mode() == mode
                            && restored.isNoGravity() == original.isNoGravity(),
                    "Tool variant or gravity changed after load: " + mode);
            if (mode == ToolProjectile.Mode.HOOKSHOT)
                helper.assertTrue(saved.getFloat("hook_launch_yaw") == 37.0F
                                && restored.saveWithoutId(new CompoundTag()).getFloat("hook_launch_yaw") == 37.0F,
                        "Hook launch direction must survive saving while in flight");
        }
        ids.clear();
        for (ThrownBomb.BombKind kind : ThrownBomb.BombKind.values()) {
            ThrownBomb original = new ThrownBomb(helper.getLevel(), owner, kind).configureEncounterBomb(23).configureDamage(9);
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.contains("kind", Tag.TAG_STRING)
                    && saved.getString("kind").equals(kind.id().toString()) && ids.add(saved.getString("kind")),
                    "Bombs must save distinct resource IDs: " + kind);
            ThrownBomb restored = ZSSRegistries.BOMB.get().create(helper.getLevel());
            restored.load(saved);
            CompoundTag roundTrip = restored.saveWithoutId(new CompoundTag());
            helper.assertTrue(!restored.isRemoved() && restored.bombKind() == kind && roundTrip.getInt("fuse") == 23
                            && roundTrip.getFloat("damage") == 9 && !roundTrip.getBoolean("griefs_blocks"),
                    "Bomb variant or encounter settings changed after load: " + kind);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void projectileVariantsRejectNumericAndUnknownIds(GameTestHelper helper) {
        for (String field : new String[]{"mode", "kind"}) {
            for (Tag invalid : new Tag[]{net.minecraft.nbt.IntTag.valueOf(1),
                    net.minecraft.nbt.StringTag.valueOf("zeldaswordskills_remastered:unknown"),
                    net.minecraft.nbt.StringTag.valueOf("Invalid ID!")}) {
                CompoundTag tag = new CompoundTag();
                tag.put(field, invalid);
                var projectile = field.equals("mode") ? ZSSRegistries.BOOMERANG.get().create(helper.getLevel())
                        : ZSSRegistries.BOMB.get().create(helper.getLevel());
                projectile.readAdditionalSaveData(tag);
                helper.assertTrue(projectile.isRemoved(), "Unsupported variant must be discarded: " + field + "=" + invalid);
            }
        }
        ToolProjectile summonedTool = ZSSRegistries.HOOKSHOT.get().create(helper.getLevel());
        summonedTool.readAdditionalSaveData(new CompoundTag());
        helper.assertTrue(!summonedTool.isRemoved() && summonedTool.mode() == ToolProjectile.Mode.HOOKSHOT,
                "Absent summon NBT must preserve the registered tool type");
        ThrownBomb summonedBomb = ZSSRegistries.BOMB.get().create(helper.getLevel());
        summonedBomb.readAdditionalSaveData(new CompoundTag());
        helper.assertTrue(!summonedBomb.isRemoved() && summonedBomb.bombKind() == ThrownBomb.BombKind.STANDARD,
                "Absent summon NBT must preserve the default bomb type");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void hookFiltersBossesAndStationaryEntities(GameTestHelper helper) {
        for (EntityType<?> type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.COW, EntityType.IRON_GOLEM}) {
            helper.assertTrue(HookshotPull.canGrab(type.create(helper.getLevel())), "Mobile creature rejected: " + type);
        }
        for (EntityType<?> type : new EntityType<?>[]{EntityType.ARMOR_STAND, EntityType.SHULKER,
                EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN,
                ZSSRegistries.DARKNUT_BOSS.get(), ZSSRegistries.DESERT_BOSS.get()}) {
            helper.assertTrue(!HookshotPull.canGrab(type.create(helper.getLevel())), "Ineligible entity accepted: " + type);
        }
        var cow = EntityType.COW.create(helper.getLevel());
        cow.setNoAi(true);
        helper.assertTrue(!HookshotPull.canGrab(cow), "NoAI creature accepted");
        helper.assertTrue(!HookshotPull.canGrab(player(helper)), "Players must not be grabbed");
        for (var type : ZSSRegistries.LEGACY_CREATURE_TYPES) {
            var creature = type.get().create(helper.getLevel());
            if (creature.isBoss() || creature instanceof zeldaswordskills_remastered.entity.DekuCreature)
                helper.assertTrue(!HookshotPull.canGrab(creature), "Boss/rooted creature accepted");
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void hookedCreatureStopsThreeBlocksAheadAndRestoresGravity(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        var cow = EntityType.COW.create(helper.getLevel());
        cow.setPos(owner.position().add(4.5D, 0, 0));
        ToolProjectile hook = hook(helper, owner);
        hook.shootFromRotation(owner, 0, -90, 0, 1.6F, 0);
        owner.setYRot(90);
        hook.onHitEntity(new EntityHitResult(cow));
        for (int tick = 0; tick < 8; tick++) {
            owner.setYRot(90 + tick * 45);
            owner.setYHeadRot(owner.getYRot());
            owner.setXRot(tick % 2 == 0 ? 80 : -80);
            hook.tick();
            helper.assertTrue(Math.abs(cow.getZ() - owner.getZ()) < 1.0E-6D,
                    "Turning before impact or during pulling must not steer the grabbed creature sideways");
        }
        helper.assertTrue(cow.position().distanceTo(owner.position().add(3, 0, 0)) < 0.16D,
                "Grabbed creature must stop three horizontal blocks along the launch direction");
        helper.assertTrue(!cow.isNoGravity() && cow.getDeltaMovement().lengthSqr() == 0.0D,
                "Completed pull must restore gravity and stop residual motion");
        hook.discard();

        cow.setPos(owner.position().add(0, 0, 4.5D));
        cow.setNoGravity(true);
        ToolProjectile interrupted = hook(helper, owner);
        interrupted.onHitEntity(new EntityHitResult(cow));
        interrupted.tick();
        interrupted.discard();
        helper.assertTrue(cow.isNoGravity(), "An interrupted pull must preserve pre-existing no-gravity state");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void hookCannotDragThroughWall(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        ItemEntity item = new ItemEntity(helper.getLevel(), owner.getX(), owner.getY(), owner.getZ() + 3.0D,
                new ItemStack(Items.EMERALD));
        BlockPos wall = BlockPos.containing(owner.position().add(0, 0, 2.0D));
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        HookshotPull pull = new HookshotPull(item);
        for (int tick = 0; tick < 8; tick++) {
            if (!pull.tick(owner.position(), 0.15D)) break;
        }
        pull.finish();
        helper.assertTrue(item.getZ() >= wall.getZ() + 1.0D, "Items must not cross an obstructing wall");
        helper.assertTrue(!item.isNoGravity(), "Obstructed pull must restore gravity");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void anchoredHookRestoresOwnerGravityOnRemoval(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        owner.setNoGravity(false);
        BlockPos anchor = BlockPos.containing(owner.position().add(0, 1, 4));
        helper.getLevel().setBlockAndUpdate(anchor, Blocks.OAK_LOG.defaultBlockState());
        ToolProjectile hook = hook(helper, owner);
        Vec3 hit = Vec3.atCenterOf(anchor).add(0, 0, -0.5D);
        hook.onHitBlock(new BlockHitResult(hit, Direction.NORTH, anchor, false));
        hook.tick();
        helper.assertTrue(hook.isAnchored() && owner.isNoGravity() && owner.getDeltaMovement().length() > 0.0D,
                "Hookable block must start a gravity-free pull toward the anchor");
        hook.discard();
        helper.assertTrue(!owner.isNoGravity() && owner.getDeltaMovement().lengthSqr() == 0.0D,
                "Removing an anchored hook must restore owner gravity and stop pulling");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void hookCollectsItemsAtOwnerAndStopsOnRemoval(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        owner.getInventory().clearContent();
        ItemEntity item = new ItemEntity(helper.getLevel(), owner.getX(), owner.getY(), owner.getZ() + 4.0D,
                new ItemStack(Items.DIAMOND, 3));
        item.setDefaultPickUpDelay();
        helper.assertTrue(HookshotPull.canGrab(item), "Dropped items must be hookable");
        ToolProjectile hook = hook(helper, owner);
        hook.onHitEntity(new EntityHitResult(item));
        for (int tick = 0; tick < 12 && !hook.isRemoved(); tick++) hook.tick();
        helper.assertTrue(item.isRemoved() && owner.getInventory().countItem(Items.DIAMOND) == 3,
                "Grabbed item must reach the owner, bypass its pickup delay, and be collected once");
        hook.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void whipCollectsDroppedItems(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        ItemEntity item = new ItemEntity(helper.getLevel(), owner.getX(), owner.getY(), owner.getZ() + 3.0D,
                new ItemStack(Items.GOLD_INGOT, 2));
        ToolProjectile whip = new ToolProjectile(ZSSRegistries.WHIP.get(), helper.getLevel(), owner,
                ToolProjectile.Mode.WHIP);
        whip.onHitEntity(new EntityHitResult(item));
        for (int tick = 0; tick < 12 && !whip.isRemoved(); tick++) whip.tick();
        helper.assertTrue(item.isRemoved() && owner.getInventory().countItem(Items.GOLD_INGOT) == 2,
                "Whip must pull dropped items to the owner and collect them");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void boomerangFliesStraightThenReturnsWithoutRepeatedDamage(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        ToolProjectile boomerang = new ToolProjectile(ZSSRegistries.BOOMERANG.get(), helper.getLevel(), owner,
                ToolProjectile.Mode.BOOMERANG);
        boomerang.setDeltaMovement(0, 0, 1.1D);
        double x = boomerang.getX();
        boomerang.tick();
        helper.assertTrue(Math.abs(boomerang.getX() - x) < 1.0E-8D, "Outgoing flight must not turn sideways");
        boomerang.setPos(owner.position().add(0, 1, 4));
        boomerang.onHitBlock(new BlockHitResult(boomerang.position(), Direction.NORTH,
                boomerang.blockPosition(), false));
        var cow = EntityType.COW.create(helper.getLevel());
        float health = cow.getHealth();
        boomerang.onHit(new EntityHitResult(cow));
        helper.assertTrue(cow.getHealth() == health, "Returning boomerang must not damage intervening entities");
        boomerang.tick();
        helper.assertTrue(boomerang.getDeltaMovement().z < 0, "Return must steer toward the current owner position");
        for (int tick = 0; tick < 10 && !boomerang.isRemoved(); tick++) boomerang.tick();
        helper.assertTrue(boomerang.isRemoved(), "Boomerang must finish returning");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void registeredHookVariantsUseDifferentMaterials(GameTestHelper helper) {
        var owner = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 4, 3));
        String[] ids = {"hookshot", "stoneshot", "multishot"};
        var blocks = new net.minecraft.world.level.block.Block[]{Blocks.OAK_LOG, Blocks.STONE, Blocks.DIRT,
                Blocks.CLAY, Blocks.SAND, Blocks.GLASS};
        boolean[][] attaches = {{true, false, false, false, false, false},
                {false, true, false, false, false, false}, {true, true, true, true, false, false}};
        for (int variant = 0; variant < ids.length; variant++) {
            for (int block = 0; block < blocks.length; block++) {
                helper.getLevel().setBlockAndUpdate(pos, blocks[block].defaultBlockState());
                ItemStack stack = new ItemStack(ZSSRegistries.getItem(ids[variant]));
                if (block % 2 == 1) stack = zeldaswordskills_remastered.item.StageNineToolItem.upgradeHook(stack, "hookshot_extender");
                owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                owner.getCooldowns().removeCooldown(stack.getItem());
                stack.use(helper.getLevel(), owner, net.minecraft.world.InteractionHand.MAIN_HAND);
                ToolProjectile hook = helper.getLevel().getEntities(ZSSRegistries.HOOKSHOT.get(), owner.getBoundingBox().inflate(64),
                        entity -> entity.getOwner() == owner).get(0);
                CompoundTag settings = new CompoundTag();
                hook.addAdditionalSaveData(settings);
                helper.assertTrue(settings.getDouble("configured_range") == (block % 2 == 1 ? 24 : 12),
                        "Extender range differs between hook variants");
                hook.onHitBlock(new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
                helper.assertTrue(hook.isAnchored() == attaches[variant][block], "Wrong material: " + ids[variant] + blocks[block]);
                helper.assertTrue(hook.isReturning() != attaches[variant][block], "Failed hit must retract");
                boolean breaks = blocks[block] == Blocks.GLASS || variant == 1 && blocks[block] == Blocks.OAK_LOG;
                helper.assertTrue(helper.getLevel().isEmptyBlock(pos) == breaks, "Wrong break behavior: " + ids[variant] + blocks[block]);
                hook.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void magicWhipAttachesSwingsAndReleases(GameTestHelper helper) {
        var owner = player(helper);
        ItemStack stack = new ItemStack(ZSSRegistries.getItem("magic_whip"));
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        stack.use(helper.getLevel(), owner, net.minecraft.world.InteractionHand.MAIN_HAND);
        ToolProjectile whip = helper.getLevel().getEntities(ZSSRegistries.WHIP.get(), owner.getBoundingBox().inflate(64),
                entity -> entity.getOwner() == owner).get(0);
        CompoundTag saved = new CompoundTag();
        whip.addAdditionalSaveData(saved);
        helper.assertTrue(saved.getDouble("configured_range") == 18 && saved.getFloat("configured_damage") == 10
                && whip.isMagicWhip(), "Magic whip range, power or render identity missing");
        var second = stack.use(helper.getLevel(), owner, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(second.getResult() == net.minecraft.world.InteractionResult.FAIL, "Duplicate whip launch accepted");
        BlockPos anchor = owner.blockPosition().offset(2, 5, 2);
        helper.getLevel().setBlockAndUpdate(anchor, Blocks.STONE.defaultBlockState());
        whip.onHitBlock(new BlockHitResult(Vec3.atBottomCenterOf(anchor), Direction.DOWN, anchor, false));
        helper.assertTrue(whip.isAnchored(), "Magic whip failed to attach to stone");
        owner.setOnGround(false);
        owner.setDeltaMovement(0, -0.1, 0);
        owner.fallDistance = 5;
        whip.tick();
        helper.assertTrue(owner.getDeltaMovement().horizontalDistanceSqr() > 0 && owner.fallDistance == 0,
                "Anchored whip did not drive a server-side swing");
        owner.stopUsingItem();
        whip.tick();
        helper.assertTrue(whip.isRemoved() && !owner.isNoGravity(), "Release left an active tether or disabled gravity");
        helper.getLevel().setBlockAndUpdate(anchor, Blocks.DIRT.defaultBlockState());
        helper.assertTrue(!ToolBlockRules.canAttachWhip(helper.getLevel(), anchor, Blocks.DIRT.defaultBlockState())
                && ToolBlockRules.canAttachWhip(helper.getLevel(), anchor, Blocks.SANDSTONE.defaultBlockState()),
                "Magic whip hardness/sandstone exception is incorrect");
        helper.succeed();
    }

    private static ToolProjectile hook(GameTestHelper helper, FakePlayer owner) {
        return new ToolProjectile(ZSSRegistries.HOOKSHOT.get(), helper.getLevel(), owner,
                ToolProjectile.Mode.HOOKSHOT).configureHook(12, false, false);
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ToolTest"));
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 0)));
        player.setPos(origin);
        player.setYRot(0);
        player.setXRot(0);
        return player;
    }
}
