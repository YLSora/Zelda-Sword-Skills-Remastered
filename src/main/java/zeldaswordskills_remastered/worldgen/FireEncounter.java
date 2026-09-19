package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;
import zeldaswordskills_remastered.entity.LegacyCreatureDrops;

/** FireBattle hazards, with a persistent phase clock and explicitly owned reinforcements. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID)
public final class FireEncounter {
    private static final String CORE_POS = "zss_fire_reinforcement_core";
    private FireEncounter() {}

    public static void begin(ServerLevel level, DungeonCore core) {
        core.beginFireBattle(level.getDifficulty().getId());
        setDoor(level, core, false);
    }

    private static void setDoor(ServerLevel level, DungeonCore core, boolean open) {
        if (core.doorDistance() <= 0) return;
        core.doorSide().ifPresent(side -> {
            BlockPos lower = core.getBlockPos().relative(side, core.doorDistance()).above(core.doorOffsetY());
            for (BlockPos pos : java.util.List.of(lower, lower.above())) {
                var state = open ? zeldaswordskills_remastered.registry.ZSSRegistries.DOOR_BOSS_FIRE.get().defaultBlockState()
                        .setValue(zeldaswordskills_remastered.block.LockedDoorBlock.FACING, side)
                        .setValue(zeldaswordskills_remastered.block.LockedDoorBlock.HALF, pos.equals(lower)
                                ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                                : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                        .setValue(zeldaswordskills_remastered.block.LockedDoorBlock.OPEN, true)
                        .setValue(zeldaswordskills_remastered.block.LockedDoorBlock.UNLOCKED, true)
                        : zeldaswordskills_remastered.registry.ZSSRegistries.SECRET_STONE_NETHER_WART_BLOCK.get().defaultBlockState()
                        .setValue(zeldaswordskills_remastered.block.MechanismBlocks.SecretStone.UNBREAKABLE, true);
                level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        });
    }

    public static void tick(ServerLevel level, DungeonCore core) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            // Unloaded participants stay bound until they can be discarded, never respawned over.
            for (var uuid : core.bossUuids()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeBoss(uuid); }
            }
            for (var uuid : core.fireReinforcements()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeFireReinforcement(uuid); }
            }
            if (core.bossUuids().isEmpty() && core.fireReinforcements().isEmpty()) {
                setDoor(level, core, true);
                core.resetFireBattle();
            }
            return;
        }
        for (var uuid : core.fireReinforcements()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof Mob mob && mob.isDeadOrDying()) core.removeFireReinforcement(uuid);
        }
        if (core.bossUuids().isEmpty() && core.fireReinforcements().isEmpty()) {
            DungeonController.finishEncounter(level, core, DungeonType.FIRE);
            BlockPos pos = core.getBlockPos().above(2);
            level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5,
                    LegacyCreatureDrops.bonusHeartOrb()));
            return;
        }
        boolean loaded = core.bossUuids().stream().allMatch(uuid -> level.getEntity(uuid) != null)
                && core.fireReinforcements().stream().allMatch(uuid -> level.getEntity(uuid) != null);
        if (!loaded) return;
        core.advanceFireBattle();
        long elapsed = core.fireBattleTicks();
        if (elapsed % 50L != 0L) return;
        int difficulty = core.fireBattleDifficulty();
        // Preserve the original 6000-tick countdown phase without its terminal timer stall.
        long phase = 6000L - elapsed;
        if (difficulty > 1) {
            if (Math.floorMod(phase, 800 - difficulty * 50) == 0) spawnReinforcement(level, core);
        }
        if (Math.floorMod(phase, 500) == 0) destroyPillar(level, core);
    }

    private static void spawnReinforcement(ServerLevel level, DungeonCore core) {
        boolean hard = core.fireBattleDifficulty() == 3;
        Mob mob = EntityType.WITHER_SKELETON.create(level);
        if (mob == null) return;
        BlockPos pos = DungeonController.bossSpawnPos(level, core, level.random.nextInt(4));
        mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        if (!DungeonArena.positionAtCorner(level, core, mob, level.random.nextInt(4))) return;
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        if (hard) {
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0D);
            mob.setHealth(300.0F);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(Enchantments.SHARPNESS, 4);
            sword.enchant(Enchantments.FIRE_ASPECT, 1);
            mob.setItemSlot(EquipmentSlot.MAINHAND, sword);
            DungeonBossEquipment.equipArmor(mob, 3);
        }
        mob.setPersistenceRequired();
        mob.getPersistentData().putLong(CORE_POS, core.getBlockPos().asLong());
        if (level.addFreshEntity(mob)) core.addFireReinforcement(mob.getUUID());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void participantLeaves(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        boolean died = entity.getRemovalReason() == Entity.RemovalReason.KILLED;
        boolean cancelled = entity.getRemovalReason() == Entity.RemovalReason.DISCARDED
                && level.getDifficulty() == Difficulty.PEACEFUL;
        if (!died && !cancelled) return;
        BlockPos pos = null;
        if (entity.getPersistentData().contains(CORE_POS, net.minecraft.nbt.Tag.TAG_LONG)) {
            pos = BlockPos.of(entity.getPersistentData().getLong(CORE_POS));
        } else if (cancelled && entity instanceof zeldaswordskills_remastered.entity.FireBossCreature boss) {
            pos = boss.dungeonCorePos().orElse(null);
        }
        if (pos != null && level.getBlockEntity(pos) instanceof DungeonCore core
                && core.dungeonType().orElse(null) == DungeonType.FIRE) {
            core.removeFireReinforcement(entity.getUUID());
            if (cancelled) core.removeBoss(entity.getUUID());
        }
    }

    private static void destroyPillar(ServerLevel level, DungeonCore core) {
        int corner = level.random.nextInt(4);
        int far = core.arenaWidth() - 4;
        BlockPos pos = DungeonArena.localPos(core, corner < 2 ? 3 : far,
                1 + level.random.nextInt(3), (corner & 1) == 0 ? 3 : far);
        if (level.isEmptyBlock(pos)) return;
        level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                1.5F + core.fireBattleDifficulty() * .5F, Level.ExplosionInteraction.NONE);
        // The scripted collapse cannot remove the shell, core, or reward BlockEntities.
        BlockPos center = core.getBlockPos();
        for (BlockPos part : BlockPos.betweenClosed(pos.offset(-1, 0, -1),
                new BlockPos(pos.getX() + 1, center.getY() + core.arenaHeight() - 3, pos.getZ() + 1))) {
            if (!DungeonArena.contains(core, new net.minecraft.world.phys.AABB(part))
                    || Math.abs(part.getX() - center.getX()) <= 1 && Math.abs(part.getZ() - center.getZ()) <= 1
                    || level.getBlockEntity(part) != null
                    || level.getBlockState(part).getDestroySpeed(level, part) < 0) continue;
            level.destroyBlock(part, false);
        }
    }

    public static void finish(ServerLevel level, DungeonCore core) {
        setDoor(level, core, true);
        DungeonVictory.reward(level, core, core.fireBattleDifficulty());
        core.resetFireBattle();
    }

}
