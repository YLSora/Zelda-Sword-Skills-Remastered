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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
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
import zeldaswordskills_remastered.entity.IceBossCreature;
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
public final class IceGameTests {
    private IceGameTests() {}

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssIce")
    public static void iceLifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                DungeonCore core = place(helper, origin, 0, Rotation.NONE);
                var neighborId = DungeonController.instanceId(level, core.getBlockPos().offset(20, 0, 0));
                var neighborBefore = ZSSWorldData.get(level).dungeons().get(neighborId);
                var first = (IceBossCreature) DungeonController.spawnBoss(level, core).orElseThrow();
                var ids = core.bossUuids();
                spawnReinforcements(helper, core);
                helper.assertTrue(ids.size() == 2 && ids.stream().map(level::getEntity).allMatch(entity ->
                        entity instanceof IceBossCreature boss && boss instanceof net.minecraft.world.entity.monster.Illusioner && boss.getMaxHealth() == (75 + 25 * difficulty.getId())
                        && boss.getHealth() == boss.getMaxHealth() && boss.isPersistenceRequired()
                        && boss.getAttributeValue(Attributes.ATTACK_DAMAGE) == 12
                        && boss.dungeonCorePos().orElseThrow().equals(core.getBlockPos())
                        && boss.dungeonType().orElseThrow() == DungeonType.ICE
                        && boss.getType().is(CreatureSpawnRules.DUNGEON_ONLY)), "Ice count, identity or health changed");
                var chest = switch (difficulty) {
                    case EASY -> Items.CHAINMAIL_CHESTPLATE;
                    case NORMAL -> Items.IRON_CHESTPLATE;
                    default -> Items.DIAMOND_CHESTPLATE;
                };
                helper.assertTrue(first.getItemBySlot(EquipmentSlot.CHEST).is(chest)
                        && first.getItemBySlot(EquipmentSlot.HEAD).isEnchanted()
                        && first.getItemBySlot(EquipmentSlot.LEGS).isEnchanted()
                        && first.getItemBySlot(EquipmentSlot.FEET).isEnchanted(), "Ice difficulty armor missing");
                var bow = first.getMainHandItem();
                helper.assertTrue(bow.is(Items.BOW)
                        && bow.getEnchantmentLevel(Enchantments.POWER_ARROWS) == 2 * difficulty.getId() - 1
                        && bow.getEnchantmentLevel(Enchantments.PUNCH_ARROWS) == difficulty.getId() - 1
                        && bow.getEnchantmentLevel(Enchantments.FLAMING_ARROWS) == (difficulty == Difficulty.HARD ? 1 : 0),
                        "Ice bow enchantments changed");
                DungeonController.spawnBoss(level, core);
                core.load(core.saveWithoutMetadata());
                helper.assertTrue(ids.equals(core.bossUuids()) && core.iceDifficulty() == difficulty.getId(),
                        "Ice duplicate activation or core reload changed participants");

