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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
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
import zeldaswordskills_remastered.entity.DesertBossCreature;
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
public final class DesertGameTests {
    private DesertGameTests() {}

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssDesert")
    public static void desertLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                DungeonCore core = place(helper, origin, 0, Rotation.NONE);
                var neighbor = DungeonController.instanceId(level, core.getBlockPos().offset(20, 0, 0));
                var neighborState = ZSSWorldData.get(level).dungeons().get(neighbor);
                var first = (DesertBossCreature) DungeonController.spawnBoss(level, core).orElseThrow();
                var bosses = core.bossUuids();
                var skeletons = core.desertReinforcements();
                helper.assertTrue(bosses.size() == 2 && bosses.stream().map(level::getEntity).allMatch(entity ->
                        entity instanceof DesertBossCreature boss && !boss.isBaby() && boss.getMaxHealth() == 80 && boss.getHealth() == 80
                        && boss.getAttributeValue(Attributes.ATTACK_DAMAGE) == 6 + 2 * difficulty.getId()
                        && boss.getAttributeValue(Attributes.MOVEMENT_SPEED) == .15
                        && boss.dungeonCorePos().orElseThrow().equals(core.getBlockPos()) && boss.isPersistenceRequired()
                        && !boss.removeWhenFarAway(160.0D * 160.0D)),
                        "Desert husk count, attributes or binding changed");
                helper.assertTrue(skeletons.size() == 4 && skeletons.stream().map(level::getEntity).allMatch(entity ->
                        entity.getClass() == Skeleton.class && ((Skeleton) entity).getMaxHealth() == 20
                        && ((Skeleton) entity).getAttributeValue(Attributes.MOVEMENT_SPEED) == .25
                        && ((Skeleton) entity).getMainHandItem().is(Items.BOW)
                        && ((Skeleton) entity).getMainHandItem().getEnchantmentLevel(Enchantments.POWER_ARROWS) == 1),
                        "Desert reinforcement lost vanilla skeleton stats or Power I bow");
                DungeonController.spawnBoss(level, core);
                core.load(core.saveWithoutMetadata());
                helper.assertTrue(core.bossUuids().equals(bosses) && core.desertReinforcements().equals(skeletons),
                        "Desert duplicated or lost participants on reload");
                first.setHealth(73);
                CompoundTag bossTag = first.saveWithoutId(new CompoundTag());
                first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                int delay = core.desertHazardDelay();
                DungeonController.tick(level, core);
                helper.assertTrue(core.desertHazardDelay() == delay && !core.completed(), "Unloaded Desert boss did not pause encounter");
                var restored = ZSSRegistries.DESERT_BOSS.get().create(level);
                restored.load(bossTag);
                helper.assertTrue(restored.getHealth() == 73 && restored.getMaxHealth() == 80
                        && restored.getAttributeValue(Attributes.MOVEMENT_SPEED) == .15
                        && restored.getAttributeValue(Attributes.ATTACK_DAMAGE) == 6 + 2 * difficulty.getId()
                        && restored.dungeonCorePos().equals(first.dungeonCorePos()) && level.addFreshEntity(restored),
                        "Desert boss reload lost attributes or identity");
                var skeleton = (Mob) level.getEntity(skeletons.iterator().next());
                CompoundTag skeletonTag = skeleton.saveWithoutId(new CompoundTag());
                skeleton.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                bosses.stream().map(level::getEntity).forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
                DungeonController.tick(level, core);
                helper.assertTrue(!core.completed() && core.desertHazardDelay() == delay && rewards(level, origin) == 0
                        && DungeonController.spawnBoss(level, core).isEmpty(), "Desert completed or respawned with unloaded reinforcement");
                var loadedSkeleton = EntityType.SKELETON.create(level);
                loadedSkeleton.load(skeletonTag);
                level.addFreshEntity(loadedSkeleton);
                for (var uuid : skeletons) if (!uuid.equals(loadedSkeleton.getUUID()))
                    level.getEntity(uuid).hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                DungeonController.tick(level, core);
                helper.assertTrue(!core.completed() && core.desertReinforcements().size() == 1, "Desert ignored its last skeleton");
                loadedSkeleton.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                DungeonController.tick(level, core);
                helper.assertTrue(core.completed() && core.desertReinforcements().isEmpty() && rewards(level, origin) == 1
                        && !level.getBlockState(core.getBlockPos()).getValue(DungeonBlocks.Core.SEALED)
                        && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN)
                        && ZSSWorldData.get(level).dungeons().get(DungeonController.instanceId(level, core.getBlockPos())).completed(),
                        "Desert failed to complete, unseal and reward");
                helper.assertTrue(BlockPos.betweenClosedStream(origin, origin.offset(44, 24, 44)).noneMatch(pos ->
                        level.getBlockState(pos).is(Blocks.SOUL_SAND) || level.getBlockState(pos).is(Blocks.DISPENSER)),
                        "Desert retained temporary floor or created dispensers");
                core.load(core.saveWithoutMetadata());
                DungeonController.tick(level, core);
                helper.assertTrue(rewards(level, origin) == 1 && DungeonController.spawnBoss(level, core).isEmpty()
                        && java.util.Objects.equals(neighborState, ZSSWorldData.get(level).dungeons().get(neighbor)), "Desert duplicated or leaked rewards");
                clean(level, origin);
            }
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            DungeonCore core = place(helper, origin, 0, Rotation.NONE);
            DungeonController.spawnBoss(level, core).orElseThrow();
            var skeleton = (Mob) level.getEntity(core.desertReinforcements().iterator().next());
            CompoundTag saved = skeleton.saveWithoutId(new CompoundTag());
            skeleton.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            DungeonController.tick(level, core);
            core.load(core.saveWithoutMetadata());
            helper.assertTrue(core.desertCancelling() && core.bossUuids().isEmpty() && core.desertReinforcements().size() == 1,
                    "Desert cancellation lost unloaded reinforcement");
            level.getServer().setDifficulty(Difficulty.HARD, true);
            var restored = EntityType.SKELETON.create(level);
            restored.load(saved);
            level.addFreshEntity(restored);
            DungeonController.tick(level, core);
            helper.assertTrue(restored.isRemoved() && core.desertDifficulty() == 0 && !core.completed() && rewards(level, origin) == 0
                    && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN), "Desert cancellation resumed or rewarded");
            DungeonController.spawnBoss(level, core).orElseThrow();
            helper.assertTrue(core.bossUuids().size() == 2 && core.desertReinforcements().size() == 4, "Desert failed to restart");
            core.desertReinforcements().stream().map(level::getEntity).forEach(entity -> entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
            DungeonController.tick(level, core);
            DungeonController.spawnBoss(level, core);
            helper.assertTrue(!core.completed() && core.desertReinforcements().isEmpty(), "Desert respawned its defeated reinforcement wave");
        } finally {
            clean(level, origin); clearRoom(level, origin); level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssDesertVariants", timeoutTicks = 200)
    public static void desertVariantsAndPotions(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.runAfterDelay(20 - level.getGameTime() % 20, () -> {
            Difficulty previous = level.getDifficulty();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[Desert_Variants]"));
            level.addFreshEntity(player);
            try {
                for (int variant = 0; variant < 20; variant++) for (Rotation rotation : Rotation.values()) {
                    Difficulty opening = variant % 2 == 0 ? Difficulty.NORMAL : Difficulty.HARD;
                    level.getServer().setDifficulty(opening, true);
                    DungeonCore core = place(helper, origin, variant, rotation);
                    helper.assertTrue(core.arenaWidth() == DungeonStructureDataProvider.roomSize(variant) && core.structureRotation() == rotation, "Desert room metadata invalid");
                    var shell = new HashMap<BlockPos, BlockState>();
                    int width = core.arenaWidth(), height = core.arenaHeight();
                    for (int x = 0; x < width; x++) for (int y = 1; y < height; y++) for (int z = 0; z < width; z++) {
                        if (x != 0 && x != width - 1 && z != 0 && z != width - 1 && y != height - 1) continue;
                        BlockPos pos = DungeonArena.localPos(core, x, y, z);
                        if (!pos.equals(door(core)) && !pos.equals(door(core).above())) shell.put(pos, level.getBlockState(pos));
                    }
                    AABB room = DungeonArena.interior(core);
                    player.setPos(room.minX + .5, room.minY + 1, room.minZ + .5);
                    player.setGameMode(GameType.SPECTATOR);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().isEmpty(), "Spectator activated Desert");
                    player.setGameMode(GameType.CREATIVE);
                    DungeonController.tick(level, core);
                    helper.assertTrue(core.bossUuids().size() == 2 && core.desertReinforcements().size() == 4
                            && core.bossUuids().stream().map(level::getEntity).allMatch(level::noCollision)
                            && core.desertReinforcements().stream().map(level::getEntity).allMatch(level::noCollision),
                            "Desert safe spawn failed " + variant + "/" + rotation);
                    helper.assertTrue(level.getBlockState(door(core)).is(ZSSRegistries.SECRET_STONE_SANDSTONE.get()), "Desert door did not seal");
                    int initial = core.desertHazardDelay();
                    helper.assertTrue(initial >= 201 && initial <= 300, "Desert initial potion delay changed");
                    core.setDesertHazardDelay(2);
                    level.getServer().setDifficulty(Difficulty.EASY, true);
                    DungeonController.tick(level, core);
                    helper.assertTrue(potions(level, core).isEmpty(), "Desert fired potions early");
                    core.load(core.saveWithoutMetadata());
                    DungeonController.tick(level, core);
                    var potions = potions(level, core);
                    helper.assertTrue(potions.size() == 3 && potions.stream().map(Entity::blockPosition).distinct().count() == 3
                            && potions.stream().allMatch(potion -> potion.getDeltaMovement().y < -1.5
                            && potion.getDeltaMovement().x == 0 && potion.getDeltaMovement().z == 0
                            && potion.getItem().is(Items.SPLASH_POTION)
                            && PotionUtils.getPotion(potion.getItem()) == Potions.STRONG_HARMING),
                            "Desert did not fire three distinct downward Harming II potions");
                    int base = 300 - 50 * opening.getId();
                    helper.assertTrue(core.desertHazardDelay() >= base - 99 && core.desertHazardDelay() <= base,
                            "Desert repeat delay lost opening difficulty");
                    shell.forEach((pos, state) -> helper.assertTrue(level.getBlockState(pos).equals(state), "Desert changed wall or roof " + pos));
                    helper.assertTrue(level.getBlockState(core.getBlockPos().above(2)).is(Blocks.CHEST), "Desert destroyed central chest");
                    level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                    DungeonController.tick(level, core);
                    helper.assertTrue(!core.completed() && core.desertDifficulty() == 0 && potions(level, core).isEmpty()
                            && level.getBlockState(door(core)).getValue(LockedDoorBlock.OPEN), "Desert variant cancellation failed");
                    clean(level, origin);
                }
                level.getServer().setDifficulty(Difficulty.EASY, true);
                DungeonCore core = place(helper, origin, 0, Rotation.NONE);
                DungeonController.spawnBoss(level, core).orElseThrow();
                for (int tick = 0; tick < 350; tick++) DungeonController.tick(level, core);
                helper.assertTrue(potions(level, core).isEmpty() && core.desertHazardDelay() == 0, "Easy Desert unexpectedly enabled potion hazards");
            } finally {
                player.discard(); clean(level, origin); clearRoom(level, origin); level.getServer().setDifficulty(previous, true);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssDesertCombat")
    public static void desertMeleeAndPotionDamage(GameTestHelper helper) {
        var level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                var boss = ZSSRegistries.DESERT_BOSS.get().create(level);
                boss.finalizeSpawn(level, level.getCurrentDifficultyAt(origin), MobSpawnType.STRUCTURE, null, null);
                var target = EntityType.COW.create(level);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                target.setHealth(100);
                helper.assertTrue(boss.doHurtTarget(target) && target.getHealth() == 100 - (6 + 2 * difficulty.getId()),
                        "Desert husk melee damage did not match difficulty");
                helper.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER), "Desert husk lost vanilla Hunger attack");
            }
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            DungeonCore core = place(helper, origin, 0, Rotation.NONE);
            DungeonController.spawnBoss(level, core).orElseThrow();
            core.setDesertHazardDelay(1);
            DungeonController.tick(level, core);
            var potion = potions(level, core).get(0);
            // Isolate impact from the random arena columns and encounter participants.
            potion.setPos(origin.getX() + 7.5, origin.getY() + 10, origin.getZ() + 7.5);
            var victim = EntityType.COW.create(level);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            victim.setHealth(100);
            victim.setNoAi(true);
            victim.setNoGravity(true);
            victim.setPos(potion.getX(), potion.getY() - 2, potion.getZ());
            level.addFreshEntity(victim);
            var nearby = EntityType.COW.create(level);
            nearby.setNoAi(true);
            nearby.setNoGravity(true);
            nearby.setPos(victim.getX() + 1.5, victim.getY(), victim.getZ());
            level.addFreshEntity(nearby);
            for (int tick = 0; tick < 5 && !potion.isRemoved(); tick++) potion.tick();
            helper.assertTrue(potion.isRemoved() && victim.getHealth() == 88,
                    "Desert splash potion did not inflict 12 instant damage on direct impact: " + victim.getHealth());
            helper.assertTrue(nearby.getHealth() < nearby.getMaxHealth()
                    && nearby.getHealth() > nearby.getMaxHealth() - 12,
                    "Desert splash potion did not damage nearby targets with distance falloff");
        } finally {
            clean(level, origin); clearRoom(level, origin); level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    private static DungeonCore place(GameTestHelper helper, BlockPos origin, int variant, Rotation rotation) {
        var level = helper.getLevel();
        clearRoom(level, origin);
        var template = level.getStructureManager().get(DungeonStructureDataProvider.templateId(DungeonType.DESERT, variant)).orElseThrow();
        int center = 1 + (DungeonStructureDataProvider.roomSize(variant)) / 2;
        BlockPos pivot = new BlockPos(center, 0, center);
        helper.assertTrue(template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot), level.random, 2), "Desert placement failed");
        var core = (DungeonCore) level.getBlockEntity(origin.offset(pivot));
        ZSSWorldData.get(level).setDungeonState(DungeonController.instanceId(level, core.getBlockPos()), false, 0L);
        return core;
    }
    private static BlockPos door(DungeonCore core) {
        return core.getBlockPos().relative(core.doorSide().orElseThrow(), core.doorDistance()).above(core.doorOffsetY());
    }
    private static List<ThrownPotion> potions(ServerLevel level, DungeonCore core) {
        return level.getEntitiesOfClass(ThrownPotion.class, DungeonArena.interior(core));
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
