package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.LockedDoorBlock;
import zeldaswordskills_remastered.block.MechanismBlocks;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;
import zeldaswordskills_remastered.entity.IceBossCreature;
import zeldaswordskills_remastered.entity.LegacyCreatureDrops;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Illusioner battle with persistent, independently timed stray reinforcements. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class IceEncounter {
    private static final String REINFORCEMENT_CORE = "zss_ice_reinforcement_core";
    private IceEncounter() {}

    public static void begin(ServerLevel level, DungeonCore core) {
        core.beginIceBattle(level.getDifficulty().getId());
        core.setIceReinforcementDelay(300 + level.random.nextInt(300));
        setDoor(level, core, false);
    }

    public static void tick(ServerLevel level, DungeonCore core) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) core.cancelIceBattle();
        if (core.iceCancelling()) {
            for (var uuid : core.bossUuids()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeBoss(uuid); }
            }
            for (var uuid : core.iceReinforcements()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeIceReinforcement(uuid); }
            }
            if (core.bossUuids().isEmpty() && core.iceReinforcements().isEmpty()) {
                setDoor(level, core, true);
                core.resetIceBattle();
            }
            return;
        }
        for (var uuid : core.iceReinforcements()) {
            if (level.getEntity(uuid) instanceof net.minecraft.world.entity.LivingEntity living && living.isDeadOrDying())
                core.removeIceReinforcement(uuid);
        }
        if (core.bossUuids().stream().anyMatch(uuid -> level.getEntity(uuid) == null)
                || core.iceReinforcements().stream().anyMatch(uuid -> level.getEntity(uuid) == null)) return;
        if (!core.bossUuids().isEmpty()) {
            core.setIceReinforcementDelay(core.iceReinforcementDelay() - 1);
            if (core.iceReinforcementDelay() <= 0) {
                for (int corner = 0; corner < 4; corner++) spawnReinforcement(level, core, corner);
                core.setIceReinforcementDelay(300 + level.random.nextInt(300));
            }
            return;
        }
        if (!core.iceReinforcements().isEmpty()) return;
        DungeonController.finishEncounter(level, core, DungeonType.ICE);
        BlockPos pos = core.getBlockPos().above(2);
        level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5,
                LegacyCreatureDrops.bonusHeartOrb()));
    }

    private static void spawnReinforcement(ServerLevel level, DungeonCore core, int corner) {
        var stray = net.minecraft.world.entity.EntityType.STRAY.create(level);
        if (stray == null || !DungeonArena.positionAtCorner(level, core, stray, corner)) return;
        stray.finalizeSpawn(level, level.getCurrentDifficultyAt(stray.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
        stray.setPersistenceRequired();
        stray.getPersistentData().putLong(REINFORCEMENT_CORE, core.getBlockPos().asLong());
        if (level.addFreshEntity(stray)) core.addIceReinforcement(stray.getUUID());
    }

    public static void finish(ServerLevel level, DungeonCore core) {
        setDoor(level, core, true);
        DungeonVictory.reward(level, core, core.iceDifficulty());
        core.resetIceBattle();
    }

    private static void setDoor(ServerLevel level, DungeonCore core, boolean open) {
        if (!DungeonArena.validRoom(core)) return;
        var side = core.doorSide().orElseThrow();
        BlockPos lower = core.getBlockPos().relative(side, core.doorDistance()).above(core.doorOffsetY());
        for (BlockPos pos : java.util.List.of(lower, lower.above())) {
            var state = open ? ZSSRegistries.DOOR_BOSS_ICE.get().defaultBlockState()
                    .setValue(LockedDoorBlock.FACING, side)
                    .setValue(LockedDoorBlock.HALF, pos.equals(lower) ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                    .setValue(LockedDoorBlock.OPEN, true).setValue(LockedDoorBlock.UNLOCKED, true)
                    : ZSSRegistries.SECRET_STONE_ICE.get().defaultBlockState()
                    .setValue(MechanismBlocks.SecretStone.UNBREAKABLE, true);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    @SubscribeEvent public static void participantLeaves(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        boolean killed = entity.getRemovalReason() == Entity.RemovalReason.KILLED;
        boolean peaceful = entity.getRemovalReason() == Entity.RemovalReason.DISCARDED
                && level.getDifficulty() == Difficulty.PEACEFUL;
        if (!killed && !peaceful) return;
        BlockPos pos = entity instanceof IceBossCreature boss ? boss.dungeonCorePos().orElse(null)
                : entity.getPersistentData().contains(REINFORCEMENT_CORE, net.minecraft.nbt.Tag.TAG_LONG)
                ? BlockPos.of(entity.getPersistentData().getLong(REINFORCEMENT_CORE)) : null;
        if (pos == null || !(level.getBlockEntity(pos) instanceof DungeonCore core)
                || core.dungeonType().orElse(null) != DungeonType.ICE) return;
        if (!core.bossUuids().contains(entity.getUUID()) && !core.iceReinforcements().contains(entity.getUUID())) return;
        if (peaceful) core.cancelIceBattle();
        core.removeBoss(entity.getUUID());
        core.removeIceReinforcement(entity.getUUID());
    }
}
