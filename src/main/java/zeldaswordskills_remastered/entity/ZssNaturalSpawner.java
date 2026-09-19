package zeldaswordskills_remastered.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.List;

/** A small, capped ZSS spawn distribution that does not compete for vanilla monster spawn selections. */
public final class ZssNaturalSpawner {
    public static final int SPAWN_INTERVAL_TICKS = 80;
    public static final int LOCAL_CAP = 18;
    public static final int LOCAL_CAP_RADIUS = 96;
    private static final int MIN_PLAYER_DISTANCE = 24;
    private static final int MAX_PLAYER_DISTANCE = 56;
    private static final int POSITION_ATTEMPTS = 12;

    private static final List<SpawnEntry> ENTRIES = List.of(
            entry(ZSSRegistries.DARKNUT, 1, 1, 2),
            entry(ZSSRegistries.DARKNUT_MIGHTY, 1, 1, 2),
            entry(ZSSRegistries.CHU, 16, 1, 3),
            entry(ZSSRegistries.CHU_GREEN, 10, 1, 3),
            entry(ZSSRegistries.CHU_BLUE, 10, 1, 3),
            entry(ZSSRegistries.CHU_YELLOW, 10, 1, 3),
            entry(ZSSRegistries.BABA_DEKU, 10, 1, 4),
            entry(ZSSRegistries.BABA_FIRE, 6, 1, 4),
            entry(ZSSRegistries.BABA_WITHERED, 5, 1, 4),
            entry(ZSSRegistries.KEESE, 8, 2, 5),
            entry(ZSSRegistries.KEESE_FIRE, 8, 2, 5),
            entry(ZSSRegistries.KEESE_ICE, 8, 2, 5),
            entry(ZSSRegistries.KEESE_THUNDER, 8, 2, 5),
            entry(ZSSRegistries.KEESE_CURSED, 4, 2, 5),
            entry(ZSSRegistries.OCTOROK, 4, 2, 5),
            entry(ZSSRegistries.OCTOROK_BOMB, 4, 1, 3),
            entry(ZSSRegistries.SKULLTULA, 8, 1, 3),
            entry(ZSSRegistries.SKULLTULA_GOLD, 1, 1, 2),
            entry(ZSSRegistries.WIZZROBE, 2, 1, 2),
            entry(ZSSRegistries.WIZZROBE_ICE, 2, 1, 2),
            entry(ZSSRegistries.WIZZROBE_LIGHTNING, 2, 1, 2),
            entry(ZSSRegistries.WIZZROBE_WIND, 2, 1, 2));

