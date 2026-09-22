package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
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
import zeldaswordskills_remastered.entity.DesertBossCreature;
import zeldaswordskills_remastered.entity.LegacyCreatureDrops;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.ArrayList;

/** Husk encounter with one skeleton reinforcement group and falling harming potions. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class DesertEncounter {
    private static final String REINFORCEMENT_CORE = "zss_desert_reinforcement_core";
    private static final String POTION_CORE = "zss_desert_potion_core";
    private DesertEncounter() {}

    static boolean spawnReinforcements(ServerLevel level, DungeonCore core) {
        var waveId = DungeonController.beginReinforcementWave(level, core.desertReinforcements(), 4);
        if (waveId.isEmpty()) return false;
        var skeletons = new ArrayList<Mob>();
        for (int corner = 0; corner < 4; corner++) {
            var skeleton = EntityType.SKELETON.create(level);
            if (skeleton == null || !DungeonArena.positionAtCorner(level, core, skeleton, corner)) {
                skeletons.forEach(Entity::discard);
                return false;
            }
            skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(skeleton.blockPosition()), MobSpawnType.EVENT, null, null);
            ItemStack bow = new ItemStack(Items.BOW);
            bow.enchant(Enchantments.POWER_ARROWS, 1);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
            skeleton.setPersistenceRequired();
            skeleton.getPersistentData().putLong(REINFORCEMENT_CORE, core.getBlockPos().asLong());
            DungeonController.markReinforcement(skeleton, waveId.orElseThrow());
            if (!level.addFreshEntity(skeleton)) {
                skeletons.forEach(Entity::discard);
                return false;
            }
            skeletons.add(skeleton);
        }
        skeletons.forEach(skeleton -> core.addDesertReinforcement(skeleton.getUUID()));
        return true;
    }

    public static void begin(ServerLevel level, DungeonCore core) {
        int difficulty = level.getDifficulty().getId();
        core.beginDesertBattle(difficulty, difficulty > 1 ? 300 - level.random.nextInt(100) : 0);
        setDoor(level, core, false);
        if (difficulty == 3) replaceFloor(level, core, false);
    }

    public static void tick(ServerLevel level, DungeonCore core) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) core.cancelDesertBattle();
        if (core.desertCancelling()) {
            for (var uuid : core.bossUuids()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeBoss(uuid); }
            }
            for (var uuid : core.desertReinforcements()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) { entity.discard(); core.removeDesertReinforcement(uuid); }
            }
            if (core.bossUuids().isEmpty() && core.desertReinforcements().isEmpty()) {
                clearPotions(level, core);
                replaceFloor(level, core, true);
                setDoor(level, core, true);
                core.resetDesertBattle();
            }
            return;
        }
        for (var uuid : core.desertReinforcements()) {
            if (level.getEntity(uuid) instanceof LivingEntity living && living.isDeadOrDying()) core.removeDesertReinforcement(uuid);
        }
        if (core.bossUuids().stream().anyMatch(uuid -> level.getEntity(uuid) == null)
                || core.desertReinforcements().stream().anyMatch(uuid -> level.getEntity(uuid) == null)) return;
        if (core.bossUuids().isEmpty() && core.desertReinforcements().isEmpty()) {
            DungeonController.finishEncounter(level, core, DungeonType.DESERT);
            BlockPos pos = core.getBlockPos().above(3);
            level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, LegacyCreatureDrops.bonusHeartOrb()));
            return;
        }
        if (core.desertDifficulty() <= 1) return;
        core.setDesertHazardDelay(core.desertHazardDelay() - 1);
        if (core.desertHazardDelay() > 0) return;
        firePotions(level, core);
        core.setDesertHazardDelay(300 - 50 * core.desertDifficulty() - level.random.nextInt(100));
    }

    private static void firePotions(ServerLevel level, DungeonCore core) {
        var positions = new ArrayList<BlockPos>();
        for (int x = 1; x < core.arenaWidth() - 1; x++) for (int z = 1; z < core.arenaWidth() - 1; z++) {
            BlockPos pos = DungeonArena.localPos(core, x, core.arenaHeight() - 2, z);
            if (level.getBlockState(pos).isAir()) positions.add(pos);
        }
        for (int index = 0; index < 3 && !positions.isEmpty(); index++) {
            BlockPos pos = positions.remove(level.random.nextInt(positions.size()));
            ThrownPotion potion = new ThrownPotion(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            potion.setItem(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.STRONG_HARMING));
            potion.getPersistentData().putLong(POTION_CORE, core.getBlockPos().asLong());
            potion.shoot(0, -1, 0, 1.6F, 0);
            level.addFreshEntity(potion);
        }
    }

    public static void finish(ServerLevel level, DungeonCore core) {
        clearPotions(level, core);
        replaceFloor(level, core, true);
        setDoor(level, core, true);
        DungeonVictory.reward(level, core, core.desertDifficulty());
        core.resetDesertBattle();
    }

    private static void clearPotions(ServerLevel level, DungeonCore core) {
        level.getEntitiesOfClass(ThrownPotion.class, DungeonArena.interior(core).inflate(1), potion ->
                potion.getPersistentData().contains(POTION_CORE, net.minecraft.nbt.Tag.TAG_LONG)
                        && potion.getPersistentData().getLong(POTION_CORE) == core.getBlockPos().asLong()).forEach(Entity::discard);
    }

    private static void replaceFloor(ServerLevel level, DungeonCore core, boolean restore) {
        for (int x = 1; x < core.arenaWidth() - 1; x++) for (int z = 1; z < core.arenaWidth() - 1; z++) {
            BlockPos pos = DungeonArena.localPos(core, x, 0, z);
            var state = level.getBlockState(pos);
            boolean matches = restore ? state.is(Blocks.SOUL_SAND)
                    : state.is(Blocks.SANDSTONE) || state.is(ZSSRegistries.SECRET_STONE_SANDSTONE.get());
            if (matches) level.setBlock(pos, (restore ? Blocks.SANDSTONE : Blocks.SOUL_SAND).defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void setDoor(ServerLevel level, DungeonCore core, boolean open) {
        var side = core.doorSide().orElseThrow();
        BlockPos lower = core.getBlockPos().relative(side, core.doorDistance()).above(core.doorOffsetY());
        for (BlockPos pos : java.util.List.of(lower, lower.above())) {
            var state = open ? ZSSRegistries.DOOR_BOSS_DESERT.get().defaultBlockState()
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
        BlockPos pos = entity instanceof DesertBossCreature boss ? boss.dungeonCorePos().orElse(null)
                : entity.getPersistentData().contains(REINFORCEMENT_CORE, net.minecraft.nbt.Tag.TAG_LONG)
                ? BlockPos.of(entity.getPersistentData().getLong(REINFORCEMENT_CORE)) : null;
        if (pos == null || !(level.getBlockEntity(pos) instanceof DungeonCore core)
                || core.dungeonType().orElse(null) != DungeonType.DESERT
                || !core.bossUuids().contains(entity.getUUID()) && !core.desertReinforcements().contains(entity.getUUID())) return;
        if (peaceful) core.cancelDesertBattle();
        core.removeBoss(entity.getUUID());
        core.removeDesertReinforcement(entity.getUUID());
    }
}
