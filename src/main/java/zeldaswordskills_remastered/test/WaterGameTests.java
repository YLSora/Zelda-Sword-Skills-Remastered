package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
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
import zeldaswordskills_remastered.entity.WaterBossCreature;
import zeldaswordskills_remastered.entity.projectile.ThrownBomb;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.ZSSWorldData;
import zeldaswordskills_remastered.worldgen.DungeonArena;
import zeldaswordskills_remastered.worldgen.DungeonController;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class WaterGameTests {
    private WaterGameTests() {}

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssWater")
    public static void waterLifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                DungeonCore core = place(helper, origin, 0, Rotation.NONE);
                var neighbor = DungeonController.instanceId(level, core.getBlockPos().offset(20, 0, 0));
                var neighborBefore = ZSSWorldData.get(level).dungeons().get(neighbor);
                var first = (WaterBossCreature) DungeonController.spawnBoss(level, core).orElseThrow();
                var ids = core.bossUuids();
                var guardianId = core.waterReinforcement().orElseThrow();
                var guardian = (net.minecraft.world.entity.monster.ElderGuardian) level.getEntity(guardianId);
                helper.assertTrue(guardian.getClass() == net.minecraft.world.entity.monster.ElderGuardian.class
                        && guardian.getType() == EntityType.ELDER_GUARDIAN && guardian.getMaxHealth() == 80
                        && guardian.getHealth() == 80 && guardian.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8
                        && guardian.getAttributeValue(Attributes.ARMOR) == 0 && guardian.isPersistenceRequired()
                        && guardian.getAllSlots().iterator().next().isEmpty(), "Water reinforcement lost vanilla identity or attributes");
                helper.assertTrue(ids.size() == 4 && ids.stream().map(level::getEntity).allMatch(entity ->
                        entity instanceof WaterBossCreature boss && boss.getMaxHealth() == 60 * difficulty.getId()
                        && boss.getHealth() == boss.getMaxHealth() && boss.isPersistenceRequired()
                        && boss.getAttributeValue(Attributes.ATTACK_DAMAGE) == 2 + 2 * difficulty.getId()
                        && boss.dungeonCorePos().orElseThrow().equals(core.getBlockPos())
                        && boss.dungeonType().orElseThrow() == DungeonType.WATER
                        && boss.getType().is(CreatureSpawnRules.DUNGEON_ONLY)), "Water boss count, attributes or binding changed");
                var armor = switch (difficulty) {
                    case EASY -> Items.CHAINMAIL_CHESTPLATE;
                    case NORMAL -> Items.IRON_CHESTPLATE;
                    default -> Items.DIAMOND_CHESTPLATE;
                };
                helper.assertTrue(first.getItemBySlot(EquipmentSlot.CHEST).is(armor)
                        && first.getItemBySlot(EquipmentSlot.HEAD).isEnchanted(), "Water difficulty equipment missing");
                DungeonController.spawnBoss(level, core);
                core.load(core.saveWithoutMetadata());
                helper.assertTrue(ids.equals(core.bossUuids()) && core.waterReinforcement().orElseThrow().equals(guardianId)
                        && level.getEntitiesOfClass(net.minecraft.world.entity.monster.ElderGuardian.class, DungeonArena.interior(core)).size() == 1
                        && core.waterDifficulty() == difficulty.getId()
                        && core.waterHazardDelay() == 4800 - 600 * difficulty.getId(), "Water activation or reload changed opening state");
                int delay = core.waterHazardDelay();
                DungeonController.tick(level, core);
                helper.assertTrue(core.waterHazardDelay() == delay - 1 && sands(level, core).isEmpty(), "Water hazard began too early");

                first.setHealth(first.getHealth() - 7);
                CompoundTag saved = first.saveWithoutId(new CompoundTag());
                first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                ids.stream().filter(uuid -> !uuid.equals(first.getUUID())).map(level::getEntity)
                        .forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
                DungeonController.tick(level, core);
                helper.assertTrue(!core.completed() && core.bossUuids().equals(java.util.Set.of(first.getUUID()))
                        && core.waterHazardDelay() == delay - 1 && rewards(level, origin) == 0
                        && DungeonController.spawnBoss(level, core).isEmpty(), "Unloaded Water boss advanced, respawned or completed");
                level.getServer().setDifficulty(Difficulty.EASY, true);
                var restored = (WaterBossCreature) ZSSRegistries.WATER_BOSS.get().create(level);
                restored.load(saved);
                helper.assertTrue(restored.getMaxHealth() == 60 * difficulty.getId()
                        && restored.getHealth() == 60 * difficulty.getId() - 7
                        && restored.getAttributeValue(Attributes.ATTACK_DAMAGE) == 2 + 2 * difficulty.getId()
                        && restored.dungeonCorePos().equals(first.dungeonCorePos()), "Water entity reload lost state");
                helper.assertTrue(level.addFreshEntity(restored), "Water boss failed reload");
                core.setWaterHazardDelay(2);
                DungeonController.tick(level, core);
                helper.assertTrue(sands(level, core).isEmpty(), "Water recurring sandfall was early");
                DungeonController.tick(level, core);
                helper.assertTrue(!sands(level, core).isEmpty() && core.waterHazardDelay() >= 100 - 20 * difficulty.getId()
                        && core.waterHazardDelay() <= 159 - 20 * difficulty.getId(), "Water sandfall lost opening difficulty");
                int airborne = sands(level, core).size();
                core.load(core.saveWithoutMetadata());
                core.setWaterHazardDelay(1);
                DungeonController.tick(level, core);
                helper.assertTrue(sands(level, core).size() > airborne, "Water did not repeat sandfall");
                BlockPos deposited = DungeonArena.localPos(core, 1, 1, 1);
                level.setBlock(deposited, Blocks.SAND.defaultBlockState(), 2);
                var unrelated = EntityType.ZOMBIE.create(level);
                unrelated.setPos(Vec3.atCenterOf(core.getBlockPos().above(3)));
                level.addFreshEntity(unrelated);
                restored.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                DungeonController.tick(level, core);
                helper.assertTrue(!core.completed() && core.bossUuids().isEmpty() && guardian.isAlive()
                        && core.waterReinforcement().orElseThrow().equals(guardianId) && rewards(level, origin) == 0
                        && DungeonController.spawnBoss(level, core).isEmpty(), "Water ignored its living reinforcement or spawned a second wave");
                guardian.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                DungeonController.tick(level, core);
                helper.assertTrue(core.completed() && sands(level, core).isEmpty() && level.getBlockState(deposited).is(Blocks.WATER)
                        && !level.getBlockState(core.getBlockPos()).getValue(DungeonBlocks.Core.SEALED)
                        && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN)
                        && ZSSWorldData.get(level).dungeons().get(DungeonController.instanceId(level, core.getBlockPos())).completed()
                        && rewards(level, origin) == 1 && unrelated.isAlive(), "Water victory did not clean, isolate, unseal and reward");
                helper.assertTrue(level.getBlockState(core.getBlockPos().above(2)).is(Blocks.CHEST), "Water destroyed its reward chest");
                core.load(core.saveWithoutMetadata());
                DungeonController.tick(level, core);
                helper.assertTrue(rewards(level, origin) == 1 && DungeonController.spawnBoss(level, core).isEmpty()
                        && java.util.Objects.equals(neighborBefore, ZSSWorldData.get(level).dungeons().get(neighbor)), "Water duplicated or leaked completion");
                clean(level, origin);
            }
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            DungeonCore core = place(helper, origin, 0, Rotation.NONE);
            var first = DungeonController.spawnBoss(level, core).orElseThrow();
            CompoundTag saved = first.saveWithoutId(new CompoundTag());
            first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            DungeonController.tick(level, core);
            core.load(core.saveWithoutMetadata());
            helper.assertTrue(core.waterCancelling() && core.bossUuids().size() == 1 && !core.completed(), "Water cancellation forgot unloaded boss");
            level.getServer().setDifficulty(Difficulty.HARD, true);
            var restored = ZSSRegistries.WATER_BOSS.get().create(level);
            restored.load(saved);
            level.addFreshEntity(restored);
            DungeonController.tick(level, core);
            helper.assertTrue(restored.isRemoved() && core.waterDifficulty() == 0 && !core.completed()
                    && rewards(level, origin) == 0 && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN), "Water cancellation resumed or rewarded");
            DungeonController.spawnBoss(level, core).orElseThrow();
            helper.assertTrue(core.bossUuids().size() == 4 && core.waterDifficulty() == 3, "Water could not restart");
        } finally {
            clean(level, origin);
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssWater", timeoutTicks = 200)
    public static void waterVariantsAndRotations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(20 - level.getGameTime() % 20, () -> {
            Difficulty previous = level.getDifficulty();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[Water_Variants]"));
            level.addFreshEntity(player);
            try {
                for (int variant = 0; variant < 20; variant++) for (Rotation rotation : Rotation.values()) {
                    level.getServer().setDifficulty(Difficulty.NORMAL, true);
                    DungeonCore core = place(helper, origin, variant, rotation);
                    helper.assertTrue(core.arenaWidth() == DungeonStructureDataProvider.roomSize(variant) && core.structureRotation() == rotation
                            && level.getBlockState(door(core)).is(ZSSRegistries.DOOR_BOSS_WATER.get()), "Water template metadata invalid");
                    core.load(core.saveWithoutMetadata());
                    var shell = new HashMap<BlockPos, BlockState>();
                    int width = core.arenaWidth(), height = core.arenaHeight();
                    for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) for (int z = 0; z < width; z++) {
                        if (x != 0 && x != width - 1 && z != 0 && z != width - 1 && y != 0 && y != height - 1) continue;
                        BlockPos pos = DungeonArena.localPos(core, x, y, z);
                        if (!pos.equals(core.getBlockPos()) && !pos.equals(door(core)) && !pos.equals(door(core).above()))
                            shell.put(pos, level.getBlockState(pos));
                    }
                    AABB room = DungeonArena.interior(core);
                    player.setPos(room.minX + .5, room.minY + 2, room.minZ + .5);
                    player.setGameMode(GameType.SPECTATOR);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().isEmpty(), "Spectator activated Water");
                    player.setGameMode(GameType.CREATIVE);
                    DungeonController.tick(level, core);
                    var guardian = level.getEntity(core.waterReinforcement().orElseThrow());
                    helper.assertTrue(core.bossUuids().size() == 4
                            && core.bossUuids().stream().map(level::getEntity).allMatch(level::noCollision)
                            && guardian.getType() == EntityType.ELDER_GUARDIAN && level.noCollision(guardian)
                            && room.contains(guardian.position()), "Water activation or safe spawning failed: " + variant + "/" + rotation);
                    helper.assertTrue(level.getBlockState(door(core)).is(ZSSRegistries.SECRET_STONE_SANDSTONE.get()), "Water did not seal real door");
                    BlockPos hinder = core.getBlockPos().relative(core.doorSide().orElseThrow()).above(2);
                    helper.assertTrue(level.getBlockState(hinder).is(Blocks.WATER), "Water reward obstruction not removed");
                    core.setWaterHazardDelay(1);
                    DungeonController.tick(level, core);
                    helper.assertTrue(!sands(level, core).isEmpty() && sands(level, core).stream().allMatch(sand ->
                            room.contains(sand.position()) && sand.getBlockState().is(Blocks.SAND)), "Water sandfall escaped room");
                    shell.forEach((pos, state) -> helper.assertTrue(level.getBlockState(pos).equals(state), "Water changed shell " + pos));
                    level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                    DungeonController.tick(level, core);
                    helper.assertTrue(!core.completed() && core.waterDifficulty() == 0 && sands(level, core).isEmpty()
                            && guardian.isRemoved() && core.waterReinforcement().isEmpty()
                            && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN), "Water rotated cancellation failed");
                    clean(level, origin);
                }
                DungeonCore core = place(helper, origin, 0, Rotation.NONE);
                var tag = core.saveWithoutMetadata();
                tag.remove("arena_width");
                core.load(tag);
                level.getServer().setDifficulty(Difficulty.NORMAL, true);
                helper.assertTrue(DungeonController.spawnBoss(level, core).isEmpty(), "Water accepted obsolete template metadata");
            } finally {
                player.discard();
                clean(level, origin);
                clearRoom(level, origin);
                level.getServer().setDifficulty(previous, true);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssWaterCombat")
    public static void waterBombDamageAndReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                clearRoom(level, origin);
                var boss = (WaterBossCreature) ZSSRegistries.WATER_BOSS.get().create(level);
                boss.setPos(Vec3.atCenterOf(origin.offset(2, 2, 2)));
                boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.STRUCTURE, null, null);
                level.addFreshEntity(boss);
                var target = EntityType.COW.create(level);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
                target.setHealth(200);
                target.setPos(Vec3.atCenterOf(origin.offset(5, 2, 2)));
                level.addFreshEntity(target);
                boss.performRangedAttack(target, 1);
                var bombs = level.getEntitiesOfClass(ThrownBomb.class, new AABB(origin).inflate(32));
                helper.assertTrue(bombs.size() == 1, "Water ranged attack did not create one bomb");
                ThrownBomb bomb = bombs.get(0);
                CompoundTag saved = bomb.saveWithoutId(new CompoundTag());
                float damage = switch (difficulty) { case EASY -> 6; case NORMAL -> 8; default -> 10; };
                // Vanilla triangular spread perturbs each velocity component; speed is not fixed at 1.
                double speed = bomb.getDeltaMovement().length();
                helper.assertTrue(bomb.bombKind() == ThrownBomb.BombKind.WATER && bomb.getOwner() == boss
                        && saved.getInt("fuse") == 24 - 4 * difficulty.getId() && !saved.getBoolean("griefs_blocks")
                        && saved.getFloat("damage") == damage && speed > .7 && speed < 1.3
                        && bomb.getDeltaMovement().dot(target.position().subtract(boss.position())) > 0,
                        "Water bomb attributes, ownership or launch direction changed: " + difficulty + " speed=" + speed);
                bomb.discard();
                ThrownBomb restored = ZSSRegistries.BOMB.get().create(level);
                restored.load(saved);
                restored.setPos(target.position().add(0, .5, 0));
                restored.setDeltaMovement(Vec3.ZERO);
                restored.setNoGravity(true);
                level.addFreshEntity(restored);
                BlockPos chestPos = origin.offset(5, 1, 2);
                level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
                var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
                chest.setItem(0, new net.minecraft.world.item.ItemStack(Items.DIAMOND, 3));
                BlockPos waterPos = origin.offset(5, 2, 2);
                level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 2);
                for (int tick = 1; tick < saved.getInt("fuse"); tick++) restored.tick();
                helper.assertTrue(!restored.isRemoved() && target.getHealth() == 200, "Water bomb exploded before its fuse");
                restored.tick();
                helper.assertTrue(restored.isRemoved() && Math.abs(target.getHealth() - (200 - damage)) < .01,
                        "Water bomb failed real underwater damage: " + target.getHealth() + ", expected " + (200 - damage));
                helper.assertTrue(level.getBlockEntity(chestPos) == chest && chest.getItem(0).getCount() == 3
                        && level.getBlockState(waterPos).is(Blocks.WATER), "Water bomb destroyed blocks or inventory");
                helper.assertTrue(boss.getHealth() == boss.getMaxHealth() && boss.getDeltaMovement().equals(Vec3.ZERO)
                        && !boss.hurt(level.damageSources().explosion(restored, boss), 100),
                        "Water boss took explosion damage or knockback");
                helper.assertTrue(boss.hurt(level.damageSources().generic(), 10) && boss.getHealth() < boss.getMaxHealth(),
                        "Water explosion immunity also blocked ordinary damage");
                var reloadedBoss = ZSSRegistries.WATER_BOSS.get().create(level);
                reloadedBoss.load(boss.saveWithoutId(new CompoundTag()));
                helper.assertTrue(reloadedBoss.ignoreExplosion()
                        && reloadedBoss.isInvulnerableTo(level.damageSources().explosion(restored, boss)), "Water explosion immunity lost on reload");
                clean(level, origin);
            }
        } finally {
            clean(level, origin);
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssWaterSand")
    public static void waterSandSettlesAndRestores(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            DungeonCore core = place(helper, origin, 0, Rotation.CLOCKWISE_90);
            BlockState cornerBefore = level.getBlockState(DungeonArena.localPos(core, 1, 1, 1));
            DungeonController.spawnBoss(level, core).orElseThrow();
            core.setWaterHazardDelay(1);
            DungeonController.tick(level, core);
            var first = sands(level, core).get(0);
            double startY = first.getY();
            first.tick();
            helper.assertTrue(first.getY() < startY, "Water sand did not fall under vanilla gravity");
            CompoundTag saved = first.saveWithoutId(new CompoundTag());
            first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var restored = EntityType.FALLING_BLOCK.create(level);
            restored.load(saved);
            level.addFreshEntity(restored);
            for (int tick = 0; tick < 80; tick++) sands(level, core).forEach(FallingBlockEntity::tick);
            helper.assertTrue(BlockPos.betweenClosedStream(origin, origin.offset(44, 24, 44))
                    .anyMatch(pos -> level.getBlockState(pos).is(Blocks.SAND)), "Water sand failed to settle as blocks");
            core.setWaterHazardDelay(1);
            DungeonController.tick(level, core);
            helper.assertTrue(!sands(level, core).isEmpty(), "Water second sand wave missing");
            var active = sands(level, core).get(0);
            saved = active.saveWithoutId(new CompoundTag());
            active.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            restored = EntityType.FALLING_BLOCK.create(level);
            restored.load(saved);
            level.addFreshEntity(restored);
            core.bossUuids().stream().map(level::getEntity).forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
            level.getEntity(core.waterReinforcement().orElseThrow()).hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            DungeonController.tick(level, core);
            helper.assertTrue(core.completed() && restored.isRemoved() && sands(level, core).isEmpty()
                    && BlockPos.betweenClosedStream(origin, origin.offset(44, 24, 44))
                    .noneMatch(pos -> level.getBlockState(pos).is(Blocks.SAND)), "Water victory left settled or reloaded airborne sand");
            helper.assertTrue(level.getBlockState(DungeonArena.localPos(core, 1, 1, 1)).equals(cornerBefore)
                    && level.getBlockState(core.getBlockPos().above(2)).is(Blocks.CHEST), "Water restoration damaged room contents");
        } finally {
            clean(level, origin);
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssWaterReinforcement")
    public static void waterReinforcementReloadAndCancellation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            DungeonCore core = place(helper, origin, 0, Rotation.NONE);
            DungeonController.spawnBoss(level, core).orElseThrow();
            var guardianId = core.waterReinforcement().orElseThrow();
            var guardian = (Mob) level.getEntity(guardianId);
            guardian.setHealth(67);
            CompoundTag saved = guardian.saveWithoutId(new CompoundTag());
            guardian.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            core.load(core.saveWithoutMetadata());
            int delay = core.waterHazardDelay();
            DungeonController.tick(level, core);
            helper.assertTrue(core.waterHazardDelay() == delay && core.waterReinforcement().orElseThrow().equals(guardianId),
                    "Unloaded Water reinforcement did not pause or survive core reload");
            core.bossUuids().stream().map(level::getEntity).forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
            DungeonController.tick(level, core);
            helper.assertTrue(!core.completed() && core.waterHazardDelay() == delay && rewards(level, origin) == 0
                    && DungeonController.spawnBoss(level, core).isEmpty(), "Water completed or respawned while its reinforcement was unloaded");
            var restored = EntityType.ELDER_GUARDIAN.create(level);
            restored.load(saved);
            helper.assertTrue(restored.getHealth() == 67 && restored.getMaxHealth() == 80
                    && restored.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8 && level.addFreshEntity(restored),
                    "Water reinforcement reload lost vanilla stats or health");
            restored.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            DungeonController.tick(level, core);
            helper.assertTrue(core.completed() && rewards(level, origin) == 1, "Reloaded Water reinforcement could not finish battle");
            clean(level, origin);

            core = place(helper, origin, 0, Rotation.NONE);
            DungeonController.spawnBoss(level, core).orElseThrow();
            guardian = (Mob) level.getEntity(core.waterReinforcement().orElseThrow());
            saved = guardian.saveWithoutId(new CompoundTag());
            guardian.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            DungeonController.tick(level, core);
            core.load(core.saveWithoutMetadata());
            helper.assertTrue(core.waterCancelling() && core.bossUuids().isEmpty() && core.waterReinforcement().isPresent()
                    && !core.completed(), "Water cancellation forgot unloaded reinforcement");
            level.getServer().setDifficulty(Difficulty.HARD, true);
            restored = EntityType.ELDER_GUARDIAN.create(level);
            restored.load(saved);
            level.addFreshEntity(restored);
            DungeonController.tick(level, core);
            helper.assertTrue(restored.isRemoved() && core.waterReinforcement().isEmpty() && core.waterDifficulty() == 0
                    && !core.completed() && rewards(level, origin) == 0 && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN),
                    "Water cancellation resumed or rewarded after its reinforcement returned");
            DungeonController.spawnBoss(level, core).orElseThrow();
            var restartedId = core.waterReinforcement().orElseThrow();
            helper.assertTrue(core.bossUuids().size() == 4 && !restartedId.equals(guardian.getUUID()), "Water did not create a new reinforcement on restart");
            var restarted = (Mob) level.getEntity(restartedId);
            restarted.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            DungeonController.tick(level, core);
            helper.assertTrue(!core.completed() && core.waterReinforcement().isEmpty() && core.bossUuids().size() == 4,
                    "Killing Water reinforcement first completed the battle");
            DungeonController.spawnBoss(level, core);
            helper.assertTrue(core.waterReinforcement().isEmpty(), "Dead Water reinforcement respawned in the same battle");
        } finally {
            clean(level, origin);
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    private static DungeonCore place(GameTestHelper helper, BlockPos origin, int variant, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        clearRoom(level, origin);
        var template = level.getStructureManager().get(DungeonStructureDataProvider.templateId(DungeonType.WATER, variant)).orElseThrow();
        int center = 1 + (DungeonStructureDataProvider.roomSize(variant)) / 2;
        BlockPos pivot = new BlockPos(center, 0, center);
        helper.assertTrue(template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot), level.random, 2), "Water placement failed");
        var core = (DungeonCore) level.getBlockEntity(origin.offset(pivot));
        ZSSWorldData.get(level).setDungeonState(DungeonController.instanceId(level, core.getBlockPos()), false, 0L);
        return core;
    }

    private static BlockPos door(DungeonCore core) {
        return core.getBlockPos().relative(core.doorSide().orElseThrow(), core.doorDistance()).above(core.doorOffsetY());
    }

    private static List<FallingBlockEntity> sands(ServerLevel level, DungeonCore core) {
        return level.getEntitiesOfClass(FallingBlockEntity.class, DungeonArena.interior(core));
    }

    private static long rewards(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(origin.offset(22, 3, 22)).inflate(32), entity ->
                entity.getItem().is(ZSSRegistries.getItem("skill_orb")) && entity.getItem().hasTag()
                        && entity.getItem().getTag().getString(zeldaswordskills_remastered.item.ProgressionItem.SKILL_TAG)
                        .equals(zeldaswordskills_remastered.registry.ZSSContentIds.BONUS_HEART.toString())).size();
    }

    private static void clean(ServerLevel level, BlockPos origin) {
        level.getEntitiesOfClass(Entity.class, new AABB(origin.offset(22, 3, 22)).inflate(32), entity ->
                !(entity instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
    }

    private static void clearRoom(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(44, 24, 44)))
            if (!level.isEmptyBlock(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }
}
