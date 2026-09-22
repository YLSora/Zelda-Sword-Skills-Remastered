package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.LockedDoorBlock;
import zeldaswordskills_remastered.block.MechanismBlocks;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;
import zeldaswordskills_remastered.entity.LegacyCreatureDrops;
import zeldaswordskills_remastered.entity.WaterBossCreature;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** OceanBattle's delayed sandfall, scoped to one generated room and its participants. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class WaterEncounter {
    private static final String SAND_CORE = "zss_water_sand_core";
    private static final String REINFORCEMENT_CORE = "zss_water_reinforcement_core";

    private WaterEncounter() {}

    static boolean spawnReinforcement(ServerLevel level, DungeonCore core) {
        var waveId = DungeonController.beginReinforcementWave(level, core.waterReinforcement().stream().toList(), 1);
        if (waveId.isEmpty()) return false;
        ElderGuardian guardian = EntityType.ELDER_GUARDIAN.create(level);
        if (guardian == null) return false;
        for (int corner = 0; corner < 4; corner++) {
            if (!DungeonArena.positionAtCorner(level, core, guardian, corner)) continue;
            guardian.finalizeSpawn(level, level.getCurrentDifficultyAt(guardian.blockPosition()), MobSpawnType.EVENT, null, null);
            guardian.setPersistenceRequired();
            guardian.getPersistentData().putLong(REINFORCEMENT_CORE, core.getBlockPos().asLong());
            DungeonController.markReinforcement(guardian, waveId.orElseThrow());
            if (!level.addFreshEntity(guardian)) return false;
            core.setWaterReinforcement(guardian.getUUID());
            return true;
        }
        return false;
    }

    public static void begin(ServerLevel level, DungeonCore core) {
        core.beginWaterBattle(level.getDifficulty().getId());
        setDoor(level, core, false);
        BlockPos hinder = core.getBlockPos().relative(core.doorSide().orElseThrow()).above(2);
        if (level.getBlockState(hinder).is(ZSSRegistries.SECRET_STONE_SANDSTONE.get()))
            level.setBlock(hinder, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    }

    public static void tick(ServerLevel level, DungeonCore core) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) core.cancelWaterBattle();
        if (!roomLoaded(level, core)) return;
        if (core.waterCancelling()) {
            for (var uuid : core.bossUuids()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeBoss(uuid); }
            }
            core.waterReinforcement().ifPresent(uuid -> {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeWaterReinforcement(uuid); }
            });
            if (core.bossUuids().isEmpty() && core.waterReinforcement().isEmpty()) {
                restoreWater(level, core);
                setDoor(level, core, true);
                core.resetWaterBattle();
            }
            return;
        }
        core.waterReinforcement().ifPresent(uuid -> {
            if (level.getEntity(uuid) instanceof LivingEntity living && living.isDeadOrDying())
                core.removeWaterReinforcement(uuid);
        });
        if (core.bossUuids().stream().anyMatch(uuid -> level.getEntity(uuid) == null)
                || core.waterReinforcement().filter(uuid -> level.getEntity(uuid) == null).isPresent()) return;
        if (core.bossUuids().isEmpty() && core.waterReinforcement().isEmpty()) {
            DungeonController.finishEncounter(level, core, DungeonType.WATER);
            BlockPos pos = core.getBlockPos().above(3);
            level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5,
                    LegacyCreatureDrops.bonusHeartOrb()));
            return;
        }
        core.setWaterHazardDelay(core.waterHazardDelay() - 1);
        if (core.waterHazardDelay() <= 0) {
            dropSand(level, core);
            core.setWaterHazardDelay(100 - 20 * core.waterDifficulty() + level.random.nextInt(60));
        }
    }

    private static boolean roomLoaded(ServerLevel level, DungeonCore core) {
        BlockPos min = DungeonArena.minimum(core);
        int span = core.arenaWidth() - 1;
        return level.hasChunkAt(min) && level.hasChunkAt(min.offset(span, 0, 0))
                && level.hasChunkAt(min.offset(0, 0, span)) && level.hasChunkAt(min.offset(span, 0, span));
    }

    private static void dropSand(ServerLevel level, DungeonCore core) {
        level.playSound(null, core.getBlockPos(), ZSSRegistries.ROCK_FALL.get(), SoundSource.BLOCKS, 1, 1);
        for (int x = 1; x < core.arenaWidth() - 1; x++) for (int z = 1; z < core.arenaWidth() - 1; z++) {
            BlockPos pos = DungeonArena.localPos(core, x, core.arenaHeight() - 2, z);
            var state = level.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.WATER)) continue;
            FallingBlockEntity sand = FallingBlockEntity.fall(level, pos, Blocks.SAND.defaultBlockState());
            sand.getPersistentData().putLong(SAND_CORE, core.getBlockPos().asLong());
        }
    }

    private static void restoreWater(ServerLevel level, DungeonCore core) {
        // Remove airborne sand as well, so a completed room cannot receive a late sand deposit.
        level.getEntitiesOfClass(FallingBlockEntity.class, DungeonArena.interior(core), sand ->
                sand.getPersistentData().contains(SAND_CORE, net.minecraft.nbt.Tag.TAG_LONG)
                        && sand.getPersistentData().getLong(SAND_CORE) == core.getBlockPos().asLong()).forEach(Entity::discard);
        for (int x = 1; x < core.arenaWidth() - 1; x++) for (int z = 1; z < core.arenaWidth() - 1; z++)
            for (int y = 1; y < core.arenaHeight() - 1; y++) {
                BlockPos pos = DungeonArena.localPos(core, x, y, z);
                var state = level.getBlockState(pos);
                if (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL))
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
    }

    public static void finish(ServerLevel level, DungeonCore core) {
        restoreWater(level, core);
        setDoor(level, core, true);
        DungeonVictory.reward(level, core, core.waterDifficulty());
        core.resetWaterBattle();
    }

    private static void setDoor(ServerLevel level, DungeonCore core, boolean open) {
        var side = core.doorSide().orElseThrow();
        BlockPos lower = core.getBlockPos().relative(side, core.doorDistance()).above(core.doorOffsetY());
        for (BlockPos pos : java.util.List.of(lower, lower.above())) {
            var state = open ? ZSSRegistries.DOOR_BOSS_WATER.get().defaultBlockState()
                    .setValue(LockedDoorBlock.FACING, side)
                    .setValue(LockedDoorBlock.HALF, pos.equals(lower) ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                    .setValue(LockedDoorBlock.OPEN, true).setValue(LockedDoorBlock.UNLOCKED, true)
                    : ZSSRegistries.SECRET_STONE_SANDSTONE.get().defaultBlockState()
                    .setValue(MechanismBlocks.SecretStone.UNBREAKABLE, true);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    @SubscribeEvent public static void participantLeaves(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        boolean killed = entity.getRemovalReason() == Entity.RemovalReason.KILLED;
        boolean peaceful = entity.getRemovalReason() == Entity.RemovalReason.DISCARDED && level.getDifficulty() == Difficulty.PEACEFUL;
        if (!killed && !peaceful) return;
        BlockPos pos = entity instanceof WaterBossCreature boss ? boss.dungeonCorePos().orElse(null)
                : entity instanceof ElderGuardian && entity.getPersistentData().contains(REINFORCEMENT_CORE, net.minecraft.nbt.Tag.TAG_LONG)
                ? BlockPos.of(entity.getPersistentData().getLong(REINFORCEMENT_CORE)) : null;
        if (pos == null || !(level.getBlockEntity(pos) instanceof DungeonCore core)
                || core.dungeonType().orElse(null) != DungeonType.WATER
                || !core.bossUuids().contains(entity.getUUID()) && !core.waterReinforcement().filter(entity.getUUID()::equals).isPresent()) return;
        if (peaceful) core.cancelWaterBattle();
        core.removeBoss(entity.getUUID());
        core.removeWaterReinforcement(entity.getUUID());
    }
}