    private ZssNaturalSpawner() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ZssNaturalSpawner::onServerTick);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % SPAWN_INTERVAL_TICKS != 0) return;
        for (var level : event.getServer().getAllLevels()) {
            if (!CreatureSpawnRules.naturalMonstersAllowed(level)) continue;
            for (var player : level.players()) {
                if (player.isSpectator() || !player.isAlive()) continue;
                int nearby = level.getEntitiesOfClass(LegacyCreature.class,
                        player.getBoundingBox().inflate(LOCAL_CAP_RADIUS),
                        creature -> independentlySpawns(creature.getType())).size();
                if (nearby >= LOCAL_CAP) continue;
                trySpawnGroup(level, player.blockPosition(), LOCAL_CAP - nearby, level.random);
            }
        }
    }

    private static void trySpawnGroup(net.minecraft.server.level.ServerLevel level, BlockPos playerPos,
                                      int remainingCapacity, RandomSource random) {
        Holder<Biome> playerBiome = level.getBiome(playerPos);
        SpawnEntry selected = select(level, playerPos, playerBiome, random);
        if (selected == null) return;
        // Default chicken jockey rate without nearby chickens: baby * failed mount search * new chicken.
        if ((selected.type() == ZSSRegistries.DARKNUT || selected.type() == ZSSRegistries.DARKNUT_MIGHTY)
                && random.nextFloat() >= 0.05F * 0.95F * 0.05F) return;
        int count = Math.min(remainingCapacity, Mth.nextInt(random, selected.minCount(), selected.maxCount()));
        for (int index = 0; index < count; index++) {
            BlockPos pos = findPosition(level, playerPos, selected.type().get(), random);
            if (pos != null) spawn(level, selected.type().get(), pos, random);
        }
    }

    private static SpawnEntry select(net.minecraft.server.level.ServerLevel level, BlockPos playerPos,
                                     Holder<Biome> biome, RandomSource random) {
        int total = 0;
        for (SpawnEntry entry : ENTRIES) {
            if (CreatureSpawnRules.matchesNaturalHabitat(entry.type().get(), level, playerPos)) {
                total += spawnWeight(entry.type().get(), biome);
            }
        }
        if (total == 0) return null;
        int roll = random.nextInt(total);
        for (SpawnEntry entry : ENTRIES) {
            if (!CreatureSpawnRules.matchesNaturalHabitat(entry.type().get(), level, playerPos)) continue;
            roll -= spawnWeight(entry.type().get(), biome);
            if (roll < 0) return entry;
        }
        return null;
    }

    private static BlockPos findPosition(net.minecraft.server.level.ServerLevel level, BlockPos playerPos,
                                         EntityType<? extends LegacyCreature> type, RandomSource random) {
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            int distance = Mth.nextInt(random, MIN_PLAYER_DISTANCE, MAX_PLAYER_DISTANCE);
            int x = playerPos.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = playerPos.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos probe = new BlockPos(x, playerPos.getY(), z);
            if (!level.hasChunkAt(probe)) continue;
            BlockPos candidate = type == ZSSRegistries.OCTOROK.get()
                    ? findWater(level, x, z, playerPos.getY())
                    : findGround(level, x, z, playerPos.getY(), isSkulltula(type), random);
            if (candidate == null || !CreatureSpawnRules.matchesNaturalHabitat(type, level, candidate)) continue;
            if (level.players().stream().anyMatch(player -> !player.isSpectator()
                    && player.distanceToSqr(candidate.getX() + .5D, candidate.getY(), candidate.getZ() + .5D)
                    < MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE)) continue;
            if (SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, candidate, random)) return candidate;
        }
        return null;
    }

    private static BlockPos findWater(net.minecraft.server.level.ServerLevel level, int x, int z, int playerY) {
        int top = Math.min(level.getMaxBuildHeight() - 2, playerY + 8);
        int bottom = Math.max(level.getMinBuildHeight() + 1, playerY - 32);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).is(FluidTags.WATER)) return pos;
        }
        return null;
    }

    private static BlockPos findGround(net.minecraft.server.level.ServerLevel level, int x, int z, int playerY,
                                       boolean deep, RandomSource random) {
        int top = deep ? CreatureSpawnRules.DEEP_CAVE_MAX_Y
                : Math.min(level.getMaxBuildHeight() - 3, playerY + 12);
        int bottom = deep ? level.getMinBuildHeight() + 2
                : Math.max(level.getMinBuildHeight() + 2, playerY - 24);
        int start = deep ? Mth.nextInt(random, bottom, top) : top;
        for (int y = start; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos floor = pos.below();
            if (level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty()
                    && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return pos;
        }
        return null;
    }

    private static boolean spawn(net.minecraft.server.level.ServerLevel level,
                                 EntityType<? extends LegacyCreature> type, BlockPos pos, RandomSource random) {
        LegacyCreature creature = type.create(level);
        if (creature == null) return false;
        creature.moveTo(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D, random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(creature) || !creature.checkSpawnObstruction(level)) return false;
        creature.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
        return level.addFreshEntity(creature);
    }

    public static int spawnWeight(EntityType<?> type, Holder<Biome> biome) {
        if (type == ZSSRegistries.OCTOROK.get()) return biome.is(BiomeTags.IS_RIVER) ? 90 : 4;
        if (type == ZSSRegistries.WIZZROBE.get()) return biome.is(net.minecraft.world.level.biome.Biomes.NETHER_WASTES) ? 1 : 2;
        for (SpawnEntry entry : ENTRIES) if (entry.type().get() == type) return entry.weight();
        return 0;
    }

    public static int maxGroupSize(EntityType<?> type) {
        for (SpawnEntry entry : ENTRIES) if (entry.type().get() == type) return entry.maxCount();
        return 0;
    }

    public static boolean independentlySpawns(EntityType<?> type) {
        return ENTRIES.stream().anyMatch(entry -> entry.type().get() == type);
    }

    private static boolean isSkulltula(EntityType<?> type) {
        return type == ZSSRegistries.SKULLTULA.get() || type == ZSSRegistries.SKULLTULA_GOLD.get();
    }

    private static SpawnEntry entry(RegistryObject<EntityType<LegacyCreature>> type, int weight, int minCount, int maxCount) {
        return new SpawnEntry(type, weight, minCount, maxCount);
    }

    private record SpawnEntry(RegistryObject<EntityType<LegacyCreature>> type, int weight, int minCount, int maxCount) {}
}
