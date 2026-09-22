package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import zeldaswordskills_remastered.block.LockedDoorBlock;
import zeldaswordskills_remastered.block.MechanismBlocks;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;
import zeldaswordskills_remastered.entity.ForestBossCreature;
import zeldaswordskills_remastered.entity.LegacyCreatureDrops;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import static zeldaswordskills_remastered.worldgen.DungeonArena.*;

/** ForestBattle's web and pillar hazards, advanced only while its owned participants are loaded. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID)
public final class ForestEncounter {
    private static final String REINFORCEMENT_CORE = "zss_forest_reinforcement_core";
    private ForestEncounter() {}

    public static void begin(ServerLevel level, DungeonCore core) {
        core.beginForestBattle(level.getDifficulty().getId(), 300 + level.random.nextInt(300));
        core.setForestReinforcementDelay(300 + level.random.nextInt(300));
        setDoor(level, core, false);
        int last = core.arenaWidth() - 2;
        for (int x = 1; x <= last; x++) for (int z = 1; z <= last; z++) {
            if (x != 1 && x != last && z != 1 && z != last) continue;
            for (int y = 1; y <= 3; y++) {
                BlockPos pos = localPos(core, x, y, z);
                var state = level.getBlockState(pos);
                if (!state.isSolid() && level.getBlockEntity(pos) == null)
                    level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
            }
        }
    }

    private static void spawnReinforcement(ServerLevel level, DungeonCore core, int corner, java.util.UUID waveId) {
        var spider = ZSSRegistries.SKULLTULA.get().create(level);
        if (spider == null || !positionAtCorner(level, core, spider, corner)) return;
        spider.finalizeSpawn(level, level.getCurrentDifficultyAt(spider.blockPosition()), MobSpawnType.EVENT, null, null);
        spider.setPersistenceRequired();
        spider.getPersistentData().putLong(REINFORCEMENT_CORE, core.getBlockPos().asLong());
        DungeonController.markReinforcement(spider, waveId);
        if (level.addFreshEntity(spider)) core.addForestReinforcement(spider.getUUID());
    }

    public static void tick(ServerLevel level, DungeonCore core) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) core.cancelForestBattle();
        if (core.forestCancelling()) {
            for (var uuid : core.bossUuids()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeBoss(uuid); }
            }
            for (var uuid : core.forestReinforcements()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeForestReinforcement(uuid); }
            }
            if (core.bossUuids().isEmpty() && core.forestReinforcements().isEmpty()) {
                setDoor(level, core, true);
                core.resetForestBattle();
            }
            return;
        }
        for (var uuid : core.forestReinforcements()) {
            if (level.getEntity(uuid) instanceof net.minecraft.world.entity.LivingEntity living && living.isDeadOrDying())
                core.removeForestReinforcement(uuid);
        }
        if (core.bossUuids().isEmpty() && core.forestReinforcements().isEmpty()) {
            DungeonController.finishEncounter(level, core, DungeonType.FOREST);
            BlockPos pos = core.getBlockPos().above(2);
            level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5,
                    LegacyCreatureDrops.bonusHeartOrb()));
            return;
        }
        if (core.bossUuids().stream().anyMatch(uuid -> level.getEntity(uuid) == null)
                || core.forestReinforcements().stream().anyMatch(uuid -> level.getEntity(uuid) == null)) return;
        if (!core.bossUuids().isEmpty()) {
            core.setForestReinforcementDelay(core.forestReinforcementDelay() - 1);
            if (core.forestReinforcementDelay() <= 0) {
                DungeonController.beginReinforcementWave(level, core.forestReinforcements(), 4).ifPresent(waveId -> {
                    for (int corner = 0; corner < 4; corner++) spawnReinforcement(level, core, corner, waveId);
                });
                core.setForestReinforcementDelay(300 + level.random.nextInt(300));
            }
        }
        core.setForestHazardDelay(core.forestHazardDelay() - 1);
        if (core.forestHazardDelay() > 0) return;
        boolean webs = level.random.nextInt(4) < 3;
        if (webs) for (int i = 0; i < core.forestDifficulty() + 2; i++) placeRandomWeb(level, core);
        if (!webs || level.random.nextInt(2) == 0) destroyPillar(level, core);
        core.setForestHazardDelay(100 + level.random.nextInt(500));
    }

    private static void placeRandomWeb(ServerLevel level, DungeonCore core) {
        BlockPos pos = localPos(core, 1 + level.random.nextInt(core.arenaWidth() - 1),
                3 + level.random.nextInt(core.arenaHeight() - 3), 1 + level.random.nextInt(core.arenaWidth() - 1));
        if (level.isEmptyBlock(pos) && interior(core).contains(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5)) {
            level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
            level.playSound(null, pos, ZSSRegistries.WEB_SPLAT.get(), SoundSource.BLOCKS, 1, 1);
        }
    }

    private static void destroyPillar(ServerLevel level, DungeonCore core) {
        BlockPos min = minimum(core);
        int corner = level.random.nextInt(4);
        int lo = 3;
        int hi = core.arenaWidth() - 4;
        BlockPos pos = localPos(core, corner < 2 ? lo : hi, 1 + level.random.nextInt(3), (corner & 1) == 0 ? lo : hi);
        if (level.isEmptyBlock(pos)) return;
        if (core.forestDifficulty() == 3)
            level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 3, Level.ExplosionInteraction.NONE);
        level.playSound(null, pos, ZSSRegistries.ROCK_FALL.get(), SoundSource.BLOCKS, 1, 1);
        for (BlockPos part : BlockPos.betweenClosed(pos.offset(-1, 0, -1),
                new BlockPos(pos.getX() + 1, min.getY() + core.arenaHeight() - 3, pos.getZ() + 1))) {
            BlockPos center = core.getBlockPos();
            if (!interior(core).contains(part.getX() + .5, part.getY() + .5, part.getZ() + .5)
                    || Math.abs(part.getX() - center.getX()) <= 1 && Math.abs(part.getZ() - center.getZ()) <= 1
                    || level.getBlockEntity(part) != null || level.getBlockState(part).getDestroySpeed(level, part) < 0) continue;
            level.destroyBlock(part, false);
        }
    }

    private static void setDoor(ServerLevel level, DungeonCore core, boolean open) {
        if (!validRoom(core)) return;
        Direction side = core.doorSide().orElseThrow();
        BlockPos lower = core.getBlockPos().relative(side, core.doorDistance()).above(core.doorOffsetY());
        for (BlockPos pos : java.util.List.of(lower, lower.above())) {
            var state = open ? ZSSRegistries.DOOR_BOSS_FOREST.get().defaultBlockState()
                    .setValue(LockedDoorBlock.FACING, side)
                    .setValue(LockedDoorBlock.HALF, pos.equals(lower) ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                    .setValue(LockedDoorBlock.OPEN, true).setValue(LockedDoorBlock.UNLOCKED, true)
                    : ZSSRegistries.SECRET_STONE_MOSSY_COBBLESTONE.get().defaultBlockState()
                    .setValue(MechanismBlocks.SecretStone.UNBREAKABLE, true);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void finish(ServerLevel level, DungeonCore core) {
        setDoor(level, core, true);
        openCeiling(level, core);
        DungeonVictory.reward(level, core, core.forestDifficulty());
        core.resetForestBattle();
    }

    /** Opens the post-victory skylight directly above the central reward or pedestal. */
    private static void openCeiling(ServerLevel level, DungeonCore core) {
        int radius = 5;
        int center = core.arenaWidth() / 2;
        int roof = core.arenaHeight() - 1;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius) continue;
            for (int y = roof - 3; y <= roof; y++) {
                BlockPos pos = localPos(core, center + dx, y, center + dz);
                if (level.getBlockEntity(pos) != null) continue;
                // Remove the old central glowstone chandelier below the new skylight.
                if (level.getBlockState(pos).is(Blocks.GLOWSTONE) || y == roof) level.removeBlock(pos, false);
            }
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void participantLeaves(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        boolean killed = entity.getRemovalReason() == Entity.RemovalReason.KILLED;
        boolean peaceful = entity.getRemovalReason() == Entity.RemovalReason.DISCARDED
                && level.getDifficulty() == Difficulty.PEACEFUL;
        if (!killed && !peaceful) return;
        BlockPos pos = entity instanceof ForestBossCreature boss ? boss.dungeonCorePos().orElse(null)
                : entity.getPersistentData().contains(REINFORCEMENT_CORE, net.minecraft.nbt.Tag.TAG_LONG)
                ? BlockPos.of(entity.getPersistentData().getLong(REINFORCEMENT_CORE)) : null;
        if (pos == null || !(level.getBlockEntity(pos) instanceof DungeonCore core)
                || core.dungeonType().orElse(null) != DungeonType.FOREST) return;
        if (!core.bossUuids().contains(entity.getUUID()) && !core.forestReinforcements().contains(entity.getUUID())) return;
        if (peaceful) core.cancelForestBattle();
        core.removeBoss(entity.getUUID());
        core.removeForestReinforcement(entity.getUUID());
    }
}
