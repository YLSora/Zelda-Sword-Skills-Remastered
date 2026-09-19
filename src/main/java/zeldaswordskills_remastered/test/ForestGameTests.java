package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.DungeonBlocks;
import zeldaswordskills_remastered.block.LockedDoorBlock;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;
import zeldaswordskills_remastered.data.DungeonStructureDataProvider;
import zeldaswordskills_remastered.entity.CreatureSpawnRules;
import zeldaswordskills_remastered.entity.ForestBossCreature;
import zeldaswordskills_remastered.entity.LegacyCreature;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.ZSSWorldData;
import zeldaswordskills_remastered.worldgen.DungeonController;
import zeldaswordskills_remastered.worldgen.DungeonType;
import zeldaswordskills_remastered.worldgen.ForestEncounter;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ForestGameTests {
    private ForestGameTests() {}

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssForest")
    public static void forestLifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                DungeonCore core = place(helper, origin, 0);
                BlockPos center = core.getBlockPos();
                var neighborId = DungeonController.instanceId(level, center.offset(20, 0, 0));
                var neighborBefore = ZSSWorldData.get(level).dungeons().get(neighborId);
                var first = (ForestBossCreature) DungeonController.spawnBoss(level, core).orElseThrow();
                ensureReinforcement(helper, core);
                var ids = core.bossUuids();
                helper.assertTrue(ids.size() == 2 && ids.stream().map(level::getEntity).allMatch(entity ->
                                entity instanceof ForestBossCreature boss && boss.getMaxHealth() == 100
                                && boss.getAttributeValue(Attributes.ATTACK_DAMAGE) == 10
                                && boss.dungeonCorePos().orElseThrow().equals(center)
                                && boss.dungeonType().orElseThrow() == DungeonType.FOREST
                                && boss.getType().is(CreatureSpawnRules.DUNGEON_ONLY)), "Forest boss count, identity or attributes changed");
                helper.assertTrue(first.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "Forest boss retained cave-spider equipment");
                helper.assertTrue(core.forestReinforcements().size() == 4, "Forest's four Skulltula reinforcements did not spawn");
                var gold = (Mob) level.getEntity(core.forestReinforcements().iterator().next());
                helper.assertTrue(core.forestReinforcements().stream().map(level::getEntity).allMatch(entity ->
                        entity instanceof zeldaswordskills_remastered.entity.SkulltulaCreature spider && spider.getType() == ZSSRegistries.SKULLTULA.get()
                        && spider.kind() == LegacyCreature.Kind.SKULLTULA_NORMAL && spider.getMaxHealth() == 20
                        && spider.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3 && spider.getAttributeValue(Attributes.ARMOR) == 4
                        && spider.getAttributeValue(Attributes.MOVEMENT_SPEED) == .25
                        && spider.isPersistenceRequired()), "Forest reinforcement changed natural attributes or became golden");
                DungeonController.spawnBoss(level, core);
                helper.assertTrue(ids.equals(core.bossUuids()), "Forest repeated activation duplicated bosses");
                helper.assertTrue(core.forestHazardDelay() >= 300 && core.forestHazardDelay() <= 599, "Forest initial delay out of bounds");
                int delay = core.forestHazardDelay();
                DungeonController.tick(level, core);
                helper.assertTrue(core.forestHazardDelay() == delay - 1, "Forest hazard clock did not advance");
                var reloadedBoss = ZSSRegistries.FOREST_BOSS.get().create(level);
                reloadedBoss.load(first.saveWithoutId(new CompoundTag()));
                helper.assertTrue(reloadedBoss.getMaxHealth() == first.getMaxHealth()
                                && reloadedBoss.getItemBySlot(EquipmentSlot.CHEST).equals(first.getItemBySlot(EquipmentSlot.CHEST), false)
                                && reloadedBoss.dungeonCorePos().equals(first.dungeonCorePos()), "Forest entity reload lost equipment or binding");
                CompoundTag saved = core.saveWithoutMetadata();
                core.load(saved);
                helper.assertTrue(core.forestReinforcements().iterator().next().equals(gold.getUUID())
                                && core.bossUuids().equals(ids) && core.forestDifficulty() == difficulty.getId()
                                && core.forestHazardDelay() == delay - 1, "Forest core state lost on reload");

                // Removing an entity as unloaded must retain its binding and pause the encounter.
                CompoundTag goldTag = gold.saveWithoutId(new CompoundTag());
                gold.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                int reinforcementDelay = core.forestReinforcementDelay();
                DungeonController.tick(level, core);
                helper.assertTrue(core.forestHazardDelay() == delay - 1 && !core.completed()
                        && core.forestReinforcementDelay() == reinforcementDelay, "Unloaded reinforcement advanced or completed Forest");
                var restoredGold = ZSSRegistries.SKULLTULA.get().create(level);
                restoredGold.load(goldTag);
                helper.assertTrue(level.addFreshEntity(restoredGold), "Failed to restore Skulltula reinforcement");
                level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(32)).forEach(Entity::discard);
                ids.stream().map(level::getEntity).forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
                core.setForestReinforcementDelay(1);
                DungeonController.tick(level, core);
                helper.assertTrue(!core.completed() && rewards(level, center) == 0
                        && core.forestReinforcementDelay() == 1 && core.forestReinforcements().size() == 4
                        && DungeonController.spawnBoss(level, core).isEmpty(), "Forest finished or kept spawning after its bosses died");
                core.forestReinforcements().stream().map(level::getEntity)
                        .forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
                DungeonController.tick(level, core);
                helper.assertTrue(core.completed() && !level.getBlockState(center).getValue(DungeonBlocks.Core.SEALED)
                                && ZSSWorldData.get(level).dungeons().get(DungeonController.instanceId(level, center)).completed()
                                && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN)
                                && rewards(level, center) == 1, "Forest victory did not unseal, persist and reward once");
                helper.assertTrue(BlockPos.betweenClosedStream(center.offset(-core.arenaRadius(), core.arenaHeight(), -core.arenaRadius()),
                                center.offset(core.arenaRadius(), core.arenaHeight(), core.arenaRadius()))
                                .anyMatch(pos -> level.getBlockState(pos).getBlock() instanceof zeldaswordskills_remastered.block.MechanismBlocks.AncientTablet),
                        "Forest victory tablet did not land on the actual roof");
                DungeonController.tick(level, core);
                helper.assertTrue(rewards(level, center) == 1 && DungeonController.spawnBoss(level, core).isEmpty()
                                && java.util.Objects.equals(neighborBefore, ZSSWorldData.get(level).dungeons().get(neighborId)),
                        "Forest completion duplicated rewards or leaked to another instance");
                clean(level, center);
            }

            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            DungeonCore core = place(helper, origin, 0);
            DungeonController.spawnBoss(level, core).orElseThrow();
            ensureReinforcement(helper, core);
            var gold = (Mob) level.getEntity(core.forestReinforcements().iterator().next());
            CompoundTag tag = gold.saveWithoutId(new CompoundTag());
            gold.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            DungeonController.tick(level, core);
            core.load(core.saveWithoutMetadata());
            helper.assertTrue(core.forestCancelling() && !core.completed() && core.bossUuids().isEmpty()
                    && !core.forestReinforcements().isEmpty(), "Peaceful Forest did not retain unloaded participant");
            level.getServer().setDifficulty(Difficulty.HARD, true);
            var restored = ZSSRegistries.SKULLTULA.get().create(level);
            restored.load(tag);
            level.addFreshEntity(restored);
            DungeonController.tick(level, core);
            helper.assertTrue(restored.isRemoved() && core.forestDifficulty() == 0 && !core.completed()
                            && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN) && rewards(level, core.getBlockPos()) == 0,
                    "Cancellation resumed or rewarded after difficulty changed");
            DungeonController.spawnBoss(level, core).orElseThrow();
            helper.assertTrue(core.bossUuids().size() == 2 && core.forestDifficulty() == 3, "Forest cancellation could not restart");
        } finally {
            clean(level, origin.offset(16, 0, 16));
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssForest")
    public static void forestVariantsAndHazards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(20 - level.getGameTime() % 20, () -> {
            Difficulty previous = level.getDifficulty();
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[Forest_Variants]"));
            BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
            level.addFreshEntity(player);
            try {
                for (int variant = 0; variant < 20; variant++) {
                    level.getServer().setDifficulty(Difficulty.HARD, true);
                    DungeonCore core = place(helper, origin, variant);
                    BlockPos min = origin.offset(1, 0, 1);
                    int width = DungeonStructureDataProvider.roomSize(variant);
                    helper.assertTrue(core.arenaWidth() == width, "Forest width metadata missing");
                    var before = new HashMap<BlockPos, BlockState>();
                    BlockPos.betweenClosedStream(min, min.offset(width - 1, core.arenaHeight() - 1, width - 1))
                            .filter(pos -> pos.getX() == min.getX() || pos.getX() == min.getX() + width - 1
                                    || pos.getZ() == min.getZ() || pos.getZ() == min.getZ() + width - 1
                                    || pos.getY() == min.getY() || pos.getY() == min.getY() + core.arenaHeight() - 1)
                            .forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
                    player.setPos(min.getX() + 1.5, min.getY() + 3, min.getZ() + 1.5);
                    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().isEmpty(), "Spectator activated Forest");
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().size() == 2, "Forest variant failed automatic room activation: " + variant);
                    helper.assertTrue(core.bossUuids().stream().map(level::getEntity).allMatch(level::noCollision),
                            "Forest spawned a boss inside a pillar or wall: " + variant);
                    helper.assertTrue(level.getBlockState(door(core)).is(ZSSRegistries.SECRET_STONE_MOSSY_COBBLESTONE.get()), "Forest door did not seal");
                    long initialWebs = BlockPos.betweenClosedStream(min.offset(1, 1, 1), min.offset(width - 2, 3, width - 2))
                            .filter(pos -> level.getBlockState(pos).is(Blocks.COBWEB)).count();
                    helper.assertTrue(initialWebs > 0, "Forest did not create initial perimeter webs");
                    var reward = level.getBlockState(core.getBlockPos().above(2));
                    core.bossUuids().stream().map(level::getEntity).forEach(entity -> entity.setInvulnerable(true));
                    core.forestReinforcements().stream().map(level::getEntity).forEach(entity -> entity.setInvulnerable(true));
                    int lo = width < 11 ? width / 2 - 2 : 3;
                    int hi = width < 11 ? width / 2 + 2 : width - 4;
                    for (int x : new int[]{lo, hi}) for (int z : new int[]{lo, hi})
                        for (int y = 1; y < core.arenaHeight() - 2; y++)
                            level.setBlockAndUpdate(min.offset(x, y, z), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                    long pillarsBefore = countPillars(level, min, core, lo, hi);
                    for (int round = 0; round < 30; round++) {
                        core.setForestHazardDelay(1);
                        level.random.setSeed(12345L + round * 193L);
                        DungeonController.tick(level, core);
                        helper.assertTrue(core.forestHazardDelay() >= 100 && core.forestHazardDelay() <= 599,
                                "Forest recurring hazard delay out of bounds");
                    }
                    helper.assertTrue(countPillars(level, min, core, lo, hi) < pillarsBefore, "Forest did not collapse a pillar");
                    helper.assertTrue(level.getBlockState(core.getBlockPos().above(2)).equals(reward), "Forest destroyed central reward");
                    before.forEach((pos, state) -> {
                        if (!pos.equals(door(core)) && !pos.equals(door(core).above()))
                            helper.assertTrue(level.getBlockState(pos).equals(state), "Forest hazard changed shell at " + pos);
                    });
                    level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.forestDifficulty() == 0 && !core.completed()
                            && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN), "Forest variant cancellation failed");
                    clean(level, core.getBlockPos());
                }
                DungeonCore core = place(helper, origin, 0);
                CompoundTag tag = core.saveWithoutMetadata();
                tag.remove("arena_width");
                core.load(tag);
                level.getServer().setDifficulty(Difficulty.NORMAL, true);
                helper.assertTrue(DungeonController.spawnBoss(level, core).isEmpty(), "Forest accepted obsolete room metadata");
            } finally {
                player.discard();
                clean(level, origin.offset(22, 0, 22));
                clearRoom(level, origin);
                level.getServer().setDifficulty(previous, true);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssForest")
    public static void forestGoldenBossAndReinforcementCombat(GameTestHelper helper) {
        var level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                var boss = (ForestBossCreature) ZSSRegistries.FOREST_BOSS.get().create(level);
                boss.finalizeSpawn(level, level.getCurrentDifficultyAt(helper.absolutePos(BlockPos.ZERO)), MobSpawnType.STRUCTURE, null, null);
                var target = EntityType.COW.create(level);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                target.setHealth(100);
                helper.assertTrue(boss.doHurtTarget(target) && target.getHealth() < 100, "Forest melee did not cause real damage");
                helper.assertTrue(target.getHealth() == 90 && !target.hasEffect(MobEffects.POISON)
                                && boss instanceof zeldaswordskills_remastered.entity.SkulltulaCreature,
                        "Golden boss did not use fixed 10 damage and Skulltula combat");
                var spider = ZSSRegistries.SKULLTULA.get().create(level);
                var victim = EntityType.COW.create(level);
                helper.assertTrue(spider.doHurtTarget(victim) && victim.getHealth() == victim.getMaxHealth() - 3
                                && !victim.hasEffect(MobEffects.POISON), "Skulltula reinforcement lost natural melee behavior");
                helper.assertTrue(CreatureSpawnRules.GOLD_SKULLTULA_CHANCE == 100
                                && ZSSRegistries.SKULLTULA_GOLD.get().create(level).getMaxHealth() == 32,
                        "Natural golden Skulltula probability or attributes changed");
                boss.setNoAi(true);
                boss.setNoGravity(true);
                boss.moveTo(helper.absolutePos(new BlockPos(3, 3, 3)).getCenter());
                boss.setDeltaMovement(.2, 0, 0);
                boss.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), new Vec3(.25, .05, .25));
                double x = boss.getX();
                boss.move(net.minecraft.world.entity.MoverType.SELF, boss.getDeltaMovement());
                helper.assertTrue(boss.getX() - x > .19, "Forest boss was slowed by its own web");
                boss.discard();
            }
        } finally { level.getServer().setDifficulty(previous, true); }
        helper.succeed();
    }

    private static DungeonCore place(GameTestHelper helper, BlockPos origin, int variant) {
        ServerLevel level = helper.getLevel();
        clearRoom(level, origin);
        var template = level.getStructureManager().get(DungeonStructureDataProvider.templateId(DungeonType.FOREST, variant)).orElseThrow();
        helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, 2), "Forest template placement failed");
        int radius = (DungeonStructureDataProvider.roomSize(variant)) / 2;
        var core = (DungeonCore) level.getBlockEntity(origin.offset(1 + radius, 0, 1 + radius));
        ZSSWorldData.get(level).setDungeonState(DungeonController.instanceId(level, core.getBlockPos()), false, 0L);
        return core;
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssForest", timeoutTicks = 200)
    public static void forestJigsawRotations(GameTestHelper helper) {
        var level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            for (int variant = 0; variant < 20; variant++) for (var rotation : net.minecraft.world.level.block.Rotation.values()) {
                level.getServer().setDifficulty(Difficulty.NORMAL, true);
                clearRoom(level, origin);
                var template = level.getStructureManager().get(DungeonStructureDataProvider.templateId(DungeonType.FOREST, variant)).orElseThrow();
                int centerOffset = 1 + (DungeonStructureDataProvider.roomSize(variant)) / 2;
                BlockPos pivot = new BlockPos(centerOffset, 0, centerOffset);
                var settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot);
                helper.assertTrue(template.placeInWorld(level, origin, origin, settings, level.random, 2), "Rotated Forest placement failed");
                var core = (DungeonCore) level.getBlockEntity(origin.offset(pivot));
                helper.assertTrue(core.structureRotation() == rotation
                        && level.getBlockState(door(core)).is(ZSSRegistries.DOOR_BOSS_FOREST.get()), "Forest rotation lost real doorway");
                core.load(core.saveWithoutMetadata());
                helper.assertTrue(core.structureRotation() == rotation
                        && level.getBlockState(door(core)).is(ZSSRegistries.DOOR_BOSS_FOREST.get()), "Forest rotation lost on reload");
                ZSSWorldData.get(level).setDungeonState(DungeonController.instanceId(level, core.getBlockPos()), false, 0L);
                var before = new HashMap<BlockPos, BlockState>();
                BlockPos.betweenClosedStream(origin, origin.offset(44, 24, 44))
                        .filter(pos -> !zeldaswordskills_remastered.worldgen.DungeonArena.interior(core).contains(Vec3.atCenterOf(pos)))
                        .forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
                DungeonController.spawnBoss(level, core).orElseThrow();
                helper.assertTrue(core.bossUuids().size() == 2 && core.bossUuids().stream().map(level::getEntity).allMatch(level::noCollision),
                        "Rotated Forest spawned colliding bosses");
                for (int round = 0; round < 12; round++) {
                    core.setForestHazardDelay(1);
                    level.random.setSeed(round * 7919L + 7);
                    DungeonController.tick(level, core);
                }
                before.forEach((pos, state) -> {
                    if (!pos.equals(door(core)) && !pos.equals(door(core).above()))
                        helper.assertTrue(level.getBlockState(pos).equals(state), "Rotated Forest hazard damaged shell at " + pos);
                });
                level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                DungeonController.tick(level, core);
                helper.assertTrue(level.getBlockState(door(core)).is(ZSSRegistries.DOOR_BOSS_FOREST.get())
                        && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN), "Rotated Forest did not restore doorway");
                clean(level, core.getBlockPos());
            }
        } finally {
            clean(level, origin.offset(22, 0, 22));
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssForest")
    public static void forestHazardsKeepOpeningDifficulty(GameTestHelper helper) {
        var level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                DungeonCore core = place(helper, origin, 19);
                DungeonController.spawnBoss(level, core).orElseThrow();
                core.bossUuids().stream().map(level::getEntity).forEach(entity -> entity.setInvulnerable(true));
                core.forestReinforcements().stream().map(level::getEntity).forEach(entity -> entity.setInvulnerable(true));
                BlockPos min = origin.offset(1, 0, 1);
                BlockPos upperMin = min.offset(1, 3, 1);
                BlockPos upperMax = min.offset(core.arenaWidth()-2, core.arenaHeight()-2, core.arenaWidth()-2);
                for (BlockPos pos : BlockPos.betweenClosed(upperMin, upperMax))
                    if (level.getBlockEntity(pos) == null) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                core.setForestHazardDelay(2);
                DungeonController.tick(level, core);
                helper.assertTrue(core.forestHazardDelay() == 1
                                && BlockPos.betweenClosedStream(upperMin, upperMax).noneMatch(pos -> level.getBlockState(pos).is(Blocks.COBWEB)),
                        "Forest hazard fired before its deadline");
                level.getServer().setDifficulty(Difficulty.EASY, true);
                boolean sawFullWebRound = false;
                for (int attempt = 0; attempt < 64; attempt++) {
                    for (BlockPos pos : BlockPos.betweenClosed(upperMin, upperMax))
                        if (level.getBlockState(pos).is(Blocks.COBWEB)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    core.setForestHazardDelay(1);
                    core.load(core.saveWithoutMetadata());
                    level.random.setSeed(7919L * attempt + 13);
                    DungeonController.tick(level, core);
                    long webs = BlockPos.betweenClosedStream(upperMin, upperMax).filter(pos -> level.getBlockState(pos).is(Blocks.COBWEB)).count();
                    helper.assertTrue(webs <= difficulty.getId() + 2 && core.forestDifficulty() == difficulty.getId(),
                            "Forest web count or saved opening difficulty changed");
                    sawFullWebRound |= webs == difficulty.getId() + 2;
                }
                helper.assertTrue(sawFullWebRound, "Forest never placed difficulty + 2 webs in an unobstructed room");
                clean(level, core.getBlockPos());
            }
        } finally {
            clean(level, origin.offset(22, 0, 22));
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    private static void ensureReinforcement(GameTestHelper helper, DungeonCore core) {
        helper.assertTrue(core.forestReinforcements().isEmpty(), "Forest spawned reinforcements before their deadline");
        helper.assertTrue(core.forestReinforcementDelay() >= 300 && core.forestReinforcementDelay() <= 599,
                "Forest reinforcement interval changed");
        int hazardDelay = core.forestHazardDelay();
        core.setForestReinforcementDelay(2);
        DungeonController.tick(helper.getLevel(), core);
        helper.assertTrue(core.forestReinforcements().isEmpty(), "Forest reinforcement spawned early");
        DungeonController.tick(helper.getLevel(), core);
        helper.assertTrue(core.forestReinforcements().size() == 4 && core.forestReinforcementDelay() >= 300
                        && core.forestReinforcementDelay() <= 599, "Forest did not spawn exactly four scheduled reinforcements");
        var firstWave = core.forestReinforcements();
        core.setForestReinforcementDelay(1);
        DungeonController.tick(helper.getLevel(), core);
        var bothWaves = core.forestReinforcements();
        int reinforcementDelay = core.forestReinforcementDelay();
        core.load(core.saveWithoutMetadata());
        helper.assertTrue(bothWaves.size() == 8 && core.forestReinforcements().equals(bothWaves)
                && core.forestReinforcementDelay() == reinforcementDelay, "Multiple reinforcement waves lost on reload");
        bothWaves.stream().filter(uuid -> !firstWave.contains(uuid)).map(helper.getLevel()::getEntity)
                .forEach(entity -> entity.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE));
        DungeonController.tick(helper.getLevel(), core);
        helper.assertTrue(core.forestReinforcements().equals(firstWave), "Dead reinforcement stayed bound");
        core.setForestHazardDelay(hazardDelay);
    }

    private static BlockPos door(DungeonCore core) {
        return core.getBlockPos().relative(core.doorSide().orElseThrow(), core.doorDistance()).above(core.doorOffsetY());
    }

    private static long rewards(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(32), entity ->
                entity.getItem().is(ZSSRegistries.getItem("skill_orb")) && entity.getItem().hasTag()
                        && entity.getItem().getTag().getString(zeldaswordskills_remastered.item.ProgressionItem.SKILL_TAG)
                        .equals(zeldaswordskills_remastered.registry.ZSSContentIds.BONUS_HEART.toString())).size();
    }

    private static long countPillars(ServerLevel level, BlockPos min, DungeonCore core, int lo, int hi) {
        return BlockPos.betweenClosedStream(min.offset(lo, 1, lo), min.offset(hi, core.arenaHeight() - 3, hi))
                .filter(pos -> (pos.getX() - min.getX() == lo || pos.getX() - min.getX() == hi)
                        && (pos.getZ() - min.getZ() == lo || pos.getZ() - min.getZ() == hi))
                .filter(pos -> level.getBlockState(pos).is(Blocks.MOSSY_COBBLESTONE)).count();
    }

    private static void clean(ServerLevel level, BlockPos center) {
        level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(32)).forEach(Entity::discard);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(32)).forEach(Entity::discard);
    }

    private static void clearRoom(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(44, 24, 44))) {
            if (!level.isEmptyBlock(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
