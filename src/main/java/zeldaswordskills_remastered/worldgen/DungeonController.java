package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.DungeonBlocks;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities;
import zeldaswordskills_remastered.entity.CreatureSpawnRules;
import zeldaswordskills_remastered.entity.projectile.ThrownBomb;
import zeldaswordskills_remastered.world.ZSSWorldData;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/** Server-authoritative lifecycle for a generated dungeon core and its explicitly linked boss. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class DungeonController {
    public static final int CHECK_INTERVAL_TICKS = 20;
    public static final double ACTIVATION_RANGE = 3.5D;

    private DungeonController() {
    }

    @SubscribeEvent
    public static void highlightCombatants(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof LivingEntity living)) return;
        boolean boss = event.getEntity() instanceof DungeonBoss binding && binding.isBoss();
        boolean reinforcement = event.getEntity().getPersistentData().getAllKeys().stream()
                .anyMatch(key -> key.endsWith("_reinforcement_core"));
        if (boss || reinforcement) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, true, true));
        }
    }

    public static void tick(ServerLevel level, StageNineBlockEntities.DungeonCore core) {
        if (core.completed()) return;
        DungeonType dungeon = core.dungeonType().orElse(null);
        ZSSWorldData.DungeonState saved = dungeon == null ? null
                : ZSSWorldData.get(level).dungeons().get(instanceId(level, core.getBlockPos()));
        if (saved != null && saved.completed()) {
            core.complete();
            DungeonBlocks.setSealed(level, core.getBlockPos(), false);
            return;
        }
        if (dungeon == DungeonType.FIRE && core.fireBattleDifficulty() > 0) {
            FireEncounter.tick(level, core);
            return;
        }
        if (dungeon == DungeonType.FOREST && core.forestDifficulty() > 0) {
            ForestEncounter.tick(level, core);
            return;
        }
        if (dungeon == DungeonType.ICE && core.iceDifficulty() > 0) {
            IceEncounter.tick(level, core);
            return;
        }
        if (dungeon == DungeonType.WATER && core.waterDifficulty() > 0) {
            WaterEncounter.tick(level, core);
            return;
        }
        if (dungeon == DungeonType.DESERT && core.desertDifficulty() > 0) {
            DesertEncounter.tick(level, core);
            return;
        }
        List<Mob> active = activeBosses(level, core);
        if (!active.isEmpty()) {
            tickEncounter(level, core, active.get(0));
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || level.getGameTime() % CHECK_INTERVAL_TICKS != 0L) return;
        BlockPos pos = core.getBlockPos();
        AABB activationArea = AABB.ofSize(Vec3.atCenterOf(pos), ACTIVATION_RANGE * 2.0D, 4.0D,
                ACTIVATION_RANGE * 2.0D);
        if (DungeonArena.validRoom(core)) activationArea = DungeonArena.interior(core);
        if (!level.getEntitiesOfClass(Player.class, activationArea,
                player -> player.isAlive() && !player.isSpectator()).isEmpty()) spawnBoss(level, core);
    }

    public static Optional<Mob> spawnBoss(ServerLevel level,
                                                     StageNineBlockEntities.DungeonCore core) {
        if (core.getLevel() != level || core.completed() || level.getDifficulty() == Difficulty.PEACEFUL) {
            return Optional.empty();
        }
        List<Mob> active = activeBosses(level, core);
        if (!active.isEmpty()) return Optional.of(active.get(0));
        if (!core.bossUuids().isEmpty() || core.fireBattleDifficulty() > 0 || core.forestDifficulty() > 0 || core.iceDifficulty() > 0 || core.waterDifficulty() > 0 || core.desertDifficulty() > 0) return Optional.empty();

        DungeonType dungeon = core.dungeonType().orElse(null);
        if (dungeon == null) return Optional.empty();
        if (!DungeonArena.validRoom(core)) return Optional.empty();
        ZSSWorldData.DungeonState saved = ZSSWorldData.get(level).dungeons().get(instanceId(level, core.getBlockPos()));
        if (saved != null && saved.completed()) return Optional.empty();
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(dungeon.bossEntityId());
        if (entityType == null || !entityType.is(CreatureSpawnRules.DUNGEON_ONLY)) {
            ZeldaSwordSkills_Remastered.LOGGER.error("Dungeon {} selected a boss outside #{}", dungeon.id(),
                    CreatureSpawnRules.DUNGEON_ONLY.location());
            return Optional.empty();
        }

        int count = dungeon == DungeonType.DESERT || dungeon == DungeonType.FOREST || dungeon == DungeonType.ICE ? 2
                : dungeon == DungeonType.FIRE || dungeon == DungeonType.WATER ? 4 : 1;
        List<Mob> bosses = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Entity created = entityType.create(level);
            if (!(created instanceof Mob boss) || !(boss instanceof DungeonBoss binding) || !binding.isBoss()) {
                bosses.forEach(Entity::discard);
                ZeldaSwordSkills_Remastered.LOGGER.error("Dungeon {} selected an invalid boss entity {}", dungeon.id(), entityType);
                return Optional.empty();
            }
            BlockPos spawnPos = DungeonArena.bossSpawnPos(core, index, count);
            boss.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (!DungeonArena.positionAtBossSlot(level, core, boss, index, count)) {
                bosses.forEach(Entity::discard);
                return Optional.empty();
            }
            boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.STRUCTURE, null, null);
            if (boss instanceof zeldaswordskills_remastered.entity.FireBossCreature caster) {
                caster.setElement(switch (index) {
                    case 0 -> zeldaswordskills_remastered.entity.projectile.ToolProjectile.Mode.FIRE;
                    case 1 -> zeldaswordskills_remastered.entity.projectile.ToolProjectile.Mode.ICE;
                    case 2 -> zeldaswordskills_remastered.entity.projectile.ToolProjectile.Mode.LIGHTNING;
                    default -> zeldaswordskills_remastered.entity.projectile.ToolProjectile.Mode.WIND;
                });
            }
            binding.linkDungeon(dungeon, core.getBlockPos());
            if (!level.addFreshEntity(boss)) {
                bosses.forEach(Entity::discard);
                return Optional.empty();
            }
            bosses.add(boss);
        }
        if (dungeon == DungeonType.WATER && !WaterEncounter.spawnReinforcement(level, core)) {
            bosses.forEach(Entity::discard);
            return Optional.empty();
        }
        if (dungeon == DungeonType.DESERT && !DesertEncounter.spawnReinforcements(level, core)) {
            bosses.forEach(Entity::discard);
            return Optional.empty();
        }
        core.setBosses(bosses.stream().map(Entity::getUUID).toList());
        beginEncounter(level, core, dungeon);
        level.players().stream().filter(player -> player.distanceToSqr(core.getBlockPos().getCenter()) <= 32 * 32)
                .forEach(player -> ZSSAdvancementService.bossStarted(player));
        return Optional.of(bosses.get(0));
    }

    public static boolean completeBoss(Mob boss) {
        if (!(boss.level() instanceof ServerLevel level) || !(boss instanceof DungeonBoss binding)) return false;
        DungeonType dungeon = binding.dungeonType().orElse(null);
        BlockPos corePos = binding.dungeonCorePos().orElse(null);
        if (dungeon == null || corePos == null
                || !(level.getBlockEntity(corePos) instanceof StageNineBlockEntities.DungeonCore core)
                || core.completed() || core.dungeonType().orElse(null) != dungeon
                || !core.bossUuids().contains(boss.getUUID())) return false;
        core.removeBoss(boss.getUUID());
        if (dungeon == DungeonType.FOREST || dungeon == DungeonType.ICE || dungeon == DungeonType.WATER || dungeon == DungeonType.DESERT) return false;
        if (!core.bossUuids().isEmpty() || !core.fireReinforcements().isEmpty()) return false;
        finishEncounter(level, core, dungeon);
        return true;
    }

    static void finishEncounter(ServerLevel level, StageNineBlockEntities.DungeonCore core, DungeonType dungeon) {
        if (core.completed()) return;
        if (dungeon == DungeonType.DESERT) DesertEncounter.finish(level, core);
        if (dungeon == DungeonType.FIRE) FireEncounter.finish(level, core);
        if (dungeon == DungeonType.FOREST) ForestEncounter.finish(level, core);
        if (dungeon == DungeonType.ICE) IceEncounter.finish(level, core);
        if (dungeon == DungeonType.WATER) WaterEncounter.finish(level, core);
        core.complete();
        ZSSWorldData.get(level).setDungeonState(instanceId(level, core.getBlockPos()), true, 0L);
        boolean swordTemple = dungeon == DungeonType.FOREST
                && level.getBlockEntity(core.getBlockPos().above(2)) instanceof zeldaswordskills_remastered.block.entity.PedestalBlockEntity;
        if (swordTemple) {
            BlockPos min = DungeonArena.minimum(core);
            ZSSWorldData.get(level).protectForestTemple(instanceId(level, core.getBlockPos()), level, new net.minecraft.world.phys.AABB(min,
                    min.offset(core.arenaWidth(), core.arenaHeight(), core.arenaWidth())));
        }
        DungeonBlocks.setSealed(level, core.getBlockPos(), false);
        placeWarpStone(level, core, dungeon);
        Vec3 center = core.getBlockPos().getCenter();
        level.players().stream().filter(player -> player.distanceToSqr(center) <= 32 * 32)
                .forEach(player -> {
                    if (swordTemple) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.zeldaswordskills_remastered.forest_sword_call"));
                    ZSSCapabilities.get(player).ifPresent(data -> {
                        data.recordBossRoom(dungeon.id());
                        ZSSAdvancementService.bossCompleted(player, data);
                    });
                    zeldaswordskills_remastered.network.ZSSNetwork.syncPlayerData(player);
                });
    }

    private static void placeWarpStone(ServerLevel level, StageNineBlockEntities.DungeonCore core, DungeonType dungeon) {
        Direction door = core.doorSide().orElse(null);
        if (door == null) return;
        var block = switch (dungeon) {
            case FIRE -> zeldaswordskills_remastered.registry.ZSSRegistries.WARP_STONE_BOLERO;
            case FOREST -> zeldaswordskills_remastered.registry.ZSSRegistries.WARP_STONE_MINUET;
            case END -> zeldaswordskills_remastered.registry.ZSSRegistries.WARP_STONE_PRELUDE;
            case EARTH -> zeldaswordskills_remastered.registry.ZSSRegistries.WARP_STONE_OATH;
            case ICE -> zeldaswordskills_remastered.registry.ZSSRegistries.WARP_STONE_NOCTURNE;
            case DESERT -> zeldaswordskills_remastered.registry.ZSSRegistries.WARP_STONE_REQUIEM;
            case WATER -> zeldaswordskills_remastered.registry.ZSSRegistries.WARP_STONE_SERENADE;
        };
        BlockPos pos = core.getBlockPos().relative(door, core.doorDistance() + 1)
                .above(core.doorOffsetY() - 1);
        level.setBlock(pos, block.get().defaultBlockState(), 3);
    }

    public static ResourceLocation instanceId(ServerLevel level, BlockPos corePos) {
        ResourceLocation dimension = level.dimension().location();
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID,
                "dungeons/" + dimension.getNamespace() + "/" + dimension.getPath() + "/"
                        + corePos.getX() + "/" + corePos.getY() + "/" + corePos.getZ());
    }

    private static void beginEncounter(ServerLevel level, StageNineBlockEntities.DungeonCore core, DungeonType dungeon) {
        if (dungeon == DungeonType.DESERT) DesertEncounter.begin(level, core);
        if (dungeon == DungeonType.FIRE) FireEncounter.begin(level, core);
        if (dungeon == DungeonType.FOREST) ForestEncounter.begin(level, core);
        if (dungeon == DungeonType.ICE) IceEncounter.begin(level, core);
        if (dungeon == DungeonType.WATER) WaterEncounter.begin(level, core);
        Direction door = core.doorSide().orElse(null);
        if (door != null) {
            BlockPos hinder = core.getBlockPos().relative(door).above(core.doorOffsetY() + 1);
            if (zeldaswordskills_remastered.registry.ZSSRegistries.SECRET_STONES.stream()
                    .anyMatch(block -> level.getBlockState(hinder).is(block.get()))) {
                level.removeBlock(hinder, false);
            }
        }
        if (dungeon == DungeonType.EARTH && level.getDifficulty().getId() > Difficulty.EASY.getId()) {
            core.setNextHazardTick(level.getGameTime() + 200L + level.random.nextInt(200));
        }
    }

    private static void tickEncounter(ServerLevel level, StageNineBlockEntities.DungeonCore core, Mob boss) {
        DungeonType dungeon = core.dungeonType().orElse(null);
        if (level.getDifficulty().getId() <= Difficulty.EASY.getId()) return;
        if (dungeon != DungeonType.EARTH) return;
        long due = core.nextHazardTick();
        if (due < 0L) {
            core.setNextHazardTick(level.getGameTime() + 200L + level.random.nextInt(200));
            return;
        }
        if (level.getGameTime() < due) return;
        int radius = Math.max(2, core.arenaRadius() - 1);
        BlockPos center = core.getBlockPos();
        int x;
        int z;
        do {
            x = center.getX() + level.random.nextInt(radius * 2 + 1) - radius;
            z = center.getZ() + level.random.nextInt(radius * 2 + 1) - radius;
        } while (Math.abs(x - center.getX()) <= 1 && Math.abs(z - center.getZ()) <= 1);
        ThrownBomb bomb = new ThrownBomb(level, boss, ThrownBomb.BombKind.STANDARD)
                .configureEncounterBomb(Math.max(1, (3 - level.getDifficulty().getId()) * 16 + 1));
        bomb.setPos(x + .5D, center.getY() + Math.max(4, core.arenaHeight() - 2), z + .5D);
        bomb.setDeltaMovement(0.0D, -0.15D, 0.0D);
        if (level.noCollision(bomb) && level.addFreshEntity(bomb)) {
            level.playSound(null, bomb.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0F, 1.4F);
        }
        int difficulty = level.getDifficulty().getId();
        core.setNextHazardTick(level.getGameTime() + (3 - difficulty) * 100L + 50L + level.random.nextInt(400));
    }

    static BlockPos bossSpawnPos(ServerLevel level, StageNineBlockEntities.DungeonCore core, int bossIndex) {
        int offset = Math.max(2, core.arenaRadius() - 2);
        DungeonType dungeon = core.dungeonType().orElse(null);
        int start = dungeon == DungeonType.FIRE ? bossIndex : level.random.nextInt(4);
        for (int attempt = 0; attempt < 4; attempt++) {
            int corner = (start + attempt) & 3;
            int dx = corner < 2 ? -offset : offset;
            int dz = (corner & 1) == 0 ? -offset : offset;
            BlockPos candidate = core.getBlockPos().offset(dx, 3, dz);
            if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()) return candidate;
        }
        return core.getBlockPos().above(3);
    }

    private static List<Mob> activeBosses(ServerLevel level,
                                                     StageNineBlockEntities.DungeonCore core) {
        return core.bossUuids().stream().map(level::getEntity)
                .filter(entity -> entity instanceof Mob boss && boss instanceof DungeonBoss && boss.isAlive())
                .map(entity -> (Mob) entity).toList();
    }

}