                first.setHealth(first.getHealth() - 7);
                CompoundTag saved = first.saveWithoutId(new CompoundTag());
                first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                ids.stream().filter(uuid -> !uuid.equals(first.getUUID())).map(level::getEntity)
                        .forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
                DungeonController.tick(level, core);
                helper.assertTrue(!core.completed() && core.bossUuids().equals(java.util.Set.of(first.getUUID()))
                        && rewards(level, origin) == 0 && DungeonController.spawnBoss(level, core).isEmpty(),
                        "Unloaded Ice boss caused respawn or premature victory");
                level.getServer().setDifficulty(Difficulty.EASY, true);
                var restored = (IceBossCreature) ZSSRegistries.ICE_BOSS.get().create(level);
                restored.load(saved);
                helper.assertTrue(restored.getMaxHealth() == (75 + 25 * difficulty.getId())
                        && restored.getAttributeValue(Attributes.ATTACK_DAMAGE) == 12
                        && restored.getHealth() == (75 + 25 * difficulty.getId()) - 7
                        && restored.getMainHandItem().equals(bow, false)
                        && restored.dungeonCorePos().equals(first.dungeonCorePos()), "Ice entity reload lost state");
                helper.assertTrue(level.addFreshEntity(restored), "Ice participant failed reload");
                var unrelated = EntityType.ZOMBIE.create(level);
                unrelated.setPos(Vec3.atCenterOf(core.getBlockPos().above(3)));
                level.addFreshEntity(unrelated);
                restored.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                core.setIceReinforcementDelay(1);
                DungeonController.tick(level, core);
                helper.assertTrue(!core.completed() && core.iceReinforcements().size() == 8
                        && core.iceReinforcementDelay() == 1 && rewards(level, origin) == 0,
                        "Ice finished or continued spawning before reinforcements died");
                core.iceReinforcements().stream().map(level::getEntity)
                        .forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
                DungeonController.tick(level, core);
                helper.assertTrue(core.completed() && !level.getBlockState(core.getBlockPos()).getValue(DungeonBlocks.Core.SEALED)
                        && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN)
                        && ZSSWorldData.get(level).dungeons().get(DungeonController.instanceId(level, core.getBlockPos())).completed()
                        && rewards(level, origin) == 1 && unrelated.isAlive(), "Ice victory did not isolate, unseal and reward");
                helper.assertTrue(BlockPos.betweenClosedStream(core.getBlockPos().offset(-core.arenaRadius(), core.arenaHeight(), -core.arenaRadius()),
                        core.getBlockPos().offset(core.arenaRadius(), core.arenaHeight(), core.arenaRadius())).anyMatch(pos ->
                        level.getBlockState(pos).getBlock() instanceof zeldaswordskills_remastered.block.MechanismBlocks.AncientTablet),
                        "Ice victory tablet missing from roof");
                helper.assertTrue(level.getBlockState(core.getBlockPos().above()).is(Blocks.QUARTZ_BLOCK)
                                && level.getBlockState(core.getBlockPos().above(2)).isAir(),
                        "Ice changed its central quartz platform");
                core.load(core.saveWithoutMetadata());
                DungeonController.tick(level, core);
                helper.assertTrue(rewards(level, origin) == 1 && DungeonController.spawnBoss(level, core).isEmpty()
                        && java.util.Objects.equals(neighborBefore, ZSSWorldData.get(level).dungeons().get(neighborId)),
                        "Ice completion duplicated or leaked");
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
            helper.assertTrue(core.iceCancelling() && core.bossUuids().size() == 1 && !core.completed(),
                    "Ice cancellation forgot unloaded participant");
            level.getServer().setDifficulty(Difficulty.HARD, true);
            var restored = ZSSRegistries.ICE_BOSS.get().create(level);
            restored.load(saved);
            level.addFreshEntity(restored);
            DungeonController.tick(level, core);
            helper.assertTrue(restored.isRemoved() && core.iceDifficulty() == 0 && !core.completed()
                    && rewards(level, origin) == 0 && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN),
                    "Ice cancellation resumed or rewarded after difficulty changed");
            DungeonController.spawnBoss(level, core).orElseThrow();
            helper.assertTrue(core.bossUuids().size() == 2 && core.iceDifficulty() == 3, "Ice failed to restart");
        } finally {
            clean(level, origin);
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssIce", timeoutTicks = 200)
    public static void iceVariantsAndRotations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(20 - level.getGameTime() % 20, () -> {
            Difficulty previous = level.getDifficulty();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[Ice_Variants]"));
            level.addFreshEntity(player);
            long[] blueIce = new long[1];
            try {
                for (int variant = 0; variant < 20; variant++) for (Rotation rotation : Rotation.values()) {
                    level.getServer().setDifficulty(Difficulty.NORMAL, true);
                    DungeonCore core = place(helper, origin, variant, rotation);
                    helper.assertTrue(core.arenaWidth() == DungeonStructureDataProvider.roomSize(variant) && core.structureRotation() == rotation
                            && level.getBlockState(door(core)).is(ZSSRegistries.DOOR_BOSS_ICE.get()), "Ice template metadata invalid");
                    core.load(core.saveWithoutMetadata());
                    helper.assertTrue(BlockPos.betweenClosedStream(origin, origin.offset(44, 24, 44))
                            .noneMatch(pos -> level.getBlockState(pos).is(Blocks.ICE)), "Ice template still contains ordinary ice");
                    blueIce[0] += BlockPos.betweenClosedStream(origin, origin.offset(44, 24, 44))
                            .filter(pos -> level.getBlockState(pos).is(Blocks.BLUE_ICE)).count();
                    var before = new HashMap<BlockPos, BlockState>();
                    BlockPos.betweenClosedStream(origin, origin.offset(44, 24, 44))
                            .forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
                    AABB room = DungeonArena.interior(core);
                    player.setPos(room.minX + .5, room.minY + 2, room.minZ + .5);
                    player.setGameMode(GameType.SPECTATOR);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().isEmpty(), "Spectator activated Ice");
                    player.setGameMode(GameType.CREATIVE);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().size() == 2
                            && core.bossUuids().stream().map(level::getEntity).allMatch(level::noCollision),
                            "Ice variant failed automatic activation or collided: " + variant + "/" + rotation);
                    helper.assertTrue(level.getBlockState(door(core)).is(ZSSRegistries.SECRET_STONE_ICE.get()), "Ice did not seal door");
                    BlockPos hinder = core.getBlockPos().relative(core.doorSide().orElseThrow()).above(core.doorOffsetY() + 1);
                    for (int tick = 0; tick < 650; tick++) DungeonController.tick(level, core);
                    helper.assertTrue(core.iceReinforcements().size() >= 4 && core.iceReinforcements().size() % 4 == 0
                            && core.iceReinforcements().stream().map(level::getEntity).allMatch(entity ->
                            entity.getType() == EntityType.STRAY && level.noCollision(entity)), "Ice variant failed safe stray reinforcement");
                    before.forEach((pos, state) -> {
                        if (!pos.equals(door(core)) && !pos.equals(door(core).above()) && !pos.equals(hinder))
                            helper.assertTrue(level.getBlockState(pos).equals(state), "Ice unexpectedly changed room block " + pos);
                    });
                    level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                    DungeonController.tick(level, core);
                    helper.assertTrue(!core.completed() && core.iceDifficulty() == 0
                            && level.getBlockState(door(core)).is(ZSSRegistries.DOOR_BOSS_ICE.get())
                            && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN), "Ice failed rotated door restoration");
                    clean(level, origin);
                }
                helper.assertTrue(blueIce[0] > 0, "Ice templates never placed blue ice");
                DungeonCore core = place(helper, origin, 0, Rotation.NONE);
                var tag = core.saveWithoutMetadata();
                tag.remove("arena_width");
                core.load(tag);
                level.getServer().setDifficulty(Difficulty.NORMAL, true);
                helper.assertTrue(DungeonController.spawnBoss(level, core).isEmpty(), "Ice accepted obsolete room metadata");
            } finally {
                player.discard();
                clean(level, origin);
                clearRoom(level, origin);
                level.getServer().setDifficulty(previous, true);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssIceCombat", timeoutTicks = 400)
    public static void iceBowCombat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.HARD, true);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        clearRoom(level, origin);
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(14, 0, 14))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        var boss = (IceBossCreature) ZSSRegistries.ICE_BOSS.get().create(level);
        boss.moveTo(Vec3.atCenterOf(origin.offset(3, 1, 5)));
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        var victim = EntityType.COW.create(level);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        victim.setHealth(1000);
        victim.setNoAi(true);
        victim.moveTo(Vec3.atCenterOf(origin.offset(9, 1, 5)));
        level.addFreshEntity(boss);
        level.addFreshEntity(victim);
        boss.setTarget(victim);
        boolean[] observed = new boolean[4];
        helper.onEachTick(() -> {
            observed[0] |= boss.isUsingItem();
            observed[3] |= boss.isInvisible();
            for (var arrow : level.getEntitiesOfClass(AbstractArrow.class, new AABB(origin).inflate(25), arrow -> arrow.getOwner() == boss)) {
                observed[1] = true;
                helper.assertTrue(arrow.getBaseDamage() == 15, "Ice arrow lost attack damage 12 plus Power V");
                observed[2] |= arrow.getKnockback() == 2 && arrow.isOnFire();
            }
        });
        helper.runAfterDelay(240, () -> {
            try {
                helper.assertTrue(observed[0] && observed[1] && victim.getHealth() < 1000,
                        "Ice bow AI failed: draw=" + observed[0] + ", arrow=" + observed[1] + ", health=" + victim.getHealth());
                helper.assertTrue(observed[2] && observed[3], "Ice illusioner lost its mirror spell or enchanted arrows");
                helper.assertTrue(level.getServer().getLootData().getLootTable(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "entities/ice_boss"))
                        != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "Ice loot table missing");
                helper.succeed();
            } finally {
                clean(level, origin);
                clearRoom(level, origin);
                level.getServer().setDifficulty(previous, true);
            }
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssIce")
    public static void iceReinforcementPersistence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            DungeonCore core = place(helper, origin, 0, Rotation.NONE);
            DungeonController.spawnBoss(level, core).orElseThrow();
            spawnReinforcements(helper, core);
            var stray = (net.minecraft.world.entity.monster.Stray) level.getEntity(core.iceReinforcements().iterator().next());
            var victim = EntityType.COW.create(level);
            victim.setPos(Vec3.atCenterOf(origin.offset(7, 10, 4)));
            victim.setNoAi(true);
            victim.setNoGravity(true);
            level.addFreshEntity(victim);
            stray.performRangedAttack(victim, 1);
            var arrow = level.getEntitiesOfClass(AbstractArrow.class, new AABB(origin.offset(22, 3, 22)).inflate(32),
                    shot -> shot.getOwner() == stray).get(0);
            arrow.setPos(victim.getX() - 2, victim.getY() + .5, victim.getZ());
            arrow.setDeltaMovement(1, 0, 0);
            for (int tick = 0; tick < 4 && !arrow.isRemoved(); tick++) arrow.tick();
            helper.assertTrue(victim.getHealth() < victim.getMaxHealth()
                    && victim.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                    "Stray reinforcement arrow lost real damage or slowness");
            var ids = core.iceReinforcements();
            CompoundTag saved = stray.saveWithoutId(new CompoundTag());
            stray.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            core.setIceReinforcementDelay(1);
            core.load(core.saveWithoutMetadata());
            DungeonController.tick(level, core);
            helper.assertTrue(core.iceReinforcementDelay() == 1 && core.iceReinforcements().equals(ids) && !core.completed(),
                    "Unloaded stray advanced Ice timer or duplicated a wave");
            level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            DungeonController.tick(level, core);
            core.load(core.saveWithoutMetadata());
            helper.assertTrue(core.iceCancelling() && core.bossUuids().isEmpty()
                    && core.iceReinforcements().equals(java.util.Set.of(stray.getUUID())),
                    "Ice cancellation forgot unloaded stray");
            level.getServer().setDifficulty(Difficulty.HARD, true);
            helper.assertTrue(DungeonController.spawnBoss(level, core).isEmpty(), "Ice restarted before cancellation finished");
            var restored = EntityType.STRAY.create(level);
            restored.load(saved);
            level.addFreshEntity(restored);
            DungeonController.tick(level, core);
            helper.assertTrue(restored.isRemoved() && core.iceReinforcements().isEmpty()
                    && core.iceDifficulty() == 0 && !core.completed() && rewards(level, origin) == 0,
                    "Ice rewarded or resumed a cancelled reinforcement wave");
        } finally {
            clean(level, origin);
            clearRoom(level, origin);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    private static void spawnReinforcements(GameTestHelper helper, DungeonCore core) {
        var level = helper.getLevel();
        helper.assertTrue(core.iceReinforcements().isEmpty() && core.iceReinforcementDelay() >= 300
                && core.iceReinforcementDelay() <= 599, "Ice initial reinforcement interval changed");
        core.setIceReinforcementDelay(2);
        DungeonController.tick(level, core);
        helper.assertTrue(core.iceReinforcements().isEmpty(), "Ice reinforcement arrived early");
        DungeonController.tick(level, core);
        helper.assertTrue(core.iceReinforcements().size() == 4, "Ice did not spawn four scheduled strays");
        core.setIceReinforcementDelay(1);
        DungeonController.tick(level, core);
        var ids = core.iceReinforcements();
        int delay = core.iceReinforcementDelay();
        core.load(core.saveWithoutMetadata());
        helper.assertTrue(ids.size() == 8 && core.iceReinforcements().equals(ids)
                && core.iceReinforcementDelay() == delay && delay >= 300 && delay <= 599,
                "Ice multiple waves or countdown did not survive reload");
        helper.assertTrue(ids.stream().map(level::getEntity).allMatch(entity ->
                entity instanceof net.minecraft.world.entity.monster.Stray stray && stray.getMaxHealth() == 20
                && stray.getMainHandItem().is(Items.BOW) && stray.isPersistenceRequired()), "Ice reinforcement lost vanilla attributes");
    }

    private static DungeonCore place(GameTestHelper helper, BlockPos origin, int variant, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        clearRoom(level, origin);
        var template = level.getStructureManager().get(DungeonStructureDataProvider.templateId(DungeonType.ICE, variant)).orElseThrow();
        int center = 1 + (DungeonStructureDataProvider.roomSize(variant)) / 2;
        BlockPos pivot = new BlockPos(center, 0, center);
        helper.assertTrue(template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot), level.random, 2), "Ice placement failed");
        var core = (DungeonCore) level.getBlockEntity(origin.offset(pivot));
        ZSSWorldData.get(level).setDungeonState(DungeonController.instanceId(level, core.getBlockPos()), false, 0L);
        return core;
    }

    private static BlockPos door(DungeonCore core) {
        return core.getBlockPos().relative(core.doorSide().orElseThrow(), core.doorDistance()).above(core.doorOffsetY());
    }

    private static long rewards(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(origin.offset(22, 3, 22)).inflate(32), entity ->
                entity.getItem().is(ZSSRegistries.getItem("skill_orb")) && entity.getItem().hasTag()
                        && entity.getItem().getTag().getString(zeldaswordskills_remastered.item.ProgressionItem.SKILL_TAG)
                        .equals(zeldaswordskills_remastered.registry.ZSSContentIds.BONUS_HEART.toString())).size();
    }

    private static void clean(ServerLevel level, BlockPos origin) {
        AABB box = new AABB(origin.offset(22, 3, 22)).inflate(32);
        level.getEntitiesOfClass(Mob.class, box).forEach(Entity::discard);
        level.getEntitiesOfClass(ItemEntity.class, box).forEach(Entity::discard);
        level.getEntitiesOfClass(AbstractArrow.class, box).forEach(Entity::discard);
        level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box).forEach(Entity::discard);
    }

    private static void clearRoom(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(44, 24, 44)))
            if (!level.isEmptyBlock(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }
}
